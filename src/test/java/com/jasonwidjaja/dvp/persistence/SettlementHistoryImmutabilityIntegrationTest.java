package com.jasonwidjaja.dvp.persistence;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;

import com.jasonwidjaja.dvp.api.AccountController;
import com.jasonwidjaja.dvp.api.CommandController;
import com.jasonwidjaja.dvp.api.JournalController;
import com.jasonwidjaja.dvp.api.TradeController;
import com.jasonwidjaja.dvp.application.BusinessCalendar;
import com.jasonwidjaja.dvp.application.SettleTradeService;
import com.jasonwidjaja.dvp.domain.SettleCommand;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementHistoryImmutabilityIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private SettleTradeService settle;

    @Autowired
    private TradeRepository trades;

    @Autowired
    private BusinessCalendar calendar;

    private UUID tradeId;
    private UUID journalId;
    private UUID postingId;
    private UUID attemptId;

    @BeforeEach
    void seedAndSettleAliceBobTrade() {
        DemoSeed.apply(dataSource);
        Trade trade = trades.insertIfAbsent(new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                calendar.businessDate())).orElseThrow();
        settle.settle(new SettleCommand("settle-T-001", trade.id()));
        tradeId = trade.id();
        journalId = jdbc.queryForObject(
                "SELECT id FROM settlement_journal WHERE trade_id = :tradeId",
                Map.of("tradeId", tradeId),
                UUID.class);
        postingId = jdbc.queryForObject(
                "SELECT id FROM posting WHERE journal_id = :journalId ORDER BY id LIMIT 1",
                Map.of("journalId", journalId),
                UUID.class);
        attemptId = jdbc.queryForObject(
                "SELECT id FROM settlement_attempt WHERE trade_id = :tradeId",
                Map.of("tradeId", tradeId),
                UUID.class);
    }

    @Test
    void databaseRejectsSettlementHistoryMutations() {
        assertRejected(
                () -> jdbc.update("UPDATE posting SET amount = 1 WHERE id = :id", Map.of("id", postingId)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update(
                        "UPDATE posting SET direction = 'CREDIT' WHERE id = :id",
                        Map.of("id", postingId)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update("DELETE FROM posting WHERE id = :id", Map.of("id", postingId)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update(
                        "UPDATE settlement_journal SET settled_at = settled_at WHERE id = :id",
                        Map.of("id", journalId)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update("DELETE FROM settlement_journal WHERE id = :id", Map.of("id", journalId)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update(
                        "UPDATE settlement_attempt SET outcome = 'NOT_DUE' WHERE id = :id",
                        Map.of("id", attemptId)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update("DELETE FROM settlement_attempt WHERE id = :id", Map.of("id", attemptId)),
                "settlement_history_immutable");
        assertRejected(
                () -> jdbc.update(
                        "UPDATE trade SET status = 'READY', journal_id = NULL WHERE id = :id",
                        Map.of("id", tradeId)),
                "trade_settled_immutable");
        assertRejected(
                () -> jdbc.update("UPDATE trade SET quantity = 11 WHERE id = :id", Map.of("id", tradeId)),
                "trade_terms_immutable");
        assertRejected(
                () -> jdbc.update(
                        """
                        UPDATE command_result
                        SET http_status = 409, response_body = '{"code":"CONFLICT"}'
                        WHERE command_key = :key
                        """,
                        Map.of("key", "settle-T-001")),
                "command_result_completed_immutable");
    }

    @Test
    void repositoriesAndControllersExposeNoHistoryMutationApi() {
        assertThat(publicInstanceMethods(SettlementJournalRepository.class)).containsExactly(
                "findJournalById",
                "findJournalByTradeId",
                "findPostingsByJournalId",
                "insertJournal",
                "insertPostings");
        assertThat(publicInstanceMethods(SettlementAttemptRepository.class)).containsExactly(
                "findByTradeId",
                "insert");
        assertThat(publicInstanceMethods(TradeRepository.class)).containsExactly(
                "findByExternalTradeId",
                "findById",
                "insertIfAbsent",
                "lockById",
                "markSettled");
        assertThat(publicInstanceMethods(CommandResultRepository.class)).containsExactly(
                "claim",
                "finalize",
                "findByCommandKey");
        assertThat(publicInstanceMethods(AccountRepository.class)).doesNotContain(
                "update",
                "delete",
                "save",
                "setCurrentBalance");

        assertNoWriteMappings(TradeController.class, JournalController.class, CommandController.class, AccountController.class);
    }

    private static List<String> publicInstanceMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .sorted()
                .toList();
    }

    private static void assertNoWriteMappings(Class<?>... types) {
        for (Class<?> type : types) {
            assertThat(Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(PutMapping.class)
                            || method.isAnnotationPresent(PatchMapping.class)
                            || method.isAnnotationPresent(DeleteMapping.class))
                    .map(Method::getName))
                    .isEmpty();
        }
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
