package com.crackedgames.craftics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
//? if <=1.21.1 {
import net.minecraft.state.property.DirectionProperty;
//?} else {
/*import net.minecraft.state.property.EnumProperty;
*///?}
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Invisible second half of the level-select table. Placed automatically by
 * {@link LevelSelectBlock#onPlaced} at the phantom position so the other
 * visual half is raycast-clickable from perpendicular angles — vanilla's
 * outline extension only registers a hit when the ray also passes through
 * the real block's column, so looking at the phantom side-on (ray stays in
 * the phantom's column) otherwise misses entirely.
 * <p>
 * {@code FACING} on this ghost equals the real block's FACING, and the real
 * block sits at {@code ghostPos.offset(FACING)}.
 */
public class LevelSelectGhostBlock extends Block {

    public static final MapCodec<LevelSelectGhostBlock> CODEC = createCodec(LevelSelectGhostBlock::new);
    //? if <=1.21.1 {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    //?} else {
    /*public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    *///?}

    private static final VoxelShape SHAPE = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

    public LevelSelectGhostBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    // Keep BlockRenderType.MODEL (default) — the model uses a fully-transparent
    // texture on a slab-shaped cuboid so nothing shows in normal render, but the
    // faces exist so MC's block-breaking progress overlay has surfaces to draw
    // the crack texture onto. With INVISIBLE render type there are no faces,
    // so breaking the ghost side showed no break animation at all.

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        BlockPos realPos = pos.offset(state.get(FACING));
        if (world.getBlockState(realPos).getBlock() instanceof LevelSelectBlock) {
            if (!world.isClient()) {
                BlockEntity be = world.getBlockEntity(realPos);
                if (be instanceof NamedScreenHandlerFactory factory) {
                    player.openHandledScreen(factory);
                }
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient()) {
            BlockPos realPos = pos.offset(state.get(FACING));
            // Drop the real half's item so the player recovers the block. The
            // real block's own onBreak then tears down this ghost via
            // setBlockState(AIR) — no drop loop because setBlockState doesn't
            // route back through onBreak.
            if (world.getBlockState(realPos).getBlock() instanceof LevelSelectBlock) {
                world.breakBlock(realPos, !player.isCreative(), player);
            }
        }
        return super.onBreak(world, pos, state, player);
    }
}
