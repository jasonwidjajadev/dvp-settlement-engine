package com.jasonwidjaja.dvp.api;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jasonwidjaja.dvp.domain.SettlementOutcome;
import com.jasonwidjaja.dvp.domain.TradeStatus;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementResponseTest {

    @Test
    void serializesTheApprovedSettlementOutcomeFields() {
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID journalId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        Instant settledAt = Instant.parse("2026-09-20T04:00:00Z");
        SettlementResponse response = new SettlementResponse(
                tradeId,
                TradeStatus.SETTLED,
                SettlementOutcome.SETTLED,
                journalId,
                settledAt);

        JsonMapper json = JsonMapper.builder().build();
        String body = json.writeValueAsString(response);
        SettlementResponse read = json.readValue(body, SettlementResponse.class);

        assertThat(read).isEqualTo(response);
        assertThat(body).contains("\"tradeId\"");
        assertThat(body).contains("\"status\"");
        assertThat(body).contains("\"outcome\"");
        assertThat(body).contains("\"journalId\"");
        assertThat(body).contains("\"settledAt\"");
    }
}
