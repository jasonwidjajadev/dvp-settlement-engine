package com.jasonwidjaja.dvp.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.Asset;
import com.jasonwidjaja.dvp.domain.AssetType;
import com.jasonwidjaja.dvp.domain.Participant;

@Repository
public class AccountRepository {

    private static final String ACCOUNT_COLUMNS = """
            SELECT
                account.id AS account_id,
                account.opening_balance,
                account.current_balance,
                participant.id AS participant_id,
                participant.name AS participant_name,
                asset.id AS asset_id,
                asset.code AS asset_code,
                asset.type AS asset_type
            FROM account
            INNER JOIN participant ON participant.id = account.participant_id
            INNER JOIN asset ON asset.id = account.asset_id
            """;

    private static final String FIND_ALL_SQL = ACCOUNT_COLUMNS + " ORDER BY account.id";

    private static final String FIND_BY_ID_SQL = ACCOUNT_COLUMNS + " WHERE account.id = :accountId";

    private final NamedParameterJdbcTemplate jdbc;

    public AccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Account> findAll() {
        return jdbc.query(FIND_ALL_SQL, Map.of(), AccountRepository::mapAccount);
    }

    public Optional<Account> findById(UUID accountId) {
        List<Account> accounts = jdbc.query(
                FIND_BY_ID_SQL,
                Map.of("accountId", accountId),
                AccountRepository::mapAccount);
        return accounts.stream().findFirst();
    }

    private static Account mapAccount(ResultSet rs, int rowNum) throws SQLException {
        Participant participant = new Participant(
                rs.getObject("participant_id", UUID.class),
                rs.getString("participant_name"));
        Asset asset = new Asset(
                rs.getObject("asset_id", UUID.class),
                rs.getString("asset_code"),
                AssetType.valueOf(rs.getString("asset_type")));
        return new Account(
                rs.getObject("account_id", UUID.class),
                participant,
                asset,
                rs.getLong("opening_balance"),
                rs.getLong("current_balance"));
    }
}
