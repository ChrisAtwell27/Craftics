package com.crackedgames.craftics.vfx.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.vfx.Vfx;
import com.crackedgames.craftics.vfx.VfxAnchor;
import com.crackedgames.craftics.vfx.VfxContext;
import com.crackedgames.craftics.vfx.VfxDescriptor;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Locale;

/**
 * The presentation layer for boss attacks: every telegraphed ability gets a
 * themed, categorized treatment instead of a flat particle sprinkle.
 *
 * <p>Two axes combine so each of the ~70 boss attacks reads distinctly:
 * <ul>
 *   <li><b>Theme</b> - derived from the boss's entity type (a wither telegraphs
 *       in souls and smoke, a drowned in splashes, an enderman in portal motes),
 *       so every boss has a consistent visual voice.</li>
 *   <li><b>Category</b> - inferred from the ability name (slam / line / charge /
 *       magic / summon / terrain / pull), which decides the SHAPE of the effect:
 *       slams detonate a traveling shockwave, lines sweep a flash down their
 *       tiles, magic converges then bursts, summons breathe souls out of the
 *       ground.</li>
 * </ul>
 *
 * <p>Three hooks, all called by CombatManager:
 * {@link #telegraph} when a warning is placed (dread builds over the player's
 * turn), {@link #impact} when it resolves (the payoff, whether or not the
 * telegraph was shown), and {@link #phaseTransition} at the Phase 2 flip.
 */
public final class BossAttackVfx {
    private BossAttackVfx() {}

    /** Attack shape archetype, inferred from the ability name. */
    private enum Category { SLAM, LINE, CHARGE, MAGIC, SUMMON, TERRAIN, PULL, GENERIC }

    /** A boss's visual voice: particles + accent color for flashes. */
    private record Theme(ParticleEffect primary, ParticleEffect secondary, int accent) {}

    private static final int WAVE_DUST = 0xD8CDB8;

    // ── public hooks ─────────────────────────────────────────────────────────

    /** Dread at telegraph time: a low resonant toll, the boss's aura flaring,
     *  and particles converging on the doomed tiles in shrinking pulses across
     *  the player's turn - the attack visibly charging, not a silent red tile. */
    public static void telegraph(ServerWorld world, GridArena arena,
                                 CombatEntity boss, EnemyAction.BossAbility ba) {
        List<GridPos> tiles = ba.warningTiles();
        if (world == null || arena == null || tiles == null || tiles.isEmpty()) return;
        Theme theme = themeFor(boss);
        Category cat = categorize(ba.abilityName());
        GridPos center = centroid(tiles);
        double radius = Math.min(4.5, maxDist(tiles, center) + 0.8);
        VfxContext ctx = contextFor(world, arena, boss, center);
        VfxAnchor epicenter = groundAnchor(arena, center);

        VfxDescriptor.Builder b = VfxDescriptor.builder();
        VfxDescriptor.PhaseBuilder p0 = b.phase(0)
            .sound(epicenter, SoundEvents.BLOCK_BELL_RESONATE, 0.9f, 0.55f)
            .converge(epicenter, radius, theme.primary(), 16)
            .particles(theme.secondary(), VfxAnchor.ORIGIN, 10, new Vec3d(0.5, 0.8, 0.5), 0.05);
        if (cat == Category.SLAM || cat == Category.CHARGE) {
            // Heavy attacks rumble while they charge.
            p0.sound(epicenter, SoundEvents.ENTITY_RAVAGER_STEP, 0.8f, 0.5f);
        }
        // Two follow-up pulses with tightening convergence: the noose closing.
        b.phase(12)
            .converge(epicenter, radius * 0.66, theme.primary(), 12)
            .sound(epicenter, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 0.6f)
        .phase(24)
            .converge(epicenter, radius * 0.4, theme.primary(), 10)
            .particles(theme.secondary(), epicenter, 6, new Vec3d(0.3, 0.1, 0.3), 0.02)
            .sound(epicenter, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.55f, 0.75f);
        Vfx.play(world, b.build(), ctx);
    }

