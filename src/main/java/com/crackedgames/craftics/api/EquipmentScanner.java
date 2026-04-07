package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.StatModifiers;
import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface EquipmentScanner {
    StatModifiers scan(ServerPlayerEntity player);
}
