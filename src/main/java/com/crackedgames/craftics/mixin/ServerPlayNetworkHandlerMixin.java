package com.crackedgames.craftics.mixin;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppress the Q / Ctrl+Q drop-item key during combat by intercepting the
 * {@link PlayerActionC2SPacket} at the network handler level. We block here
 * instead of inside {@code PlayerEntity} because:
 *
 *   1. The vanilla method names for the drop helpers vary across yarn versions
 *      (mixins by name break on minor MC updates).
 *   2. Vanilla calls {@code Inventory.removeStack} BEFORE actually spawning the
 *      dropped item, so cancelling the spawn alone deletes the held stack —
 *      cancelling the packet here prevents the inventory mutation entirely.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void craftics$blockDropKeyDuringCombat(PlayerActionC2SPacket packet, CallbackInfo ci) {
        PlayerActionC2SPacket.Action action = packet.getAction();
        if (action != PlayerActionC2SPacket.Action.DROP_ITEM
                && action != PlayerActionC2SPacket.Action.DROP_ALL_ITEMS) {
            return;
        }
        if (player == null) return;
        var cm = com.crackedgames.craftics.combat.CombatManager.get(player);
        if (cm.isActive()) {
            ci.cancel();
        }
    }
}
