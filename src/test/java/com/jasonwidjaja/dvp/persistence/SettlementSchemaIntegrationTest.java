package com.jasonwidjaja.dvp.persistence;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID TRADE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_TRADE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID JOURNAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID OTHER_JOURNAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-00000000c0ff");
    private static final UUID EQ2_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    private static final UUID ALICE_EQ2_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID BOB_EQ2_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 20);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void journalCanBeInsertedForExistingTrade() {
        insertReadyTrade(TRADE_ID, "T-001");
        commitValidSettlement(TRADE_ID, JOURNAL_ID);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM settlement_journal WHERE id = :id",
                Map.of("id", JOURNAL_ID));
        assertThat(row.get("trade_id")).isEqualTo(TRADE_ID);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM trade WHERE id = :id",
                        Map.of("id", TRADE_ID),
                        String.class))
                .isEqualTo("SETTLED");
        assertThat(jdbc.queryForObject(
                        "SELECT journal_id FROM trade WHERE id = :id",
                        Map.of("id", TRADE_ID),
                        UUID.class))
                .isEqualTo(JOURNAL_ID);
    }

    @Test
    void secondJournalForTheSameTradeIsRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        commitValidSettlement(TRADE_ID, JOURNAL_ID);
        assertRejected(
                () -> jdbc.update(
                        """
                        INSERT INTO settlement_journal (id, trade_id, settled_at)
                        VALUES (:id, :tradeId, TIMESTAMPTZ '2026-09-20T00:00:00Z')
                        """,
                        Map.of("id", OTHER_JOURNAL_ID, "tradeId", TRADE_ID)),
                "settlement_journal_trade_id_unique");
    }

    @Test
    void journalForUnknownTradeIsRejected() {
        assertRejected(
                () -> jdbc.update(
                        """
                        INSERT INTO settlement_journal (id, trade_id, settled_at)
                        VALUES (:id, :tradeId, TIMESTAMPTZ '2026-09-20T00:00:00Z')
                        """,
                        Map.of("id", JOURNAL_ID, "tradeId", UNKNOWN_ID)),
                "settlement_journal_trade_fk");
    }

    @Test
    void postingSignedAmountFollowsProjectLocalDebitCreditRule() {
        insertReadyTrade(TRADE_ID, "T-001");
        commitValidSettlement(TRADE_ID, JOURNAL_ID);

        assertThat(signedAmount(DemoSeed.ALICE_AUD_ID)).isEqualTo(-50000);
        assertThat(signedAmount(DemoSeed.BOB_AUD_ID)).isEqualTo(50000);
        assertThat(signedAmount(DemoSeed.ALICE_EQ1_ID)).isEqualTo(10);
        assertThat(signedAmount(DemoSeed.BOB_EQ1_ID)).isEqualTo(-10);
    }

    @Test
    void zeroAndNegativePostingAmountsAreRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "DEBIT", 0);
                }),
                "posting_amount_positive");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "DEBIT", -1);
                }),
                "posting_amount_positive");
    }

    @Test
    void unsupportedPostingDirectionIsRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "REVERSE", 50000);
                }),
                "posting_direction_supported");
    }

    @Test
    void duplicateAccountWithinOneJournalIsRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "DEBIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "CREDIT", 50000);
                }),
                "posting_journal_account_unique");
    }

    @Test
    void postingSignedAmountCannotBeUpdated() {
        insertReadyTrade(TRADE_ID, "T-001");
        commitValidSettlement(TRADE_ID, JOURNAL_ID);
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE posting SET signed_amount = 1 WHERE journal_id = :journalId",
                        Map.of("journalId", JOURNAL_ID)))
                .isInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class)
                .hasMessageContaining("signed_amount");
    }

    @Test
    void eachApprovedAttemptOutcomeCanBeInserted() {
        insertReadyTrade(TRADE_ID, "T-001");
        insertCommandResult("settle-not-due", "SETTLE_TRADE");
        insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-not-due", "NOT_DUE", null);
        insertCommandResult("settle-cash", "SETTLE_TRADE");
        insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-cash", "INSUFFICIENT_CASH", null);
        insertCommandResult("settle-securities", "SETTLE_TRADE");
        insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-securities", "INSUFFICIENT_SECURITIES", null);

        commitValidSettlement(TRADE_ID, JOURNAL_ID);
        insertCommandResult("settle-ok", "SETTLE_TRADE");
        insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-ok", "SETTLED", JOURNAL_ID);
        insertCommandResult("settle-again", "SETTLE_TRADE");
        insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-again", "ALREADY_SETTLED", JOURNAL_ID);

        Integer attempts = jdbc.queryForObject("SELECT count(*) FROM settlement_attempt", Map.of(), Integer.class);
        assertThat(attempts).isEqualTo(5);
    }

    @Test
    void missingAccountIsNotAnAttemptOutcome() {
        insertReadyTrade(TRADE_ID, "T-001");
        insertCommandResult("settle-missing", "SETTLE_TRADE");
        assertRejected(
                () -> insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-missing", "MISSING_ACCOUNT", null),
                "settlement_attempt_outcome_supported");
    }

    @Test
    void settledAttemptWithoutJournalIsRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        insertCommandResult("settle-ok", "SETTLE_TRADE");
        assertRejected(
                () -> insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-ok", "SETTLED", null),
                "settlement_attempt_journal_consistency");
    }

    @Test
    void rejectionAttemptWithJournalIsRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        commitValidSettlement(TRADE_ID, JOURNAL_ID);
        insertCommandResult("settle-not-due", "SETTLE_TRADE");
        assertRejected(
                () -> insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-not-due", "NOT_DUE", JOURNAL_ID),
                "settlement_attempt_journal_consistency");
    }

    @Test
    void secondAttemptForTheSameCommandKeyIsRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        insertCommandResult("settle-not-due", "SETTLE_TRADE");
        insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-not-due", "NOT_DUE", null);
        assertRejected(
                () -> insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-not-due", "NOT_DUE", null),
                "settlement_attempt_command_key_unique");
    }

    @Test
    void secondSettledAttemptForTheSameTradeIsRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        commitValidSettlement(TRADE_ID, JOURNAL_ID);
        insertCommandResult("settle-ok", "SETTLE_TRADE");
        insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-ok", "SETTLED", JOURNAL_ID);
        insertCommandResult("settle-ok-2", "SETTLE_TRADE");
        assertRejected(
                () -> insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-ok-2", "SETTLED", JOURNAL_ID),
                "settlement_attempt_one_settled_per_trade");
    }

    @Test
    void attemptForUnknownCommandKeyIsRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> insertAttempt(UUID.randomUUID(), TRADE_ID, "missing-key", "NOT_DUE", null),
                "settlement_attempt_command_fk");
    }

    @Test
    void settledTradeWithoutJournalIsRejected() {
        assertRejected(
                () -> insertTrade(TRADE_ID, "T-001", "SETTLED"),
                "trade_settled_journal_consistency");
    }

    @Test
    void readyTradeWithJournalIsRejected() {
        insertReadyTrade(TRADE_ID, "T-001");
        commitJournalWithoutSettlingTrade(TRADE_ID, JOURNAL_ID);
        assertRejected(
                () -> jdbc.update(
                        "UPDATE trade SET journal_id = :journalId WHERE id = :id",
                        Map.of("journalId", JOURNAL_ID, "id", TRADE_ID)),
                "trade_settlement_transition_invalid");
    }

    @Test
    void twoTradesCannotShareOneJournal() {
        insertReadyTrade(TRADE_ID, "T-001");
        insertReadyTrade(OTHER_TRADE_ID, "T-002");
        commitValidSettlement(TRADE_ID, JOURNAL_ID);
        assertRejected(
                () -> jdbc.update(
                        """
                        UPDATE trade
                        SET status = 'SETTLED', journal_id = :journalId
                        WHERE id = :id
                        """,
                        Map.of("journalId", JOURNAL_ID, "id", OTHER_TRADE_ID)),
                "trade_journal_id_unique");
    }

    @Test
    void tradeCannotPointAtAnotherTradesJournal() {
        insertReadyTrade(TRADE_ID, "T-001");
        insertReadyTrade(OTHER_TRADE_ID, "T-002");
        commitJournalWithoutSettlingTrade(TRADE_ID, JOURNAL_ID);
        commitJournalWithoutSettlingTrade(OTHER_TRADE_ID, OTHER_JOURNAL_ID);
        assertRejected(
                () -> jdbc.update(
                        """
                        UPDATE trade
                        SET status = 'SETTLED', journal_id = :journalId
                        WHERE id = :id
                        """,
                        Map.of("journalId", OTHER_JOURNAL_ID, "id", TRADE_ID)),
                "trade_journal_same_trade_fk");
    }

    @Test
    void settleTradeOperationIsAcceptedAndUnsupportedOperationStillFails() {
        insertCommandResult("settle-T-001", "SETTLE_TRADE");
        assertThat(jdbc.queryForObject(
                        "SELECT operation FROM command_result WHERE command_key = :key",
                        Map.of("key", "settle-T-001"),
                        String.class))
                .isEqualTo("SETTLE_TRADE");
        assertRejected(
                () -> insertCommandResult("other-key", "CANCEL_TRADE"),
                "command_result_operation_supported");
    }

    @Test
    void settlementHistoryRejectsUpdateAndDelete() {
        insertReadyTrade(TRADE_ID, "T-001");
        commitValidSettlement(TRADE_ID, JOURNAL_ID);
        insertCommandResult("settle-ok", "SETTLE_TRADE");
        insertAttempt(UUID.randomUUID(), TRADE_ID, "settle-ok", "SETTLED", JOURNAL_ID);

        assertRejected(
                () -> jdbc.update(
                        "UPDATE settlement_journal SET settled_at = settled_at WHERE id = :id",
                        Map.of("id", JOURNAL_ID)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update("DELETE FROM settlement_journal WHERE id = :id", Map.of("id", JOURNAL_ID)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update(
                        "UPDATE posting SET amount = 1 WHERE journal_id = :id",
                        Map.of("id", JOURNAL_ID)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update("DELETE FROM posting WHERE journal_id = :id", Map.of("id", JOURNAL_ID)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update("DELETE FROM settlement_attempt WHERE trade_id = :id", Map.of("id", TRADE_ID)),
                "settlement_history_immutable");
    }

    @Test
    void journalWithThreePostingsFailsAtCommit() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "DEBIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.BOB_AUD_ID, "CREDIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_EQ1_ID, "CREDIT", 10);
                }),
                "settlement_journal_shape");
    }

    @Test
    void journalWhoseCashLegsDoNotNetToZeroFailsAtCommit() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "DEBIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.BOB_AUD_ID, "DEBIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_EQ1_ID, "CREDIT", 10);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.BOB_EQ1_ID, "DEBIT", 10);
                }),
                "settlement_journal_shape");
    }

    @Test
    void journalWhoseAmountsDisagreeWithTradeTermsFailsAtCommit() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertApprovedDirections(JOURNAL_ID, 1, 1);
                }),
                "settlement_journal_shape");
    }

    @Test
    void reversingAPostingDirectionFailsAtCommit() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "CREDIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.BOB_AUD_ID, "DEBIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_EQ1_ID, "DEBIT", 10);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.BOB_EQ1_ID, "CREDIT", 10);
                }),
                "settlement_journal_shape");
    }

    @Test
    void cashPostingsToNonAudAccountsFailAtCommit() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_EQ1_ID, "DEBIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.BOB_EQ1_ID, "CREDIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "CREDIT", 10);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.BOB_AUD_ID, "DEBIT", 10);
                }),
                "settlement_journal_shape");
    }

    @Test
    void securityPostingsToADifferentSecurityFailAtCommit() {
        insertEq2Accounts();
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> transactions.executeWithoutResult(status -> {
                    insertJournal(JOURNAL_ID, TRADE_ID);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.ALICE_AUD_ID, "DEBIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, DemoSeed.BOB_AUD_ID, "CREDIT", 50000);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, ALICE_EQ2_ID, "CREDIT", 10);
                    insertPosting(UUID.randomUUID(), JOURNAL_ID, BOB_EQ2_ID, "DEBIT", 10);
                }),
                "settlement_journal_shape");
    }

    @Test
    void capturedTermsAndSettledTradesCannotBeEdited() {
        insertReadyTrade(TRADE_ID, "T-001");
        assertRejected(
                () -> jdbc.update(
                        "UPDATE trade SET quantity = 11 WHERE id = :id",
                        Map.of("id", TRADE_ID)),
                "trade_terms_immutable");

        commitValidSettlement(TRADE_ID, JOURNAL_ID);
        assertRejected(
                () -> jdbc.update(
                        "UPDATE trade SET status = 'READY', journal_id = NULL WHERE id = :id",
                        Map.of("id", TRADE_ID)),
                "trade_settled_immutable");
    }

    @Test
    void testCleanupStillTruncatesSettlementTables() {
        insertReadyTrade(TRADE_ID, "T-001");
        commitValidSettlement(TRADE_ID, JOURNAL_ID);
        jdbc.update(
                """
                TRUNCATE TABLE settlement_attempt, posting, settlement_journal,
                               command_result, trade, account, participant, asset
                """,
                Map.of());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement_journal", Map.of(), Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posting", Map.of(), Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trade", Map.of(), Integer.class)).isZero();
    }

    private void commitValidSettlement(UUID tradeId, UUID journalId) {
        transactions.executeWithoutResult(status -> {
            insertJournal(journalId, tradeId);
            insertApprovedDirections(journalId, 50000, 10);
            jdbc.update(
                    """
                    UPDATE trade
                    SET status = 'SETTLED', journal_id = :journalId
                    WHERE id = :tradeId
                    """,
                    Map.of("journalId", journalId, "tradeId", tradeId));
        });
    }

    private void commitJournalWithoutSettlingTrade(UUID tradeId, UUID journalId) {
        transactions.executeWithoutResult(status -> {
            insertJournal(journalId, tradeId);
            insertApprovedDirections(journalId, 50000, 10);
        });
    }

    private void insertApprovedDirections(UUID journalId, long cashAmount, long quantity) {
        insertPosting(UUID.randomUUID(), journalId, DemoSeed.ALICE_AUD_ID, "DEBIT", cashAmount);
        insertPosting(UUID.randomUUID(), journalId, DemoSeed.BOB_AUD_ID, "CREDIT", cashAmount);
        insertPosting(UUID.randomUUID(), journalId, DemoSeed.ALICE_EQ1_ID, "CREDIT", quantity);
        insertPosting(UUID.randomUUID(), journalId, DemoSeed.BOB_EQ1_ID, "DEBIT", quantity);
    }

    private void insertReadyTrade(UUID id, String externalTradeId) {
        insertTrade(id, externalTradeId, "READY");
    }

    private void insertTrade(UUID id, String externalTradeId, String status) {
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
                    :externalTradeId,
                    :buyerId,
                    :sellerId,
                    :securityId,
                    10,
                    50000,
                    :settlementDate,
                    :status
                )
                """,
                Map.of(
                        "id", id,
                        "externalTradeId", externalTradeId,
                        "buyerId", DemoSeed.ALICE_ID,
                        "sellerId", DemoSeed.BOB_ID,
                        "securityId", DemoSeed.EQ1_ID,
                        "settlementDate", BUSINESS_DATE,
                        "status", status));
    }

    private void insertJournal(UUID id, UUID tradeId) {
        jdbc.update(
                """
                INSERT INTO settlement_journal (id, trade_id, settled_at)
                VALUES (:id, :tradeId, TIMESTAMPTZ '2026-09-20T00:00:00Z')
                """,
                Map.of("id", id, "tradeId", tradeId));
    }

    private void insertPosting(UUID id, UUID journalId, UUID accountId, String direction, long amount) {
        jdbc.update(
                """
                INSERT INTO posting (id, journal_id, account_id, direction, amount)
                VALUES (:id, :journalId, :accountId, :direction, :amount)
                """,
                Map.of(
                        "id", id,
                        "journalId", journalId,
                        "accountId", accountId,
                        "direction", direction,
                        "amount", amount));
    }

    private void insertCommandResult(String commandKey, String operation) {
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
                    :commandKey,
                    :operation,
                    :requestIdentity,
                    201,
                    '{}',
                    '/v1/journals/1'
                )
                """,
                Map.of(
                        "commandKey", commandKey,
                        "operation", operation,
                        "requestIdentity", "12:SETTLE_TRADE,36:" + TRADE_ID + ","));
    }

    private void insertAttempt(UUID id, UUID tradeId, String commandKey, String outcome, UUID journalId) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("tradeId", tradeId);
        params.put("commandKey", commandKey);
        params.put("outcome", outcome);
        params.put("journalId", journalId);
        params.put("businessDate", BUSINESS_DATE);
        jdbc.update(
                """
                INSERT INTO settlement_attempt (
                    id,
                    trade_id,
                    command_key,
                    outcome,
                    journal_id,
                    business_date,
                    decided_at
                ) VALUES (
                    :id,
                    :tradeId,
                    :commandKey,
                    :outcome,
                    :journalId,
                    :businessDate,
                    TIMESTAMPTZ '2026-09-20T00:00:00Z'
                )
                """,
                params);
    }

    private void insertEq2Accounts() {
        jdbc.update(
                "INSERT INTO asset (id, code, type) VALUES (:id, 'EQ2', 'SECURITY')",
                Map.of("id", EQ2_ID));
        jdbc.update(
                """
                INSERT INTO account (id, participant_id, asset_id, opening_balance, current_balance)
                VALUES (:id, :participantId, :assetId, 0, 0)
                """,
                Map.of("id", ALICE_EQ2_ID, "participantId", DemoSeed.ALICE_ID, "assetId", EQ2_ID));
        jdbc.update(
                """
                INSERT INTO account (id, participant_id, asset_id, opening_balance, current_balance)
                VALUES (:id, :participantId, :assetId, 10, 10)
                """,
                Map.of("id", BOB_EQ2_ID, "participantId", DemoSeed.BOB_ID, "assetId", EQ2_ID));
    }

    private long signedAmount(UUID accountId) {
        return jdbc.queryForObject(
                """
                SELECT signed_amount
                FROM posting
                WHERE journal_id = :journalId AND account_id = :accountId
                """,
                Map.of("journalId", JOURNAL_ID, "accountId", accountId),
                Long.class);
    }

    private static void assertRejected(Runnable statement, String constraint) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    Throwable root = ((DataIntegrityViolationException) ex).getMostSpecificCause();
                    assertThat(root).isInstanceOf(PSQLException.class);
                    assertThat(root.getMessage()).contains(constraint);
                });
    }
}
