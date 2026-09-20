package com.jasonwidjaja.dvp.persistence;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jasonwidjaja.dvp.domain.Posting;
import com.jasonwidjaja.dvp.domain.PostingDirection;
import com.jasonwidjaja.dvp.domain.SettleRequestIdentity;
import com.jasonwidjaja.dvp.domain.SettlementAttempt;
import com.jasonwidjaja.dvp.domain.SettlementJournal;
import com.jasonwidjaja.dvp.domain.SettlementOutcome;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SettlementAttemptRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 20);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TradeRepository trades;

    @Autowired
    private SettlementJournalRepository journals;

    @Autowired
    private SettlementAttemptRepository attempts;

    @Autowired
    private CommandResultRepository commandResults;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void eachApprovedOutcomeCanBeRecorded() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        SettlementJournal journal = persistValidJournal(trade.id());
        claim("settle-not-due", trade.id());
        claim("settle-cash", trade.id());
        claim("settle-securities", trade.id());
        claim("settle-ok", trade.id());
        claim("settle-already", trade.id());

        SettlementAttempt notDue = attempts.insert(
                trade.id(), "settle-not-due", SettlementOutcome.NOT_DUE, null, BUSINESS_DATE);
        SettlementAttempt cash = attempts.insert(
                trade.id(), "settle-cash", SettlementOutcome.INSUFFICIENT_CASH, null, BUSINESS_DATE);
        SettlementAttempt securities = attempts.insert(
                trade.id(), "settle-securities", SettlementOutcome.INSUFFICIENT_SECURITIES, null, BUSINESS_DATE);
        SettlementAttempt settled = attempts.insert(
                trade.id(), "settle-ok", SettlementOutcome.SETTLED, journal.id(), BUSINESS_DATE);
        SettlementAttempt already = attempts.insert(
                trade.id(), "settle-already", SettlementOutcome.ALREADY_SETTLED, journal.id(), BUSINESS_DATE);

        List<SettlementAttempt> stored = attempts.findByTradeId(trade.id());
        assertThat(stored)
                .extracting(SettlementAttempt::outcome)
                .containsExactlyInAnyOrder(
                        SettlementOutcome.NOT_DUE,
                        SettlementOutcome.INSUFFICIENT_CASH,
                        SettlementOutcome.INSUFFICIENT_SECURITIES,
                        SettlementOutcome.SETTLED,
                        SettlementOutcome.ALREADY_SETTLED);
        assertThat(stored).extracting(SettlementAttempt::id)
                .containsExactlyInAnyOrder(notDue.id(), cash.id(), securities.id(), settled.id(), already.id());
        assertThat(notDue.journalId()).isNull();
        assertThat(cash.journalId()).isNull();
        assertThat(securities.journalId()).isNull();
        assertThat(settled.journalId()).isEqualTo(journal.id());
        assertThat(already.journalId()).isEqualTo(journal.id());
        assertThat(stored).allMatch(attempt -> attempt.tradeId().equals(trade.id()));
        assertThat(stored).allMatch(attempt -> attempt.businessDate().equals(BUSINESS_DATE));
        assertThat(stored.getFirst().decidedAt()).isCloseTo(notDue.decidedAt(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void rejectionIsRecordedWithoutAJournal() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        claim("settle-T-001", trade.id());

        SettlementAttempt stored = attempts.insert(
                trade.id(),
                "settle-T-001",
                SettlementOutcome.INSUFFICIENT_CASH,
                null,
                BUSINESS_DATE);

        assertThat(stored.outcome()).isEqualTo(SettlementOutcome.INSUFFICIENT_CASH);
        assertThat(stored.journalId()).isNull();
        assertThat(attempts.findByTradeId(trade.id())).containsExactly(stored);
    }

    @Test
    void settledAttemptIsRecordedWithItsJournal() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        SettlementJournal journal = persistValidJournal(trade.id());
        claim("settle-T-001", trade.id());

        SettlementAttempt stored = attempts.insert(
                trade.id(),
                "settle-T-001",
                SettlementOutcome.SETTLED,
                journal.id(),
                BUSINESS_DATE);

        assertThat(stored.outcome()).isEqualTo(SettlementOutcome.SETTLED);
        assertThat(stored.journalId()).isEqualTo(journal.id());
        assertThat(attempts.findByTradeId(trade.id())).containsExactly(stored);
    }

    @Test
    void secondAttemptForTheSameCommandKeyFails() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        claim("settle-T-001", trade.id());
        attempts.insert(trade.id(), "settle-T-001", SettlementOutcome.NOT_DUE, null, BUSINESS_DATE);

        assertThatThrownBy(() -> attempts.insert(
                        trade.id(),
                        "settle-T-001",
                        SettlementOutcome.INSUFFICIENT_CASH,
                        null,
                        BUSINESS_DATE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    Throwable root = ((DataIntegrityViolationException) ex).getMostSpecificCause();
                    assertThat(root).isInstanceOf(PSQLException.class);
                    assertThat(root.getMessage()).contains("settlement_attempt_command_key_unique");
                });

        assertThat(attempts.findByTradeId(trade.id())).hasSize(1);
        assertThat(attempts.findByTradeId(trade.id()).getFirst().outcome()).isEqualTo(SettlementOutcome.NOT_DUE);
    }

    @Test
    void attemptsReadBackInDecisionOrder() throws InterruptedException {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        claim("settle-first", trade.id());
        claim("settle-second", trade.id());

        SettlementAttempt first = attempts.insert(
                trade.id(), "settle-first", SettlementOutcome.NOT_DUE, null, BUSINESS_DATE);
        Thread.sleep(20);
        SettlementAttempt second = attempts.insert(
                trade.id(), "settle-second", SettlementOutcome.INSUFFICIENT_CASH, null, BUSINESS_DATE);

        List<SettlementAttempt> stored = attempts.findByTradeId(trade.id());
        assertThat(stored).extracting(SettlementAttempt::id).containsExactly(first.id(), second.id());
        assertThat(stored.get(0).decidedAt()).isBeforeOrEqualTo(stored.get(1).decidedAt());
        assertThat(second.decidedAt()).isAfterOrEqualTo(first.decidedAt());
    }

    @Test
    void repositoryHasNoUpdateOrDeleteMethods() {
        assertThat(Arrays.stream(SettlementAttemptRepository.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("remove")
                        || name.startsWith("replace"));
    }

    private void claim(String commandKey, UUID tradeId) {
        assertThat(commandResults.claim(
                commandKey,
                SettleRequestIdentity.OPERATION,
                SettleRequestIdentity.of(tradeId))).isTrue();
    }

    private SettlementJournal persistValidJournal(UUID tradeId) {
        return transactions.execute(status -> {
            SettlementJournal journal = journals.insertJournal(tradeId);
            journals.insertPostings(journal.id(), aliceBobPostings());
            return journal;
        });
    }

    private static List<Posting> aliceBobPostings() {
        return List.of(
                posting(DemoSeed.ALICE_AUD_ID, PostingDirection.DEBIT, 50000),
                posting(DemoSeed.BOB_AUD_ID, PostingDirection.CREDIT, 50000),
                posting(DemoSeed.ALICE_EQ1_ID, PostingDirection.CREDIT, 10),
                posting(DemoSeed.BOB_EQ1_ID, PostingDirection.DEBIT, 10));
    }

    private static Posting posting(UUID accountId, PostingDirection direction, long amount) {
        return new Posting(UUID.randomUUID(), UUID.randomUUID(), accountId, direction, amount, 0);
    }

    private static TradeTerms aliceBuysEq1() {
        return new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 20));
    }
}
