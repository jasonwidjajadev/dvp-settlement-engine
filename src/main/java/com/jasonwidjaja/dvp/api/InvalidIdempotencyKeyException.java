package com.jasonwidjaja.dvp.api;

class InvalidIdempotencyKeyException extends RuntimeException {

    InvalidIdempotencyKeyException(String message) {
        super(message);
    }
}
