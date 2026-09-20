package com.jasonwidjaja.dvp.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.CaptureCommand;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.persistence.AccountRepository;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureTradeRollbackIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CaptureTradeService capture;

    @Autowired
    private TradeRepository trades;

    @Autowired
    private CommandResultRepository commandResults;

    @Autowired
    private AccountRepository accounts;

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
    }

    @Test
    void technicalFailureAfterInsertRollsBackTradeAndCommandClaim() {
        List<Account> before = accounts.findAll();
        CaptureCommand command = new CaptureCommand("capture-T-001", aliceBuysEq1());

        assertThatThrownBy(() -> capture.capture(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced capture failure");

        assertThat(trades.findByExternalTradeId("T-001")).isEmpty();
        assertThat(commandResults.findByCommandKey("capture-T-001")).isEmpty();
        assertThat(accounts.findAll()).containsExactlyElementsOf(before);

        CommandOutcome retry = capture.capture(command);

        assertThat(retry.httpStatus()).isEqualTo(201);
        assertThat(trades.findByExternalTradeId("T-001")).isPresent();
        assertThat(commandResults.findByCommandKey("capture-T-001")).isPresent();
        assertThat(accounts.findAll()).containsExactlyElementsOf(before);
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
    static class FailOnceAfterInsertConfig {

        @Bean
        @Primary
        TradeRepository failOnceAfterInsertTradeRepository(NamedParameterJdbcTemplate jdbc) {
            return new FailOnceAfterInsertTradeRepository(jdbc);
        }
    }

    static class FailOnceAfterInsertTradeRepository extends TradeRepository {

        private boolean failNextSuccessfulInsert = true;

        FailOnceAfterInsertTradeRepository(NamedParameterJdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public Optional<Trade> insertIfAbsent(TradeTerms terms) {
            Optional<Trade> inserted = super.insertIfAbsent(terms);
            if (inserted.isPresent() && failNextSuccessfulInsert) {
                failNextSuccessfulInsert = false;
                throw new IllegalStateException("forced capture failure");
            }
            return inserted;
        }
    }
}
