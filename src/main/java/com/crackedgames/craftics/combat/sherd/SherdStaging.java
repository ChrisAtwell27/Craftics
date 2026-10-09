package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.core.GridPos;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.enums.Thickness;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The staged sequences sherds play: what the arena itself does when one is cast.
 *
 * <p>Each method here is one spell's {@link SpellVisuals.Choreography}, attached to it in
 * {@link SherdRegistry}. They are written as a timeline, top to bottom, in ticks from the
 * cast, against {@link SpellStage} - which is what keeps every one of them decoration: a
 * choreography cannot move a combatant, change a tile or leave a block behind, because the
 * stage has no way to do any of those.
 *
 * <p>A spell's numbers and its looks stay apart on purpose. The rings a wave is drawn in come
 * from the spell's own reach, so an inscription that widens the spell widens the wave with it,
 * but nothing here decides who is hit.
 *
 * <p>One thing to know when adding to it: a block shown on a tile is replaced by the next one
 * shown there, and goes back to the real block when the longest hold on it runs out. So a
 * sequence on one tile (ground that melts, then cools) gives each stage a hold that runs a
 * tick or two past the start of the next, or the real floor blinks through between them.
 */
final class SherdStaging {

    private SherdStaging() {}

    // ═════════════════════════════════════════════════════════════════════════
    // 3 AP
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Phase Step. The floor tears open where you stood and where you land, and the ground
     * around each darkens for a moment as you go through.
     */
    static void phaseStep(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos from = stage.caster();
        GridPos to = stage.targets().get(0);
        BlockState rift = Blocks.CRYING_OBSIDIAN.getDefaultState();
        BlockState dark = Blocks.OBSIDIAN.getDefaultState();

        stage.floor(0, from, rift, 13);
        stage.column(0, from, 0.2, 2.4, ParticleTypes.REVERSE_PORTAL, 24);
        stage.circle(1, from, 0.6, 0.3, ParticleTypes.PORTAL, 14);
        for (GridPos beside : ground(stage, SpellShapes.ring(from, 1))) stage.floor(2, beside, dark, 6);

        stage.floor(4, to, rift, 16);
        stage.column(5, to, 2.6, 0.2, ParticleTypes.END_ROD, 20);
        stage.circle(6, to, 0.8, 0.2, ParticleTypes.PORTAL, 18);
        for (GridPos beside : ground(stage, SpellShapes.ring(to, 1))) stage.floor(6, beside, dark, 8);
        stage.flash(4, List.of(from, to), 0xFFAA55FF, 10);
    }

    /** Guardian Spirit. The ground under each pet turns to moss, and flowers come up around it. */
    static void guardianSpirit(SpellStage stage) {
        BlockState moss = Blocks.MOSS_BLOCK.getDefaultState();
        BlockState[] blooms = {
            Blocks.DANDELION.getDefaultState(), Blocks.POPPY.getDefaultState(),
            Blocks.AZURE_BLUET.getDefaultState(), Blocks.OXEYE_DAISY.getDefaultState()
        };
        int bloom = 0;
        for (GridPos pet : stage.targets()) {
            stage.floor(4, pet, moss, 32);
            stage.circle(5, pet, 0.9, 0.2, ParticleTypes.HAPPY_VILLAGER, 12);
            stage.burst(7, pet, 1.3, ParticleTypes.HEART, 4, 0.3, 0.02);
            for (GridPos beside : ground(stage, SpellShapes.ring(pet, 1))) {
                stage.floor(6, beside, moss, 26);
                stage.prop(8, beside, 0, blooms[bloom++ % blooms.length], 22);
            }
        }
        stage.sound(5, stage.caster(), SoundEvents.BLOCK_MOSS_PLACE, 0.9f, 1.0f);
    }

