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

    /**
     * Whether the previous tick was inside a fight, so leaving one can be detected as an edge.
     *
     * <p>Separate from {@link #wasAnimating}, which only says a WALK was in flight. The pose that
     * outlives a fight is usually the idle breather, installed by the tail of {@link #tick()}
     * whenever the layer falls idle mid-fight - which has nothing to do with whether the player
     * was walking when the last enemy died.
     */
    private static boolean wasInCombat = false;
    /**
     * Per-player attack-animation countdowns. Attack animations play on EVERY
     * party member's avatar (the damage event names the attacker), so a single
     * shared timer let one teammate's swing cut another's short, and the
     * expiry always faded out the LOCAL player's layer, leaving remote avatars
     * frozen at the final frame of their swing.
     */
    private static final java.util.WeakHashMap<AbstractClientPlayerEntity, Integer> attackTimers =
        new java.util.WeakHashMap<>();

    // Cinematic walk tracking: drive the same WalkAnimation used in combat while the
    // player is being walked (position changing) during a non-combat event cinematic.
    private static boolean wasCinematicWalking = false;
    private static double lastCinX = Double.NaN, lastCinZ = Double.NaN;

    /**
     * The combat layer is stored in PlayerAnimator's per-entity associated data,
     * NEVER in a map keyed by the entity. Entity.hashCode()/equals() use the entity
     * ID, and the client assigns the network ID via setId() AFTER construction (the
     * REGISTER_ANIMATION_EVENT fires mid-constructor), so any entity-keyed map ends
     * up with entries hidden under the temporary construction ID plus entries that
     * alias OLD respawned entities under the live ID. The aliased hit returns a
     * layer attached to a dead entity's AnimationStack: every animation call lands
     * on a stack nobody renders, nothing throws, and only a client restart (or an
     * eventual GC expunging the stale weak key) recovers. Associated data lives in
     * a field on the entity instance itself, so it can neither alias nor go stale.
     */
    private static final net.minecraft.util.Identifier LAYER_ID =
        net.minecraft.util.Identifier.of("craftics", "combat_layer");

    // Identity of the local player entity we last ticked against. When the
    // client respawns (void death, /kill, dimension change) Minecraft swaps
    // in a fresh AbstractClientPlayerEntity. Without detecting that swap
    // here, the static animation flags below (wasAnimating, currentLayer,
    // attackAnimTimer, lastCinX/Z) stay pinned to the previous entity and
    // walk / attack animations stop firing for the new entity from the
    // local player's POV.
    private static AbstractClientPlayerEntity lastTickPlayer = null;

    public static void register() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
            var layer = new ModifierLayer<IAnimation>();
            stack.addAnimLayer(42, layer);
            PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);
        });
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> getOrCreateLayer(AbstractClientPlayerEntity player) {
        if (PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID)
                instanceof ModifierLayer<?> stored) {
            return (ModifierLayer<IAnimation>) stored;
        }
        // Fallback if register callback hasn't fired yet
        if (player instanceof IPlayer iPlayer) {
            AnimationStack stack = iPlayer.getAnimationStack();
            if (stack != null) {
                ModifierLayer<IAnimation> layer = new ModifierLayer<IAnimation>();
                stack.addAnimLayer(42, layer);
                PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);
                return layer;
            }
        }
        // Returning null here silently disables EVERY animation - every caller bails on it -
        // and that is exactly what "my character stopped animating and only a restart fixes
        // it" looks like from the outside. There is no other symptom: nothing throws, nothing
        // logs, the game plays on. Say so once so the cause is identifiable from a log rather
        // than inferred. Once per client session; this must never become per-tick spam.
        if (!loggedMissingLayer) {
            loggedMissingLayer = true;
            com.crackedgames.craftics.CrafticsMod.LOGGER.warn(
                "PlayerAnimator layer unavailable for {} (isIPlayer={}); combat animations are "
                + "disabled until this resolves. Either the REGISTER_ANIMATION_EVENT never fired "
                + "for this player entity, or player-animator is missing/mismatched.",
                player.getName().getString(), player instanceof IPlayer);
        }
        return null;
    }

    /** One-shot guard for the warning above - the null path can recur every tick. */
    private static boolean loggedMissingLayer = false;

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            lastTickPlayer = null;
            return;
        }

        // Reset all per-player animation state when the local entity changes
        // (respawn after void / kill, dimension travel, etc). Without this
        // the stuck wasAnimating / currentLayer / attackAnimTimer from the
        // dead entity make startWalking/stopWalking no-op against the new
        // entity, breaking the host's combat walk + attack animations
        // entirely from their own POV until a full client restart.
        //
        // This MUST run before the hit-pause gate below, not after it. A freeze that is
        // still counting down while the player entity is swapped out would otherwise skip
        // the swap entirely - and a freeze that never finishes counting down (see below)
        // would skip it forever, which is the state this reset exists to escape.
        if (client.player != lastTickPlayer) {
            wasAnimating = false;
            wasCinematicWalking = false;
            wasInCombat = false;
            attackTimers.clear();
            lastCinX = Double.NaN;
            lastCinZ = Double.NaN;
            lastTickPlayer = client.player;
            // A freeze belongs to the fight the old entity died in. Carrying it across a
            // respawn suppresses every animation for the new one, and nothing else would
            // clear it: the countdown only advances inside CombatVisualEffects.tick(), so
            // a freeze stranded across a disconnect or a broken world transition never
            // expires and the character stops animating until the game is restarted.
            com.crackedgames.craftics.client.vfx.HitPauseState.reset();
        }

        if (com.crackedgames.craftics.client.vfx.HitPauseState.isFrozen()) return;

        // Count down every avatar's attack animation and fade THAT avatar's
        // layer out on expiry. Runs before the combat guard so a swing that
        // outlives the fight still resolves cleanly.
        if (!attackTimers.isEmpty()) {
            var it = attackTimers.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    stopAttack(entry.getKey());
                    it.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        }

        if (!CombatState.isInCombat()) {
            // Leaving a fight clears the animation layer whatever it is holding - not just when a
            // walk happened to be mid-stride. This used to be gated on wasAnimating, which is only
            // true when the fight ended during the player's OWN move animation. Every other ending
            // (the ordinary one: the last enemy dies on someone else's turn) left the idle-breathing
            // pose installed, and IdleBreathingAnimation reports isActive() forever, so nothing
            // retired it: the body kept its animated rotation while the head went on tracking the
            // camera, and the player rendered bent - on the island, while walking, until a restart.
            // stopAll() had no callers at all, which is how this survived.
            if (wasInCombat || wasAnimating) {
                stopAll();
                wasAnimating = false;
                wasInCombat = false;
            }
            // During a non-combat event cinematic, play the same WalkAnimation combat
            // uses while the player is actually moving (position changing this tick),
            // and stop it when they arrive/stand still.
            if (CombatState.isCinematicActive()) {
                double x = client.player.getX();
                double z = client.player.getZ();
                boolean movingNow = false;
                if (!Double.isNaN(lastCinX)) {
                    double dx = x - lastCinX, dz = z - lastCinZ;
                    movingNow = (dx * dx + dz * dz) > 1.0e-5; // moved a meaningful amount
                }
                lastCinX = x;
                lastCinZ = z;
                if (movingNow && !wasCinematicWalking) startWalking(client.player);
                else if (!movingNow && wasCinematicWalking) stopWalking(client.player);
                wasCinematicWalking = movingNow;
            } else if (wasCinematicWalking) {
                stopWalking(client.player);
                wasCinematicWalking = false;
                lastCinX = Double.NaN;
                lastCinZ = Double.NaN;
            }
            return;
        }

        int phase = CombatState.getPhase();
        // Phase 2 = combat move animation, broadcast to ALL party clients. Only
        // play the local-player walking anim when the local player is the one
        // actually moving, otherwise every party member's avatar walks in
        // place whenever any one teammate moves. In solo (empty turn order)
        // the local player is always the actor.
        boolean isMyTurn = true;
        var turnOrder = CombatState.getTurnOrderList();
        if (!turnOrder.isEmpty()) {
            String myUuid = client.getSession().getUuidOrNull() != null
                ? client.getSession().getUuidOrNull().toString() : "";
            isMyTurn = false;
            for (var entry : turnOrder) {
                if (entry.isCurrent() && entry.uuid().equals(myUuid)) {
                    isMyTurn = true;
                    break;
                }
            }
        }
        boolean isAnimating = (phase == 2) && isMyTurn;

        if (isAnimating && !wasAnimating) startWalking(client.player);
        else if (!isAnimating && wasAnimating) stopWalking(client.player);
        wasAnimating = isAnimating;

        ModifierLayer<IAnimation> layer = getOrCreateLayer(client.player);
        if (layer != null && !layer.isActive() && CombatState.isInCombat()) {
            layer.setAnimation(new IdleBreathingAnimation());
        }
        wasInCombat = true;
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

    /** Ticks every battle-intro animation runs; matches the intro camera dwell. */
    public static final int INTRO_ANIM_TICKS = 45;

    /**
     * Play this fighter's battle-intro flourish, picked by their leading affinity.
     * Called once per fighter by {@link CombatIntroSequence} as the camera reaches
     * them. The attack-timer map fades the layer back out when the flourish ends,
     * exactly as it does for weapon swings.
     */
    public static void playIntro(AbstractClientPlayerEntity player, String affinityName) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;
        IAnimation anim = switch (affinityName) {
            case "SLASHING" -> new SlashingIntroAnimation();
            case "CLEAVING" -> new CleavingIntroAnimation();
            case "BLUNT" -> new BluntIntroAnimation();
            case "RANGED" -> new RangedIntroAnimation();
            case "WATER" -> new WaterIntroAnimation();
            case "SPECIAL" -> new SpecialIntroAnimation();
            case "PET" -> new PetIntroAnimation();
            default -> new PhysicalIntroAnimation();
        };
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE), anim);
        attackTimers.put(player, INTRO_ANIM_TICKS + 4);
    }

    public static void playAttack(AbstractClientPlayerEntity player) { playWeaponAttack(player); }

    public static void playWeaponAttack(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;

        net.minecraft.item.Item held = player.getMainHandStack().getItem();
        String itemId = net.minecraft.registry.Registries.ITEM.getId(held).getPath();

        IAnimation anim;
        int duration;

        if (itemId.contains("dagger")) {
            anim = new DaggerJabAnimation();
            duration = 10;
        } else if (itemId.contains("club")) {
            anim = new ClubBashAnimation();
            duration = 16;
        } else if (itemId.contains("hammer")) {
            anim = new MaceSlamAnimation();
            duration = 22;
        } else if (itemId.contains("spear")) {
            anim = new SpearThrustAnimation();
            duration = 14;
        } else if (itemId.contains("quarterstaff")) {
            anim = new QuarterstaffTwirlAnimation();
            duration = 16;
        } else if (itemId.contains("glaive")) {
            anim = new GlaiveSweepAnimation();
            duration = 18;
        } else if (itemId.contains("bow") || itemId.contains("crossbow")) {
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
        attackTimers.put(player, duration);
    }

    public static void playUseItem(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.LINEAR), new EatAnimation());
        attackTimers.put(player, 12);
    }

    public static void playThrow(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(1, Ease.LINEAR), new ThrowAnimation());
        attackTimers.put(player, 10);
    }

    private static void stopAttack(AbstractClientPlayerEntity player) {
        ModifierLayer<IAnimation> layer = getOrCreateLayer(player);
        if (layer == null) return;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.LINEAR), null);
    }

    /** Hard-stop every visible avatar's layer (combat end), not just the local player's. */
    public static void stopAll() {
        var world = MinecraftClient.getInstance().world;
        if (world != null) {
            for (AbstractClientPlayerEntity p : world.getPlayers()) {
                if (PlayerAnimationAccess.getPlayerAssociatedData(p).get(LAYER_ID)
                        instanceof ModifierLayer<?> l) {
                    l.setAnimation(null);
                }
            }
        }
        attackTimers.clear();
    }

    public static void clearCache() {
        wasAnimating = false;
        // Also reset the cinematic-walk tracking so a scene/event that left the local
        // player mid-walk can't carry a stale "already walking" state into the next
        // combat (which would suppress that fight's walk animation).
        wasCinematicWalking = false;
        lastCinX = Double.NaN;
        lastCinZ = Double.NaN;
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

    // 14 ticks: windup(0-5) -> strike(5-7) -> follow-through(7-10) -> recovery(10-14)
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

    // Basic Weapons mod animations

    // 10 ticks: jab1(0-3) -> jab2(3-6) -> recovery(6-10) - fast double thrust
    private static class DaggerJabAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 10; }
        @Override public void setupAnim(float tickDelta) {}
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            if (type != TransformType.ROTATION) return v;
            float t = tick + tickDelta;
            float jab1 = easeOut(phase(t, 0, 3)) * (1 - phase(t, 3, 6));
            float jab2 = easeOut(phase(t, 3, 6)) * (1 - phase(t, 6, 10));
            float thrust = jab1 + jab2;
            if ("rightArm".equals(modelPart)) return new Vec3f(v.getX() - thrust * 1.6f, v.getY(), v.getZ());
            if ("body".equals(modelPart)) return new Vec3f(v.getX(), v.getY() + thrust * 0.2f, v.getZ());
            return v;
        }
    }

    // 16 ticks: windup(0-5) -> swing(5-8) -> recovery(8-16) - horizontal side bash
    private static class ClubBashAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 16; }
        @Override public void setupAnim(float tickDelta) {}
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            if (type != TransformType.ROTATION) return v;
            float t = tick + tickDelta;
            float windup = easeIn(phase(t, 0, 5));
            float swing = easeOut(phase(t, 5, 8));
            float recovery = easeInOut(phase(t, 8, 16));
            if ("rightArm".equals(modelPart)) {
                float armZ = windup * 1.0f - swing * 2.2f + recovery * 1.2f;
                return new Vec3f(v.getX() - swing * 1.5f, v.getY(), v.getZ() + armZ);
            }
            if ("body".equals(modelPart)) return new Vec3f(v.getX(), v.getY() + windup * 0.3f - swing * 0.5f + recovery * 0.2f, v.getZ());
            return v;
        }
    }

    // 14 ticks: coil(0-4) -> lunge(4-7) -> recovery(7-14) - long forward thrust
    private static class SpearThrustAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 14; }
        @Override public void setupAnim(float tickDelta) {}
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;
            float coil = easeIn(phase(t, 0, 4));
            float lunge = easeOut(phase(t, 4, 7));
            float recovery = easeInOut(phase(t, 7, 14));
            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                return new Vec3f(v.getX(), v.getY(), v.getZ() + lunge * (1 - recovery) * 0.6f);
            }
            if (type != TransformType.ROTATION) return v;
            float ext = coil * 0.8f - lunge * 2.4f + recovery * 1.6f;
            if ("rightArm".equals(modelPart)) return new Vec3f(v.getX() + ext, v.getY(), v.getZ());
            if ("leftArm".equals(modelPart)) return new Vec3f(v.getX() + ext * 0.5f, v.getY(), v.getZ());
            return v;
        }
    }

    // 16 ticks: spin around - staff twirl
    private static class QuarterstaffTwirlAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 16; }
        @Override public void setupAnim(float tickDelta) {}
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            if (type != TransformType.ROTATION) return v;
            float t = tick + tickDelta;
            float spin = phase(t, 2, 12);
            float wheel = (float) Math.sin(spin * Math.PI * 2) * (1 - phase(t, 12, 16));
            if ("rightArm".equals(modelPart)) return new Vec3f(v.getX() + wheel * 2.0f, v.getY(), v.getZ());
            if ("leftArm".equals(modelPart)) return new Vec3f(v.getX() - wheel * 2.0f, v.getY(), v.getZ());
            if ("body".equals(modelPart)) return new Vec3f(v.getX(), v.getY() + wheel * 0.6f, v.getZ());
            return v;
        }
    }

    // 18 ticks: windup(0-6) -> wide sweep(6-10) -> recovery(10-18) - broad horizontal arc
    private static class GlaiveSweepAnimation implements IAnimation {
        private float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < 18; }
        @Override public void setupAnim(float tickDelta) {}
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            if (type != TransformType.ROTATION) return v;
            float t = tick + tickDelta;
            float windup = easeIn(phase(t, 0, 6));
            float sweep = easeOut(phase(t, 6, 10));
            float recovery = easeInOut(phase(t, 10, 18));
            if ("rightArm".equals(modelPart)) {
                float armY = windup * 0.8f - sweep * 2.0f + recovery * 1.2f;
                return new Vec3f(v.getX() - sweep * 1.2f, v.getY() + armY, v.getZ());
            }
            if ("body".equals(modelPart)) {
                float twist = windup * 0.4f - sweep * 0.9f + recovery * 0.5f;
                return new Vec3f(v.getX(), v.getY() + twist, v.getZ());
            }
            return v;
        }
    }

    // 16 ticks: stance(0-3) -> raise(3-7) -> hold(7-8) -> slam(8-10) -> impact(10-13) -> recovery(13-16)
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

    // 22 ticks: crouch(0-3) -> jump(3-8) -> peak(8-10) -> slam(10-12) -> impact(12-16) -> recovery(16-22)
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

    // 16 ticks: nock(0-3) -> draw(3-9) -> hold(9-10) -> release(10-11) -> recovery(11-16)
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

    // 14 ticks: coil(0-4) -> stab(4-6) -> hold(6-9) -> recovery(9-14)
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

    // --- Battle intro flourishes (one per affinity, all INTRO_ANIM_TICKS long) ---
    //
    // Authored as keyframe channels rather than summed phase ramps: each channel is a
    // list of {tick, value} keys with smoothstep easing between them, so every segment
    // eases in and out and there are no velocity pops at phase boundaries. The
    // animation principles live in the KEYS: a small counter-move before every big one
    // (anticipation), poses keyed slightly past their target then back (overshoot and
    // settle), and head/body channels keyed a couple of ticks behind the arms
    // (follow-through). Arcs come from pairing X and Z rotation channels.

    /** Sample a keyframe channel: smoothstep between consecutive {tick, value} keys. */
    private static float kf(float t, float[]... keys) {
        if (t <= keys[0][0]) return keys[0][1];
        for (int i = 1; i < keys.length; i++) {
            if (t < keys[i][0]) {
                float span = keys[i][0] - keys[i - 1][0];
                float f = span <= 0 ? 1f : (t - keys[i - 1][0]) / span;
                f = f * f * (3 - 2 * f);
                return keys[i - 1][1] + (keys[i][1] - keys[i - 1][1]) * f;
            }
        }
        return keys[keys.length - 1][1];
    }

    private static float[] k(float t, float v) { return new float[] { t, v }; }

    /** Shared shape for the intro flourishes: fixed length, keyframed transforms. */
    private abstract static class IntroAnimation implements IAnimation {
        protected float tick = 0;
        @Override public void tick() { tick += 1; }
        @Override public boolean isActive() { return tick < INTRO_ANIM_TICKS; }
        @Override public void setupAnim(float tickDelta) {}
    }

    // Anticipation draw-back -> slash across -> return cut -> sword raised high
    private static class SlashingIntroAnimation extends IntroAnimation {
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            if (type != TransformType.ROTATION) return v;
            float t = tick + tickDelta;
            switch (modelPart) {
                case "rightArm" -> {
                    float armX = kf(t, k(0, 0), k(6, 0.55f), k(12, -2.85f), k(15, -2.5f),
                        k(20, 0.65f), k(26, -2.25f), k(29, -1.95f), k(35, -2.75f), k(39, -2.4f), k(45, -2.4f));
                    float armZ = kf(t, k(0, 0), k(6, 0.35f), k(12, -0.9f), k(15, -0.7f),
                        k(20, 0.5f), k(26, -0.5f), k(29, -0.35f), k(35, 0.45f), k(39, 0.3f), k(45, 0.3f));
                    return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + armZ);
                }
                case "leftArm" -> {
                    float armX = kf(t, k(0, 0), k(8, -0.25f), k(14, 0.45f), k(17, 0.3f),
                        k(22, -0.35f), k(28, 0.35f), k(31, 0.25f), k(37, -0.6f), k(41, -0.45f), k(45, -0.45f));
                    return new Vec3f(v.getX() + armX, v.getY(), v.getZ());
                }
                case "body" -> {
                    float twist = kf(t, k(0, 0), k(7, 0.18f), k(13, -0.5f), k(16, -0.4f),
                        k(21, 0.32f), k(27, -0.38f), k(30, -0.28f), k(36, 0.2f), k(40, 0.14f), k(45, 0.14f));
                    return new Vec3f(v.getX(), v.getY() + twist, v.getZ());
                }
                case "head" -> {
                    float headY = kf(t, k(0, 0), k(9, 0.1f), k(15, -0.18f), k(18, -0.1f),
                        k(23, 0.14f), k(29, -0.12f), k(32, -0.06f), k(38, 0.04f), k(45, 0));
                    return new Vec3f(v.getX(), v.getY() + headY, v.getZ());
                }
            }
            return v;
        }
    }

    // Dip -> heave the axe high overhead -> massive chop with a body drop -> shoulder carry
    private static class CleavingIntroAnimation extends IntroAnimation {
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;
            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                float y = kf(t, k(0, 0), k(6, -0.25f), k(14, 0.35f), k(20, 0.3f),
                    k(24, -1.05f), k(27, -0.8f), k(29, -0.9f), k(35, -0.2f), k(41, 0), k(45, 0));
                return new Vec3f(v.getX(), v.getY() + y, v.getZ());
            }
            if (type != TransformType.ROTATION) return v;
            switch (modelPart) {
                case "rightArm", "leftArm" -> {
                    float armX = kf(t, k(0, 0), k(6, 0.6f), k(14, -3.15f), k(17, -2.9f),
                        k(24, 1.55f), k(27, 1.25f), k(33, -0.4f), k(39, -2.55f), k(42, -2.25f), k(45, -2.25f));
                    float armZ = "rightArm".equals(modelPart)
                        ? kf(t, k(0, 0), k(30, 0), k(39, 0.62f), k(42, 0.55f), k(45, 0.55f))
                        : kf(t, k(0, 0), k(30, 0), k(39, -0.22f), k(45, -0.2f));
                    return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + armZ);
                }
                case "body" -> {
                    float lean = kf(t, k(0, 0), k(7, 0.16f), k(15, 0.35f), k(21, 0.3f),
                        k(25, -0.58f), k(28, -0.44f), k(35, -0.1f), k(41, 0.1f), k(45, 0.1f));
                    return new Vec3f(v.getX() + lean, v.getY(), v.getZ());
                }
                case "rightLeg" -> {
                    float leg = kf(t, k(0, 0), k(20, 0), k(24, -0.4f), k(28, -0.3f), k(35, 0), k(45, 0));
                    return new Vec3f(v.getX() + leg, v.getY(), v.getZ());
                }
                case "head" -> {
                    float headX = kf(t, k(0, 0), k(9, 0.1f), k(16, 0.3f), k(26, -0.32f),
                        k(30, -0.22f), k(37, -0.05f), k(45, 0));
                    return new Vec3f(v.getX() + headX, v.getY(), v.getZ());
                }
            }
            return v;
        }
    }

    // Fists rise -> crouched double ground-pound with a bounce -> rise -> arms-out flex
    private static class BluntIntroAnimation extends IntroAnimation {
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;
            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                float y = kf(t, k(0, 0), k(6, -0.2f), k(13, 0.28f), k(19, 0.22f),
                    k(23, -1.75f), k(26, -1.4f), k(28, -1.55f), k(34, -0.35f), k(40, 0.08f), k(43, 0), k(45, 0));
                return new Vec3f(v.getX(), v.getY() + y, v.getZ());
            }
            if (type != TransformType.ROTATION) return v;
            switch (modelPart) {
                case "rightArm", "leftArm" -> {
                    float armX = kf(t, k(0, 0), k(6, 0.5f), k(13, -2.85f), k(19, -2.6f),
                        k(23, 1.15f), k(26, 0.85f), k(32, 0.2f), k(38, -1.65f), k(41, -1.35f), k(45, -1.35f));
                    float out = kf(t, k(0, 0), k(32, 0), k(38, 1.0f), k(41, 0.88f), k(45, 0.88f));
                    float armZ = "rightArm".equals(modelPart) ? out : -out;
                    return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + armZ);
                }
                case "body" -> {
                    float lean = kf(t, k(0, 0), k(7, 0.14f), k(14, -0.2f), k(24, -0.48f),
                        k(28, -0.35f), k(35, -0.05f), k(41, 0.08f), k(45, 0.08f));
                    return new Vec3f(v.getX() + lean, v.getY(), v.getZ());
                }
                case "rightLeg", "leftLeg" -> {
                    float bend = kf(t, k(0, 0), k(19, 0), k(23, -0.85f), k(28, -0.7f), k(34, -0.15f), k(40, 0), k(45, 0));
                    return new Vec3f(v.getX() + bend, v.getY(), v.getZ());
                }
                case "head" -> {
                    float headX = kf(t, k(0, 0), k(13, -0.12f), k(24, 0.22f), k(29, 0.12f),
                        k(38, -0.1f), k(45, -0.06f));
                    return new Vec3f(v.getX() + headX, v.getY(), v.getZ());
                }
            }
            return v;
        }
    }

    // Nock -> long tense draw (with a tremble) -> smooth aim arc across the field -> lower
    private static class RangedIntroAnimation extends IntroAnimation {
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            if (type != TransformType.ROTATION) return v;
            float t = tick + tickDelta;
            // Draw tension: a tiny tremble that only exists while fully drawn.
            float tension = kf(t, k(0, 0), k(20, 0), k(24, 1), k(36, 1), k(40, 0), k(45, 0));
            float tremble = (float) Math.sin(t * 2.1f) * 0.035f * tension;
            // One smooth aim arc: out to the left, sweep across to the right, recenter.
            float aim = kf(t, k(0, 0), k(24, 0), k(29, 0.5f), k(36, -0.42f), k(40, 0.05f), k(42, 0), k(45, 0));
            switch (modelPart) {
                case "rightArm" -> {
                    float armX = kf(t, k(0, 0), k(5, 0.3f), k(9, -1.25f), k(24, -2.35f),
                        k(38, -2.3f), k(43, -0.75f), k(45, -0.7f)) + tremble;
                    return new Vec3f(v.getX() + armX, v.getY() + aim, v.getZ() + 0.18f * tension);
                }
                case "leftArm" -> {
                    float armX = kf(t, k(0, 0), k(5, 0.2f), k(9, -1.45f), k(24, -2.1f),
                        k(38, -2.05f), k(43, -0.65f), k(45, -0.6f)) - tremble;
                    return new Vec3f(v.getX() + armX, v.getY() + aim, v.getZ() - 0.14f * tension);
                }
                case "body" -> {
                    float twist = kf(t, k(0, 0), k(9, 0.08f), k(24, 0.3f), k(40, 0.25f), k(45, 0.05f))
                        + aim * 0.55f;
                    return new Vec3f(v.getX(), v.getY() + twist, v.getZ());
                }
                case "head" -> {
                    float lean = kf(t, k(0, 0), k(10, -0.05f), k(24, -0.14f), k(42, -0.1f), k(45, 0));
                    return new Vec3f(v.getX() + lean, v.getY() + aim * 0.5f, v.getZ());
                }
            }
            return v;
        }
    }

    // Continuous water-bending: arms trace opposing circles, body and head ride the wave a beat behind
    private static class WaterIntroAnimation extends IntroAnimation {
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;
            // Bell envelope so the flow grows in and drains out with no hard edges.
            float amp = kf(t, k(0, 0), k(10, 1), k(34, 1), k(45, 0));
            float wave = t * 0.34f;
            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                return new Vec3f(v.getX(), v.getY() + (float) Math.sin(wave * 0.7) * 0.26f * amp, v.getZ());
            }
            if (type != TransformType.ROTATION) return v;
            switch (modelPart) {
                case "rightArm" -> {
                    float armX = ((float) Math.sin(wave) * 1.1f - 1.35f) * amp;
                    float armZ = (float) Math.cos(wave) * 0.85f * amp;
                    return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + armZ);
                }
                case "leftArm" -> {
                    float armX = ((float) Math.sin(wave + Math.PI) * 1.1f - 1.35f) * amp;
                    float armZ = (float) Math.cos(wave + Math.PI) * 0.85f * amp;
                    return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + armZ);
                }
                case "body" -> {
                    // Follow-through: the torso rides the same wave a beat behind the arms.
                    float sway = (float) Math.sin(wave * 0.5 - 0.6) * 0.2f * amp;
                    return new Vec3f(v.getX(), v.getY() + sway, v.getZ() + sway * 0.5f);
                }
                case "head" -> {
                    float nod = (float) Math.sin(wave * 0.5 - 1.0) * 0.12f * amp;
                    return new Vec3f(v.getX(), v.getY() + nod, v.getZ());
                }
            }
            return v;
        }
    }

    // Gathering dip -> levitate -> hands weave opposing arcane circles -> ease back down
    private static class SpecialIntroAnimation extends IntroAnimation {
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;
            float amp = kf(t, k(0, 0), k(12, 1), k(34, 1), k(45, 0));
            float weave = t * 0.5f;
            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                // Anticipation: sink a touch before lifting off, hover with a slow bob, settle.
                float hover = kf(t, k(0, 0), k(5, -0.18f), k(14, 0.62f), k(36, 0.5f), k(43, 0), k(45, 0));
                float bob = (float) Math.sin(weave * 0.6) * 0.14f * amp;
                return new Vec3f(v.getX(), v.getY() + hover + bob, v.getZ());
            }
            if (type != TransformType.ROTATION) return v;
            switch (modelPart) {
                case "rightArm" -> {
                    float armX = (-1.9f + (float) Math.sin(weave) * 0.5f) * amp;
                    float armY = (float) Math.cos(weave) * 0.6f * amp;
                    return new Vec3f(v.getX() + armX, v.getY() + armY, v.getZ() + 0.3f * amp);
                }
                case "leftArm" -> {
                    float armX = (-1.9f + (float) Math.sin(weave + Math.PI) * 0.5f) * amp;
                    float armY = (float) Math.cos(weave + Math.PI) * 0.6f * amp;
                    return new Vec3f(v.getX() + armX, v.getY() + armY, v.getZ() - 0.3f * amp);
                }
                case "body" -> {
                    float sway = (float) Math.sin(weave * 0.5 - 0.5) * 0.1f * amp;
                    return new Vec3f(v.getX() - 0.08f * amp, v.getY() + sway, v.getZ());
                }
                case "head" -> {
                    float tilt = (float) Math.sin(weave * 0.8 - 0.8) * 0.09f * amp;
                    return new Vec3f(v.getX() - 0.18f * amp, v.getY(), v.getZ() + tilt);
                }
            }
            return v;
        }
    }

    // Settle to one knee -> whistle skyward -> spring up -> three warm beckoning waves
    private static class PetIntroAnimation extends IntroAnimation {
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;
            // Kneel depth: tiny rise first (anticipation), sink past the pose, settle, spring up.
            float down = kf(t, k(0, 0), k(4, 0.1f), k(12, -2.4f), k(15, -2.15f),
                k(26, -2.15f), k(29, -2.35f), k(35, 0.12f), k(38, 0), k(45, 0));
            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                return new Vec3f(v.getX(), v.getY() + down, v.getZ());
            }
            if (type != TransformType.ROTATION) return v;
            float kneel = Math.min(1f, -down / 2.15f);
            // Beckon: three soft in-out waves, eased on and off by their own envelope.
            float waveAmp = kf(t, k(0, 0), k(35, 0), k(38, 1), k(43, 1), k(45, 0.7f));
            float beckon = (float) Math.sin((t - 35) * 0.85f) * 0.55f * waveAmp;
            switch (modelPart) {
                case "rightArm" -> {
                    float armX = kf(t, k(0, 0), k(12, -0.25f), k(16, -2.7f), k(19, -2.45f),
                        k(26, -2.45f), k(33, -0.4f), k(37, -2.35f), k(40, -2.1f), k(45, -2.1f));
                    return new Vec3f(v.getX() + armX, v.getY(), v.getZ() + beckon);
                }
                case "leftArm" -> {
                    return new Vec3f(v.getX() + kneel * 0.55f, v.getY(), v.getZ() - kneel * 0.15f);
                }
                case "rightLeg" -> {
                    return new Vec3f(v.getX() - kneel * 1.35f, v.getY(), v.getZ());
                }
                case "leftLeg" -> {
                    return new Vec3f(v.getX() + kneel * 0.5f, v.getY(), v.getZ());
                }
                case "head" -> {
                    // Whistle: head tips back with a small overshoot while kneeling, then levels.
                    float up = kf(t, k(0, 0), k(14, 0), k(18, -0.48f), k(21, -0.38f),
                        k(28, -0.38f), k(35, 0.05f), k(38, 0), k(45, 0));
                    return new Vec3f(v.getX() + up, v.getY() + beckon * 0.25f, v.getZ());
                }
                case "body" -> {
                    float lean = kneel * 0.32f;
                    return new Vec3f(v.getX() + lean, v.getY(), v.getZ());
                }
            }
            return v;
        }
    }

    // Light-footed shadow-boxing: bounce, three snapping jabs with body English, guard up
    private static class PhysicalIntroAnimation extends IntroAnimation {
        @Override public @NotNull Vec3f get3DTransform(@NotNull String modelPart, @NotNull TransformType type,
                                                       float tickDelta, @NotNull Vec3f v) {
            float t = tick + tickDelta;
            float warm = kf(t, k(0, 0), k(5, 1), k(27, 1), k(33, 0), k(45, 0));
            float guard = kf(t, k(0, 0), k(29, 0), k(34, -2.15f), k(37, -1.9f), k(45, -1.9f));
            // Three keyed jabs (right, left, right), each with its own snap-out and pull-back.
            float rJab = kf(t, k(0, 0), k(6, 0.3f), k(9, -2.3f), k(12, -0.3f),
                k(20, 0.25f), k(23, -2.45f), k(26, -0.35f), k(30, 0), k(45, 0)) * warm;
            float lJab = kf(t, k(0, 0), k(12, 0.3f), k(15, -2.35f), k(18, -0.3f), k(28, 0), k(45, 0)) * warm;
            if (type == TransformType.POSITION && "body".equals(modelPart)) {
                float bounce = Math.abs((float) Math.sin(t * 0.42f)) * 0.32f * warm;
                return new Vec3f(v.getX(), v.getY() + bounce, v.getZ());
            }
            if (type != TransformType.ROTATION) return v;
            switch (modelPart) {
                case "rightArm" -> {
                    float armX = rJab + guard;
                    return new Vec3f(v.getX() + armX, v.getY() - kf(t, k(0, 0), k(29, 0), k(34, 0.55f), k(45, 0.5f)),
                        v.getZ() + kf(t, k(0, 0), k(29, 0), k(34, 0.65f), k(45, 0.6f)));
                }
                case "leftArm" -> {
                    float armX = lJab + guard;
                    return new Vec3f(v.getX() + armX, v.getY() + kf(t, k(0, 0), k(29, 0), k(34, 0.55f), k(45, 0.5f)),
                        v.getZ() - kf(t, k(0, 0), k(29, 0), k(34, 0.65f), k(45, 0.6f)));
                }
                case "body" -> {
                    // Body English: the torso counter-rotates into each jab, a tick behind the fist.
                    float twist = (rJab - lJab) * -0.16f
                        + kf(t, k(0, 0), k(30, 0), k(35, 0.08f), k(45, 0.06f));
                    return new Vec3f(v.getX(), v.getY() + twist, v.getZ());
                }
                case "head" -> {
                    float bob = (float) Math.sin(t * 0.42f - 0.5) * 0.07f * warm;
                    return new Vec3f(v.getX() + bob, v.getY() + (rJab - lJab) * -0.05f, v.getZ());
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

    // 10 ticks: windup(0-4) -> release(4-6) -> recovery(6-10)
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
