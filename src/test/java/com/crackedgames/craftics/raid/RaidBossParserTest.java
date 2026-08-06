package com.crackedgames.craftics.raid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RaidBossParserTest {

    private static final Set<String> KNOWN = Set.of(
        "fireball_rain", "lava_cage", "gore_charge", "magma_eruption",
        "tremor_stomp", "ground_slam", "rampage", "skull_barrage", "decay_aura");

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static String valid() {
        return """
        {
          "id": "ashen_tyrant",
          "name": "The Ashen Tyrant",
          "entity": "minecraft:wither_skeleton",
          "hp": 900, "attack": 14, "defense": 6, "range": 1, "speed": 3,
          "moves": ["fireball_rain","lava_cage","gore_charge",
                    "magma_eruption","tremor_stomp","ground_slam"],
          "power": { "type": "double_move" },
          "arena": 3,
          "environment": "nether",
          "bounty": 64,
          "loot": [ {"item":"minecraft:diamond","weight":8,"min":2,"max":5} ],
          "weight": 10
        }
        """;
    }

    @Test
    void parsesAValidDefinition() {
        RaidBossParser.Result r = RaidBossParser.parse(json(valid()), "ashen_tyrant", KNOWN);
        assertTrue(r.ok(), () -> "errors: " + r.errors());
        RaidBossDefinition d = r.definition();
        assertEquals("ashen_tyrant", d.id());
        assertEquals("The Ashen Tyrant", d.name());
        assertEquals("minecraft:wither_skeleton", d.entityTypeId());
        assertEquals(900, d.hp());
        assertEquals(6, d.moves().size());
        assertTrue(d.power().isDoubleMove());
        assertEquals(2, d.arenaVariant()); // JSON is 1-based, stored 0-based
        assertEquals("nether", d.environmentId());
        assertEquals(64, d.bounty());
        assertEquals(1, d.loot().size());
        assertEquals(10, d.weight());
    }

    @Test
    void derivesAiKeysFromTheId() {
        RaidBossDefinition d = RaidBossParser.parse(json(valid()), "ashen_tyrant", KNOWN).definition();
        assertEquals("raidboss/ashen_tyrant", d.aiKey());
        assertEquals("boss:raidboss/ashen_tyrant", d.bossAiRegistryKey());
    }

    @Test
    void idMustMatchTheFileStem() {
        RaidBossParser.Result r = RaidBossParser.parse(json(valid()), "something_else", KNOWN);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("filename")));
    }

    @Test
    void missingRequiredFieldsAreErrors() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","moves":["fireball_rain"],"power":{"type":"double_move"},
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("name")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("entity")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("hp")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("attack")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("bounty")));
    }

    @Test
    void nonPositiveStatsAreErrors() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":0,"attack":-1,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":0,
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertFalse(r.ok());
        assertEquals(3, r.errors().stream()
            .filter(e -> e.contains("positive")).count());
    }

    @Test
    void unknownAbilityIdsAreDroppedWithAWarning() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain","not_a_real_move","lava_cage"],
             "power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertTrue(r.ok());
        assertEquals(List.of("fireball_rain", "lava_cage"), r.definition().moves());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("not_a_real_move")));
    }

    @Test
    void moreThanEightMovesIsTruncated() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain","lava_cage","gore_charge","magma_eruption",
                      "tremor_stomp","ground_slam","rampage","skull_barrage","decay_aura"],
             "power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertTrue(r.ok());
        assertEquals(8, r.definition().moves().size());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("truncated")));
    }

    @Test
    void fewerThanSixMovesWarnsButLoads() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain","lava_cage"],
             "power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertTrue(r.ok());
        assertEquals(2, r.definition().moves().size());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("fewer than 6")));
    }

    @Test
    void noValidMovesIsAnError() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["nope","also_nope"],
             "power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("no valid moves")));
    }

    @Test
    void parsesABuffPower() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],
             "power":{"type":"buff","effect":"strength","amplifier":1},
             "bounty":10,"loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertTrue(r.ok());
        assertFalse(r.definition().power().isDoubleMove());
        assertEquals("strength", r.definition().power().buffEffect());
        assertEquals(1, r.definition().power().amplifier());
    }

    @Test
    void buffWithoutAnEffectIsAnError() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"buff"},
             "bounty":10,"loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("effect")));
    }

    @Test
    void missingPowerIsAnError() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"bounty":10,
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("power")));
    }

    @Test
    void lootDefaultsAndEmptyLootIsAnError() {
        RaidBossParser.Result withDefaults = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertTrue(withDefaults.ok());
        RaidBossLootEntry e = withDefaults.definition().loot().get(0);
        assertEquals(5, e.weight());
        assertEquals(1, e.min());
        assertEquals(1, e.max());

        RaidBossParser.Result empty = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[]}
            """), "x", KNOWN);
        assertFalse(empty.ok());
        assertTrue(empty.errors().stream().anyMatch(msg -> msg.contains("loot")));
    }

    @Test
    void optionalFieldsFallBackToDefaults() {
        RaidBossDefinition d = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN).definition();
        assertEquals(0, d.defense());
        assertEquals(1, d.range());
        assertEquals(0, d.speed());
        assertEquals(-1, d.arenaVariant());
        assertEquals("plains", d.environmentId());
        assertEquals(10, d.weight());
    }

    @Test
    void malformedJsonTypesProduceErrorsRatherThanThrowing() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":"lots","attack":5,
             "moves":"not_an_array","power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}]}
            """), "x", KNOWN);
        assertFalse(r.ok());
        assertFalse(r.errors().isEmpty());
    }

    @Test
    void obstaclesDefaultToEmptyWhenAbsent() {
        RaidBossParser.Result r = RaidBossParser.parse(json(valid()), "ashen_tyrant", KNOWN);
        assertTrue(r.ok());
        assertTrue(r.definition().obstacles().isEmpty());
    }

    @Test
    void parsesObstaclesWithBothCountForms() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}],
             "obstacles":[
               {"tile":"lava","block":"minecraft:lava","count":{"min":3,"max":6},"cluster":3},
               {"tile":"obstacle","block":"minecraft:cactus","count":10}
             ]}
            """), "x", KNOWN);
        assertTrue(r.ok(), () -> "errors: " + r.errors());
        assertEquals(2, r.definition().obstacles().size());

        RaidBossObstacle lava = r.definition().obstacles().get(0);
        assertEquals("LAVA", lava.tileType());
        assertEquals("minecraft:lava", lava.blockId());
        assertEquals(3, lava.minCount());
        assertEquals(6, lava.maxCount());
        assertEquals(3, lava.cluster());

        RaidBossObstacle cactus = r.definition().obstacles().get(1);
        assertEquals("OBSTACLE", cactus.tileType());
        assertEquals(10, cactus.minCount());
        assertEquals(10, cactus.maxCount());
        assertEquals(1, cactus.cluster());
    }

    @Test
    void obstacleTileNamesAreCaseInsensitive() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}],
             "obstacles":[{"tile":"Soul_Fire","count":3}]}
            """), "x", KNOWN);
        assertTrue(r.ok());
        assertEquals("SOUL_FIRE", r.definition().obstacles().get(0).tileType());
        assertEquals("", r.definition().obstacles().get(0).blockId());
    }

    @Test
    void unknownObstacleTileIsDroppedWithAWarning() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}],
             "obstacles":[{"tile":"not_a_tile","count":3},{"tile":"ice","count":2}]}
            """), "x", KNOWN);
        assertTrue(r.ok());
        assertEquals(1, r.definition().obstacles().size());
        assertEquals("ICE", r.definition().obstacles().get(0).tileType());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("not_a_tile")));
    }

    @Test
    void obstacleCountsAndClusterAreClamped() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}],
             "obstacles":[{"tile":"water","count":{"min":9,"max":2},"cluster":0}]}
            """), "x", KNOWN);
        assertTrue(r.ok());
        RaidBossObstacle water = r.definition().obstacles().get(0);
        assertEquals(9, water.minCount());
        assertEquals(9, water.maxCount(), "max below min clamps up to min");
        assertEquals(1, water.cluster(), "cluster floors at 1");
    }

    @Test
    void obstacleRowWithNonStringTileIsSkippedWithWarning() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}],
             "obstacles":[{"tile":null,"count":3}]}
            """), "x", KNOWN);
        assertTrue(r.ok(), () -> "errors: " + r.errors());
        assertTrue(r.definition().obstacles().isEmpty());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("tile")));
    }

    @Test
    void obstacleRowWithNonNumericCountIsSkippedWithWarning() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}],
             "obstacles":[{"tile":"lava","count":"abc"}]}
            """), "x", KNOWN);
        assertTrue(r.ok(), () -> "errors: " + r.errors());
        assertTrue(r.definition().obstacles().isEmpty());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("count")));
    }

    @Test
    void anEmptyObstacleArrayIsFine() {
        RaidBossParser.Result r = RaidBossParser.parse(json("""
            {"id":"x","name":"X","entity":"minecraft:zombie","hp":100,"attack":5,
             "moves":["fireball_rain"],"power":{"type":"double_move"},"bounty":10,
             "loot":[{"item":"minecraft:diamond"}],"obstacles":[]}
            """), "x", KNOWN);
        assertTrue(r.ok());
        assertTrue(r.definition().obstacles().isEmpty());
    }
}
