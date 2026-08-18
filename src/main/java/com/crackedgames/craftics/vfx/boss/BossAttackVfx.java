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

    /**
     * The windup: what the boss does while the attack is charging, across the
     * player's turn.
     *
     * <p>This used to be one convergence onto the middle of the footprint plus a
     * couple of ambient puffs, which reads as "some particles are happening"
     * rather than "something is about to hit me". Three things fix that, and all
     * three are about telling the player something they can act on:
     *
     * <ul>
     *   <li><b>An opening beat.</b> The instant the warning appears there is a
     *       camera jolt, a screen tint in the boss's colour and a low hit, so the
     *       telegraph announces itself instead of fading in. You feel it start.</li>
     *   <li><b>Every doomed tile is marked, not just the centre.</b> Each warning
     *       tile gets its own flash and a column of the boss's particles rising
     *       out of it, so a wide footprint reads as a shape rather than a blob.</li>
     *   <li><b>The boss performs the attack it is about to make.</b> A slam
     *       throws rubble skyward, a charge sprays down the lane it will run, a
     *       summon breathes souls out of the ground. Windup and payoff are the
     *       same gesture, so the shape becomes learnable.</li>
     * </ul>
     *
     * <p>Intensity climbs across the three pulses: shake, pitch and particle
     * count all rise as the resolve approaches, so the last beat before impact is
     * the loudest one.
     */
    public static void telegraph(ServerWorld world, GridArena arena,
                                 CombatEntity boss, EnemyAction.BossAbility ba) {
        List<GridPos> tiles = ba.warningTiles();
        if (world == null || arena == null || tiles == null || tiles.isEmpty()) return;
        Theme theme = themeFor(boss);
        Category cat = categorize(ba.abilityName());
        GridPos center = centroid(tiles);
        GridPos bossPos = boss != null ? boss.getGridPos() : center;
        double radius = Math.min(4.5, maxDist(tiles, center) + 0.8);
        VfxContext ctx = contextFor(world, arena, boss, center);
        VfxAnchor epicenter = groundAnchor(arena, center);

        VfxDescriptor.Builder b = VfxDescriptor.builder();

        // Beat 1: it starts NOW.
        VfxDescriptor.PhaseBuilder p0 = b.phase(0)
            .shake(0.35f, 8)
            .screenFlash((theme.accent() & 0x00FFFFFF) | 0x33000000, 6)
            .sound(epicenter, SoundEvents.BLOCK_BELL_RESONATE, 0.9f, 0.55f)
            .converge(epicenter, radius, theme.primary(), 16)
            // The boss flares as the wind-up begins, so the eye goes to the
            // caster and not only to the floor.
            .ring(VfxAnchor.ORIGIN, 1.2, theme.primary(), 14)
            .directionalBurst(VfxAnchor.ORIGIN, theme.secondary(), 12, 0.35, 70, 0.5);
        if (cat == Category.SLAM || cat == Category.CHARGE) {
            p0.sound(epicenter, SoundEvents.ENTITY_RAVAGER_STEP, 0.8f, 0.5f);
        }

        // Per-tile marks: the footprint as a shape, not a blob. Capped for the
        // same reason sweepTiles caps - a lane-wide ability can cover dozens of
        // tiles and each one costs a phase.
        int marked = 0;
        for (GridPos t : tiles) {
            if (marked >= 20) break;
            VfxAnchor tileAnchor = new VfxAnchor.AtGridTile(t.x(), t.z(), 0.1);
            b.phase(2 + (marked % 4))
                .tileRingFlash(tileAnchor, 0, theme.accent(), 12)
                .directionalBurst(tileAnchor, theme.primary(), 4, 0.18, 20, 1.0);
            marked++;
        }

        // The boss rehearses the attack it is about to throw.
        addWindupGesture(b, arena, theme, cat, bossPos, center, tiles);

        // Beats 2 and 3: the noose closing, louder each time.
        b.phase(12)
            .shake(0.18f, 5)
            .converge(epicenter, radius * 0.66, theme.primary(), 14)
            .sound(epicenter, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 0.6f)
        .phase(24)
            .shake(0.5f, 10)
            .screenFlash((theme.accent() & 0x00FFFFFF) | 0x40000000, 8)
            .converge(epicenter, radius * 0.4, theme.primary(), 18)
            .particles(theme.secondary(), epicenter, 10, new Vec3d(0.3, 0.1, 0.3), 0.02)
            .sound(epicenter, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.7f, 0.95f);
        Vfx.play(world, b.build(), ctx);
    }

    /**
     * The category-specific half of the windup: the boss visibly preparing THIS
     * attack, so the telegraph teaches its shape and not just its footprint.
     *
     * <p>Deliberately anchored on the boss, or on the line between boss and
     * target, rather than on the warning tiles that the per-tile marks already
     * cover. The two layers answer different questions: the marks say WHERE, this
     * says WHAT, and for the attacks that travel, WHICH WAY.
     */
    private static void addWindupGesture(VfxDescriptor.Builder b, GridArena arena, Theme theme,
                                         Category cat, GridPos bossPos, GridPos center,
                                         List<GridPos> tiles) {
        VfxAnchor bossGround = groundAnchor(arena, bossPos);

        switch (cat) {
            case SLAM -> {
                // Rubble thrown skyward off the boss. The lifetimes are short on
                // purpose: every block is discarded mid-flight, so they leave the
                // screen going up and never come back down. That is the whole
                // read - something heavy is up there and it has to land somewhere.
                for (int i = 0; i < 5; i++) {
                    double driftX = (i - 2) * 0.09;
                    double driftZ = ((i % 2 == 0) ? 1 : -1) * 0.07;
                    b.phase(3 + i * 2)
                        .launchFloorBlock(bossGround, new Vec3d(driftX, 1.15 + i * 0.05, driftZ), 22);
                }
                b.phase(4)
                    .shake(0.3f, 10)
                    .sound(bossGround, SoundEvents.ENTITY_RAVAGER_ROAR, 0.7f, 0.6f);
                b.phase(20)
                    .directionalBurst(bossGround, theme.secondary(), 14, 0.5, 35, 1.0);
            }
            case CHARGE -> {
                // Particles rake down the lane the boss will run, so the player
                // reads the direction BEFORE the dash rather than during it.
                b.phase(3)
                    .trail(bossGround, groundAnchor(arena, center), theme.primary(), theme.secondary(), 22, 0.0)
                    .directionalBurst(bossGround, theme.primary(), 18, 0.75, 12, 0.05)
                    .sound(bossGround, SoundEvents.ENTITY_HORSE_BREATHE, 0.8f, 0.7f);
                // Hooves digging in, the way a sprinter loads before the start.
                b.phase(14)
                    .directionalBurst(bossGround, theme.secondary(), 12, 0.5, 25, 0.2)
                    .trail(bossGround, groundAnchor(arena, center), theme.primary(), theme.secondary(), 26, 0.0);
                b.phase(26)
                    .directionalBurst(bossGround, theme.primary(), 20, 0.9, 8, 0.05);
            }
            case LINE -> {
                // The strike travels, so the warning travels: tiles light nearest
                // first, which points the sweep before it happens.
                sweepTiles(b, arena, null, tiles, theme.accent(), 2);
                b.phase(6).trail(bossGround, groundAnchor(arena, center),
                    theme.primary(), theme.secondary(), 20, 0.0);
            }
            case SUMMON -> {
                // Something is coming UP, so everything rises: souls out of each
                // marked tile, and the ground cracking under the boss.
                int i = 0;
                for (GridPos t : tiles) {
                    if (i >= 12) break;
                    b.phase(5 + i * 2).directionalBurst(
                        new VfxAnchor.AtGridTile(t.x(), t.z(), 0.05), theme.primary(), 8, 0.3, 15, 1.0);
                    i++;
                }
                b.phase(8).sound(bossGround, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 0.9f, 0.8f);
            }
            case TERRAIN -> {
                // The floor itself is the subject, so shake it and knock dust off
                // it. Low hops with short lifetimes: the tiles twitch, they do not
                // become debris.
                int i = 0;
                for (GridPos t : tiles) {
                    if (i >= 12) break;
                    b.phase(4 + i)
                        .launchFloorBlock(new VfxAnchor.AtGridTile(t.x(), t.z(), 0.05),
                            new Vec3d(0, 0.35, 0), 12);
                    i++;
                }
                b.phase(6).shake(0.22f, 14)
                    .sound(bossGround, SoundEvents.BLOCK_ROOTED_DIRT_BREAK, 0.9f, 0.6f);
            }
            case PULL -> {
                // Reversed on purpose: everything streams tiles-to-boss, so the
                // windup shows which way the player is about to be dragged.
                int i = 0;
                for (GridPos t : tiles) {
                    if (i >= 10) break;
                    b.phase(4 + i * 2).trail(groundAnchor(arena, t), bossGround,
                        theme.primary(), theme.secondary(), 14, 0.45);
                    i++;
                }
                b.phase(10).ring(VfxAnchor.ORIGIN, 1.6, theme.secondary(), 16)
                    .sound(bossGround, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 1.2f);
            }
            case MAGIC -> {
                b.phase(4)
                    .ring(VfxAnchor.ORIGIN, 1.5, theme.primary(), 20)
                    .directionalBurst(VfxAnchor.ORIGIN, theme.primary(), 14, 0.25, 45, 0.9)
                    .sound(bossGround, SoundEvents.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.1f);
                b.phase(18)
                    .ring(VfxAnchor.ORIGIN, 2.1, theme.secondary(), 22)
                    .sound(bossGround, SoundEvents.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.35f);
            }
            case GENERIC -> b.phase(6)
                .directionalBurst(VfxAnchor.ORIGIN, theme.primary(), 10, 0.3, 40, 0.7);
        }
    }

    /** The payoff at resolve time: a category-shaped, boss-themed impact -
     *  slams ripple shockwaves, lines sweep tile-by-tile, magic bursts from a
     *  final convergence, summons breathe souls out of cracked earth. Fired both
     *  when a telegraph resolves and when an untelegraphed (tile-less) ability lands. */
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

    /**
     * A single mace-slam-style shockwave at one tile, themed to the boss and
     * delayed by {@code delayTicks}. This is the SLAM impact's shockwave
     * (SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY + a traveling ground ring, the
     * same params {@link #impact} uses for slam abilities), pulled out so a
     * per-target chain (the Tidecaller's Conduction) can ripple one shockwave at
     * each combatant it strikes, staggered so the impacts travel outward in time
     * with the chain arcs. Layers on top of any existing arc/thunder - it does not
     * replace them.
     */
    public static void shockwaveImpact(ServerWorld world, GridArena arena,
                                       CombatEntity boss, GridPos tile, int delayTicks) {
        if (world == null || arena == null || tile == null) return;
        Theme theme = themeFor(boss);
        VfxContext ctx = contextFor(world, arena, boss, tile);
        VfxAnchor epicenter = groundAnchor(arena, tile);
        Vfx.play(world, VfxDescriptor.builder()
            .phase(Math.max(0, delayTicks))
                .sound(epicenter, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 0.9f, 0.9f)
                .particles(ParticleTypes.EXPLOSION, epicenter, 1, Vec3d.ZERO, 0.0)
                .shockwave(epicenter, 2, 2, theme.primary(), theme.secondary(),
                           WAVE_DUST, 10, 0.35f, 10, SoundEvents.BLOCK_BASALT_BREAK)
                .shake(0.5f, 8)
            .build(), ctx);
    }

    /** How high above the floor a dropped ember starts its fall, in blocks. */
    private static final double EMBER_FALL_HEIGHT = 7.0;

    /** How many descending motes the fall is drawn with. Two ticks apart each. */
    private static final int EMBER_FALL_STEPS = 4;

    /**
     * A single burning ember falling out of the sky onto one tile and catching.
     *
     * <p>For attacks that light ONE tile and leave the rest to the fire itself. The whole
     * point of a seed-and-spread attack is that the thing which lands is small, so it needs
     * to be legible as a discrete object falling rather than as fire simply appearing - the
     * player should be able to see where the burn started and count outward from it.
     *
     * <p>Drawn as a streak from high above down to the tile, four motes descending along it
     * two ticks apart, then a landing burst and a ring. Fast on purpose (about half a
     * second): the tile is already alight when this starts, because the burn is game state
     * and resolves the instant the action does, so the fall has to read as the cause rather
     * than dawdle after the effect.
     *
     * @param delayTicks stagger between embers when several drop at once, so a phase-2
     *                   volley falls as a scatter instead of three identical columns
     */
    public static void fallingEmber(ServerWorld world, GridArena arena,
                                    CombatEntity boss, GridPos tile, int delayTicks) {
        if (world == null || arena == null || tile == null) return;
        VfxContext ctx = contextFor(world, arena, boss, tile);
        int base = Math.max(0, delayTicks);
        VfxAnchor high = new VfxAnchor.AtGridTile(tile.x(), tile.z(), EMBER_FALL_HEIGHT);
        VfxAnchor ground = new VfxAnchor.AtGridTile(tile.x(), tile.z(), 0.2);

        VfxDescriptor.Builder ember = VfxDescriptor.builder();
        // The streak: one line of soul flame from the sky to the tile, laid down flat (no
        // arc) so it reads as a drop rather than a lobbed shot.
        ember.phase(base)
            .sound(high, SoundEvents.ENTITY_GHAST_SHOOT, 0.4f, 1.7f)
            .trail(high, ground, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SMOKE, 14, 0.0);

        // The ember itself, stepping down the streak so the eye has something to follow.
        for (int step = 1; step <= EMBER_FALL_STEPS; step++) {
            double y = EMBER_FALL_HEIGHT * (1.0 - (double) step / EMBER_FALL_STEPS);
            VfxAnchor at = new VfxAnchor.AtGridTile(tile.x(), tile.z(), y);
            ember.phase(base + step * 2)
                .particles(ParticleTypes.SOUL_FIRE_FLAME, at, 6, new Vec3d(0.08, 0.12, 0.08), 0.01)
                .particles(ParticleTypes.ASH, at, 3, new Vec3d(0.12, 0.15, 0.12), 0.0);
        }

        // Landing: it hits, it flares, it catches.
        ember.phase(base + EMBER_FALL_STEPS * 2 + 1)
            .sound(ground, SoundEvents.ITEM_FIRECHARGE_USE, 0.7f, 1.4f)
            .particles(ParticleTypes.SOUL_FIRE_FLAME, ground, 20, new Vec3d(0.3, 0.15, 0.3), 0.05)
            .particles(ParticleTypes.SOUL, ground, 8, new Vec3d(0.25, 0.1, 0.25), 0.02)
            .ring(ground, 0.7, ParticleTypes.SOUL_FIRE_FLAME, 12);

        Vfx.play(world, ember.build(), ctx);
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
