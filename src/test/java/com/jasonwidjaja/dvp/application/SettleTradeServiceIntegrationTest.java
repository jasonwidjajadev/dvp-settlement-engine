package com.jasonwidjaja.dvp.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jasonwidjaja.dvp.api.ErrorResponse;
import com.jasonwidjaja.dvp.api.SettlementResponse;
import com.jasonwidjaja.dvp.api.UnknownTradeException;
import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.CommandResult;
import com.jasonwidjaja.dvp.domain.Posting;
import com.jasonwidjaja.dvp.domain.PostingDirection;
import com.jasonwidjaja.dvp.domain.SettleCommand;
import com.jasonwidjaja.dvp.domain.SettleRequestIdentity;
import com.jasonwidjaja.dvp.domain.SettlementAttempt;
import com.jasonwidjaja.dvp.domain.SettlementJournal;
import com.jasonwidjaja.dvp.domain.SettlementOutcome;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.persistence.AccountRepository;
import com.jasonwidjaja.dvp.persistence.CommandResultRepository;
import com.jasonwidjaja.dvp.persistence.SettlementAttemptRepository;
import com.jasonwidjaja.dvp.persistence.SettlementJournalRepository;
import com.jasonwidjaja.dvp.persistence.TradeRepository;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

class SettleTradeServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 20);
    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private SettleTradeService settle;

    @Autowired
    private TradeRepository trades;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CommandResultRepository commandResults;

    @Autowired
    private SettlementAttemptRepository attempts;

    @Autowired
    private SettlementJournalRepository journals;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private BusinessCalendar calendar;

    private TransactionTemplate transactions;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void serviceUsesJdbcTransactionManagerForTheApplicationDataSource() {
        PlatformTransactionManager owned = settle.transactionManager();
        assertThat(owned).isInstanceOf(JdbcTransactionManager.class);
        assertThat(((JdbcTransactionManager) owned).getDataSource()).isSameAs(dataSource);
        assertThat(calendar.businessDate()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    void newKeyIsClaimedOnceForANotDueTrade() {
        Trade trade = insertTrade(aliceBuysEq1(10, 50000, LocalDate.of(2026, 9, 21)));

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertRejected(outcome, 422, "NOT_DUE");
        CommandResult stored = commandResults.findByCommandKey("settle-T-001").orElseThrow();
        assertThat(stored.operation()).isEqualTo(SettleRequestIdentity.OPERATION);
        assertThat(stored.hasRequestIdentity(SettleRequestIdentity.of(trade.id()))).isTrue();
        assertThat(stored.completed()).isTrue();
        assertThat(stored.httpStatus()).isEqualTo(422);
        assertThat(attempts.findByTradeId(trade.id())).hasSize(1);
    }

    @Test
    void replaySameKeyAndTradeReturnsStoredOutcomeAndWritesNothing() {
        Trade trade = insertTrade(aliceBuysEq1(10, 50000, LocalDate.of(2026, 9, 21)));
        CommandOutcome first = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        CommandOutcome replay = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertThat(replay).isEqualTo(first);
        assertThat(attempts.findByTradeId(trade.id())).hasSize(1);
        CommandResult stored = commandResults.findByCommandKey("settle-T-001").orElseThrow();
        assertThat(stored.responseBody()).isEqualTo(first.responseBody());
        assertThat(trades.findById(trade.id()).orElseThrow().status()).isEqualTo(TradeStatus.READY);
        assertThat(journals.findJournalByTradeId(trade.id())).isEmpty();
    }

    @Test
    void sameKeyAgainstADifferentTradeIsConflictWithoutAttempt() {
        Trade firstTrade = insertTrade(aliceBuysEq1(10, 50000, LocalDate.of(2026, 9, 21)));
        Trade secondTrade = insertTrade(new TradeTerms(
                "T-002",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 21)));
        CommandOutcome first = settle.settle(new SettleCommand("settle-shared", firstTrade.id()));

        CommandOutcome conflict = settle.settle(new SettleCommand("settle-shared", secondTrade.id()));

        assertRejected(conflict, 409, "IDEMPOTENCY_KEY_CONFLICT");
        CommandResult stored = commandResults.findByCommandKey("settle-shared").orElseThrow();
        assertThat(stored.httpStatus()).isEqualTo(422);
        assertThat(stored.responseBody()).isEqualTo(first.responseBody());
        assertThat(attempts.findByTradeId(firstTrade.id())).hasSize(1);
        assertThat(attempts.findByTradeId(secondTrade.id())).isEmpty();
    }

    @Test
    void unfinishedCommandCannotBeReplayed() {
        Trade trade = insertTrade(aliceBuysEq1(10, 50000, LocalDate.of(2026, 9, 21)));
        String identity = SettleRequestIdentity.of(trade.id());
        assertThat(commandResults.claim("settle-T-001", SettleRequestIdentity.OPERATION, identity)).isTrue();

        assertThatThrownBy(() -> settle.settle(new SettleCommand("settle-T-001", trade.id())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unfinished command_result");

        assertThat(attempts.findByTradeId(trade.id())).isEmpty();
        assertThat(commandResults.findByCommandKey("settle-T-001").orElseThrow().completed()).isFalse();
    }

    @Test
    void alreadySettledTradeRecordsAttemptAndDoesNotChangeBalances() {
        Trade ready = insertTrade(aliceBuysEq1(10, 50000, BUSINESS_DATE));
        SettlementJournal journal = persistValidJournal(ready.id());
        trades.markSettled(ready.id(), journal.id());
        List<Account> before = accounts.findAll();

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-again", ready.id()));

        assertRejected(outcome, 409, "ALREADY_SETTLED");
        Trade stored = trades.findById(ready.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(stored.journalId()).isEqualTo(journal.id());
        assertThat(accounts.findAll()).containsExactlyElementsOf(before);
        assertThat(journals.findJournalByTradeId(ready.id()).orElseThrow().id()).isEqualTo(journal.id());
        SettlementAttempt attempt = attempts.findByTradeId(ready.id()).getFirst();
        assertThat(attempt.outcome()).isEqualTo(SettlementOutcome.ALREADY_SETTLED);
        assertThat(attempt.journalId()).isEqualTo(journal.id());
        assertThat(attempt.businessDate()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    void futureDatedTradeIsNotDueAndStaysReady() {
        Trade trade = insertTrade(aliceBuysEq1(10, 50000, LocalDate.of(2026, 9, 21)));
        List<Account> before = accounts.findAll();

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertRejected(outcome, 422, "NOT_DUE");
        assertUnmoved(trade.id(), before);
        SettlementAttempt attempt = attempts.findByTradeId(trade.id()).getFirst();
        assertThat(attempt.outcome()).isEqualTo(SettlementOutcome.NOT_DUE);
        assertThat(attempt.journalId()).isNull();
        assertThat(attempt.businessDate()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    void tradeDatedTodaySettles() {
        Trade trade = insertTrade(aliceBuysEq1(10, 50000, BUSINESS_DATE));

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertThat(outcome.httpStatus()).isEqualTo(201);
        assertThat(trades.findById(trade.id()).orElseThrow().status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(attempts.findByTradeId(trade.id()).getFirst().outcome()).isEqualTo(SettlementOutcome.SETTLED);
    }

    @Test
    void overdueTradeSettles() {
        Trade trade = insertTrade(aliceBuysEq1(10, 50000, LocalDate.of(2026, 9, 19)));

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertThat(outcome.httpStatus()).isEqualTo(201);
        assertThat(trades.findById(trade.id()).orElseThrow().status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(attempts.findByTradeId(trade.id()).getFirst().outcome()).isEqualTo(SettlementOutcome.SETTLED);
    }

    @Test
    void unknownTradeReturnsWithoutCommittedClaimOrAttempt() {
        UUID unknownId = DemoSeed.UNKNOWN_ACCOUNT_ID;

        assertThatThrownBy(() -> settle.settle(new SettleCommand("settle-missing", unknownId)))
                .isInstanceOf(UnknownTradeException.class);

        assertThat(commandResults.findByCommandKey("settle-missing")).isEmpty();
        assertThat(attempts.findByTradeId(unknownId)).isEmpty();
    }

    @Test
    void missingRequiredAccountRollsBackWithoutAttemptOrDurableResult() {
        Trade trade = insertTrade(aliceBuysEq1(10, 50000, BUSINESS_DATE));
        jdbc.update("DELETE FROM account WHERE id = :id", Map.of("id", DemoSeed.ALICE_AUD_ID));
        List<Account> before = accounts.findAll();

        assertThatThrownBy(() -> settle.settle(new SettleCommand("settle-T-001", trade.id())))
                .isInstanceOf(SettlementIntegrityException.class)
                .hasMessageContaining("buyer cash");

        assertThat(commandResults.findByCommandKey("settle-T-001")).isEmpty();
        assertThat(attempts.findByTradeId(trade.id())).isEmpty();
        assertThat(trades.findById(trade.id()).orElseThrow().status()).isEqualTo(TradeStatus.READY);
        assertThat(accounts.findAll()).containsExactlyElementsOf(before);
        assertThat(journals.findJournalByTradeId(trade.id())).isEmpty();
    }

    @Test
    void insufficientCashLeavesReadyTradeUnchanged() {
        Trade trade = insertTrade(aliceBuysEq1(10, 100001, BUSINESS_DATE));
        List<Account> before = accounts.findAll();

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertRejected(outcome, 422, "INSUFFICIENT_CASH");
        assertUnmoved(trade.id(), before);
        assertThat(attempts.findByTradeId(trade.id()).getFirst().outcome())
                .isEqualTo(SettlementOutcome.INSUFFICIENT_CASH);
    }

    @Test
    void insufficientSecuritiesLeavesReadyTradeUnchanged() {
        Trade trade = insertTrade(aliceBuysEq1(11, 50000, BUSINESS_DATE));
        List<Account> before = accounts.findAll();

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertRejected(outcome, 422, "INSUFFICIENT_SECURITIES");
        assertUnmoved(trade.id(), before);
        assertThat(attempts.findByTradeId(trade.id()).getFirst().outcome())
                .isEqualTo(SettlementOutcome.INSUFFICIENT_SECURITIES);
    }

    @Test
    void bothInsufficientRecordsInsufficientCash() {
        Trade trade = insertTrade(aliceBuysEq1(11, 100001, BUSINESS_DATE));
        List<Account> before = accounts.findAll();

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertRejected(outcome, 422, "INSUFFICIENT_CASH");
        assertUnmoved(trade.id(), before);
        assertThat(attempts.findByTradeId(trade.id()))
                .extracting(SettlementAttempt::outcome)
                .containsExactly(SettlementOutcome.INSUFFICIENT_CASH);
    }

    @Test
    void exactlySufficientBalancesSettle() {
        Trade trade = insertTrade(aliceBuysEq1(10, 100000, BUSINESS_DATE));

        CommandOutcome outcome = settle.settle(new SettleCommand("settle-T-001", trade.id()));

        assertThat(outcome.httpStatus()).isEqualTo(201);
        assertThat(balance(DemoSeed.ALICE_AUD_ID).currentBalance()).isEqualTo(0);
        assertThat(balance(DemoSeed.ALICE_EQ1_ID).currentBalance()).isEqualTo(10);
        assertThat(balance(DemoSeed.BOB_AUD_ID).currentBalance()).isEqualTo(100000);
        assertThat(balance(DemoSeed.BOB_EQ1_ID).currentBalance()).isEqualTo(0);
        assertThat(trades.findById(trade.id()).orElseThrow().status()).isEqualTo(TradeStatus.SETTLED);
    }

    @Test
    void aliceBobSettlementProducesTheApprovedFinancialState() {
        Trade trade = insertTrade(aliceBuysEq1(10, 50000, BUSINESS_DATE));
        TradeTerms capturedTerms = trade.terms();

        CommandOutcome first = settle.settle(new SettleCommand("settle-T-001", trade.id()));
        SettlementResponse body = json.readValue(first.responseBody(), SettlementResponse.class);
        SettlementJournal journal = journals.findJournalByTradeId(trade.id()).orElseThrow();
        List<Posting> postings = journals.findPostingsByJournalId(journal.id());
        Trade settled = trades.findById(trade.id()).orElseThrow();
        CommandResult stored = commandResults.findByCommandKey("settle-T-001").orElseThrow();
        SettlementAttempt attempt = attempts.findByTradeId(trade.id()).getFirst();

        assertThat(first.httpStatus()).isEqualTo(201);
        assertThat(first.location()).isEqualTo("/v1/journals/" + journal.id());
        assertThat(body.tradeId()).isEqualTo(trade.id());
        assertThat(body.status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(body.outcome()).isEqualTo(SettlementOutcome.SETTLED);
        assertThat(body.journalId()).isEqualTo(journal.id());
        assertThat(body.settledAt()).isCloseTo(journal.settledAt(), within(1, ChronoUnit.SECONDS));
        assertThat(journal.settledAt()).isNotNull();

        assertThat(postings).hasSize(4);
        assertThat(postings)
                .extracting(Posting::accountId, Posting::direction, Posting::amount, Posting::signedAmount)
                .containsExactly(
                        tuple(DemoSeed.BOB_AUD_ID, PostingDirection.CREDIT, 50000L, 50000L),
                        tuple(DemoSeed.ALICE_AUD_ID, PostingDirection.DEBIT, 50000L, -50000L),
                        tuple(DemoSeed.ALICE_EQ1_ID, PostingDirection.CREDIT, 10L, 10L),
                        tuple(DemoSeed.BOB_EQ1_ID, PostingDirection.DEBIT, 10L, -10L));
        assertThat(postings.stream().filter(posting -> posting.accountId().equals(DemoSeed.ALICE_AUD_ID)
                        || posting.accountId().equals(DemoSeed.BOB_AUD_ID))
                .mapToLong(Posting::signedAmount).sum()).isZero();
        assertThat(postings.stream().filter(posting -> posting.accountId().equals(DemoSeed.ALICE_EQ1_ID)
                        || posting.accountId().equals(DemoSeed.BOB_EQ1_ID))
                .mapToLong(Posting::signedAmount).sum()).isZero();

        Account aliceAud = balance(DemoSeed.ALICE_AUD_ID);
        Account aliceEq1 = balance(DemoSeed.ALICE_EQ1_ID);
        Account bobAud = balance(DemoSeed.BOB_AUD_ID);
        Account bobEq1 = balance(DemoSeed.BOB_EQ1_ID);
        assertThat(aliceAud.currentBalance()).isEqualTo(50000);
        assertThat(aliceEq1.currentBalance()).isEqualTo(10);
        assertThat(bobAud.currentBalance()).isEqualTo(50000);
        assertThat(bobEq1.currentBalance()).isEqualTo(0);
        assertThat(aliceAud.openingBalance()).isEqualTo(100000);
        assertThat(aliceEq1.openingBalance()).isEqualTo(0);
        assertThat(bobAud.openingBalance()).isEqualTo(0);
        assertThat(bobEq1.openingBalance()).isEqualTo(10);
        assertThat(aliceAud.currentBalance() + bobAud.currentBalance()).isEqualTo(100000);
        assertThat(aliceEq1.currentBalance() + bobEq1.currentBalance()).isEqualTo(10);

        assertThat(settled.status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(settled.journalId()).isEqualTo(journal.id());
        assertThat(settled.terms()).isEqualTo(capturedTerms);
        assertThatThrownBy(() -> trades.markSettled(trade.id(), journal.id()))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class);

        assertThat(stored.completed()).isTrue();
        assertThat(stored.operation()).isEqualTo(SettleRequestIdentity.OPERATION);
        assertThat(stored.httpStatus()).isEqualTo(201);
        assertThat(stored.responseBody()).isEqualTo(first.responseBody());
        assertThat(stored.location()).isEqualTo(first.location());
        assertThat(attempt.outcome()).isEqualTo(SettlementOutcome.SETTLED);
        assertThat(attempt.journalId()).isEqualTo(journal.id());
        assertThat(attempt.businessDate()).isEqualTo(BUSINESS_DATE);
        assertThat(attempts.findByTradeId(trade.id())).hasSize(1);

        CommandOutcome replay = settle.settle(new SettleCommand("settle-T-001", trade.id()));
        assertThat(replay).isEqualTo(first);
        assertThat(attempts.findByTradeId(trade.id())).hasSize(1);
        assertThat(journals.findJournalByTradeId(trade.id()).orElseThrow().id()).isEqualTo(journal.id());
        assertThat(journals.findPostingsByJournalId(journal.id())).hasSize(4);
        assertThat(balance(DemoSeed.ALICE_AUD_ID).currentBalance()).isEqualTo(50000);
        assertThat(balance(DemoSeed.BOB_EQ1_ID).currentBalance()).isEqualTo(0);
        assertThat(commandResults.findByCommandKey("settle-T-001").orElseThrow().responseBody())
                .isEqualTo(first.responseBody());
    }

    private void assertRejected(CommandOutcome outcome, int status, String code) {
        assertThat(outcome.httpStatus()).isEqualTo(status);
        assertThat(outcome.location()).isNull();
        ErrorResponse error = json.readValue(outcome.responseBody(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo(code);
    }

    private void assertUnmoved(UUID tradeId, List<Account> before) {
        assertThat(trades.findById(tradeId).orElseThrow().status()).isEqualTo(TradeStatus.READY);
        assertThat(journals.findJournalByTradeId(tradeId)).isEmpty();
        assertThat(journals.findPostingsByJournalId(UUID.randomUUID())).isEmpty();
        assertThat(accounts.findAll()).containsExactlyElementsOf(before);
    }

    private Account balance(UUID accountId) {
        return accounts.findById(accountId).orElseThrow();
    }

    private Trade insertTrade(TradeTerms terms) {
        return trades.insertIfAbsent(terms).orElseThrow();
    }

    private SettlementJournal persistValidJournal(UUID tradeId) {
        return transactions.execute(status -> {
            SettlementJournal journal = journals.insertJournal(tradeId);
            journals.insertPostings(journal.id(), List.of(
                    posting(DemoSeed.ALICE_AUD_ID, PostingDirection.DEBIT, 50000),
                    posting(DemoSeed.BOB_AUD_ID, PostingDirection.CREDIT, 50000),
                    posting(DemoSeed.ALICE_EQ1_ID, PostingDirection.CREDIT, 10),
                    posting(DemoSeed.BOB_EQ1_ID, PostingDirection.DEBIT, 10)));
            return journal;
        });
    }

    private static Posting posting(UUID accountId, PostingDirection direction, long amount) {
        return new Posting(UUID.randomUUID(), UUID.randomUUID(), accountId, direction, amount, 0);
    }

    private static TradeTerms aliceBuysEq1(long quantity, long cashAmount, LocalDate settlementDate) {
        return new TradeTerms(
                "T-001",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                quantity,
                cashAmount,
                settlementDate);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-20T04:00:00Z"), SYDNEY);
        }
    }
}
