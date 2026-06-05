package com.crackedgames.craftics.compat.golemoverhaul;

import com.crackedgames.craftics.combat.ai.ally.AllyAbilities;
import com.crackedgames.craftics.combat.ai.ally.AllyAI;
import com.crackedgames.craftics.combat.ai.ally.AllyArchetypes;
import com.crackedgames.craftics.combat.ai.ally.AllyDeathRegistry;
import com.crackedgames.craftics.combat.ai.ally.AllyKillRegistry;
import com.crackedgames.craftics.combat.ai.ally.AllyTauntRegistry;
import com.crackedgames.craftics.combat.ai.ally.IgnitableAlly;
import com.crackedgames.craftics.combat.ai.ally.IgnitableAllyRegistry;
import com.crackedgames.craftics.combat.ai.ally.RangedAllyAI;
import com.crackedgames.craftics.combat.ai.ally.SupportAllyAI;
import com.crackedgames.craftics.combat.ai.ally.TankAllyAI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for the Golem Overhaul abilities wiring (no Minecraft registry). */
class GolemOverhaulCompatTest {

    @AfterEach
    void tearDown() {
        AllyArchetypes.clearRegisteredForTest();
        AllyAbilities.clearRegisteredForTest();
        AllyDeathRegistry.clearRegisteredForTest();
        AllyKillRegistry.clearRegisteredForTest();
        AllyTauntRegistry.clearRegisteredForTest();
        IgnitableAllyRegistry.clearRegisteredForTest();
    }

    @Test
    void archetypes_registerAndResolve() {
        AllyAI tank = new TankAllyAI();
        AllyArchetypes.register("golemoverhaul:test_tank", tank);
        assertSame(tank, AllyArchetypes.aiFor("golemoverhaul:test_tank"));
    }

    @Test
    void archetypes_unregisteredFallsBackToMelee() {
        AllyAI ai = AllyArchetypes.aiFor("golemoverhaul:never_registered_xyz");
        assertNotNull(ai);
    }

    @Test
    void archetypes_registeredOverridesVanillaByType() {
        AllyAI custom = new TankAllyAI();
        AllyArchetypes.register("minecraft:iron_golem", custom);
        assertSame(custom, AllyArchetypes.aiFor("minecraft:iron_golem"));
    }

    @Test
    void onHitEffects_registerAndResolve() {
        com.crackedgames.craftics.combat.ai.ally.AllyAbilities.register(
            "golemoverhaul:test_soaker",
            com.crackedgames.craftics.combat.ai.ally.AllyAbilities.OnHitEffect.SOAK);
        assertEquals(
            com.crackedgames.craftics.combat.ai.ally.AllyAbilities.OnHitEffect.SOAK,
            com.crackedgames.craftics.combat.ai.ally.AllyAbilities.effectFor("golemoverhaul:test_soaker"));
    }

    @Test
    void onHitEffects_unregisteredIsNone() {
        assertEquals(
            com.crackedgames.craftics.combat.ai.ally.AllyAbilities.OnHitEffect.NONE,
            com.crackedgames.craftics.combat.ai.ally.AllyAbilities.effectFor("golemoverhaul:never_xyz"));
    }

    @Test
    void registerBehaviors_assignsGolemArchetypesAndEffects() {
        GolemOverhaulCompat.registerBehaviors();

        assertTrue(AllyArchetypes.aiFor("golemoverhaul:terracotta_golem") instanceof TankAllyAI);
        assertTrue(AllyArchetypes.aiFor("golemoverhaul:hay_golem") instanceof SupportAllyAI);
        assertTrue(AllyArchetypes.aiFor("golemoverhaul:honey_golem") instanceof SupportAllyAI);
        assertTrue(AllyArchetypes.aiFor("golemoverhaul:candle_golem") instanceof RangedAllyAI);

        // Coal/slime/kelp/barrel/netherite stay melee (not registered as tank/support/ranged).
        AllyAI coal = AllyArchetypes.aiFor("golemoverhaul:coal_golem");
        assertFalse(coal instanceof TankAllyAI);
        assertFalse(coal instanceof SupportAllyAI);
        assertFalse(coal instanceof RangedAllyAI);

        assertEquals(AllyAbilities.OnHitEffect.SOAK,
            AllyAbilities.effectFor("golemoverhaul:kelp_golem"));
        assertEquals(AllyAbilities.OnHitEffect.BURN,
            AllyAbilities.effectFor("golemoverhaul:candle_golem"));

        // The split-spawned small slime inherits no unintended on-hit effect.
        assertEquals(AllyAbilities.OnHitEffect.NONE,
            AllyAbilities.effectFor("golemoverhaul:small_slime_golem"));
    }

    @Test
    void registerBehaviors_wiresGenericExtensionHooks() {
        GolemOverhaulCompat.registerBehaviors();

        // Terracotta golem taunts; a non-taunter does not.
        assertTrue(AllyTauntRegistry.isTaunter("golemoverhaul:terracotta_golem"));
        assertFalse(AllyTauntRegistry.isTaunter("golemoverhaul:hay_golem"));

        // Coal golem is ignitable; hay golem is not.
        assertTrue(IgnitableAllyRegistry.isIgnitable("golemoverhaul:coal_golem"));
        assertFalse(IgnitableAllyRegistry.isIgnitable("golemoverhaul:hay_golem"));

        // Coal golem's lit transform matches LitCoalState.
        IgnitableAlly coal = IgnitableAllyRegistry.transformFor("golemoverhaul:coal_golem");
        assertNotNull(coal);
        assertEquals(LitCoalState.litAttack(3), coal.litAttack(3));
        assertEquals(LitCoalState.litSpeed(2), coal.litSpeed(2));

        // Slime golem has a death hook; coal golem does not.
        assertNotNull(AllyDeathRegistry.hookFor("golemoverhaul:slime_golem"));
        assertNull(AllyDeathRegistry.hookFor("golemoverhaul:coal_golem"));

        // Barrel golem has a kill hook; coal golem does not.
        assertNotNull(AllyKillRegistry.hookFor("golemoverhaul:barrel_golem"));
        assertNull(AllyKillRegistry.hookFor("golemoverhaul:coal_golem"));
    }

    @Test
    void litCoal_ignitableAdapterMatchesStatics() {
        assertEquals(LitCoalState.litAttack(5), LitCoalState.IGNITABLE.litAttack(5));
        assertEquals(LitCoalState.litSpeed(1), LitCoalState.IGNITABLE.litSpeed(1));
    }

    @Test
    void litCoal_isOnlyForCoalGolem() {
        GolemOverhaulCompat.registerBehaviors();
        assertTrue(IgnitableAllyRegistry.isIgnitable("golemoverhaul:coal_golem"));
        assertFalse(IgnitableAllyRegistry.isIgnitable("golemoverhaul:hay_golem"));
        assertFalse(IgnitableAllyRegistry.isIgnitable("minecraft:iron_golem"));
        assertFalse(IgnitableAllyRegistry.isIgnitable(null));
    }

    @Test
    void litCoal_boostsAttackAndSpeed() {
        assertEquals(9, LitCoalState.litAttack(3));   // 3*2+3
        assertEquals(4, LitCoalState.litSpeed(2));    // 2+2
        assertEquals(3, LitCoalState.litAttack(0));   // boundary: zero base
        assertEquals(2, LitCoalState.litSpeed(0));
    }
}
