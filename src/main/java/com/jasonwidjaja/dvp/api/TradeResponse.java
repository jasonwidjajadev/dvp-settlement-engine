package com.jasonwidjaja.dvp.api;

import java.time.LocalDate;
import java.util.UUID;

import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeStatus;

public record TradeResponse(
        UUID id,
        String externalTradeId,
        UUID buyerId,
        UUID sellerId,
        UUID securityId,
        long quantity,
        long cashAmount,
        LocalDate settlementDate,
        TradeStatus status,
        UUID journalId
) {

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
                trade.id(),
                trade.terms().externalTradeId(),
                trade.terms().buyerId(),
                trade.terms().sellerId(),
                trade.terms().securityId(),
                trade.terms().quantity(),
                trade.terms().cashAmount(),
                trade.terms().settlementDate(),
                trade.status(),
                trade.journalId());
    }
}
