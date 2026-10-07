package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.ProjectileImpactHandler;
import com.crackedgames.craftics.api.registry.ProjectileImpactRegistry;
import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hooks an encounter uses to make ordinary attacks not work: a guard nothing gets past,
 * the one sanctioned way through it, and projectiles that answer to their own handler.
 *
 * <p>A guard is only worth having if it is airtight. Most damage in a fight never passes a
 * "may I hit this" check - sweeps, splashes and procs all reach for the entity directly - so
 * the tests here go at the entity directly too.
 */
class EncounterGuardTest {

    private static CombatEntity boss() {
        return new CombatEntity(1, "minecraft:zombie", new GridPos(0, 0), 100, 5, 0, 1);
    }

    @AfterEach
    void cleanup() {
        ProjectileImpactRegistry.clear();
    }

    // ── The guard ──

    @Test
    @DisplayName("a guarded combatant takes nothing, from any amount")
    void guardStopsEverything() {
        CombatEntity e = boss();
        e.setDamageImmune(true, "hint");
        assertEquals(0, e.takeDamage(50));
        assertEquals(0, e.takeDamage(9999));
        assertEquals(0, e.takeLightningDamage(40));
        assertEquals(0, e.takeSpecialDamage(10, 0.5));
        assertEquals(100, e.getCurrentHp());
        assertTrue(e.isAlive());
    }

    @Test
    @DisplayName("dropping the guard makes it an ordinary target again")
    void guardCanBeDropped() {
        CombatEntity e = boss();
        e.setDamageImmune(true, "hint");
        e.setDamageImmune(false, "ignored");
        assertFalse(e.isDamageImmune());
        assertNull(e.getDamageImmuneHint(), "a hint with no guard would be shown for nothing");
        assertTrue(e.takeDamage(10) > 0);
    }

    @Test
    @DisplayName("the hint travels with the guard")
    void hintIsKept() {
        CombatEntity e = boss();
        e.setDamageImmune(true, "Perhaps a pickaxe?");
        assertEquals("Perhaps a pickaxe?", e.getDamageImmuneHint());
        e.setDamageImmune(true, null);
        assertTrue(e.isDamageImmune());
        assertNull(e.getDamageImmuneHint(), "null means a generic line, not a stale one");
    }

    @Test
    @DisplayName("the sanctioned source lands, and the guard is still up afterwards")
    void throughImmunity() {
        CombatEntity e = boss();
        e.setDamageImmune(true, "hint");
        int dealt = e.takeDamageThroughImmunity(12);
        assertTrue(dealt > 0);
        assertEquals(100 - dealt, e.getCurrentHp());
        assertTrue(e.isDamageImmune(), "one hit through must not leave the guard down");
        assertEquals(0, e.takeDamage(50));
    }

    @Test
    @DisplayName("going through a guard that is not up changes nothing about the hit")
    void throughImmunityWithoutGuard() {
        CombatEntity a = boss();
        CombatEntity b = boss();
        assertEquals(a.takeDamage(12), b.takeDamageThroughImmunity(12));
        assertFalse(b.isDamageImmune(), "it must not raise a guard that was never there");
    }

    @Test
    @DisplayName("a guarded combatant can still be killed through the guard")
    void lethalThroughImmunity() {
        CombatEntity e = boss();
        e.setDamageImmune(true, "hint");
        e.takeDamageThroughImmunity(9999);
        assertFalse(e.isAlive());
    }

    @Test
    @DisplayName("the guard shows as the Invulnerable trait, and only while it is up")
    void guardShowsAsTrait() {
        CombatEntity e = boss();
        assertFalse(MobTraits.forEntity(e).contains(MobTraits.INVULNERABLE));
        e.setDamageImmune(true, null);
        assertTrue(MobTraits.forEntity(e).contains(MobTraits.INVULNERABLE));
        e.setDamageImmune(false, null);
        assertFalse(MobTraits.forEntity(e).contains(MobTraits.INVULNERABLE));
    }

    @Test
    @DisplayName("pickaxe vulnerability is opt-in")
    void pickaxeFlag() {
        CombatEntity e = boss();
        assertFalse(e.isPickaxeVulnerable());
        e.setPickaxeVulnerable(true);
        assertTrue(e.isPickaxeVulnerable());
    }

    // ── Tool tiers ──

    @Test
    @DisplayName("a pickaxe is ranked by the material in its name")
    void toolTiers() {
        assertEquals(ToolTiers.WOOD, ToolTiers.of("wooden_pickaxe"));
        assertEquals(ToolTiers.WOOD, ToolTiers.of("golden_pickaxe"));
        assertEquals(ToolTiers.STONE, ToolTiers.of("stone_pickaxe"));
        assertEquals(ToolTiers.STONE, ToolTiers.of("copper_pickaxe"));
        assertEquals(ToolTiers.IRON, ToolTiers.of("iron_pickaxe"));
        assertEquals(ToolTiers.DIAMOND, ToolTiers.of("diamond_pickaxe"));
        assertEquals(ToolTiers.NETHERITE, ToolTiers.of("netherite_pickaxe"));
    }

    @Test
    @DisplayName("Aether pickaxes sit at the tier they mine at")
    void aetherToolTiers() {
        assertEquals(ToolTiers.WOOD, ToolTiers.of("skyroot_pickaxe"));
        assertEquals(ToolTiers.STONE, ToolTiers.of("holystone_pickaxe"));
        assertEquals(ToolTiers.IRON, ToolTiers.of("zanite_pickaxe"));
        assertEquals(ToolTiers.DIAMOND, ToolTiers.of("gravitite_pickaxe"));
        assertEquals(ToolTiers.DIAMOND, ToolTiers.of("valkyrie_pickaxe"));
    }

    @Test
    @DisplayName("an unknown tool is ranked low, not high")
    void unknownToolIsWood() {
        assertEquals(ToolTiers.WOOD, ToolTiers.of("mystery_pickaxe"));
        assertEquals(ToolTiers.WOOD, ToolTiers.of(""));
        assertEquals(ToolTiers.WOOD, ToolTiers.of(null));
    }

    // ── Custom projectiles ──

    private static final ProjectileImpactHandler NOTHING = ctx -> { };

    @Test
    @DisplayName("a registered projectile type resolves to its handler")
    void registryLookup() {
        ProjectileImpactRegistry.register("test_bolt", NOTHING, false);
        assertSame(NOTHING, ProjectileImpactRegistry.get("test_bolt"));
        assertNull(ProjectileImpactRegistry.get("other_bolt"));
        assertNull(ProjectileImpactRegistry.get(null));
    }

    @Test
    @DisplayName("deflectable is per type, and can be taken back")
    void deflectableFlag() {
        ProjectileImpactRegistry.register("test_ice", NOTHING, true);
        ProjectileImpactRegistry.register("test_fire", NOTHING, false);
        assertTrue(ProjectileImpactRegistry.isDeflectable("test_ice"));
        assertFalse(ProjectileImpactRegistry.isDeflectable("test_fire"));
        assertFalse(ProjectileImpactRegistry.isDeflectable("ghast_fireball"),
            "the built-in fireball is deflected by the engine, not through this registry");
        assertFalse(ProjectileImpactRegistry.isDeflectable(null));

        ProjectileImpactRegistry.register("test_ice", NOTHING, false);
        assertFalse(ProjectileImpactRegistry.isDeflectable("test_ice"));
    }

    @Test
    @DisplayName("a struck projectile bounces unless its handler says otherwise")
    void defaultDeflectBounces() {
        assertFalse(NOTHING.onDeflect(null), "the default hands the bounce back to the engine");
    }
}
