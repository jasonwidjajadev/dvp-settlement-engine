package com.jasonwidjaja.dvp.domain;

import java.util.UUID;

public final class SettleRequestIdentity {

    public static final String OPERATION = "SETTLE_TRADE";

    private SettleRequestIdentity() {
    }

    /**
     * Canonical identity for settlement: operation {@link #OPERATION} plus the
     * internal trade id. Uses the same length-prefixed encoding as capture.
     */
    public static String of(UUID tradeId) {
        return RequestIdentityEncoding.encode(OPERATION, tradeId.toString());
    }
}
