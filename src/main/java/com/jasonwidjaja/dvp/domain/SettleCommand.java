package com.jasonwidjaja.dvp.domain;

import java.util.UUID;

public record SettleCommand(
        String idempotencyKey,
        UUID tradeId
) {
}
