package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@link EnemyAI#isHostileThreat} classification that drives the
 * anti-farming auto-end. Passive mobs are never threats; neutral mobs only
 * become threats once provoked (enraged / hit).
 */
class HostileThreatClassificationTest {

    private static CombatEntity mob(String type) {
        return new CombatEntity(1, type, new GridPos(0, 0), 10, 2, 0, 1);
    }

    private static final GridPos PLAYER = new GridPos(3, 3);

    @Test
    void passiveAi_isNeverAThreat() {
        CombatEntity cow = mob("minecraft:cow");
        assertFalse(new PassiveAI().isHostileThreat(cow, null, PLAYER));
        assertFalse(new RabbitAI().isHostileThreat(mob("minecraft:rabbit"), null, PLAYER));
        assertFalse(new BatAI().isHostileThreat(mob("minecraft:bat"), null, PLAYER));
        assertFalse(new CodAI().isHostileThreat(mob("minecraft:cod"), null, PLAYER));
    }

    @Test
    void neutralAi_isNotAThreatUntilEnraged() {
        CombatEntity wolf = mob("minecraft:wolf");
        WolfAI ai = new WolfAI();
        assertFalse(ai.isHostileThreat(wolf, null, PLAYER), "unprovoked wolf is not a threat");

        wolf.setEnraged(true);
        assertTrue(ai.isHostileThreat(wolf, null, PLAYER), "provoked wolf is a threat");
    }

    @Test
    void enrageGatedNeutrals_followTheEnragedFlag() {
        record Case(EnemyAI ai, CombatEntity self) {}
        Case[] cases = {
            new Case(new BeeAI(), mob("minecraft:bee")),
            new Case(new FoxAI(), mob("minecraft:fox")),
            new Case(new GoatAI(), mob("minecraft:goat")),
            new Case(new CatAI(), mob("minecraft:cat")),
            new Case(new LlamaAI(), mob("minecraft:llama")),
            new Case(new PolarBearAI(), mob("minecraft:polar_bear")),
        };
        for (Case c : cases) {
            assertFalse(c.ai().isHostileThreat(c.self(), null, PLAYER),
                c.self().getEntityTypeId() + " should be calm by default");
            c.self().setEnraged(true);
            assertTrue(c.ai().isHostileThreat(c.self(), null, PLAYER),
                c.self().getEntityTypeId() + " should be a threat when enraged");
        }
    }

    @Test
    void hostileAi_isAThreatByDefault() {
        CombatEntity zombie = mob("minecraft:zombie");
        assertTrue(new ZombieAI().isHostileThreat(zombie, null, PLAYER));
        assertTrue(new SkeletonAI().isHostileThreat(mob("minecraft:skeleton"), null, PLAYER));
    }
}
