package com.jasonwidjaja.dvp.domain;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementOutcomeIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void enumValuesMatchTheDatabaseOutcomeConstraint() {
        String definition = jdbc.queryForObject(
                """
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname = 'settlement_attempt_outcome_supported'
                """,
                Map.of(),
                String.class);

        assertThat(definition).isNotNull();
        assertThat(definition).doesNotContain("MISSING_ACCOUNT");
        Arrays.stream(SettlementOutcome.values())
                .map(outcome -> "'" + outcome.name() + "'")
                .forEach(quoted -> assertThat(definition).contains(quoted));
        assertThat(SettlementOutcome.values()).hasSize(5);
    }
}
