package com.jasonwidjaja.dvp.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.api.SettlementResponse;
import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.SettleCommand;
import com.jasonwidjaja.dvp.domain.SettlementOutcome;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.persistence.AccountRepository;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;
import com.jasonwidjaja.dvp.persistence.SettlementAttemptRepository;
import com.jasonwidjaja.dvp.persistence.SettlementJournalRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;
import com.jasonwidjaja.dvp.support.FinancialInvariantChecks;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettleTradeWriteRollbackIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private SettleTradeService settle;

    @Autowired
    private TradeRepository trades;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CommandResultRepository commandResults;

    @Autowired
    private SettlementAttemptRepository attempts;

    @Autowired
    private SettlementJournalRepository journals;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
    }

    @Test
    void failureAfterBalanceUpdatesRollsBackThenRetrySettlesOnce() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        List<Account> before = accounts.findAll();

        assertThatThrownBy(() -> settle.settle(new SettleCommand("settle-T-001", trade.id())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced settlement write failure");

        assertThat(journals.findJournalByTradeId(trade.id())).isEmpty();
        assertThat(postingCount()).isZero();
        assertThat(attempts.findByTradeId(trade.id())).isEmpty();
        assertThat(commandResults.findByCommandKey("settle-T-001")).isEmpty();
        Trade rolledBack = trades.findById(trade.id()).orElseThrow();
        assertThat(rolledBack.status()).isEqualTo(TradeStatus.READY);
        assertThat(rolledBack.journalId()).isNull();
        assertThat(rolledBack.terms()).isEqualTo(trade.terms());
        assertThat(accounts.findAll()).containsExactlyElementsOf(before);
        assertOpeningBalancesUnchanged();
        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        FinancialInvariantChecks.assertConservationAndJournalShapeHold(jdbc);

        CommandOutcome retry = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        SettlementResponse body = json.readValue(retry.responseBody(), SettlementResponse.class);
        assertThat(retry.httpStatus()).isEqualTo(201);
        assertThat(body.outcome()).isEqualTo(SettlementOutcome.SETTLED);
        assertThat(journals.findJournalByTradeId(trade.id())).isPresent();
        assertThat(journals.findPostingsByJournalId(body.journalId())).hasSize(4);
        assertThat(attempts.findByTradeId(trade.id())).hasSize(1);
        assertThat(commandResults.findByCommandKey("settle-T-001").orElseThrow().completed()).isTrue();
        assertThat(trades.findById(trade.id()).orElseThrow().status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(accounts.findById(DemoSeed.ALICE_AUD_ID).orElseThrow().currentBalance()).isEqualTo(50000);
        assertThat(accounts.findById(DemoSeed.ALICE_EQ1_ID).orElseThrow().currentBalance()).isEqualTo(10);
        assertThat(accounts.findById(DemoSeed.BOB_AUD_ID).orElseThrow().currentBalance()).isEqualTo(50000);
        assertThat(accounts.findById(DemoSeed.BOB_EQ1_ID).orElseThrow().currentBalance()).isEqualTo(0);
        assertOpeningBalancesUnchanged();
        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        FinancialInvariantChecks.assertConservationAndJournalShapeHold(jdbc);

        CommandOutcome secondRetry = settle.settle(new SettleCommand("settle-T-001", trade.id()));
        assertThat(secondRetry).isEqualTo(retry);
        assertThat(attempts.findByTradeId(trade.id())).hasSize(1);
        assertThat(journals.findJournalByTradeId(trade.id())).isPresent();
        assertThat(journals.findPostingsByJournalId(body.journalId())).hasSize(4);
        FinancialInvariantChecks.assertReconstructionHolds(jdbc);
        FinancialInvariantChecks.assertConservationAndJournalShapeHold(jdbc);
    }

    private Integer postingCount() {
        return jdbc.queryForObject("SELECT count(*) FROM posting", Map.of(), Integer.class);
    }

    private void assertOpeningBalancesUnchanged() {
        assertThat(accounts.findById(DemoSeed.ALICE_AUD_ID).orElseThrow().openingBalance()).isEqualTo(100000);
        assertThat(accounts.findById(DemoSeed.ALICE_EQ1_ID).orElseThrow().openingBalance()).isEqualTo(0);
        assertThat(accounts.findById(DemoSeed.BOB_AUD_ID).orElseThrow().openingBalance()).isEqualTo(0);
        assertThat(accounts.findById(DemoSeed.BOB_EQ1_ID).orElseThrow().openingBalance()).isEqualTo(10);
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

    @TestConfiguration
    static class FailOnceBeforeMarkSettledConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-20T04:00:00Z"), SYDNEY);
        }

        @Bean
        @Primary
        TradeRepository failOnceBeforeMarkSettledTradeRepository(NamedParameterJdbcTemplate jdbc) {
            return new FailOnceBeforeMarkSettledTradeRepository(jdbc);
        }
    }

    static class FailOnceBeforeMarkSettledTradeRepository extends TradeRepository {

        private boolean failNextMarkSettled = true;

        FailOnceBeforeMarkSettledTradeRepository(NamedParameterJdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public void markSettled(UUID tradeId, UUID journalId) {
            if (failNextMarkSettled) {
                failNextMarkSettled = false;
                throw new IllegalStateException("forced settlement write failure");
            }
            super.markSettled(tradeId, journalId);
        }
    }
}
