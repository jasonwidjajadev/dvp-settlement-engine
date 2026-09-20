package com.jasonwidjaja.dvp.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementJournalDomainTest {

    @Test
    void journalAndPostingHoldCommittedHistoryWithoutMutatingBalances() {
        UUID journalId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        Instant settledAt = Instant.parse("2026-09-20T00:00:00Z");
        SettlementJournal journal = new SettlementJournal(
                journalId,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                settledAt);

        Posting buyerCash = new Posting(
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                journalId,
                DemoSeed.ALICE_AUD_ID,
                PostingDirection.DEBIT,
                50000,
                -50000);
        Posting sellerCash = new Posting(
                UUID.fromString("00000000-0000-0000-0000-000000000302"),
                journalId,
                DemoSeed.BOB_AUD_ID,
                PostingDirection.CREDIT,
                50000,
                50000);

        assertThat(journal.settledAt()).isEqualTo(settledAt);
        assertThat(buyerCash.signedAmount()).isEqualTo(-buyerCash.amount());
        assertThat(sellerCash.signedAmount()).isEqualTo(sellerCash.amount());
        assertThat(PostingDirection.values()).containsExactly(PostingDirection.DEBIT, PostingDirection.CREDIT);
    }
}
