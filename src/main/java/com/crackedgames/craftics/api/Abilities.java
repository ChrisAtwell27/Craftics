package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Static factory methods that return {@link WeaponAbilityHandler} instances.
 * Each method encapsulates a reusable combat pattern.
 * Handlers can be composed with {@link WeaponAbilityHandler#and(WeaponAbilityHandler)}.
 *
 * <p>Example:
 * <pre>
 *   WeaponAbilityHandler handler = Abilities.bleed()
 *       .and(Abilities.sweepAdjacent(0.10, 0.05));
 * </pre>
 */
public final class Abilities {

    private Abilities() {}

    // -------------------------------------------------------------------------
    // 1. bleed()
    // -------------------------------------------------------------------------

    /**
     * Reads the Sharpness enchant level from the player's weapon.
     * If sharpness > 0, applies that many bleed stacks to the target.
     */
    public static WeaponAbilityHandler bleed() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            int sharpness = PlayerCombatStats.getSharpness(player);
            if (sharpness > 0) {
                target.stackBleed(sharpness);
                messages.add("§c✦ Bleed! " + target.getDisplayName()
                        + " has " + target.getBleedStacks() + " bleed stacks.");
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        };
    }

    // -------------------------------------------------------------------------
    // 2. sweepAdjacent(baseChance, bonusPerPoint)
    // -------------------------------------------------------------------------

    /**
     * Affinity-scaled chance to hit one adjacent enemy for half base damage.
     * Chance = baseChance + (SLASHING affinity points * bonusPerPoint) + (luckPoints * 0.02).
     */
    public static WeaponAbilityHandler sweepAdjacent(double baseChance, double bonusPerPoint) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            List<CombatEntity> extraTargets = new ArrayList<>();
            int slashingPts = stats != null ? stats.getAffinityPoints(PlayerProgression.Affinity.SLASHING) : 0;
            double chance = baseChance + (slashingPts * bonusPerPoint) + (luckPoints * 0.02);
            if (Math.random() < chance) {
                List<CombatEntity> adjacent = findAdjacentEnemies(arena, target, 1);
                for (CombatEntity sweepTarget : adjacent) {
                    int sweepDmg = sweepTarget.takeDamage(baseDamage / 2);
                    extraTargets.add(sweepTarget);
                    messages.add("§e⚔ Sweep! " + sweepTarget.getDisplayName()
                            + " takes " + sweepDmg + " splash damage!");
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, extraTargets);
        };
    }

    // -------------------------------------------------------------------------
    // 3. armorIgnore(baseChance, bonusPerPoint)
    // -------------------------------------------------------------------------

    /**
     * Affinity-scaled chance to PERMANENTLY destroy a portion of the target's defense.
     * The destroyed defense is also dealt as bonus damage in the same swing — the axe
     * doesn't just bypass armor, it shatters it for the rest of the fight.
     * Uses CLEAVING affinity.
     * Chance = baseChance + (CLEAVING affinity points * bonusPerPoint) + (luckPoints * 0.02).
     * Destroyed amount = min(current defense, 2 + CLEAVING affinity points).
     */
    public static WeaponAbilityHandler armorIgnore(double baseChance, double bonusPerPoint) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            int cleavingPts = stats != null ? stats.getAffinityPoints(PlayerProgression.Affinity.CLEAVING) : 0;
            double chance = baseChance + (cleavingPts * bonusPerPoint) + (luckPoints * 0.02);
            int totalDamage = baseDamage;
            if (Math.random() < chance) {
                int def = target.getDefense();
                if (def > 0) {
                    int destroyed = Math.min(def, 2 + cleavingPts);
                    target.addPermanentDefReduction(destroyed);
                    int bonusDmg = target.takeDamage(destroyed);
                    totalDamage += bonusDmg;
                    int remaining = target.getDefense();
                    messages.add("§6✦ SHATTER ARMOR! §ePermanently destroyed " + destroyed
                        + " DEF for +" + bonusDmg + " damage! §7(" + remaining + " DEF remaining)");
                } else {
                    messages.add("§6✦ SHATTER ARMOR! §7(target has no armor left)");
                }
            }
            return new WeaponAbility.AttackResult(totalDamage, messages, List.of());
        };
    }

    // -------------------------------------------------------------------------
    // 4. stun(baseChance, bonusPerPoint)
    // -------------------------------------------------------------------------

    /**
     * Affinity-scaled chance to stun the target for one turn.
     * Uses BLUNT affinity.
     * Chance = baseChance + (BLUNT affinity points * bonusPerPoint) + (luckPoints * 0.02).
     */
    public static WeaponAbilityHandler stun(double baseChance, double bonusPerPoint) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            int bluntPts = stats != null ? stats.getAffinityPoints(PlayerProgression.Affinity.BLUNT) : 0;
            double chance = baseChance + (bluntPts * bonusPerPoint) + (luckPoints * 0.02);
            if (Math.random() < chance) {
                target.setStunned(true);
                messages.add("§8✦ STUNNED! " + target.getDisplayName() + " can't move next turn!");
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        };
    }

    // -------------------------------------------------------------------------
    // 5. knockbackDirection(distance)
    // -------------------------------------------------------------------------

    /**
     * Pushes the target away from the player by up to {@code distance} tiles.
     * Checks bounds, walkability, and occupancy for each step.
     */
    public static WeaponAbilityHandler knockbackDirection(int distance) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            GridPos pPos = arena.getPlayerGridPos();
            int dx = Integer.signum(target.getGridPos().x() - pPos.x());
            int dz = Integer.signum(target.getGridPos().z() - pPos.z());
            // Avoid zero-vector when player and target share a row/column axis
            if (dx == 0 && dz == 0) dx = 1;

            GridPos kbPos = target.getGridPos();
            int pushed = 0;
            for (int step = 0; step < distance; step++) {
                GridPos next = new GridPos(kbPos.x() + dx, kbPos.z() + dz);
                if (!arena.isInBounds(next) || arena.isOccupied(next)) break;
                var tile = arena.getTile(next);
                if (tile == null || !tile.isWalkable()) break;
                kbPos = next;
                pushed++;
            }
            if (pushed > 0) {
                arena.moveEntity(target, kbPos);
                if (target.getMobEntity() != null) {
                    var bp = arena.gridToBlockPos(kbPos);
                    target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                }
                messages.add("§6💨 Knocked back " + target.getDisplayName() + " " + pushed + " tile(s)!");
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        };
    }

    // -------------------------------------------------------------------------
    // 6. aoe(radius, damageMultiplier)
    // -------------------------------------------------------------------------

    /**
     * Hits all non-ally, alive enemies within {@code radius} Manhattan distance of the target
     * for {@code (int)(baseDamage * damageMultiplier)} damage each.
     */
    public static WeaponAbilityHandler aoe(int radius, double damageMultiplier) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            List<CombatEntity> extraTargets = new ArrayList<>();
            GridPos tPos = target.getGridPos();
            int aoeDmgBase = (int)(baseDamage * damageMultiplier);
            for (CombatEntity entity : arena.getOccupants().values()) {
                if (entity == target || !entity.isAlive() || entity.isAlly()) continue;
                if (tPos.manhattanDistance(entity.getGridPos()) <= radius) {
                    int dmg = entity.takeDamage(aoeDmgBase);
                    extraTargets.add(entity);
                    messages.add("§6💥 Shockwave hits " + entity.getDisplayName() + " for " + dmg + "!");
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, extraTargets);
        };
    }

    // -------------------------------------------------------------------------
    // 7. applyEffect(type, turns, amplifier)
    // -------------------------------------------------------------------------

    /**
     * Applies a status effect to the target using the appropriate stacking method.
     * Not all {@link CombatEffects.EffectType} values have a direct stacking method on
     * {@link CombatEntity}; unsupported types are silently ignored (see TODO below).
     */
    public static WeaponAbilityHandler applyEffect(CombatEffects.EffectType type, int turns, int amplifier) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            switch (type) {
                case POISON ->  {
                    target.stackPoison(turns, amplifier);
                    messages.add("§2✦ Poisoned! " + target.getDisplayName()
                            + " is poisoned for " + turns + " turn(s).");
                }
                case BURNING -> {
                    target.stackBurning(turns, amplifier);
                    messages.add("§6✦ Burning! " + target.getDisplayName()
                            + " is on fire for " + turns + " turn(s).");
                }
                case SOAKED -> {
                    target.stackSoaked(turns, amplifier);
                    messages.add("§3✦ Soaked! " + target.getDisplayName()
                            + " is drenched and slowed.");
                }
                case SLOWNESS -> {
                    target.stackSlowness(turns, amplifier);
                    messages.add("§7✦ Slowed! " + target.getDisplayName()
                            + " is slowed for " + turns + " turn(s).");
                }
                case CONFUSION -> {
                    target.stackConfusion(turns, amplifier);
                    messages.add("§d✦ Confused! " + target.getDisplayName()
                            + " is disoriented for " + turns + " turn(s).");
                }
                // TODO: WEAKNESS and other types that reduce attack require
                //   target.stackDefensePenalty() or target.setAttackPenalty() which do not
                //   match a generic EffectType cleanly — implement case-by-case as needed.
                default -> {
                    // Effect type not yet supported via direct CombatEntity method — no-op.
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        };
    }

    // -------------------------------------------------------------------------
    // 8. pierce()
    // -------------------------------------------------------------------------

    /**
     * Hits the first enemy behind the target in the player→target direction.
     * Full base damage is dealt to the pierced entity.
     */
    public static WeaponAbilityHandler pierce() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            List<CombatEntity> extraTargets = new ArrayList<>();
            GridPos pPos = arena.getPlayerGridPos();
            int dx = Integer.signum(target.getGridPos().x() - pPos.x());
            int dz = Integer.signum(target.getGridPos().z() - pPos.z());
            GridPos check = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
            if (arena.isInBounds(check)) {
                CombatEntity pierced = arena.getOccupant(check);
                if (pierced != null && pierced.isAlive() && !pierced.isAlly()) {
                    int pierceDmg = pierced.takeDamage(baseDamage);
                    extraTargets.add(pierced);
                    messages.add("§b⚔ PIERCE! Hit " + pierced.getDisplayName()
                            + " behind for " + pierceDmg + "!");
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, extraTargets);
        };
    }

    // -------------------------------------------------------------------------
    // 9. fireDamage(bonusDmg)
    // -------------------------------------------------------------------------

    /**
     * Sets the target entity on fire (if it has a mob entity) and deals {@code bonusDmg}
     * flat bonus fire damage.
     */
    public static WeaponAbilityHandler fireDamage(int bonusDmg) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            if (target.getMobEntity() != null) {
                target.getMobEntity().setFireTicks(100);
            }
            int actualFireDmg = target.takeDamage(bonusDmg);
            int totalDamage = baseDamage + actualFireDmg;
            messages.add("§6✖ Blaze Rod scorches " + target.getDisplayName()
                    + " for +" + actualFireDmg + " fire damage!");
            return new WeaponAbility.AttackResult(totalDamage, messages, List.of());
        };
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Returns up to {@code maxTargets} alive, non-ally enemies occupying tiles adjacent
     * (including diagonals) to the given target.
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
