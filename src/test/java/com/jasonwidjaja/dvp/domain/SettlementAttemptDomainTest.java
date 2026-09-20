package com.jasonwidjaja.dvp.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementAttemptDomainTest {

    @Test
    void outcomeVocabularyMatchesApprovedSettlementDecisions() {
        assertThat(SettlementOutcome.values()).containsExactly(
                SettlementOutcome.SETTLED,
                SettlementOutcome.ALREADY_SETTLED,
                SettlementOutcome.NOT_DUE,
                SettlementOutcome.INSUFFICIENT_CASH,
                SettlementOutcome.INSUFFICIENT_SECURITIES);
        assertThat(SettlementOutcome.values())
                .extracting(Enum::name)
                .doesNotContain("MISSING_ACCOUNT");
    }

    @Test
    void rejectionAttemptHasNoJournal() {
        SettlementAttempt attempt = new SettlementAttempt(
                UUID.fromString("00000000-0000-0000-0000-000000000401"),
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "settle-T-001",
                SettlementOutcome.NOT_DUE,
                null,
                LocalDate.of(2026, 9, 20),
                Instant.parse("2026-09-20T00:00:00Z"));

        assertThat(attempt.journalId()).isNull();
        assertThat(attempt.outcome()).isEqualTo(SettlementOutcome.NOT_DUE);
        assertThat(attempt.businessDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }
}
