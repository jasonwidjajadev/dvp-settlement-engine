package com.jasonwidjaja.dvp.domain;

public final class CaptureRequestIdentity {

    public static final String OPERATION = "CAPTURE_TRADE";

    private CaptureRequestIdentity() {
    }

    /**
     * Canonical identity for C3: operation {@link #OPERATION} plus every parsed
     * trade term. Fields are length-prefixed so a delimiter inside
     * {@code externalTradeId} cannot merge with an adjacent field.
     */
    public static String of(TradeTerms terms) {
        return RequestIdentityEncoding.encode(
                OPERATION,
                terms.externalTradeId(),
                terms.buyerId().toString(),
                terms.sellerId().toString(),
                terms.securityId().toString(),
                Long.toString(terms.quantity()),
                Long.toString(terms.cashAmount()),
                terms.settlementDate().toString());
    }
}
