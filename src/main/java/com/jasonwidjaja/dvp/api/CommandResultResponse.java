package com.jasonwidjaja.dvp.api;

import tools.jackson.databind.JsonNode;

public record CommandResultResponse(
        String commandKey,
        String operation,
        int httpStatus,
        String location,
        JsonNode response
) {
}
