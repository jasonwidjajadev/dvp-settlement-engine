package com.jasonwidjaja.dvp.domain;

import java.time.LocalDate;
import java.util.UUID;

public record TradeTerms(
        String externalTradeId,
        UUID buyerId,
        UUID sellerId,
        UUID securityId,
        long quantity,
        long cashAmount,
        LocalDate settlementDate
) {
}
