package com.crackedgames.craftics.combat.miniboss;

import com.crackedgames.craftics.combat.miniboss.mechanics.*;
import com.crackedgames.craftics.level.LevelDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that every level-4 encounter can explain itself to the guide book.
 *
 * <p>The description is the only place a player can find out what waits on level 4 of a biome
 * before walking into it, and the mechanic is the only thing that knows. A mechanic shipped
 * without one is not a crash or a visible gap - it is a biome page that quietly says nothing
 * about its own set piece, which is the kind of omission nobody notices until someone asks why
 * one biome has a note and the next does not.
 *
 * <p>These construct the mechanics directly rather than reading the live registry, because the
 * real registrations happen in the mod's entrypoint and need a running game. That leaves one
 * honest gap: a new mechanic is covered only once somebody adds it below. The list cannot be
 * checked against the registration array without a running game, so it is kept in that array's
 * order instead, which is the most a test here can do to make a missing entry visible.
 */
class MinibossDescriptionTest {

    /** Every mechanic the mod registers, in the order CrafticsMod registers them. */
    private static List<MinibossMechanic> allMechanics() {
        return List.of(
            new PlainsGraveyardMechanic(),
            new DesertSandstormMechanic(),
            new JungleBroodmotherMechanic(),
            new RiverFlashFloodMechanic(),
            new SnowyBlizzardMechanic(),
            new MountainRockbreakerMechanic(),
            new CaveInMechanic(),
            new NetherFireRainMechanic(),
            new SoulSandColossusMechanic(),
            new CrimsonFungalBloomMechanic(),
            new WarpedEndermanMechanic(),
            new BasaltMagmaSurgeMechanic(),
            new OuterEndVoidRiftMechanic(),
            new EndCityShulkerMechanic(),
            new ChorusGroveBloomMechanic(),
            // Registered only when a creaking entity is available (1.21.4+, or the backport
            // on older shards). Constructing it needs none of that, so its description is
            // pinned like the rest - a conditional registration is still a shipped fight.
            new ForestCreakingMechanic());
    }

    @Test
    void everyMechanicDescribesItself() {
        for (MinibossMechanic m : allMechanics()) {
            String text = m.description();
            assertNotNull(text, m.getClass().getSimpleName() + " returned null");
            assertFalse(text.isBlank(),
                m.getClass().getSimpleName() + " has no description - its biome page will say "
                + "nothing about its level 4");
        }
    }

    @Test
    void descriptionsAreProseRatherThanLabels() {
        // A bare name ("Sandstorm") tells a player nothing they could not guess from the biome.
        // The point is what the encounter DOES, which does not fit in three words.
        for (MinibossMechanic m : allMechanics()) {
            assertTrue(m.description().length() > 40,
                m.getClass().getSimpleName() + " description is too short to say anything: "
                + m.description());
        }
    }

    @Test
    void descriptionsCarryNoFormattingCodes() {
        // The banner is styled; the guide book page is not. A stray colour code would leak
        // section formatting into prose that the book colours itself.
        for (MinibossMechanic m : allMechanics()) {
            assertFalse(m.description().contains("§"),
                m.getClass().getSimpleName() + " description contains a formatting code");
        }
    }

    @Test
    void eachMechanicClaimsADistinctBiome() {
        // The registry keys on biomeId, so two mechanics claiming one biome means the second
        // silently replaces the first.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MinibossMechanic m : allMechanics()) {
            assertTrue(seen.add(m.biomeId()),
                "two mechanics claim the biome " + m.biomeId());
        }
    }

    @Test
    void descriptionsAreDistinct() {
        // Copy-paste between these files is the obvious way to write sixteen of them, and a
        // duplicated line describes the wrong fight on somebody's page.
        java.util.Map<String, String> byText = new java.util.HashMap<>();
        for (MinibossMechanic m : allMechanics()) {
            String prior = byText.put(m.description(), m.getClass().getSimpleName());
            assertNull(prior, m.getClass().getSimpleName() + " shares its description with " + prior);
        }
    }

    @Test
    void aMechanicThatWritesNoDescriptionSimplyHasNone() {
        // The default, for addon mechanics. It must be empty rather than null, so the caller
        // can treat "nothing to say" as ordinary rather than as a case to guard.
        MinibossMechanic bare = new MinibossMechanic() {
            @Override public String biomeId() { return "addon:somewhere"; }
            @Override public List<LevelDefinition.EnemySpawn> initialSpawns(
                int w, int h, int o, Random r) { return List.of(); }
            @Override public String introTitle() { return "Somewhere"; }
        };
        assertEquals("", bare.description());
    }
}
