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

/**
 * Combat animations using PlayerAnimator.
 * Each weapon has distinct anticipation → impact → follow-through → recovery phases.
 */
public class CombatAnimations {

    private static boolean wasAnimating = false;
    private static int attackAnimTimer = 0;
    private static ModifierLayer<IAnimation> currentLayer = null;

    /** Registered layer per-player, keyed by player reference to survive respawns. */
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
        // Return the layer registered via the animation event callback
        ModifierLayer<IAnimation> layer = layerMap.get(player);
        if (layer != null) {
            currentLayer = layer;
            return layer;
        }
        // Fallback: create one manually if the callback hasn't fired yet
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

    /** Play weapon-specific attack animation based on held item. */
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

    // ========================= UTILITY =========================

    /** Ease-in-out curve (slow start, fast middle, slow end). */
    private static float easeInOut(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }

    /** Ease-out: fast start, slow end (impact deceleration). */
    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    /** Ease-in: slow start, fast end (buildup acceleration). */
    private static float easeIn(float t) {
        return t * t;
    }

    /** Overshoot: goes past 1.0 then settles back (for follow-through). */
    private static float overshoot(float t, float amount) {
        float s = amount;
        return (t = t - 1) * t * ((s + 1) * t + s) + 1;
    }

    /** Clamp a phase progress value between 0 and 1. */
    private static float phase(float t, float start, float end) {
        if (t < start) return 0f;
        if (t >= end) return 1f;
        return (t - start) / (end - start);
    }

    // ========================= IDLE BREATHING =========================

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

    // ========================= WALK =========================

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

    // ========================= SWORD SLASH =========================
    // Duration: 14 ticks
    // 0-5: Windup — arm draws back, body coils right, weight shifts
    // 5-7: STRIKE — fast diagonal slash, body uncoils
    // 7-10: Follow-through — arm sweeps past, body overextends
    // 10-14: Recovery

    private static class SwordSlashAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 14; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;

            float windup    = easeIn(phase(t, 0, 5));     // slow buildup
            float strike    = easeOut(phase(t, 5, 7));     // explosive hit
            float follow    = easeOut(phase(t, 7, 10));    // carry momentum
            float recovery  = easeInOut(phase(t, 10, 14)); // settle

            if (type == TransformType.ROTATION) {
                switch (modelPart) {
                    case "rightArm" -> {
                        // Draws back during windup, slashes forward hard, follows through
                        float armX = windup * 1.2f - strike * 3.5f - follow * 0.3f + recovery * 2.6f;
                        float armZ = windup * 0.4f - strike * 0.8f + recovery * 0.4f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + armZ);
                    }
                    case "leftArm" -> {
                        // Counter-swing for balance
                        float lArm = -windup * 0.3f + strike * 0.5f - recovery * 0.2f;
                        return new Vec3f(v.getX() + lArm, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        // Coil right during windup, uncoil hard on strike
                        float twistY = windup * 0.35f - strike * 0.6f - follow * 0.1f + recovery * 0.35f;
                        float leanX = -strike * 0.2f - follow * 0.1f + recovery * 0.3f;
                        return new Vec3f(v.getX() + leanX, v.getY() + twistY, v.getZ());
                    }
                    case "rightLeg" -> {
                        // Plant forward on strike
                        float leg = -strike * 0.25f + recovery * 0.25f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "leftLeg" -> {
                        float leg = strike * 0.15f - recovery * 0.15f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "head" -> {
                        // Look toward target on strike
                        float headY = windup * 0.1f - strike * 0.15f + recovery * 0.05f;
                        return new Vec3f(v.getX(), v.getY() + headY, v.getZ());
                    }
                }
            }
            return v;
        }
    }

