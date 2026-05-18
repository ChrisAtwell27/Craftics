package com.crackedgames.craftics.level;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition.EnemySpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link EnemySpawn}'s backward-compatible constructor. */
class EnemySpawnTest {

    @Test
    void sixArgConstructor_defaultsAiKeyToEntityType() {
        EnemySpawn s = new EnemySpawn("minecraft:zombie", new GridPos(2, 3), 6, 2, 0, 1);
        assertEquals("minecraft:zombie", s.aiKey());
    }

    @Test
    void sevenArgConstructor_keepsExplicitAiKey() {
        EnemySpawn s = new EnemySpawn(
            "minecraft:husk", new GridPos(2, 3), 10, 3, 1, 1, "minecraft:skeleton");
        assertEquals("minecraft:husk", s.entityTypeId());
        assertEquals("minecraft:skeleton", s.aiKey());
    }
}
