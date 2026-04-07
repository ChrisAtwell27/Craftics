package com.crackedgames.craftics.api;

@FunctionalInterface
public interface EnchantmentEffectHandler {
    void apply(EnchantmentContext ctx);
}
