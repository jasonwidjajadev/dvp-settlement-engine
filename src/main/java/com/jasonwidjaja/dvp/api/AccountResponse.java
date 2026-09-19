package com.jasonwidjaja.dvp.api;

import java.util.UUID;

import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.AssetType;

public record AccountResponse(
        UUID id,
        ParticipantResponse participant,
        AssetResponse asset,
        long openingBalance,
        long currentBalance
) {

    public record ParticipantResponse(UUID id, String name) {
    }

    public record AssetResponse(UUID id, String code, AssetType type) {
    }

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.id(),
                new ParticipantResponse(account.participant().id(), account.participant().name()),
                new AssetResponse(account.asset().id(), account.asset().code(), account.asset().type()),
                account.openingBalance(),
                account.currentBalance());
    }
}
