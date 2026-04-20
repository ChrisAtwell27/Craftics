package com.crackedgames.craftics.client.vfx;

import com.crackedgames.craftics.client.CombatVisualEffects;
import com.crackedgames.craftics.network.VfxClientPayload;
import com.crackedgames.craftics.vfx.VfxPrimitive;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Receives VfxClientPayload and delegates to CombatVisualEffects / HitPauseState. */
public final class VfxClientDispatcher {
    private VfxClientDispatcher() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(VfxClientPayload.ID, (payload, ctx) -> {
            ctx.client().execute(() -> handle(payload));
        });
    }

    private static void handle(VfxClientPayload payload) {
        for (VfxClientPayload.ClientPrim p : payload.primitives()) {
            switch (p) {
                case VfxClientPayload.ClientPrim.Shake s ->
                    CombatVisualEffects.triggerShakeTimed(s.intensity(), s.durationTicks());
                case VfxClientPayload.ClientPrim.ScreenFlash s ->
                    CombatVisualEffects.flashWithColor(s.argb(), s.durationTicks());
                case VfxClientPayload.ClientPrim.HitPause s ->
                    HitPauseState.freeze(s.freezeTicks());
                case VfxClientPayload.ClientPrim.FloatingText s ->
                    CombatVisualEffects.addFloatingTextAt(s.x(), s.y(), s.z(), s.text(), s.color(), s.lifetimeTicks());
                case VfxClientPayload.ClientPrim.Vignette s -> {
                    VfxPrimitive.VignetteType type = VfxPrimitive.VignetteType.values()[s.typeOrdinal()];
                    switch (type) {
                        case POISON, BURNING, BLINDNESS -> { /* driven by CombatState already */ }
                        case EXECUTE -> CombatVisualEffects.flashWithColor(0x88FF2222, s.durationTicks());
                        case FROST   -> CombatVisualEffects.flashWithColor(0x88AAE0FF, s.durationTicks());
                    }
                }
            }
        }
    }
}
