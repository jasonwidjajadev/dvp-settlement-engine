package com.jasonwidjaja.dvp.api;

public class UnknownJournalException extends RuntimeException {

    public UnknownJournalException() {
        super("Journal does not exist");
    }
}
