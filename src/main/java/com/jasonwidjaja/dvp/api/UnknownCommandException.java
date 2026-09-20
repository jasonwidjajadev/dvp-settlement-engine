package com.jasonwidjaja.dvp.api;

public class UnknownCommandException extends RuntimeException {

    public UnknownCommandException() {
        super("Command does not exist");
    }
}
