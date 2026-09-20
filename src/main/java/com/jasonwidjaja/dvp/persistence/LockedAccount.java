package com.jasonwidjaja.dvp.persistence;

import java.util.UUID;

public record LockedAccount(
        UUID id,
        UUID assetId,
        long currentBalance
) {
}
