package com.jasonwidjaja.dvp.domain;

/**
 * Project-local balance-movement direction, not GAAP general-ledger semantics.
 * {@link #DEBIT} decreases the account balance.
 * {@link #CREDIT} increases the account balance.
 */
public enum PostingDirection {
    DEBIT,
    CREDIT
}
