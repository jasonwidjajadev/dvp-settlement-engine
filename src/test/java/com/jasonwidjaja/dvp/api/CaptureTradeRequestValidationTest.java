package com.jasonwidjaja.dvp.api;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jasonwidjaja.dvp.support.DemoSeed;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureTradeRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validRequestHasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void missingAndBlankExternalTradeIdAreRejected() {
        assertThat(violatedProperty(withExternalTradeId(null))).contains("externalTradeId");
        assertThat(violatedProperty(withExternalTradeId(""))).contains("externalTradeId");
        assertThat(violatedProperty(withExternalTradeId("   "))).contains("externalTradeId");
    }

    @Test
    void leadingOrTrailingWhitespaceOnExternalTradeIdIsRejected() {
        assertThat(violatedProperty(withExternalTradeId(" T-001"))).contains("externalTradeId");
        assertThat(violatedProperty(withExternalTradeId("T-001 "))).contains("externalTradeId");
    }

    @Test
    void externalTradeIdLongerThan128IsRejected() {
        assertThat(violatedProperty(withExternalTradeId("T".repeat(129)))).contains("externalTradeId");
    }

    @Test
    void missingIdentifiersAreRejected() {
        CaptureTradeRequest missingBuyer = new CaptureTradeRequest(
                "T-001", null, DemoSeed.BOB_ID, DemoSeed.EQ1_ID, 10L, 50000L, LocalDate.of(2026, 9, 20));
        CaptureTradeRequest missingSeller = new CaptureTradeRequest(
                "T-001", DemoSeed.ALICE_ID, null, DemoSeed.EQ1_ID, 10L, 50000L, LocalDate.of(2026, 9, 20));
        CaptureTradeRequest missingSecurity = new CaptureTradeRequest(
                "T-001", DemoSeed.ALICE_ID, DemoSeed.BOB_ID, null, 10L, 50000L, LocalDate.of(2026, 9, 20));

        assertThat(violatedProperty(missingBuyer)).contains("buyerId");
        assertThat(violatedProperty(missingSeller)).contains("sellerId");
        assertThat(violatedProperty(missingSecurity)).contains("securityId");
    }

    @Test
    void missingZeroAndNegativeAmountsAreDistinctFailures() {
        assertThat(violatedProperty(withQuantity(null))).contains("quantity");
        assertThat(violatedProperty(withQuantity(0L))).contains("quantity");
        assertThat(violatedProperty(withQuantity(-1L))).contains("quantity");
        assertThat(violatedProperty(withCashAmount(null))).contains("cashAmount");
        assertThat(violatedProperty(withCashAmount(0L))).contains("cashAmount");
        assertThat(violatedProperty(withCashAmount(-1L))).contains("cashAmount");
    }

    @Test
    void missingSettlementDateIsRejected() {
        CaptureTradeRequest request = new CaptureTradeRequest(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10L,
                50000L,
                null);
        assertThat(violatedProperty(request)).contains("settlementDate");
    }

    @Test
    void dtoDoesNotRequireBuyerAndSellerToDiffer() {
        CaptureTradeRequest selfTrade = new CaptureTradeRequest(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.ALICE_ID,
                DemoSeed.EQ1_ID,
                10L,
                50000L,
                LocalDate.of(2026, 9, 20));
        assertThat(validator.validate(selfTrade)).isEmpty();
    }

    private Set<ConstraintViolation<CaptureTradeRequest>> validate(CaptureTradeRequest request) {
        return validator.validate(request);
    }

    private String violatedProperty(CaptureTradeRequest request) {
        return validate(request).iterator().next().getPropertyPath().toString();
    }

    private static CaptureTradeRequest validRequest() {
        return new CaptureTradeRequest(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10L,
                50000L,
                LocalDate.of(2026, 9, 20));
    }

    private static CaptureTradeRequest withExternalTradeId(String externalTradeId) {
        return new CaptureTradeRequest(
                externalTradeId,
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10L,
                50000L,
                LocalDate.of(2026, 9, 20));
    }

    private static CaptureTradeRequest withQuantity(Long quantity) {
        return new CaptureTradeRequest(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                quantity,
                50000L,
                LocalDate.of(2026, 9, 20));
    }

    private static CaptureTradeRequest withCashAmount(Long cashAmount) {
        return new CaptureTradeRequest(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10L,
                cashAmount,
                LocalDate.of(2026, 9, 20));
    }
}
