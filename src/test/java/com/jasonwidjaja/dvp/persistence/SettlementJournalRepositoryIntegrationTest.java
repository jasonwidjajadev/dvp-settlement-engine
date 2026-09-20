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
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jasonwidjaja.dvp.domain.Posting;
import com.jasonwidjaja.dvp.domain.PostingDirection;
import com.jasonwidjaja.dvp.domain.SettlementJournal;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SettlementJournalRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TradeRepository trades;

    @Autowired
    private SettlementJournalRepository journals;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void journalCanBeInsertedAndReadByIdAndTradeId() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        SettlementJournal inserted = persistValidJournal(trade.id());

        SettlementJournal byId = journals.findJournalById(inserted.id()).orElseThrow();
        SettlementJournal byTrade = journals.findJournalByTradeId(trade.id()).orElseThrow();

        assertThat(byId.id()).isEqualTo(inserted.id());
        assertThat(byId.tradeId()).isEqualTo(trade.id());
        assertThat(byId.settledAt()).isCloseTo(inserted.settledAt(), within(1, ChronoUnit.SECONDS));
        assertThat(byTrade).isEqualTo(byId);
    }

    @Test
    void fourPostingsInsertAndReadBackInAssetThenDirectionThenAccountOrder() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        SettlementJournal journal = persistValidJournal(trade.id());

        List<Posting> postings = journals.findPostingsByJournalId(journal.id());
        assertThat(postings).hasSize(4);
        assertThat(postings)
                .extracting(Posting::accountId)
                .containsExactly(
                        DemoSeed.BOB_AUD_ID,
                        DemoSeed.ALICE_AUD_ID,
                        DemoSeed.ALICE_EQ1_ID,
                        DemoSeed.BOB_EQ1_ID);
        assertThat(postings)
                .extracting(Posting::direction)
                .containsExactly(
                        PostingDirection.CREDIT,
                        PostingDirection.DEBIT,
                        PostingDirection.CREDIT,
                        PostingDirection.DEBIT);
        assertThat(postings.get(0).amount()).isEqualTo(50000);
        assertThat(postings.get(0).signedAmount()).isEqualTo(50000);
        assertThat(postings.get(1).amount()).isEqualTo(50000);
        assertThat(postings.get(1).signedAmount()).isEqualTo(-50000);
        assertThat(postings.get(2).amount()).isEqualTo(10);
        assertThat(postings.get(2).signedAmount()).isEqualTo(10);
        assertThat(postings.get(3).amount()).isEqualTo(10);
        assertThat(postings.get(3).signedAmount()).isEqualTo(-10);
        assertThat(postings).allMatch(posting -> posting.journalId().equals(journal.id()));
    }

    @Test
    void secondJournalForTheSameTradeFails() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        persistValidJournal(trade.id());

        assertThatThrownBy(() -> persistValidJournal(trade.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    Throwable root = ((DataIntegrityViolationException) ex).getMostSpecificCause();
                    assertThat(root).isInstanceOf(PSQLException.class);
                    assertThat(root.getMessage()).contains("settlement_journal_trade_id_unique");
                });

        assertThat(journals.findJournalByTradeId(trade.id())).isPresent();
    }

    @Test
    void insertPostingsRequiresExactlyFourPostings() {
        Trade trade = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            SettlementJournal journal = journals.insertJournal(trade.id());
            journals.insertPostings(journal.id(), aliceBobPostings().subList(0, 3));
        })).isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("exactly four postings");

        assertThat(journals.findJournalByTradeId(trade.id())).isEmpty();
    }

    @Test
    void repositoryHasNoUpdateOrDeleteMethods() {
        assertThat(Arrays.stream(SettlementJournalRepository.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("remove")
                        || name.startsWith("replace"));
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
