package com.crackedgames.craftics.block;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.screen.ScreenHandler;

public class LevelSelectScreenHandler extends ScreenHandler {

    private final int highestLevelUnlocked;
    private final int branchChoice;
    private final String discoveredBiomes;

    // Data record sent from server to client when screen opens
    public record LevelSelectData(int highestLevelUnlocked, int branchChoice, String discoveredBiomes) {
        public static final PacketCodec<RegistryByteBuf, LevelSelectData> PACKET_CODEC =
            PacketCodec.tuple(
                PacketCodecs.INTEGER, LevelSelectData::highestLevelUnlocked,
                PacketCodecs.INTEGER, LevelSelectData::branchChoice,
                PacketCodecs.STRING, LevelSelectData::discoveredBiomes,
                LevelSelectData::new
            );
    }

    // Server constructor
    public LevelSelectScreenHandler(int syncId, PlayerInventory playerInventory,
                                      int highestLevelUnlocked, int branchChoice, String discoveredBiomes) {
        super(ModScreenHandlers.LEVEL_SELECT_SCREEN_HANDLER, syncId);
        this.highestLevelUnlocked = highestLevelUnlocked;
        this.branchChoice = branchChoice;
        this.discoveredBiomes = discoveredBiomes;
    }

    // Client constructor (from ExtendedScreenHandlerType)
    public LevelSelectScreenHandler(int syncId, PlayerInventory playerInventory, LevelSelectData data) {
        this(syncId, playerInventory, data.highestLevelUnlocked(), data.branchChoice(), data.discoveredBiomes());
    }

    public int getHighestLevelUnlocked() { return highestLevelUnlocked; }
    public int getBranchChoice() { return branchChoice; }
    public String getDiscoveredBiomes() { return discoveredBiomes; }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
