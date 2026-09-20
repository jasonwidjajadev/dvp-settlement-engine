package com.jasonwidjaja.dvp.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jasonwidjaja.dvp.domain.Posting;
import com.jasonwidjaja.dvp.domain.PostingDirection;
import com.jasonwidjaja.dvp.domain.SettlementJournal;
import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeSettlementPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

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
    void markSettledSetsStatusAndJournalId() {
        Trade ready = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        SettlementJournal journal = persistValidJournal(ready.id());

        trades.markSettled(ready.id(), journal.id());

        Trade settled = trades.findById(ready.id()).orElseThrow();
        Trade locked = trades.lockById(ready.id()).orElseThrow();
        assertThat(settled.status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(settled.journalId()).isEqualTo(journal.id());
        assertThat(settled.terms()).isEqualTo(ready.terms());
        assertThat(locked).isEqualTo(settled);
    }

    @Test
    void markSettledOnAlreadySettledTradeFails() {
        Trade ready = trades.insertIfAbsent(aliceBuysEq1()).orElseThrow();
        SettlementJournal journal = persistValidJournal(ready.id());
        trades.markSettled(ready.id(), journal.id());

        assertThatThrownBy(() -> trades.markSettled(ready.id(), journal.id()))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("exactly one READY trade");

        Trade stored = trades.findById(ready.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(TradeStatus.SETTLED);
        assertThat(stored.journalId()).isEqualTo(journal.id());
        assertThat(stored.terms()).isEqualTo(ready.terms());
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