    // ========================= AXE OVERHEAD =========================
    // Duration: 16 ticks
    // 0-3: Stance shift — grip with both hands, lean back
    // 3-7: Raise overhead — arms way up, back arches
    // 7-8: Peak hold — brief freeze (tension!)
    // 8-10: SLAM — explosive downward, body lunges
    // 10-13: Impact — body stays forward, recoil bounce
    // 13-16: Recovery

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
            float hold     = phase(t, 7, 8);       // linear brief pause
            float slam     = easeOut(phase(t, 8, 10));
            float impact   = phase(t, 10, 13);
            float recovery = easeInOut(phase(t, 13, 16));

            // Combined raise amount (stays up during hold)
            float raised = raise * (1 - slam);
            float slammed = slam;

            if (type == TransformType.ROTATION) {
                switch (modelPart) {
                    case "rightArm" -> {
                        // Up behind head → explosive slam down
                        float armX = -stance * 0.3f - raised * 3.2f + slammed * 4.8f
                                   + impact * (float) Math.sin(impact * Math.PI) * -0.4f
                                   - recovery * 1.3f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "leftArm" -> {
                        // Follows right arm (two-handed grip)
                        float armX = -stance * 0.2f - raised * 2.8f + slammed * 4.2f
                                   + impact * (float) Math.sin(impact * Math.PI) * -0.3f
                                   - recovery * 1.1f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        // Arch back during raise, lunge forward on slam
                        float lean = stance * 0.1f + raised * 0.35f - slammed * 0.5f
                                   - impact * 0.15f + recovery * 0.2f;
                        return new Vec3f(v.getX() + lean, v.getY(), v.getZ());
                    }
                    case "rightLeg" -> {
                        // Step forward on slam
                        float leg = -slammed * 0.35f - impact * 0.1f + recovery * 0.45f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "leftLeg" -> {
                        float leg = slammed * 0.2f - recovery * 0.2f;
                        return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                    }
                    case "head" -> {
                        // Tilt up during raise, snap down on slam
                        float headX = raised * 0.25f - slammed * 0.3f + recovery * 0.05f;
                        return new Vec3f(v.getX() + headX, v.getY(), v.getZ());
                    }
                }
            }
            return v;
        }
    }

    // ========================= MACE SLAM =========================
    // Duration: 22 ticks — heaviest, most dramatic, player JUMPS then SLAMS
    // 0-3: Grip shift — both hands, widen stance, crouch
    // 3-8: JUMP — player launches upward, arms raise overhead
    // 8-10: Airborne peak — arms fully overhead, body at max height
    // 10-12: SLAM DOWN — explosive descent, full body commit
    // 12-16: Impact shockwave — body stays low, bounce, ground pound feel
    // 16-22: Slow recovery (heavy weapon, stands back up)

