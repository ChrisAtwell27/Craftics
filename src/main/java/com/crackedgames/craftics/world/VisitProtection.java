package com.crackedgames.craftics.world;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/** Look-only rule for island visitors: walking is free, everything else is not. */
public final class VisitProtection {
    private VisitProtection() {}

    public static boolean isForeignVisitor(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return false;
        ServerWorld w = (ServerWorld) sp.getEntityWorld();
        if (!IslandDimensions.isIslandWorld(w)) return false;
        UUID islandOwner = IslandDimensions.ownerOf(w);
        if (islandOwner == null) return false;
        CrafticsSavedData data = CrafticsSavedData.get(w);
        if (data.getEffectiveWorldOwner(sp.getUuid()).equals(islandOwner)) return false; // own island / party
        return true; // anyone else on a foreign island is look-only
    }
}
