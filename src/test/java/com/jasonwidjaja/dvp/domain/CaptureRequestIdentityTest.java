package com.jasonwidjaja.dvp.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureRequestIdentityTest {

    @Test
    void sameParsedTermsProduceTheSameIdentity() {
        TradeTerms first = aliceBuysEq1("T-001", 10);
        TradeTerms sameFields = aliceBuysEq1("T-001", 10);

        assertThat(CaptureRequestIdentity.of(first)).isEqualTo(CaptureRequestIdentity.of(sameFields));
        assertThat(CaptureRequestIdentity.of(first)).startsWith(CaptureRequestIdentity.OPERATION.length() + ":");
        assertThat(CaptureRequestIdentity.of(first)).contains(":" + CaptureRequestIdentity.OPERATION + ",");
    }

    @Test
    void changedTermProducesADifferentIdentity() {
        assertThat(CaptureRequestIdentity.of(aliceBuysEq1("T-001", 10)))
                .isNotEqualTo(CaptureRequestIdentity.of(aliceBuysEq1("T-001", 11)));
    }

    @Test
    void pipeInExternalTradeIdDoesNotCollideWithShiftedFields() {
        LocalDate date = LocalDate.of(2026, 9, 20);
        TradeTerms pipeInReference = new TradeTerms(
                "A|" + DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.ALICE_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                date);
        TradeTerms shiftedFields = new TradeTerms(
                "A",
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.ALICE_ID,
                10,
                50000,
                date);

        assertThat(pipeInReference.externalTradeId()).contains("|");
        assertThat(CaptureRequestIdentity.of(pipeInReference))
                .isNotEqualTo(CaptureRequestIdentity.of(shiftedFields));
    }

    @Test
    void lengthPrefixAndCommaCharactersInExternalTradeIdDoNotCollide() {
        TradeTerms looksLikePrefix = aliceBuysEq1("5:T-001,", 10);
        TradeTerms plain = aliceBuysEq1("T-001", 10);
        TradeTerms commaInReference = aliceBuysEq1("T-001,00000000-0000-0000-0000-000000000001", 10);
        TradeTerms otherBuyer = new TradeTerms(
                "T-001",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                10,
                50000,
                LocalDate.of(2026, 9, 20));

        assertThat(CaptureRequestIdentity.of(looksLikePrefix)).isNotEqualTo(CaptureRequestIdentity.of(plain));
        assertThat(CaptureRequestIdentity.of(commaInReference)).isNotEqualTo(CaptureRequestIdentity.of(otherBuyer));
    }

    private static TradeTerms aliceBuysEq1(String externalTradeId, long quantity) {
        return new TradeTerms(
                externalTradeId,
                DemoSeed.ALICE_ID,
                DemoSeed.BOB_ID,
                DemoSeed.EQ1_ID,
                quantity,
                50000,
                LocalDate.of(2026, 9, 20));
    }
}
