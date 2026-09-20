package com.jasonwidjaja.dvp.application;

public record CommandOutcome(int httpStatus, String responseBody, String location) {
}
