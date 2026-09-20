package com.jasonwidjaja.dvp.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;

class SettleRequestIdentityTest {

    private static final UUID TRADE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_TRADE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Test
    void twoSettleCommandsForTheSameTradeShareAnIdentity() {
        SettleCommand first = new SettleCommand("settle-T-001", TRADE_ID);
        SettleCommand retry = new SettleCommand("settle-T-001-retry", TRADE_ID);

        assertThat(SettleRequestIdentity.of(first.tradeId())).isEqualTo(SettleRequestIdentity.of(retry.tradeId()));
        assertThat(first.idempotencyKey()).isNotEqualTo(retry.idempotencyKey());
        assertThat(SettleRequestIdentity.of(TRADE_ID)).isEqualTo(
                "12:SETTLE_TRADE,36:00000000-0000-0000-0000-000000000101,");
    }

    @Test
    void sameKeyAgainstADifferentTradeHasADifferentIdentity() {
        assertThat(SettleRequestIdentity.of(TRADE_ID)).isNotEqualTo(SettleRequestIdentity.of(OTHER_TRADE_ID));
    }

    @Test
    void captureIdentityAndSettleIdentityNeverCollide() {
        TradeTerms terms = new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 20));

        assertThat(CaptureRequestIdentity.of(terms)).isNotEqualTo(SettleRequestIdentity.of(TRADE_ID));
        assertThat(CaptureRequestIdentity.of(terms)).startsWith("13:CAPTURE_TRADE,");
        assertThat(SettleRequestIdentity.of(TRADE_ID)).startsWith("12:SETTLE_TRADE,");
    }
}
