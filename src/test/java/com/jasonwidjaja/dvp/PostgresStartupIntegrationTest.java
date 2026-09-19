package com.jasonwidjaja.dvp;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.PostgresTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresStartupIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void springStartsAgainstTestcontainersPostgreSQL() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBean(DvpApplication.class)).isNotNull();

        assertThat(PostgresTestDatabase.POSTGRES.isRunning()).isTrue();
        assertThat(PostgresTestDatabase.IMAGE).isEqualTo("postgres:18.6");
        assertThat(PostgresTestDatabase.POSTGRES.getDockerImageName()).contains("postgres:18.6");
        assertThat(PostgresTestDatabase.POSTGRES.getJdbcUrl())
                .doesNotContain("localhost:5432/dvp");
        assertThat(PostgresTestDatabase.POSTGRES.getDatabaseName()).isNotEqualTo("dvp");

        String version = jdbc.queryForObject("SHOW server_version", Map.of(), String.class);
        assertThat(version).startsWith("18.6");

        Integer one = jdbc.queryForObject("SELECT 1", Map.of(), Integer.class);
        assertThat(one).isEqualTo(1);
    }

    @Test
    void flywayAppliesV1AndV2ToCleanTestDatabase() {
        flyway.validate();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().current().getScript()).isEqualTo("V2__trades_and_command_results.sql");
        assertThat(flyway.info().applied())
                .extracting(info -> info.getScript())
                .containsExactly(
                        "V1__participants_assets_accounts.sql",
                        "V2__trades_and_command_results.sql");

        List<String> tables = jdbc.queryForList(
                """
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'public'
                ORDER BY tablename
                """,
                Map.of(),
                String.class);

        assertThat(tables).containsExactly(
                "account",
                "asset",
                "command_result",
                "flyway_schema_history",
                "participant",
                "trade");
        assertThat(tables).doesNotContain("settlement", "journal", "reconciliation");
    }
}
