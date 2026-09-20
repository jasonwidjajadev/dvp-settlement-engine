package com.jasonwidjaja.dvp.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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

import com.jasonwidjaja.dvp.api.ErrorResponse;
import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.SettleCommand;
import com.jasonwidjaja.dvp.domain.SettlementOutcome;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.persistence.AccountRepository;
import com.jasonwidjaja.dvp.persistence.LockedAccount;
import com.jasonwidjaja.dvp.persistence.SettlementAttemptRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SettleTradeLockProtocolIntegrationTest extends AbstractPostgresIntegrationTest {

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
    private SettlementAttemptRepository attempts;

    @Autowired
    private RecordingTradeRepository recordingTrades;

    @Autowired
    private RecordingAccountRepository recordingAccounts;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
        recordingTrades.events().clear();
        recordingAccounts.events().clear();
        recordingAccounts.overrideBuyerCashToZero(false);
    }

    @Test
    void locksTradeThenFourSeededAccountsInAscendingIdOrder() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1(10, 100001)).orElseThrow();

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertThat(json.readValue(outcome.responseBody(), ErrorResponse.class).code())
                .isEqualTo("INSUFFICIENT_CASH");
        assertThat(recordingTrades.events()).containsExactly("lock-trade:" + trade.id());
        assertThat(recordingAccounts.events()).containsExactly(
                "resolve:" + DemoSeed.ALICE_AUD_ID,
                "resolve:" + DemoSeed.BOB_AUD_ID,
                "resolve:" + DemoSeed.ALICE_EQ1_ID,
                "resolve:" + DemoSeed.BOB_EQ1_ID,
                "lock:" + DemoSeed.ALICE_AUD_ID,
                "lock:" + DemoSeed.ALICE_EQ1_ID,
                "lock:" + DemoSeed.BOB_AUD_ID,
                "lock:" + DemoSeed.BOB_EQ1_ID);
        List<UUID> expectedLockOrder = List.of(
                DemoSeed.ALICE_AUD_ID,
                DemoSeed.ALICE_EQ1_ID,
                DemoSeed.BOB_AUD_ID,
                DemoSeed.BOB_EQ1_ID).stream().sorted().toList();
        assertThat(expectedLockOrder).containsExactly(
                DemoSeed.ALICE_AUD_ID,
                DemoSeed.ALICE_EQ1_ID,
                DemoSeed.BOB_AUD_ID,
                DemoSeed.BOB_EQ1_ID);
        assertThat(recordingAccounts.lockOrder()).containsExactlyElementsOf(expectedLockOrder);
        assertThat(trades.findById(trade.id()).orElseThrow().status()).isEqualTo(TradeStatus.READY);
    }

    @Test
    void validationUsesThePostLockBalance() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1(10, 50000)).orElseThrow();
        List<Account> before = accounts.findAll();
        recordingAccounts.overrideBuyerCashToZero(true);

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertThat(json.readValue(outcome.responseBody(), ErrorResponse.class).code())
                .isEqualTo("INSUFFICIENT_CASH");
        assertThat(attempts.findByTradeId(trade.id()).getFirst().outcome())
                .isEqualTo(SettlementOutcome.INSUFFICIENT_CASH);
        assertThat(accounts.findById(DemoSeed.ALICE_AUD_ID).orElseThrow().currentBalance()).isEqualTo(100000);
        assertThat(accounts.findAll()).containsExactlyElementsOf(before);
        assertThat(recordingAccounts.lockOrder()).isNotEmpty();
    }

    private static TradeTerms aliceBuysEq1(long quantity, long cashAmount) {
        return new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                quantity,
                cashAmount,
                LocalDate.of(2026, 9, 20));
    }

    @TestConfiguration
    static class LockProtocolConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-20T04:00:00Z"), SYDNEY);
        }

        @Bean
        @Primary
        RecordingTradeRepository recordingTradeRepository(NamedParameterJdbcTemplate jdbc) {
            return new RecordingTradeRepository(jdbc);
        }

        @Bean
        @Primary
        RecordingAccountRepository recordingAccountRepository(NamedParameterJdbcTemplate jdbc) {
            return new RecordingAccountRepository(jdbc);
        }
    }

    static class RecordingTradeRepository extends TradeRepository {

        private final List<String> events = new ArrayList<>();

        RecordingTradeRepository(NamedParameterJdbcTemplate jdbc) {
            super(jdbc);
        }

        List<String> events() {
            return events;
        }

        @Override
        public Optional<Trade> lockById(UUID tradeId) {
            events.add("lock-trade:" + tradeId);
            return super.lockById(tradeId);
        }
    }

    static class RecordingAccountRepository extends AccountRepository {

        private final List<String> events = new ArrayList<>();
        private boolean overrideBuyerCashToZero;

        RecordingAccountRepository(NamedParameterJdbcTemplate jdbc) {
            super(jdbc);
        }

        List<String> events() {
            return events;
        }

        List<UUID> lockOrder() {
            return events.stream()
                    .filter(event -> event.startsWith("lock:"))
                    .map(event -> UUID.fromString(event.substring("lock:".length())))
                    .toList();
        }

        void overrideBuyerCashToZero(boolean overrideBuyerCashToZero) {
            this.overrideBuyerCashToZero = overrideBuyerCashToZero;
        }

        @Override
        public Optional<UUID> findIdByParticipantAndAsset(UUID participantId, UUID assetId) {
            Optional<UUID> id = super.findIdByParticipantAndAsset(participantId, assetId);
            id.ifPresent(accountId -> events.add("resolve:" + accountId));
            return id;
        }

        @Override
        public Optional<LockedAccount> lockBalance(UUID accountId) {
            Optional<LockedAccount> locked = super.lockBalance(accountId);
            events.add("lock:" + accountId);
            if (overrideBuyerCashToZero && DemoSeed.ALICE_AUD_ID.equals(accountId)) {
                return locked.map(row -> new LockedAccount(row.id(), row.assetId(), 0));
            }
            return locked;
        }
    }
}
