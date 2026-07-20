"""Tests for the IP-risk feature (app.model.ip_risk) and its wiring into the
feature vector.

All tests run offline: no MaxMind database, no `geoip2` wheel, no network. The
provider path is exercised via a fake provider / fake responses so the MaxMind
scoring logic is covered without the optional dependency.
"""

from __future__ import annotations

from types import SimpleNamespace

import pytest

from app.model import ip_risk
from app.model.features import extract


@pytest.fixture(autouse=True)
def _reset_provider():
    """Clear any cached provider before and after each test (env isolation)."""
    ip_risk.reset()
    yield
    ip_risk.reset()


# ---------------------------------------------------------------------------
# Heuristic fallback (no MaxMind configured — the default / CI path)
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "ip",
    [
        "10.0.0.1",       # private
        "192.168.1.10",   # private
        "172.16.5.4",     # private
        "127.0.0.1",      # loopback
        "::1",            # IPv6 loopback
        "169.254.1.1",    # link-local
        "0.0.0.0",        # unspecified
        "fe80::1",        # IPv6 link-local
    ],
)
def test_private_and_nonroutable_ip_is_zero(ip):
    assert ip_risk.resolve(ip) == 0.0


@pytest.mark.parametrize("ip", [None, "", "   ", "not-an-ip", "999.999.999.999"])
def test_missing_or_unparseable_ip_is_zero(ip):
    assert ip_risk.resolve(ip) == 0.0


@pytest.mark.parametrize(
    "ip",
    [
        "185.220.101.5",  # sampled Tor exit-relay range
        "185.220.100.7",
        "171.25.193.20",
        "45.146.164.9",   # sampled anonymizer / hosting block
    ],
)
def test_sampled_high_risk_ip_is_elevated(ip):
    score = ip_risk.resolve(ip)
    assert score >= 0.5, f"expected elevated risk for {ip}, got {score}"
    assert 0.0 <= score <= 1.0


def test_ordinary_public_ip_is_light_default():
    # A routable public IP not in the sample list -> light default baseline.
    score = ip_risk.resolve("8.8.8.8")
    assert score == ip_risk._DEFAULT_RISK
    assert 0.0 < score < 0.5


def test_resolve_always_in_range():
    for ip in ["8.8.8.8", "185.220.101.5", "10.0.0.1", "1.2.3.4", "2001:4860:4860::8888"]:
        assert 0.0 <= ip_risk.resolve(ip) <= 1.0


def test_no_provider_when_maxmind_unconfigured(monkeypatch):
    for key in ("MAXMIND_DB_PATH", "MAXMIND_ACCOUNT_ID", "MAXMIND_LICENSE_KEY"):
        monkeypatch.delenv(key, raising=False)
    ip_risk.reset()
    assert ip_risk._get_provider() is None


def test_missing_mmdb_file_falls_back_to_heuristic(monkeypatch):
    # A configured-but-missing DB path must not break scoring: no provider built,
    # heuristic still resolves.
    monkeypatch.setenv("MAXMIND_DB_PATH", "/nonexistent/GeoLite2-Country.mmdb")
    ip_risk.reset()
    assert ip_risk._get_provider() is None
    assert ip_risk.resolve("185.220.101.5") >= 0.5   # heuristic high-risk
    assert ip_risk.resolve("10.0.0.1") == 0.0        # heuristic private


# ---------------------------------------------------------------------------
# MaxMind provider path (faked — no geoip2 wheel / network needed)
# ---------------------------------------------------------------------------


def test_maxmind_provider_score_is_used(monkeypatch):
    class _FakeProvider:
        def score(self, ip):
            return 0.73

    monkeypatch.setattr(ip_risk, "_provider", _FakeProvider(), raising=False)
    monkeypatch.setattr(ip_risk, "_loaded", True, raising=False)
    # A public IP is routed to the provider (non-routable IPs short-circuit to 0).
    assert ip_risk.resolve("1.2.3.4") == pytest.approx(0.73)


