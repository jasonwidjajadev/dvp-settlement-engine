package com.jasonwidjaja.dvp.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.jasonwidjaja.dvp.domain.SettlementAttempt;
import com.jasonwidjaja.dvp.domain.SettlementOutcome;

@Repository
public class SettlementAttemptRepository {

    private static final String INSERT_SQL = """
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
                :decidedAt
            )
            """;

    private static final String FIND_BY_TRADE_ID_SQL = """
            SELECT
                id,
                trade_id,
                command_key,
                outcome,
                journal_id,
                business_date,
                decided_at
            FROM settlement_attempt
            WHERE trade_id = :tradeId
            ORDER BY decided_at, id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SettlementAttemptRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SettlementAttempt insert(
            UUID tradeId,
            String commandKey,
            SettlementOutcome outcome,
            UUID journalId,
            LocalDate businessDate
    ) {
        UUID id = UUID.randomUUID();
        Instant decidedAt = Instant.now();
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("tradeId", tradeId);
        params.put("commandKey", commandKey);
        params.put("outcome", outcome.name());
        params.put("journalId", journalId);
        params.put("businessDate", businessDate);
        params.put("decidedAt", decidedAt.atOffset(ZoneOffset.UTC));
        jdbc.update(INSERT_SQL, params);
        return new SettlementAttempt(id, tradeId, commandKey, outcome, journalId, businessDate, decidedAt);
    }

    public List<SettlementAttempt> findByTradeId(UUID tradeId) {
        return jdbc.query(
                FIND_BY_TRADE_ID_SQL,
                Map.of("tradeId", tradeId),
                SettlementAttemptRepository::mapAttempt);
    }

    private static SettlementAttempt mapAttempt(ResultSet rs, int rowNum) throws SQLException {
        return new SettlementAttempt(
                rs.getObject("id", UUID.class),
                rs.getObject("trade_id", UUID.class),
                rs.getString("command_key"),
                SettlementOutcome.valueOf(rs.getString("outcome")),
                rs.getObject("journal_id", UUID.class),
                rs.getObject("business_date", LocalDate.class),
                rs.getObject("decided_at", OffsetDateTime.class).toInstant());
    }
}
