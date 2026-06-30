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


class ScoreResponse(BaseModel):
    """The scoring decision returned to the payment engine."""

    score: int = Field(..., ge=0, le=100, description="Risk score; higher = riskier.")
    decision: Literal["allow", "challenge", "block"]
    reasons: list[str] = Field(
        default_factory=list, description="Explainable signals that fired."
    )
    latencyMs: float = Field(..., ge=0, description="Server-side scoring latency in ms.")


class HealthResponse(BaseModel):
    status: Literal["ok"] = "ok"
