package com.crackedgames.craftics.client.hints;

import com.crackedgames.craftics.block.LevelSelectBlock;
import com.crackedgames.craftics.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class HintArrowRenderer {

    private static int tickCounter = 0;
    /** Spawn particles every N client ticks (≈4 per second at 20 TPS). */
    private static final int SPAWN_INTERVAL_TICKS = 5;

    private HintArrowRenderer() {}

    public static void tick(MinecraftClient client) {
        tickCounter++;
        if (tickCounter % SPAWN_INTERVAL_TICKS != 0) return;
        ClientWorld world = client.world;
        if (world == null) return;

        long now = System.currentTimeMillis();
        HintContext ctx = CrafticsHints.snapshot(client, now);

        for (Hint h : HintManager.get().getActiveWorldHints()) {
            if (!(h.presenter() instanceof HintPresenter.WorldArrow arrow)) continue;
            BlockPos target = "hub.level_select_arrow".equals(h.id())
                ? CrafticsHints.nearestLevelSelectBlock(ctx)
                : arrow.target();
            if (target != null) spawnArrowParticles(world, target, tickCounter);
        }
    }

    private static void spawnArrowParticles(ClientWorld world, BlockPos target, int t) {
        // The Level Select Block visually spans two block columns: the main hitbox
        // and a phantom/ghost half offset along facing.getOpposite(). To center the
        // arrow above the visible model, shift the spawn position by 0.5 toward
        // the ghost half. For any other anchor we just use the block center.
        double cx = target.getX() + 0.5;
        double cz = target.getZ() + 0.5;
        BlockState bs = world.getBlockState(target);
        if (bs.getBlock() == ModBlocks.LEVEL_SELECT_BLOCK) {
            Direction facing = bs.get(LevelSelectBlock.FACING);
            Direction toGhost = facing.getOpposite();
            cx += 0.5 * toGhost.getOffsetX();
            cz += 0.5 * toGhost.getOffsetZ();
        }

        double baseY = target.getY() + 1.4;
        double bob = 0.25 * Math.sin(t * 0.25);

        // Chevron arrow: tip at the bottom, two rings of wings flaring up and outward.
        // Spawned in 3D so the shape reads as a downward arrow from any view angle.
        spawn(world, cx, baseY + bob, cz);
        spawn(world, cx, baseY + 0.05 + bob, cz); // double tip for visual emphasis

        double w1 = 0.30, h1 = 0.30;
        spawn(world, cx + w1, baseY + h1 + bob, cz);
        spawn(world, cx - w1, baseY + h1 + bob, cz);
        spawn(world, cx, baseY + h1 + bob, cz + w1);
        spawn(world, cx, baseY + h1 + bob, cz - w1);

        double w2 = 0.55, h2 = 0.65;
        spawn(world, cx + w2, baseY + h2 + bob, cz);
        spawn(world, cx - w2, baseY + h2 + bob, cz);
        spawn(world, cx, baseY + h2 + bob, cz + w2);
        spawn(world, cx, baseY + h2 + bob, cz - w2);
    }

    private static void spawn(ClientWorld world, double x, double y, double z) {
        //? if <=1.21.4 {
        world.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0.0, -0.02, 0.0);
        //?} else
        /*world.addParticleClient(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0.0, -0.02, 0.0);*/
    }
}
