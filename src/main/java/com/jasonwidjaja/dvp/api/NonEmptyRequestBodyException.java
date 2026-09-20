package com.jasonwidjaja.dvp.api;

class NonEmptyRequestBodyException extends RuntimeException {

    NonEmptyRequestBodyException() {
        super("Request body must be empty");
    }
}
