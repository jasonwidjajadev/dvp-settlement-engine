package com.jasonwidjaja.dvp.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.AssetType;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSeedIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private AccountRepository accounts;

    @Test
    void seedIsDeterministicAndSafeToRerun() {
        DemoSeed.apply(dataSource);
        assertCanonicalSeed();

        jdbc.update(
                "UPDATE account SET current_balance = 50000 WHERE id = :id",
                Map.of("id", DemoSeed.ALICE_AUD_ID));

        DemoSeed.apply(dataSource);

        assertThat(tableCount("participant")).isEqualTo(2);
        assertThat(tableCount("asset")).isEqualTo(2);
        assertThat(tableCount("account")).isEqualTo(4);
        assertThat(balance(DemoSeed.ALICE_AUD_ID, "opening_balance")).isEqualTo(100000);
        assertThat(balance(DemoSeed.ALICE_AUD_ID, "current_balance")).isEqualTo(50000);
    }

    @Test
    void accountRepositoryReadsSeededAccounts() {
        DemoSeed.apply(dataSource);

        List<Account> all = accounts.findAll();
        assertThat(all).hasSize(4);

        Account aliceAud = require(accounts.findById(DemoSeed.ALICE_AUD_ID));
        assertThat(aliceAud.participant().id()).isEqualTo(DemoSeed.ALICE_ID);
        assertThat(aliceAud.participant().name()).isEqualTo("Alice");
        assertThat(aliceAud.asset().id()).isEqualTo(DemoSeed.AUD_ID);
        assertThat(aliceAud.asset().code()).isEqualTo("AUD");
        assertThat(aliceAud.asset().type()).isEqualTo(AssetType.CASH);
        assertThat(aliceAud.openingBalance()).isEqualTo(100000);
        assertThat(aliceAud.currentBalance()).isEqualTo(100000);

        Account aliceEq1 = require(accounts.findById(DemoSeed.ALICE_EQ1_ID));
        assertThat(aliceEq1.participant().name()).isEqualTo("Alice");
        assertThat(aliceEq1.asset().code()).isEqualTo("EQ1");
        assertThat(aliceEq1.asset().type()).isEqualTo(AssetType.SECURITY);
        assertThat(aliceEq1.openingBalance()).isEqualTo(0);
        assertThat(aliceEq1.currentBalance()).isEqualTo(0);

        Account bobAud = require(accounts.findById(DemoSeed.BOB_AUD_ID));
        assertThat(bobAud.participant().id()).isEqualTo(DemoSeed.BOB_ID);
        assertThat(bobAud.participant().name()).isEqualTo("Bob");
        assertThat(bobAud.asset().code()).isEqualTo("AUD");
        assertThat(bobAud.openingBalance()).isEqualTo(0);
        assertThat(bobAud.currentBalance()).isEqualTo(0);

        Account bobEq1 = require(accounts.findById(DemoSeed.BOB_EQ1_ID));
        assertThat(bobEq1.participant().name()).isEqualTo("Bob");
        assertThat(bobEq1.asset().id()).isEqualTo(DemoSeed.EQ1_ID);
        assertThat(bobEq1.asset().code()).isEqualTo("EQ1");
        assertThat(bobEq1.openingBalance()).isEqualTo(10);
        assertThat(bobEq1.currentBalance()).isEqualTo(10);

        assertThat(all)
                .extracting(Account::id)
                .containsExactlyInAnyOrder(
                        DemoSeed.ALICE_AUD_ID,
                        DemoSeed.ALICE_EQ1_ID,
                        DemoSeed.BOB_AUD_ID,
                        DemoSeed.BOB_EQ1_ID);

        assertThat(accounts.findById(DemoSeed.UNKNOWN_ACCOUNT_ID)).isEmpty();
    }

    private void assertCanonicalSeed() {
        assertThat(tableCount("participant")).isEqualTo(2);
        assertThat(tableCount("asset")).isEqualTo(2);
        assertThat(tableCount("account")).isEqualTo(4);
        assertThat(nameOf(DemoSeed.ALICE_ID)).isEqualTo("Alice");
        assertThat(nameOf(DemoSeed.BOB_ID)).isEqualTo("Bob");
        assertThat(assetCode(DemoSeed.AUD_ID)).isEqualTo("AUD");
        assertThat(assetType(DemoSeed.AUD_ID)).isEqualTo("CASH");
        assertThat(assetCode(DemoSeed.EQ1_ID)).isEqualTo("EQ1");
        assertThat(assetType(DemoSeed.EQ1_ID)).isEqualTo("SECURITY");
        assertThat(balance(DemoSeed.ALICE_AUD_ID, "opening_balance")).isEqualTo(100000);
        assertThat(balance(DemoSeed.ALICE_AUD_ID, "current_balance")).isEqualTo(100000);
        assertThat(balance(DemoSeed.ALICE_EQ1_ID, "opening_balance")).isEqualTo(0);
        assertThat(balance(DemoSeed.ALICE_EQ1_ID, "current_balance")).isEqualTo(0);
        assertThat(balance(DemoSeed.BOB_AUD_ID, "opening_balance")).isEqualTo(0);
        assertThat(balance(DemoSeed.BOB_AUD_ID, "current_balance")).isEqualTo(0);
        assertThat(balance(DemoSeed.BOB_EQ1_ID, "opening_balance")).isEqualTo(10);
        assertThat(balance(DemoSeed.BOB_EQ1_ID, "current_balance")).isEqualTo(10);
    }

    private static Account require(Optional<Account> account) {
        assertThat(account).isPresent();
        return account.orElseThrow();
    }

    private Integer tableCount(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Map.of(), Integer.class);
    }

    private String nameOf(UUID participantId) {
        return jdbc.queryForObject(
                "SELECT name FROM participant WHERE id = :id",
                Map.of("id", participantId),
                String.class);
    }

    private String assetCode(UUID assetId) {
        return jdbc.queryForObject(
                "SELECT code FROM asset WHERE id = :id",
                Map.of("id", assetId),
                String.class);
    }

    private String assetType(UUID assetId) {
        return jdbc.queryForObject(
                "SELECT type FROM asset WHERE id = :id",
                Map.of("id", assetId),
                String.class);
    }

    private Long balance(UUID accountId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM account WHERE id = :id",
                Map.of("id", accountId),
                Long.class);
    }
}
