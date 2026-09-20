package com.jasonwidjaja.dvp.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Independent PostgreSQL checks on committed financial state.
 * These queries do not call settlement services or reconstruct from Java objects.
 */
public final class FinancialInvariantChecks {

    static final String MISMATCHED_BALANCES_SQL = """
            SELECT account.id
            FROM account
            LEFT JOIN posting ON posting.account_id = account.id
            GROUP BY account.id, account.opening_balance, account.current_balance
            HAVING account.current_balance
                 <> account.opening_balance + COALESCE(SUM(posting.signed_amount), 0)
            """;

    private static final String NEGATIVE_BALANCES_SQL = """
            SELECT id
            FROM account
            WHERE current_balance < 0
            """;

    private static final String ASSET_TOTAL_NOT_CONSERVED_SQL = """
            SELECT asset.code
            FROM account
            INNER JOIN asset ON asset.id = account.asset_id
            GROUP BY asset.code
            HAVING SUM(account.current_balance) <> SUM(account.opening_balance)
            """;

    private static final String TRADES_WITH_MULTIPLE_JOURNALS_SQL = """
            SELECT trade_id
            FROM settlement_journal
            GROUP BY trade_id
            HAVING COUNT(*) > 1
            """;

    private static final String JOURNALS_NOT_EXACTLY_FOUR_POSTINGS_SQL = """
            SELECT settlement_journal.id
            FROM settlement_journal
            LEFT JOIN posting ON posting.journal_id = settlement_journal.id
            GROUP BY settlement_journal.id
            HAVING COUNT(posting.id) <> 4
            """;

    private static final String JOURNALS_WITHOUT_TWO_ASSETS_SQL = """
            SELECT posting.journal_id
            FROM posting
            INNER JOIN account ON account.id = posting.account_id
            GROUP BY posting.journal_id
            HAVING COUNT(DISTINCT account.asset_id) <> 2
            """;

    private static final String JOURNALS_WITH_NONZERO_ASSET_NET_SQL = """
            SELECT posting.journal_id
            FROM posting
            INNER JOIN account ON account.id = posting.account_id
            GROUP BY posting.journal_id, account.asset_id
            HAVING SUM(posting.signed_amount) <> 0
            """;

    private static final String JOURNALS_WITH_INCORRECT_POSTINGS_SQL = """
            WITH expected AS (
                SELECT
                    settlement_journal.id AS journal_id,
                    buyer_cash.id AS buyer_cash_id,
                    seller_cash.id AS seller_cash_id,
                    buyer_security.id AS buyer_security_id,
                    seller_security.id AS seller_security_id,
                    trade.cash_amount,
                    trade.quantity
                FROM settlement_journal
                INNER JOIN trade ON trade.id = settlement_journal.trade_id
                INNER JOIN asset cash ON cash.code = 'AUD' AND cash.type = 'CASH'
                INNER JOIN account buyer_cash
                    ON buyer_cash.participant_id = trade.buyer_id
                   AND buyer_cash.asset_id = cash.id
                INNER JOIN account seller_cash
                    ON seller_cash.participant_id = trade.seller_id
                   AND seller_cash.asset_id = cash.id
                INNER JOIN account buyer_security
                    ON buyer_security.participant_id = trade.buyer_id
                   AND buyer_security.asset_id = trade.security_id
                INNER JOIN account seller_security
                    ON seller_security.participant_id = trade.seller_id
                   AND seller_security.asset_id = trade.security_id
            )
            SELECT expected.journal_id
            FROM expected
            WHERE NOT EXISTS (
                    SELECT 1 FROM posting
                    WHERE journal_id = expected.journal_id
                      AND account_id = expected.buyer_cash_id
                      AND direction = 'DEBIT'
                      AND amount = expected.cash_amount
                )
               OR NOT EXISTS (
                    SELECT 1 FROM posting
                    WHERE journal_id = expected.journal_id
                      AND account_id = expected.seller_cash_id
                      AND direction = 'CREDIT'
                      AND amount = expected.cash_amount
                )
               OR NOT EXISTS (
                    SELECT 1 FROM posting
                    WHERE journal_id = expected.journal_id
                      AND account_id = expected.buyer_security_id
                      AND direction = 'CREDIT'
                      AND amount = expected.quantity
                )
               OR NOT EXISTS (
                    SELECT 1 FROM posting
                    WHERE journal_id = expected.journal_id
                      AND account_id = expected.seller_security_id
                      AND direction = 'DEBIT'
                      AND amount = expected.quantity
                )
            """;

    private FinancialInvariantChecks() {
    }

    public static List<UUID> mismatchedAccountIds(NamedParameterJdbcTemplate jdbc) {
        return jdbc.query(MISMATCHED_BALANCES_SQL, Map.of(), (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    public static void assertReconstructionHolds(NamedParameterJdbcTemplate jdbc) {
        assertThat(mismatchedAccountIds(jdbc)).isEmpty();
    }

    public static void assertConservationAndJournalShapeHold(NamedParameterJdbcTemplate jdbc) {
        assertThat(ids(jdbc, NEGATIVE_BALANCES_SQL)).isEmpty();
        assertThat(codes(jdbc, ASSET_TOTAL_NOT_CONSERVED_SQL)).isEmpty();
        assertThat(ids(jdbc, TRADES_WITH_MULTIPLE_JOURNALS_SQL)).isEmpty();
        assertThat(ids(jdbc, JOURNALS_NOT_EXACTLY_FOUR_POSTINGS_SQL)).isEmpty();
        assertThat(ids(jdbc, JOURNALS_WITHOUT_TWO_ASSETS_SQL)).isEmpty();
        assertThat(ids(jdbc, JOURNALS_WITH_NONZERO_ASSET_NET_SQL)).isEmpty();
        assertThat(ids(jdbc, JOURNALS_WITH_INCORRECT_POSTINGS_SQL)).isEmpty();
    }

    public static long assetCurrentTotal(NamedParameterJdbcTemplate jdbc, String assetCode) {
        Long total = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(account.current_balance), 0)
                FROM account
                INNER JOIN asset ON asset.id = account.asset_id
                WHERE asset.code = :code
                """,
                Map.of("code", assetCode),
                Long.class);
        return total == null ? 0 : total;
    }

    public static long assetOpeningTotal(NamedParameterJdbcTemplate jdbc, String assetCode) {
        Long total = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(account.opening_balance), 0)
                FROM account
                INNER JOIN asset ON asset.id = account.asset_id
                WHERE asset.code = :code
                """,
                Map.of("code", assetCode),
                Long.class);
        return total == null ? 0 : total;
    }

    public static long journalCount(NamedParameterJdbcTemplate jdbc) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM settlement_journal", Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    public static long postingCount(NamedParameterJdbcTemplate jdbc) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM posting", Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    private static List<UUID> ids(NamedParameterJdbcTemplate jdbc, String sql) {
        return jdbc.query(sql, Map.of(), (rs, rowNum) -> rs.getObject(1, UUID.class));
    }

    private static List<String> codes(NamedParameterJdbcTemplate jdbc, String sql) {
        return jdbc.query(sql, Map.of(), (rs, rowNum) -> rs.getString(1));
    }
}
