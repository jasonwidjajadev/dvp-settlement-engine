package com.jasonwidjaja.dvp.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
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

class FlywayV3UpgradeIntegrationTest {

    private static final String UPGRADE_DATABASE = "dvp_v2_to_v3";
    private static final UUID TRADE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 9, 20);

    @Test
    void v2DatabaseUpgradesToV3WithoutChangingPhase2Data() throws Exception {
        createUpgradeDatabase();

        String url = upgradeJdbcUrl();
        String username = PostgresTestDatabase.POSTGRES.getUsername();
        String password = PostgresTestDatabase.POSTGRES.getPassword();

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2"))
                .load()
                .migrate();

        DataSource dataSource = new DriverManagerDataSource(url, username, password);
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);

        DemoSeed.apply(dataSource);
        insertCapturedTrade(jdbc);
        List<AccountSnapshot> accountsBefore = accountSnapshots(jdbc);
        Map<String, Object> tradeBefore = jdbc.queryForMap(
                "SELECT * FROM trade WHERE id = :id",
                Map.of("id", TRADE_ID));
        Map<String, Object> commandBefore = jdbc.queryForMap(
                "SELECT * FROM command_result WHERE command_key = :key",
                Map.of("key", "capture-T-001"));

        Flyway upgraded = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load();
        upgraded.migrate();

        assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(upgraded.info().applied())
                .extracting(info -> info.getScript())
                .containsExactly(
                        "V1__participants_assets_accounts.sql",
                        "V2__trades_and_command_results.sql",
                        "V3__settlement_journal_postings_attempts.sql");
        assertThat(tableNames(jdbc)).containsExactly(
                "account",
                "asset",
                "command_result",
                "flyway_schema_history",
                "participant",
                "posting",
                "settlement_attempt",
                "settlement_journal",
                "trade");
        assertThat(accountSnapshots(jdbc)).containsExactlyElementsOf(accountsBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM trade WHERE id = :id", Map.of("id", TRADE_ID)))
                .containsAllEntriesOf(tradeBefore);
        assertThat(jdbc.queryForObject(
                        "SELECT journal_id FROM trade WHERE id = :id",
                        Map.of("id", TRADE_ID),
                        UUID.class))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM trade WHERE id = :id",
                        Map.of("id", TRADE_ID),
                        String.class))
                .isEqualTo("READY");
        assertThat(jdbc.queryForMap(
                        "SELECT * FROM command_result WHERE command_key = :key",
                        Map.of("key", "capture-T-001")))
                .containsAllEntriesOf(commandBefore);
        assertThat(count(jdbc, "settlement_journal")).isZero();
        assertThat(count(jdbc, "posting")).isZero();
        assertThat(count(jdbc, "settlement_attempt")).isZero();
    }

    private static void insertCapturedTrade(NamedParameterJdbcTemplate jdbc) {
        jdbc.update(
                """
                INSERT INTO trade (
                    id,
                    external_trade_id,
                    buyer_id,
                    seller_id,
                    security_id,
                    quantity,
                    cash_amount,
                    settlement_date,
                    status
                ) VALUES (
                    :id,
                    'T-001',
                    :buyerId,
                    :sellerId,
                    :securityId,
                    10,
                    50000,
                    :settlementDate,
                    'READY'
                )
                """,
                Map.of(
                        "id", TRADE_ID,
                        "buyerId", DemoSeed.ALICE_ID,
                        "sellerId", DemoSeed.BOB_ID,
                        "securityId", DemoSeed.EQ1_ID,
                        "settlementDate", SETTLEMENT_DATE));
        jdbc.update(
                """
                INSERT INTO command_result (
                    command_key,
                    operation,
                    request_identity,
                    http_status,
                    response_body,
                    location
                ) VALUES (
                    'capture-T-001',
                    'CAPTURE_TRADE',
                    'captured-T-001',
                    201,
                    '{"status":"READY"}',
                    '/v1/trades/00000000-0000-0000-0000-000000000101'
                )
                """,
                Map.of());
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

    private record AccountSnapshot(
            UUID id,
            UUID participantId,
            UUID assetId,
            long openingBalance,
            long currentBalance
    ) {
    }
}
