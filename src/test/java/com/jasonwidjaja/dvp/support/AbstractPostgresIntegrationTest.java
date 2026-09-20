package com.jasonwidjaja.dvp.support;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerDatasource(registry);
    }

    @BeforeEach
    void clearApplicationTables() {
        jdbc.update(
                """
                TRUNCATE TABLE settlement_attempt, posting, settlement_journal,
                               command_result, trade, account, participant, asset
                """,
                Map.of());
    }
}
