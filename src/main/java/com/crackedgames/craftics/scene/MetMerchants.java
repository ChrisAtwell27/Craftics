package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Per-island "merchants you have met in events" tracking. Booth occupants in the
 *  trading hall / bartering station are limited to these; nothing appears by default. */
public final class MetMerchants {
    private MetMerchants() {}

    /** Record a trader type (TraderSystem.TraderType.name()) as met for the member's island. */
    public static void recordTrader(ServerWorld world, UUID anyMemberUuid, String traderTypeName) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        UUID owner = data.getEffectiveWorldOwner(anyMemberUuid);
        if (data.getPlayerData(owner).metTraders.add(traderTypeName)) data.markDirty();
    }

    /** Record a barter category id (BarterCategory.id()) as met for the member's island. */
    public static void recordBarterer(ServerWorld world, UUID anyMemberUuid, String barterCategoryId) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        UUID owner = data.getEffectiveWorldOwner(anyMemberUuid);
        if (data.getPlayerData(owner).metBarterers.add(barterCategoryId)) data.markDirty();
    }

    /** Pure filter: candidates whose id is in the met set, original order kept. */
    public static <T> List<T> filterMet(List<T> candidates, Set<String> met, Function<T, String> idOf) {
        List<T> out = new ArrayList<>();
        for (T c : candidates) if (met.contains(idOf.apply(c))) out.add(c);
        return out;
    }
}
