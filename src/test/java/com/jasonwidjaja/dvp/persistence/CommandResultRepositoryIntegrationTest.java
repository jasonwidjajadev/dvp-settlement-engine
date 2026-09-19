package com.jasonwidjaja.dvp.persistence;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import com.jasonwidjaja.dvp.domain.CaptureRequestIdentity;
import com.jasonwidjaja.dvp.domain.CommandResult;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandResultRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CommandResultRepository commandResults;

    @Test
    void newKeyCanBeClaimed() {
        String identity = CaptureRequestIdentity.of(aliceBuysEq1(10));

        assertThat(commandResults.claim("capture-T-001", identity)).isTrue();

        CommandResult stored = commandResults.findByCommandKey("capture-T-001").orElseThrow();
        assertThat(stored.commandKey()).isEqualTo("capture-T-001");
        assertThat(stored.operation()).isEqualTo(CaptureRequestIdentity.OPERATION);
        assertThat(stored.requestIdentity()).isEqualTo(identity);
        assertThat(stored.completed()).isFalse();
        assertThat(stored.httpStatus()).isNull();
        assertThat(stored.responseBody()).isNull();
        assertThat(stored.location()).isNull();
    }

    @Test
    void duplicateKeyKeepsTheOriginalRequestIdentity() {
        String firstIdentity = CaptureRequestIdentity.of(aliceBuysEq1(10));
        String changedIdentity = CaptureRequestIdentity.of(aliceBuysEq1(11));

        assertThat(commandResults.claim("capture-T-001", firstIdentity)).isTrue();
        assertThat(commandResults.claim("capture-T-001", changedIdentity)).isFalse();

        CommandResult stored = commandResults.findByCommandKey("capture-T-001").orElseThrow();
        assertThat(stored.hasRequestIdentity(firstIdentity)).isTrue();
        assertThat(stored.hasRequestIdentity(changedIdentity)).isFalse();
        assertThat(firstIdentity).isNotEqualTo(changedIdentity);
    }

    @Test
    void completedResultCanBeReadAfterFinalize() {
        String identity = CaptureRequestIdentity.of(aliceBuysEq1(10));
        assertThat(commandResults.claim("capture-T-001", identity)).isTrue();
        commandResults.finalize(
                "capture-T-001",
                201,
                "{\"status\":\"READY\"}",
                "/v1/trades/8724");

        CommandResult stored = commandResults.findByCommandKey("capture-T-001").orElseThrow();
        assertThat(stored.completed()).isTrue();
        assertThat(stored.httpStatus()).isEqualTo(201);
        assertThat(stored.responseBody()).isEqualTo("{\"status\":\"READY\"}");
        assertThat(stored.location()).isEqualTo("/v1/trades/8724");
        assertThat(stored.hasRequestIdentity(identity)).isTrue();
    }

    @Test
    void completedResultCannotBeReplaced() {
        String identity = CaptureRequestIdentity.of(aliceBuysEq1(10));
        assertThat(commandResults.claim("capture-T-001", identity)).isTrue();
        commandResults.finalize("capture-T-001", 201, "{\"status\":\"READY\"}", "/v1/trades/8724");

        assertThatThrownBy(() -> commandResults.finalize("capture-T-001", 409, "{\"code\":\"CONFLICT\"}", null))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("exactly one unfinished command_result");

        CommandResult stored = commandResults.findByCommandKey("capture-T-001").orElseThrow();
        assertThat(stored.httpStatus()).isEqualTo(201);
        assertThat(stored.responseBody()).isEqualTo("{\"status\":\"READY\"}");
        assertThat(stored.location()).isEqualTo("/v1/trades/8724");
    }

    @Test
    void finalizeUnknownKeyIsAnError() {
        assertThatThrownBy(() -> commandResults.finalize("missing-key", 201, "{}", "/v1/trades/1"))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("exactly one unfinished command_result");
        assertThat(commandResults.findByCommandKey("missing-key")).isEmpty();
    }

    @Test
    void businessRejectionCanBeFinalizedWithoutALocation() {
        String identity = CaptureRequestIdentity.of(aliceBuysEq1(10));
        assertThat(commandResults.claim("capture-unknown", identity)).isTrue();
        commandResults.finalize(
                "capture-unknown",
                422,
                "{\"code\":\"UNKNOWN_PARTICIPANT\",\"message\":\"Buyer does not exist\"}",
                null);

        CommandResult stored = commandResults.findByCommandKey("capture-unknown").orElseThrow();
        assertThat(stored.httpStatus()).isEqualTo(422);
        assertThat(stored.location()).isNull();
        assertThat(stored.completed()).isTrue();
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
