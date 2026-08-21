package com.crackedgames.craftics.combat.biomeeffect;

import com.crackedgames.craftics.combat.biomeeffect.effects.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that every biome weather effect can explain itself to the guide book.
 *
 * <p>Deep Dark is the case that made this matter: it has no level-4 encounter, so its sculk
 * sensors are the only special condition its page can describe. The page previously printed the
 * effect's id and stopped - naming the thing about to blind your party while saying nothing
 * about the Swift Sneak boots that prevent it or the pickaxe that removes it.
 *
 * <p>Constructed directly rather than read from the registry, because the real registrations
 * happen in the mod's entrypoint and need a running game. A new effect is therefore covered only
 * once somebody adds it below; the list is kept in the order CrafticsMod registers them so a
 * missing entry is visible side by side.
 */
class BiomeEffectDescriptionTest {

    private static List<BiomeEffect> allEffects() {
        return List.of(
            new BlizzardWindsEffect(),
            new JungleRainEffect(),
            new SandstormEffect(),
            new SculkSensorEffect(),
            new WarpedMovementEffect(),
            new CrimsonBloomEffect(),
            new RiverCurrentEffect(),
            new ForestHiveEffect());
    }

    @Test
    void everyEffectDescribesItself() {
        for (BiomeEffect e : allEffects()) {
            String text = e.description();
            assertNotNull(text, e.getClass().getSimpleName() + " returned null");
            assertFalse(text.isBlank(),
                e.getClass().getSimpleName() + " has no description - its biome page will print "
                + "the effect's name and nothing else");
        }
    }

    @Test
    void deepDarksEffectExplainsItsCounterplay() {
        // The one biome whose whole special condition IS its weather. A description that only
        // said "sculk sensors are dangerous" would leave a player with no idea what to do about
        // them, which is the entire reason this page needed prose.
        String text = new SculkSensorEffect().description();
        assertTrue(text.contains("Swift Sneak"), "does not mention the boots that prevent it");
        assertTrue(text.toLowerCase().contains("pickaxe"), "does not mention how to remove one");
    }

    @Test
    void descriptionsAreProseRatherThanLabels() {
        for (BiomeEffect e : allEffects()) {
            assertTrue(e.description().length() > 40,
                e.getClass().getSimpleName() + " description is too short to say anything: "
                + e.description());
        }
    }

    @Test
    void descriptionsCarryNoFormattingCodes() {
        // The page colours the heading itself; a stray code in the prose would leak through.
        for (BiomeEffect e : allEffects()) {
            assertFalse(e.description().contains("§"),
                e.getClass().getSimpleName() + " description contains a formatting code");
        }
    }

    @Test
    void eachEffectClaimsADistinctId() {
        // The registry keys on id, so a duplicate silently replaces the earlier effect.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (BiomeEffect e : allEffects()) {
            assertTrue(seen.add(e.id()), "two effects claim the id " + e.id());
        }
    }

    @Test
    void descriptionsAreDistinct() {
        java.util.Map<String, String> byText = new java.util.HashMap<>();
        for (BiomeEffect e : allEffects()) {
            String prior = byText.put(e.description(), e.getClass().getSimpleName());
            assertNull(prior, e.getClass().getSimpleName() + " shares its description with " + prior);
        }
    }

    @Test
    void anEffectThatWritesNoDescriptionSimplyHasNone() {
        // The default, for addon effects: empty rather than null, so "nothing to say" is an
        // ordinary answer instead of a case every caller has to guard.
        BiomeEffect bare = new BiomeEffect() {
            @Override public String id() { return "addon:weather"; }
        };
        assertEquals("", bare.description());
    }
}
