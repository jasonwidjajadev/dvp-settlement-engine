package com.jasonwidjaja.dvp.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.domain.TradeTerms;

@Repository
public class TradeRepository {

    private static final String TRADE_COLUMNS = """
            id,
            external_trade_id,
            buyer_id,
            seller_id,
            security_id,
            quantity,
            cash_amount,
            settlement_date,
            status
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT
            """ + TRADE_COLUMNS + """
            FROM trade
            WHERE id = :tradeId
            """;

    private static final String FIND_BY_EXTERNAL_TRADE_ID_SQL = """
            SELECT
            """ + TRADE_COLUMNS + """
            FROM trade
            WHERE external_trade_id = :externalTradeId
            """;

    private static final String INSERT_IF_ABSENT_SQL = """
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
                :quantity,
                :cashAmount,
                :settlementDate,
                :status
            )
            ON CONFLICT (external_trade_id) DO NOTHING
            RETURNING
            """ + TRADE_COLUMNS;

    private final NamedParameterJdbcTemplate jdbc;

    public TradeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Trade> findById(UUID tradeId) {
        return queryOne(FIND_BY_ID_SQL, Map.of("tradeId", tradeId));
    }

    public Optional<Trade> findByExternalTradeId(String externalTradeId) {
        return queryOne(FIND_BY_EXTERNAL_TRADE_ID_SQL, Map.of("externalTradeId", externalTradeId));
    }

    public Optional<Trade> insertIfAbsent(TradeTerms terms) {
        return queryOne(
                INSERT_IF_ABSENT_SQL,
                Map.of(
                        "id", UUID.randomUUID(),
                        "externalTradeId", terms.externalTradeId(),
                        "buyerId", terms.buyerId(),
                        "sellerId", terms.sellerId(),
                        "securityId", terms.securityId(),
                        "quantity", terms.quantity(),
                        "cashAmount", terms.cashAmount(),
                        "settlementDate", terms.settlementDate(),
                        "status", TradeStatus.READY.name()));
    }

    private Optional<Trade> queryOne(String sql, Map<String, ?> params) {
        List<Trade> trades = jdbc.query(sql, params, TradeRepository::mapTrade);
        return trades.stream().findFirst();
    }

    private static Trade mapTrade(ResultSet rs, int rowNum) throws SQLException {
        TradeTerms terms = new TradeTerms(
                rs.getString("external_trade_id"),
                rs.getObject("buyer_id", UUID.class),
                rs.getObject("seller_id", UUID.class),
                rs.getObject("security_id", UUID.class),
                rs.getLong("quantity"),
                rs.getLong("cash_amount"),
                rs.getObject("settlement_date", LocalDate.class));
        return new Trade(
                rs.getObject("id", UUID.class),
                terms,
                TradeStatus.valueOf(rs.getString("status")));
    }
}
