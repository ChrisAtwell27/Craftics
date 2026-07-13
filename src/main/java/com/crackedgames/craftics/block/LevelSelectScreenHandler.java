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
    private final boolean metAnyTrader;
    private final boolean metAnyBarterer;

    /**
     * Data record sent from server to client when the screen opens.
     *
     * <p>{@code metAnyTrader} / {@code metAnyBarterer} gate the Trading Hall and Bartering Station
     * buttons: a hall you have never met a merchant for would just be an empty building, so the
     * button stays hidden until the island meets its first one at a run event.
     */
    public record LevelSelectData(int highestLevelUnlocked, int branchChoice, String discoveredBiomes,
                                  boolean metAnyTrader, boolean metAnyBarterer) {
        // PacketCodecs.BOOL was renamed to BOOLEAN in 1.21.4.
        //? if <=1.21.3 {
        public static final PacketCodec<RegistryByteBuf, LevelSelectData> PACKET_CODEC =
            PacketCodec.tuple(
                PacketCodecs.INTEGER, LevelSelectData::highestLevelUnlocked,
                PacketCodecs.INTEGER, LevelSelectData::branchChoice,
                PacketCodecs.STRING, LevelSelectData::discoveredBiomes,
                PacketCodecs.BOOL, LevelSelectData::metAnyTrader,
                PacketCodecs.BOOL, LevelSelectData::metAnyBarterer,
                LevelSelectData::new
            );
        //?} else {
        /*public static final PacketCodec<RegistryByteBuf, LevelSelectData> PACKET_CODEC =
            PacketCodec.tuple(
                PacketCodecs.INTEGER, LevelSelectData::highestLevelUnlocked,
                PacketCodecs.INTEGER, LevelSelectData::branchChoice,
                PacketCodecs.STRING, LevelSelectData::discoveredBiomes,
                PacketCodecs.BOOLEAN, LevelSelectData::metAnyTrader,
                PacketCodecs.BOOLEAN, LevelSelectData::metAnyBarterer,
                LevelSelectData::new
            );
        *///?}
    }

    // Server constructor
    public LevelSelectScreenHandler(int syncId, PlayerInventory playerInventory,
                                      int highestLevelUnlocked, int branchChoice, String discoveredBiomes,
                                      boolean metAnyTrader, boolean metAnyBarterer) {
        super(ModScreenHandlers.LEVEL_SELECT_SCREEN_HANDLER, syncId);
        this.highestLevelUnlocked = highestLevelUnlocked;
        this.branchChoice = branchChoice;
        this.discoveredBiomes = discoveredBiomes;
        this.metAnyTrader = metAnyTrader;
        this.metAnyBarterer = metAnyBarterer;
    }

    // Client constructor (from ExtendedScreenHandlerType)
    public LevelSelectScreenHandler(int syncId, PlayerInventory playerInventory, LevelSelectData data) {
        this(syncId, playerInventory, data.highestLevelUnlocked(), data.branchChoice(),
            data.discoveredBiomes(), data.metAnyTrader(), data.metAnyBarterer());
    }

    public int getHighestLevelUnlocked() { return highestLevelUnlocked; }
    public int getBranchChoice() { return branchChoice; }
    public String getDiscoveredBiomes() { return discoveredBiomes; }
    /** Whether this island has met at least one villager trader - unlocks the Trading Hall. */
    public boolean hasMetAnyTrader() { return metAnyTrader; }
    /** Whether this island has met at least one piglin barterer - unlocks the Bartering Station. */
    public boolean hasMetAnyBarterer() { return metAnyBarterer; }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
