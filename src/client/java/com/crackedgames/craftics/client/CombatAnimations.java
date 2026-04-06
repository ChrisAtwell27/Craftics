package com.crackedgames.craftics.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.api.IPlayer;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.jetbrains.annotations.NotNull;

public class CombatAnimations {

    private static boolean wasAnimating = false;
    private static int attackAnimTimer = 0;
    private static ModifierLayer<IAnimation> currentLayer = null;

    // WeakHashMap: survives respawns, gets GC'd with the player
    private static java.util.WeakHashMap<AbstractClientPlayerEntity, ModifierLayer<IAnimation>> layerMap = new java.util.WeakHashMap<>();

    public static void register() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
            var layer = new ModifierLayer<IAnimation>();
            stack.addAnimLayer(42, layer);
            if (player instanceof AbstractClientPlayerEntity acp) {
                layerMap.put(acp, layer);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> getOrCreateLayer(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = layerMap.get(player);
        if (layer != null) {
            currentLayer = layer;
            return layer;
        }
        // Fallback if register callback hasn't fired yet
        if (player instanceof IPlayer iPlayer) {
            AnimationStack stack = iPlayer.getAnimationStack();
            if (stack != null) {
                layer = new ModifierLayer<IAnimation>();
                stack.addAnimLayer(42, layer);
                layerMap.put(player, layer);
                currentLayer = layer;
                return layer;
            }
        }
        return null;
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (!CombatState.isInCombat()) {
            if (wasAnimating) { stopAll(); wasAnimating = false; }
            return;
        }

        int phase = CombatState.getPhase();
        boolean isAnimating = (phase == 2);

        if (isAnimating && !wasAnimating) startWalking(client.player);
        else if (!isAnimating && wasAnimating) stopWalking(client.player);
        wasAnimating = isAnimating;

        ModifierLayer<IAnimation> layer = getOrCreateLayer(client.player);
        if (layer != null && !layer.isActive() && CombatState.isInCombat()) {
            layer.setAnimation(new IdleBreathingAnimation());
        }

        if (attackAnimTimer > 0) {
            attackAnimTimer--;
            if (attackAnimTimer == 0) stopAttack(client.player);
        }
    }

    public static void startWalking(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.LINEAR), new WalkAnimation());
    }

    public static void stopWalking(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.LINEAR), null);
    }

    public static void playAttack(AbstractClientPlayerEntity player) { playWeaponAttack(player); }

    public static void playWeaponAttack(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;

        net.minecraft.item.Item held = player.getMainHandStack().getItem();
        String itemId = net.minecraft.registry.Registries.ITEM.getId(held).getPath();

        IAnimation anim;
        int duration;

        if (itemId.contains("bow") || itemId.contains("crossbow")) {
            anim = new BowDrawAnimation();
            duration = 16;
        } else if (itemId.contains("axe")) {
            anim = new AxeOverheadAnimation();
            duration = 16;
        } else if (itemId.contains("mace")) {
            anim = new MaceSlamAnimation();
            duration = 22;
        } else if (itemId.contains("trident")) {
            anim = new TridentThrustAnimation();
            duration = 14;
        } else {
            anim = new SwordSlashAnimation();
            duration = 14;
        }

        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(1, Ease.LINEAR), anim);
        attackAnimTimer = duration;
    }

    public static void playUseItem(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.LINEAR), new EatAnimation());
        attackAnimTimer = 12;
    }

    public static void playThrow(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(1, Ease.LINEAR), new ThrowAnimation());
        attackAnimTimer = 10;
    }

    private static void stopAttack(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.LINEAR), null);
    }

    public static void stopAll() {
        if (currentLayer != null) currentLayer.setAnimation(null);
    }

    public static void clearCache() {
        currentLayer = null;
        wasAnimating = false;
    }

    private static float easeInOut(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    private static float easeIn(float t) {
        return t * t;
    }

    private static float overshoot(float t, float amount) {
        float s = amount;
        return (t = t - 1) * t * ((s + 1) * t + s) + 1;
    }

    private static float phase(float t, float start, float end) {
        if (t < start) return 0f;
        if (t >= end) return 1f;
        return (t - start) / (end - start);
    }

    private static class IdleBreathingAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return true; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            if (type != TransformType.ROTATION) return v;
            float t = tick + tickDelta;
            float breath = (float) Math.sin(t * 0.12) * 0.06f;
            return switch (modelPart) {
                case "rightArm" -> new Vec3f(v.getX() + breath, v.getY(), v.getZ() + breath * 0.5f);
                case "leftArm"  -> new Vec3f(v.getX() - breath, v.getY(), v.getZ() - breath * 0.5f);
                case "body"     -> new Vec3f(v.getX() + breath * 0.3f, v.getY(), v.getZ());
                default -> v;
            };
        }
    }

    private static class WalkAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return true; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;
            float swing = (float) Math.sin(t * 1.4) * 0.7f;

            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                float bob = Math.abs((float) Math.sin(t * 0.8)) * 0.4f;
                return new Vec3f(v.getX(), v.getY() + bob, v.getZ());
            }
            if (type != TransformType.ROTATION) return v;

            return switch (modelPart) {
                case "rightLeg" -> new Vec3f(v.getX() + swing, v.getY(), v.getZ());
                case "leftLeg"  -> new Vec3f(v.getX() - swing, v.getY(), v.getZ());
                case "rightArm" -> new Vec3f(v.getX() - swing * 0.6f, v.getY(), v.getZ());
                case "leftArm"  -> new Vec3f(v.getX() + swing * 0.6f, v.getY(), v.getZ());
                case "body"     -> new Vec3f(v.getX(), v.getY(), v.getZ() + (float) Math.sin(t * 0.4) * 0.03f);
                default -> v;
            };
        }
    }

    // 14 ticks: windup(0-5) → strike(5-7) → follow-through(7-10) → recovery(10-14)
    private static class SwordSlashAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 14; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;

            float windup    = easeIn(phase(t, 0, 5));
            float strike    = easeOut(phase(t, 5, 7));
            float follow    = easeOut(phase(t, 7, 10));
            float recovery  = easeInOut(phase(t, 10, 14));

            if (type == TransformType.ROTATION) {
                switch (modelPart) {
                    case "rightArm" -> {
                        float armX = windup * 1.2f - strike * 3.5f - follow * 0.3f + recovery * 2.6f;
                        float armZ = windup * 0.4f - strike * 0.8f + recovery * 0.4f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + armZ);
                    }
                    case "leftArm" -> {
                        float lArm = -windup * 0.3f + strike * 0.5f - recovery * 0.2f;
                        return new Vec3f(v.getX() + lArm, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        float twistY = windup * 0.35f - strike * 0.6f - follow * 0.1f + recovery * 0.35f;
                        float leanX = -strike * 0.2f - follow * 0.1f + recovery * 0.3f;
                        return new Vec3f(v.getX() + leanX, v.getY() + twistY, v.getZ());
                    }
                    case "rightLeg" -> {
                        float leg = -strike * 0.25f + recovery * 0.25f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "leftLeg" -> {
                        float leg = strike * 0.15f - recovery * 0.15f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "head" -> {
                        float headY = windup * 0.1f - strike * 0.15f + recovery * 0.05f;
                        return new Vec3f(v.getX(), v.getY() + headY, v.getZ());
                    }
                }
            }
            return v;
        }
    }

    // 16 ticks: stance(0-3) → raise(3-7) → hold(7-8) → slam(8-10) → impact(10-13) → recovery(13-16)
    private static class AxeOverheadAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 16; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;

            float stance   = easeInOut(phase(t, 0, 3));
            float raise    = easeIn(phase(t, 3, 7));
            float hold     = phase(t, 7, 8);
            float slam     = easeOut(phase(t, 8, 10));
            float impact   = phase(t, 10, 13);
            float recovery = easeInOut(phase(t, 13, 16));

            float raised = raise * (1 - slam);
            float slammed = slam;

            if (type == TransformType.ROTATION) {
                switch (modelPart) {
                    case "rightArm" -> {
                        float armX = -stance * 0.3f - raised * 3.2f + slammed * 4.8f
                                   + impact * (float) Math.sin(impact * Math.PI) * -0.4f
                                   - recovery * 1.3f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "leftArm" -> {
                        float armX = -stance * 0.2f - raised * 2.8f + slammed * 4.2f
                                   + impact * (float) Math.sin(impact * Math.PI) * -0.3f
                                   - recovery * 1.1f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        float lean = stance * 0.1f + raised * 0.35f - slammed * 0.5f
                                   - impact * 0.15f + recovery * 0.2f;
                        return new Vec3f(v.getX() + lean, v.getY(), v.getZ());
                    }
                    case "rightLeg" -> {
                        float leg = -slammed * 0.35f - impact * 0.1f + recovery * 0.45f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "leftLeg" -> {
                        float leg = slammed * 0.2f - recovery * 0.2f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "head" -> {
                        float headX = raised * 0.25f - slammed * 0.3f + recovery * 0.05f;
                        return new Vec3f(v.getX() + headX, v.getY(), v.getZ());
                    }
                }
            }
            return v;
        }
    }

    // 22 ticks: crouch(0-3) → jump(3-8) → peak(8-10) → slam(10-12) → impact(12-16) → recovery(16-22)
    private static class MaceSlamAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 22; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;

            float crouch   = easeInOut(phase(t, 0, 3));
            float jump     = easeOut(phase(t, 3, 8));
            float peak     = phase(t, 8, 10);
            float slamDown = easeIn(phase(t, 10, 12));
            float impact   = phase(t, 12, 16);
            float recovery = easeInOut(phase(t, 16, 22));

            float airborne = jump * (1 - slamDown);
            float slammed = slamDown;
            float bounce = impact > 0 ? (float) Math.sin(impact * Math.PI * 3) * 0.25f * (1 - impact) : 0;

            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                float height = -crouch * 0.8f
                    + airborne * 4.0f
                    - slammed * 4.5f
                    + bounce * 1.5f
                    + recovery * 1.3f;
                return new Vec3f(v.getX(), v.getY() + height, v.getZ());
            }

            if (type == TransformType.ROTATION) {
                switch (modelPart) {
                    case "rightArm", "leftArm" -> {
                        float armX = crouch * 0.3f
                            - airborne * 3.2f
                            + slammed * 4.5f
                            + bounce
                            - recovery * 1.3f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        float lean = crouch * 0.25f + airborne * 0.4f - slammed * 0.7f
                            + bounce * 0.4f + recovery * 0.15f;
                        return new Vec3f(v.getX() + lean, v.getY(), v.getZ());
                    }
                    case "rightLeg" -> {
                        float legX = crouch * 0.3f - airborne * 0.5f - slammed * 0.5f + recovery * 0.7f;
                        float legZ = crouch * 0.2f - recovery * 0.2f;
                        return new Vec3f(v.getX() + legX, v.getY(), v.getZ() + legZ);
                    }
                    case "leftLeg" -> {
                        float legX = crouch * 0.3f - airborne * 0.4f + slammed * 0.3f - recovery * 0.2f;
                        float legZ = -crouch * 0.2f + recovery * 0.2f;
                        return new Vec3f(v.getX() + legX, v.getY(), v.getZ() + legZ);
                    }
                    case "head" -> {
                        float headX = airborne * 0.35f - slammed * 0.5f + recovery * 0.15f;
                        return new Vec3f(v.getX() + headX, v.getY(), v.getZ());
                    }
                }
            }
            return v;
        }
    }

    // 16 ticks: nock(0-3) → draw(3-9) → hold(9-10) → release(10-11) → recovery(11-16)
    private static class BowDrawAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 16; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;

            float nock     = easeOut(phase(t, 0, 3));
            float draw     = easeIn(phase(t, 3, 9));
            float hold     = phase(t, 9, 10);
            float release  = easeOut(phase(t, 10, 11));
            float recovery = easeInOut(phase(t, 11, 16));

            float drawn = (nock * 0.3f + draw * 0.7f) * (1 - release);
            float released = release;

            if (type == TransformType.ROTATION) {
                switch (modelPart) {
                    case "rightArm" -> {
                        float armX = -drawn * 2.0f + released * 1.0f + recovery * 1.0f;
                        float armZ = drawn * 0.3f - recovery * 0.3f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + armZ);
                    }
                    case "leftArm" -> {
                        float armX = -nock * 1.4f * (1 - recovery) + released * 0.3f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        float lean = -drawn * 0.15f + released * 0.1f + recovery * 0.05f;
                        float twist = drawn * 0.2f - recovery * 0.2f;
                        return new Vec3f(v.getX() + lean, v.getY() + twist, v.getZ());
                    }
                    case "head" -> {
                        float headY = -drawn * 0.1f + recovery * 0.1f;
                        return new Vec3f(v.getX(), v.getY() + headY, v.getZ());
                    }
                }
            }
            return v;
        }
    }

    // 14 ticks: coil(0-4) → stab(4-6) → hold(6-9) → recovery(9-14)
    private static class TridentThrustAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 14; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;

            float coil     = easeIn(phase(t, 0, 4));
            float stab     = easeOut(phase(t, 4, 6));
            float hold     = phase(t, 6, 9);
            float recovery = easeInOut(phase(t, 9, 14));

            float coiled = coil * (1 - stab);
            float stabbed = stab * (1 - recovery * 0.8f);

            if (type == TransformType.ROTATION) {
                switch (modelPart) {
                    case "rightArm" -> {
                        float armX = coiled * 1.5f - stabbed * 2.2f + recovery * 0.7f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "leftArm" -> {
                        float lArm = -coiled * 0.3f + stabbed * 0.4f - recovery * 0.1f;
                        return new Vec3f(v.getX() + lArm, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        float twistY = coiled * 0.4f - stabbed * 0.5f + recovery * 0.1f;
                        float leanX = -stabbed * 0.25f + recovery * 0.25f;
                        return new Vec3f(v.getX() + leanX, v.getY() + twistY, v.getZ());
                    }
                    case "rightLeg" -> {
                        float leg = coiled * 0.2f - stabbed * 0.4f + recovery * 0.2f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "leftLeg" -> {
                        float leg = -coiled * 0.15f + stabbed * 0.2f - recovery * 0.05f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "head" -> {
                        float headY = coiled * 0.1f - stabbed * 0.15f + recovery * 0.05f;
                        return new Vec3f(v.getX(), v.getY() + headY, v.getZ());
                    }
                }
            }
            return v;
        }
    }

    private static class EatAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 12; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            if (type != TransformType.ROTATION) return v;
            float t = tick + tickDelta;
            float raise = Math.min(1.0f, t / 3.0f);
            float chew = t > 3 ? (float) Math.sin((t - 3) * 2.0) * 0.1f : 0;
            if ("rightArm".equals(modelPart))
                return new Vec3f(v.getX() - 1.8f * raise + chew, v.getY(), v.getZ() - 0.3f * raise);
            if ("head".equals(modelPart))
                return new Vec3f(v.getX() + 0.15f * raise + chew * 0.5f, v.getY(), v.getZ());
            return v;
        }
    }

    // 10 ticks: windup(0-4) → release(4-6) → recovery(6-10)
    private static class ThrowAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 10; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            if (type != TransformType.ROTATION) return v;
            float t = tick + tickDelta;

            float wind     = easeIn(phase(t, 0, 4));
            float release  = easeOut(phase(t, 4, 6));
            float recovery = easeInOut(phase(t, 6, 10));

            float wound = wind * (1 - release);
            float thrown = release;

            switch (modelPart) {
                case "rightArm" -> {
                    float armX = wound * 2.0f - thrown * 3.5f + recovery * 1.5f;
                    return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                }
                case "body" -> {
                    float twistY = wound * 0.3f - thrown * 0.4f + recovery * 0.1f;
                    float leanX = -thrown * 0.2f + recovery * 0.2f;
                    return new Vec3f(v.getX() + leanX, v.getY() + twistY, v.getZ());
                }
                case "rightLeg" -> {
                    float leg = -thrown * 0.25f + recovery * 0.25f;
                    return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                }
            }
            return v;
        }
    }
}
