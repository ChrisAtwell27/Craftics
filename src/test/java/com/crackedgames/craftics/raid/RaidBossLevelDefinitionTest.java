package com.crackedgames.craftics.raid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only metadata is asserted here. buildTiles() and getFloorBlock() touch the
 * block registry and cannot run without a Minecraft bootstrap, so they are on
 * the in-game checklist instead.
 */
class RaidBossLevelDefinitionTest {

    private static RaidBossDefinition boss(int arenaVariant) {
        return new RaidBossDefinition(
            "ashen_tyrant", "The Ashen Tyrant", "minecraft:wither_skeleton",
            900, 14, 6, 1, 3, List.of("fireball_rain"),
            RaidBossPower.doubleMove(), arenaVariant, "nether", 64,
            List.of(new RaidBossLootEntry("minecraft:diamond", 8, 2, 5)), List.of(), 10);
    }

    @Test
    void usesASyntheticLevelNumber() {
        RaidBossLevelDefinition def = new RaidBossLevelDefinition(boss(-1), 5, null);
        assertTrue(def.getLevelNumber()
            >= com.crackedgames.craftics.level.LevelDefinition.SYNTHETIC_LEVEL_BASE);
        assertEquals(RaidBossLevelDefinition.RAIDBOSS_LEVEL_NUMBER, def.getLevelNumber());
    }

    @Test
    void pointsAtTheSharedRaidbossSchemSet() {
        RaidBossLevelDefinition def = new RaidBossLevelDefinition(boss(-1), 5, null);
        assertEquals("raidboss", def.getArenaBiomeId());
        assertEquals(RaidBossDefinition.ARENA_BIOME_ID, def.getArenaBiomeId());
    }

    @Test
    void aPinnedVariantWinsOverTheRolledOne() {
        assertEquals(2, new RaidBossLevelDefinition(boss(2), 5, null).getArenaVariantIndex());
    }

    @Test
    void anUnpinnedVariantUsesTheRolledOne() {
        assertEquals(5, new RaidBossLevelDefinition(boss(-1), 5, null).getArenaVariantIndex());
    }

    @Test
    void carriesTheBossEnvironmentAndName() {
        RaidBossLevelDefinition def = new RaidBossLevelDefinition(boss(-1), 0, null);
        assertEquals("nether", def.getArenaEnvironmentId());
        assertEquals("The Ashen Tyrant", def.getName());
    }

    @Test
    void declaresItsSingleSpawnAsTheBoss() {
        RaidBossLevelDefinition def = new RaidBossLevelDefinition(boss(-1), 0, null);
        assertEquals(0, def.getBossSpawnIndex());
        assertEquals("raidboss/ashen_tyrant", def.getBossAiBiomeId());
        assertEquals(1, def.getEnemySpawns().length);
        assertEquals("minecraft:wither_skeleton", def.getEnemySpawns()[0].entityTypeId());
        assertEquals(900, def.getEnemySpawns()[0].hp());
        assertEquals("raidboss/ashen_tyrant", def.getEnemySpawns()[0].aiKey());
    }

    @Test
    void alwaysDeclaresAnOverrideOrigin() {
        // A synthetic level must never reach the level*300 numbered-arena formula,
        // so it declares an override origin whether or not one was supplied.
        assertTrue(new RaidBossLevelDefinition(boss(-1), 0, null).hasOverrideOrigin());
    }
}
