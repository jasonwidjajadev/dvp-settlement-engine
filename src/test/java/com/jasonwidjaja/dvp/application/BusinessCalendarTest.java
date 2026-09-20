package com.jasonwidjaja.dvp.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessCalendarTest {

    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Test
    void businessDateIsTheCalendarDateInAustraliaSydney() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-19T16:00:00Z"), SYDNEY);
        BusinessCalendar calendar = new BusinessCalendar(clock);

        assertThat(calendar.businessDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(LocalDate.ofInstant(clock.instant(), ZoneId.of("UTC"))).isEqualTo(LocalDate.of(2026, 9, 19));
    }
}
