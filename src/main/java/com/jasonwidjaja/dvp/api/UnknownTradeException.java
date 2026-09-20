package com.jasonwidjaja.dvp.api;

public class UnknownTradeException extends RuntimeException {

    public UnknownTradeException() {
        super("Trade does not exist");
    }
}
