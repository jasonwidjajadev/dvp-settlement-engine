package com.jasonwidjaja.dvp.domain;

public record CommandResult(
        String commandKey,
        String operation,
        String requestIdentity,
        Integer httpStatus,
        String responseBody,
        String location
) {

    public boolean completed() {
        return httpStatus != null;
    }

    public boolean hasRequestIdentity(String requestIdentity) {
        return this.requestIdentity.equals(requestIdentity);
    }
}
