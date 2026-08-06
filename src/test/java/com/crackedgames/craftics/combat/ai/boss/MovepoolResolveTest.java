package com.crackedgames.craftics.combat.ai.boss;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only the pure id-resolution helper is covered here. Cycling an actual movepool
 * needs a CombatEntity and a GridArena, which cannot be built without a
 * Minecraft bootstrap; that behaviour is on the in-game checklist instead.
 */
class MovepoolResolveTest {

    private static final Set<String> KNOWN = Set.of("a", "b", "c");

    @Test
    void keepsOnlyKnownIdsInOrder() {
        assertEquals(List.of("c", "a"),
            MovepoolBossAI.resolveMoveIds(List.of("c", "zzz", "a"), KNOWN));
    }

    @Test
    void dropsDuplicates() {
        assertEquals(List.of("a", "b"),
            MovepoolBossAI.resolveMoveIds(List.of("a", "a", "b", "a"), KNOWN));
    }

    @Test
    void nullAndEmptyInputsYieldAnEmptyList() {
        assertTrue(MovepoolBossAI.resolveMoveIds(null, KNOWN).isEmpty());
        assertTrue(MovepoolBossAI.resolveMoveIds(List.of(), KNOWN).isEmpty());
        assertTrue(MovepoolBossAI.resolveMoveIds(List.of("a"), Set.of()).isEmpty());
    }

    @Test
    void nullEntriesAreSkipped() {
        java.util.List<String> withNull = new java.util.ArrayList<>();
        withNull.add("a");
        withNull.add(null);
        withNull.add("b");
        assertEquals(List.of("a", "b"), MovepoolBossAI.resolveMoveIds(withNull, KNOWN));
    }
}
