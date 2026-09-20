package com.jasonwidjaja.dvp.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.SettleCommand;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.persistence.AccountRepository;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;
import com.jasonwidjaja.dvp.persistence.SettlementAttemptRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettleTradeRollbackIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Autowired
    private DataSource dataSource;

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

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
    }

    @Test
    void technicalFailureAfterClaimRollsBackTheCommandResult() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        List<Account> before = accounts.findAll();

        assertThatThrownBy(() -> settle.settle(new SettleCommand("settle-T-001", trade.id())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced settlement failure");

        assertThat(commandResults.findByCommandKey("settle-T-001")).isEmpty();
        assertThat(attempts.findByTradeId(trade.id())).isEmpty();
        assertThat(accounts.findAll()).containsExactlyElementsOf(before);
        assertThat(trades.findById(trade.id())).isPresent();
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
    static class FailOnceAfterTradeLockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-20T04:00:00Z"), SYDNEY);
        }

        @Bean
        @Primary
        TradeRepository failOnceAfterLockTradeRepository(NamedParameterJdbcTemplate jdbc) {
            return new FailOnceAfterLockTradeRepository(jdbc);
        }
    }

    static class FailOnceAfterLockTradeRepository extends TradeRepository {

        private boolean failNextLock = true;

        FailOnceAfterLockTradeRepository(NamedParameterJdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public Optional<Trade> lockById(UUID tradeId) {
            Optional<Trade> locked = super.lockById(tradeId);
            if (failNextLock) {
                failNextLock = false;
                throw new IllegalStateException("forced settlement failure");
            }
            return locked;
        }
    }
}
