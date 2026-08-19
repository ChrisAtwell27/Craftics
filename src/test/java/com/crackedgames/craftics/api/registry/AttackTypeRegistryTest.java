package com.crackedgames.craftics.api.registry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the attack-type chart: what an attack is versus what a defender is, resolved to
 * a damage multiplier.
 *
 * <p>The registry is static, so every test clears it first and last. Leaving a chart behind
 * would make a later test pass or fail depending on the order the suite happened to run in,
 * which is the kind of flake that gets a whole suite disbelieved.
 */
class AttackTypeRegistryTest {

    private static final String FIRE  = "test:fire";
    private static final String WATER = "test:water";
    private static final String GRASS = "test:grass";
    private static final String ROCK  = "test:rock";
    private static final String GHOST = "test:ghost";

    @BeforeEach
    @AfterEach
    void reset() {
        AttackTypeRegistry.clear();
    }

    private static void registerFireChart() {
        AttackTypeRegistry.register(AttackTypeEntry.builder(FIRE)
            .displayName("Fire").colorCode("§c")
            .superEffectiveAgainst(GRASS)
            .notVeryEffectiveAgainst(WATER, ROCK)
            .noEffectAgainst(GHOST)
            .build());
    }

    // ── The inert default ────────────────────────────────────────────────────

