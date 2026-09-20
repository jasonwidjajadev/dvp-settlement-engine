package com.jasonwidjaja.dvp.domain;

/**
 * Canonical length-prefixed command identity encoding.
 * A delimiter or length-prefix inside a field cannot merge with an adjacent field.
 */
final class RequestIdentityEncoding {

    private RequestIdentityEncoding() {
    }

    static String encode(String... fields) {
        StringBuilder identity = new StringBuilder();
        for (String field : fields) {
            identity.append(field.length()).append(':').append(field).append(',');
        }
        return identity.toString();
    }
}
