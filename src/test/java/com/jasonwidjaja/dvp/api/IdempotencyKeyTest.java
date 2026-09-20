package com.jasonwidjaja.dvp.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    @Test
    void acceptsExactOneTo128CharacterKey() {
        assertThat(IdempotencyKey.requireExactlyOne(List.of("capture-T-001"))).isEqualTo("capture-T-001");
        assertThat(IdempotencyKey.requireExactlyOne(List.of("k"))).isEqualTo("k");
        assertThat(IdempotencyKey.requireExactlyOne(List.of("k".repeat(128)))).isEqualTo("k".repeat(128));
    }

    @Test
    void rejectsMissingMultipleBlankAndOversizedKeys() {
        assertThatThrownBy(() -> IdempotencyKey.requireExactlyOne(null))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.requireExactlyOne(List.of()))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.requireExactlyOne(List.of("a", "b")))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.requireExactlyOne(List.of("")))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.requireExactlyOne(List.of("k".repeat(129))))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void rejectsSurroundingWhitespaceWithoutTrimming() {
        assertThatThrownBy(() -> IdempotencyKey.requireExactlyOne(List.of(" capture-T-001")))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.requireExactlyOne(List.of("capture-T-001 ")))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.requireExactlyOne(List.of("\tcapture-T-001")))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }
}
