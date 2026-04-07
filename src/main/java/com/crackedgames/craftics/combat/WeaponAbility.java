package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Unique weapon abilities that trigger during attacks.
 * Each weapon type has a distinct combat identity.
 *
 * Affinity-scaled effects (base % + affinity points * bonus per point):
 *   Slashing (Swords):  10% base + 5%/pt -> sweep adjacent enemy
 *   Cleaving (Axes):     5% base + 3%/pt -> ignore armor on hit
 *   Blunt:               5% base + 3%/pt -> stun target
 *   Water:               5% base + 3%/pt -> knockback + Wet debuff
 *
 * Ability logic is registered per-weapon in
 * {@link com.crackedgames.craftics.api.VanillaWeapons}. This class delegates
 * to the registry and retains shared utility methods.
 */
public class WeaponAbility {

    public record AttackResult(int totalDamage, List<String> messages, List<CombatEntity> extraTargets) {
        public AttackResult(int totalDamage, String message) {
            this(totalDamage, List.of(message), List.of());
        }
    }

    /**
     * Get the AP cost for attacking with this weapon.
     * Most weapons cost 1 AP. Heavy weapons cost 2.
     * Note: Trident AP cost is dynamic (1 melee / 2 throw) -- handled in CombatManager.
     */
    public static int getAttackCost(Item weapon) {
        return WeaponRegistry.getApCost(weapon);
    }

    /**
     * Apply weapon-specific effects after the primary attack.
     * Delegates to the ability handler registered in the WeaponRegistry.
     * Returns extra messages and any additional targets hit.
     */
    public static AttackResult applyAbility(ServerPlayerEntity player, Item weapon,
                                             CombatEntity target, GridArena arena,
                                             int baseDamage,
                                             PlayerProgression.PlayerStats playerStats,
                                             int luckPoints) {
        WeaponEntry entry = WeaponRegistry.get(weapon);
        if (entry.ability() == null) {
            return new AttackResult(baseDamage, "");
        }
        return entry.ability().apply(player, target, arena, baseDamage, playerStats, luckPoints);
    }

    /** Backwards-compatible overload for callers that don't have PlayerStats. */
    public static AttackResult applyAbility(ServerPlayerEntity player, Item weapon,
                                             CombatEntity target, GridArena arena,
                                             int baseDamage) {
        return applyAbility(player, weapon, target, arena, baseDamage, null, 0);
    }

    /**
     * Check if a weapon has any special ability. Fists, feathers, food etc. do not.
     */
    public static boolean hasAbility(Item item) {
        return WeaponRegistry.hasAbility(item);
    }

    /**
     * Get the break chance for non-durable weapons (items without vanilla durability).
     * Returns 0.0 for items that don't break (durable items or non-weapons).
     * These items have a chance to be consumed on each attack.
     */
    public static double getBreakChance(Item item) {
        return WeaponRegistry.getBreakChance(item);
    }

    /** Get a display string for the break chance (e.g., "10%" or null if no break chance). */
    public static String getBreakChanceDisplay(Item item) {
        double chance = getBreakChance(item);
        if (chance <= 0.0) return null;
        int pct = (int) (chance * 100);
        return pct + "%";
    }

    /**
     * Find up to maxTargets adjacent enemies around a target (for sweep and other AoE).
     * Checks all 8 surrounding tiles. Excludes allies and dead entities.
     */
    public static List<CombatEntity> findAdjacentEnemies(GridArena arena, CombatEntity target, int maxTargets) {
        List<CombatEntity> found = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                GridPos adj = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
                CombatEntity other = arena.getOccupant(adj);
                if (other != null && other.isAlive() && other != target && !other.isAlly()) {
                    found.add(other);
                    if (found.size() >= maxTargets) return found;
                }
            }
        }
        return found;
    }
}
