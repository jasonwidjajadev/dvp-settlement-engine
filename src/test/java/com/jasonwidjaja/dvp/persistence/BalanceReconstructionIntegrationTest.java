package com.jasonwidjaja.dvp.persistence;

import java.time.LocalDate;
import java.util.Map;

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

class BalanceReconstructionIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void reconstructionHoldsForSeededStateBeforeSettlement() {
        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        assertThat(FinancialInvariantChecks.journalCount(jdbc)).isZero();
        assertThat(FinancialInvariantChecks.postingCount(jdbc)).isZero();
    }

    @Test
    void reconstructionHoldsAfterAliceBobSettlementAndReplay() {
        Trade trade = insertDueTrade("T-001", 10, 50000);

        settle.settle(new SettleCommand("settle-T-001", trade.id()));
        FinancialInvariantChecks.assertReconstructionHolds(jdbc);

        settle.settle(new SettleCommand("settle-T-001", trade.id()));
        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        assertThat(FinancialInvariantChecks.journalCount(jdbc)).isEqualTo(1);
        assertThat(FinancialInvariantChecks.postingCount(jdbc)).isEqualTo(4);
    }

    @Test
    void reconstructionHoldsAfterNotDueRejection() {
        Trade trade = insertTrade("T-001", 10, 50000, calendar.businessDate().plusDays(1));

        settle.settle(new SettleCommand("settle-not-due", trade.id()));

        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        assertThat(FinancialInvariantChecks.journalCount(jdbc)).isZero();
        assertThat(FinancialInvariantChecks.postingCount(jdbc)).isZero();
    }

    @Test
    void reconstructionHoldsAfterInsufficientCashRejection() {
        Trade trade = insertDueTrade("T-001", 10, 100001);

        settle.settle(new SettleCommand("settle-cash-short", trade.id()));

        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        assertThat(FinancialInvariantChecks.journalCount(jdbc)).isZero();
        assertThat(FinancialInvariantChecks.postingCount(jdbc)).isZero();
    }

    @Test
    void reconstructionHoldsAfterInsufficientSecuritiesRejection() {
        Trade trade = insertDueTrade("T-001", 11, 50000);

        settle.settle(new SettleCommand("settle-sec-short", trade.id()));

        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        assertThat(FinancialInvariantChecks.journalCount(jdbc)).isZero();
        assertThat(FinancialInvariantChecks.postingCount(jdbc)).isZero();
    }

    @Test
    void reconstructionDetectsADeliberatelyCorruptedCurrentBalance() {
        jdbc.update(
                "UPDATE account SET current_balance = 50000 WHERE id = :id",
                Map.of("id", DemoSeed.ALICE_AUD_ID));

        assertThat(FinancialInvariantChecks.mismatchedAccountIds(jdbc))
                .containsExactly(DemoSeed.ALICE_AUD_ID);
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
}
