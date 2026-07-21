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
 *      dropped item, so cancelling the spawn alone deletes the held stack -
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

        // The Move item must never be droppable, in or out of combat, and it's
        // always the held/selected hotbar stack when a drop key is pressed
        // (MoveSlotManager locks it to a hotbar slot).
        boolean droppingMoveItem = com.crackedgames.craftics.item.MoveSlotManager.isMoveStack(player.getMainHandStack());

        // Resolve through getActiveCombat (leader-resolved), NOT get(player):
        // non-leader party members share the leader's CombatManager, and their
        // own per-player instance is always inactive, so get(player).isActive()
        // never blocks a non-leader's drop. getActiveCombat never returns null.
        boolean inActiveCombat = com.crackedgames.craftics.combat.CombatManager
            .getActiveCombat(player.getUuid()).isActive();

        if (droppingMoveItem || inActiveCombat) {
            ci.cancel();
            // The client optimistically removes the dropped stack from its
            // inventory view before the server packet round-trips. Cancelling
            // here stops the server-side removeStack, but without a resync the
            // client still shows the item gone until some unrelated sync
            // happens. Force a full content resync so the item reappears.
            // ScreenHandler.syncState() has an identical signature on every
            // supported shard (verified 1.21.1/1.21.3/1.21.4/1.21.5 via javap),
            // so no Stonecutter conditional is needed here.
            player.playerScreenHandler.syncState();
        }
    }
}
