package com.crackedgames.craftics.compat.artifacts;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Thin reflection shim over {@code artifacts.equipment.EquipmentHelper}.
 * Resolved once at class load; all subsequent calls are a single
 * {@link Method#invoke(Object, Object...)}.
 * <p>
 * We use reflection instead of a compile-time {@code modCompileOnly} dependency so the
 * Craftics build stays slim and the compat layer degrades cleanly if the Artifacts API
 * ever changes its package layout.
 */
public final class ArtifactsReflect {

    private static final Method ITERATE_EQUIPMENT;
    private static final boolean AVAILABLE;

    static {
        Method m = null;
        boolean ok = false;
        Throwable failure = null;
        try {
            Class<?> cls = Class.forName("artifacts.equipment.EquipmentHelper");
            m = cls.getMethod("iterateEquipment", LivingEntity.class, Consumer.class);
            ok = true;
        } catch (Throwable t) {
            failure = t;
        }
        ITERATE_EQUIPMENT = m;
        AVAILABLE = ok;
        if (ok) {
            CrafticsMod.LOGGER.info("[Craftics × Artifacts] Resolved EquipmentHelper.iterateEquipment via reflection");
        } else if (failure != null) {
            CrafticsMod.LOGGER.warn("[Craftics × Artifacts] Could not resolve EquipmentHelper.iterateEquipment: {}", failure.toString());
        }
    }

    private ArtifactsReflect() {}

    static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Invokes {@code EquipmentHelper.iterateEquipment} with the given visitor.
     * The visitor is invoked for each ItemStack the player currently has equipped in any
     * slot provider (vanilla armor, Trinkets, Accessories, or Curios — whichever is active).
     */
    static void iterateEquipment(LivingEntity entity, Consumer<ItemStack> visitor) {
        if (!AVAILABLE) return;
        try {
            ITERATE_EQUIPMENT.invoke(null, entity, visitor);
        } catch (Throwable t) {
            // Don't let a reflection hiccup break combat.
            CrafticsMod.LOGGER.debug("[Craftics × Artifacts] iterateEquipment failed", t);
        }
    }

    /**
     * Force an {@code artifacts.entity.MimicEntity} into its dormant (chest-form)
     * state via reflection. The MimicEntity has a custom {@code tick()} override
     * that increments {@code ticksInAir} and drives its hop/attack logic even when
     * {@code setAiDisabled(true)} — that flag only gates the vanilla
     * {@code GoalSelector}, not an entity's own tick. Forcing dormant = true
     * short-circuits the custom tick so Craftics' turn-based controller fully
     * owns the mimic's position and animation.
     * <p>
     * No-op for non-mimic entities; no-op if the Artifacts mod isn't loaded.
     *
     * @return true if the setDormant call succeeded
     */
    public static boolean trySetDormant(net.minecraft.entity.Entity entity, boolean dormant) {
        if (entity == null) return false;
        try {
            Class<?> mimicCls = Class.forName("artifacts.entity.MimicEntity");
            if (!mimicCls.isInstance(entity)) return false;
            Method setDormant = mimicCls.getMethod("setDormant", boolean.class);
            setDormant.invoke(entity, dormant);
            return true;
        } catch (Throwable t) {
            CrafticsMod.LOGGER.debug("[Craftics × Artifacts] setDormant failed: {}", t.toString());
            return false;
        }
    }
}
