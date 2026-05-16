package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;

/**
 * Static factory methods that return composable {@link UsableItemHandler} building
 * blocks — the usable-item parallel of {@link Abilities}.
 *
 * <p>Chain blocks with {@link UsableItemHandler#and(UsableItemHandler)}:
 *
 * <pre>{@code
 * UsableItemHandler handler = ItemEffects.damageTarget(6)
 *     .and(ItemEffects.applyToTarget(CombatEffects.EffectType.POISON, 3, 0));
 * }</pre>
 *
 * <p>Blocks that need a combatant on the targeted tile ({@code damageTarget},
 * {@code applyToTarget}, {@code stunTarget}, {@code knockbackTarget}) fail cleanly when
 * the tile is empty, so the player keeps their AP.
 *
 * @since 0.2.0
 */
public final class ItemEffects {

    private ItemEffects() {}

    /** Heal the player by {@code amount}. */
    public static UsableItemHandler heal(int amount) {
        return ctx -> {
            ctx.healPlayer(amount);
            return ItemUseResult.ok();
        };
    }

    /** Apply a combat effect to the player. */
    public static UsableItemHandler applyToSelf(CombatEffects.EffectType type, int turns, int amplifier) {
        return ctx -> {
            ctx.applyPlayerEffect(type, turns, amplifier);
            return ItemUseResult.ok();
        };
    }

    /** Deal {@code amount} damage to the combatant on the targeted tile. */
    public static UsableItemHandler damageTarget(int amount) {
        return ctx -> {
            CombatEntity target = ctx.targetEntity();
            if (target == null || !target.isAlive()) {
                return ItemUseResult.fail("§cNo target there.");
            }
            ctx.damage(target, amount);
            return ItemUseResult.ok();
        };
    }

    /** Apply a combat effect to the combatant on the targeted tile. */
    public static UsableItemHandler applyToTarget(CombatEffects.EffectType type, int turns, int amplifier) {
        return ctx -> {
            CombatEntity target = ctx.targetEntity();
            if (target == null || !target.isAlive()) {
                return ItemUseResult.fail("§cNo target there.");
            }
            ctx.applyEffect(target, type, turns, amplifier);
            return ItemUseResult.ok();
        };
    }

    /** Stun the combatant on the targeted tile. */
    public static UsableItemHandler stunTarget() {
        return ctx -> {
            CombatEntity target = ctx.targetEntity();
            if (target == null || !target.isAlive()) {
                return ItemUseResult.fail("§cNo target there.");
            }
            ctx.stun(target);
            return ItemUseResult.ok();
        };
    }

    /** Push the combatant on the targeted tile up to {@code distance} tiles away. */
    public static UsableItemHandler knockbackTarget(int distance) {
        return ctx -> {
            CombatEntity target = ctx.targetEntity();
            if (target == null || !target.isAlive()) {
                return ItemUseResult.fail("§cNo target there.");
            }
            ctx.knockback(target, distance);
            return ItemUseResult.ok();
        };
    }

    /** Deal {@code amount} damage to every enemy within {@code radius} tiles of the target. */
    public static UsableItemHandler aoeDamage(int radius, int amount) {
        return ctx -> {
            for (CombatEntity entity : ctx.combatants()) {
                if (!entity.isAlive() || entity.isAlly()) {
                    continue;
                }
                if (ctx.targetPos().manhattanDistance(entity.getGridPos()) <= radius) {
                    ctx.damage(entity, amount);
                }
            }
            return ItemUseResult.ok();
        };
    }

    /** Teleport the player onto the targeted tile. */
    public static UsableItemHandler teleportToTarget() {
        return ctx -> {
            ctx.teleportPlayer(ctx.targetPos());
            return ItemUseResult.ok();
        };
    }

    /** Place a tile effect on the targeted tile. */
    public static UsableItemHandler placeTile(String effectType) {
        return ctx -> {
            ctx.placeTileEffect(ctx.targetPos(), effectType);
            return ItemUseResult.ok();
        };
    }

    /** Send a chat message to the player. */
    public static UsableItemHandler message(String text) {
        return ctx -> {
            ctx.message(text);
            return ItemUseResult.ok();
        };
    }
}
