package com.jasonwidjaja.dvp.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.jasonwidjaja.dvp.domain.CommandResult;

@Repository
public class CommandResultRepository {

    private static final String CLAIM_SQL = """
            INSERT INTO command_result (
                command_key,
                operation,
                request_identity
            ) VALUES (
                :commandKey,
                :operation,
                :requestIdentity
            )
            ON CONFLICT (command_key) DO NOTHING
            """;

    private static final String FIND_BY_COMMAND_KEY_SQL = """
            SELECT
                command_key,
                operation,
                request_identity,
                http_status,
                response_body,
                location
            FROM command_result
            WHERE command_key = :commandKey
            """;

    private static final String FINALIZE_SQL = """
            UPDATE command_result
            SET
                http_status = :httpStatus,
                response_body = :responseBody,
                location = :location
            WHERE command_key = :commandKey
              AND http_status IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CommandResultRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(String commandKey, String operation, String requestIdentity) {
        int inserted = jdbc.update(
                CLAIM_SQL,
                Map.of(
                        "commandKey", commandKey,
                        "operation", operation,
                        "requestIdentity", requestIdentity));
        return inserted == 1;
    }

    public Optional<CommandResult> findByCommandKey(String commandKey) {
        List<CommandResult> results = jdbc.query(
                FIND_BY_COMMAND_KEY_SQL,
                Map.of("commandKey", commandKey),
                CommandResultRepository::mapCommandResult);
        return results.stream().findFirst();
    }

    public void finalize(String commandKey, int httpStatus, String responseBody, String location) {
        Map<String, Object> params = new HashMap<>();
        params.put("commandKey", commandKey);
        params.put("httpStatus", httpStatus);
        params.put("responseBody", responseBody);
        params.put("location", location);
        int updated = jdbc.update(FINALIZE_SQL, params);
        if (updated != 1) {
            throw new IncorrectResultSizeDataAccessException(
                    "Expected to finalize exactly one unfinished command_result for key '" + commandKey + "'",
                    1,
                    updated);
        }
    }

    private static CommandResult mapCommandResult(ResultSet rs, int rowNum) throws SQLException {
        Integer httpStatus = rs.getObject("http_status", Integer.class);
        return new CommandResult(
                rs.getString("command_key"),
                rs.getString("operation"),
                rs.getString("request_identity"),
                httpStatus,
                rs.getString("response_body"),
                rs.getString("location"));
    }
}
