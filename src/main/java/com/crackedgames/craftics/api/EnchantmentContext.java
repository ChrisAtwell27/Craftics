package com.crackedgames.craftics.api;

import net.minecraft.server.network.ServerPlayerEntity;

public class EnchantmentContext {
    private final int level;
    private final ServerPlayerEntity player;
    private final StatModifiers modifiers;

    public EnchantmentContext(int level, ServerPlayerEntity player, StatModifiers modifiers) {
        this.level = level;
        this.player = player;
        this.modifiers = modifiers;
    }

    public int getLevel() { return level; }
    public ServerPlayerEntity getPlayer() { return player; }
    public StatModifiers getModifiers() { return modifiers; }
}
