package com.crackedgames.craftics.client.hints;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public record HintContext(
        MinecraftClient client,
        ClientPlayerEntity player,
        boolean inHub,
        boolean inCombat,
        boolean isPlayerTurn,
        int apRemaining,
        int apMax,
        int speedRemaining,
        int speedMax,
        int hpCurrent,
        int hpMax,
        int turnNumber,
        boolean tookActionThisTurn,
        boolean holdingMoveFeather,
        boolean hasFoodOrPotionInHotbar,
        boolean isAnyScreenOpen,
        long now,
        long lastInputAtMs
) {}
