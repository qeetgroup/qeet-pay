"""Pydantic request/response models for the fraud-scoring service."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class ScoreRequest(BaseModel):
    """A payment-authorization scoring request from the Java payment engine."""

    paymentId: str = Field(..., min_length=1, description="Payment attempt id.")
    merchantId: str = Field(..., min_length=1, description="Merchant id.")
    amountMinor: int = Field(
        ..., ge=0, description="Amount in the smallest currency unit (paise for INR)."
    )
    currency: str = Field(
        ..., min_length=3, max_length=3, description="ISO-4217 currency code, e.g. INR."
    )
    method: str = Field(..., min_length=1, description="Payment method, e.g. UPI, CARD.")
    customerVpa: str | None = Field(
        default=None, description="Customer UPI VPA, e.g. user@bank (UPI only)."
    )
    ip: str | None = Field(default=None, description="Client IP address.")


class FeatureContribution(BaseModel):
    """One explainable, RBI-audit-friendly feature contribution to the score.

    A deterministic SHAP-style attribution: ``contribution`` is the signed
    risk-points this feature added (positive) or removed (negative), ``value`` is
    the normalised feature value, and ``reason`` is a plain-English explanation.
    """

    feature: str = Field(..., description="Feature name, e.g. missing_vpa.")
    contribution: float = Field(..., description="Signed risk-points contributed.")
    value: float = Field(..., description="Normalised feature value.")
    reason: str = Field(..., description="Plain-English, audit-friendly explanation.")


class ScoreResponse(BaseModel):
    """The scoring decision returned to the payment engine."""

    score: int = Field(..., ge=0, le=100, description="Risk score; higher = riskier.")
    decision: Literal["allow", "challenge", "block"]
    reasons: list[str] = Field(
        default_factory=list, description="Explainable signals that fired."
    )
    explanation: list[FeatureContribution] = Field(
        default_factory=list,
        description="Deterministic SHAP-style top contributing features (Explainable AI).",
    )
    model: str = Field(
        default="rules",
        description="Scoring model that produced the score: 'onnx' | 'rules'.",
    )
    latencyMs: float = Field(..., ge=0, description="Server-side scoring latency in ms.")


class HealthResponse(BaseModel):
    status: Literal["ok"] = "ok"
