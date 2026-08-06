package com.crackedgames.craftics.raid;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RaidBossJsonWriterTest {

    private static final Set<String> KNOWN = Set.of("fireball_rain", "lava_cage", "gore_charge");

    private static RaidBossDefinition sample(RaidBossPower power, int arenaVariant) {
        return new RaidBossDefinition(
            "ashen_tyrant", "The Ashen Tyrant", "minecraft:wither_skeleton",
            900, 14, 6, 1, 3,
            List.of("fireball_rain", "lava_cage", "gore_charge"),
            power, arenaVariant, "nether", 64,
            List.of(new RaidBossLootEntry("minecraft:diamond", 8, 2, 5)), List.of(), 10);
    }

    @Test
    void doubleMoveDefinitionRoundTrips() {
        RaidBossDefinition original = sample(RaidBossPower.doubleMove(), 2);
        JsonObject json = RaidBossJsonWriter.toJson(original);
        RaidBossParser.Result back = RaidBossParser.parse(json, "ashen_tyrant", KNOWN);
        assertTrue(back.ok(), () -> "errors: " + back.errors());
        assertEquals(original, back.definition());
    }

    @Test
    void buffDefinitionRoundTrips() {
        RaidBossDefinition original = sample(RaidBossPower.buff("regeneration", 2), 0);
        JsonObject json = RaidBossJsonWriter.toJson(original);
        RaidBossParser.Result back = RaidBossParser.parse(json, "ashen_tyrant", KNOWN);
        assertTrue(back.ok(), () -> "errors: " + back.errors());
        assertEquals(original, back.definition());
    }

    @Test
    void unsetArenaVariantRoundTripsAsUnset() {
        RaidBossDefinition original = sample(RaidBossPower.doubleMove(), -1);
        JsonObject json = RaidBossJsonWriter.toJson(original);
        assertFalse(json.has("arena"));
        assertEquals(-1, RaidBossParser.parse(json, "ashen_tyrant", KNOWN).definition().arenaVariant());
    }

    @Test
    void arenaVariantIsWrittenOneBased() {
        JsonObject json = RaidBossJsonWriter.toJson(sample(RaidBossPower.doubleMove(), 2));
        assertEquals(3, json.get("arena").getAsInt());
    }

    @Test
    void obstaclesRoundTrip() {
        RaidBossDefinition original = new RaidBossDefinition(
            "ashen_tyrant", "The Ashen Tyrant", "minecraft:wither_skeleton",
            900, 14, 6, 1, 3,
            List.of("fireball_rain", "lava_cage", "gore_charge"),
            RaidBossPower.doubleMove(), 2, "nether", 64,
            List.of(new RaidBossLootEntry("minecraft:diamond", 8, 2, 5)),
            List.of(new RaidBossObstacle("LAVA", "minecraft:lava", 3, 6, 3),
                    new RaidBossObstacle("TALL_GRASS", "", 12, 20, 1)),
            10);
        RaidBossParser.Result back = RaidBossParser.parse(
            RaidBossJsonWriter.toJson(original), "ashen_tyrant", KNOWN);
        assertTrue(back.ok(), () -> "errors: " + back.errors());
        assertEquals(original, back.definition());
    }

    @Test
    void anEmptyObstacleListIsOmittedFromJson() {
        assertFalse(RaidBossJsonWriter.toJson(sample(RaidBossPower.doubleMove(), 2)).has("obstacles"));
    }
}
