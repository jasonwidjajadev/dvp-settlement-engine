package com.jasonwidjaja.dvp.api;

import java.time.Instant;
import java.util.UUID;

import com.jasonwidjaja.dvp.domain.SettlementOutcome;
import com.jasonwidjaja.dvp.domain.TradeStatus;

public record SettlementResponse(
        UUID tradeId,
        TradeStatus status,
        SettlementOutcome outcome,
        UUID journalId,
        Instant settledAt
) {
}
