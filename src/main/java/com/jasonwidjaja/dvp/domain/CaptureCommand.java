package com.jasonwidjaja.dvp.domain;

public record CaptureCommand(
        String idempotencyKey,
        TradeTerms terms
) {
}
