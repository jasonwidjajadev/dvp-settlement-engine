package com.jasonwidjaja.dvp.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeCaptureHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String VALID_BODY = """
            {
              "externalTradeId": "T-001",
              "buyerId": "00000000-0000-0000-0000-000000000001",
              "sellerId": "00000000-0000-0000-0000-000000000002",
              "securityId": "00000000-0000-0000-0000-0000000000e1",
              "quantity": 10,
              "cashAmount": 50000,
              "settlementDate": "2026-09-20"
            }
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TradeRepository trades;

    @Autowired
    private CommandResultRepository commandResults;

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
    void postCapturesReadyTradeAndGetReadsTheSameTerms() {
        HttpResponse created = postTrade("capture-T-001", VALID_BODY);

        assertThat(created.status()).isEqualTo(201);
        TradeResponse body = json.readValue(created.body(), TradeResponse.class);
        assertThat(body.status()).isEqualTo(TradeStatus.READY);
        assertThat(body.journalId()).isNull();
        assertThat(body.externalTradeId()).isEqualTo("T-001");
        assertThat(body.buyerId()).isEqualTo(DemoSeed.ALICE_ID);
        assertThat(body.sellerId()).isEqualTo(DemoSeed.BOB_ID);
        assertThat(body.securityId()).isEqualTo(DemoSeed.EQ1_ID);
        assertThat(body.quantity()).isEqualTo(10);
        assertThat(body.cashAmount()).isEqualTo(50000);
        assertThat(body.settlementDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(created.location()).isEqualTo("/v1/trades/" + body.id());
        assertThat(trades.findByExternalTradeId("T-001")).isPresent();

        HttpResponse read = get(created.location());
        assertThat(read.status()).isEqualTo(200);
        TradeResponse stored = json.readValue(read.body(), TradeResponse.class);
        assertThat(stored).isEqualTo(body);
    }

    @Test
    void captureDoesNotChangeAccountBalances() {
        AccountResponse[] before = json.readValue(get("/v1/accounts").body(), AccountResponse[].class);

        HttpResponse created = postTrade("capture-T-001", VALID_BODY);
        assertThat(created.status()).isEqualTo(201);

        AccountResponse[] after = json.readValue(get("/v1/accounts").body(), AccountResponse[].class);
        assertThat(after).containsExactly(before);
        assertThat(after).allSatisfy(account ->
                assertThat(account.currentBalance()).isEqualTo(account.openingBalance()));
        assertThat(balancesOf(after)).containsExactlyInAnyOrder(
                new Balance(DemoSeed.ALICE_AUD_ID, 100000, 100000),
                new Balance(DemoSeed.ALICE_EQ1_ID, 0, 0),
                new Balance(DemoSeed.BOB_AUD_ID, 0, 0),
                new Balance(DemoSeed.BOB_EQ1_ID, 10, 10));
    }

    @Test
    void retryingTheSamePostReplaysOneReadyTrade() {
        HttpResponse first = postTrade("capture-T-001", VALID_BODY);
        HttpResponse replay = postTrade("capture-T-001", VALID_BODY);

        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(replay.location()).isEqualTo(first.location());
        assertThat(trades.findByExternalTradeId("T-001")).isPresent();
        assertThat(commandResults.findByCommandKey("capture-T-001")).hasValueSatisfying(result ->
                assertThat(result.httpStatus()).isEqualTo(201));
    }

    @Test
    void newKeySameTermsReturnsExistingTrade() {
        HttpResponse first = postTrade("key-A", VALID_BODY);
        HttpResponse second = postTrade("key-B", VALID_BODY);

        assertThat(second.status()).isEqualTo(200);
        TradeResponse firstBody = json.readValue(first.body(), TradeResponse.class);
        TradeResponse secondBody = json.readValue(second.body(), TradeResponse.class);
        assertThat(secondBody.id()).isEqualTo(firstBody.id());
        assertThat(second.location()).isEqualTo(first.location());
    }

    @Test
    void sameKeyChangedRequestIsIdempotencyConflict() {
        HttpResponse first = postTrade("capture-T-001", VALID_BODY);
        HttpResponse conflict = postTrade("capture-T-001", quantityJson(11));

        assertThat(conflict.status()).isEqualTo(409);
        ErrorResponse error = json.readValue(conflict.body(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertSafeError(error);
        assertThat(trades.findByExternalTradeId("T-001").orElseThrow().terms().quantity()).isEqualTo(10);
        assertThat(commandResults.findByCommandKey("capture-T-001")).hasValueSatisfying(result ->
                assertThat(result.responseBody()).isEqualTo(first.body()));
    }

    @Test
    void newKeyDifferentTermsIsTradeReferenceConflict() {
        postTrade("key-A", VALID_BODY);
        HttpResponse conflict = postTrade("key-C", quantityJson(11));

        assertThat(conflict.status()).isEqualTo(409);
        ErrorResponse error = json.readValue(conflict.body(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("TRADE_REFERENCE_CONFLICT");
        assertSafeError(error);
        assertThat(trades.findByExternalTradeId("T-001").orElseThrow().terms().quantity()).isEqualTo(10);
        assertThat(commandResults.findByCommandKey("key-C")).hasValueSatisfying(result ->
                assertThat(result.httpStatus()).isEqualTo(409));
    }

    @Test
    void unknownBuyerIsDurableUnprocessableContent() {
        String unknownBuyer = """
                {
                  "externalTradeId": "T-001",
                  "buyerId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
                  "sellerId": "00000000-0000-0000-0000-000000000002",
                  "securityId": "00000000-0000-0000-0000-0000000000e1",
                  "quantity": 10,
                  "cashAmount": 50000,
                  "settlementDate": "2026-09-20"
                }
                """;

        HttpResponse first = postTrade("capture-unknown-buyer", unknownBuyer);
        HttpResponse replay = postTrade("capture-unknown-buyer", unknownBuyer);

        assertThat(first.status()).isEqualTo(422);
        ErrorResponse error = json.readValue(first.body(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("UNKNOWN_PARTICIPANT");
        assertSafeError(error);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(trades.findByExternalTradeId("T-001")).isEmpty();
        assertThat(commandResults.findByCommandKey("capture-unknown-buyer")).isPresent();
    }

    @Test
    void selfTradeIsRejectedThroughHttp() {
        String selfTrade = """
                {
                  "externalTradeId": "T-001",
                  "buyerId": "00000000-0000-0000-0000-000000000001",
                  "sellerId": "00000000-0000-0000-0000-000000000001",
                  "securityId": "00000000-0000-0000-0000-0000000000e1",
                  "quantity": 10,
                  "cashAmount": 50000,
                  "settlementDate": "2026-09-20"
                }
                """;

        HttpResponse response = postTrade("capture-self-trade", selfTrade);

        assertThat(response.status()).isEqualTo(422);
        assertThat(json.readValue(response.body(), ErrorResponse.class).code()).isEqualTo("SELF_TRADE");
        assertThat(trades.findByExternalTradeId("T-001")).isEmpty();
    }

    @Test
    void requestLevelFailuresAreNotDurable() {
        assertThat(postTrade(null, VALID_BODY).status()).isEqualTo(400);
        assertThat(json.readValue(postTrade(null, VALID_BODY).body(), ErrorResponse.class).code())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");

        HttpResponse emptyKey = postTrade("", VALID_BODY);
        assertThat(emptyKey.status()).isEqualTo(400);
        assertThat(json.readValue(emptyKey.body(), ErrorResponse.class).code())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");

        HttpResponse tooLong = postTrade("k".repeat(129), VALID_BODY);
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(json.readValue(tooLong.body(), ErrorResponse.class).code())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");

        HttpResponse duplicateKeys = post(
                "/v1/trades",
                List.of("key-A", "key-B"),
                MediaType.APPLICATION_JSON,
                VALID_BODY);
        assertThat(duplicateKeys.status()).isEqualTo(400);
        assertThat(json.readValue(duplicateKeys.body(), ErrorResponse.class).code())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");

        HttpResponse malformed = postTrade("capture-malformed", "{");
        assertThat(malformed.status()).isEqualTo(400);
        assertThat(json.readValue(malformed.body(), ErrorResponse.class).code()).isEqualTo("MALFORMED_REQUEST");

        HttpResponse zeroQuantity = postTrade("capture-zero", quantityJson(0));
        assertThat(zeroQuantity.status()).isEqualTo(400);
        assertThat(json.readValue(zeroQuantity.body(), ErrorResponse.class).code()).isEqualTo("INVALID_REQUEST");

        HttpResponse fractional = postTrade("capture-fraction", quantityJson("10.5"));
        assertThat(fractional.status()).isEqualTo(400);
        assertThat(json.readValue(fractional.body(), ErrorResponse.class).code()).isEqualTo("MALFORMED_REQUEST");

        HttpResponse unsupported = post(
                "/v1/trades",
                List.of("capture-plain"),
                MediaType.TEXT_PLAIN,
                VALID_BODY);
        assertThat(unsupported.status()).isEqualTo(415);
        ErrorResponse media = json.readValue(unsupported.body(), ErrorResponse.class);
        assertThat(media.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertSafeError(media);

        assertThat(commandResults.findByCommandKey("capture-malformed")).isEmpty();
        assertThat(commandResults.findByCommandKey("capture-zero")).isEmpty();
        assertThat(commandResults.findByCommandKey("capture-plain")).isEmpty();
        assertThat(trades.findByExternalTradeId("T-001")).isEmpty();
    }

    @Test
    void getUnknownOrMalformedTradeId() {
        HttpResponse unknown = get("/v1/trades/" + UUID.fromString("00000000-0000-0000-0000-000000000101"));
        assertThat(unknown.status()).isEqualTo(404);
        ErrorResponse missing = json.readValue(unknown.body(), ErrorResponse.class);
        assertThat(missing.code()).isEqualTo("UNKNOWN_TRADE");
        assertSafeError(missing);

        HttpResponse malformed = get("/v1/trades/not-a-uuid");
        assertThat(malformed.status()).isEqualTo(400);
        ErrorResponse invalid = json.readValue(malformed.body(), ErrorResponse.class);
        assertThat(invalid.code()).isEqualTo("INVALID_REQUEST");
        assertSafeError(invalid);
    }

    @Test
    void unmappedRootPathReturnsNotFoundWithoutHidingOpenApi() {
        HttpResponse root = get("/");
        assertThat(root.status()).isEqualTo(404);
        ErrorResponse error = json.readValue(root.body(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("NOT_FOUND");
        assertThat(error.message()).isEqualTo("Resource does not exist");
        assertThat(error.message()).doesNotContain("/");
        assertSafeError(error);

        HttpResponse apiDocs = get("/v3/api-docs");
        assertThat(apiDocs.status()).isEqualTo(200);
        assertThat(apiDocs.body()).contains("\"/v1/trades\"");

        HttpResponse swaggerUi = get("/swagger-ui/index.html");
        assertThat(swaggerUi.status()).isEqualTo(200);
    }

    private HttpResponse postTrade(String idempotencyKey, String body) {
        List<String> keys = idempotencyKey == null ? List.of() : List.of(idempotencyKey);
        return post("/v1/trades", keys, MediaType.APPLICATION_JSON, body);
    }

    private HttpResponse post(String path, List<String> idempotencyKeys, MediaType contentType, String body) {
        return http.post()
                .uri(path)
                .headers(headers -> idempotencyKeys.forEach(key -> headers.add("Idempotency-Key", key)))
                .contentType(contentType)
                .body(body)
                .exchange((request, response) -> new HttpResponse(
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

    private static String quantityJson(long quantity) {
        return quantityJson(Long.toString(quantity));
    }

    private static String quantityJson(String quantity) {
        return """
                {
                  "externalTradeId": "T-001",
                  "buyerId": "00000000-0000-0000-0000-000000000001",
                  "sellerId": "00000000-0000-0000-0000-000000000002",
                  "securityId": "00000000-0000-0000-0000-0000000000e1",
                  "quantity": %s,
                  "cashAmount": 50000,
                  "settlementDate": "2026-09-20"
                }
                """.formatted(quantity);
    }

    private static void assertSafeError(ErrorResponse error) {
        assertThat(error.message())
                .doesNotContainIgnoringCase("sql")
                .doesNotContainIgnoringCase("postgresql")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("jdbc")
                .doesNotContain("Exception");
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
