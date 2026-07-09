package com.crackedgames.craftics.world;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.PlayerProgression;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/**
 * Hardcore islands: created via {@code /new hardcore}. When the whole run party is
 * defeated in combat, the island dimension is deleted from disk, the owner's island
 * record is reset, and every run participant loses inventory, XP, and combat
 * progression. Everyone lands in the central lobby. Guests merely visiting are
 * evacuated unharmed. See docs/superpowers/specs/2026-07-09-hardcore-islands-design.md.
 */
public final class HardcoreIslands {

    private HardcoreIslands() {}

    /** True when {@code owner}'s existing island is flagged hardcore. */
    public static boolean isHardcore(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return false;
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        return data.hasPersonalWorld(owner) && data.getPlayerData(owner).hardcoreIsland;
    }

    /**
     * The full hardcore wipe. Call AFTER combat teardown (endCombat) so no combat
     * state references the dimension. Order matters: players out first, dim delete last.
     */
    public static void wipe(MinecraftServer server, UUID owner,
                            List<ServerPlayerEntity> participants) {
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());

        // 1. Wipe online participants; flag offline ones for wipe-on-join.
        for (ServerPlayerEntity p : participants) {
            if (p == null) continue;
            if (p.isRemoved() || p.isDisconnected()) {
                data.addPendingHardcoreWipe(p.getUuid());
                continue;
            }
            wipeParticipant(p);
            p.sendMessage(Text.literal(
                "§4§l☠ HARDCORE DEATH §r§7- the island and everything on it is gone."), false);
        }

        // 2. Evacuate EVERYONE still inside the island dim (participants and
        //    visitors alike) to the lobby before the dimension dies. Visitors
        //    keep their items - only run participants were wiped above.
        ServerWorld island = IslandDimensions.getLoaded(server, owner);
        if (island != null) {
            for (ServerPlayerEntity p : List.copyOf(island.getPlayers())) {
                HubTeleports.toLobby(p);
                p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            }
        }

        // 3. Reset the owner's island record (frees the world slot -> /new works).
        data.resetPlayerData(owner);

        // 4. Delete the island dimension's region files from disk.
        IslandDimensions.delete(server, owner);
        CrafticsMod.LOGGER.info("[Hardcore] island of {} wiped ({} participants)",
            owner, participants.size());
    }

    /** Inventory + XP + combat progression gone; healed; guide book handed back. */
    public static void wipeParticipant(ServerPlayerEntity p) {
        p.getInventory().clear(); // main + armor + offhand
        p.setExperienceLevel(0);
        p.setExperiencePoints(0);
        p.clearStatusEffects();
        p.setHealth(p.getMaxHealth());
        MinecraftServer server = p.getServer();
        if (server != null) {
            // Fresh level-1 stats; achievements carry over (account-wide).
            PlayerProgression.get(server.getOverworld()).resetForInfiniteRun(p.getUuid());
        }
        p.giveItemStack(new net.minecraft.item.ItemStack(
            com.crackedgames.craftics.item.ModItems.GUIDE_BOOK));
    }

    /**
     * Disconnect-dodge handling: a participant who logged out before the wipe gets
     * it on next join. MUST run AFTER InfiniteRunManager.onPlayerJoin so a restored
     * infinite stash doesn't survive the wipe.
     */
    public static void checkPendingWipeOnJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        if (!data.consumePendingHardcoreWipe(player.getUuid())) return;
        wipeParticipant(player);
        HubTeleports.toLobby(player);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.sendMessage(Text.literal(
            "§4§l☠ HARDCORE DEATH §r§7- your party fell while you were away. Everything is gone."), false);
    }
}
