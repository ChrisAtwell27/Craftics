package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.block.Blocks;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The plains biome's level-4 miniboss: the Graveyard encounter. Opens with 3 zombies and 3
 * living graves (20 HP cobblestone-wall block objects, the same block-backed occupant pattern
 * {@link com.crackedgames.craftics.combat.ai.boss.RevenantAI} uses, just placed directly by the
 * mechanic instead of by a boss AI). Every 3rd round, each grave still standing raises one more
 * zombie next to itself; destroying a grave permanently stops its stream.
 *
 * <p>Graves are placed via {@link MinibossContext#spawnBlockObject}, not
 * {@link MinibossContext#spawnMob} - they are block-backed combatants with no mob entity, and
 * {@code spawnMob} only knows how to spawn real mob entities.
 *
 * <p>Graves are {@code scenery} (see {@code CombatManager#placeBlockObject}): by design they do
 * NOT count toward the base "all enemies cleared" room-clear check, so a fight could otherwise
 * report victory while a grave still stands. {@link #isComplete} closes that gap explicitly by
 * scanning {@link MinibossContext#enemies()} for any living {@code craftics:grave} - the fight
 * only completes once every grave is down AND the field (zombies etc.) is clear.
 */
public final class PlainsGraveyardMechanic implements MinibossMechanic {

    private static final int GRAVE_HP = 20;
    private static final int GRAVE_COUNT = 3;
    private static final int ZOMBIE_COUNT = 3;
    private static final int ZOMBIE_CADENCE = 3; // raise every 3rd round

    /** Tiles the graves were placed on, recorded in onFightStart for the per-round raise check. */
    private final List<GridPos> graves = new ArrayList<>();

    @Override
    public String biomeId() {
        return "plains";
    }

    @Override
    public String introTitle() {
        return "§2§l☠ Graveyard";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        for (int i = 0; i < ZOMBIE_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:zombie", pos,
                12 + hpBonus, 3 + atkBonus, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        graves.clear();
        GridArena arena = ctx.arena();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(arena.getWidth() / 2, 0)); // player start - never spawn here
        // Avoid stacking a grave directly on an already-placed enemy tile.
        for (CombatEntity e : ctx.enemies()) {
            if (e.getGridPos() != null) used.add(e.getGridPos());
        }

        for (int i = 0; i < GRAVE_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(arena.getWidth(), arena.getHeight(), used, ctx.rng());
            if (pos == null) continue;
            used.add(pos);
            CombatEntity grave = ctx.spawnBlockObject("craftics:grave", pos, GRAVE_HP, Blocks.COBBLESTONE_WALL);
            if (grave != null) graves.add(pos);
        }
        ctx.banner(introTitle());
        // Low tolling bell to open the encounter - the graveyard's fight-start cue.
        ctx.playSound(SoundEvents.BLOCK_BELL_USE, 0.6f, 0.7f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % ZOMBIE_CADENCE != 0) return;

        int hpBonus = 0; // round-hook zombies mirror the opening wave's base stats
        boolean raisedAny = false;
        for (GridPos g : graves) {
            if (!graveAlive(ctx, g)) continue;
            GridPos spot = adjacentFree(ctx, g);
            if (spot != null) {
                ctx.spawnMob("minecraft:zombie", spot, 12 + hpBonus, 3, 0, 1);
                raisedAny = true;
            }
        }
        // One-shot zombie groan on the raise round, not spammed per-grave.
        if (raisedAny) ctx.playSound(SoundEvents.ENTITY_ZOMBIE_AMBIENT, 0.6f, 0.9f);
    }

    @Override
    public boolean isComplete(MinibossContext ctx) {
        // Graves are `scenery` (CombatManager#placeBlockObject) and deliberately do NOT count
        // toward the base "all enemies cleared" room-clear check - so this extra check is load
        // bearing, not redundant. Without it the fight could complete with a living grave still
        // standing (and still raising zombies) once the rest of the field was clear.
        for (CombatEntity e : ctx.enemies()) {
            if (e.isAlive() && "craftics:grave".equals(e.getEntityTypeId())) {
                return false;
            }
        }
        return true;
    }

    /** True if the given tile still hosts a living grave in the enemies list. */
    private static boolean graveAlive(MinibossContext ctx, GridPos tile) {
        for (CombatEntity e : ctx.enemies()) {
            if (e.isAlive() && "craftics:grave".equals(e.getEntityTypeId()) && tile.equals(e.getGridPos())) {
                return true;
            }
        }
        return false;
    }

    /** First free orthogonal neighbour of {@code tile}, or null if all four are blocked/OOB. */
    private static GridPos adjacentFree(MinibossContext ctx, GridPos tile) {
        GridArena arena = ctx.arena();
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            GridPos p = new GridPos(tile.x() + d[0], tile.z() + d[1]);
            if (arena.isInBounds(p) && !arena.isOccupied(p)
                    && arena.getTile(p) != null && arena.getTile(p).isWalkable()) {
                return p;
            }
        }
        return null;
    }
}
