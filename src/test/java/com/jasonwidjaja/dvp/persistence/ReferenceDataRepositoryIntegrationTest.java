package com.jasonwidjaja.dvp.persistence;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jasonwidjaja.dvp.domain.Asset;
import com.jasonwidjaja.dvp.domain.AssetType;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceDataRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ParticipantRepository participants;

    @Autowired
    private AssetRepository assets;

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
    }

    @Test
    void knownParticipantExists() {
        assertThat(participants.existsById(DemoSeed.ALICE_ID)).isTrue();
        assertThat(participants.existsById(DemoSeed.BOB_ID)).isTrue();
    }

    @Test
    void unknownParticipantDoesNotExist() {
        assertThat(participants.existsById(DemoSeed.UNKNOWN_ACCOUNT_ID)).isFalse();
    }

    @Test
    void audReadsAsCash() {
        Asset aud = assets.findById(DemoSeed.AUD_ID).orElseThrow();
        assertThat(aud.id()).isEqualTo(DemoSeed.AUD_ID);
        assertThat(aud.code()).isEqualTo("AUD");
        assertThat(aud.type()).isEqualTo(AssetType.CASH);
    }

    @Test
    void eq1ReadsAsSecurity() {
        Asset eq1 = assets.findById(DemoSeed.EQ1_ID).orElseThrow();
        assertThat(eq1.id()).isEqualTo(DemoSeed.EQ1_ID);
        assertThat(eq1.code()).isEqualTo("EQ1");
        assertThat(eq1.type()).isEqualTo(AssetType.SECURITY);
    }

    @Test
    void unknownAssetIsEmpty() {
        assertThat(assets.findById(DemoSeed.UNKNOWN_ACCOUNT_ID)).isEmpty();
    }
}
