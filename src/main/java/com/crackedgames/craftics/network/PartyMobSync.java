package com.crackedgames.craftics.network;

import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * Server→client sync of a player's battle-party mob UUIDs. Sent on join, when
 * the party changes (Shift+Right-Click toggle), and after a combat run so the
 * client can keep its "Active In Party" labels accurate.
 *
 * @since 0.3.0
 */
public final class PartyMobSync {

    private PartyMobSync() {}

    /** Send {@code player} their current battle-party membership. */
    public static void sync(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        CrafticsSavedData data = CrafticsSavedData.get(world);
        StringBuilder sb = new StringBuilder();
        for (UUID id : data.getPlayerData(player.getUuid()).getPartyMobs()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(id.toString());
        }
        ServerPlayNetworking.send(player, new PartyMobsSyncPayload(sb.toString()));
    }
}
