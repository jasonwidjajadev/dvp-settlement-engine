package com.jasonwidjaja.dvp.api;

class UnknownTradeException extends RuntimeException {

    UnknownTradeException() {
        super("Trade does not exist");
    }
}
