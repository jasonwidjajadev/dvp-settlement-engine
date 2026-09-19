package com.jasonwidjaja.dvp;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DVP_VERIFY_JDBC", matches = "true")
class JdbcConnectionVerification {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void selectOneFromPostgreSQL() {
        Integer one = jdbc.queryForObject("SELECT 1", Map.of(), Integer.class);
        assertThat(one).isEqualTo(1);
    }
}
