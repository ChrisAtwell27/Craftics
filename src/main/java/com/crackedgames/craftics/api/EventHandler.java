package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.EventManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

@FunctionalInterface
public interface EventHandler {
    void execute(List<ServerPlayerEntity> participants, ServerWorld world,
                 EventManager eventManager);
}