    /** The payoff at resolve time: a category-shaped, boss-themed impact -
     *  slams ripple shockwaves, lines sweep tile-by-tile, magic bursts from a
     *  final convergence, summons breathe souls out of cracked earth. Fired on
     *  BOTH the telegraphed and the skip-telegraph (late-biome) paths. */
    public static void impact(ServerWorld world, GridArena arena,
                              CombatEntity boss, EnemyAction.BossAbility ba,
                              GridPos playerPos) {
        List<GridPos> tiles = ba.warningTiles();
        Theme theme = themeFor(boss);
        Category cat = categorize(ba.abilityName());
        // Tile-less abilities (self-buffs, instant melee) still get a themed accent.
        if (world == null || arena == null || tiles == null || tiles.isEmpty()) {
            if (world == null || arena == null || boss == null) return;
            VfxContext selfCtx = contextFor(world, arena, boss, boss.getGridPos());
            Vfx.play(world, VfxDescriptor.builder().phase(0)
                .particles(theme.primary(), VfxAnchor.ORIGIN, 12, new Vec3d(0.5, 0.7, 0.5), 0.08)
                .sound(VfxAnchor.ORIGIN, SoundEvents.ENTITY_EVOKER_CAST_SPELL, 0.7f, 0.9f)
                .build(), selfCtx);
            return;
        }
        GridPos center = centroid(tiles);
        int radiusTiles = Math.max(1, Math.min(3, (int) Math.ceil(maxDist(tiles, center))));
        VfxContext ctx = contextFor(world, arena, boss, center);
        VfxAnchor epicenter = groundAnchor(arena, center);
        boolean playerInside = playerPos != null && tiles.contains(playerPos);

        VfxDescriptor.Builder b = VfxDescriptor.builder();
        VfxDescriptor.PhaseBuilder p = b.phase(0);
        switch (cat) {
            case SLAM -> {
                p.sound(epicenter, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.8f)
                 .particles(ParticleTypes.EXPLOSION, epicenter, 1, Vec3d.ZERO, 0.0)
                 .directionalBurst(epicenter, theme.primary(), 12, 0.45, 100, 0.3)
                 .shockwave(epicenter, radiusTiles, 2, theme.primary(), theme.secondary(),
                            WAVE_DUST, 10, 0.4f, 10, SoundEvents.BLOCK_BASALT_BREAK)
                 .shake(0.9f, 10)
                 .hitPause(3);
            }
            case LINE, CHARGE -> {
                p.sound(epicenter, cat == Category.CHARGE
                        ? SoundEvents.ENTITY_RAVAGER_ROAR : SoundEvents.ENTITY_BLAZE_SHOOT,
                        0.9f, cat == Category.CHARGE ? 1.0f : 0.8f)
                 .directionalBurst(epicenter, theme.primary(), 14, 0.5, 25, 0.12)
                 .shake(cat == Category.CHARGE ? 0.7f : 0.5f, 7);
                // The strike sweeps down its tiles: nearest-to-boss first, one
                // tile flash per tick, so the player SEES the direction of travel.
                sweepTiles(b, arena, boss, tiles, theme.accent(), 1);
            }
            case MAGIC -> {
                p.sound(epicenter, SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.8f)
                 .converge(epicenter, radiusTiles + 0.8, theme.primary(), 14);
                b.phase(3)
                    .ring(epicenter, Math.max(0.8, radiusTiles * 0.8), theme.primary(), 18)
                    .particles(theme.secondary(), epicenter, 14,
                        new Vec3d(radiusTiles * 0.5, 0.6, radiusTiles * 0.5), 0.08)
                    .screenFlash(0x2C000000 | (theme.accent() & 0xFFFFFF), 5)
                    .shake(0.55f, 7);
            }
            case SUMMON -> {
                p.sound(epicenter, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 0.9f)
                 .shake(0.35f, 5);
                // Souls breathe out of every spawn tile as the earth cracks.
                for (GridPos t : tiles) {
                    p.particles(ParticleTypes.SOUL, groundAnchor(arena, t), 8,
                          new Vec3d(0.2, 0.5, 0.2), 0.04)
                     .particles(theme.secondary(), groundAnchor(arena, t), 5,
                          new Vec3d(0.25, 0.1, 0.25), 0.02);
                }
                b.phase(4).sound(epicenter, SoundEvents.ENTITY_VEX_CHARGE, 0.6f, 0.7f);
            }
            case TERRAIN -> {
                p.sound(epicenter, SoundEvents.BLOCK_STONE_PLACE, 1.0f, 0.7f)
                 .shake(0.45f, 6);
                sweepTiles(b, arena, boss, tiles, theme.accent(), 2);
            }
            case PULL -> {
                p.sound(epicenter, SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.6f)
                 .directionalBurst(epicenter, theme.primary(), 12, 0.5, 18, 0.1)
                 .shake(0.4f, 6);
            }
            case GENERIC -> {
                p.sound(epicenter, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.8f, 1.1f)
                 .particles(theme.primary(), epicenter, 12,
                     new Vec3d(radiusTiles * 0.5, 0.5, radiusTiles * 0.5), 0.08)
                 .ring(epicenter, Math.max(0.8, radiusTiles * 0.7), theme.secondary(), 12)
                 .shake(0.5f, 6);
            }
        }
        // When YOU are standing in it, the hit owns the screen for a beat.
        if (playerInside) {
            b.phase(0).screenFlash(0x48FF2A2A, 7).hitPause(2);
        }
        Vfx.play(world, b.build(), ctx);
    }

