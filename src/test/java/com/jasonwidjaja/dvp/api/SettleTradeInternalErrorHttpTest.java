package com.jasonwidjaja.dvp.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.jasonwidjaja.dvp.application.SettleTradeService;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SettleTradeInternalErrorHttpTest extends AbstractPostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private SettleTradeService settle;

    private RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void startHttp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                })
                .build();
    }

    @Test
    void commitTimeConstraintFailureReturnsSafeInternalError() {
        when(settle.settle(any())).thenThrow(new DataIntegrityViolationException(
                "ERROR: new row violates check constraint settlement_journal_shape "
                        + "Detail: Trigger trg_settlement_journal_shape password=secret "
                        + "jdbc:postgresql://localhost/dvp"));

        HttpResponse response = http.post()
                .uri("/v1/trades/{id}/settle", UUID.fromString("00000000-0000-0000-0000-000000000101"))
                .header("Idempotency-Key", "settle-constraint")
                .exchange((request, res) -> new HttpResponse(
                        res.getStatusCode().value(),
                        new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(response.status()).isEqualTo(500);
        ErrorResponse error = json.readValue(response.body(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.message()).isEqualTo("An unexpected error occurred");
        assertThat(error.message())
                .doesNotContainIgnoringCase("sql")
                .doesNotContainIgnoringCase("postgresql")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("jdbc")
                .doesNotContainIgnoringCase("constraint")
                .doesNotContainIgnoringCase("trigger")
                .doesNotContain("Exception")
                .doesNotContain("settlement_journal_shape");
        assertThat(response.body())
                .doesNotContain("settlement_journal_shape")
                .doesNotContain("password")
                .doesNotContain("jdbc:postgresql");
    }

    private record HttpResponse(int status, String body) {
    }
}
