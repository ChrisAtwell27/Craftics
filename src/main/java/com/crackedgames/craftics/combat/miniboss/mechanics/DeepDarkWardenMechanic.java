package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The deep dark biome's level-4 miniboss: the Warden Echo encounter. This is a literal miniboss
 * (an elite version of a real enemy) rather than an event mechanic - the arena opens with one
 * beefy warden (roughly double a normal add's HP/attack, matching the elite-scaling pattern used
 * by {@link JungleBroodmotherMechanic}) plus one silverfish for company. {@code minecraft:warden}
 * is the entity id already used for this biome elsewhere in the mod - see
 * {@code AIRegistry.register("minecraft:warden", new WardenAI())} and
 * {@code AIRegistry.registerBoss("boss:deep_dark", WardenAI::new)} - so it is both the "safe
 * vanilla id" and the id this codebase already treats as the deep dark's boss/warden-type combat
 * entity.
 *
 * <p>Every 3rd round, provided the arena isn't already crowded, the warden "shrieks": two
 * silverfish adds spawn on free tiles near it, mirroring the vanilla warden's sculk-shrieker/sonic
 * boom lore (sculk answering the shriek) via the achievable primitive this codebase actually has -
 * {@link MinibossContext#spawnMob}.
 *
 * <p>Design note: the intended "Blindness in radius each round" targets the player/party directly,
 * but {@link MinibossContext} only exposes {@link MinibossContext#enemies()} - there is no
 * primitive to reach the player from a mechanic. {@code CombatEntity.setBlindedTurns(int)} exists,
 * but that blinds an <em>enemy</em>, not the player, so using it here would be pointless (and not
 * what the design intends). Rather than invent a new player-facing status primitive out of scope
 * for this task, this mechanic drops the blindness aura entirely. The elite warden (a slow, heavy
 * hitter) plus periodic sculk-shriek reinforcements already reads as a complete miniboss encounter
 * without it; a future {@code blindParty(int turns)} context hook would let a later pass add the
 * aura back in properly.
 *
 * <p>No extra win objective - the elite warden is a normal enemy (no scenery/inert flags), so
 * clearing every enemy (warden included) completes the fight via the base "all enemies cleared"
 * check. {@link #isComplete} uses the interface default (always true).
 */
public final class DeepDarkWardenMechanic implements MinibossMechanic {

    private static final int SHRIEK_CADENCE = 3; // sculk shriek wave every 3rd round
    private static final int SHRIEK_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip the shriek wave if this many enemies are already alive

    @Override
    public String biomeId() {
        return "deep_dark";
    }

    @Override
    public String introTitle() {
        return "§3§l☠ Warden Echo";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        // Elite warden, placed near arena center - a beefy, slow, heavy hitter.
        GridPos elitePos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (elitePos == null) elitePos = new GridPos(width / 2, height / 2);
        used.add(elitePos);
        spawns.add(new LevelDefinition.EnemySpawn("minecraft:warden", elitePos,
            70 + hpBonus * 2, 9 + atkBonus, 4, 1));

        // One silverfish for company at the opening.
        GridPos addPos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (addPos != null) {
            used.add(addPos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:silverfish", addPos,
                8 + hpBonus, 3 + atkBonus, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % SHRIEK_CADENCE != 0) return;

        int liveCount = 0;
        for (var e : ctx.enemies()) {
            if (e.isAlive()) liveCount++;
        }
        if (liveCount >= CROWD_CAP) return; // arena too crowded, skip this shriek

        var arena = ctx.arena();
        List<GridPos> used = new ArrayList<>();
        for (var e : ctx.enemies()) {
            if (e.isAlive() && e.getGridPos() != null) used.add(e.getGridPos());
        }

        boolean spawnedAny = false;
        for (int i = 0; i < SHRIEK_WAVE_SIZE; i++) {
            GridPos pos = MinibossSpawns.findOpenBiased(arena.getWidth(), arena.getHeight(), used, ctx.rng(),
                arena.getWidth() / 2, arena.getHeight() / 2);
            if (pos == null) continue;
            used.add(pos);
            ctx.spawnMob("minecraft:silverfish", pos, 8, 3, 0, 1);
            spawnedAny = true;
        }
        if (spawnedAny) ctx.message("§3The Warden shrieks — the sculk answers!");
    }
}