    private static class MaceSlamAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 22; }
        @Override public void setupAnim(float tickDelta) {}

        @Override
        public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                              float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;

            float crouch   = easeInOut(phase(t, 0, 3));    // prep crouch
            float jump     = easeOut(phase(t, 3, 8));       // launch up
            float peak     = phase(t, 8, 10);               // hang in air
            float slamDown = easeIn(phase(t, 10, 12));      // accelerate down
            float impact   = phase(t, 12, 16);              // ground pound
            float recovery = easeInOut(phase(t, 16, 22));   // slow stand up

            // Jump height curve: up during jump, stays up at peak, crashes down on slam
            float airborne = jump * (1 - slamDown);
            float slammed = slamDown;
            // Impact bounce — dampened oscillation
            float bounce = impact > 0 ? (float) Math.sin(impact * Math.PI * 3) * 0.25f * (1 - impact) : 0;

            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                // Crouch down, JUMP UP high, then SLAM back to ground
                float height = -crouch * 0.8f         // crouch
                    + airborne * 4.0f                   // jump up
                    - slammed * 4.5f                    // slam back to ground (net ~-0.5 at impact)
                    + bounce * 1.5f                     // impact bounce
                    + recovery * 1.3f;                  // stand back up
                return new Vec3f(v.getX(), v.getY() + height, v.getZ());
            }

            if (type == TransformType.ROTATION) {
                switch (modelPart) {
                    case "rightArm", "leftArm" -> {
                        // Crouch: arms tense. Jump: arms swing overhead. Slam: arms drive down
                        float armX = crouch * 0.3f                     // tense
                            - airborne * 3.2f                           // arms overhead
                            + slammed * 4.5f                            // drive down
                            + bounce                                    // recoil
                            - recovery * 1.3f;                          // return to neutral
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        // Crouch: lean forward. Air: arch back. Slam: lunge forward
                        float lean = crouch * 0.25f + airborne * 0.4f - slammed * 0.7f
                            + bounce * 0.4f + recovery * 0.15f;
                        return new Vec3f(v.getX() + lean, v.getY(), v.getZ());
                    }
                    case "rightLeg" -> {
                        // Crouch: bend. Air: tuck. Slam: stomp
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
                        // Look up during jump, snap down during slam
                        float headX = airborne * 0.35f - slammed * 0.5f + recovery * 0.15f;
                        return new Vec3f(v.getX() + headX, v.getY(), v.getZ());
                    }
                }
            }
            return v;
        }
    }

    // ========================= BOW DRAW & RELEASE =========================
    // Duration: 16 ticks
    // 0-3: Nock — left arm raises bow, right reaches back
    // 3-9: Draw — steady pull, body leans into aim, tension builds
    // 9-10: Hold/Aim — brief stillness
    // 10-11: RELEASE — snap forward, left arm recoils
    // 11-16: Recovery

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
                        // Pull string back, then snap forward on release
                        float armX = -drawn * 2.0f + released * 1.0f + recovery * 1.0f;
                        float armZ = drawn * 0.3f - recovery * 0.3f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + armZ);
                    }
                    case "leftArm" -> {
                        // Hold bow up and steady, slight recoil on release
                        float armX = -nock * 1.4f * (1 - recovery) + released * 0.3f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        // Lean into the shot
                        float lean = -drawn * 0.15f + released * 0.1f + recovery * 0.05f;
                        float twist = drawn * 0.2f - recovery * 0.2f;
                        return new Vec3f(v.getX() + lean, v.getY() + twist, v.getZ());
                    }
                    case "head" -> {
                        // Aim — slight tilt toward target
                        float headY = -drawn * 0.1f + recovery * 0.1f;
                        return new Vec3f(v.getX(), v.getY() + headY, v.getZ());
                    }
                }
            }
            return v;
        }
    }

    // ========================= TRIDENT THRUST =========================
    // Duration: 14 ticks
    // 0-4: Coil — arm draws back, body rotates away, weight on back foot
    // 4-6: STAB — explosive forward thrust, full extension
    // 6-9: Hold — arm stays extended, body committed
    // 9-14: Recovery

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
                        // Pull back, then explosive forward jab
                        float armX = coiled * 1.5f - stabbed * 2.2f + recovery * 0.7f;
                        return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                    }
                    case "leftArm" -> {
                        // Counter-balance
                        float lArm = -coiled * 0.3f + stabbed * 0.4f - recovery * 0.1f;
                        return new Vec3f(v.getX() + lArm, v.getY(), v.getZ());
                    }
                    case "body" -> {
                        // Rotate away during coil, snap forward on stab
                        float twistY = coiled * 0.4f - stabbed * 0.5f + recovery * 0.1f;
                        float leanX = -stabbed * 0.25f + recovery * 0.25f;
                        return new Vec3f(v.getX() + leanX, v.getY() + twistY, v.getZ());
                    }
                    case "rightLeg" -> {
                        // Lunge forward
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

    // ========================= EAT/DRINK =========================

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

    // ========================= THROW =========================
    // Duration: 10 ticks
    // 0-4: Wind up — arm back, body coils
    // 4-6: RELEASE — explosive forward snap
    // 6-10: Follow-through and recovery

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
