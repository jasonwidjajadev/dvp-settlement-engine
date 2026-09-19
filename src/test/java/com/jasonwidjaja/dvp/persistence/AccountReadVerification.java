package com.jasonwidjaja.dvp.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.AssetType;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DVP_VERIFY_JDBC", matches = "true")
class AccountReadVerification {

    private static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID AUD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID EQ1_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID ALICE_AUD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ALICE_EQ1_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ae");
    private static final UUID BOB_AUD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ba");
    private static final UUID BOB_EQ1_ID = UUID.fromString("00000000-0000-0000-0000-0000000000be");
    private static final UUID UNKNOWN_ACCOUNT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void readsSeededAccountsWithoutChangingState() {
        String version = jdbc.queryForObject("SHOW server_version", Map.of(), String.class);
        assertThat(version).startsWith("18");

        List<BalanceRow> before = balanceRows();

        List<Account> all = accounts.findAll();
        assertThat(all).hasSize(4);

        Account aliceAud = require(accounts.findById(ALICE_AUD_ID));
        assertThat(aliceAud.id()).isEqualTo(ALICE_AUD_ID);
        assertThat(aliceAud.participant().id()).isEqualTo(ALICE_ID);
        assertThat(aliceAud.participant().name()).isEqualTo("Alice");
        assertThat(aliceAud.asset().id()).isEqualTo(AUD_ID);
        assertThat(aliceAud.asset().code()).isEqualTo("AUD");
        assertThat(aliceAud.asset().type()).isEqualTo(AssetType.CASH);
        assertThat(aliceAud.openingBalance()).isEqualTo(100000);
        assertThat(aliceAud.currentBalance()).isEqualTo(100000);

        Account aliceEq1 = require(accounts.findById(ALICE_EQ1_ID));
        assertThat(aliceEq1.participant().name()).isEqualTo("Alice");
        assertThat(aliceEq1.asset().code()).isEqualTo("EQ1");
        assertThat(aliceEq1.asset().type()).isEqualTo(AssetType.SECURITY);
        assertThat(aliceEq1.openingBalance()).isEqualTo(0);
        assertThat(aliceEq1.currentBalance()).isEqualTo(0);

        Account bobAud = require(accounts.findById(BOB_AUD_ID));
        assertThat(bobAud.participant().id()).isEqualTo(BOB_ID);
        assertThat(bobAud.participant().name()).isEqualTo("Bob");
        assertThat(bobAud.asset().code()).isEqualTo("AUD");
        assertThat(bobAud.asset().type()).isEqualTo(AssetType.CASH);
        assertThat(bobAud.openingBalance()).isEqualTo(0);
        assertThat(bobAud.currentBalance()).isEqualTo(0);

        Account bobEq1 = require(accounts.findById(BOB_EQ1_ID));
        assertThat(bobEq1.participant().name()).isEqualTo("Bob");
        assertThat(bobEq1.asset().id()).isEqualTo(EQ1_ID);
        assertThat(bobEq1.asset().code()).isEqualTo("EQ1");
        assertThat(bobEq1.asset().type()).isEqualTo(AssetType.SECURITY);
        assertThat(bobEq1.openingBalance()).isEqualTo(10);
        assertThat(bobEq1.currentBalance()).isEqualTo(10);

        assertThat(all)
                .extracting(Account::id)
                .containsExactlyInAnyOrder(ALICE_AUD_ID, ALICE_EQ1_ID, BOB_AUD_ID, BOB_EQ1_ID);

        assertThat(accounts.findById(UNKNOWN_ACCOUNT_ID)).isEmpty();

        assertThat(balanceRows()).containsExactlyElementsOf(before);
        assertThat(tableCount("participant")).isEqualTo(2);
        assertThat(tableCount("asset")).isEqualTo(2);
        assertThat(tableCount("account")).isEqualTo(4);
    }

    private static Account require(Optional<Account> account) {
        assertThat(account).isPresent();
        return account.orElseThrow();
    }

    private List<BalanceRow> balanceRows() {
        return jdbc.query(
                """
                SELECT id, opening_balance, current_balance
                FROM account
                ORDER BY id
                """,
                Map.of(),
                (rs, rowNum) -> new BalanceRow(
                        rs.getObject("id", UUID.class),
                        rs.getLong("opening_balance"),
                        rs.getLong("current_balance")));
    }

    private Integer tableCount(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Map.of(), Integer.class);
    }

    private record BalanceRow(UUID id, long openingBalance, long currentBalance) {
    }
}
