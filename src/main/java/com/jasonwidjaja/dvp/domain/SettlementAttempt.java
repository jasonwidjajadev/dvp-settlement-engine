package com.jasonwidjaja.dvp.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementAttempt(
        UUID id,
        UUID tradeId,
        String commandKey,
        SettlementOutcome outcome,
        UUID journalId,
        LocalDate businessDate,
        Instant decidedAt
) {
}
