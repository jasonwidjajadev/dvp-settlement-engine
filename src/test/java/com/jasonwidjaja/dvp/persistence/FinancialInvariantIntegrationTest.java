package com.jasonwidjaja.dvp.persistence;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.application.BusinessCalendar;
import com.jasonwidjaja.dvp.application.SettleTradeService;
import com.jasonwidjaja.dvp.domain.SettleCommand;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;
import com.jasonwidjaja.dvp.support.FinancialInvariantChecks;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialInvariantIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private SettleTradeService settle;

    @Autowired
    private TradeRepository trades;

    @Autowired
    private BusinessCalendar calendar;

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
    }

    @Test
    void conservationAndJournalShapeHoldAfterAliceBobSettlement() {
        long audBefore = FinancialInvariantChecks.assetCurrentTotal(jdbc, "AUD");
        long eq1Before = FinancialInvariantChecks.assetCurrentTotal(jdbc, "EQ1");
        assertThat(audBefore).isEqualTo(100000);
        assertThat(eq1Before).isEqualTo(10);
        assertThat(audBefore).isEqualTo(FinancialInvariantChecks.assetOpeningTotal(jdbc, "AUD"));
        assertThat(eq1Before).isEqualTo(FinancialInvariantChecks.assetOpeningTotal(jdbc, "EQ1"));

        Trade trade = insertDueTrade("T-001", 10, 50000);
        settle.settle(new SettleCommand("settle-T-001", trade.id()));

        FinancialInvariantChecks.assertConservationAndJournalShapeHold(jdbc);
        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        assertThat(FinancialInvariantChecks.assetCurrentTotal(jdbc, "AUD")).isEqualTo(audBefore);
        assertThat(FinancialInvariantChecks.assetCurrentTotal(jdbc, "EQ1")).isEqualTo(eq1Before);
        assertThat(FinancialInvariantChecks.journalCount(jdbc)).isEqualTo(1);
        assertThat(FinancialInvariantChecks.postingCount(jdbc)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM settlement_journal WHERE trade_id = :tradeId",
                        Map.of("tradeId", trade.id()),
                        Long.class))
                .isEqualTo(1);
        assertThat(currentBalance(DemoSeed.ALICE_AUD_ID)).isEqualTo(50000);
        assertThat(currentBalance(DemoSeed.ALICE_EQ1_ID)).isEqualTo(10);
        assertThat(currentBalance(DemoSeed.BOB_AUD_ID)).isEqualTo(50000);
        assertThat(currentBalance(DemoSeed.BOB_EQ1_ID)).isEqualTo(0);
    }

    @Test
    void conservationAndShapeHoldAfterRejections() {
        settle.settle(new SettleCommand(
                "settle-not-due",
                insertTrade("T-002", 10, 50000, calendar.businessDate().plusDays(1)).id()));
        settle.settle(new SettleCommand("settle-cash-short", insertDueTrade("T-003", 10, 100001).id()));
        settle.settle(new SettleCommand("settle-sec-short", insertDueTrade("T-004", 11, 50000).id()));

        FinancialInvariantChecks.assertConservationAndJournalShapeHold(jdbc);
        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        assertThat(FinancialInvariantChecks.journalCount(jdbc)).isZero();
        assertThat(FinancialInvariantChecks.postingCount(jdbc)).isZero();
        assertThat(FinancialInvariantChecks.assetCurrentTotal(jdbc, "AUD")).isEqualTo(100000);
        assertThat(FinancialInvariantChecks.assetCurrentTotal(jdbc, "EQ1")).isEqualTo(10);
        assertThat(currentBalance(DemoSeed.ALICE_AUD_ID)).isEqualTo(100000);
        assertThat(currentBalance(DemoSeed.BOB_EQ1_ID)).isEqualTo(10);
    }

    private Trade insertDueTrade(String externalTradeId, long quantity, long cashAmount) {
        return insertTrade(externalTradeId, quantity, cashAmount, calendar.businessDate());
    }

    private Trade insertTrade(String externalTradeId, long quantity, long cashAmount, LocalDate settlementDate) {
        return trades.insertIfAbsent(new TradeTerms(
                externalTradeId,
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                quantity,
                cashAmount,
                settlementDate)).orElseThrow();
    }

    private long currentBalance(UUID accountId) {
        return jdbc.queryForObject(
                "SELECT current_balance FROM account WHERE id = :id",
                Map.of("id", accountId),
                Long.class);
    }
}
