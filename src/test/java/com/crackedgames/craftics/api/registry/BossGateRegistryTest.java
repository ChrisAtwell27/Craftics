package com.crackedgames.craftics.api.registry;

import net.minecraft.server.network.ServerPlayerEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for boss gate lookup.
 *
 * <p>The rule worth pinning is which way every doubtful case falls. A gate that refuses holds a
 * party at a dialogue until its leader answers, so anything short of a clear refusal has to let
 * the party through: no gate, nobody to ask about, an empty answer, a gate that throws.
 *
 * <p>The party is a list holding one null throughout. The registry passes members to the gate
 * and never dereferences one, so these run for real rather than around a Minecraft type.
 */
class BossGateRegistryTest {

    private static final String BIOME = "test_dungeon";
    private static final List<ServerPlayerEntity> PARTY = Collections.singletonList(null);

    @BeforeEach
    @AfterEach
    void reset() {
        BossGateRegistry.clear();
    }

    @Test
    void noGate_letsThePartyThrough() {
        assertTrue(BossGateRegistry.isEmpty());
        assertFalse(BossGateRegistry.has(BIOME));
        assertTrue(BossGateRegistry.check(BIOME, PARTY).isEmpty());
    }

    @Test
    void aRefusal_comesBackAsTheGatesLines() {
        BossGateRegistry.register(BIOME, party -> List.of("No key.", "Go and find one."));

        assertTrue(BossGateRegistry.has(BIOME));
        assertEquals(List.of("No key.", "Go and find one."), BossGateRegistry.check(BIOME, PARTY));
    }

    @Test
    void anEmptyAnswer_letsThePartyThrough() {
        BossGateRegistry.register(BIOME, party -> List.of());
        assertTrue(BossGateRegistry.check(BIOME, PARTY).isEmpty());

        BossGateRegistry.register(BIOME, party -> null);
        assertTrue(BossGateRegistry.check(BIOME, PARTY).isEmpty());
    }

    @Test
    void blankLines_areNotARefusal() {
        // A dialogue made only of empty boxes would hold the party and tell it nothing.
        BossGateRegistry.register(BIOME, party -> Arrays.asList("", "   ", null));
        assertTrue(BossGateRegistry.check(BIOME, PARTY).isEmpty());

        BossGateRegistry.register(BIOME, party -> Arrays.asList("", "No key.", null));
        assertEquals(List.of("No key."), BossGateRegistry.check(BIOME, PARTY));
    }

    @Test
    void aThrowingGate_letsThePartyThrough() {
        BossGateRegistry.register(BIOME, party -> { throw new IllegalStateException("boom"); });

        assertTrue(BossGateRegistry.check(BIOME, PARTY).isEmpty());
    }

    @Test
    void aGateGuardsOnlyItsOwnBiome() {
        BossGateRegistry.register(BIOME, party -> List.of("No key."));

        assertTrue(BossGateRegistry.check("another_biome", PARTY).isEmpty());
        assertTrue(BossGateRegistry.check(null, PARTY).isEmpty());
    }

    @Test
    void nobodyToAskAbout_isNeverPutToTheGate() {
        // An empty party has nobody to show a dialogue to, so a refusal could never be answered.
        // The gate here refuses everyone it is asked about; getting nothing back means it was not.
        boolean[] asked = {false};
        BossGateRegistry.register(BIOME, party -> {
            asked[0] = true;
            return List.of("No key.");
        });

        assertTrue(BossGateRegistry.check(BIOME, List.of()).isEmpty());
        assertTrue(BossGateRegistry.check(BIOME, null).isEmpty());
        assertFalse(asked[0]);
    }

    @Test
    void reRegisteringABiome_replacesItsGate() {
        BossGateRegistry.register(BIOME, party -> List.of("First."));
        BossGateRegistry.register(BIOME, party -> List.of("Second."));

        assertEquals(List.of("Second."), BossGateRegistry.check(BIOME, PARTY));
    }

    @Test
    void theGateIsHandedACopyOfTheParty() {
        // The caller goes on to use its list as the party it shows the dialogue to. Read back
        // out here rather than asserted inside the gate, where a failure would be caught as
        // "the gate threw" and pass.
        List<ServerPlayerEntity> party = new ArrayList<>(PARTY);
        int[] seen = {-1};
        BossGateRegistry.register(BIOME, asked -> {
            seen[0] = asked.size();
            asked.clear();
            return List.of("No key.");
        });

        BossGateRegistry.check(BIOME, party);
        assertEquals(1, seen[0]);
        assertEquals(1, party.size());
    }

    @Test
    void aRegistrationWithNothingToKeyOrAsk_isIgnored() {
        BossGateRegistry.register(null, party -> List.of("No key."));
        BossGateRegistry.register("  ", party -> List.of("No key."));
        BossGateRegistry.register(BIOME, null);

        assertTrue(BossGateRegistry.isEmpty());
    }
}
