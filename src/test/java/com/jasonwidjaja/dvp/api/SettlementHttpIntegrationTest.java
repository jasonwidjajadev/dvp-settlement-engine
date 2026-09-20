package com.jasonwidjaja.dvp.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.client.RestClient;

import com.jasonwidjaja.dvp.application.BusinessCalendar;
import com.jasonwidjaja.dvp.domain.AssetType;
import com.jasonwidjaja.dvp.domain.CaptureRequestIdentity;
import com.jasonwidjaja.dvp.domain.PostingDirection;
import com.jasonwidjaja.dvp.domain.SettleRequestIdentity;
import com.jasonwidjaja.dvp.domain.SettlementOutcome;
import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SettlementHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private CommandResultRepository commandResults;

    @Autowired
    private BusinessCalendar calendar;

    private RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void startHttpAndSeed() {
        DemoSeed.apply(dataSource);
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                })
                .build();
    }

    @Test
    void aliceBobSettlementWorkflowMovesBalancesOnceAndReplaysIdentically() {
        AccountResponse[] before = json.readValue(get("/v1/accounts").body(), AccountResponse[].class);

        HttpResponse captured = postTrade("capture-T-001", dueTrade("T-001", 10, 50000));
        assertThat(captured.status()).isEqualTo(201);
        TradeResponse ready = json.readValue(captured.body(), TradeResponse.class);
        assertThat(ready.status()).isEqualTo(TradeStatus.READY);
        assertThat(ready.journalId()).isNull();

        HttpResponse emptyAttempts = get("/v1/trades/" + ready.id() + "/attempts");
        assertThat(emptyAttempts.status()).isEqualTo(200);
        assertThat(json.readValue(emptyAttempts.body(), SettlementAttemptResponse[].class)).isEmpty();

        HttpResponse settled = postSettle(ready.id(), "settle-T-001");
        assertThat(settled.status()).isEqualTo(201);
        SettlementResponse settleBody = json.readValue(settled.body(), SettlementResponse.class);
        assertThat(settleBody.status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(settleBody.outcome()).isEqualTo(SettlementOutcome.SETTLED);
        assertThat(settleBody.tradeId()).isEqualTo(ready.id());
        assertThat(settled.location()).isEqualTo("/v1/journals/" + settleBody.journalId());

        HttpResponse journal = get(settled.location());
        assertThat(journal.status()).isEqualTo(200);
        JournalResponse journalBody = json.readValue(journal.body(), JournalResponse.class);
        assertThat(journalBody.id()).isEqualTo(settleBody.journalId());
        assertThat(journalBody.tradeId()).isEqualTo(ready.id());
        assertThat(journalBody.settledAt()).isCloseTo(settleBody.settledAt(), within(1, ChronoUnit.MILLIS));
        assertThat(journal.body()).doesNotContain("signedAmount");
        assertThat(journalBody.postings()).hasSize(4);
        assertThat(journalBody.postings())
                .extracting(
                        JournalResponse.PostingResponse::accountId,
                        posting -> posting.participant().id(),
                        posting -> posting.participant().name(),
                        posting -> posting.asset().id(),
                        posting -> posting.asset().code(),
                        posting -> posting.asset().type(),
                        JournalResponse.PostingResponse::direction,
                        JournalResponse.PostingResponse::amount)
                .containsExactly(
                        tuple(DemoSeed.BOB_AUD_ID, DemoSeed.BOB_ID, "Bob", DemoSeed.AUD_ID, "AUD", AssetType.CASH,
                                PostingDirection.CREDIT, 50000L),
                        tuple(DemoSeed.ALICE_AUD_ID, DemoSeed.ALICE_ID, "Alice", DemoSeed.AUD_ID, "AUD", AssetType.CASH,
                                PostingDirection.DEBIT, 50000L),
                        tuple(DemoSeed.ALICE_EQ1_ID, DemoSeed.ALICE_ID, "Alice", DemoSeed.EQ1_ID, "EQ1", AssetType.SECURITY,
                                PostingDirection.CREDIT, 10L),
                        tuple(DemoSeed.BOB_EQ1_ID, DemoSeed.BOB_ID, "Bob", DemoSeed.EQ1_ID, "EQ1", AssetType.SECURITY,
                                PostingDirection.DEBIT, 10L));

        HttpResponse trade = get("/v1/trades/" + ready.id());
        assertThat(trade.status()).isEqualTo(200);
        TradeResponse settledTrade = json.readValue(trade.body(), TradeResponse.class);
        assertThat(settledTrade.status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(settledTrade.journalId()).isEqualTo(settleBody.journalId());
        assertThat(settledTrade.externalTradeId()).isEqualTo("T-001");
        assertThat(settledTrade.quantity()).isEqualTo(10);
        assertThat(settledTrade.cashAmount()).isEqualTo(50000);

        HttpResponse attempts = get("/v1/trades/" + ready.id() + "/attempts");
        assertThat(attempts.status()).isEqualTo(200);
        SettlementAttemptResponse[] attemptBodies =
                json.readValue(attempts.body(), SettlementAttemptResponse[].class);
        assertThat(attemptBodies).hasSize(1);
        assertThat(attemptBodies[0].outcome()).isEqualTo(SettlementOutcome.SETTLED);
        assertThat(attemptBodies[0].journalId()).isEqualTo(settleBody.journalId());
        assertThat(attemptBodies[0].businessDate()).isEqualTo(calendar.businessDate());
        assertThat(attemptBodies[0].commandKey()).isEqualTo("settle-T-001");
        assertThat(attemptBodies[0].decidedAt()).isNotNull();

        HttpResponse command = getCommand("settle-T-001");
        assertThat(command.status()).isEqualTo(200);
        CommandResultResponse commandBody = json.readValue(command.body(), CommandResultResponse.class);
        assertThat(commandBody.commandKey()).isEqualTo("settle-T-001");
        assertThat(commandBody.operation()).isEqualTo(SettleRequestIdentity.OPERATION);
        assertThat(commandBody.httpStatus()).isEqualTo(201);
        assertThat(commandBody.location()).isEqualTo(settled.location());
        assertThat(commandBody.response()).isEqualTo(json.readTree(settled.body()));
        assertThat(command.body()).doesNotContain("requestIdentity");
        assertThat(command.body()).doesNotContain("request_identity");

        AccountResponse[] after = json.readValue(get("/v1/accounts").body(), AccountResponse[].class);
        assertThat(balancesOf(after)).containsExactlyInAnyOrder(
                new Balance(DemoSeed.ALICE_AUD_ID, 100000, 50000),
                new Balance(DemoSeed.ALICE_EQ1_ID, 0, 10),
                new Balance(DemoSeed.BOB_AUD_ID, 0, 50000),
                new Balance(DemoSeed.BOB_EQ1_ID, 10, 0));
        assertThat(after).allSatisfy(account ->
                assertThat(account.openingBalance()).isEqualTo(
                        openingBalance(before, account.id())));

        HttpResponse replay = postSettle(ready.id(), "settle-T-001");
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(settled.body());
        assertThat(replay.location()).isEqualTo(settled.location());
        assertThat(json.readValue(get("/v1/accounts").body(), AccountResponse[].class)).containsExactly(after);
        assertThat(count("settlement_journal")).isEqualTo(1);
        assertThat(count("posting")).isEqualTo(4);
        assertThat(json.readValue(get("/v1/trades/" + ready.id() + "/attempts").body(), SettlementAttemptResponse[].class))
                .hasSize(1);
    }

    @Test
    void settleRequestRulesRejectBadKeysBodyAndMalformedId() {
        TradeResponse trade = json.readValue(postTrade("capture-T-001", dueTrade("T-001", 10, 50000)).body(), TradeResponse.class);

        HttpResponse missingKey = postSettle(trade.id(), null);
        assertThat(missingKey.status()).isEqualTo(400);
        assertThat(json.readValue(missingKey.body(), ErrorResponse.class).code()).isEqualTo("INVALID_IDEMPOTENCY_KEY");
        assertSafeError(json.readValue(missingKey.body(), ErrorResponse.class));

        HttpResponse emptyKey = postSettle(trade.id(), "");
        assertThat(emptyKey.status()).isEqualTo(400);
        assertThat(json.readValue(emptyKey.body(), ErrorResponse.class).code()).isEqualTo("INVALID_IDEMPOTENCY_KEY");

        HttpResponse tooLong = postSettle(trade.id(), "k".repeat(129));
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(json.readValue(tooLong.body(), ErrorResponse.class).code()).isEqualTo("INVALID_IDEMPOTENCY_KEY");

        HttpResponse duplicateKeys = post(
                "/v1/trades/" + trade.id() + "/settle",
                List.of("key-A", "key-B"),
                null,
                null);
        assertThat(duplicateKeys.status()).isEqualTo(400);
        assertThat(json.readValue(duplicateKeys.body(), ErrorResponse.class).code()).isEqualTo("INVALID_IDEMPOTENCY_KEY");

        HttpResponse nonEmptyBody = post(
                "/v1/trades/" + trade.id() + "/settle",
                List.of("settle-body"),
                MediaType.APPLICATION_JSON,
                "{}");
        assertThat(nonEmptyBody.status()).isEqualTo(400);
        ErrorResponse invalid = json.readValue(nonEmptyBody.body(), ErrorResponse.class);
        assertThat(invalid.code()).isEqualTo("INVALID_REQUEST");
        assertSafeError(invalid);

        HttpResponse malformed = post(
                "/v1/trades/not-a-uuid/settle",
                List.of("settle-malformed"),
                null,
                null);
        assertThat(malformed.status()).isEqualTo(400);
        ErrorResponse malformedError = json.readValue(malformed.body(), ErrorResponse.class);
        assertThat(malformedError.code()).isEqualTo("INVALID_REQUEST");
        assertSafeError(malformedError);

        assertThat(json.readValue(get("/v1/trades/" + trade.id()).body(), TradeResponse.class).status())
                .isEqualTo(TradeStatus.READY);
        assertThat(count("settlement_journal")).isZero();
        assertThat(count("posting")).isZero();
        assertThat(commandResults.findByCommandKey("settle-body")).isEmpty();
        assertThat(commandResults.findByCommandKey("settle-malformed")).isEmpty();
    }

    @Test
    void rejectedSettlementsLeaveFinancialStateUnchanged() {
        AccountResponse[] opening = json.readValue(get("/v1/accounts").body(), AccountResponse[].class);
        TradeResponse first = json.readValue(postTrade("capture-T-001", dueTrade("T-001", 10, 50000)).body(), TradeResponse.class);
        HttpResponse settled = postSettle(first.id(), "settle-T-001");
        assertThat(settled.status()).isEqualTo(201);
        AccountResponse[] afterSettle = json.readValue(get("/v1/accounts").body(), AccountResponse[].class);

        HttpResponse alreadySettled = postSettle(first.id(), "settle-T-001-again");
        assertThat(alreadySettled.status()).isEqualTo(409);
        ErrorResponse alreadySettledError = json.readValue(alreadySettled.body(), ErrorResponse.class);
        assertThat(alreadySettledError.code()).isEqualTo("ALREADY_SETTLED");
        assertSafeError(alreadySettledError);
        assertUnmovedAfter(afterSettle, first.id(), 1, 4);

        TradeResponse notDue = json.readValue(
                postTrade("capture-T-002", tradeJson("T-002", 10, 50000, calendar.businessDate().plusDays(1))).body(),
                TradeResponse.class);
        HttpResponse notDueResponse = postSettle(notDue.id(), "settle-not-due");
        assertThat(notDueResponse.status()).isEqualTo(422);
        ErrorResponse notDueError = json.readValue(notDueResponse.body(), ErrorResponse.class);
        assertThat(notDueError.code()).isEqualTo("NOT_DUE");
        assertSafeError(notDueError);
        SettlementAttemptResponse[] notDueAttempts =
                json.readValue(get("/v1/trades/" + notDue.id() + "/attempts").body(), SettlementAttemptResponse[].class);
        assertThat(notDueAttempts).extracting(SettlementAttemptResponse::outcome)
                .containsExactly(SettlementOutcome.NOT_DUE);
        assertThat(notDueAttempts[0].businessDate()).isEqualTo(calendar.businessDate());
        assertThat(notDueAttempts[0].journalId()).isNull();
        assertThat(json.readValue(get("/v1/trades/" + notDue.id()).body(), TradeResponse.class).status())
                .isEqualTo(TradeStatus.READY);
        assertUnmovedAfter(afterSettle, first.id(), 1, 4);

        TradeResponse cashShort = json.readValue(
                postTrade("capture-T-003", dueTrade("T-003", 10, 100001)).body(), TradeResponse.class);
        HttpResponse insufficientCash = postSettle(cashShort.id(), "settle-cash-short");
        assertThat(insufficientCash.status()).isEqualTo(422);
        assertThat(json.readValue(insufficientCash.body(), ErrorResponse.class).code()).isEqualTo("INSUFFICIENT_CASH");
        assertSafeError(json.readValue(insufficientCash.body(), ErrorResponse.class));
        assertThat(json.readValue(get("/v1/trades/" + cashShort.id() + "/attempts").body(), SettlementAttemptResponse[].class))
                .extracting(SettlementAttemptResponse::outcome)
                .containsExactly(SettlementOutcome.INSUFFICIENT_CASH);
        assertUnmovedAfter(afterSettle, first.id(), 1, 4);

        TradeResponse securityShort = json.readValue(
                postTrade("capture-T-004", dueTrade("T-004", 11, 50000)).body(), TradeResponse.class);
        HttpResponse insufficientSecurities = postSettle(securityShort.id(), "settle-sec-short");
        assertThat(insufficientSecurities.status()).isEqualTo(422);
        assertThat(json.readValue(insufficientSecurities.body(), ErrorResponse.class).code())
                .isEqualTo("INSUFFICIENT_SECURITIES");
        assertSafeError(json.readValue(insufficientSecurities.body(), ErrorResponse.class));
        assertThat(json.readValue(get("/v1/trades/" + securityShort.id() + "/attempts").body(), SettlementAttemptResponse[].class))
                .extracting(SettlementAttemptResponse::outcome)
                .containsExactly(SettlementOutcome.INSUFFICIENT_SECURITIES);
        assertUnmovedAfter(afterSettle, first.id(), 1, 4);

        UUID unknownId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        HttpResponse unknownTrade = postSettle(unknownId, "settle-unknown");
        assertThat(unknownTrade.status()).isEqualTo(404);
        ErrorResponse unknown = json.readValue(unknownTrade.body(), ErrorResponse.class);
        assertThat(unknown.code()).isEqualTo("UNKNOWN_TRADE");
        assertSafeError(unknown);
        assertThat(commandResults.findByCommandKey("settle-unknown")).isEmpty();
        assertUnmovedAfter(afterSettle, first.id(), 1, 4);

        TradeResponse second = json.readValue(postTrade("capture-T-005", dueTrade("T-005", 10, 50000)).body(), TradeResponse.class);
        HttpResponse reusedKey = postSettle(second.id(), "settle-T-001");
        assertThat(reusedKey.status()).isEqualTo(409);
        ErrorResponse conflict = json.readValue(reusedKey.body(), ErrorResponse.class);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertSafeError(conflict);
        assertThat(json.readValue(get("/v1/trades/" + second.id()).body(), TradeResponse.class).status())
                .isEqualTo(TradeStatus.READY);
        assertThat(json.readValue(get("/v1/trades/" + second.id() + "/attempts").body(), SettlementAttemptResponse[].class))
                .isEmpty();
        assertUnmovedAfter(afterSettle, first.id(), 1, 4);

        assertThat(balancesOf(json.readValue(get("/v1/accounts").body(), AccountResponse[].class)))
                .isNotEqualTo(balancesOf(opening));
    }

    @Test
    void commandInspectionReturnsStoredOutcomesWithoutRequestIdentity() {
        HttpResponse captured = postTrade("capture-T-001", dueTrade("T-001", 10, 50000));
        TradeResponse trade = json.readValue(captured.body(), TradeResponse.class);

        HttpResponse captureCommand = getCommand("capture-T-001");
        assertThat(captureCommand.status()).isEqualTo(200);
        CommandResultResponse captureBody = json.readValue(captureCommand.body(), CommandResultResponse.class);
        assertThat(captureBody.commandKey()).isEqualTo("capture-T-001");
        assertThat(captureBody.operation()).isEqualTo(CaptureRequestIdentity.OPERATION);
        assertThat(captureBody.httpStatus()).isEqualTo(201);
        assertThat(captureBody.location()).isEqualTo("/v1/trades/" + trade.id());
        assertThat(captureBody.response()).isEqualTo(json.readTree(captured.body()));
        assertThat(captureBody.response()).isEqualTo(json.readTree(
                commandResults.findByCommandKey("capture-T-001").orElseThrow().responseBody()));
        assertThat(captureCommand.body()).doesNotContain("requestIdentity");
        assertThat(captureCommand.body()).doesNotContain("request_identity");

        HttpResponse settled = postSettle(trade.id(), "settle-T-001");
        HttpResponse settleCommand = getCommand("settle-T-001");
        assertThat(settleCommand.status()).isEqualTo(200);
        CommandResultResponse settleBody = json.readValue(settleCommand.body(), CommandResultResponse.class);
        assertThat(settleBody.httpStatus()).isEqualTo(201);
        assertThat(settleBody.location()).isEqualTo(settled.location());
        assertThat(settleBody.response()).isEqualTo(json.readTree(settled.body()));

        HttpResponse alreadySettled = postSettle(trade.id(), "settle-again");
        HttpResponse rejectionCommand = getCommand("settle-again");
        assertThat(rejectionCommand.status()).isEqualTo(200);
        CommandResultResponse rejectionBody = json.readValue(rejectionCommand.body(), CommandResultResponse.class);
        assertThat(rejectionBody.httpStatus()).isEqualTo(409);
        assertThat(rejectionBody.location()).isNull();
        assertThat(rejectionBody.response()).isEqualTo(json.readTree(alreadySettled.body()));
        assertThat(rejectionBody.response().get("code").stringValue()).isEqualTo("ALREADY_SETTLED");

        HttpResponse unknown = getCommand("no-such-command");
        assertThat(unknown.status()).isEqualTo(404);
        ErrorResponse unknownError = json.readValue(unknown.body(), ErrorResponse.class);
        assertThat(unknownError.code()).isEqualTo("UNKNOWN_COMMAND");
        assertThat(unknownError.message()).isEqualTo("Command does not exist");
        assertSafeError(unknownError);

        HttpResponse tooLong = get("/v1/commands/" + "k".repeat(129));
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(json.readValue(tooLong.body(), ErrorResponse.class).code()).isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    void journalAndAttemptInspectionRejectUnknownAndMalformedIds() {
        HttpResponse unknownJournal = get("/v1/journals/" + UUID.fromString("00000000-0000-0000-0000-000000000201"));
        assertThat(unknownJournal.status()).isEqualTo(404);
        ErrorResponse missingJournal = json.readValue(unknownJournal.body(), ErrorResponse.class);
        assertThat(missingJournal.code()).isEqualTo("UNKNOWN_JOURNAL");
        assertThat(missingJournal.message()).isEqualTo("Journal does not exist");
        assertSafeError(missingJournal);

        HttpResponse malformedJournal = get("/v1/journals/not-a-uuid");
        assertThat(malformedJournal.status()).isEqualTo(400);
        assertThat(json.readValue(malformedJournal.body(), ErrorResponse.class).code()).isEqualTo("INVALID_REQUEST");

        HttpResponse unknownAttempts = get("/v1/trades/" + UUID.fromString("00000000-0000-0000-0000-000000000101") + "/attempts");
        assertThat(unknownAttempts.status()).isEqualTo(404);
        ErrorResponse missingTrade = json.readValue(unknownAttempts.body(), ErrorResponse.class);
        assertThat(missingTrade.code()).isEqualTo("UNKNOWN_TRADE");
        assertSafeError(missingTrade);

        HttpResponse malformedAttempts = get("/v1/trades/not-a-uuid/attempts");
        assertThat(malformedAttempts.status()).isEqualTo(400);
        assertThat(json.readValue(malformedAttempts.body(), ErrorResponse.class).code()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void missingRequiredAccountIsSafeInternalError() {
        TradeResponse trade = json.readValue(postTrade("capture-T-001", dueTrade("T-001", 10, 50000)).body(), TradeResponse.class);
        jdbc.update("DELETE FROM account WHERE id = :id", Map.of("id", DemoSeed.ALICE_AUD_ID));

        HttpResponse response = postSettle(trade.id(), "settle-missing-account");

        assertThat(response.status()).isEqualTo(500);
        ErrorResponse error = json.readValue(response.body(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.message()).isEqualTo("An unexpected error occurred");
        assertSafeError(error);
        assertThat(error.message()).doesNotContain("buyer cash");
        assertThat(commandResults.findByCommandKey("settle-missing-account")).isEmpty();
        assertThat(count("settlement_journal")).isZero();
        assertThat(count("posting")).isZero();
    }

    @Test
    void generatedOpenApiIncludesPhase3Endpoints() {
        HttpResponse apiDocs = get("/v3/api-docs");
        assertThat(apiDocs.status()).isEqualTo(200);
        assertThat(apiDocs.body())
                .contains("\"/v1/trades/{id}/settle\"")
                .contains("\"/v1/journals/{id}\"")
                .contains("\"/v1/trades/{id}/attempts\"")
                .contains("\"/v1/commands/{key}\"");
        assertThat(apiDocs.body()).doesNotContain("@Operation");
    }

    private void assertUnmovedAfter(AccountResponse[] expectedAccounts, UUID settledTradeId, long journals, long postings) {
        assertThat(json.readValue(get("/v1/accounts").body(), AccountResponse[].class)).containsExactly(expectedAccounts);
        assertThat(count("settlement_journal")).isEqualTo(journals);
        assertThat(count("posting")).isEqualTo(postings);
        assertThat(json.readValue(get("/v1/trades/" + settledTradeId).body(), TradeResponse.class).status())
                .isEqualTo(TradeStatus.SETTLED);
    }

    private HttpResponse postTrade(String idempotencyKey, String body) {
        return post("/v1/trades", List.of(idempotencyKey), MediaType.APPLICATION_JSON, body);
    }

    private HttpResponse postSettle(UUID tradeId, String idempotencyKey) {
        List<String> keys = idempotencyKey == null ? List.of() : List.of(idempotencyKey);
        return post("/v1/trades/" + tradeId + "/settle", keys, null, null);
    }

    private HttpResponse post(String path, List<String> idempotencyKeys, MediaType contentType, String body) {
        var spec = http.post().uri(path).headers(headers ->
                idempotencyKeys.forEach(key -> headers.add("Idempotency-Key", key)));
        if (contentType != null) {
            spec = spec.contentType(contentType);
        }
        if (body != null) {
            spec = spec.body(body);
        }
        return spec.exchange((request, response) -> new HttpResponse(
                response.getStatusCode().value(),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                response.getHeaders().getFirst("Location")));
    }

    private HttpResponse get(String path) {
        return http.get()
                .uri(path)
                .exchange((request, response) -> new HttpResponse(
                        response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                        response.getHeaders().getFirst("Location")));
    }

    private HttpResponse getCommand(String key) {
        return http.get()
                .uri("/v1/commands/{key}", key)
                .exchange((request, response) -> new HttpResponse(
                        response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                        response.getHeaders().getFirst("Location")));
    }

    private String dueTrade(String externalTradeId, long quantity, long cashAmount) {
        return tradeJson(externalTradeId, quantity, cashAmount, calendar.businessDate());
    }

    private static String tradeJson(String externalTradeId, long quantity, long cashAmount, LocalDate settlementDate) {
        return """
                {
                  "externalTradeId": "%s",
                  "buyerId": "00000000-0000-0000-0000-000000000001",
                  "sellerId": "00000000-0000-0000-0000-000000000002",
                  "securityId": "00000000-0000-0000-0000-0000000000e1",
                  "quantity": %d,
                  "cashAmount": %d,
                  "settlementDate": "%s"
                }
                """.formatted(externalTradeId, quantity, cashAmount, settlementDate);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Map.of(), Long.class);
    }

    private static void assertSafeError(ErrorResponse error) {
        assertThat(error.message())
                .doesNotContainIgnoringCase("sql")
                .doesNotContainIgnoringCase("postgresql")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("jdbc")
                .doesNotContainIgnoringCase("constraint")
                .doesNotContainIgnoringCase("trigger")
                .doesNotContain("Exception")
                .doesNotContain("settlement_journal_shape");
    }

    private static long openingBalance(AccountResponse[] accounts, UUID id) {
        return java.util.Arrays.stream(accounts)
                .filter(account -> account.id().equals(id))
                .findFirst()
                .orElseThrow()
                .openingBalance();
    }

    private static List<Balance> balancesOf(AccountResponse[] accounts) {
        return java.util.Arrays.stream(accounts)
                .map(account -> new Balance(account.id(), account.openingBalance(), account.currentBalance()))
                .toList();
    }

    private record HttpResponse(int status, String body, String location) {
    }

    private record Balance(UUID id, long openingBalance, long currentBalance) {
    }
}
