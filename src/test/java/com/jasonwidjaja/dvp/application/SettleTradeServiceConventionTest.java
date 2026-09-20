package com.jasonwidjaja.dvp.application;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettleTradeServiceConventionTest {

    @Test
    void settlementPathHasNoJavaLockingPrimitives() {
        assertThat(Arrays.stream(SettleTradeService.class.getDeclaredFields()).map(Field::getType))
                .noneMatch(type -> Lock.class.isAssignableFrom(type)
                        || type.getName().startsWith("java.util.concurrent.locks"));
        assertThat(SettleTradeService.class.getDeclaredMethods())
                .noneMatch(method -> Modifier.isSynchronized(method.getModifiers()));
    }
}
