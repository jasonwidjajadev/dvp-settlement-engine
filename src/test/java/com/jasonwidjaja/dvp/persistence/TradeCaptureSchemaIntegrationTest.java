package com.jasonwidjaja.dvp.persistence;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeCaptureSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-00000000c0ff");
    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 9, 20);
    private static final String REQUEST_IDENTITY =
            "T-001|00000000-0000-0000-0000-000000000001|00000000-0000-0000-0000-000000000002"
                    + "|00000000-0000-0000-0000-0000000000e1|10|50000|2026-09-20";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
    }

    @Test
    void validTradeRowCanBeInserted() {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        insertTrade(tradeId, "T-001", DemoSeed.ALICE_ID, DemoSeed.BOB_ID, DemoSeed.EQ1_ID, 10, 50000, "READY");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM trade WHERE id = :id",
                Map.of("id", tradeId));

        assertThat(row.get("external_trade_id")).isEqualTo("T-001");
        assertThat(row.get("buyer_id")).isEqualTo(DemoSeed.ALICE_ID);
        assertThat(row.get("seller_id")).isEqualTo(DemoSeed.BOB_ID);
        assertThat(row.get("security_id")).isEqualTo(DemoSeed.EQ1_ID);
        assertThat(((Number) row.get("quantity")).longValue()).isEqualTo(10);
        assertThat(((Number) row.get("cash_amount")).longValue()).isEqualTo(50000);
        assertThat(((java.sql.Date) row.get("settlement_date")).toLocalDate()).isEqualTo(SETTLEMENT_DATE);
        assertThat(row.get("status")).isEqualTo("READY");
    }

    @Test
    void duplicateExternalTradeReferenceIsRejected() {
        insertTrade(UUID.randomUUID(), "T-001", DemoSeed.ALICE_ID, DemoSeed.BOB_ID, DemoSeed.EQ1_ID, 10, 50000, "READY");
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        DemoSeed.ALICE_ID,
                        DemoSeed.BOB_ID,
                        DemoSeed.EQ1_ID,
                        11,
                        50000,
                        "READY"),
                "trade_external_trade_id_unique");
    }

    @Test
    void unknownBuyerIsRejected() {
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        UNKNOWN_ID,
                        DemoSeed.BOB_ID,
                        DemoSeed.EQ1_ID,
                        10,
                        50000,
                        "READY"),
                "trade_buyer_fk");
    }

    @Test
    void unknownSellerIsRejected() {
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        DemoSeed.ALICE_ID,
                        UNKNOWN_ID,
                        DemoSeed.EQ1_ID,
                        10,
                        50000,
                        "READY"),
                "trade_seller_fk");
    }

    @Test
    void unknownSecurityIsRejected() {
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        DemoSeed.ALICE_ID,
                        DemoSeed.BOB_ID,
                        UNKNOWN_ID,
                        10,
                        50000,
                        "READY"),
                "trade_security_fk");
    }

    @Test
    void selfTradeIsRejected() {
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        DemoSeed.ALICE_ID,
                        DemoSeed.ALICE_ID,
                        DemoSeed.EQ1_ID,
                        10,
                        50000,
                        "READY"),
                "trade_buyer_not_seller");
    }

    @Test
    void zeroQuantityIsRejected() {
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        DemoSeed.ALICE_ID,
                        DemoSeed.BOB_ID,
                        DemoSeed.EQ1_ID,
                        0,
                        50000,
                        "READY"),
                "trade_quantity_positive");
    }

    @Test
    void negativeQuantityIsRejected() {
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        DemoSeed.ALICE_ID,
                        DemoSeed.BOB_ID,
                        DemoSeed.EQ1_ID,
                        -1,
                        50000,
                        "READY"),
                "trade_quantity_positive");
    }

    @Test
    void zeroCashAmountIsRejected() {
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        DemoSeed.ALICE_ID,
                        DemoSeed.BOB_ID,
                        DemoSeed.EQ1_ID,
                        10,
                        0,
                        "READY"),
                "trade_cash_amount_positive");
    }

    @Test
    void negativeCashAmountIsRejected() {
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        DemoSeed.ALICE_ID,
                        DemoSeed.BOB_ID,
                        DemoSeed.EQ1_ID,
                        10,
                        -1,
                        "READY"),
                "trade_cash_amount_positive");
    }

    @Test
    void unsupportedStatusIsRejected() {
        assertRejected(
                () -> insertTrade(
                        UUID.randomUUID(),
                        "T-001",
                        DemoSeed.ALICE_ID,
                        DemoSeed.BOB_ID,
                        DemoSeed.EQ1_ID,
                        10,
                        50000,
                        "SETTLED"),
                "trade_status_supported");
    }

    @Test
    void surroundingWhitespaceOnExternalTradeIdIsRejected() {
        for (String externalTradeId : new String[] {
                " T-001",
                "T-001 ",
                "\tT-001",
                "T-001\n",
                "T-001\r",
                "\u3000T-001"
        }) {
            assertRejected(
                    () -> insertTrade(
                            UUID.randomUUID(),
                            externalTradeId,
                            DemoSeed.ALICE_ID,
                            DemoSeed.BOB_ID,
                            DemoSeed.EQ1_ID,
                            10,
                            50000,
                            "READY"),
                    "trade_external_trade_id_format");
        }
    }

    @Test
    void validCompletedCommandResultCanBeStored() {
        insertCommandResult("capture-T-001", REQUEST_IDENTITY, 201, "{\"status\":\"READY\"}", "/v1/trades/8724");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM command_result WHERE command_key = :key",
                Map.of("key", "capture-T-001"));

        assertThat(row.get("operation")).isEqualTo("CAPTURE_TRADE");
        assertThat(row.get("request_identity")).isEqualTo(REQUEST_IDENTITY);
        assertThat(row.get("http_status")).isEqualTo(201);
        assertThat(row.get("response_body")).isEqualTo("{\"status\":\"READY\"}");
        assertThat(row.get("location")).isEqualTo("/v1/trades/8724");
    }

    @Test
    void surroundingWhitespaceOnCommandKeyIsRejected() {
        for (String commandKey : new String[] {
                " capture-T-001",
                "capture-T-001 ",
                "\tcapture-T-001",
                "capture-T-001\n",
                "capture-T-001\r",
                "\u3000capture-T-001"
        }) {
            assertRejected(
                    () -> insertCommandResult(commandKey, REQUEST_IDENTITY, 201, "{}", "/v1/trades/8724"),
                    "command_result_key_format");
        }
    }

    @Test
    void duplicateCommandKeyIsRejected() {
        insertCommandResult("capture-T-001", REQUEST_IDENTITY, 201, "{}", "/v1/trades/8724");
        assertRejected(
                () -> insertCommandResult("capture-T-001", REQUEST_IDENTITY, 201, "{}", "/v1/trades/9999"),
                "command_result_pkey");
    }

    @Test
    void invalidPartialCommandResultIsRejected() {
        assertRejected(
                () -> insertCommandResult("capture-T-001", REQUEST_IDENTITY, 201, null, null),
                "command_result_completion_state");
    }

    @Test
    void businessRejectionCanExistWithoutATrade() {
        insertCommandResult(
                "capture-unknown",
                REQUEST_IDENTITY,
                422,
                "{\"code\":\"UNKNOWN_PARTICIPANT\",\"message\":\"Buyer does not exist\"}",
                null);

        Integer trades = jdbc.queryForObject("SELECT count(*) FROM trade", Map.of(), Integer.class);
        Integer results = jdbc.queryForObject("SELECT count(*) FROM command_result", Map.of(), Integer.class);

        assertThat(trades).isZero();
        assertThat(results).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT location FROM command_result WHERE command_key = :key",
                        Map.of("key", "capture-unknown"),
                        String.class))
                .isNull();
    }

    @Test
    void unfinishedCommandClaimCanBeFinalized() {
        insertCommandResult("capture-T-001", REQUEST_IDENTITY, null, null, null);
        jdbc.update(
                """
                UPDATE command_result
                SET http_status = :status, response_body = :body, location = :location
                WHERE command_key = :key
                """,
                Map.of(
                        "status", 201,
                        "body", "{}",
                        "location", "/v1/trades/8724",
                        "key", "capture-T-001"));

        assertThat(jdbc.queryForObject(
                        "SELECT http_status FROM command_result WHERE command_key = :key",
                        Map.of("key", "capture-T-001"),
                        Integer.class))
                .isEqualTo(201);
    }

    @Test
    void completedCommandResultCannotBeOverwritten() {
        insertCommandResult("capture-T-001", REQUEST_IDENTITY, 201, "{}", "/v1/trades/8724");
        assertRejected(
                () -> jdbc.update(
                        """
                        UPDATE command_result
                        SET http_status = 409, response_body = '{"code":"CONFLICT"}'
                        WHERE command_key = :key
                        """,
                        Map.of("key", "capture-T-001")),
                "command_result_completed_immutable");
    }

    private void insertTrade(
            UUID id,
            String externalTradeId,
            UUID buyerId,
            UUID sellerId,
            UUID securityId,
            long quantity,
            long cashAmount,
            String status
    ) {
        jdbc.update(
                """
                INSERT INTO trade (
                    id,
                    external_trade_id,
                    buyer_id,
                    seller_id,
                    security_id,
                    quantity,
                    cash_amount,
                    settlement_date,
                    status
                ) VALUES (
                    :id,
                    :externalTradeId,
                    :buyerId,
                    :sellerId,
                    :securityId,
                    :quantity,
                    :cashAmount,
                    :settlementDate,
                    :status
                )
                """,
                Map.of(
                        "id", id,
                        "externalTradeId", externalTradeId,
                        "buyerId", buyerId,
                        "sellerId", sellerId,
                        "securityId", securityId,
                        "quantity", quantity,
                        "cashAmount", cashAmount,
                        "settlementDate", SETTLEMENT_DATE,
                        "status", status));
    }

    private void insertCommandResult(
            String commandKey,
            String requestIdentity,
            Integer httpStatus,
            String responseBody,
            String location
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("commandKey", commandKey);
        params.put("requestIdentity", requestIdentity);
        params.put("httpStatus", httpStatus);
        params.put("responseBody", responseBody);
        params.put("location", location);
        jdbc.update(
                """
                INSERT INTO command_result (
                    command_key,
                    operation,
                    request_identity,
                    http_status,
                    response_body,
                    location
                ) VALUES (
                    :commandKey,
                    'CAPTURE_TRADE',
                    :requestIdentity,
                    :httpStatus,
                    :responseBody,
                    :location
                )
                """,
                params);
    }

    private static void assertRejected(Runnable statement, String constraint) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    Throwable root = ((DataIntegrityViolationException) ex).getMostSpecificCause();
                    assertThat(root).isInstanceOf(PSQLException.class);
                    assertThat(root.getMessage()).contains(constraint);
                });
    }
}
