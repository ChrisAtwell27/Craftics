package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The warped forest biome's level-4 miniboss: the Warped Enderman. Opens with one beefy "elite"
 * enderman (roughly double a normal add's HP, so it reads as the miniboss) plus an optional single
 * endermite for company. Every 3rd round two more endermites spill in near the elite, unless the
 * arena is already crowded.
 *
 * <p>Design note: the brief's intended flavor mechanic is the elite teleporting ("blinking") to a
 * random tile each round. That needs a primitive to relocate an already-live {@link
 * com.crackedgames.craftics.combat.CombatEntity} to a new {@link GridPos} - {@link
 * MinibossContext} can spawn, kill, and place tiles/block-objects, but has no
 * {@code moveEntity(entity, tile)} (or equivalent) hook to reposition an existing combatant.
 * Inventing that primitive is out of scope for this task, so the blink is dropped entirely rather
 * than faked. A future {@code ctx.moveEntity(CombatEntity, GridPos)} primitive on
 * {@code MinibossContext} (backed by a CombatManager relocation helper) would let a later revision
 * add the teleport back in. This mechanic implements the achievable core of the encounter (elite +
 * endermite swarm waves), which is a complete miniboss on its own: the elite's doubled HP carries
 * the "miniboss" weight and the recurring endermite swarm keeps the arena pressured.
 *
 * <p>No extra win objective - the elite is a normal enemy (no scenery/inert flags), so clearing
 * every mob (elite included) completes the fight via the base "all enemies cleared" check.
 * {@link #isComplete} uses the interface default (always true).
 */
public final class WarpedEndermanMechanic implements MinibossMechanic {

    private static final int SWARM_CADENCE = 3; // endermite swarm every 3rd round
    private static final int SWARM_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip the swarm if this many enemies are already alive

    @Override
    public String biomeId() {
        return "warped_forest";
    }

    @Override
    public String introTitle() {
        return "§3§l☠ Warped Enderman";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        // Elite warped enderman, placed near arena center - roughly double a normal add's HP.
        GridPos elitePos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (elitePos == null) elitePos = new GridPos(width / 2, height / 2);
        used.add(elitePos);
        spawns.add(new LevelDefinition.EnemySpawn("minecraft:enderman", elitePos,
            40 + hpBonus * 2, 8 + atkBonus, 1, 1));

        // One starting endermite for company.
        GridPos addPos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (addPos != null) {
            used.add(addPos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:endermite", addPos,
                6, 3, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.ENTITY_ENDERMAN_STARE, 0.6f, 0.8f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % SWARM_CADENCE != 0) return;

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
        for (int i = 0; i < SWARM_WAVE_SIZE; i++) {
            GridPos pos = MinibossSpawns.findOpenBiased(arena.getWidth(), arena.getHeight(), used, ctx.rng(),
                arena.getWidth() / 2, arena.getHeight() / 2);
            if (pos == null) continue;
            used.add(pos);
            ctx.spawnMob("minecraft:endermite", pos, 6, 3, 0, 1);
            ctx.spawnHazardBurst(ParticleTypes.PORTAL, pos);
            spawnedAny = true;
        }
        if (spawnedAny) {
            ctx.message("§3The warp tears - endermites spill out!");
            ctx.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.1f);
        }
    }
}
