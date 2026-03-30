package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Unique weapon abilities that trigger during attacks.
 * Each weapon type has a distinct combat identity.
 *
 * Affinity-scaled effects (base % + affinity points * bonus per point):
 *   Slashing (Swords):  10% base + 5%/pt → sweep adjacent enemy
 *   Cleaving (Axes):     5% base + 3%/pt → ignore armor on hit
 *   Blunt:               5% base + 3%/pt → stun target
 *   Water:               5% base + 3%/pt → knockback + Wet debuff
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
     * Note: Trident AP cost is dynamic (1 melee / 2 throw) — handled in CombatManager.
     */
    public static int getAttackCost(Item weapon) {
        // Heavy weapons cost 2 AP
        if (weapon == Items.MACE) return 2;
        if (weapon == Items.CROSSBOW) return 2;
        if (weapon == Items.WOODEN_AXE || weapon == Items.STONE_AXE || weapon == Items.GOLDEN_AXE) return 2;
        if (weapon == Items.IRON_AXE || weapon == Items.DIAMOND_AXE || weapon == Items.NETHERITE_AXE) return 2;
        // Trident defaults to 1 AP (melee); throw cost (2 AP) overridden in CombatManager
        return 1;
    }

    /**
     * Apply weapon-specific effects after the primary attack.
     * Returns extra messages and any additional targets hit.
     */
    public static AttackResult applyAbility(ServerPlayerEntity player, Item weapon,
                                             CombatEntity target, GridArena arena,
                                             int baseDamage,
                                             PlayerProgression.PlayerStats playerStats) {
        // No ability for fists, feathers, food, potions, or other non-weapon items
        if (!hasAbility(weapon)) {
            return new AttackResult(baseDamage, "");
        }

        List<String> messages = new ArrayList<>();
        List<CombatEntity> extraTargets = new ArrayList<>();
        int totalExtra = 0;

        // === SWORDS: Chance-based Sweep ===
        // Base 10% chance + 5% per Slashing affinity point
        // Sweeping Edge enchantment increases max sweep targets beyond 1
        if (isSword(weapon)) {
            int slashingPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.SLASHING) : 0;
            double sweepChance = 0.10 + (slashingPts * 0.05);
            int sweepingEdge = PlayerCombatStats.getSweepingEdge(player);
            int maxSweepTargets = 1 + sweepingEdge; // base 1, +1 per enchant level

            if (Math.random() < sweepChance) {
                List<CombatEntity> sweepTargets = findAdjacentEnemies(arena, target, maxSweepTargets);
                for (CombatEntity sweepTarget : sweepTargets) {
                    int sweepDmg = sweepTarget.takeDamage(baseDamage / 2);
                    extraTargets.add(sweepTarget);
                    totalExtra += sweepDmg;
                    messages.add("§e⚔ Sweep! " + sweepTarget.getDisplayName() + " takes " + sweepDmg + " splash damage!");
                }
                if (sweepTargets.isEmpty()) {
                    // Sweep triggered but no adjacent enemies
                } else if (sweepingEdge > 0) {
                    messages.add("§7  Sweeping Edge " + sweepingEdge + ": up to " + maxSweepTargets + " targets");
                }
            }

            // Diamond Sword: 30% crit (double damage)
            if (weapon == Items.DIAMOND_SWORD && Math.random() < 0.3) {
                messages.add("§6✦ CRITICAL HIT! Double damage!");
                return new AttackResult(baseDamage * 2 + totalExtra, messages, extraTargets);
            }

            // Netherite Sword: execute (triple damage if target below 30% HP)
            if (weapon == Items.NETHERITE_SWORD && target.getCurrentHp() < target.getMaxHp() * 0.3) {
                messages.add("§4✦ EXECUTE! Triple damage on wounded target!");
                return new AttackResult(baseDamage * 3 + totalExtra, messages, extraTargets);
            }
        }

        // === AXES: Chance-based Armor Ignore ===
        // Base 5% chance + 3% per Cleaving affinity point
        // When triggered, this attack ignores all target defense
        if (isAxe(weapon)) {
            int cleavingPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.CLEAVING) : 0;
            double armorIgnoreChance = 0.05 + (cleavingPts * 0.03);

            if (Math.random() < armorIgnoreChance) {
                // Deal bonus damage equal to the target's defense (simulates ignoring armor)
                int targetDef = target.getDefense();
                if (targetDef > 0) {
                    int bonusDmg = target.takeDamage(targetDef);
                    totalExtra += bonusDmg;
                    messages.add("§6✦ ARMOR CRUSH! Ignores " + targetDef + " defense for +" + bonusDmg + " damage!");
                } else {
                    messages.add("§6✦ ARMOR CRUSH! (target has no armor)");
                }
            }
        }

        // === BLUNT: Chance-based Stun ===
        // Base 5% chance + 3% per Blunt affinity point
        if (weapon == Items.MACE || weapon == Items.STICK || weapon == Items.BAMBOO) {
            int bluntPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.BLUNT) : 0;
            double stunChance = 0.05 + (bluntPts * 0.03);

            if (Math.random() < stunChance) {
                target.setStunned(true);
                messages.add("§8✦ STUNNED! " + target.getDisplayName() + " can't move next turn!");
            }
        }

        // === WATER: Chance-based Knockback + Wet ===
        // Base 5% chance + 3% per Water affinity point
        // Applies to Trident and Coral weapons
        if (weapon == Items.TRIDENT || DamageType.isCoral(weapon)) {
            int waterPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.WATER) : 0;
            double waterChance = 0.05 + (waterPts * 0.03);

            if (Math.random() < waterChance) {
                // Apply Soaked (Wet) debuff
                target.setSoakedTurns(Math.max(target.getSoakedTurns(), 2));
                target.setSoakedAmplifier(Math.max(target.getSoakedAmplifier(), 0));

                // Knockback 1 tile
                GridPos pPos = arena.getPlayerGridPos();
                int wdx = Integer.signum(target.getGridPos().x() - pPos.x());
                int wdz = Integer.signum(target.getGridPos().z() - pPos.z());
                GridPos kbPos = new GridPos(target.getGridPos().x() + wdx, target.getGridPos().z() + wdz);
                boolean knockedBack = false;
                if (arena.isInBounds(kbPos) && !arena.isOccupied(kbPos)) {
                    var tile = arena.getTile(kbPos);
                    if (tile != null && tile.isWalkable()) {
                        arena.moveEntity(target, kbPos);
                        if (target.getMobEntity() != null) {
                            var bp = arena.gridToBlockPos(kbPos);
                            target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                        }
                        knockedBack = true;
                    }
                }
                String kbMsg = knockedBack ? " + knocked back!" : "";
                messages.add("§3✦ SOAKED! " + target.getDisplayName() + " is drenched and slowed" + kbMsg);
            }
        }

        // === SPEARS: Pierce ===
        // Spears hit ALL enemies in a straight line from the player through the target
        if (isSpear(weapon)) {
            GridPos pPos = arena.getPlayerGridPos();
            int dx = Integer.signum(target.getGridPos().x() - pPos.x());
            int dz = Integer.signum(target.getGridPos().z() - pPos.z());
            // Check tiles beyond the target in the same direction
            GridPos check = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
            if (arena.isInBounds(check)) {
                CombatEntity pierced = arena.getOccupant(check);
                if (pierced != null && pierced.isAlive()) {
                    int pierceDmg = pierced.takeDamage(baseDamage);
                    extraTargets.add(pierced);
                    totalExtra += pierceDmg;
                    messages.add("§b⚔ PIERCE! Hit " + pierced.getDisplayName() + " behind for " + pierceDmg + "!");
                }
            }
        }

        // === MACE: AoE + Knockback ===
        // Density: +1 bonus AoE damage per level
        // Breach: ignore 2 defense per level on primary target
        // Wind Burst: +1 knockback range per level
        if (weapon == Items.MACE) {
            int densityLevel = PlayerCombatStats.getDensity(player);
            int breachLevel = PlayerCombatStats.getBreach(player);
            int windBurstLevel = PlayerCombatStats.getWindBurst(player);

            // Breach: deal bonus damage to primary target (armor penetration)
            if (breachLevel > 0) {
                int breachDmg = target.takeDamage(breachLevel * 2);
                messages.add("§4Breach! Pierces armor for +" + breachDmg + " damage.");
            }

            // AoE: hit all enemies in 3x3 around target
            // Density: bonus damage per level to shockwave hits
            int aoeBonusDmg = densityLevel;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    GridPos aoePos = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
                    CombatEntity aoeTarget = arena.getOccupant(aoePos);
                    if (aoeTarget != null && aoeTarget.isAlive() && aoeTarget != target) {
                        int aoeDmg = aoeTarget.takeDamage(baseDamage / 2 + aoeBonusDmg);
                        extraTargets.add(aoeTarget);
                        totalExtra += aoeDmg;
                        String densityTag = densityLevel > 0 ? " §8[Density +" + aoeBonusDmg + "]" : "";
                        messages.add("§6💥 Shockwave hits " + aoeTarget.getDisplayName() + " for " + aoeDmg + "!" + densityTag);
                    }
                }
            }

            // Knockback main target — Wind Burst extends range
            int kbRange = 1 + windBurstLevel;
            GridPos pPos = arena.getPlayerGridPos();
            int dx = Integer.signum(target.getGridPos().x() - pPos.x());
            int dz = Integer.signum(target.getGridPos().z() - pPos.z());
            GridPos kbPos = target.getGridPos();
            for (int i = 0; i < kbRange; i++) {
                GridPos nextKb = new GridPos(kbPos.x() + dx, kbPos.z() + dz);
                if (!arena.isInBounds(nextKb) || arena.isOccupied(nextKb)) break;
                var tile = arena.getTile(nextKb);
                if (tile == null || !tile.isWalkable()) break;
                kbPos = nextKb;
            }
            if (!kbPos.equals(target.getGridPos())) {
                arena.moveEntity(target, kbPos);
                if (target.getMobEntity() != null) {
                    var bp = arena.gridToBlockPos(kbPos);
                    target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                }
                String windTag = windBurstLevel > 0 ? " §b[Wind Burst +" + windBurstLevel + "]" : "";
                messages.add("§6💨 Knocked back " + target.getDisplayName() + " " + kbRange + " tiles!" + windTag);
            }
        }

        // === BLAZE ROD: Fire on hit ===
        if (weapon == Items.BLAZE_ROD) {
            if (target.getMobEntity() != null) {
                target.getMobEntity().setFireTicks(100);
            }
            int fireDmg = target.takeDamage(1);
            totalExtra += fireDmg;
            messages.add("\u00a76\u2716 Blaze Rod scorches " + target.getDisplayName() + " for +" + fireDmg + " fire damage!");

            // Blunt stun check for blaze rod too
            int bluntPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.BLUNT) : 0;
            double stunChance = 0.05 + (bluntPts * 0.03);
            if (Math.random() < stunChance) {
                target.setStunned(true);
                messages.add("§8✦ STUNNED! " + target.getDisplayName() + " can't move next turn!");
            }
        }

        // === BREEZE ROD: Knockback 1 tile ===
        if (weapon == Items.BREEZE_ROD) {
            GridPos pPos = arena.getPlayerGridPos();
            int dx = Integer.signum(target.getGridPos().x() - pPos.x());
            int dz = Integer.signum(target.getGridPos().z() - pPos.z());
            GridPos kbPos = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
            if (arena.isInBounds(kbPos) && !arena.isOccupied(kbPos)) {
                var tile = arena.getTile(kbPos);
                if (tile != null && tile.isWalkable()) {
                    arena.moveEntity(target, kbPos);
                    if (target.getMobEntity() != null) {
                        var bp = arena.gridToBlockPos(kbPos);
                        target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                    }
                    messages.add("\u00a7b\u2716 Breeze Rod knocks " + target.getDisplayName() + " back 1 tile!");
                }
            }

            // Blunt stun check for breeze rod too
            int bluntPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.BLUNT) : 0;
            double stunChance = 0.05 + (bluntPts * 0.03);
            if (Math.random() < stunChance) {
                target.setStunned(true);
                messages.add("§8✦ STUNNED! " + target.getDisplayName() + " can't move next turn!");
            }
        }

        // === CORAL WEAPONS ===
        if (DamageType.isCoral(weapon)) {
            // Tube Coral: Soaked (reduces speed)
            if (weapon == Items.TUBE_CORAL) {
                target.setSoakedTurns(Math.max(target.getSoakedTurns(), 1));
                target.setSoakedAmplifier(Math.max(target.getSoakedAmplifier(), 0));
                messages.add("\u00a73\u2716 Soaked! " + target.getDisplayName() + " is drenched and slowed!");
            }
            // Brain Coral: Confusion (chance to skip action)
            if (weapon == Items.BRAIN_CORAL) {
                if (Math.random() < 0.4) {
                    target.setConfusionTurns(Math.max(target.getConfusionTurns(), 1));
                    target.setConfusionAmplifier(Math.max(target.getConfusionAmplifier(), 0));
                    messages.add("\u00a7d\u2716 Confused! " + target.getDisplayName() + " is disoriented!");
                }
            }
            // Bubble Coral: Knockback 1 tile
            if (weapon == Items.BUBBLE_CORAL) {
                GridPos pPos = arena.getPlayerGridPos();
                int bdx = Integer.signum(target.getGridPos().x() - pPos.x());
                int bdz = Integer.signum(target.getGridPos().z() - pPos.z());
                GridPos kbPos = new GridPos(target.getGridPos().x() + bdx, target.getGridPos().z() + bdz);
                if (arena.isInBounds(kbPos) && !arena.isOccupied(kbPos)) {
                    var tile = arena.getTile(kbPos);
                    if (tile != null && tile.isWalkable()) {
                        arena.moveEntity(target, kbPos);
                        if (target.getMobEntity() != null) {
                            var bp = arena.gridToBlockPos(kbPos);
                            target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                        }
                        messages.add("\u00a7b\u2716 Bubble burst! " + target.getDisplayName() + " knocked back 1 tile!");
                    }
                }
            }
            // Fire Coral: Extra damage to burning enemies
            if (weapon == Items.FIRE_CORAL) {
                if (target.getBurningTurns() > 0) {
                    int bonusDmg = target.takeDamage(3);
                    totalExtra += bonusDmg;
                    messages.add("\u00a76\u2716 Searing sting! +" + bonusDmg + " bonus damage to burning target!");
                }
            }
            // Horn Coral: Defense pierce (ignores 3 defense)
            if (weapon == Items.HORN_CORAL) {
                int currentPenalty = target.getDefensePenalty();
                target.setDefensePenalty(currentPenalty + 3);
                target.setDefensePenaltyTurns(Math.max(target.getDefensePenaltyTurns(), 1));
                messages.add("\u00a7e\u2716 Armor pierced! " + target.getDisplayName() + " loses 3 DEF for 1 turn!");
            }
            // Dead Corals: Weakened (reduce attack)
            if (weapon == Items.DEAD_TUBE_CORAL || weapon == Items.DEAD_BRAIN_CORAL
                || weapon == Items.DEAD_BUBBLE_CORAL || weapon == Items.DEAD_FIRE_CORAL
                || weapon == Items.DEAD_HORN_CORAL
                || weapon == Items.DEAD_TUBE_CORAL_FAN || weapon == Items.DEAD_BRAIN_CORAL_FAN
                || weapon == Items.DEAD_BUBBLE_CORAL_FAN || weapon == Items.DEAD_FIRE_CORAL_FAN
                || weapon == Items.DEAD_HORN_CORAL_FAN) {
                target.setAttackPenalty(Math.max(target.getAttackPenalty(), 2));
                messages.add("\u00a77\u2716 Weakened! " + target.getDisplayName() + " loses 2 ATK for 1 turn!");
            }
            // Coral Fans: AoE splash to all adjacent enemies
            if (weapon == Items.TUBE_CORAL_FAN || weapon == Items.BRAIN_CORAL_FAN
                || weapon == Items.BUBBLE_CORAL_FAN || weapon == Items.FIRE_CORAL_FAN
                || weapon == Items.HORN_CORAL_FAN) {
                for (int fdx = -1; fdx <= 1; fdx++) {
                    for (int fdz = -1; fdz <= 1; fdz++) {
                        if (fdx == 0 && fdz == 0) continue;
                        GridPos adj = new GridPos(target.getGridPos().x() + fdx, target.getGridPos().z() + fdz);
                        CombatEntity splash = arena.getOccupant(adj);
                        if (splash != null && splash.isAlive() && splash != target) {
                            int splashDmg = splash.takeDamage(baseDamage);
                            extraTargets.add(splash);
                            totalExtra += splashDmg;
                            messages.add("\u00a73\u2716 Splash! " + splash.getDisplayName() + " hit for " + splashDmg + "!");
                        }
                    }
                }
            }
        }

        // === CROSSBOW: Pierce through ===
        // Bolt continues through the first target to hit a second
        if (weapon == Items.CROSSBOW) {
            GridPos pPos = arena.getPlayerGridPos();
            int dx = Integer.signum(target.getGridPos().x() - pPos.x());
            int dz = Integer.signum(target.getGridPos().z() - pPos.z());
            // Check all tiles beyond target in line
            GridPos check = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
            while (arena.isInBounds(check)) {
                CombatEntity pierced = arena.getOccupant(check);
                if (pierced != null && pierced.isAlive()) {
                    int pierceDmg = pierced.takeDamage(baseDamage / 2);
                    extraTargets.add(pierced);
                    totalExtra += pierceDmg;
                    messages.add("§b⚔ Bolt pierces through to " + pierced.getDisplayName() + " for " + pierceDmg + "!");
                    break;
                }
                check = new GridPos(check.x() + dx, check.z() + dz);
            }
        }

        return new AttackResult(baseDamage + totalExtra, messages, extraTargets);
    }

    /** Backwards-compatible overload for callers that don't have PlayerStats. */
    public static AttackResult applyAbility(ServerPlayerEntity player, Item weapon,
                                             CombatEntity target, GridArena arena,
                                             int baseDamage) {
        return applyAbility(player, weapon, target, arena, baseDamage, null);
    }

    /** Find up to maxTargets adjacent enemies around a target (for sweep). */
    private static List<CombatEntity> findAdjacentEnemies(GridArena arena, CombatEntity target, int maxTargets) {
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

    private static boolean isSword(Item item) {
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD
            || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD
            || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
    }

    private static boolean isAxe(Item item) {
        return item == Items.WOODEN_AXE || item == Items.STONE_AXE || item == Items.IRON_AXE
            || item == Items.DIAMOND_AXE || item == Items.GOLDEN_AXE || item == Items.NETHERITE_AXE;
    }

    /**
     * Check if a weapon has any special ability. Fists, feathers, food etc. do not.
     */
    public static boolean hasAbility(Item item) {
        // Swords (all tiers now have sweep chance)
        if (isSword(item)) return true;
        // Axes
        if (isAxe(item)) return true;
        // Spears
        if (isSpear(item)) return true;
        // Mace
        if (item == Items.MACE) return true;
        // Crossbow
        if (item == Items.CROSSBOW) return true;
        // Blunt rods
        if (item == Items.BLAZE_ROD || item == Items.BREEZE_ROD) return true;
        // Blunt basics
        if (item == Items.STICK || item == Items.BAMBOO) return true;
        // Coral weapons
        if (DamageType.isCoral(item)) return true;
        // Trident (Water affinity effect)
        if (item == Items.TRIDENT) return true;
        return false;
    }

    private static boolean isSpear(Item item) {
        // Spears not available in 1.21.1
        return false;
    }
}
