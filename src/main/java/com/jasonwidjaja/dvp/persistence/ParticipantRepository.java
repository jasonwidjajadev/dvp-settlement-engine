package com.jasonwidjaja.dvp.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipantRepository {

    private static final String EXISTS_BY_ID_SQL = """
            SELECT id
            FROM participant
            WHERE id = :participantId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ParticipantRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsById(UUID participantId) {
        List<UUID> ids = jdbc.query(
                EXISTS_BY_ID_SQL,
                Map.of("participantId", participantId),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        return !ids.isEmpty();
    }
}
