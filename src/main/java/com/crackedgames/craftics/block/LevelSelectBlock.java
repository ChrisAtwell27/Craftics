package com.crackedgames.craftics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
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

    // Model extends 2 blocks along Z (0 to 32 in model space).
    // Blockstate rotates the model via Y rotation, so shapes must match.
    // North (y=0):   model extends along +Z → shape goes Z 0..2
    // South (y=180): model extends along -Z → shape goes Z -1..1
    // East  (y=90):  model extends along +X → shape goes X 0..2
    // West  (y=270): model extends along -X → shape goes X -1..1
    // Outline shapes extend into the neighbor block for raycast/interaction (2 blocks wide)
    private static final VoxelShape OUTLINE_NORTH = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 2.0);
    private static final VoxelShape OUTLINE_SOUTH = VoxelShapes.cuboid(0.0, 0.0, -1.0, 1.0, 0.5, 1.0);
    private static final VoxelShape OUTLINE_EAST  = VoxelShapes.cuboid(-1.0, 0.0, 0.0, 1.0, 0.5, 1.0);
    private static final VoxelShape OUTLINE_WEST  = VoxelShapes.cuboid(0.0, 0.0, 0.0, 2.0, 0.5, 1.0);
    // Collision shape stays within the single block (slab height)
    private static final VoxelShape COLLISION = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

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
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().rotateYClockwise());
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
