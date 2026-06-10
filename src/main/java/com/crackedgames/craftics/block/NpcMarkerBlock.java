package com.crackedgames.craftics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
//? if <=1.21.1 {
/*import net.minecraft.state.property.DirectionProperty;
*///?} else {
import net.minecraft.state.property.EnumProperty;
//?}
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Marker block placed in a scene schematic to define where a booth's NPC stands
 * and which way it faces. {@link com.crackedgames.craftics.scene.SceneScanner}
 * reads its position and {@code FACING}; {@link com.crackedgames.craftics.scene.SceneLayoutResolver}
 * pairs each NPC marker to the nearest stand. Pure marker, no behavior.
 */
public class NpcMarkerBlock extends Block {

    public static final MapCodec<NpcMarkerBlock> CODEC = createCodec(NpcMarkerBlock::new);
    //? if <=1.21.1 {
    /*public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    *///?} else {
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    //?}

    public NpcMarkerBlock(Settings settings) {
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
}
