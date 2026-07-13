package com.crackedgames.craftics.mixin.client.spellengine;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Silences the Spell Engine spell hotbar during Craftics combat.
 *
 * <p>Spell Engine (which Simply Swords builds its weapon spells on) drives everything from
 * one place: {@code SpellHotbar.update} rebuilds the list of castable slots each tick, and
 * every other entry point - the key handlers and the HUD widget - reads that list. Empty
 * the list and refuse to rebuild it, and no key casts anything and the spell bar has
 * nothing left to draw.
 *
 * <p>That is a deliberately small lever. Craftics combat is turn-based and spends its own
 * action points; a real-time spell fired from the hotbar bypasses the whole system. Outside
 * combat the mod is left entirely alone.
 *
 * <p>{@link Pseudo} plus a {@code require = 0} injector means this silently does nothing
 * when Spell Engine is not installed - there is no compile-time dependency on it, and the
 * mixin targets only vanilla types.
 */
@Pseudo
@Mixin(targets = "net.spell_engine.client.input.SpellHotbar", remap = false)
public abstract class SpellHotbarSuppressMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void craftics$suppressSpellsInCombat(ClientPlayerEntity player, GameOptions options,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!CombatState.isInCombat() && !CombatState.isInScene()) return;
        // Refusing to rebuild the slots is not enough on its own: the key handlers walk
        // whatever list the last pre-combat tick left behind. Empty it, and every cast
        // path and the HUD widget alike find nothing to act on.
        craftics$clearSlots();
        cir.setReturnValue(false);
    }

    /**
     * Empty Spell Engine's {@code slots} list. Reached reflectively rather than shadowed:
     * a {@code @Shadow} against a mod we do not compile against would abort mixin
     * application - and take the client with it - the day that field is renamed. Reflection
     * degrades instead to "spells still fire", which is a bug, not a crash.
     */
    private void craftics$clearSlots() {
        try {
            var field = getClass().getField("slots");
            if (field.get(this) instanceof List<?> slots) slots.clear();
        } catch (ReflectiveOperationException | ClassCastException | UnsupportedOperationException e) {
            // Spell Engine changed shape. The cancel above still blocks this tick's rebuild.
        }
    }
}
