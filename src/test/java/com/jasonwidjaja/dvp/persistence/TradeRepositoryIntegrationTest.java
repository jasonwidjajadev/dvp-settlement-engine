package com.jasonwidjaja.dvp.persistence;

import java.time.LocalDate;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jasonwidjaja.dvp.domain.Trade;
import com.jasonwidjaja.dvp.domain.TradeStatus;
import com.jasonwidjaja.dvp.domain.TradeTerms;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;

class TradeRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TradeRepository trades;

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
    }

    @Test
    void insertThenReadRoundTripsEveryTerm() {
        TradeTerms terms = aliceBuysEq1("T-001", 10, 50000);

        Trade inserted = trades.insertIfAbsent(terms).orElseThrow();
        Trade byId = trades.findById(inserted.id()).orElseThrow();
        Trade byReference = trades.findByExternalTradeId("T-001").orElseThrow();

        assertThat(inserted.status()).isEqualTo(TradeStatus.READY);
        assertThat(byId).isEqualTo(inserted);
        assertThat(byReference).isEqualTo(inserted);
        assertThat(byId.terms()).isEqualTo(terms);
        assertThat(byId.terms().externalTradeId()).isEqualTo("T-001");
        assertThat(byId.terms().buyerId()).isEqualTo(DemoSeed.ALICE_ID);
        assertThat(byId.terms().sellerId()).isEqualTo(DemoSeed.BOB_ID);
        assertThat(byId.terms().securityId()).isEqualTo(DemoSeed.EQ1_ID);
        assertThat(byId.terms().quantity()).isEqualTo(10);
        assertThat(byId.terms().cashAmount()).isEqualTo(50000);
        assertThat(byId.terms().settlementDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void unknownIdReturnsEmpty() {
        assertThat(trades.findById(DemoSeed.UNKNOWN_ACCOUNT_ID)).isEmpty();
        assertThat(trades.findByExternalTradeId("missing")).isEmpty();
    }

    @Test
    void duplicateExternalReferenceDoesNotOverwriteOriginalTrade() {
        TradeTerms original = aliceBuysEq1("T-001", 10, 50000);
        TradeTerms changed = aliceBuysEq1("T-001", 11, 60000);

        Trade first = trades.insertIfAbsent(original).orElseThrow();
        Optional<Trade> second = trades.insertIfAbsent(changed);

        Trade stored = trades.findByExternalTradeId("T-001").orElseThrow();
        assertThat(second).isEmpty();
        assertThat(stored.id()).isEqualTo(first.id());
        assertThat(stored.terms()).isEqualTo(original);
        assertThat(stored.terms().quantity()).isEqualTo(10);
        assertThat(stored.terms().cashAmount()).isEqualTo(50000);
    }

    private static TradeTerms aliceBuysEq1(String externalTradeId, long quantity, long cashAmount) {
        return new TradeTerms(
                externalTradeId,
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                quantity,
                cashAmount,
                LocalDate.of(2026, 9, 20));
    }
}
