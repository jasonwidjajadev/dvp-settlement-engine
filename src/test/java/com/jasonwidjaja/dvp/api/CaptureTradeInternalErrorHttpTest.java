package com.jasonwidjaja.dvp.api;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.jasonwidjaja.dvp.application.CaptureTradeService;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaptureTradeInternalErrorHttpTest extends AbstractPostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private CaptureTradeService capture;

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
    void unexpectedFailureReturnsSafeInternalError() {
        when(capture.capture(any())).thenThrow(new IllegalStateException(
                "org.postgresql.util.PSQLException: FATAL password=secret jdbc:postgresql://localhost/dvp"));

        HttpResponse response = http.post()
                .uri("/v1/trades")
                .header("Idempotency-Key", "capture-T-001")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {
                          "externalTradeId": "T-001",
                          "buyerId": "00000000-0000-0000-0000-000000000001",
                          "sellerId": "00000000-0000-0000-0000-000000000002",
                          "securityId": "00000000-0000-0000-0000-0000000000e1",
                          "quantity": 10,
                          "cashAmount": 50000,
                          "settlementDate": "2026-09-20"
                        }
                        """)
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
                .doesNotContain("Exception");
    }

    private record HttpResponse(int status, String body) {
    }
}
