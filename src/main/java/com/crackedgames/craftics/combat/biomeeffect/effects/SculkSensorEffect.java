package com.crackedgames.craftics.combat.biomeeffect.effects;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.SculkRange;
import com.crackedgames.craftics.combat.biomeeffect.BiomeEffect;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The deep-dark biome's persistent hazard layer: 1-3 destructible sculk sensors scattered
 * across the arena, each painting a Chebyshev-2 sculk boundary around itself. Any participant
 * (not enemies) who steps within that boundary without Swift Sneak boots triggers the sensor:
 * the whole party is blinded by Darkness, a shrieker wails, and a warned silverfish ambush
 * lands next round near the sensor. A triggered sensor re-arms after {@link #REARM_ROUNDS}
 * rounds of quiet.
 *
 * <p>Sensors are placed via {@link MinibossContext#spawnBlockObject} - the graves pattern
 * ({@link com.crackedgames.craftics.combat.miniboss.mechanics.PlainsGraveyardMechanic}) - a
 * block-backed, scenery/inert occupant, not a real mob entity. They are tracked here as plain
 * {@link Sensor} records keyed by tile, and dropped from tracking once no longer found alive
 * among {@link MinibossContext#enemies()} (i.e. once a player breaks one).
 */
public final class SculkSensorEffect implements BiomeEffect {

    private static final String SENSOR_ID = "craftics:sculk_sensor";
    private static final int SENSOR_HP = 10;
    private static final int RANGE = 2;              // Chebyshev trigger + boundary radius
    private static final int REARM_ROUNDS = 2;        // re-arm every other round after firing
    private static final int SILVERFISH_PER_TRIGGER = 2;
    private static final int MIN_SENSORS = 1;
    private static final int MAX_SENSORS = 3;         // inclusive

    @Override
    public String id() {
        return "sculk_sensors";
    }

    /** One live sensor: its tile and remaining re-arm cooldown (0 = armed and can trigger). */
    private static final class Sensor {
        final GridPos tile;
        int cooldown = 0;
        Sensor(GridPos t) { this.tile = t; }
    }

    private final List<Sensor> sensors = new ArrayList<>();
    /** Tiles warned last round, to spawn silverfish onto this round (telegraph -> land). */
    private final List<GridPos> pendingSpawns = new ArrayList<>();

    @Override
    public void onFightStart(MinibossContext ctx) {
        sensors.clear();
        pendingSpawns.clear();

        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        Random rng = ctx.rng();

        int count = MIN_SENSORS + rng.nextInt(MAX_SENSORS - MIN_SENSORS + 1);
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never place here

        for (int i = 0; i < count; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            CombatEntity sensor = ctx.spawnBlockObject(SENSOR_ID, pos, SENSOR_HP, Blocks.SCULK_SENSOR);
            if (sensor == null) continue;
            sensors.add(new Sensor(pos));
            paintBoundary(ctx, pos);
        }

        ctx.message("§3The sculk stirs - sensors line the dark. Tread carefully.");
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        // 1. Resolve last round's warned spawns: the silverfish ambush lands now.
        if (!pendingSpawns.isEmpty()) {
            for (GridPos t : pendingSpawns) {
                ctx.spawnMob("minecraft:silverfish", t, 8, 3, 0, 1);
            }
            pendingSpawns.clear();
        }

        // 2. Tick re-arm cooldowns.
        for (Sensor s : sensors) {
            if (s.cooldown > 0) s.cooldown--;
        }

        // 3. Drop sensors that are no longer alive on the field (player broke them).
        sensors.removeIf(s -> ctx.enemies().stream().noneMatch(e ->
            e.isAlive() && SENSOR_ID.equals(e.getEntityTypeId()) && s.tile.equals(e.getGridPos())));

        // 4. Check each armed sensor for a triggering (non-swift-sneak) participant in range.
        for (Sensor s : sensors) {
            if (s.cooldown > 0) continue;

            boolean triggered = false;
            for (ServerPlayerEntity p : ctx.participants()) {
                if (p == null || ctx.hasSwiftSneak(p)) continue;
                GridPos pTile = ctx.tileOf(p);
                if (pTile != null && SculkRange.within(pTile, s.tile, RANGE)) {
                    triggered = true;
                    break;
                }
            }
            if (!triggered) continue;

            // Trigger: darkness to the whole party, a shriek, a telegraphed silverfish ambush,
            // then disarm for REARM_ROUNDS.
            ctx.applyPartyEffect(CombatEffects.EffectType.DARKNESS, 1);
            ctx.playSound(SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.9f, 1.0f);
            ctx.message("§3A sculk shrieker wails - the dark closes in!");

            List<GridPos> spots = freeTilesNear(ctx, s.tile, SILVERFISH_PER_TRIGGER);
            pendingSpawns.addAll(spots);
            ctx.warnTiles(spots);
            s.cooldown = REARM_ROUNDS;
        }
    }

    /** Paints the Chebyshev-{@link #RANGE} boundary square around {@code center} with a
     *  99-round (effectively permanent for the fight) SCULK tile overlay, skipping any tile
     *  that falls outside the arena. */
    private static void paintBoundary(MinibossContext ctx, GridPos center) {
        GridArena arena = ctx.arena();
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dz = -RANGE; dz <= RANGE; dz++) {
                GridPos t = new GridPos(center.x() + dx, center.z() + dz);
                if (!arena.isInBounds(t)) continue;
                ctx.placeTemporaryTile(t, TileType.SCULK, 99);
            }
        }
    }

    /** Up to {@code count} walkable, unoccupied, in-bounds tiles near {@code center} for the
     *  silverfish ambush to land on next round - scans the same Chebyshev-{@link #RANGE} ring
     *  as the boundary, closest tiles first, skipping the sensor's own tile. */
    private static List<GridPos> freeTilesNear(MinibossContext ctx, GridPos center, int count) {
        GridArena arena = ctx.arena();
        List<GridPos> found = new ArrayList<>();
        for (int radius = 1; radius <= RANGE && found.size() < count; radius++) {
            for (int dx = -radius; dx <= radius && found.size() < count; dx++) {
                for (int dz = -radius; dz <= radius && found.size() < count; dz++) {
                    // Only the outer ring of this radius, so we scan closest-first overall.
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    GridPos t = new GridPos(center.x() + dx, center.z() + dz);
                    if (found.contains(t)) continue;
                    if (!arena.isInBounds(t)) continue;
                    GridTile tile = arena.getTile(t);
                    if (tile == null || !tile.isWalkable()) continue;
                    if (arena.isOccupied(t)) continue;
                    found.add(t);
                }
            }
        }
        return found;
    }

    @Override
    public String toString() {
        return id();
    }
}
