package com.jasonwidjaja.dvp.persistence;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConstraintIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID PARTICIPANT_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-0000-0000-00000000c0a1");
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-00000000c0ff");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void constraintFixtures() {
        jdbc.update(
                "INSERT INTO participant (id, name) VALUES (:id, 'Constraint') ON CONFLICT (id) DO NOTHING",
                Map.of("id", PARTICIPANT_ID));
        jdbc.update(
                """
                INSERT INTO asset (id, code, type)
                VALUES (:id, 'C1', 'CASH')
                ON CONFLICT (id) DO NOTHING
                """,
                Map.of("id", ASSET_ID));
    }

    @Test
    void duplicateParticipantAssetAccountIsRejected() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-00000000c0a2");
        insertAccount(firstId, PARTICIPANT_ID, ASSET_ID, 0, 0);
        try {
            assertRejected(
                    () -> insertAccount(UUID.randomUUID(), PARTICIPANT_ID, ASSET_ID, 1, 1),
                    "account_participant_asset_unique");
        } finally {
            jdbc.update("DELETE FROM account WHERE id = :id", Map.of("id", firstId));
        }
    }

    @Test
    void negativeOpeningBalanceIsRejected() {
        assertRejected(
                () -> insertAccount(UUID.randomUUID(), PARTICIPANT_ID, ASSET_ID, -1, 0),
                "account_opening_balance_non_negative");
    }

    @Test
    void negativeCurrentBalanceIsRejected() {
        assertRejected(
                () -> insertAccount(UUID.randomUUID(), PARTICIPANT_ID, ASSET_ID, 0, -1),
                "account_current_balance_non_negative");
    }

    @Test
    void unknownParticipantIsRejected() {
        assertRejected(
                () -> insertAccount(UUID.randomUUID(), UNKNOWN_ID, ASSET_ID, 0, 0),
                "account_participant_fk");
    }

    @Test
    void unknownAssetIsRejected() {
        assertRejected(
                () -> insertAccount(UUID.randomUUID(), PARTICIPANT_ID, UNKNOWN_ID, 0, 0),
                "account_asset_fk");
    }

    @Test
    void duplicateAssetCodeIsRejected() {
        assertRejected(
                () -> jdbc.update(
                        "INSERT INTO asset (id, code, type) VALUES (:id, 'C1', 'SECURITY')",
                        Map.of("id", UUID.randomUUID())),
                "asset_code_unique");
    }

    private void insertAccount(
            UUID id,
            UUID participantId,
            UUID assetId,
            long openingBalance,
            long currentBalance
    ) {
        jdbc.update(
                """
                INSERT INTO account (
                    id,
                    participant_id,
                    asset_id,
                    opening_balance,
                    current_balance
                ) VALUES (
                    :id,
                    :participantId,
                    :assetId,
                    :openingBalance,
                    :currentBalance
                )
                """,
                Map.of(
                        "id", id,
                        "participantId", participantId,
                        "assetId", assetId,
                        "openingBalance", openingBalance,
                        "currentBalance", currentBalance));
    }

    private static void assertRejected(Runnable insert, String constraint) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    Throwable root = ((DataIntegrityViolationException) ex).getMostSpecificCause();
                    assertThat(root).isInstanceOf(PSQLException.class);
                    assertThat(root.getMessage()).contains(constraint);
                });
    }
}
