package com.jasonwidjaja.dvp.api;

import java.time.LocalDate;
import java.util.UUID;

import com.jasonwidjaja.dvp.api.validation.NoSurroundingWhitespace;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CaptureTradeRequest(
        @NotNull
        @Size(min = 1, max = 128)
        @NoSurroundingWhitespace
        String externalTradeId,

        @NotNull
        UUID buyerId,

        @NotNull
        UUID sellerId,

        @NotNull
        UUID securityId,

        @NotNull
        @Positive
        Long quantity,

        @NotNull
        @Positive
        Long cashAmount,

        @NotNull
        LocalDate settlementDate
) {
}
