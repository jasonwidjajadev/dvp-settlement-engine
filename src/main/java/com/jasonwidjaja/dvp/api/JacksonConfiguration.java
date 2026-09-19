package com.jasonwidjaja.dvp.api;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JacksonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer strictIntegerJson() {
        return JsonMapping::applyStrictIntegerRules;
    }
}
