package com.jasonwidjaja.dvp.domain;

import java.util.UUID;

public record Account(
        UUID id,
        Participant participant,
        Asset asset,
        long openingBalance,
        long currentBalance
) {
}
