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
 * Marker block placed inside a booth's stand-corner rectangle to define where the
 * booth's NPC stands and which way it faces, and to identify the booth: its
 * {@link NpcMarkerBlockEntity} stores the occupant id. {@code FACING} records the
 * NPC's facing; the player walks up one tile in front of the NPC facing it.
 * {@link com.crackedgames.craftics.scene.SceneScanner} reads position, FACING, and
 * occupant during a scan, then replaces the marker with the most common neighboring
 * block so it is invisible in the built scene.
 */
public class NpcMarkerBlock extends Block implements BlockEntityProvider {

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

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new NpcMarkerBlockEntity(pos, state);
    }
}
