package com.crackedgames.craftics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
//? if <=1.21.1 {
import net.minecraft.state.property.DirectionProperty;
//?} else {
/*import net.minecraft.state.property.EnumProperty;
*///?}
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class LevelSelectBlock extends BlockWithEntity {

    public static final MapCodec<LevelSelectBlock> CODEC = createCodec(LevelSelectBlock::new);
    //? if <=1.21.1 {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    //?} else {
    /*public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    *///?}

    // The model extends 2 blocks (0..32 in model space). Blockstate rotates it via
    // y-rotation, and y=90 maps +Z → -X in MC's coordinate system, so the body
    // extension per FACING is:
    //   NORTH (y=0)   → body +Z  (phantom at realPos.south())
    //   EAST  (y=90)  → body -X  (phantom at realPos.west())
    //   SOUTH (y=180) → body -Z  (phantom at realPos.north())
    //   WEST  (y=270) → body +X  (phantom at realPos.east())
    // i.e. phantom = realPos.offset(FACING.getOpposite()).
    // The outline still extends into the phantom column so hits from angles that
    // cross the real block's column still register, but vanilla raycast won't
    // test this extended shape when the ray stays purely in the phantom column —
    // that's why a LevelSelectGhostBlock is placed there too (see onPlaced).
    private static final VoxelShape OUTLINE_NORTH = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 2.0);
    private static final VoxelShape OUTLINE_SOUTH = VoxelShapes.cuboid(0.0, 0.0, -1.0, 1.0, 0.5, 1.0);
    private static final VoxelShape OUTLINE_EAST  = VoxelShapes.cuboid(-1.0, 0.0, 0.0, 1.0, 0.5, 1.0);
    private static final VoxelShape OUTLINE_WEST  = VoxelShapes.cuboid(0.0, 0.0, 0.0, 2.0, 0.5, 1.0);
    // Collision shape stays within the single block (slab height)
    private static final VoxelShape COLLISION = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

    private static BlockPos phantomPos(BlockPos realPos, Direction facing) {
        return realPos.offset(facing.getOpposite());
    }

    public LevelSelectBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Place sideways — model extends perpendicular to player facing
        Direction facing = ctx.getHorizontalPlayerFacing().rotateYClockwise();
        // Reject placement if the phantom half's position isn't free — we need
        // to put a LevelSelectGhostBlock there so the other visual half is
        // clickable from perpendicular angles.
        if (!ctx.getWorld().getBlockState(phantomPos(ctx.getBlockPos(), facing)).canReplace(ctx)) {
            return null;
        }
        return getDefaultState().with(FACING, facing);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient()) {
            Direction facing = state.get(FACING);
            BlockPos ghostPos = phantomPos(pos, facing);
            world.setBlockState(
                ghostPos,
                ModBlocks.LEVEL_SELECT_GHOST_BLOCK.getDefaultState()
                    .with(LevelSelectGhostBlock.FACING, facing),
                Block.NOTIFY_ALL
            );
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient()) {
            BlockPos ghostPos = phantomPos(pos, state.get(FACING));
            // Use setBlockState (not breakBlock) so the ghost's onBreak doesn't
            // recurse back into this block — the real half has already dropped
            // its item, and the ghost has no drops of its own.
            if (world.getBlockState(ghostPos).getBlock() instanceof LevelSelectGhostBlock) {
                world.setBlockState(ghostPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case SOUTH -> OUTLINE_SOUTH;
            case EAST -> OUTLINE_EAST;
            case WEST -> OUTLINE_WEST;
            default -> OUTLINE_NORTH;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LevelSelectBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                               PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient()) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof NamedScreenHandlerFactory factory) {
                player.openHandledScreen(factory);
            }
        }
        return ActionResult.SUCCESS;
    }
}
