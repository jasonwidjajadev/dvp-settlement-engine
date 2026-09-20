package com.jasonwidjaja.dvp.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import com.jasonwidjaja.dvp.api.ErrorResponse;
import com.jasonwidjaja.dvp.api.TradeResponse;
import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.CaptureCommand;
import com.jasonwidjaja.dvp.domain.CommandResult;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.persistence.AccountRepository;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureTradeServiceIntegrationTest extends AbstractPostgresIntegrationTest {

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

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
    }

    @Test
    void serviceUsesJdbcTransactionManagerForTheApplicationDataSource() {
        PlatformTransactionManager transactionManager = capture.transactionManager();
        assertThat(transactionManager).isInstanceOf(JdbcTransactionManager.class);
        assertThat(((JdbcTransactionManager) transactionManager).getDataSource()).isSameAs(dataSource);
    }

    @Test
    void capturesValidTradeAsReadyWithoutChangingBalances() {
        List<Account> before = accounts.findAll();

        CommandOutcome outcome = capture.capture(new CaptureCommand("capture-T-001", aliceBuysEq1(10)));

        assertThat(outcome.httpStatus()).isEqualTo(201);
        TradeResponse body = json.readValue(outcome.responseBody(), TradeResponse.class);
        assertThat(body.status()).isEqualTo(TradeStatus.READY);
        assertThat(body.externalTradeId()).isEqualTo("T-001");
        assertThat(body.quantity()).isEqualTo(10);
        assertThat(body.cashAmount()).isEqualTo(50000);
        assertThat(outcome.location()).isEqualTo("/v1/trades/" + body.id());

        List<Trade> storedTrades = List.of(trades.findByExternalTradeId("T-001").orElseThrow());
        assertThat(storedTrades).hasSize(1);
        assertThat(storedTrades.getFirst().status()).isEqualTo(TradeStatus.READY);
        CommandResult storedResult = commandResults.findByCommandKey("capture-T-001").orElseThrow();
        assertThat(storedResult.completed()).isTrue();
        assertThat(storedResult.httpStatus()).isEqualTo(201);
        assertThat(accounts.findAll()).containsExactlyElementsOf(before);
    }

    @Test
    void sameKeyAndSameRequestReplaysTheSavedOutcome() {
        CommandOutcome first = capture.capture(new CaptureCommand("capture-T-001", aliceBuysEq1(10)));
        CommandOutcome replay = capture.capture(new CaptureCommand("capture-T-001", aliceBuysEq1(10)));

        assertThat(replay).isEqualTo(first);
        assertThat(trades.findByExternalTradeId("T-001")).isPresent();
        assertThat(commandResults.findByCommandKey("capture-T-001")).hasValueSatisfying(result -> {
            assertThat(result.httpStatus()).isEqualTo(201);
            assertThat(result.responseBody()).isEqualTo(first.responseBody());
        });
    }

    @Test
    void sameKeyAndChangedRequestIsConflictWithoutOverwriting() {
        CommandOutcome first = capture.capture(new CaptureCommand("capture-T-001", aliceBuysEq1(10)));

        CommandOutcome conflict = capture.capture(new CaptureCommand("capture-T-001", aliceBuysEq1(11)));

        assertThat(conflict.httpStatus()).isEqualTo(409);
        ErrorResponse error = json.readValue(conflict.responseBody(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(conflict.location()).isNull();

        CommandResult stored = commandResults.findByCommandKey("capture-T-001").orElseThrow();
        assertThat(stored.httpStatus()).isEqualTo(201);
        assertThat(stored.responseBody()).isEqualTo(first.responseBody());
        assertThat(trades.findByExternalTradeId("T-001").orElseThrow().terms().quantity()).isEqualTo(10);
    }

    @Test
    void newKeySameTradeReturnsExistingTrade() {
        CommandOutcome first = capture.capture(new CaptureCommand("key-A", aliceBuysEq1(10)));
        CommandOutcome second = capture.capture(new CaptureCommand("key-B", aliceBuysEq1(10)));

        assertThat(second.httpStatus()).isEqualTo(200);
        TradeResponse firstBody = json.readValue(first.responseBody(), TradeResponse.class);
        TradeResponse secondBody = json.readValue(second.responseBody(), TradeResponse.class);
        assertThat(secondBody.id()).isEqualTo(firstBody.id());
        assertThat(second.location()).isEqualTo(first.location());
        assertThat(commandResults.findByCommandKey("key-A")).isPresent();
        assertThat(commandResults.findByCommandKey("key-B")).hasValueSatisfying(result ->
                assertThat(result.httpStatus()).isEqualTo(200));
    }

    @Test
    void newKeySameReferenceDifferentTermsIsConflict() {
        capture.capture(new CaptureCommand("key-A", aliceBuysEq1(10)));

        CommandOutcome conflict = capture.capture(new CaptureCommand("key-C", aliceBuysEq1(11)));

        assertThat(conflict.httpStatus()).isEqualTo(409);
        ErrorResponse error = json.readValue(conflict.responseBody(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("TRADE_REFERENCE_CONFLICT");
        assertThat(trades.findByExternalTradeId("T-001").orElseThrow().terms().quantity()).isEqualTo(10);
        assertThat(commandResults.findByCommandKey("key-C")).hasValueSatisfying(result ->
                assertThat(result.httpStatus()).isEqualTo(409));
    }

    @Test
    void unknownBuyerIsDurableRejection() {
        TradeTerms unknownBuyer = new TradeTerms(
                "T-001",
                DemoSeed.UNKNOWN_ACCOUNT_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 20));

        CommandOutcome first = capture.capture(new CaptureCommand("capture-unknown-buyer", unknownBuyer));
        CommandOutcome replay = capture.capture(new CaptureCommand("capture-unknown-buyer", unknownBuyer));

        assertThat(first.httpStatus()).isEqualTo(422);
        assertThat(json.readValue(first.responseBody(), ErrorResponse.class).code()).isEqualTo("UNKNOWN_PARTICIPANT");
        assertThat(replay).isEqualTo(first);
        assertThat(trades.findByExternalTradeId("T-001")).isEmpty();
        assertThat(commandResults.findByCommandKey("capture-unknown-buyer")).hasValueSatisfying(CommandResult::completed);
    }

    @Test
    void unknownSellerIsRejected() {
        TradeTerms unknownSeller = new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.UNKNOWN_ACCOUNT_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 20));

        CommandOutcome outcome = capture.capture(new CaptureCommand("capture-unknown-seller", unknownSeller));

        assertThat(outcome.httpStatus()).isEqualTo(422);
        assertThat(json.readValue(outcome.responseBody(), ErrorResponse.class).message()).isEqualTo("Seller does not exist");
        assertThat(trades.findByExternalTradeId("T-001")).isEmpty();
    }

    @Test
    void unknownSecurityIsRejected() {
        TradeTerms unknownSecurity = new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                10,
                50000,
                LocalDate.of(2026, 9, 20));

        CommandOutcome outcome = capture.capture(new CaptureCommand("capture-unknown-security", unknownSecurity));

        assertThat(outcome.httpStatus()).isEqualTo(422);
        assertThat(json.readValue(outcome.responseBody(), ErrorResponse.class).code()).isEqualTo("UNKNOWN_SECURITY");
        assertThat(trades.findByExternalTradeId("T-001")).isEmpty();
    }

    @Test
    void cashAssetUsedAsSecurityIsRejected() {
        TradeTerms cashAsSecurity = new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.AUD_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 20));

        CommandOutcome outcome = capture.capture(new CaptureCommand("capture-cash-security", cashAsSecurity));

        assertThat(outcome.httpStatus()).isEqualTo(422);
        assertThat(json.readValue(outcome.responseBody(), ErrorResponse.class).code()).isEqualTo("INVALID_SECURITY");
        assertThat(trades.findByExternalTradeId("T-001")).isEmpty();
    }

    @Test
    void selfTradeIsRejected() {
        TradeTerms selfTrade = new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.ALICE_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 20));

        CommandOutcome outcome = capture.capture(new CaptureCommand("capture-self-trade", selfTrade));

        assertThat(outcome.httpStatus()).isEqualTo(422);
        assertThat(json.readValue(outcome.responseBody(), ErrorResponse.class).code()).isEqualTo("SELF_TRADE");
        assertThat(trades.findByExternalTradeId("T-001")).isEmpty();
    }

    private static TradeTerms aliceBuysEq1(long quantity) {
        return new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                quantity,
                50000,
                LocalDate.of(2026, 9, 20));
    }
}
