package com.jasonwidjaja.dvp.domain;

import java.util.UUID;

public record Trade(
        UUID id,
        TradeTerms terms,
        TradeStatus status,
        UUID journalId
) {
}
