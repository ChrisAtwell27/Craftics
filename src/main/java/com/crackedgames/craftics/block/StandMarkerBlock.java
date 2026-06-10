package com.crackedgames.craftics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
//? if <=1.21.1 {
/*import net.minecraft.state.property.DirectionProperty;
*///?} else {
import net.minecraft.state.property.EnumProperty;
//?}
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Marker block placed in a scene schematic to define a booth ("stand"). Its
 * {@link StandMarkerBlockEntity} stores the occupant id; the {@code FACING}
 * property records the direction the player faces when stood at the booth.
 * {@link com.crackedgames.craftics.scene.SceneScanner} reads both during a scan.
 */
public class StandMarkerBlock extends Block implements BlockEntityProvider {

    public static final MapCodec<StandMarkerBlock> CODEC = createCodec(StandMarkerBlock::new);
    //? if <=1.21.1 {
    /*public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    *///?} else {
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    //?}

    public StandMarkerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
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
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StandMarkerBlockEntity(pos, state);
    }
}