    /** Corrode. The floor under the target rusts through, stage by stage, and it spreads. */
    static void corrode(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos target = stage.targets().get(0);
        BlockState[] rust = {
            Blocks.EXPOSED_COPPER.getDefaultState(), Blocks.WEATHERED_COPPER.getDefaultState(),
            Blocks.OXIDIZED_COPPER.getDefaultState()
        };
        int hit = 8;
        for (int i = 0; i < rust.length; i++) {
            int tick = hit + i * 5;
            stage.floor(tick, target, rust[i], i == rust.length - 1 ? 22 : 7);
            stage.burst(tick, target, 0.3, ParticleTypes.SCRAPE, 8, 0.35, 0.02);
            stage.sound(tick, target, SoundEvents.ITEM_AXE_SCRAPE, 0.7f, 0.8f + i * 0.15f);
        }
        for (GridPos beside : ground(stage, SpellShapes.ring(target, 1))) {
            stage.floor(hit + 5, beside, rust[0], 7);
            stage.floor(hit + 10, beside, rust[1], 16);
        }
        stage.burst(hit + 10, target, 0.9, ParticleTypes.ITEM_SLIME, 10, 0.3, 0.05);
        stage.flash(hit, List.of(target), 0xFF55AA88, 12);
    }

    /**
     * Riptide Hook. A line of water runs out along the floor to where the target stood, and
     * reels back in from the far end.
     */
    static void riptideHook(SpellStage stage) {
        if (stage.origins().isEmpty()) return;
        GridPos caster = stage.caster();
        GridPos hooked = stage.origins().get(0);
        List<GridPos> line = new ArrayList<>(SpellShapes.between(caster, hooked));
        line.add(hooked);
        BlockState water = Blocks.WATER.getDefaultState();

        int length = line.size();
        for (int i = 0; i < length; i++) {
            GridPos tile = line.get(i);
            int out = 1 + i;
            int back = length + 5 + (length - 1 - i) * 2;
            stage.floor(out, tile, water, back - out);
            stage.burst(out, tile, 0.9, ParticleTypes.SPLASH, 8, 0.3, 0.08);
            stage.burst(back, tile, 0.6, ParticleTypes.FISHING, 5, 0.25, 0.02);
        }
        stage.streak(1, caster, hooked, ParticleTypes.CRIT, null, 14, 0.2);
        stage.sound(length, hooked, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, 0.9f, 0.9f);
        stage.sound(length + 5, caster, SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 0.8f);
        stage.flash(1, line, 0xFF3388CC, length + 6);
    }

