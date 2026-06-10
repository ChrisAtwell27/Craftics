package com.crackedgames.craftics.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for a {@link StandMarkerBlock}. Holds the booth's occupant id:
 * a dedicated merchant id (e.g. {@code "craftics:weaponsmith"}) or an overflow
 * wildcard ({@code "villager:addon"} / {@code "piglin:addon"}).
 * {@link com.crackedgames.craftics.scene.SceneScanner} reads this during a scan.
 */
public class StandMarkerBlockEntity extends BlockEntity {

    private static final String OCCUPANT_KEY = "Occupant";

    private String occupant = "";

    public StandMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.STAND_MARKER_BLOCK_ENTITY, pos, state);
    }

    public String getOccupant() {
        return occupant;
    }

    public void setOccupant(String occupant) {
        this.occupant = occupant == null ? "" : occupant;
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString(OCCUPANT_KEY, occupant);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        //? if <=1.21.1 {
        occupant = nbt.getString(OCCUPANT_KEY);
        //?} else {
        /*occupant = nbt.getString(OCCUPANT_KEY, "");
        *///?}
    }
}