    @Test
    void multiplier_isOneWhenNothingIsRegistered() {
        // The whole system sits in the damage path unconditionally, so an install with no
        // addon must resolve to a plain 1.0 without touching anything.
        assertEquals(1.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "minecraft:zombie"));
    }

    @Test
    void multiplier_isOneForAnUntypedAttack() {
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", GRASS);
        assertEquals(1.0, AttackTypeRegistry.multiplierFor(null, "mob", "minecraft:zombie"));
    }

    @Test
    void multiplier_isOneForAnUnregisteredAttackType() {
        // An addon removed mid-save leaves AIs naming types whose charts are gone. That must
        // read as an ordinary hit, not as an immunity or a crash.
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", GRASS);
        assertEquals(1.0, AttackTypeRegistry.multiplierFor("test:gone", "mob", "minecraft:zombie"));
    }

    @Test
    void multiplier_isOneForAnUntypedDefender() {
        registerFireChart();
        assertEquals(1.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "minecraft:zombie"));
    }

    @Test
    void multiplier_isOneForAMatchupTheChartDoesNotMention() {
        // Charts only list the interesting matchups; everything unlisted is neutral. This is
        // what keeps an eighteen-type chart small.
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", "test:normal");
        assertEquals(1.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "minecraft:zombie"));
    }

    // ── Single-type matchups ─────────────────────────────────────────────────

    @Test
    void multiplier_superEffectiveDoubles() {
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", GRASS);
        assertEquals(2.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "minecraft:zombie"));
    }

    @Test
    void multiplier_notVeryEffectiveHalves() {
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", WATER);
        assertEquals(0.5, AttackTypeRegistry.multiplierFor(FIRE, "mob", "minecraft:zombie"));
    }

    @Test
    void multiplier_noEffectIsZero() {
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", GHOST);
        assertEquals(0.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "minecraft:zombie"));
    }

    // ── Dual types ───────────────────────────────────────────────────────────

    @Test
    void multiplier_dualTypeStrongAndWeakCancelsOut() {
        // The standard dual-type rule, and the reason multipliers multiply rather than
        // taking the strongest: 2.0 x 0.5 is a plain hit.
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", GRASS, WATER);
        assertEquals(1.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "minecraft:zombie"));
    }

    @Test
    void multiplier_dualTypeBothWeakStacksToAQuarter() {
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", WATER, ROCK);
        assertEquals(0.25, AttackTypeRegistry.multiplierFor(FIRE, "mob", "minecraft:zombie"));
    }

    @Test
    void multiplier_immunityIsAbsorbing() {
        // Order must not matter: a defender immune on one of its types is immune full stop,
        // and a later 2.0 in the list cannot bring it back above zero.
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("ghostGrass", GHOST, GRASS);
        AttackTypeRegistry.setDefendingTypes("grassGhost", GRASS, GHOST);
        assertEquals(0.0, AttackTypeRegistry.multiplierFor(FIRE, "ghostGrass", "x"));
        assertEquals(0.0, AttackTypeRegistry.multiplierFor(FIRE, "grassGhost", "x"));
    }

    // ── Key resolution ───────────────────────────────────────────────────────

    @Test
    void defendingTypes_preferAiKeyOverEntityType() {
        // The property the whole one-entity-type-many-creatures design rests on: creatures
        // sharing an entity type are told apart by their aiKey.
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mymod:charizard", GRASS);
        AttackTypeRegistry.setDefendingTypes("mymod:pokemon", WATER);
        assertEquals(2.0,
            AttackTypeRegistry.multiplierFor(FIRE, "mymod:charizard", "mymod:pokemon"));
    }

    @Test
    void defendingTypes_fallBackToEntityTypeWhenTheAiKeyIsUnknown() {
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("minecraft:zombie", GRASS);
        assertEquals(2.0,
            AttackTypeRegistry.multiplierFor(FIRE, "nothing:registered", "minecraft:zombie"));
    }

    @Test
    void defendingTypes_passingNoneClearsTheEntry() {
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", GRASS);
        assertEquals(2.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "x"));
        AttackTypeRegistry.setDefendingTypes("mob");
        assertEquals(1.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "x"));
    }

    @Test
    void defaultAttackType_prefersAiKeyThenFallsBack() {
        AttackTypeRegistry.setDefaultAttackType("mymod:charizard", FIRE);
        AttackTypeRegistry.setDefaultAttackType("mymod:pokemon", WATER);
        assertEquals(FIRE, AttackTypeRegistry.defaultAttackTypeOf("mymod:charizard", "mymod:pokemon"));
        assertEquals(WATER, AttackTypeRegistry.defaultAttackTypeOf("unknown", "mymod:pokemon"));
        assertNull(AttackTypeRegistry.defaultAttackTypeOf("unknown", "also:unknown"));
    }

    // ── The player form ──────────────────────────────────────────────────────

    @Test
    void multiplierForTypes_matchesTheMobFormForTheSameTypes() {
        // The player has no aiKey to look up, so their defence goes through an explicit list.
        // The two forms must agree or a Fire hit would resist differently depending on who
        // was standing there.
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", GRASS, WATER);
        assertEquals(
            AttackTypeRegistry.multiplierFor(FIRE, "mob", "x"),
            AttackTypeRegistry.multiplierForTypes(FIRE, List.of(GRASS, WATER)));
    }

    @Test
    void multiplierForTypes_isOneForAnEmptyOrNullList() {
        registerFireChart();
        assertEquals(1.0, AttackTypeRegistry.multiplierForTypes(FIRE, List.of()));
        assertEquals(1.0, AttackTypeRegistry.multiplierForTypes(FIRE, null));
    }

    // ── Registration behaviour ───────────────────────────────────────────────

    @Test
    void register_replacesAnEntryWithTheSameId() {
        // How an addon overrides a built-in or another addon: entrypoints run last and win.
        registerFireChart();
        AttackTypeRegistry.setDefendingTypes("mob", GRASS);
        assertEquals(2.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "x"));

        AttackTypeRegistry.register(AttackTypeEntry.builder(FIRE)
            .notVeryEffectiveAgainst(GRASS)
            .build());
        assertEquals(0.5, AttackTypeRegistry.multiplierFor(FIRE, "mob", "x"));
    }

    @Test
    void entry_requiresAnId() {
        assertThrows(IllegalArgumentException.class,
            () -> AttackTypeEntry.builder(" ").build());
    }

    @Test
    void entry_defaultsNameAndColourRatherThanFailing() {
        // A chart with a missing display name should still work; it is a cosmetic omission,
        // not a reason to refuse the type and silently untype every attack that uses it.
        AttackTypeEntry e = new AttackTypeEntry(FIRE, null, null, null);
        assertEquals(FIRE, e.displayName());
        assertNotNull(e.colorCode());
        assertEquals(1.0, e.multiplierAgainst(GRASS));
    }

    @Test
    void entry_customMultiplierIsHonoured() {
        AttackTypeRegistry.register(AttackTypeEntry.builder(FIRE)
            .against(3.0, GRASS)
            .build());
        AttackTypeRegistry.setDefendingTypes("mob", GRASS);
        assertEquals(3.0, AttackTypeRegistry.multiplierFor(FIRE, "mob", "x"));
    }

    // ── The player-facing note ───────────────────────────────────────────────

    @Test
    void describeMultiplier_saysNothingForAnOrdinaryHit() {
        // A line on every single swing would be noise; only a matchup worth noticing speaks.
        assertNull(AttackTypeRegistry.describeMultiplier(1.0));
    }

    @Test
    void describeMultiplier_coversEachBand() {
        assertNotNull(AttackTypeRegistry.describeMultiplier(0.0));
        assertNotNull(AttackTypeRegistry.describeMultiplier(0.5));
        assertNotNull(AttackTypeRegistry.describeMultiplier(0.75));
        assertNotNull(AttackTypeRegistry.describeMultiplier(2.0));
        assertNotNull(AttackTypeRegistry.describeMultiplier(4.0));
    }
}
