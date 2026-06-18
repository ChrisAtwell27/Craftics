package com.crackedgames.craftics.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for a {@link NpcMarkerBlock}. Holds the booth's occupant id:
 * a dedicated merchant id (e.g. {@code "craftics:weaponsmith"}) or an overflow
 * wildcard ({@code "villager:addon"} / {@code "piglin:addon"}).
 *
 * <p>The NPC marker is the booth's identity carrier: it sits inside the booth's
 * stand-corner rectangle, fixes where the NPC stands, and stores the occupant.
 * {@link com.crackedgames.craftics.scene.SceneScanner} reads this during a scan.
 */
public class NpcMarkerBlockEntity extends BlockEntity {

    private static final String OCCUPANT_KEY = "Occupant";

    private String occupant = "";

    public NpcMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.NPC_MARKER_BLOCK_ENTITY, pos, state);
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
        // 1.21.5 changed getString(key) to return Optional<String> and added the
        // two-arg getString(key, default) overload; <=1.21.4 only has the single-arg
        // form that returns String directly.
        //? if <=1.21.4 {
        occupant = nbt.getString(OCCUPANT_KEY);
        //?} else {
        /*occupant = nbt.getString(OCCUPANT_KEY, "");
        *///?}
    }
}
