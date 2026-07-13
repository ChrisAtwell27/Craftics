package com.crackedgames.craftics.mixin.client.spellengine;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the Spell Engine HUD - the spell bar, its cooldown swirls, its keybind hints -
 * while Craftics combat is running.
 *
 * <p>{@link SpellHotbarSuppressMixin} already stops the spells themselves from firing.
 * This stops the icons that advertise them from being drawn, so nothing on screen offers
 * the player an action the battle will not let them take.
 *
 * <p>{@link Pseudo} plus {@code require = 0} means this silently does nothing when Spell
 * Engine is absent.
 */
@Pseudo
@Mixin(targets = "net.spell_engine.client.gui.HudRenderHelper", remap = false)
public abstract class SpellHudSuppressMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;F)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void craftics$hideSpellHudInCombat(DrawContext context, float tickDelta,
                                                      CallbackInfo ci) {
        if (CombatState.isInCombat() || CombatState.isInScene()) ci.cancel();
    }
}
