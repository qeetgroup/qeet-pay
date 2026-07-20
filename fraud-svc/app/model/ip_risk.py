"""IP-risk signal provider for fraud scoring (TAD Module 08 — geo/IP reputation).

Resolves a client IP address to a normalised risk signal in ``[0.0, 1.0]``
(``0.0`` = trusted, ``1.0`` = maximum risk). Two backends, chosen at call time:

* **MaxMind GeoIP2 / GeoLite2 (production).** When ``MAXMIND_DB_PATH`` points at a
  local ``.mmdb`` database — or ``MAXMIND_ACCOUNT_ID`` + ``MAXMIND_LICENSE_KEY``
  are set for the GeoIP2 web service — the ``geoip2`` client (imported lazily)
  resolves the IP to its country plus anonymizer / proxy / hosting flags, which
  are combined into a single risk score. Supports the Enterprise, Anonymous-IP,
  City and Country database shapes, and the ``insights`` / ``country`` web-service
  endpoints.

* **Deterministic heuristic (default / CI / offline).** When no MaxMind config is
  present — or the ``geoip2`` wheel is not installed, or a lookup fails — a fully
  offline, deterministic heuristic is used:

    - private / loopback / link-local / reserved / multicast → ``0.0`` (trusted)
    - IPs inside a sampled high-risk / anonymizer CIDR list → elevated
    - every other public IP → a light default baseline.

There is **no hard dependency on the ``geoip2`` / ``maxminddb`` wheels at import
time** — they are imported lazily and guarded, so the service runs without them.
They are the same class of optional extra as ``onnxruntime`` / ``redis`` (see
``requirements.txt``). Everything here is deterministic and offline-friendly, and
:func:`resolve` never raises.
"""

from __future__ import annotations

import ipaddress
import os

# ---------------------------------------------------------------------------
# Risk tuning (documented sample values; production calibrates from outcomes)
# ---------------------------------------------------------------------------

# Risk contributed by each MaxMind anonymizer / network-type flag. When several
# flags are set the strongest wins (max, then clamped) — e.g. a Tor exit that is
# also a hosting IP scores at the Tor ceiling.
_FLAG_RISK: dict[str, float] = {
    "is_tor_exit_node": 0.9,
    "is_residential_proxy": 0.8,
    "is_anonymous_vpn": 0.7,
    "is_public_proxy": 0.7,
    "is_anonymous": 0.6,
    "is_hosting_provider": 0.5,
}
_FLAG_ATTRS = tuple(_FLAG_RISK)

# Baseline risk for an ordinary, resolvable public IP with no adverse signal.
_DEFAULT_RISK = 0.1

# Risk assigned to an IP that falls inside the sampled high-risk CIDR list below.
_HIGH_RISK_SCORE = 0.85

# Sampled country-level risk weights (ISO-3166 alpha-2 -> 0..1). Illustrative
# sample only; production tunes this from chargeback / fraud-outcome data. Unknown
# countries fall back to :data:`_DEFAULT_RISK`.
_COUNTRY_RISK: dict[str, float] = {
    "IN": 0.10,  # home market
    "US": 0.15,
    "GB": 0.15,
    "SG": 0.15,
    "AU": 0.15,
    "DE": 0.12,
    "AE": 0.20,
    # Elevated-risk sample (illustrative)
    "NG": 0.70,
    "RU": 0.65,
    "IR": 0.85,
    "KP": 0.95,
    "VE": 0.60,
    "PK": 0.45,
}

# Sampled high-risk / anonymizer network ranges. Documented sample only — a mix
# of well-known public Tor exit-relay ranges and illustrative anonymizer /
# bulletproof-hosting blocks. Parsed once at import (stdlib ``ipaddress``, no
# external dependency) so per-request membership checks stay cheap (< 100ms P99).
_HIGH_RISK_CIDR_SAMPLES = (
    # Tor exit-relay ranges (public, well-known)
    "185.220.100.0/22",
    "185.220.101.0/24",
    "171.25.193.0/24",
    "192.42.116.0/22",
    "204.85.191.0/24",
    # Anonymizer / bulletproof-hosting sample blocks (illustrative)
    "45.146.164.0/22",
    "185.156.72.0/22",
    "5.188.10.0/23",
    # IPv6 Tor sample
    "2a0b:f4c0::/29",
)
_HIGH_RISK_CIDRS = tuple(ipaddress.ip_network(c) for c in _HIGH_RISK_CIDR_SAMPLES)


def _clamp(x: float) -> float:
    return max(0.0, min(1.0, float(x)))


# ---------------------------------------------------------------------------
# MaxMind provider (lazy / guarded — no hard dependency on geoip2)
# ---------------------------------------------------------------------------


def _flag_risk(obj: object) -> float | None:
    """Strongest anonymizer/proxy/hosting flag risk on ``obj``, or ``None``.

    Handles both the Anonymous-IP response (flags directly on the response) and
    the Enterprise / Insights response (flags on ``response.traits``).
    """
    if obj is None:
        return None
    risks = [_FLAG_RISK[attr] for attr in _FLAG_ATTRS if getattr(obj, attr, False)]
    return max(risks) if risks else None


