package com.jasonwidjaja.dvp.domain;

import java.time.Instant;
import java.util.UUID;

public record SettlementJournal(
        UUID id,
        UUID tradeId,
        Instant settledAt
) {
}
