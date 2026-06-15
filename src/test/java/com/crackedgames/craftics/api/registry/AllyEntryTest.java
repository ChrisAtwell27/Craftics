package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.ai.ally.MeleeAllyAI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the pure {@link AllyEntry} builder - defaults, validation, overrides. */
class AllyEntryTest {

    @Test
    void builder_appliesDefaults() {
        AllyEntry e = AllyEntry.builder("minecraft:wolf").build();
        assertEquals(AllyEntry.RecruitMode.TAMED, e.recruitMode());
        assertTrue(e.scalesWithOwnerGear());
        assertNull(e.roundHook());
        assertNull(e.healItem());
        assertEquals(0, e.healAmount());
        assertInstanceOf(MeleeAllyAI.class, e.ai());
    }

    @Test
    void builder_storesStatsAndOverrides() {
        AllyEntry e = AllyEntry.builder("minecraft:wolf")
            .hp(8).attack(3).defense(0).range(1).speed(3)
            .recruitMode(AllyEntry.RecruitMode.BUILT)
            .scalesWithOwnerGear(false)
            .build();
        assertEquals("minecraft:wolf", e.entityTypeId());
        assertEquals(8, e.hp());
        assertEquals(3, e.attack());
        assertEquals(0, e.defense());
        assertEquals(1, e.range());
        assertEquals(3, e.speed());
        assertEquals(AllyEntry.RecruitMode.BUILT, e.recruitMode());
        assertFalse(e.scalesWithOwnerGear());
    }

    @Test
    void builder_healItem_setsAmount() {
        // healItem(Item, int) sets both fields atomically. A null Item is fine here -
        // the builder does not validate it - so this stays a pure-logic test.
        AllyEntry e = AllyEntry.builder("minecraft:iron_golem")
            .healItem(null, 7)
            .build();
        assertEquals(7, e.healAmount());
        assertNull(e.healItem());
    }

    @Test
    void build_rejectsBlankEntityType() {
        assertThrows(IllegalStateException.class,
            () -> AllyEntry.builder("  ").build());
        assertThrows(IllegalStateException.class,
            () -> AllyEntry.builder(null).build());
    }
}