    /** Phase 2 flip: the arena itself flinches - dragon growl, a shockwave
     *  rolling out from the boss that bounces its own minions, a blood flash,
     *  and ENRAGED floating over its head. */
    public static void phaseTransition(ServerWorld world, GridArena arena, CombatEntity boss) {
        if (world == null || arena == null || boss == null) return;
        Theme theme = themeFor(boss);
        VfxContext ctx = contextFor(world, arena, boss, boss.getGridPos());
        VfxAnchor epicenter = groundAnchor(arena, boss.getGridPos());
        Vfx.play(world, VfxDescriptor.builder()
            .phase(0)
                .sound(VfxAnchor.ORIGIN, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.8f)
                .particles(theme.primary(), VfxAnchor.ORIGIN, 24, new Vec3d(0.7, 1.0, 0.7), 0.1)
                .shockwave(epicenter, 3, 2, theme.primary(), theme.secondary(),
                           WAVE_DUST, 10, 0.3f, 10, SoundEvents.BLOCK_BASALT_BREAK)
                .screenFlash(0x60CC1111, 8)
                .shake(1.0f, 12)
                .hitPause(4)
                .floatingText(VfxAnchor.ORIGIN, "ENRAGED", 0xFFFF2222, 40)
            .build(), ctx);
    }

    /** One-line player-facing hint for what a telegraphed ability is about to
     *  do, derived from its category - "prepares fire_pillar!" tells you the
     *  name; this tells you how to survive it. */
    public static String hintFor(String abilityName) {
        return switch (categorize(abilityName)) {
            case SLAM    -> "a heavy blow will crush the marked tiles - get clear!";
            case LINE    -> "a strike will sweep along the marked line!";
            case CHARGE  -> "it will charge along the arrows - step out of the lane!";
            case MAGIC   -> "a spell will detonate on the marked tiles!";
            case SUMMON  -> "reinforcements will rise from the marked ground!";
            case TERRAIN -> "the marked ground is about to change!";
            case PULL    -> "the arrows show which way you'll be dragged - brace or reposition!";
            case GENERIC -> "the marked tiles will be struck!";
        };
    }

    // ── internals ────────────────────────────────────────────────────────────

    /** One tile flash per {@code ticksPerTile}, nearest-to-boss first, so line
     *  and terrain attacks visibly travel instead of appearing all at once. */
    private static void sweepTiles(VfxDescriptor.Builder b, GridArena arena, CombatEntity boss,
                                   List<GridPos> tiles, int accent, int ticksPerTile) {
        GridPos from = boss != null ? boss.getGridPos() : tiles.get(0);
        List<GridPos> ordered = tiles.stream()
            .sorted(java.util.Comparator.comparingDouble(t ->
                Math.hypot(t.x() - from.x(), t.z() - from.z())))
            .toList();
        int i = 0;
        for (GridPos t : ordered) {
            if (i > 24) break; // cap phases on huge footprints
            b.phase(i * ticksPerTile)
                .tileRingFlash(new VfxAnchor.AtGridTile(t.x(), t.z(), 0.1), 0, accent, 10);
            i++;
        }
    }

    private static Category categorize(String abilityName) {
        String n = abilityName == null ? "" : abilityName.toLowerCase(Locale.ROOT);
        if (containsAny(n, "slam", "eruption", "stomp", "quake", "cave_in", "burial",
                "blizzard", "whiteout", "pound", "explosion", "bomb", "boulder")) return Category.SLAM;
        if (containsAny(n, "charge", "dive", "pounce", "assault", "riptide")) return Category.CHARGE;
        if (containsAny(n, "beam", "line", "pillar", "fang", "avalanche", "fury",
                "slash", "brand", "barrage", "storm", "wave")) return Category.LINE;
        if (containsAny(n, "summon", "spawn", "swarm", "brood", "raise", "reinforcement",
                "deploy", "mirror", "call")) return Category.SUMMON;
        if (containsAny(n, "wall", "terrain", "rift", "collapse", "bloom", "cage",
                "grid", "rows", "trap", "mine", "deluge")) return Category.TERRAIN;
        if (containsAny(n, "pull", "gale", "snare", "harpoon", "hook")) return Category.PULL;
        if (containsAny(n, "hex", "curse", "pulse", "roar", "burst", "cascade", "fog",
                "sonic", "darkness", "lights_out", "resonance", "entangle", "chain")) return Category.MAGIC;
        return Category.GENERIC;
    }

