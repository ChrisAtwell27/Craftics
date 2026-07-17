package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.crackedgames.craftics.combat.CombatEffects.EffectType;

class DebuffCopyTest {

    @Test
    void copyableTypesHaveAnEnemySideApply() {
        // These have stack* methods on CombatEntity.
        assertTrue(DebuffCopy.copyableToEnemy(EffectType.POISON));
        assertTrue(DebuffCopy.copyableToEnemy(EffectType.BURNING));
        assertTrue(DebuffCopy.copyableToEnemy(EffectType.WITHER));
        assertTrue(DebuffCopy.copyableToEnemy(EffectType.SLOWNESS));
        assertTrue(DebuffCopy.copyableToEnemy(EffectType.BLEEDING));
        assertTrue(DebuffCopy.copyableToEnemy(EffectType.BLINDNESS));
        assertTrue(DebuffCopy.copyableToEnemy(EffectType.SOAKED));
        assertTrue(DebuffCopy.copyableToEnemy(EffectType.CONFUSION));
    }

    @Test
    void typesWithNoEnemyApplyAreNotCopyable() {
        // No enemy-side stack* method exists for these; the copy loop must skip them.
        assertFalse(DebuffCopy.copyableToEnemy(EffectType.WEAKNESS));
        assertFalse(DebuffCopy.copyableToEnemy(EffectType.MINING_FATIGUE));
        assertFalse(DebuffCopy.copyableToEnemy(EffectType.LEVITATION));
        assertFalse(DebuffCopy.copyableToEnemy(EffectType.DARKNESS));
    }

    @Test
    void buffsAreNotCopyable() {
        assertFalse(DebuffCopy.copyableToEnemy(EffectType.REGENERATION));
    }
}
