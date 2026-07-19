package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.util.RegistryHealthScanner;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the registry health scan at the HEAD of every MinecraftClient.disconnect
 * overload - guaranteed to execute before Fabric registry-sync's injected
 * disconnect handler calls unmap(), which is where a registry broken by some
 * other mod crashes the game on quit. The Fabric DISCONNECT event is not a
 * reliable pre-unmap hook for this (ordering differs by disconnect path);
 * injecting at the same method Fabric injects into is.
 *
 * <p>Name-only target deliberately matches all disconnect overloads (the no-arg
 * one delegates to the screen one, so a quit can scan twice). The scan is
 * read-only and takes a few ms; {@code craftics$scanGuard} keeps the duplicate
 * out of the log.
 */
@Mixin(MinecraftClient.class)
public abstract class DisconnectScanMixin {

    @Unique
    private static long craftics$lastScanMs = 0;

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void craftics$scanBeforeUnmap(CallbackInfo ci) {
        long now = System.currentTimeMillis();
        if (now - craftics$lastScanMs < 2000) return;
        craftics$lastScanMs = now;
        if (RegistryHealthScanner.scan("pre-unmap") > 0) {
            // Null holes ahead of the unmap iteration = guaranteed crash. Compact
            // them out; the unmap that runs next rebuilds every raw id from the
            // stored defaults anyway, so the shifted ids never reach gameplay.
            RegistryHealthScanner.repairHoles("pre-unmap");
        }
    }
}