def _score_response(resp: object) -> float | None:
    """Combine a MaxMind response into a single risk score, or ``None``.

    Takes the max of (a) any anonymizer/proxy/hosting flag risk and (b) the
    country-level risk. Returns ``None`` when the response carries neither, so
    the caller can fall through to the heuristic.
    """
    scores: list[float] = []

    flag = _flag_risk(resp)
    if flag is None:
        flag = _flag_risk(getattr(resp, "traits", None))
    if flag is not None:
        scores.append(flag)

    country = getattr(resp, "country", None)
    iso = getattr(country, "iso_code", None) if country is not None else None
    if iso:
        scores.append(_COUNTRY_RISK.get(str(iso).upper(), _DEFAULT_RISK))

    return max(scores) if scores else None


class _MaxMindProvider:
    """Thin wrapper over a geoip2 local DB reader or web-service client.

    Constructed only when MaxMind is configured; the ``geoip2`` import happens
    here (lazily), so a missing wheel simply means no provider is built.
    """

    def __init__(
        self,
        *,
        db_path: str | None = None,
        account_id: str | None = None,
        license_key: str | None = None,
    ) -> None:
        self._reader = None
        self._client = None
        if db_path:
            import geoip2.database  # type: ignore

            self._reader = geoip2.database.Reader(db_path)
        elif account_id and license_key:
            import geoip2.webservice  # type: ignore

            self._client = geoip2.webservice.Client(int(account_id), license_key)

    def score(self, ip: str) -> float | None:
        """Resolve ``ip`` to a risk score, or ``None`` if it can't be scored."""
        if self._reader is not None:
            return self._score_via_reader(ip)
        if self._client is not None:
            return self._score_via(self._client, ip, ("insights", "country"))
        return None

    def _score_via_reader(self, ip: str) -> float | None:
        # Databases that carry anonymizer flags first, then plain geo. Methods
        # unsupported by the loaded DB type raise and are skipped.
        return self._score_via(
            self._reader, ip, ("enterprise", "anonymous_ip", "city", "country")
        )

    @staticmethod
    def _score_via(source: object, ip: str, methods: tuple[str, ...]) -> float | None:
        for name in methods:
            fn = getattr(source, name, None)
            if fn is None:
                continue
            try:
                resp = fn(ip)
            except Exception:
                # Method unsupported for this DB, IP absent, or transient
                # web-service error — try the next / fall back to heuristic.
                continue
            return _score_response(resp)
        return None


# Cached provider (built once, like predict._session). ``_loaded`` lets tests /
# env changes force a rebuild via :func:`reset`.
_provider: _MaxMindProvider | None = None
_loaded = False


def _build_provider() -> _MaxMindProvider | None:
    db_path = os.getenv("MAXMIND_DB_PATH")
    account_id = os.getenv("MAXMIND_ACCOUNT_ID")
    license_key = os.getenv("MAXMIND_LICENSE_KEY")
    try:
        if db_path:
            if not os.path.exists(db_path):
                return None
            return _MaxMindProvider(db_path=db_path)
        if account_id and license_key:
            return _MaxMindProvider(account_id=account_id, license_key=license_key)
    except Exception:
        # geoip2 wheel missing, unreadable .mmdb, or bad config -> heuristic.
        return None
    return None


def _get_provider() -> _MaxMindProvider | None:
    global _provider, _loaded
    if not _loaded:
        _provider = _build_provider()
        _loaded = True
    return _provider


def reset() -> None:
    """Clear the cached provider so config/env changes take effect (tests)."""
    global _provider, _loaded
    _provider = None
    _loaded = False


# ---------------------------------------------------------------------------
# Deterministic offline heuristic
# ---------------------------------------------------------------------------


def _is_non_routable(addr: ipaddress._BaseAddress) -> bool:
    return (
        addr.is_private
        or addr.is_loopback
        or addr.is_link_local
        or addr.is_unspecified
        or addr.is_reserved
        or addr.is_multicast
    )


def _heuristic_public_risk(addr: ipaddress._BaseAddress) -> float:
    for net in _HIGH_RISK_CIDRS:
        if addr.version == net.version and addr in net:
            return _HIGH_RISK_SCORE
    return _DEFAULT_RISK


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------


def resolve(ip: str | None) -> float:
    """Return the IP-risk signal in ``[0.0, 1.0]`` (0.0 trusted, 1.0 max risk).

    Never raises: missing / blank / unparseable IPs return the trusted ``0.0``.
    Non-routable (private/loopback/…) addresses are always ``0.0``. Otherwise the
    MaxMind provider is consulted when configured, falling back to the offline
    heuristic on any error or when unconfigured.
    """
    if not ip:
        return 0.0
    ip = ip.strip()
    if not ip:
        return 0.0

    try:
        addr = ipaddress.ip_address(ip)
    except ValueError:
        return 0.0  # unparseable -> don't penalise bad/unknown input

    # Non-routable addresses are inherently trusted and absent from GeoIP DBs.
    if _is_non_routable(addr):
        return 0.0

    provider = _get_provider()
    if provider is not None:
        try:
            score = provider.score(ip)
            if score is not None:
                return _clamp(score)
        except Exception:
            pass  # any provider error -> deterministic heuristic

    return _heuristic_public_risk(addr)
