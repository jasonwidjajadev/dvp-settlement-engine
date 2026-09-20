package com.jasonwidjaja.dvp.application;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TimeConfiguration {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Australia/Sydney");

    @Bean
    Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
