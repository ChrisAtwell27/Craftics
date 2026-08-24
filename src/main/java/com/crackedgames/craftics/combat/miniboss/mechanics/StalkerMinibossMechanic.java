package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deeper-and-Darker deep_dark level-4 encounter: Full Darkness.
 *
 * <p>Registered ONLY when Deeper and Darker is installed (see
 * {@code DeeperAndDarkerCompat}); otherwise the vanilla {@link DeepDarkWaveMechanic} stays
 * the deep_dark miniboss.
 *
 * <p><b>Why there is no Stalker here.</b> This used to be a literal miniboss fight against the
 * mod's Stalker, and it could not be made to behave. Its behaviour lives in its own entity
 * code rather than in vanilla goals, so the {@code NoAI} flag every arena mob is frozen with
 * did not hold it: it went invisible, attacked in real time between turns, and summoned
 * leeches the grid had never placed. A mob that ignores the turn system cannot be a fair
 * fight in a turn-based game, so the encounter is built out of the biome instead.
 *
 * <p>What is left is a pressure fight in the dark: the sculk keeps the party blind a couple of
 * turns at a time, five sensors make the floor itself hostile to cross, and the deep dark's
 * ordinary residents do the fighting. It clears the way every other room does, by killing
 * what is in it.
 */
public final class StalkerMinibossMechanic implements MinibossMechanic {

    /** Sensors ringing the arena. Destructible: a pickaxe is a real answer to them. */
    private static final int SENSORS = 5;
    /** Turns of Darkness applied at the top of every round. */
    private static final int DARKNESS_TURNS = 2;

    /**
     * The deep dark's own residents, all ordinary grid mobs with ordinary AI.
     *
     * <p>No Warden. It is the biome's boss and belongs to its own fight, not to a level-4
     * room. No Stalker either, for the reason in the class note above.
     */
    private static final String[] CHAFF = {
        "deeperdarker:sculk_leech",
        "deeperdarker:sculk_centipede",
        "deeperdarker:shriek_worm",
    };
    /** The one heavier body in the room, to give the fight a shape. */
    private static final String HEAVY = "deeperdarker:shattered";

    @Override
    public String biomeId() {
        return "deep_dark";
    }

    @Override
    public String introTitle() {
        return "§3§l☠ Full Darkness";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        // A room of the biome's own mobs rather than one elite. Numbers scale with the
        // arena so a bigger deep dark is a fuller one.
        int count = Math.max(4, Math.min(7, (width * height) / 20));
        for (int i = 0; i < count; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) break;
            used.add(pos);
            // The last slot is the heavy one; the rest are chaff that keeps the party moving.
            boolean heavy = i == count - 1;
            String id = heavy ? HEAVY : CHAFF[rng.nextInt(CHAFF.length)];
            spawns.add(heavy
                ? new LevelDefinition.EnemySpawn(id, pos, 45 + hpBonus * 2, 8 + atkBonus, 3, 1)
                : new LevelDefinition.EnemySpawn(id, pos, 12 + hpBonus, 4 + atkBonus, 1, 1));
        }
        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.ENTITY_WARDEN_EMERGE, 0.6f, 1.1f);
        ctx.message("§3The dark closes in. Sensors line the floor - tread carefully or mine them out.");

        // Five sensors scattered across the floor as breakable obstacles, the same shape the
        // biome's own sensor hazard uses. Placed as blocks, not entities, so they never count
        // toward clearing the room and never take a turn.
        List<GridPos> used = new ArrayList<>();
        for (var e : ctx.enemies()) {
            if (e.isAlive() && e.getGridPos() != null) used.add(e.getGridPos());
        }
        var arena = ctx.arena();
        for (int i = 0; i < SENSORS; i++) {
            GridPos pos = MinibossSpawns.findOpen(arena.getWidth(), arena.getHeight(), used,
                ctx.rng(), arena::isPlaceableFloor); // a sensor is a block: no pits
            if (pos == null) break;
            used.add(pos);
            ctx.placeObstacle(pos, sensorBlock());
            ctx.spawnHazardBurst(ParticleTypes.SCULK_SOUL, pos);
        }
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        // The whole encounter in one line: you fight this room blind. Re-applied every round
        // rather than stacked, so it is a constant condition to play around and not a timer
        // that runs away from the party.
        ctx.applyPartyEffect(CombatEffects.EffectType.DARKNESS, DARKNESS_TURNS);
        if (ctx.round() % 2 == 0) {
            ctx.playSound(SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.5f, 1.2f);
        }
    }

    /**
     * The sensor block. Vanilla's, deliberately.
     *
     * <p>Not Deeper and Darker's sculk jaw: that block runs its own logic every tick inside
     * the arena, biting whoever stands on it in real time on nobody's turn, which is why jaws
     * were taken out of generation in the first place. A vanilla sculk sensor is inert - it is
     * scenery and a mining target, and the hazard here is the darkness, not the floor.
     */
    private static net.minecraft.block.Block sensorBlock() {
        return Blocks.SCULK_SENSOR;
    }
}
