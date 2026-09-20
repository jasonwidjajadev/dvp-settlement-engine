package com.jasonwidjaja.dvp.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jasonwidjaja.dvp.domain.PostingDirection;

public record JournalResponse(
        UUID id,
        UUID tradeId,
        Instant settledAt,
        List<PostingResponse> postings
) {

    public record PostingResponse(
            UUID id,
            UUID accountId,
            AccountResponse.ParticipantResponse participant,
            AccountResponse.AssetResponse asset,
            PostingDirection direction,
            long amount
    ) {
    }
}
