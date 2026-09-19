package com.jasonwidjaja.dvp.api;

import org.junit.jupiter.api.Test;

import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.Asset;
import com.jasonwidjaja.dvp.domain.AssetType;
import com.jasonwidjaja.dvp.domain.Participant;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;

class AccountResponseTest {

    @Test
    void mapsPhase1AccountWithoutLosingParticipantOrAsset() {
        Account account = new Account(
                DemoSeed.ALICE_AUD_ID,
                new Participant(DemoSeed.ALICE_ID, "Alice"),
                new Asset(DemoSeed.AUD_ID, "AUD", AssetType.CASH),
                100000,
                100000);

        AccountResponse response = AccountResponse.from(account);

        assertThat(response.id()).isEqualTo(DemoSeed.ALICE_AUD_ID);
        assertThat(response.participant().id()).isEqualTo(DemoSeed.ALICE_ID);
        assertThat(response.participant().name()).isEqualTo("Alice");
        assertThat(response.asset().id()).isEqualTo(DemoSeed.AUD_ID);
        assertThat(response.asset().code()).isEqualTo("AUD");
        assertThat(response.asset().type()).isEqualTo(AssetType.CASH);
        assertThat(response.openingBalance()).isEqualTo(100000);
        assertThat(response.currentBalance()).isEqualTo(100000);
    }
}
