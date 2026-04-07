package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Lifecycle callback interface for custom combat effects.
 * <p>
 * Addon mods implement this interface to create fully custom combat behaviors
 * (damage reflection, death prevention, conditional buffs, etc.).
 * Override only the callbacks you need — all methods have default no-op implementations.
 * <p>
 * Handlers are stateful per combat encounter — instance fields reset each fight
 * because the EquipmentScanner creates fresh instances at combat start.
 * <p>
 * Register via {@code StatModifiers.addCombatEffect(name, handler)} inside an
 * {@link EquipmentScanner}.
 */
public interface CombatEffectHandler {

    // === Core combat lifecycle ===

    /** Fires once when combat begins, before the first turn. */
    default void onCombatStart(CombatEffectContext ctx) {}

    /** Fires at the start of the player's turn. */
    default void onTurnStart(CombatEffectContext ctx) {}

    /** Fires at the end of the player's turn, before enemies act. */
    default void onTurnEnd(CombatEffectContext ctx) {}

    /** Fires when combat ends (victory or defeat). */
    default void onCombatEnd(CombatEffectContext ctx) {}

    // === Damage dealing ===

    /** Fires when the player deals damage. Can modify the damage amount. */
    default CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
        return CombatResult.unchanged(damage);
    }

    /** Fires when the player's attack kills an enemy. */
    default void onDealKillingBlow(CombatEffectContext ctx, CombatEntity killed) {}

    /** Fires when the player lands a critical hit. */
    default void onCrit(CombatEffectContext ctx, CombatEntity target, int damage) {}

    /** Fires when the player's attack misses (if miss mechanics exist). */
    default void onMiss(CombatEffectContext ctx, CombatEntity target) {}

    // === Damage receiving ===

    /** Fires when the player takes damage. Can modify the damage amount. */
    default CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
        return CombatResult.unchanged(damage);
    }

    /** Fires when damage would kill the player. Can cancel to prevent death. */
    default CombatResult onLethalDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
        return CombatResult.unchanged(damage);
    }

    /** Fires when the player dodges an attack (e.g., Ethereal set bonus). */
    default void onDodge(CombatEffectContext ctx, CombatEntity attacker) {}

    /** Fires when the player's shield blocks damage. */
    default void onBlocked(CombatEffectContext ctx, CombatEntity attacker, int blockedDamage) {}

    // === Movement ===

    /** Fires after the player moves on the grid. */
    default void onMove(CombatEffectContext ctx, GridPos from, GridPos to, int distance) {}

    /** Fires when the player is knocked back. Can modify knockback distance. */
    default CombatResult onKnockback(CombatEffectContext ctx, CombatEntity source, int distance) {
        return CombatResult.unchanged(distance);
    }

    // === Allies/Pets ===

    /** Fires when a pet/ally deals damage to an enemy. */
    default void onAllyAttack(CombatEffectContext ctx, CombatEntity ally, CombatEntity target, int damage) {}

    /** Fires when a pet/ally takes damage. Can modify the damage amount. */
    default CombatResult onAllyTakeDamage(CombatEffectContext ctx, CombatEntity ally, CombatEntity attacker, int damage) {
        return CombatResult.unchanged(damage);
    }

    /** Fires when a pet/ally kills an enemy. */
    default void onAllyKill(CombatEffectContext ctx, CombatEntity ally, CombatEntity killed) {}

    /** Fires when a pet/ally dies. */
    default void onAllyDeath(CombatEffectContext ctx, CombatEntity ally) {}

    // === Status effects ===

    /** Fires when a status effect is applied to the player. Can modify duration. */
    default CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns) {
        return CombatResult.unchanged(turns);
    }

    /** Fires when a status effect expires on the player. */
    default void onEffectExpired(CombatEffectContext ctx, CombatEffects.EffectType effect) {}

    // === Economy/Progression ===

    /** Fires when loot is rolled after completing a level. Modify the mutable list directly. */
    default void onLootRoll(CombatEffectContext ctx, List<ItemStack> loot) {}

    /** Fires when emeralds are awarded. Can modify the amount. */
    default CombatResult onEmeraldGain(CombatEffectContext ctx, int amount) {
        return CombatResult.unchanged(amount);
    }

    // === Enemy-specific ===

    /** Fires when an enemy spawns into the arena. */
    default void onEnemySpawn(CombatEffectContext ctx, CombatEntity enemy) {}

    /** Fires when a boss transitions to a new phase. */
    default void onBossPhaseChange(CombatEffectContext ctx, CombatEntity boss, int newPhase) {}
}
