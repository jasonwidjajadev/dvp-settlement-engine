package com.jasonwidjaja.dvp.api;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.support.DemoSeed;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureTradeRequestJsonTest {

    private static final String VALID_JSON = """
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

    private final JsonMapper json = JsonMapping.applyStrictIntegerRules(JsonMapper.builder()).build();

    @Test
    void decodesApprovedCaptureJson() {
        CaptureTradeRequest request = json.readValue(VALID_JSON, CaptureTradeRequest.class);

        assertThat(request.externalTradeId()).isEqualTo("T-001");
        assertThat(request.buyerId()).isEqualTo(DemoSeed.ALICE_ID);
        assertThat(request.sellerId()).isEqualTo(DemoSeed.BOB_ID);
        assertThat(request.securityId()).isEqualTo(DemoSeed.EQ1_ID);
        assertThat(request.quantity()).isEqualTo(10L);
        assertThat(request.cashAmount()).isEqualTo(50000L);
        assertThat(request.settlementDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void jsonWhitespaceAndPropertyOrderDoNotChangeDecodedValues() {
        String reordered = """
                {
                  "settlementDate": "2026-09-20",
                  "cashAmount": 50000,
                  "quantity": 10,
                  "securityId": "00000000-0000-0000-0000-0000000000e1",
                  "sellerId": "00000000-0000-0000-0000-000000000002",
                  "buyerId": "00000000-0000-0000-0000-000000000001",
                  "externalTradeId": "T-001"
                }
                """;

        CaptureTradeRequest compact = json.readValue(VALID_JSON, CaptureTradeRequest.class);
        CaptureTradeRequest shuffled = json.readValue(reordered, CaptureTradeRequest.class);

        assertThat(shuffled).isEqualTo(compact);
    }

    @Test
    void missingQuantityDecodesAsNullSoValidationCanDistinguishItFromZero() {
        String jsonBody = """
                {
                  "externalTradeId": "T-001",
                  "buyerId": "00000000-0000-0000-0000-000000000001",
                  "sellerId": "00000000-0000-0000-0000-000000000002",
                  "securityId": "00000000-0000-0000-0000-0000000000e1",
                  "cashAmount": 50000,
                  "settlementDate": "2026-09-20"
                }
                """;

        CaptureTradeRequest request = json.readValue(jsonBody, CaptureTradeRequest.class);
        assertThat(request.quantity()).isNull();
    }

    @Test
    void rejectsMalformedJsonInvalidTypesFractionsAndNumericStrings() {
        assertThatThrownBy(() -> json.readValue("{", CaptureTradeRequest.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> json.readValue(quantityJson("\"banana\""), CaptureTradeRequest.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> json.readValue(quantityJson("10.5"), CaptureTradeRequest.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> json.readValue(quantityJson("\"10\""), CaptureTradeRequest.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> json.readValue(buyerJson("\"banana\""), CaptureTradeRequest.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> json.readValue(settlementDateJson("\"20-09-2026\""), CaptureTradeRequest.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void writesApprovedTradeAndErrorFieldNames() {
        TradeResponse trade = new TradeResponse(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 20),
                TradeStatus.READY,
                null);
        ErrorResponse error = new ErrorResponse("UNKNOWN_PARTICIPANT", "Buyer does not exist");

        assertThat(json.writeValueAsString(trade))
                .contains("\"externalTradeId\":\"T-001\"")
                .contains("\"buyerId\":\"00000000-0000-0000-0000-000000000001\"")
                .contains("\"cashAmount\":50000")
                .contains("\"status\":\"READY\"")
                .contains("\"journalId\":null");
        assertThat(json.writeValueAsString(error))
                .contains("\"code\":\"UNKNOWN_PARTICIPANT\"")
                .contains("\"message\":\"Buyer does not exist\"");
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

    private static String buyerJson(String buyerId) {
        return """
                {
                  "externalTradeId": "T-001",
                  "buyerId": %s,
                  "sellerId": "00000000-0000-0000-0000-000000000002",
                  "securityId": "00000000-0000-0000-0000-0000000000e1",
                  "quantity": 10,
                  "cashAmount": 50000,
                  "settlementDate": "2026-09-20"
                }
                """.formatted(buyerId);
    }

    private static String settlementDateJson(String settlementDate) {
        return """
                {
                  "externalTradeId": "T-001",
                  "buyerId": "00000000-0000-0000-0000-000000000001",
                  "sellerId": "00000000-0000-0000-0000-000000000002",
                  "securityId": "00000000-0000-0000-0000-0000000000e1",
                  "quantity": 10,
                  "cashAmount": 50000,
                  "settlementDate": %s
                }
                """.formatted(settlementDate);
    }
}
