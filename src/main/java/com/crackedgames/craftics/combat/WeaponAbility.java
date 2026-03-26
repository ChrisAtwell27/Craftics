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
     */
    public static int getAttackCost(Item weapon) {
        // Heavy weapons cost 2 AP
        if (weapon == Items.MACE) return 2;
        if (weapon == Items.CROSSBOW) return 2;
        if (weapon == Items.WOODEN_AXE || weapon == Items.STONE_AXE || weapon == Items.GOLDEN_AXE) return 2;
        if (weapon == Items.IRON_AXE || weapon == Items.DIAMOND_AXE || weapon == Items.NETHERITE_AXE) return 2;
        return 1;
    }

    /**
     * Apply weapon-specific effects after the primary attack.
     * Returns extra messages and any additional targets hit.
     */
    public static AttackResult applyAbility(ServerPlayerEntity player, Item weapon,
                                             CombatEntity target, GridArena arena,
                                             int baseDamage) {
        // No ability for fists, feathers, food, potions, or other non-weapon items
        if (!hasAbility(weapon)) {
            return new AttackResult(baseDamage, "");
        }

        List<String> messages = new ArrayList<>();
        List<CombatEntity> extraTargets = new ArrayList<>();
        int totalExtra = 0;

        // === SWORDS: Cleave ===
        // Iron Sword: cleave hits 1 adjacent enemy for half damage
        // Diamond Sword: cleave + 30% crit chance (double damage on main target)
        // Netherite Sword: cleave + execute (triple damage if target below 30% HP)
        if (weapon == Items.IRON_SWORD || weapon == Items.DIAMOND_SWORD || weapon == Items.NETHERITE_SWORD) {
            // Cleave: find one enemy adjacent to the target
            CombatEntity cleaveTarget = findAdjacentEnemy(arena, target);
            if (cleaveTarget != null) {
                int cleaveDmg = cleaveTarget.takeDamage(baseDamage / 2);
                extraTargets.add(cleaveTarget);
                totalExtra += cleaveDmg;
                messages.add("§e⚔ Cleave! " + cleaveTarget.getDisplayName() + " takes " + cleaveDmg + " splash damage!");
            }

            if (weapon == Items.DIAMOND_SWORD && Math.random() < 0.3) {
                messages.add("§6✦ CRITICAL HIT! Double damage!");
                return new AttackResult(baseDamage * 2 + totalExtra, messages, extraTargets);
            }

            if (weapon == Items.NETHERITE_SWORD && target.getCurrentHp() < target.getMaxHp() * 0.3) {
                messages.add("§4✦ EXECUTE! Triple damage on wounded target!");
                return new AttackResult(baseDamage * 3 + totalExtra, messages, extraTargets);
            }
        }

        // === AXES: Stun ===
        // Axes stun the target — they skip their next move
        if (weapon == Items.WOODEN_AXE || weapon == Items.STONE_AXE || weapon == Items.IRON_AXE
            || weapon == Items.DIAMOND_AXE || weapon == Items.GOLDEN_AXE || weapon == Items.NETHERITE_AXE) {
            target.setStunned(true);
            messages.add("§c✦ STUNNED! " + target.getDisplayName() + " can't move next turn!");
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

    private static CombatEntity findAdjacentEnemy(GridArena arena, CombatEntity target) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                GridPos adj = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
                CombatEntity other = arena.getOccupant(adj);
                if (other != null && other.isAlive() && other != target) {
                    return other;
                }
            }
        }
        return null;
    }

    /**
     * Check if a weapon has any special ability. Fists, feathers, food etc. do not.
     */
    public static boolean hasAbility(Item item) {
        // Swords
        if (item == Items.IRON_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD) return true;
        // Axes
        if (item == Items.WOODEN_AXE || item == Items.STONE_AXE || item == Items.IRON_AXE
            || item == Items.DIAMOND_AXE || item == Items.GOLDEN_AXE || item == Items.NETHERITE_AXE) return true;
        // Spears
        if (isSpear(item)) return true;
        // Mace
        if (item == Items.MACE) return true;
        // Crossbow
        if (item == Items.CROSSBOW) return true;
        return false;
    }

    private static boolean isSpear(Item item) {
        // Spears not available in 1.21.1
        return false;
    }
}
