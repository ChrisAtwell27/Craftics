package com.crackedgames.craftics.combat.miniboss;

import com.crackedgames.craftics.combat.miniboss.mechanics.*;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every non-dragon biome must have exactly one miniboss mechanic, keyed on a distinct biomeId.
 *
 * <p>This constructs the mechanics directly (their constructors touch no Minecraft types, so no
 * bootstrap is needed) and checks the biomeId set. It does NOT exercise MinibossRegistry.register,
 * which runs in {@code CrafticsMod.onInitialize} under a live game and can't be reached from a
 * plain unit test (see the project's no-bootstrap convention).
 */
class MinibossCoverageTest {

    /** The 17 biomes that get a level-4 miniboss. dragons_nest (3 levels) is intentionally absent. */
    private static final Set<String> EXPECTED_BIOMES = Set.of(
        "plains", "desert", "jungle", "forest", "river", "snowy", "mountain", "cave", "deep_dark",
        "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas",
        "outer_end_islands", "end_city", "chorus_grove");

    private static final List<MinibossMechanic> ALL = List.of(
        new PlainsGraveyardMechanic(),
        new DesertSandstormMechanic(),
        new JungleBroodmotherMechanic(),
        new ForestCreakingMechanic(),
        new RiverFlashFloodMechanic(),
        new SnowyBlizzardMechanic(),
        new MountainRockbreakerMechanic(),
        new CaveInMechanic(),
        new DeepDarkWardenMechanic(),
        new NetherFireRainMechanic(),
        new SoulSandColossusMechanic(),
        new CrimsonFungalBloomMechanic(),
        new WarpedEndermanMechanic(),
        new BasaltMagmaSurgeMechanic(),
        new OuterEndVoidRiftMechanic(),
        new EndCityShulkerMechanic(),
        new ChorusGroveBloomMechanic());

    @Test
    void seventeenMechanicsExist() {
        assertEquals(17, ALL.size());
    }

    @Test
    void everyExpectedBiomeIsCovered() {
        Set<String> ids = new HashSet<>();
        for (MinibossMechanic m : ALL) ids.add(m.biomeId());
        assertEquals(EXPECTED_BIOMES, ids, "biome coverage mismatch");
    }

    @Test
    void biomeIdsAreDistinct() {
        Set<String> ids = new HashSet<>();
        for (MinibossMechanic m : ALL) {
            assertTrue(ids.add(m.biomeId()), "duplicate biomeId: " + m.biomeId());
        }
    }

    @Test
    void dragonsNestHasNoMechanic() {
        for (MinibossMechanic m : ALL) {
            assertNotEquals("dragons_nest", m.biomeId());
        }
    }

    @Test
    void everyMechanicHasATitle() {
        for (MinibossMechanic m : ALL) {
            assertNotNull(m.introTitle());
            assertFalse(m.introTitle().isBlank(), m.biomeId() + " has a blank title");
        }
    }
}
