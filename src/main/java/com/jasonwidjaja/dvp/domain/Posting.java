package com.jasonwidjaja.dvp.domain;

import java.util.UUID;

public record Posting(
        UUID id,
        UUID journalId,
        UUID accountId,
        PostingDirection direction,
        long amount,
        long signedAmount
) {
}
