package com.jasonwidjaja.dvp.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.jasonwidjaja.dvp.domain.SettlementAttempt;
import com.jasonwidjaja.dvp.domain.SettlementOutcome;

public record SettlementAttemptResponse(
        UUID id,
        SettlementOutcome outcome,
        UUID journalId,
        LocalDate businessDate,
        Instant decidedAt,
        String commandKey
) {

    public static SettlementAttemptResponse from(SettlementAttempt attempt) {
        return new SettlementAttemptResponse(
                attempt.id(),
                attempt.outcome(),
                attempt.journalId(),
                attempt.businessDate(),
                attempt.decidedAt(),
                attempt.commandKey());
    }
}
