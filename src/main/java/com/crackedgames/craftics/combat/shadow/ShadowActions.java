package com.crackedgames.craftics.combat.shadow;

import com.crackedgames.craftics.api.CustomActionHandler;
import com.crackedgames.craftics.api.registry.CustomActionRegistry;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

/**
 * What happens when a Shadow uses an item out of its copied inventory.
 *
 * <p>A Shadow's attacks are ordinary enemy actions. Using an item is not something any other
 * enemy does, so it goes through the custom action hook that addons use: one registered id,
 * one handler, and no new case in the turn machine.
 */
public final class ShadowActions {

    private ShadowActions() {}

    /** Custom action id for "use the consumable the AI just picked". */
    public static final String USE_ITEM = "craftics:shadow_use_item";

    /**
     * Custom action id for "attack with the weapon the AI just picked". Not registered here:
     * a weapon swing has to move players and pets around the arena, which only the combat
     * manager can do, so it resolves this id itself (see {@code resolveShadowStrike}).
     */
    public static final String STRIKE = "craftics:shadow_strike";

    public static void register() {
        CustomActionRegistry.register(USE_ITEM, ShadowActions::useItem);
    }

    /** The buffs a Shadow can drink. Anything else a potion grants only means something to a player. */
    public static boolean canBuffSelfWith(CombatEffects.EffectType type) {
        return switch (type) {
            case STRENGTH, SPEED, RESISTANCE, REGENERATION, ABSORPTION -> true;
            default -> false;
        };
    }

    private static void useItem(CustomActionHandler.Context ctx) {
        CombatEntity self = ctx.self();
        if (!(self.getAiInstance() instanceof ShadowCloneAI ai)) return;
        ShadowCloneAI.PendingItem pending = ai.takePendingItem();
        if (pending == null) return;
        ShadowItem item = pending.item();
        String name = pending.displayName();

        BlockPos at = ctx.arena().gridToBlockPos(self.getGridPos());
        switch (item.use()) {
            case HEAL -> {
                int before = self.getCurrentHp();
                ctx.heal(self, pending.amount());
                ctx.world().spawnParticles(ParticleTypes.HEART,
                    at.getX() + 0.5, at.getY() + 2.2, at.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
                ctx.world().playSound(null, at, SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.HOSTILE, 1.0f, 0.7f);
                ctx.message("§5" + self.getDisplayName() + " uses your " + name + " and recovers "
                    + (self.getCurrentHp() - before) + " HP.");
            }
            case BUFF -> {
                CombatEffects.EffectType type = effectOf(item);
                if (type == null) return;
                int level = item.amount() + 1;
                switch (type) {
                    case STRENGTH -> self.applyAttackBuff(2 * level, item.turns());
                    case SPEED -> self.applySpeedBuff(level, item.turns());
                    case RESISTANCE -> self.applyResistance(level, item.turns());
                    case REGENERATION -> self.applyRegeneration(item.turns(), item.amount());
                    case ABSORPTION -> self.applyAbsorption(4 * level, item.turns());
                    default -> { return; }
                }
                ctx.world().playSound(null, at, SoundEvents.ENTITY_WITCH_DRINK, SoundCategory.HOSTILE, 1.0f, 0.7f);
                ctx.message("§5" + self.getDisplayName() + " drinks your " + name + ": "
                    + type.displayName + " for " + item.turns() + " turns.");
            }
            case DEBUFF -> {
                BlockPos target = ctx.arena().gridToBlockPos(ctx.playerPos());
                if (!ctx.tiles().isEmpty()) target = ctx.arena().gridToBlockPos(ctx.tiles().get(0));
                ctx.world().spawnParticles(ParticleTypes.EFFECT,
                    target.getX() + 0.5, target.getY() + 1.5, target.getZ() + 0.5, 20, 0.5, 0.4, 0.5, 0.1);
                ctx.world().playSound(null, target, SoundEvents.ENTITY_SPLASH_POTION_BREAK,
                    SoundCategory.HOSTILE, 1.0f, 0.8f);
                ctx.message("§5" + self.getDisplayName() + " throws your " + name + " at you!");
                CombatEffects.EffectType type = effectOf(item);
                if (type == null) {
                    ctx.damagePlayer(pending.amount());
                } else {
                    ctx.applyEffectToPlayer(type, item.turns(), item.amount());
                }
            }
            // Spells, blinks and summons are ordinary enemy actions and never arrive here.
            default -> { }
        }
    }

    private static CombatEffects.EffectType effectOf(ShadowItem item) {
        if (item.effect() == null || item.effect().isEmpty()) return null;
        try {
            return CombatEffects.EffectType.valueOf(item.effect());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
