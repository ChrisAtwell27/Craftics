package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The forest biome's level-4 miniboss: the Pale Garden's Creaking encounter. Spawns 2 creakings
 * each paired with a creaking heart. Depth-scaled via
 * {@code ordinal} (campaign ordinal in campaign runs, virtualOrdinal in infinite runs) exactly
 * like the encounter it replaces ({@code LevelGenerator#buildPaleGardenLevel}, now removed).
 *
 * <p>The arena override (Pale Garden schem) is applied by
 * {@link com.crackedgames.craftics.level.LevelGenerator#buildMinibossLevel} for the "forest"
 * biome id, not here - {@code initialSpawns} only has width/height/ordinal/rng, no arena hook.
 */
public final class ForestCreakingMechanic implements MinibossMechanic {

    @Override
    public String biomeId() {
        return "forest";
    }

    @Override
    public String introTitle() {
        return "§8§l☠ The Pale Garden";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        String creakingId = com.crackedgames.craftics.compat.palegardenbackport
            .PaleGardenBackportCompat.creakingEntityId();

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        int creakingCount = 2;
        for (int i = 0; i < creakingCount; i++) {
            GridPos creakingPos = MinibossSpawns.findOpen(width, height, used, rng);
            if (creakingPos == null) continue;
            used.add(creakingPos);
            spawns.add(new LevelDefinition.EnemySpawn(creakingId, creakingPos,
                18 + hpBonus, 5 + atkBonus, 2, 1));

            // Creaking Heart - placed nearby, away from the player.
            GridPos heartPos = MinibossSpawns.findOpen(width, height, used, rng);
            if (heartPos == null) continue;
            used.add(heartPos);
            spawns.add(new LevelDefinition.EnemySpawn("craftics:creaking_heart", heartPos,
                10 + hpBonus / 2, 0, 0, 0));
        }

        return spawns;
    }
}
