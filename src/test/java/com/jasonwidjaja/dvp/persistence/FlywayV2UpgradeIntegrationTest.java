package com.jasonwidjaja.dvp.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.jasonwidjaja.dvp.support.DemoSeed;
import com.jasonwidjaja.dvp.support.PostgresTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV2UpgradeIntegrationTest {

    private static final String UPGRADE_DATABASE = "dvp_v1_to_v2";

    @Test
    void v1DatabaseUpgradesToV2WithoutChangingPhase1Data() throws Exception {
        createUpgradeDatabase();

        String url = upgradeJdbcUrl();
        String username = PostgresTestDatabase.POSTGRES.getUsername();
        String password = PostgresTestDatabase.POSTGRES.getPassword();

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        DataSource dataSource = new DriverManagerDataSource(url, username, password);
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);

        assertThat(tableNames(jdbc)).containsExactly("account", "asset", "flyway_schema_history", "participant");
        assertThat(tableNames(jdbc)).doesNotContain("trade", "command_result");

        DemoSeed.apply(dataSource);
        List<AccountSnapshot> before = accountSnapshots(jdbc);
        assertThat(before).hasSize(4);

        Flyway upgraded = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load();
        upgraded.migrate();

        assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(upgraded.info().applied())
                .extracting(info -> info.getScript())
                .containsExactly(
                        "V1__participants_assets_accounts.sql",
                        "V2__trades_and_command_results.sql");
        assertThat(tableNames(jdbc)).containsExactly(
                "account",
                "asset",
                "command_result",
                "flyway_schema_history",
                "participant",
                "trade");
        assertThat(accountSnapshots(jdbc)).containsExactlyElementsOf(before);
        assertThat(count(jdbc, "participant")).isEqualTo(2);
        assertThat(count(jdbc, "asset")).isEqualTo(2);
        assertThat(count(jdbc, "account")).isEqualTo(4);
        assertThat(count(jdbc, "trade")).isZero();
        assertThat(count(jdbc, "command_result")).isZero();
        assertThat(nameOf(jdbc, DemoSeed.ALICE_ID)).isEqualTo("Alice");
        assertThat(nameOf(jdbc, DemoSeed.BOB_ID)).isEqualTo("Bob");
        assertThat(assetCode(jdbc, DemoSeed.AUD_ID)).isEqualTo("AUD");
        assertThat(assetCode(jdbc, DemoSeed.EQ1_ID)).isEqualTo("EQ1");
        assertThat(balance(jdbc, DemoSeed.ALICE_AUD_ID, "current_balance")).isEqualTo(100000);
        assertThat(balance(jdbc, DemoSeed.BOB_EQ1_ID, "current_balance")).isEqualTo(10);
    }

    private static void createUpgradeDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        PostgresTestDatabase.POSTGRES.getJdbcUrl(),
                        PostgresTestDatabase.POSTGRES.getUsername(),
                        PostgresTestDatabase.POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + UPGRADE_DATABASE + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + UPGRADE_DATABASE);
        }
    }

    private static String upgradeJdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                PostgresTestDatabase.POSTGRES.getHost(),
                PostgresTestDatabase.POSTGRES.getFirstMappedPort(),
                UPGRADE_DATABASE);
    }

    private static List<String> tableNames(NamedParameterJdbcTemplate jdbc) {
        return jdbc.queryForList(
                """
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'public'
                ORDER BY tablename
                """,
                Map.of(),
                String.class);
    }

    private static List<AccountSnapshot> accountSnapshots(NamedParameterJdbcTemplate jdbc) {
        return jdbc.query(
                """
                SELECT id, participant_id, asset_id, opening_balance, current_balance
                FROM account
                ORDER BY id
                """,
                Map.of(),
                (rs, rowNum) -> new AccountSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("participant_id", UUID.class),
                        rs.getObject("asset_id", UUID.class),
                        rs.getLong("opening_balance"),
                        rs.getLong("current_balance")));
    }

    private static Integer count(NamedParameterJdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Map.of(), Integer.class);
    }

    private static String nameOf(NamedParameterJdbcTemplate jdbc, UUID participantId) {
        return jdbc.queryForObject(
                "SELECT name FROM participant WHERE id = :id",
                Map.of("id", participantId),
                String.class);
    }

    private static String assetCode(NamedParameterJdbcTemplate jdbc, UUID assetId) {
        return jdbc.queryForObject(
                "SELECT code FROM asset WHERE id = :id",
                Map.of("id", assetId),
                String.class);
    }

    private static Long balance(NamedParameterJdbcTemplate jdbc, UUID accountId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM account WHERE id = :id",
                Map.of("id", accountId),
                Long.class);
    }

    private record AccountSnapshot(
            UUID id,
            UUID participantId,
            UUID assetId,
            long openingBalance,
            long currentBalance
    ) {
    }
}
