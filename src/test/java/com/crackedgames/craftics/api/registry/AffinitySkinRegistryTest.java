package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.AffinitySkin;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.PlayerProgression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for affinity reskins: renaming one of the eight fixed affinities, and the damage type
 * that scales from it, without changing anything mechanical.
 *
 * <p>The registry is static, so it is cleared around every test - a skin left behind would
 * rename an affinity for whichever test ran next.
 */
class AffinitySkinRegistryTest {

    @BeforeEach
    @AfterEach
    void reset() {
        AffinitySkinRegistry.clear();
    }

    // ── The unskinned default ────────────────────────────────────────────────

    @Test
    void unskinned_returnsCrafticsOwnText() {
        assertEquals(PlayerProgression.Affinity.SLASHING.displayName,
            AffinitySkinRegistry.nameOf(PlayerProgression.Affinity.SLASHING));
        assertEquals(PlayerProgression.Affinity.SLASHING.icon,
            AffinitySkinRegistry.iconOf(PlayerProgression.Affinity.SLASHING));
        assertEquals(PlayerProgression.Affinity.SLASHING.description,
            AffinitySkinRegistry.descriptionOf(PlayerProgression.Affinity.SLASHING));
    }

    @Test
    void unskinned_damageTypeKeepsItsOwnName() {
        assertEquals(DamageType.SLASHING.displayName,
            AffinitySkinRegistry.nameOf(DamageType.SLASHING));
    }

    @Test
    void isEmpty_tracksRegistration() {
        // Display code short-circuits on this, so it has to be honest.
        assertTrue(AffinitySkinRegistry.isEmpty());
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.PET, AffinitySkin.named("Ally"));
        assertFalse(AffinitySkinRegistry.isEmpty());
    }

    // ── Renaming ─────────────────────────────────────────────────────────────

    @Test
    void reskin_replacesAllThreeFields() {
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.SLASHING,
            AffinitySkin.of("Physical", "§c!", "+3 dmg to physical moves"));
        assertEquals("Physical", AffinitySkinRegistry.nameOf(PlayerProgression.Affinity.SLASHING));
        assertEquals("§c!", AffinitySkinRegistry.iconOf(PlayerProgression.Affinity.SLASHING));
        assertEquals("+3 dmg to physical moves",
            AffinitySkinRegistry.descriptionOf(PlayerProgression.Affinity.SLASHING));
    }

    @Test
    void reskin_nullFieldsKeepTheOriginal() {
        // named() exists for exactly this: rename without having to restate an icon and a
        // description you were happy with.
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.PET, AffinitySkin.named("Ally"));
        assertEquals("Ally", AffinitySkinRegistry.nameOf(PlayerProgression.Affinity.PET));
        assertEquals(PlayerProgression.Affinity.PET.icon,
            AffinitySkinRegistry.iconOf(PlayerProgression.Affinity.PET));
        assertEquals(PlayerProgression.Affinity.PET.description,
            AffinitySkinRegistry.descriptionOf(PlayerProgression.Affinity.PET));
    }

    @Test
    void reskin_onlyAffectsTheAffinityItNames() {
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.SLASHING,
            AffinitySkin.named("Physical"));
        assertEquals("Physical", AffinitySkinRegistry.nameOf(PlayerProgression.Affinity.SLASHING));
        assertEquals(PlayerProgression.Affinity.BLUNT.displayName,
            AffinitySkinRegistry.nameOf(PlayerProgression.Affinity.BLUNT));
    }

    @Test
    void reskin_replacingAnAffinityOverwritesTheEarlierSkin() {
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.WATER, AffinitySkin.named("Aqua"));
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.WATER, AffinitySkin.named("Hydro"));
        assertEquals("Hydro", AffinitySkinRegistry.nameOf(PlayerProgression.Affinity.WATER));
    }

    @Test
    void reskin_withNullSkinRemovesIt() {
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.WATER, AffinitySkin.named("Aqua"));
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.WATER, null);
        assertEquals(PlayerProgression.Affinity.WATER.displayName,
            AffinitySkinRegistry.nameOf(PlayerProgression.Affinity.WATER));
    }

    // ── The damage type follows its affinity ─────────────────────────────────

    @Test
    void damageType_takesTheNameOfTheAffinityItScalesFrom() {
        // The reason this exists at all: a player seeing "Fire affinity" next to "Slashing
        // damage" reads it as a bug rather than a theme.
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.SLASHING,
            AffinitySkin.named("Physical"));
        assertEquals("Physical", AffinitySkinRegistry.nameOf(DamageType.SLASHING));
    }

    @Test
    void damageType_isUnaffectedByAnUnrelatedAffinitysSkin() {
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.WATER, AffinitySkin.named("Aqua"));
        assertEquals(DamageType.BLUNT.displayName, AffinitySkinRegistry.nameOf(DamageType.BLUNT));
    }

    @Test
    void damageTypeIcon_isNullUntilSkinned() {
        // Damage types have no icon of their own; the panel borrows the affinity's. Null means
        // "keep doing whatever you did before", so an unskinned install is untouched.
        assertNull(AffinitySkinRegistry.iconOrNull(DamageType.SLASHING));
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.SLASHING,
            AffinitySkin.of("Physical", "§c!", "desc"));
        assertEquals("§c!", AffinitySkinRegistry.iconOrNull(DamageType.SLASHING));
    }

    @Test
    void damageTypeIcon_isNullWhenTheSkinOnlyRenamed() {
        AffinitySkinRegistry.reskin(PlayerProgression.Affinity.SLASHING,
            AffinitySkin.named("Physical"));
        assertNull(AffinitySkinRegistry.iconOrNull(DamageType.SLASHING));
    }

    // ── Every affinity is reachable ──────────────────────────────────────────

    @Test
    void everyAffinityCanBeSkinnedAndMapsToADamageType() {
        // Guards the one-to-one weld between the two enums. If a ninth affinity were added
        // without a matching damage type, or the mapping switch missed a case, a reskin would
        // silently fail to reach half the places that affinity is shown.
        for (PlayerProgression.Affinity a : PlayerProgression.Affinity.values()) {
            AffinitySkinRegistry.reskin(a, AffinitySkin.named("Skin-" + a.name()));
        }
        for (PlayerProgression.Affinity a : PlayerProgression.Affinity.values()) {
            assertEquals("Skin-" + a.name(), AffinitySkinRegistry.nameOf(a));
        }
        for (DamageType t : DamageType.values()) {
            PlayerProgression.Affinity mapped = DamageType.affinityOf(t);
            assertNotNull(mapped, t + " has no affinity to take a skin from");
            assertEquals("Skin-" + mapped.name(), AffinitySkinRegistry.nameOf(t));
        }
    }

    @Test
    void nullInputsAreTolerated() {
        // These run inside render loops and message builders; a null there should produce an
        // empty string, not take the screen down.
        assertEquals("", AffinitySkinRegistry.nameOf((PlayerProgression.Affinity) null));
        assertEquals("", AffinitySkinRegistry.iconOf(null));
        assertEquals("", AffinitySkinRegistry.descriptionOf(null));
        assertEquals("", AffinitySkinRegistry.nameOf((DamageType) null));
        assertNull(AffinitySkinRegistry.iconOrNull(null));
    }
}
