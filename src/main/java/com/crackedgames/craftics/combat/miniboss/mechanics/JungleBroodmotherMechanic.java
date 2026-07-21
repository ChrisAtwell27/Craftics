package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The jungle biome's level-4 miniboss: the Broodmother's Spawn encounter. Opens with one beefy
 * "elite" cave spider (roughly double a normal add's HP, so it reads as the miniboss) plus one
 * normal cave spider for company. Every 3rd round two more cave-spider adds skitter in near the
 * elite, unless the arena is already crowded.
 *
 * <p>Design note: the brief describes the broodmother leaving web (Rooted) tiles behind on
 * death/on hit. There is no Rooted effect setter on {@code CombatEntity} and no web/cobweb
 * {@link com.crackedgames.craftics.core.TileType} in this codebase - inventing either is out of
 * scope for this task. This mechanic implements the achievable core of the encounter (elite +
 * reinforcement waves) and drops the web-tile flavor entirely; the elite's doubled HP and the
 * steady add pressure carry the "miniboss lair" feel on their own.
 *
 * <p>No extra win objective - the elite is a normal enemy (no scenery/inert flags), so clearing
 * every spider (elite included) completes the fight via the base "all enemies cleared" check.
 * {@link #isComplete} uses the interface default (always true).
 */
public final class JungleBroodmotherMechanic implements MinibossMechanic {

    private static final int ADD_CADENCE = 3; // reinforcement wave every 3rd round
    private static final int ADD_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip reinforcements if this many enemies are already alive

    @Override
    public String biomeId() {
        return "jungle";
    }

    @Override
    public String introTitle() {
        return "§2§l☠ Broodmother's Spawn";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        // Elite broodmother, placed near arena center - roughly double a normal add's HP.
        GridPos elitePos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (elitePos == null) elitePos = new GridPos(width / 2, height / 2);
        used.add(elitePos);
        spawns.add(new LevelDefinition.EnemySpawn("minecraft:cave_spider", elitePos,
            40 + hpBonus * 2, 6 + atkBonus, 1, 1));

        // One normal cave spider for company at the opening.
        GridPos addPos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (addPos != null) {
            used.add(addPos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:cave_spider", addPos,
                14 + hpBonus, 3 + atkBonus, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
        // No vanilla spider "roar" exists; the ravager's roar is the nearest large-beast growl
        // vanilla offers and reads well as the elite broodmother's opening cue.
        ctx.playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 0.5f, 1.4f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % ADD_CADENCE != 0) return;

        int liveCount = 0;
        for (var e : ctx.enemies()) {
            if (e.isAlive()) liveCount++;
        }
        if (liveCount >= CROWD_CAP) return; // arena too crowded, skip this wave

        var arena = ctx.arena();
        List<GridPos> used = new ArrayList<>();
        for (var e : ctx.enemies()) {
            if (e.isAlive() && e.getGridPos() != null) used.add(e.getGridPos());
        }

        boolean spawnedAny = false;
        for (int i = 0; i < ADD_WAVE_SIZE; i++) {
            GridPos pos = MinibossSpawns.findOpenBiased(arena.getWidth(), arena.getHeight(), used, ctx.rng(),
                arena.getWidth() / 2, arena.getHeight() / 2);
            if (pos == null) continue;
            used.add(pos);
            ctx.spawnMob("minecraft:cave_spider", pos, 14, 4, 0, 1);
            spawnedAny = true;
        }
        if (spawnedAny) {
            ctx.message("§2More spiders skitter out of the brood!");
            ctx.playSound(SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 0.5f, 1.2f);
        }
    }
}
