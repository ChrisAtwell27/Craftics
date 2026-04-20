package com.crackedgames.craftics.vfx;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VfxAnchorResolverTest {

    private static VfxContext ctxWithBlocks() {
        return new VfxContext(-1, -1,
            new BlockPos(10, 64, 20),
            new BlockPos(30, 64, 40),
            5.0f, false, null);
    }

    @Test
    void atPosReturnsFixedPosition() {
        var resolver = new VfxAnchorResolver(ctxWithBlocks(), null);
        Vec3d v = resolver.resolve(new VfxAnchor.AtPos(new Vec3d(1.5, 2.5, 3.5)));
        assertEquals(new Vec3d(1.5, 2.5, 3.5), v);
    }

    @Test
    void atOriginFallsBackToOriginBlockWhenNoEntity() {
        var resolver = new VfxAnchorResolver(ctxWithBlocks(), null);
        Vec3d v = resolver.resolve(VfxAnchor.ORIGIN);
        // originBlock = (10,64,20). Resolver adds +0.5 horizontal and +1.0 head-height
        assertEquals(new Vec3d(10.5, 65.0, 20.5), v);
    }

    @Test
    void atTargetFallsBackToTargetBlockWhenNoEntity() {
        var resolver = new VfxAnchorResolver(ctxWithBlocks(), null);
        Vec3d v = resolver.resolve(VfxAnchor.TARGET);
        // targetBlock = (30,64,40). Resolver adds +0.5 horizontal and +1.0 head-height
        assertEquals(new Vec3d(30.5, 65.0, 40.5), v);
    }

    @Test
    void atGridTileUsesArenaOrigin() {
        // Use real GridArena constructor: (width, height, tiles, origin, levelNumber, playerStart)
        GridArena arena = new GridArena(5, 5, new GridTile[5][5],
            new BlockPos(100, 64, 200), 1, new GridPos(0, 0));
        VfxContext ctx = new VfxContext(-1, -1,
            new BlockPos(0, 0, 0), new BlockPos(0, 0, 0),
            0f, false, arena);
        var resolver = new VfxAnchorResolver(ctx, null);
        Vec3d v = resolver.resolve(new VfxAnchor.AtGridTile(2, 3, 0.5));
        // GridArena.gridToBlockPos(GridPos(2,3)) = BlockPos(102, 65, 203)  (yOffset=1 baked in)
        // Resolver adds +0.5 horizontal and the requested yOffset(0.5) → (102.5, 65.5, 203.5)
        assertEquals(new Vec3d(102.5, 65.5, 203.5), v);
    }
}