    /** Shatter Will. Glass closes round the target, and breaks. */
    static void shatterWill(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos target = stage.targets().get(0);
        BlockState glass = Blocks.MAGENTA_STAINED_GLASS.getDefaultState();
        int closes = 6;
        int breaks = 13;

        stage.prop(closes, target, 0, glass, breaks - closes);
        stage.prop(closes + 1, target, 1, glass, breaks - closes - 1);
        stage.sound(closes, target, SoundEvents.BLOCK_GLASS_PLACE, 0.8f, 1.4f);

        stage.shatter(breaks, target, 0.6, glass, 24);
        stage.shatter(breaks, target, 1.6, glass, 18);
        stage.burst(breaks, target, 1.2, ParticleTypes.ENCHANTED_HIT, 14, 0.4, 0.2);
        stage.sound(breaks, target, SoundEvents.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        stage.shake(breaks, 0.3f, 5);
        stage.flash(breaks, List.of(target), 0xFFCC66CC, 8);
    }

    /**
     * Entangle. Roots run through the ground to the target, a thorn bush closes over its
     * legs, and moss spreads onto the tiles beside it.
     */
    static void entangle(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos target = stage.targets().get(0);
        BlockState rooted = Blocks.ROOTED_DIRT.getDefaultState();
        BlockState moss = Blocks.MOSS_BLOCK.getDefaultState();
        BlockState bush = Blocks.SWEET_BERRY_BUSH.getDefaultState().with(SweetBerryBushBlock.AGE, 2);
        BlockState carpet = Blocks.MOSS_CARPET.getDefaultState();

        int tick = 1;
        for (GridPos tile : SpellShapes.between(stage.caster(), target)) {
            stage.floor(tick, tile, rooted, 18);
            stage.burst(tick, tile, 0.2, ParticleTypes.COMPOSTER, 4, 0.3, 0.02);
            tick += 2;
        }
        int grab = tick + 1;
        stage.floor(grab, target, moss, 34);
        stage.prop(grab, target, 0, bush, 28);
        stage.sound(grab, target, SoundEvents.BLOCK_SWEET_BERRY_BUSH_PLACE, 1.0f, 0.7f);
        stage.burst(grab, target, 0.5, ParticleTypes.COMPOSTER, 14, 0.4, 0.05);
        for (GridPos beside : ground(stage, SpellShapes.ring(target, 1))) {
            stage.floor(grab + 2, beside, moss, 26);
            stage.prop(grab + 3, beside, 0, carpet, 22);
        }
        stage.flash(grab, SpellShapes.disc(target, 1), 0xFF44AA44, 10);
    }

    /** Hex Trap. A rune burns on the tile for a moment, dims, and is gone: the trap is set. */
    static void hexTrap(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos tile = stage.targets().get(0);
        stage.floor(4, tile, Blocks.CRYING_OBSIDIAN.getDefaultState(), 9);
        stage.floor(12, tile, Blocks.OBSIDIAN.getDefaultState(), 6);
        stage.circle(4, tile, 0.45, 0.15, ParticleTypes.ENCHANT, 14);
        stage.circle(7, tile, 0.30, 0.15, ParticleTypes.WITCH, 10);
        stage.circle(10, tile, 0.15, 0.15, ParticleTypes.WITCH, 6);
        stage.column(4, tile, 0.2, 1.4, ParticleTypes.ENCHANT, 10);
        stage.burst(18, tile, 0.2, ParticleTypes.SMOKE, 6, 0.2, 0.01);
        stage.sound(18, tile, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.6f);
    }

    // ── Earthen Spike ────────────────────────────────────────────────────────

    /** Ticks the spike stands before it crumbles. */
    private static final int SPIKE_STANDS = 16;

    /**
     * Earthen Spike. The ground splits from the caster to the target, and a spike of stone
     * comes up through whoever is standing at the end of it.
     */
    static void earthenSpike(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos target = stage.targets().get(0);
        BlockState churned = Blocks.COARSE_DIRT.getDefaultState();
        BlockState bedrock = Blocks.DRIPSTONE_BLOCK.getDefaultState();

        // The split runs along the floor, a tile every other tick.
        int tick = 1;
        for (GridPos tile : SpellShapes.between(stage.caster(), target)) {
            stage.floor(tick, tile, churned, 16);
            stage.floorDebris(tick, tile, 10);
            stage.sound(tick, tile, SoundEvents.BLOCK_STONE_BREAK, 0.6f, 0.7f);
            tick += 2;
        }

        // The spike: two blocks of dripstone, wide at the foot and pointed at the top.
        int up = tick + 1;
        stage.floor(up, target, bedrock, SPIKE_STANDS + 6);
        stage.prop(up, target, 0, dripstone(Thickness.FRUSTUM), SPIKE_STANDS);
        stage.prop(up, target, 1, dripstone(Thickness.TIP), SPIKE_STANDS);
        stage.floorDebris(up, target, 26);
        stage.burst(up, target, 0.4, ParticleTypes.DUST_PLUME, 16, 0.4, 0.08);
        stage.sound(up, target, SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 0.6f);
        stage.sound(up, target, SoundEvents.BLOCK_STONE_BREAK, 1.0f, 0.5f);
        stage.shake(up, 0.5f, 8);
        stage.flash(up, List.of(target), 0xFFB08050, 8);

        // The ground around it heaves.
        for (GridPos beside : ground(stage, SpellShapes.ring(target, 1))) {
            stage.floor(up, beside, churned, 14);
            stage.floorDebris(up, beside, 8);
        }

        // And it falls apart again.
        int down = up + SPIKE_STANDS;
        stage.shatter(down, target, 1.0, bedrock, 24);
        stage.sound(down, target, SoundEvents.BLOCK_STONE_BREAK, 0.8f, 0.9f);
    }

    private static BlockState dripstone(Thickness thickness) {
        return Blocks.POINTED_DRIPSTONE.getDefaultState().with(PointedDripstoneBlock.THICKNESS, thickness);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4 AP
    // ═════════════════════════════════════════════════════════════════════════

    /** Phantom Slash. The cut carries on through the target and scores the floor from side to side. */
    static void phantomSlash(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos caster = stage.caster();
        GridPos target = stage.targets().get(0);
        // Across the swing: a quarter turn from the line between the two of them.
        int acrossX = -Integer.signum(target.z() - caster.z());
        int acrossZ = Integer.signum(target.x() - caster.x());
        if (acrossX == 0 && acrossZ == 0) acrossX = 1;
        List<GridPos> cut = List.of(
            new GridPos(target.x() - acrossX, target.z() - acrossZ), target,
            new GridPos(target.x() + acrossX, target.z() + acrossZ));
        BlockState scar = Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();

        for (int i = 0; i < cut.size(); i++) {
            GridPos tile = cut.get(i);
            if (!stage.isGround(tile)) continue;
            stage.floor(3 + i, tile, scar, 24);
            stage.floorDebris(3 + i, tile, 6);
            stage.burst(3 + i, tile, 1.0, ParticleTypes.SWEEP_ATTACK, 1, 0.1, 0.0);
        }
        stage.sound(3, target, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        stage.shake(4, 0.3f, 4);
    }

    // ── Immolation ───────────────────────────────────────────────────────────

    /** The tick the fireball lands, matching the step's own impact burst. */
    private static final int FIRE_LANDS = 8;

    /**
     * Immolation. The fireball lands and the ground takes it: fire stands on the target's
     * tile and the ones beside it, the floor under the blast glows, and what is left when it
     * burns down is scorched for a while longer.
     */
    static void immolation(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos target = stage.targets().get(0);
        BlockState fire = Blocks.FIRE.getDefaultState();
        BlockState molten = Blocks.MAGMA_BLOCK.getDefaultState();
        BlockState scorched = Blocks.BLACKSTONE.getDefaultState();

        int hit = FIRE_LANDS;
        stage.screenFlash(hit, 0x55FF7A1E, 3);
        stage.shake(hit, 0.6f, 8);
        stage.flash(hit, SpellShapes.disc(target, 1), 0xFFFF7722, 10);

        // Under the target the floor melts, then cools black.
        stage.floor(hit, target, molten, 26);
        stage.floor(hit + 24, target, scorched, 16);
        stage.prop(hit, target, 0, fire, 14);
        stage.burst(hit, target, 0.6, ParticleTypes.LAVA, 12, 0.3, 0.0);
        stage.burst(hit + 14, target, 0.9, ParticleTypes.LARGE_SMOKE, 8, 0.3, 0.02);
        stage.sound(hit + 14, target, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.8f);

        // Beside it the blast only licks out: a shorter fire, and a scorch mark.
        for (GridPos beside : ground(stage, SpellShapes.ring(target, 1))) {
            stage.prop(hit + 1, beside, 0, fire, 9);
            stage.floor(hit + 1, beside, scorched, 30);
            stage.burst(hit + 1, beside, 0.5, ParticleTypes.FLAME, 8, 0.3, 0.03);
            stage.burst(hit + 10, beside, 0.6, ParticleTypes.SMOKE, 5, 0.25, 0.02);
        }
    }

    /**
     * Tectonic Charge. A furrow is torn out of the floor along the way the target was thrown,
     * from where it stood to where it stopped.
     */
    static void tectonicCharge(SpellStage stage) {
        if (stage.targets().isEmpty() || stage.origins().isEmpty()) return;
        GridPos from = stage.origins().get(0);
        GridPos to = stage.targets().get(0);
        List<GridPos> path = new ArrayList<>();
        path.add(from);
        path.addAll(SpellShapes.between(from, to));
        if (!to.equals(from)) path.add(to);
        BlockState furrow = Blocks.COARSE_DIRT.getDefaultState();

        int tick = 3;
        for (GridPos tile : path) {
            stage.floor(tick, tile, furrow, 28);
            stage.floorDebris(tick, tile, 10);
            stage.burst(tick, tile, 0.3, ParticleTypes.DUST_PLUME, 6, 0.3, 0.05);
            tick++;
        }
        stage.shake(3, 0.5f, 8);
        stage.flash(3, path, 0xFFB08050, 10);
        // Where it came to rest.
        stage.floorDebris(tick, to, 20);
        stage.sound(tick, to, SoundEvents.BLOCK_ANVIL_LAND, 0.6f, 0.6f);
    }

    // ── Tidal Surge ──────────────────────────────────────────────────────────

    /** Ticks between one ring of the wave and the next. */
    private static final int WAVE_STEP = 4;

    /**
     * Tidal Surge. A wave goes out from the caster, one ring at a time.
     *
     * <p>Each ring is a crest of standing water that lasts a moment, and what it leaves is the
     * floor awash behind it. The inner rings are flooded first and so drain first, which is
     * what makes it read as water running off rather than a puddle switching on and off.
     */
    static void tidalSurge(SpellStage stage) {
        GridPos centre = stage.caster();
        int reach = Math.max(1, stage.radius());
        BlockState water = Blocks.WATER.getDefaultState();

        stage.shake(2, 0.25f, 6);
        for (int ring = 1; ring <= reach; ring++) {
            int tick = 2 + (ring - 1) * WAVE_STEP;
            List<GridPos> tiles = ground(stage, SpellShapes.ring(centre, ring));
            for (GridPos tile : tiles) {
                stage.prop(tick, tile, 0, water, WAVE_STEP + 1);
                stage.floor(tick + WAVE_STEP - 1, tile, water, 16);
                stage.burst(tick, tile, 1.1, ParticleTypes.SPLASH, 12, 0.35, 0.1);
                stage.burst(tick + 1, tile, 0.7, ParticleTypes.BUBBLE_POP, 5, 0.3, 0.02);
                stage.burst(tick + WAVE_STEP, tile, 0.4, ParticleTypes.FALLING_WATER, 6, 0.3, 0.0);
            }
            stage.flash(tick, tiles, 0xFF3388CC, 8);
            stage.sound(tick, centre, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.6f, 0.7f + ring * 0.15f);
        }
    }

    /**
     * Soul Drain. The ground under the target turns to soul soil and burns blue, and what is
     * drawn out of it streams back across the arena to the caster.
     */
    static void soulDrain(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos caster = stage.caster();
        GridPos target = stage.targets().get(0);
        int hit = 8;

        stage.floor(hit, target, Blocks.SOUL_SOIL.getDefaultState(), 28);
        stage.prop(hit, target, 0, Blocks.SOUL_FIRE.getDefaultState(), 10);
        for (GridPos beside : ground(stage, SpellShapes.ring(target, 1))) {
            stage.floor(hit + 2, beside, Blocks.SOUL_SAND.getDefaultState(), 18);
        }
        // Back toward the caster: the streak is drawn from the target, so it reads as leaving it.
        stage.streak(hit + 2, target, caster, ParticleTypes.SOUL, ParticleTypes.SCULK_SOUL, 16, 0.5);
        stage.streak(hit + 5, target, caster, ParticleTypes.SOUL_FIRE_FLAME, null, 12, 0.3);
        stage.burst(hit + 8, caster, 1.0, ParticleTypes.SOUL, 8, 0.3, 0.02);
        stage.flash(hit, List.of(target), 0xFF33CCDD, 10);
    }

    /**
     * Petsplosion. The ground under each pet flashes like a lit charge, and goes off: a burst
     * the size of the blast, and a ring of scorched floor left where it was.
     */
    static void petsplosion(SpellStage stage) {
        if (stage.pets().isEmpty()) return;
        BlockState charge = Blocks.TNT.getDefaultState();
        BlockState primed = Blocks.WHITE_CONCRETE.getDefaultState();
        BlockState scorched = Blocks.BLACKSTONE.getDefaultState();
        int reach = Math.max(1, stage.radius());
        int boom = 5;

        for (GridPos pet : stage.pets()) {
            stage.floor(1, pet, charge, 3);
            stage.floor(3, pet, primed, 2);
            stage.floor(4, pet, charge, 2);
            stage.sound(1, pet, SoundEvents.ENTITY_TNT_PRIMED, 0.8f, 1.3f);

            stage.burst(boom, pet, 1.0, ParticleTypes.EXPLOSION_EMITTER, 1, 0.0, 0.0);
            for (int ring = 0; ring <= reach; ring++) {
                for (GridPos tile : ground(stage, SpellShapes.ring(pet, ring))) {
                    stage.floor(boom + ring, tile, scorched, 34 - ring * 6);
                    stage.burst(boom + ring + 4, tile, 0.5, ParticleTypes.LARGE_SMOKE, 2, 0.25, 0.01);
                }
            }
            stage.flash(boom, SpellShapes.disc(pet, reach), 0xFFFF7722, 10);
        }
        stage.shake(boom, 0.8f, 10);
        stage.screenFlash(boom, 0x44FFFFFF, 2);
    }

    /**
     * Chain Lightning. A bolt comes down on every enemy the chain reaches, one after another,
     * with the arc jumping between them and the floor left burnt under each.
     */
    static void chainLightning(SpellStage stage) {
        if (stage.targets().isEmpty()) return;
        GridPos previous = stage.caster();
        int hop = 0;
        for (GridPos struck : stage.targets()) {
            int tick = 4 + hop * 3;
            stage.streak(tick - 1, previous, struck, ParticleTypes.ELECTRIC_SPARK, null, 12, 0.6);
            stage.lightning(tick, struck);
            stage.floor(tick, struck, Blocks.BLACKSTONE.getDefaultState(), 26);
            stage.burst(tick, struck, 0.4, ParticleTypes.ELECTRIC_SPARK, 14, 0.4, 0.3);
            stage.flash(tick, List.of(struck), 0xFFFFEE55, 6);
            previous = struck;
            hop++;
        }
        stage.shake(4, 0.5f, 6);
        stage.screenFlash(4, 0x33FFFFFF, 2);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5 AP
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Mending Light. The floor under the caster and beside them lights up, and a ring of moss
     * and flowers grows round it. The rising helix is the spell's own and plays over this.
     */
    static void mendingLight(SpellStage stage) {
        GridPos centre = stage.caster();
        BlockState glow = Blocks.VERDANT_FROGLIGHT.getDefaultState();
        BlockState moss = Blocks.MOSS_BLOCK.getDefaultState();
        BlockState[] blooms = {
            Blocks.OXEYE_DAISY.getDefaultState(), Blocks.AZURE_BLUET.getDefaultState(),
            Blocks.DANDELION.getDefaultState(), Blocks.POPPY.getDefaultState()
        };

        stage.floor(3, centre, glow, 30);
        for (GridPos beside : ground(stage, SpellShapes.ring(centre, 1))) stage.floor(5, beside, glow, 24);
        int bloom = 0;
        for (GridPos outer : ground(stage, SpellShapes.ring(centre, 2))) {
            stage.floor(7, outer, moss, 22);
            stage.prop(9, outer, 0, blooms[bloom++ % blooms.length], 18);
        }
        stage.circle(4, centre, 1.2, 0.2, ParticleTypes.HAPPY_VILLAGER, 16);
        stage.circle(8, centre, 2.0, 0.2, ParticleTypes.HAPPY_VILLAGER, 22);
        stage.flash(3, SpellShapes.disc(centre, 1), 0xFF88FF99, 12);
    }

    /**
     * Stone Aegis. Stone comes up out of the floor on every side of the caster, two blocks
     * high where it faces them and a low kerb at the corners, and sinks back again.
     */
    static void stoneAegis(SpellStage stage) {
        GridPos centre = stage.caster();
        BlockState stone = Blocks.STONE_BRICKS.getDefaultState();
        BlockState kerb = Blocks.STONE_BRICK_SLAB.getDefaultState();

        stage.floor(2, centre, Blocks.CHISELED_STONE_BRICKS.getDefaultState(), 28);
        for (GridPos beside : ground(stage, SpellShapes.ring(centre, 1))) {
            // The lower course first and the upper last going up; the upper goes first coming down.
            stage.prop(3, beside, 0, stone, 18);
            stage.prop(5, beside, 1, stone, 13);
            stage.floorDebris(3, beside, 8);
            stage.burst(21, beside, 0.5, ParticleTypes.DUST_PLUME, 6, 0.3, 0.04);
        }
        for (GridPos corner : ground(stage, corners(centre))) {
            stage.prop(4, corner, 0, kerb, 15);
            stage.floorDebris(4, corner, 5);
        }
        stage.sound(3, centre, SoundEvents.BLOCK_STONE_PLACE, 1.0f, 0.6f);
        stage.sound(5, centre, SoundEvents.BLOCK_STONE_PLACE, 1.0f, 0.8f);
        stage.sound(21, centre, SoundEvents.BLOCK_STONE_BREAK, 0.7f, 0.7f);
        stage.shake(3, 0.35f, 6);
    }

    /** Alchemist's Surge. The floor crystallises to amethyst, shards grow out of it, and the brew rises. */
    static void alchemistsSurge(SpellStage stage) {
        GridPos centre = stage.caster();
        BlockState crystal = Blocks.AMETHYST_BLOCK.getDefaultState();
        BlockState shard = Blocks.AMETHYST_CLUSTER.getDefaultState();

        stage.floor(2, centre, crystal, 30);
        for (GridPos beside : ground(stage, SpellShapes.ring(centre, 1))) {
            stage.floor(4, beside, crystal, 24);
            stage.prop(7, beside, 0, shard, 17);
            stage.burst(7, beside, 0.6, ParticleTypes.WITCH, 5, 0.25, 0.02);
        }
        stage.column(5, centre, 0.3, 2.4, ParticleTypes.WITCH, 14);
        stage.circle(3, centre, 0.8, 0.4, ParticleTypes.EFFECT, 14);
        stage.circle(8, centre, 1.3, 1.0, ParticleTypes.EFFECT, 18);
        stage.sound(4, centre, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        stage.sound(7, centre, SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 0.9f, 1.2f);
        stage.flash(2, SpellShapes.disc(centre, 1), 0xFFAA66EE, 12);
    }

    /**
     * Bountiful Harvest. The ground around the caster turns to tilled soil, wheat comes up
     * through it and ripens in a breath, and then it is gathered in.
     */
    static void bountifulHarvest(SpellStage stage) {
        GridPos centre = stage.caster();
        BlockState soil = Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7);

        for (int ring = 1; ring <= 2; ring++) {
            for (GridPos tile : ground(stage, SpellShapes.ring(centre, ring))) {
                int sown = 2 + ring;
                stage.floor(sown, tile, soil, 30);
                stage.prop(sown + 2, tile, 0, wheat(1), 6);
                stage.prop(sown + 6, tile, 0, wheat(4), 6);
                stage.prop(sown + 10, tile, 0, wheat(7), 12);
                stage.burst(sown + 10, tile, 0.6, ParticleTypes.HAPPY_VILLAGER, 3, 0.25, 0.02);
                stage.shatter(sown + 22, tile, 0.5, wheat(7), 6);
            }
        }
        stage.sound(4, centre, SoundEvents.ITEM_CROP_PLANT, 1.0f, 1.0f);
        stage.sound(25, centre, SoundEvents.BLOCK_CROP_BREAK, 1.0f, 1.0f);
    }

    private static BlockState wheat(int age) {
        return Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, age);
    }

    /**
     * Seeker Vexes. A summoning circle is laid in dark tile around the caster with a soul
     * lantern at each corner, and something rises from every one of them.
     */
    static void seekerVexes(SpellStage stage) {
        GridPos centre = stage.caster();
        BlockState tile = Blocks.DEEPSLATE_TILES.getDefaultState();

        stage.floor(1, centre, Blocks.CHISELED_DEEPSLATE.getDefaultState(), 28);
        for (GridPos beside : ground(stage, SpellShapes.ring(centre, 1))) stage.floor(2, beside, tile, 24);
        for (GridPos corner : ground(stage, corners(centre))) {
            stage.floor(3, corner, tile, 22);
            stage.prop(4, corner, 0, Blocks.SOUL_LANTERN.getDefaultState(), 19);
            stage.column(10, corner, 0.4, 2.6, ParticleTypes.SOUL, 10);
        }
        stage.circle(2, centre, 1.5, 0.2, ParticleTypes.SOUL_FIRE_FLAME, 20);
        stage.circle(6, centre, 1.5, 0.2, ParticleTypes.ENCHANT, 20);
        stage.circle(9, centre, 1.5, 0.6, ParticleTypes.SOUL_FIRE_FLAME, 20);
        stage.sound(2, centre, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 0.8f, 1.2f);
    }

    /** Fortune's Favor. Gold runs out across the floor in a ripple, and coins fly up where the caster stands. */
    static void fortunesFavor(SpellStage stage) {
        GridPos centre = stage.caster();
        BlockState gold = Blocks.GOLD_BLOCK.getDefaultState();

        for (int ring = 0; ring <= 2; ring++) {
            int tick = 2 + ring * 3;
            List<GridPos> tiles = ground(stage, SpellShapes.ring(centre, ring));
            for (GridPos tile : tiles) {
                stage.floor(tick, tile, gold, 6);
                stage.burst(tick, tile, 0.3, ParticleTypes.WAX_ON, 4, 0.3, 0.02);
            }
            stage.flash(tick, tiles, 0xFFFFD700, 6);
            stage.sound(tick, centre, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f + ring * 0.3f);
        }
        stage.burst(4, centre, 1.2,
            new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), 18, 0.2, 0.18);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 6 AP
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * War Cry. Banners go up at the four corners, fire stands for a moment on the ground
     * beside the caster, and a ring of flame runs outward from them.
     */
    static void warCry(SpellStage stage) {
        GridPos centre = stage.caster();

        stage.floor(2, centre, Blocks.RED_NETHER_BRICKS.getDefaultState(), 28);
        for (GridPos corner : ground(stage, corners(centre))) {
            stage.prop(4, corner, 0, Blocks.RED_BANNER.getDefaultState(), 24);
            stage.floorDebris(4, corner, 6);
        }
        for (GridPos beside : ground(stage, SpellShapes.ring(centre, 1))) {
            stage.floor(6, beside, Blocks.BLACKSTONE.getDefaultState(), 24);
            stage.prop(6, beside, 0, Blocks.FIRE.getDefaultState(), 9);
        }
        stage.circle(2, centre, 1.4, 0.3, ParticleTypes.FLAME, 24);
        stage.circle(5, centre, 1.9, 0.3, ParticleTypes.FLAME, 28);
        stage.circle(8, centre, 2.4, 0.3, ParticleTypes.SMALL_FLAME, 30);
        stage.shake(4, 0.6f, 10);
        stage.screenFlash(4, 0x33FF3300, 3);
    }

    /** Death Mark, when it only wounds: the ground blackens, blue fire rises, and wither roses come up round it. */
    static void deathMark(SpellStage stage) {
        mark(stage, false);
    }

    /** Death Mark, when it kills outright: the same, under a bolt of lightning. */
    static void deathMarkExecute(SpellStage stage) {
        mark(stage, true);
    }

    private static void mark(SpellStage stage, boolean execute) {
        if (stage.targets().isEmpty()) return;
        GridPos target = stage.targets().get(0);
        int hit = 8;

        stage.floor(hit, target, Blocks.COAL_BLOCK.getDefaultState(), 34);
        stage.prop(hit, target, 0, Blocks.SOUL_FIRE.getDefaultState(), 10);
        stage.column(hit, target, 0.2, 3.2, ParticleTypes.SOUL_FIRE_FLAME, 26);
        stage.column(hit + 2, target, 0.2, 3.6, ParticleTypes.SOUL, 14);
        for (GridPos beside : ground(stage, SpellShapes.ring(target, 1))) {
            stage.floor(hit + 2, beside, Blocks.BLACKSTONE.getDefaultState(), 28);
            stage.prop(hit + 4, beside, 0, Blocks.WITHER_ROSE.getDefaultState(), 22);
            stage.burst(hit + 4, beside, 0.4, ParticleTypes.SMOKE, 4, 0.25, 0.01);
        }
        stage.flash(hit, SpellShapes.disc(target, 1), 0xFF330011, 12);
        if (execute) {
            stage.lightning(hit, target);
            stage.shake(hit, 0.9f, 12);
            stage.screenFlash(hit, 0x66220000, 4);
        } else {
            stage.shake(hit, 0.4f, 6);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Shared
    // ═════════════════════════════════════════════════════════════════════════

    /** The tiles of {@code shape} a spell can draw on: inside the arena and not a hole in it. */
    private static List<GridPos> ground(SpellStage stage, List<GridPos> shape) {
        List<GridPos> tiles = new ArrayList<>();
        for (GridPos tile : shape) {
            if (stage.isGround(tile)) tiles.add(tile);
        }
        return tiles;
    }

    /** The four tiles diagonally off {@code centre}. */
    private static List<GridPos> corners(GridPos centre) {
        return List.of(
            new GridPos(centre.x() - 1, centre.z() - 1), new GridPos(centre.x() + 1, centre.z() - 1),
            new GridPos(centre.x() - 1, centre.z() + 1), new GridPos(centre.x() + 1, centre.z() + 1));
    }
}
