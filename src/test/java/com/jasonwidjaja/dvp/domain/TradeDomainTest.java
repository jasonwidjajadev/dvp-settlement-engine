package com.jasonwidjaja.dvp.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;

class TradeDomainTest {

    @Test
    void tradeTermsHoldImmutableEconomicFacts() {
        TradeTerms terms = aliceBuysEq1();

        assertThat(terms.externalTradeId()).isEqualTo("T-001");
        assertThat(terms.buyerId()).isEqualTo(DemoSeed.ALICE_ID);
        assertThat(terms.sellerId()).isEqualTo(DemoSeed.BOB_ID);
        assertThat(terms.securityId()).isEqualTo(DemoSeed.EQ1_ID);
        assertThat(terms.quantity()).isEqualTo(10);
        assertThat(terms.cashAmount()).isEqualTo(50000);
        assertThat(terms.settlementDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void tradeIsReadyWithoutAJournal() {
        TradeTerms terms = aliceBuysEq1();
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        Trade trade = new Trade(tradeId, terms, TradeStatus.READY, null);

        assertThat(trade.id()).isEqualTo(tradeId);
        assertThat(trade.terms()).isEqualTo(terms);
        assertThat(trade.status()).isEqualTo(TradeStatus.READY);
        assertThat(trade.journalId()).isNull();
    }

    @Test
    void settledTradePointsAtItsJournal() {
        TradeTerms terms = aliceBuysEq1();
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID journalId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        Trade trade = new Trade(tradeId, terms, TradeStatus.SETTLED, journalId);

        assertThat(trade.status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(trade.journalId()).isEqualTo(journalId);
        assertThat(trade.terms()).isEqualTo(terms);
        assertThat(TradeStatus.values()).containsExactly(TradeStatus.READY, TradeStatus.SETTLED);
    }

    @Test
    void captureCommandKeepsKeySeparateFromTradeTerms() {
        TradeTerms terms = aliceBuysEq1();
        CaptureCommand first = new CaptureCommand("capture-T-001", terms);
        CaptureCommand second = new CaptureCommand("capture-T-001-retry", terms);

        assertThat(first.terms()).isEqualTo(second.terms());
        assertThat(first.idempotencyKey()).isNotEqualTo(second.idempotencyKey());
        assertThat(first).isNotEqualTo(second);
    }

    private static TradeTerms aliceBuysEq1() {
        return new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 20));
    }
}