def test_provider_error_falls_back_to_heuristic(monkeypatch):
    class _BoomProvider:
        def score(self, ip):
            raise RuntimeError("db read failed")

    monkeypatch.setattr(ip_risk, "_provider", _BoomProvider(), raising=False)
    monkeypatch.setattr(ip_risk, "_loaded", True, raising=False)
    # High-risk sampled IP still resolves via the heuristic despite provider error.
    assert ip_risk.resolve("185.220.101.5") >= 0.5


def test_provider_none_result_falls_back_to_heuristic(monkeypatch):
    class _NoneProvider:
        def score(self, ip):
            return None

    monkeypatch.setattr(ip_risk, "_provider", _NoneProvider(), raising=False)
    monkeypatch.setattr(ip_risk, "_loaded", True, raising=False)
    assert ip_risk.resolve("185.220.101.5") >= 0.5
    assert ip_risk.resolve("8.8.8.8") == ip_risk._DEFAULT_RISK


def test_score_response_reads_anonymizer_flags():
    # Anonymous-IP response shape: flags directly on the response object.
    tor = SimpleNamespace(
        is_tor_exit_node=True,
        is_anonymous_vpn=False,
        is_public_proxy=False,
        is_anonymous=True,
        is_residential_proxy=False,
        is_hosting_provider=False,
    )
    assert ip_risk._score_response(tor) == pytest.approx(ip_risk._FLAG_RISK["is_tor_exit_node"])


def test_score_response_reads_flags_on_traits_and_country():
    # Enterprise/Insights response shape: flags on .traits, country on .country.
    resp = SimpleNamespace(
        traits=SimpleNamespace(
            is_hosting_provider=True,
            is_tor_exit_node=False,
            is_anonymous_vpn=False,
            is_public_proxy=False,
            is_anonymous=False,
            is_residential_proxy=False,
        ),
        country=SimpleNamespace(iso_code="US"),
    )
    # max(hosting flag risk, US country risk)
    expected = max(ip_risk._FLAG_RISK["is_hosting_provider"], ip_risk._COUNTRY_RISK["US"])
    assert ip_risk._score_response(resp) == pytest.approx(expected)


def test_score_response_country_only():
    resp = SimpleNamespace(country=SimpleNamespace(iso_code="NG"))
    assert ip_risk._score_response(resp) == pytest.approx(ip_risk._COUNTRY_RISK["NG"])


def test_score_response_unknown_country_uses_default():
    resp = SimpleNamespace(country=SimpleNamespace(iso_code="ZZ"))
    assert ip_risk._score_response(resp) == pytest.approx(ip_risk._DEFAULT_RISK)


def test_score_response_empty_returns_none():
    assert ip_risk._score_response(SimpleNamespace()) is None


# ---------------------------------------------------------------------------
# Feature-vector wiring: ip_risk flows through extract() with the same shape
# ---------------------------------------------------------------------------


def test_feature_vector_uses_ip_risk_signal():
    high = extract(
        amount_minor=1000, customer_vpa="a@b", merchant_id="m",
        method="UPI", velocity_1m=0, ip="185.220.101.5",
    )
    private = extract(
        amount_minor=1000, customer_vpa="a@b", merchant_id="m",
        method="UPI", velocity_1m=0, ip="10.0.0.1",
    )
    assert high.ip_risk >= 0.5
    assert private.ip_risk == 0.0
    assert high.ip_risk > private.ip_risk


def test_feature_vector_shape_preserved():
    # Same 9-field vector as before; ip_risk stays the last element and is a float.
    fv = extract(
        amount_minor=500_000, customer_vpa="a@b", merchant_id="m",
        method="CARD", velocity_1m=2, ip="185.220.101.5",
    )
    vec = fv.to_list()
    assert len(vec) == 9
    assert all(isinstance(v, (int, float)) for v in vec)
    assert isinstance(fv.ip_risk, float)
    assert vec[-1] == fv.ip_risk


def test_feature_vector_default_ip_is_zero():
    # ip defaults to None (kwarg) -> trusted 0.0, no regression for missing IP.
    fv = extract(amount_minor=1000, customer_vpa="a@b", merchant_id="m", method="UPI", velocity_1m=0)
    assert fv.ip_risk == 0.0
