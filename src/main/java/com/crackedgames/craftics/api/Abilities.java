package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CrafticsEnchantments;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.SwordAxeEnchantEffects;
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
 * <pre>{@code
 *   WeaponAbilityHandler handler = Abilities.sweepAdjacent(0.10, 0.05)
 *       .and(Abilities.stun(0.05, 0.03));
 * }</pre>
 *
 * @since 0.2.0
 */
public final class Abilities {

    private Abilities() {}

    // A bleed() factory (reading Sharpness off the held weapon) used to live here. It was
    // removed: Sharpness bleed is now automatic for EVERY melee weapon regardless of ability
    // handler (see VanillaWeapons.universalEnchantEffects), so composing this into a custom
    // handler would have double-applied Bleed stacks - the same risk the axe handler's old
    // Abilities.enchantKnockback() call had for Knockback. Nothing in this repo ever called
    // it besides a JSON datapack keyword (see WeaponJsonLoader, which now treats "bleed" as a
    // documented no-op instead) and these doc examples, so removing it costs nothing real.

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
                    messages.add("§eSweep! " + sweepTarget.getDisplayName()
                            + " takes " + sweepDmg + " splash damage.");
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, extraTargets);
        };
    }

    /**
     * Affinity-scaled chance to PERMANENTLY destroy a portion of the target's defense.
     * The destroyed defense is also dealt as bonus damage in the same swing: the axe
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
                    int remaining = target.getEffectiveDefense();
                    messages.add("§6SHATTER ARMOR! §ePermanently destroyed " + destroyed
                        + " DEF for +" + bonusDmg + " damage. §7(" + remaining + " DEF remaining)");
                } else {
                    messages.add("§6SHATTER ARMOR! §7(target has no armor left)");
                }
            }
            return new WeaponAbility.AttackResult(totalDamage, messages, List.of());
        };
    }

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
                messages.add("§8STUNNED! " + target.getDisplayName() + " can't move next turn.");
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        };
    }

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

            // Crater enchant on the held weapon: push further, and slamming into whatever
            // stopped the push hurts and Stuns. This loop only walks onto clear walkable
            // tiles, so "stopped short" is the collision signal.
            boolean crater = CrafticsEnchantments.heldLevel(player, CrafticsEnchantments.CRATER) > 0;
            int reach = crater ? distance + SwordAxeEnchantEffects.CRATER_EXTRA_TILES : distance;

            GridPos kbPos = target.getGridPos();
            int pushed = 0;
            boolean blocked = false;
            for (int step = 0; step < reach; step++) {
                GridPos next = new GridPos(kbPos.x() + dx, kbPos.z() + dz);
                if (!arena.isInBounds(next) || arena.isOccupied(next)) { blocked = true; break; }
                var tile = arena.getTile(next);
                if (tile == null || !tile.isWalkable()) { blocked = true; break; }
                kbPos = next;
                pushed++;
            }
            if (pushed > 0) {
                arena.moveEntity(target, kbPos);
                if (target.getMobEntity() != null) {
                    var bp = arena.gridToBlockPos(kbPos);
                    target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                }
                messages.add("§6Knocked back " + target.getDisplayName() + " " + pushed + " tile(s)!");
            }
            if (crater && blocked && target.isAlive()) {
                int dealt = target.takeDamage(SwordAxeEnchantEffects.CRATER_COLLISION_DAMAGE);
                target.setStunned(true);
                messages.add("§6Crater! " + target.getDisplayName() + " takes " + dealt
                    + " more from the impact and is Stunned!");
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        };
    }

    /**
     * Applies the player's Knockback enchant as a directional push, for melee weapons whose
     * base ability does not already handle it (axes, maces, etc.). No-op without the enchant.
     * Pushes (level + 1) tiles away from the player, matching the sword's push distance.
     */
    public static WeaponAbilityHandler enchantKnockback() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            int kb = PlayerCombatStats.getKnockback(player);
            if (kb <= 0) return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
            // Crater enchant: same boost as knockbackDirection - push further, slam stuns.
            boolean crater = CrafticsEnchantments.heldLevel(player, CrafticsEnchantments.CRATER) > 0;
            int distance = kb + 1 + (crater ? SwordAxeEnchantEffects.CRATER_EXTRA_TILES : 0);
            GridPos pPos = arena.getPlayerGridPos();
            int dx = Integer.signum(target.getGridPos().x() - pPos.x());
            int dz = Integer.signum(target.getGridPos().z() - pPos.z());
            if (dx == 0 && dz == 0) dx = 1;
            GridPos kbPos = target.getGridPos();
            int pushed = 0;
            boolean blocked = false;
            boolean intoVoid = false;
            for (int step = 0; step < distance; step++) {
                GridPos next = new GridPos(kbPos.x() + dx, kbPos.z() + dz);
                if (!arena.isInBounds(next) || arena.isOccupied(next)) { blocked = true; break; }
                var tile = arena.getTile(next);
                if (tile == null) { blocked = true; break; }
                if (tile.getType() == com.crackedgames.craftics.core.TileType.VOID
                        && !target.isHazardImmune()) {
                    kbPos = next;
                    pushed++;
                    intoVoid = true;
                    break;
                }
                if (!tile.isWalkable()) { blocked = true; break; }
                kbPos = next;
                pushed++;
            }
            if (pushed > 0) {
                arena.moveEntity(target, kbPos);
                if (target.getMobEntity() != null) {
                    var bp = arena.gridToBlockPos(kbPos);
                    target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                }
                messages.add("§6Knocked back " + target.getDisplayName() + " " + pushed + " tile(s)!");
                if (intoVoid) {
                    target.takeDamage(target.getCurrentHp() + 100);
                    messages.add("§4" + target.getDisplayName() + " fell into the void!");
                }
            }
            if (crater && blocked && target.isAlive()) {
                int dealt = target.takeDamage(SwordAxeEnchantEffects.CRATER_COLLISION_DAMAGE);
                target.setStunned(true);
                messages.add("§6Crater! " + target.getDisplayName() + " takes " + dealt
                    + " more from the impact and is Stunned!");
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        };
    }

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
                    messages.add("§6Shockwave hits " + entity.getDisplayName() + " for " + dmg + "!");
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, extraTargets);
        };
    }

    /**
     * Applies a status effect to the target using the appropriate stacking method.
     * Most {@link CombatEffects.EffectType} values map to a stacking method on
     * {@link CombatEntity}; anything still unmapped logs a warning rather than resolving as a
     * silent no-op, so an addon can tell the difference between "applied" and "ignored".
     */
    public static WeaponAbilityHandler applyEffect(CombatEffects.EffectType type, int turns, int amplifier) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            switch (type) {
                case POISON ->  {
                    target.stackPoison(turns, amplifier);
                    messages.add("§2Poisoned! " + target.getDisplayName()
                            + " is poisoned for " + turns + " turn(s).");
                }
                case BURNING -> {
                    target.stackBurning(turns, amplifier);
                    messages.add("§6Burning! " + target.getDisplayName()
                            + " is on fire for " + turns + " turn(s).");
                }
                case SOAKED -> {
                    target.stackSoaked(turns, amplifier);
                    messages.add("§3Soaked! " + target.getDisplayName()
                            + " is drenched and slowed.");
                }
                case SLOWNESS -> {
                    target.stackSlowness(turns, amplifier);
                    messages.add("§7Slowed! " + target.getDisplayName()
                            + " is slowed for " + turns + " turn(s).");
                }
                case CONFUSION -> {
                    // Nerf: confusion is never guaranteed - roll confusionApplyChance.
                    if (com.crackedgames.craftics.combat.ConfusionLogic.rollHits(
                            Math.random(),
                            com.crackedgames.craftics.CrafticsMod.CONFIG.confusionApplyChance())) {
                        target.stackConfusion(turns, amplifier);
                        messages.add("§dConfused! " + target.getDisplayName()
                                + " is disoriented for " + turns + " turn(s).");
                    }
                }
                // WEAKNESS has no stack* method because it isn't a counter - it's a flat attack
                // penalty with its own timer. Applied here as the strongest of the existing and
                // incoming penalty (so a weaker re-application can't overwrite a stronger one)
                // with the longer of the two durations, which is how the stack* helpers behave.
                case WEAKNESS -> {
                    int penalty = Math.max(1, amplifier + 1);
                    target.setAttackPenalty(Math.max(target.getAttackPenalty(), penalty));
                    target.setAttackPenaltyTurns(Math.max(target.getAttackPenaltyTurns(), turns));
                    messages.add("§8Weakened! " + target.getDisplayName()
                            + " hits for " + penalty + " less for " + turns + " turn(s).");
                }
                default -> {
                    // Anything still unmapped is a genuine gap rather than a silent success:
                    // log it once so an addon author sees why their effect did nothing instead
                    // of chasing a no-op through their own code.
                    com.crackedgames.craftics.CrafticsMod.LOGGER.warn(
                        "Abilities.applyEffect: no CombatEntity mapping for effect type {} - "
                        + "the ability resolved but applied nothing", type);
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        };
    }

    /**
     * Drives a shot through everything standing in its line, for full damage each.
     *
     * <p>Used to hit exactly one enemy: the single tile directly behind the target. That is not
     * what pierce means to a player - a bolt that goes through the first body should keep going
     * through the second and the third - and it is why a crossbow shot into a queue of skeletons
     * only ever killed two of them. The line now runs from the target to the edge of the arena,
     * stopping at a wall, and every enemy on it is a hop.
     *
     * <p>Planned rather than applied, so the arrow can be seen crossing all of them instead of
     * stopping at the first, and so each pierced enemy takes the shot through the ordinary
     * on-hit path (Flame, Punch, the lot) rather than as a bare point of damage.
     */
    public static WeaponAbilityHandler pierce() {
        return new WeaponAbilityHandler() {
            @Override
            public boolean isPlanned() {
                return true;
            }

            @Override
            public AbilityPlan plan(net.minecraft.server.network.ServerPlayerEntity player,
                                    CombatEntity target, GridArena arena, int baseDamage,
                                    PlayerProgression.PlayerStats stats, int luckPoints) {
                List<AbilityPlan.Hop> hops = new ArrayList<>();
                hops.add(new AbilityPlan.Hop(target, baseDamage));

                GridPos pPos = arena.getPlayerGridPos();
                GridPos tPos = target.getGridPos();
                int dx = Integer.signum(tPos.x() - pPos.x());
                int dz = Integer.signum(tPos.z() - pPos.z());
                if (dx == 0 && dz == 0) return new AbilityPlan(hops, false, List.of());

                // Identity, not position: a multi-tile mob stands on several tiles of the line
                // and must be pierced once, not once per tile it covers.
                List<CombatEntity> struck = new ArrayList<>();
                struck.add(target);
                GridPos step = new GridPos(tPos.x() + dx, tPos.z() + dz);
                while (arena.isInBounds(step)) {
                    var tile = arena.getTile(step);
                    // A wall stops the bolt. Anything it can be shot over does not.
                    if (tile != null && tile.getType() == com.crackedgames.craftics.core.TileType.OBSTACLE) break;
                    CombatEntity e = arena.getOccupant(step);
                    if (e != null && e.isAlive() && !e.isAlly() && !struck.contains(e)) {
                        struck.add(e);
                        hops.add(new AbilityPlan.Hop(e, baseDamage));
                    }
                    step = new GridPos(step.x() + dx, step.z() + dz);
                }
                return new AbilityPlan(hops, false, List.of());
            }

            @Override
            public WeaponAbility.AttackResult apply(net.minecraft.server.network.ServerPlayerEntity player,
                                                    CombatEntity target, GridArena arena, int baseDamage,
                                                    PlayerProgression.PlayerStats stats, int luckPoints) {
                // The line belongs to the plan. Re-piercing here would hit everything twice.
                return new WeaponAbility.AttackResult(baseDamage, List.of(), List.of());
            }
        };
    }

    /**
     * Sets the target entity on fire (if it has a mob entity) and deals {@code bonusDmg}
     * flat bonus fire damage.
     */
    public static WeaponAbilityHandler fireDamage(int bonusDmg) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            // Apply the turn-based Burning DoT so the fire persists across turns. Vanilla
            // fireTicks alone is zeroed every server tick by the stray-fire suppression in
            // CombatManager.tick(), so it never survives a whole turn - the burningTurns DoT
            // is what actually deals damage each turn (see CombatEntity.stackBurning + the
            // per-turn burn loop in CombatManager). Mirrors the Fire Aspect path.
            target.stackBurning(3, 0); // Burning I for 3 turns
            if (target.getMobEntity() != null) {
                target.getMobEntity().setFireTicks(3 * 80); // visual synced to burn turns
            }
            int actualFireDmg = target.takeDamage(bonusDmg);
            int totalDamage = baseDamage + actualFireDmg;
            messages.add("§6Blaze Rod scorches " + target.getDisplayName()
                    + " for +" + actualFireDmg + " fire damage!");
            return new WeaponAbility.AttackResult(totalDamage, messages, List.of());
        };
    }

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
