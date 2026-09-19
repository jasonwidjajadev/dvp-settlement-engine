package com.jasonwidjaja.dvp.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandResultRepositoryFinalizeTest {

    @Test
    void finalizeFailsWhenUpdateAffectsZeroRows() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), anyMap())).thenReturn(0);
        CommandResultRepository commandResults = new CommandResultRepository(jdbc);

        assertThatThrownBy(() -> commandResults.finalize("capture-T-001", 201, "{}", "/v1/trades/1"))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("exactly one unfinished command_result");
    }

    @Test
    void finalizeFailsWhenUpdateAffectsMultipleRows() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), anyMap())).thenReturn(2);
        CommandResultRepository commandResults = new CommandResultRepository(jdbc);

        assertThatThrownBy(() -> commandResults.finalize("capture-T-001", 201, "{}", "/v1/trades/1"))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("exactly one unfinished command_result");
    }
}
