package com.crackedgames.craftics.mixin;

import com.crackedgames.craftics.component.CrafticsComponents;
import com.crackedgames.craftics.component.DeathProtectionComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void craftics$blockItemDropDuringCombat(net.minecraft.item.ItemStack stack, boolean throwRandomly,
                                                     boolean retainOwnership, CallbackInfoReturnable<net.minecraft.entity.ItemEntity> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            var cm = com.crackedgames.craftics.combat.CombatManager.get(serverPlayer);
            if (cm.isActive()) {
                cir.setReturnValue(null);
            }
        }
    }

    /**
     * Suppress the Q / Ctrl+Q drop key during combat. We block at the
     * dropSelectedItem level (rather than just dropItem) so the inventory
     * is never emptied — vanilla calls removeStack BEFORE dropItem, which
     * would otherwise destroy the held item even when the drop is cancelled.
     */
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void craftics$blockSelectedItemDropDuringCombat(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            var cm = com.crackedgames.craftics.combat.CombatManager.get(serverPlayer);
            if (cm.isActive()) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
    private void craftics$protectInventoryWithRecoveryCompass(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (serverPlayer.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;
        if (!DeathProtectionComponent.hasRecoveryCompass(serverPlayer)) return;

        var deathProtection = CrafticsComponents.DEATH_PROTECTION.get(serverPlayer);
        if (!deathProtection.armFromInventory(serverPlayer)) return;

        // Particles + sound for recovery compass activation
        if (serverPlayer.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL,
                serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                40, 0.5, 0.8, 0.5, 0.02);
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                serverPlayer.getX(), serverPlayer.getY() + 1.5, serverPlayer.getZ(),
                25, 0.4, 0.6, 0.4, 0.05);
            serverPlayer.getWorld().playSound(null, serverPlayer.getBlockPos(),
                net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.2f);
        }
        serverPlayer.sendMessage(net.minecraft.text.Text.literal("\u00a76\u00a7l\u2728 Recovery Compass activated! \u00a7rYour inventory was saved."), false);
        ci.cancel();
    }
}