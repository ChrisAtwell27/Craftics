package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-Java tests for {@link GoatHornEffects}'s combat-side dispatch logic.
 *
 * <p>Buff variants ({@code ponder, sing, seek, feel, dream}) call into
 * {@link CombatEffects#addEffect}, which reads {@code CrafticsMod.CONFIG}.
 * That field is null in the unit-test environment (no Fabric loader, no
 * owo-lib config I/O). Buff stacking is verified manually in-game via the
 * smoke test in the implementation plan; the unit tests here cover the
 * debuff variants and the previously-no-op switch cases.
 */
class GoatHornEffectsTest {

    private static CombatEntity makeEnemy(int id) {
        return new CombatEntity(id, "minecraft:zombie", new GridPos(id, 0),
            10, 3, 1, 1);
    }

    @Test
    void useHorn_admire_appliesAttackPenaltyToAllLiveEnemies() {
        CombatEntity a = makeEnemy(1);
        CombatEntity b = makeEnemy(2);
        CombatEffects effects = new CombatEffects();
        GoatHornEffects.useHorn("admire", effects, List.of(a, b));

        assertEquals(2, a.getAttackPenalty());
        assertEquals(2, a.getAttackPenaltyTurns());
        assertEquals(2, b.getAttackPenalty());
        assertEquals(2, b.getAttackPenaltyTurns());
    }

    @Test
    void useHorn_yearn_stacksPoisonOnAllEnemies() {
        CombatEntity a = makeEnemy(1);
        CombatEffects effects = new CombatEffects();
        GoatHornEffects.useHorn("yearn", effects, List.of(a));

        assertEquals(3, a.getPoisonTurns());
        assertEquals(0, a.getPoisonAmplifier());
    }

    @Test
    void useHorn_call_stacksSlownessOnAllEnemies() {
        CombatEntity a = makeEnemy(1);
        CombatEffects effects = new CombatEffects();
        GoatHornEffects.useHorn("call", effects, List.of(a));

        assertEquals(2, a.getSlownessTurns());
        assertEquals(1, a.getSlownessPenalty());
    }

    @Test
    void useHorn_unknownVariantId_returnsFallbackString() {
        CombatEffects effects = new CombatEffects();
        String msg = GoatHornEffects.useHorn("unknown_variant", effects, List.of());
        assertEquals("§7The horn makes no sound...", msg);
    }

    @Test
    void useHorn_doesNotAffectDeadEnemies() {
        CombatEntity dead = makeEnemy(1);
        dead.takeDamage(999);
        assertFalse(dead.isAlive());

        CombatEffects effects = new CombatEffects();
        GoatHornEffects.useHorn("admire", effects, List.of(dead));

        assertEquals(0, dead.getAttackPenalty());
        assertEquals(0, dead.getAttackPenaltyTurns());
    }

    // ---- Special-class scaling (potency / duration bonuses) ----

    @Test
    void useHorn_admire_potencyBonusIncreasesAttackPenalty() {
        CombatEntity a = makeEnemy(1);
        CombatEffects effects = new CombatEffects();
        // potency 2 → base -2 + 2 = -4
        GoatHornEffects.useHorn("admire", effects, List.of(a), 0, 2);
        assertEquals(4, a.getAttackPenalty());
    }

    @Test
    void useHorn_admire_durationBonusExtendsTurns() {
        CombatEntity a = makeEnemy(1);
        CombatEffects effects = new CombatEffects();
        // base 2 + duration 3 = 5
        GoatHornEffects.useHorn("admire", effects, List.of(a), 3, 0);
        assertEquals(5, a.getAttackPenaltyTurns());
    }

    @Test
    void useHorn_yearn_potencyBonusIncreasesPoisonAmplifier() {
        CombatEntity a = makeEnemy(1);
        CombatEffects effects = new CombatEffects();
        GoatHornEffects.useHorn("yearn", effects, List.of(a), 0, 2);
        assertEquals(2, a.getPoisonAmplifier());
    }

    @Test
    void useHorn_call_potencyBonusIncreasesSlownessPenalty() {
        CombatEntity a = makeEnemy(1);
        CombatEffects effects = new CombatEffects();
        // base slowness penalty 1 + potency 2 = 3
        GoatHornEffects.useHorn("call", effects, List.of(a), 0, 2);
        assertEquals(3, a.getSlownessPenalty());
    }

    // ---- Buff/debuff stacking on re-cast ----

    @Test
    void useHorn_admire_recastStacksPenaltyUpToCap() {
        CombatEntity a = makeEnemy(1);
        CombatEffects effects = new CombatEffects();
        // Cast 1 → -2
        GoatHornEffects.useHorn("admire", effects, List.of(a));
        assertEquals(2, a.getAttackPenalty());
        // Cast 2 → -4 (under cap of MAX_HORN_AMPLIFIER+2 = 5)
        GoatHornEffects.useHorn("admire", effects, List.of(a));
        assertEquals(4, a.getAttackPenalty());
        // Cast 3 → capped at 5 (would otherwise be 6)
        GoatHornEffects.useHorn("admire", effects, List.of(a));
        assertEquals(GoatHornEffects.MAX_HORN_AMPLIFIER + 2, a.getAttackPenalty());
    }
}