    private static boolean containsAny(String s, String... keys) {
        for (String k : keys) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    /** Theme by the boss's entity type - each boss keeps one visual voice. */
    private static Theme themeFor(CombatEntity boss) {
        String type = boss != null && boss.getEntityTypeId() != null
            ? boss.getEntityTypeId() : "";
        if (type.contains("wither_skeleton")) return new Theme(ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.ASH, 0xFF7A5CFF);
        if (type.contains("wither"))       return new Theme(ParticleTypes.SOUL, ParticleTypes.LARGE_SMOKE, 0xFF54407A);
        if (type.contains("stray"))        return new Theme(ParticleTypes.SNOWFLAKE, ParticleTypes.END_ROD, 0xFF8FD4FF);
        if (type.contains("ghast"))        return new Theme(ParticleTypes.FLAME, ParticleTypes.SMOKE, 0xFFFF7733);
        if (type.contains("drowned"))      return new Theme(ParticleTypes.SPLASH, ParticleTypes.BUBBLE, 0xFF3FA9E0);
        if (type.contains("magma"))        return new Theme(ParticleTypes.LAVA, ParticleTypes.FLAME, 0xFFFF6A00);
        if (type.contains("enderman") || type.contains("endermite") || type.contains("shulker"))
                                           return new Theme(ParticleTypes.PORTAL, ParticleTypes.REVERSE_PORTAL, 0xFFB24BF3);
        if (type.contains("evoker"))       return new Theme(ParticleTypes.WITCH, ParticleTypes.ENCHANT, 0xFFC96BD6);
        if (type.contains("vindicator"))   return new Theme(ParticleTypes.CRIT, ParticleTypes.CLOUD, 0xFFB0A489);
        if (type.contains("husk"))         return new Theme(ParticleTypes.ASH, ParticleTypes.CLOUD, 0xFFD9B36C);
        if (type.contains("warden"))       return new Theme(ParticleTypes.SCULK_SOUL, ParticleTypes.SCULK_CHARGE_POP, 0xFF29DFEB);
        if (type.contains("spider"))       return new Theme(ParticleTypes.COMPOSTER, ParticleTypes.CRIT, 0xFF77CC44);
        if (type.contains("zombie"))       return new Theme(ParticleTypes.SMOKE, ParticleTypes.FLAME, 0xFF7B9E4D);
        return new Theme(ParticleTypes.ENCHANT, ParticleTypes.CRIT, 0xFFE8B637);
    }

    private static VfxContext contextFor(ServerWorld world, GridArena arena,
                                         CombatEntity boss, GridPos center) {
        int bossId = boss != null && boss.getMobEntity() != null ? boss.getMobEntity().getId() : -1;
        BlockPos bossBlock = boss != null ? arena.gridToBlockPos(boss.getGridPos())
                                          : arena.gridToBlockPos(center);
        return VfxContext.ofEntities(bossId, -1, bossBlock,
            arena.gridToBlockPos(center), 0f, false, arena);
    }

    /** Anchor pinned just above the arena floor at a grid tile's center. */
    private static VfxAnchor groundAnchor(GridArena arena, GridPos tile) {
        BlockPos bp = arena.gridToBlockPos(tile);
        return new VfxAnchor.AtPos(new Vec3d(bp.getX() + 0.5, arena.getOrigin().getY() + 1.15, bp.getZ() + 0.5));
    }

    private static GridPos centroid(List<GridPos> tiles) {
        long sx = 0, sz = 0;
        for (GridPos t : tiles) { sx += t.x(); sz += t.z(); }
        return new GridPos(Math.round(sx / (float) tiles.size()),
                           Math.round(sz / (float) tiles.size()));
    }

    private static double maxDist(List<GridPos> tiles, GridPos center) {
        double max = 0;
        for (GridPos t : tiles) {
            max = Math.max(max, Math.hypot(t.x() - center.x(), t.z() - center.z()));
        }
        return max;
    }
}
