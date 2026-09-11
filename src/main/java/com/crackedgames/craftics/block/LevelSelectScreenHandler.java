package com.crackedgames.craftics.block;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.screen.ScreenHandler;

public class LevelSelectScreenHandler extends ScreenHandler {

    private final int highestLevelUnlocked;
    private final int branchChoice;
    private final String discoveredBiomes;
    private final boolean metAnyTrader;
    private final boolean metAnyBarterer;
    private final int ngPlusLevel;
    private final boolean ngPlusAvailable;

    /**
     * Data record sent from server to client when the screen opens.
     *
     * <p>{@code metAnyTrader} / {@code metAnyBarterer} gate the Trading Hall and Bartering Station
     * buttons: a hall you have never met a merchant for would just be an empty building, so the
     * button stays hidden until the island meets its first one at a run event.
     *
     * <p>{@code ngPlusLevel} is the island's real cycle count. The screen used to infer it from
     * {@code highestLevelUnlocked / totalBiomes}, which was only ever right by accident: a cycle
     * resets the frontier to 1, so the derived number was 0 for the whole of every NG+ run, and
     * now that a cleared campaign holds its unlocks until the player opts in it would read a
     * finished NG+0 island as NG+1. The server knows the answer, so it sends it.
     *
     * <p>{@code ngPlusAvailable} is the island's standing offer to advance a cycle - raised by
     * clearing the final boss, cleared by taking it. It gates the NG+ button.
     */
    public record LevelSelectData(int highestLevelUnlocked, int branchChoice, String discoveredBiomes,
                                  boolean metAnyTrader, boolean metAnyBarterer,
                                  int ngPlusLevel, boolean ngPlusAvailable) {
        // Hand-written rather than PacketCodec.tuple: tuple() tops out at six field pairs and
        // this record has seven. Writing it out also drops the version fork this block used to
        // carry (PacketCodecs.BOOL was renamed BOOLEAN in 1.21.4) - the buffer's own
        // read/write methods are spelled the same on every version we build for.
        public static final PacketCodec<RegistryByteBuf, LevelSelectData> PACKET_CODEC =
            PacketCodec.of(LevelSelectData::encode, LevelSelectData::decode);

        private void encode(RegistryByteBuf buf) {
            buf.writeInt(highestLevelUnlocked);
            buf.writeInt(branchChoice);
            buf.writeString(discoveredBiomes);
            buf.writeBoolean(metAnyTrader);
            buf.writeBoolean(metAnyBarterer);
            buf.writeInt(ngPlusLevel);
            buf.writeBoolean(ngPlusAvailable);
        }

        private static LevelSelectData decode(RegistryByteBuf buf) {
            int highest = buf.readInt();
            int branch = buf.readInt();
            String discovered = buf.readString();
            boolean trader = buf.readBoolean();
            boolean barterer = buf.readBoolean();
            int ngPlus = buf.readInt();
            boolean ngPlusOffer = buf.readBoolean();
            return new LevelSelectData(highest, branch, discovered, trader, barterer,
                ngPlus, ngPlusOffer);
        }
    }

    // Server constructor
    public LevelSelectScreenHandler(int syncId, PlayerInventory playerInventory,
                                      int highestLevelUnlocked, int branchChoice, String discoveredBiomes,
                                      boolean metAnyTrader, boolean metAnyBarterer,
                                      int ngPlusLevel, boolean ngPlusAvailable) {
        super(ModScreenHandlers.LEVEL_SELECT_SCREEN_HANDLER, syncId);
        this.highestLevelUnlocked = highestLevelUnlocked;
        this.branchChoice = branchChoice;
        this.discoveredBiomes = discoveredBiomes;
        this.metAnyTrader = metAnyTrader;
        this.metAnyBarterer = metAnyBarterer;
        this.ngPlusLevel = ngPlusLevel;
        this.ngPlusAvailable = ngPlusAvailable;
    }

    // Client constructor (from ExtendedScreenHandlerType)
    public LevelSelectScreenHandler(int syncId, PlayerInventory playerInventory, LevelSelectData data) {
        this(syncId, playerInventory, data.highestLevelUnlocked(), data.branchChoice(),
            data.discoveredBiomes(), data.metAnyTrader(), data.metAnyBarterer(),
            data.ngPlusLevel(), data.ngPlusAvailable());
    }

    public int getHighestLevelUnlocked() { return highestLevelUnlocked; }
    public int getBranchChoice() { return branchChoice; }
    public String getDiscoveredBiomes() { return discoveredBiomes; }
    /** Whether this island has met at least one villager trader - unlocks the Trading Hall. */
    public boolean hasMetAnyTrader() { return metAnyTrader; }
    /** Whether this island has met at least one piglin barterer - unlocks the Bartering Station. */
    public boolean hasMetAnyBarterer() { return metAnyBarterer; }
    /** This island's completed New Game+ cycles. 0 on a first playthrough. */
    public int getNgPlusLevel() { return ngPlusLevel; }
    /** Whether the island has cleared the campaign and not yet taken the next NG+ cycle. */
    public boolean isNgPlusAvailable() { return ngPlusAvailable; }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
