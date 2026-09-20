package com.jasonwidjaja.dvp.api;

import java.util.List;

final class IdempotencyKey {

    private IdempotencyKey() {
    }

    static String requireExactlyOne(List<String> keys) {
        if (keys == null || keys.size() != 1) {
            throw new InvalidIdempotencyKeyException("Exactly one Idempotency-Key header is required");
        }
        String key = keys.getFirst();
        if (key == null || key.isEmpty() || key.length() > 128 || !key.equals(key.strip())) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key must be 1 to 128 characters with no surrounding whitespace");
        }
        return key;
    }
}
