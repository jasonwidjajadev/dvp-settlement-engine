package com.jasonwidjaja.dvp.persistence;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import com.jasonwidjaja.dvp.domain.Account;
import com.jasonwidjaja.dvp.domain.Asset;
import com.jasonwidjaja.dvp.domain.AssetType;
import com.jasonwidjaja.dvp.support.AbstractPostgresIntegrationTest;
import com.jasonwidjaja.dvp.support.DemoSeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountSettlementPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private AssetRepository assets;

    @BeforeEach
    void seedReferenceData() {
        DemoSeed.apply(dataSource);
    }

    @Test
    void aliceAndBobAudAndEq1AccountsResolveByParticipantAndAsset() {
        assertThat(accounts.findIdByParticipantAndAsset(DemoSeed.ALICE_ID, DemoSeed.AUD_ID))
                .contains(DemoSeed.ALICE_AUD_ID);
        assertThat(accounts.findIdByParticipantAndAsset(DemoSeed.ALICE_ID, DemoSeed.EQ1_ID))
                .contains(DemoSeed.ALICE_EQ1_ID);
        assertThat(accounts.findIdByParticipantAndAsset(DemoSeed.BOB_ID, DemoSeed.AUD_ID))
                .contains(DemoSeed.BOB_AUD_ID);
        assertThat(accounts.findIdByParticipantAndAsset(DemoSeed.BOB_ID, DemoSeed.EQ1_ID))
                .contains(DemoSeed.BOB_EQ1_ID);
    }

    @Test
    void unknownParticipantOrAssetPairReturnsEmpty() {
        assertThat(accounts.findIdByParticipantAndAsset(DemoSeed.UNKNOWN_ACCOUNT_ID, DemoSeed.AUD_ID)).isEmpty();
        assertThat(accounts.findIdByParticipantAndAsset(DemoSeed.ALICE_ID, DemoSeed.UNKNOWN_ACCOUNT_ID)).isEmpty();
    }

    @Test
    void audAssetResolvesByCode() {
        Asset aud = assets.findByCode("AUD").orElseThrow();
        assertThat(aud.id()).isEqualTo(DemoSeed.AUD_ID);
        assertThat(aud.code()).isEqualTo("AUD");
        assertThat(aud.type()).isEqualTo(AssetType.CASH);
        assertThat(assets.findByCode("missing")).isEmpty();
    }

    @Test
    void lockBalanceReturnsCurrentBalance() {
        LockedAccount locked = accounts.lockBalance(DemoSeed.ALICE_AUD_ID).orElseThrow();
        Account aliceAud = accounts.findById(DemoSeed.ALICE_AUD_ID).orElseThrow();

        assertThat(locked.id()).isEqualTo(DemoSeed.ALICE_AUD_ID);
        assertThat(locked.assetId()).isEqualTo(DemoSeed.AUD_ID);
        assertThat(locked.currentBalance()).isEqualTo(aliceAud.currentBalance());
        assertThat(locked.currentBalance()).isEqualTo(100000);
        assertThat(accounts.lockBalance(DemoSeed.UNKNOWN_ACCOUNT_ID)).isEmpty();
    }

    @Test
    void applyDeltaIncreasesAndDecreasesCurrentBalance() {
        Account before = accounts.findById(DemoSeed.ALICE_AUD_ID).orElseThrow();

        accounts.applyDelta(DemoSeed.ALICE_AUD_ID, 1000);
        Account increased = accounts.findById(DemoSeed.ALICE_AUD_ID).orElseThrow();
        assertThat(increased.currentBalance()).isEqualTo(before.currentBalance() + 1000);
        assertThat(increased.openingBalance()).isEqualTo(before.openingBalance());

        accounts.applyDelta(DemoSeed.ALICE_AUD_ID, -50000);
        Account decreased = accounts.findById(DemoSeed.ALICE_AUD_ID).orElseThrow();
        assertThat(decreased.currentBalance()).isEqualTo(before.currentBalance() + 1000 - 50000);
        assertThat(decreased.openingBalance()).isEqualTo(before.openingBalance());
        assertThat(accounts.lockBalance(DemoSeed.ALICE_AUD_ID).orElseThrow().currentBalance())
                .isEqualTo(decreased.currentBalance());
    }

    @Test
    void applyDeltaForUnknownAccountFails() {
        assertThatThrownBy(() -> accounts.applyDelta(DemoSeed.UNKNOWN_ACCOUNT_ID, 1))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("exactly one account");
    }

    @Test
    void applyDeltaThatWouldMakeBalanceNegativeIsRejected() {
        assertThatThrownBy(() -> accounts.applyDelta(DemoSeed.BOB_AUD_ID, -1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    Throwable root = ((DataIntegrityViolationException) ex).getMostSpecificCause();
                    assertThat(root).isInstanceOf(PSQLException.class);
                    assertThat(root.getMessage()).contains("account_current_balance_non_negative");
                });

        assertThat(accounts.findById(DemoSeed.BOB_AUD_ID).orElseThrow().currentBalance()).isEqualTo(0);
    }

    @Test
    void accountRepositoryHasNoAbsoluteBalanceSetter() {
        assertThat(Arrays.stream(AccountRepository.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.toLowerCase().contains("set")
                        && name.toLowerCase().contains("balance"));
    }
}
