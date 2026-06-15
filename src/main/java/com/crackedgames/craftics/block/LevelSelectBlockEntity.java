package com.crackedgames.craftics.block;

import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class LevelSelectBlockEntity extends BlockEntity
        implements ExtendedScreenHandlerFactory<LevelSelectScreenHandler.LevelSelectData> {

    public LevelSelectBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.LEVEL_SELECT_BLOCK_ENTITY, pos, state);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("gui.craftics.level_select.title");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        CrafticsSavedData data = getData();
        // Resolve to the party leader (or this player if solo) so every party
        // member sees the SAME level / branch / discovered biomes on the level
        // select block. Reading the visiting player's own data was making
        // player 1 see Desert while player 2 sees Tundra - leader's data is
        // the shared run state every member queues into.
        java.util.UUID owner = data.getEffectiveWorldOwner(player.getUuid());
        CrafticsSavedData.PlayerData pd = data.getPlayerData(owner);
        pd.initBranchIfNeeded();
        data.markDirty();
        return new LevelSelectScreenHandler(syncId, playerInventory,
            pd.highestBiomeUnlocked, pd.branchChoice, pd.discoveredBiomes);
    }

    @Override
    public LevelSelectScreenHandler.LevelSelectData getScreenOpeningData(ServerPlayerEntity player) {
        CrafticsSavedData data = getData();
        java.util.UUID owner = data.getEffectiveWorldOwner(player.getUuid());
        CrafticsSavedData.PlayerData pd = data.getPlayerData(owner);
        pd.initBranchIfNeeded();
        data.markDirty();
        return new LevelSelectScreenHandler.LevelSelectData(
            pd.highestBiomeUnlocked, pd.branchChoice, pd.discoveredBiomes);
    }

    private CrafticsSavedData getData() {
        if (world instanceof ServerWorld serverWorld) {
            return CrafticsSavedData.get(serverWorld);
        }
        // Fallback - shouldn't happen since this runs server-side
        return new CrafticsSavedData();
    }
}
