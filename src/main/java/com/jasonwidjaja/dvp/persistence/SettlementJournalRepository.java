package com.jasonwidjaja.dvp.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.jasonwidjaja.dvp.domain.Posting;
import com.jasonwidjaja.dvp.domain.PostingDirection;
import com.jasonwidjaja.dvp.domain.SettlementJournal;

@Repository
public class SettlementJournalRepository {

    private static final String INSERT_JOURNAL_SQL = """
            INSERT INTO settlement_journal (id, trade_id, settled_at)
            VALUES (:id, :tradeId, :settledAt)
            """;

    private static final String INSERT_POSTING_SQL = """
            INSERT INTO posting (id, journal_id, account_id, direction, amount)
            VALUES (:id, :journalId, :accountId, :direction, :amount)
            """;

    private static final String JOURNAL_COLUMNS = """
            id,
            trade_id,
            settled_at
            """;

    private static final String FIND_JOURNAL_BY_ID_SQL = """
            SELECT
            """ + JOURNAL_COLUMNS + """
            FROM settlement_journal
            WHERE id = :id
            """;

    private static final String FIND_JOURNAL_BY_TRADE_ID_SQL = """
            SELECT
            """ + JOURNAL_COLUMNS + """
            FROM settlement_journal
            WHERE trade_id = :tradeId
            """;

    private static final String FIND_POSTINGS_BY_JOURNAL_ID_SQL = """
            SELECT
                posting.id,
                posting.journal_id,
                posting.account_id,
                posting.direction,
                posting.amount,
                posting.signed_amount
            FROM posting
            INNER JOIN account ON account.id = posting.account_id
            INNER JOIN asset ON asset.id = account.asset_id
            WHERE posting.journal_id = :journalId
            ORDER BY asset.code, posting.direction, posting.account_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SettlementJournalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SettlementJournal insertJournal(UUID tradeId) {
        UUID id = UUID.randomUUID();
        Instant settledAt = Instant.now();
        jdbc.update(
                INSERT_JOURNAL_SQL,
                Map.of(
                        "id", id,
                        "tradeId", tradeId,
                        "settledAt", settledAt.atOffset(ZoneOffset.UTC)));
        return new SettlementJournal(id, tradeId, settledAt);
    }

    public void insertPostings(UUID journalId, List<Posting> postings) {
        if (postings.size() != 4) {
            throw new IncorrectResultSizeDataAccessException(
                    "Expected to insert exactly four postings for journal '" + journalId + "'",
                    4,
                    postings.size());
        }
        int inserted = 0;
        for (Posting posting : postings) {
            inserted += jdbc.update(
                    INSERT_POSTING_SQL,
                    Map.of(
                            "id", UUID.randomUUID(),
                            "journalId", journalId,
                            "accountId", posting.accountId(),
                            "direction", posting.direction().name(),
                            "amount", posting.amount()));
        }
        if (inserted != 4) {
            throw new IncorrectResultSizeDataAccessException(
                    "Expected to insert exactly four postings for journal '" + journalId + "'",
                    4,
                    inserted);
        }
    }

    public Optional<SettlementJournal> findJournalById(UUID id) {
        return queryJournal(FIND_JOURNAL_BY_ID_SQL, Map.of("id", id));
    }

    public Optional<SettlementJournal> findJournalByTradeId(UUID tradeId) {
        return queryJournal(FIND_JOURNAL_BY_TRADE_ID_SQL, Map.of("tradeId", tradeId));
    }

    public List<Posting> findPostingsByJournalId(UUID journalId) {
        return jdbc.query(
                FIND_POSTINGS_BY_JOURNAL_ID_SQL,
                Map.of("journalId", journalId),
                SettlementJournalRepository::mapPosting);
    }

    private Optional<SettlementJournal> queryJournal(String sql, Map<String, ?> params) {
        List<SettlementJournal> journals = jdbc.query(sql, params, SettlementJournalRepository::mapJournal);
        return journals.stream().findFirst();
    }

    private static SettlementJournal mapJournal(ResultSet rs, int rowNum) throws SQLException {
        return new SettlementJournal(
                rs.getObject("id", UUID.class),
                rs.getObject("trade_id", UUID.class),
                rs.getObject("settled_at", OffsetDateTime.class).toInstant());
    }

    private static Posting mapPosting(ResultSet rs, int rowNum) throws SQLException {
        return new Posting(
                rs.getObject("id", UUID.class),
                rs.getObject("journal_id", UUID.class),
                rs.getObject("account_id", UUID.class),
                PostingDirection.valueOf(rs.getString("direction")),
                rs.getLong("amount"),
                rs.getLong("signed_amount"));
    }
}
