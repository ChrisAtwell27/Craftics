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
        // Heavy weapons cost 2+ AP
        if (weapon == Items.MACE) return 2;
        if (weapon == Items.CROSSBOW) return 4; // Quick Charge reduces this in CombatManager
        if (weapon == Items.WOODEN_AXE || weapon == Items.STONE_AXE || weapon == Items.GOLDEN_AXE) return 2;
        if (weapon == Items.IRON_AXE || weapon == Items.DIAMOND_AXE || weapon == Items.NETHERITE_AXE) return 2;
        // Trident defaults to 1 AP (melee); throw cost (2 AP) overridden in CombatManager
        return 1;
    }

    /**
     * Apply weapon-specific effects after the primary attack.
     * Returns extra messages and any additional targets hit.
     */
    /** Luck bonus per point added to all probability-based weapon effects. */
    private static final double LUCK_BONUS_PER_POINT = 0.02;

    public static AttackResult applyAbility(ServerPlayerEntity player, Item weapon,
                                             CombatEntity target, GridArena arena,
                                             int baseDamage,
                                             PlayerProgression.PlayerStats playerStats,
                                             int luckPoints) {
        if (!hasAbility(weapon)) {
            return new AttackResult(baseDamage, "");
        }

        double luckBonus = luckPoints * LUCK_BONUS_PER_POINT;
        List<String> messages = new ArrayList<>();
        List<CombatEntity> extraTargets = new ArrayList<>();
        int totalExtra = 0;

        if (isSword(weapon)) {
            // Sharpness: apply bleed stacks (each stack = +1 damage when attacked)
            int sharpness = PlayerCombatStats.getSharpness(player);
            if (sharpness > 0) {
                target.stackBleed(sharpness);
                messages.add("§c✦ Bleed! " + target.getDisplayName() + " has " + target.getBleedStacks() + " bleed stacks.");
            }

            // Smite: AoE radiant burst vs undead
            int smite = PlayerCombatStats.getSmite(player);
            if (smite > 0 && PlayerCombatStats.isUndead(target.getEntityTypeId())) {
                int smiteDmg = smite * 2;
                int smiteRadius = smite <= 2 ? 2 : (smite <= 4 ? 3 : 4);
                GridPos tPos = target.getGridPos();
                // Burst damage to all undead in radius
                for (CombatEntity enemy : arena.getOccupants().values()) {
                    if (enemy == target || !enemy.isAlive() || enemy.isAlly()) continue;
                    if (!PlayerCombatStats.isUndead(enemy.getEntityTypeId())) continue;
                    if (tPos.manhattanDistance(enemy.getGridPos()) <= smiteRadius) {
                        int burstDmg = enemy.takeDamage(smiteDmg);
                        extraTargets.add(enemy);
                        totalExtra += burstDmg;
                        messages.add("§e✦ Holy Radiance! " + enemy.getDisplayName() + " takes " + burstDmg + " radiant damage!");
                    }
                }
            }

            // Bane of Arthropods: poison + slowness vs arthropods
            int bane = PlayerCombatStats.getBane(player);
            if (bane > 0 && PlayerCombatStats.isArthropod(target.getEntityTypeId())) {
                target.stackPoison(3, bane);
                int slowPenalty = bane <= 2 ? 1 : (bane <= 4 ? 2 : 3);
                target.stackSlowness(3, slowPenalty);
                messages.add("§2✦ Venom! " + target.getDisplayName() + " is poisoned (" + bane + "/turn) and slowed (-" + slowPenalty + " speed).");
            }

            // Knockback: directional shockwave — push target + enemies behind in a line
            int knockback = PlayerCombatStats.getKnockback(player);
            if (knockback > 0) {
                int pushDist = knockback + 1; // Lv1 = 2, Lv2 = 3
                int collisionDmg = knockback * 2; // Lv1 = 2, Lv2 = 4
                GridPos pPos = arena.getPlayerGridPos();
                int dx = Integer.signum(target.getGridPos().x() - pPos.x());
                int dz = Integer.signum(target.getGridPos().z() - pPos.z());
                // Find all enemies in the shockwave line behind target
                List<CombatEntity> lineTargets = new ArrayList<>();
                lineTargets.add(target);
                GridPos scan = target.getGridPos();
                for (int i = 0; i < pushDist + 2; i++) {
                    scan = new GridPos(scan.x() + dx, scan.z() + dz);
                    if (!arena.isInBounds(scan)) break;
                    CombatEntity lineHit = arena.getOccupant(scan);
                    if (lineHit != null && lineHit.isAlive() && !lineHit.isAlly()) {
                        lineTargets.add(lineHit);
                    }
                }
                // Push each entity in reverse order (furthest first to avoid collisions)
                for (int i = lineTargets.size() - 1; i >= 0; i--) {
                    CombatEntity pushTarget = lineTargets.get(i);
                    GridPos kbPos = pushTarget.getGridPos();
                    boolean hitWall = false;
                    for (int step = 0; step < pushDist; step++) {
                        GridPos next = new GridPos(kbPos.x() + dx, kbPos.z() + dz);
                        if (!arena.isInBounds(next) || arena.isOccupied(next)) {
                            hitWall = true;
                            break;
                        }
                        var tile = arena.getTile(next);
                        if (tile == null || !tile.isWalkable()) { hitWall = true; break; }
                        kbPos = next;
                    }
                    if (!kbPos.equals(pushTarget.getGridPos())) {
                        arena.moveEntity(pushTarget, kbPos);
                        if (pushTarget.getMobEntity() != null) {
                            var bp = arena.gridToBlockPos(kbPos);
                            pushTarget.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                        }
                    }
                    // Collision damage if hit wall/obstacle
                    if (hitWall) {
                        int cDmg = pushTarget.takeDamage(collisionDmg);
                        totalExtra += cDmg;
                        if (pushTarget != target) extraTargets.add(pushTarget);
                        messages.add("§6💨 " + pushTarget.getDisplayName() + " slammed into obstacle for " + cDmg + " collision damage!");
                    } else if (pushTarget != target) {
                        extraTargets.add(pushTarget);
                        messages.add("§6💨 " + pushTarget.getDisplayName() + " pushed back " + pushDist + " tiles!");
                    }
                }
                if (!lineTargets.isEmpty()) {
                    messages.add("§6💨 Shockwave! Knocked back " + lineTargets.size() + " enemies!");
                }
            }

            // Sweeping Edge: 360 spin hitting ALL adjacent enemies
            int sweepingEdge = PlayerCombatStats.getSweepingEdge(player);
            if (sweepingEdge > 0) {
                double sweepDmgPct = sweepingEdge == 1 ? 0.60 : (sweepingEdge == 2 ? 0.75 : 0.90);
                int sweepKb = sweepingEdge >= 3 ? 1 : 0;
                List<CombatEntity> sweepTargets = findAdjacentEnemies(arena, target, 99); // all adjacent
                for (CombatEntity sweepTarget : sweepTargets) {
                    int sweepDmg = sweepTarget.takeDamage((int)(baseDamage * sweepDmgPct));
                    extraTargets.add(sweepTarget);
                    totalExtra += sweepDmg;
                    messages.add("§e⚔ Whirlwind! " + sweepTarget.getDisplayName() + " takes " + sweepDmg + " damage!");
                    // Lv3: knockback 1 tile
                    if (sweepKb > 0) {
                        GridPos pPos2 = target.getGridPos();
                        int sdx = Integer.signum(sweepTarget.getGridPos().x() - pPos2.x());
                        int sdz = Integer.signum(sweepTarget.getGridPos().z() - pPos2.z());
                        GridPos sweepKbPos = new GridPos(sweepTarget.getGridPos().x() + sdx, sweepTarget.getGridPos().z() + sdz);
                        if (arena.isInBounds(sweepKbPos) && !arena.isOccupied(sweepKbPos)) {
                            var tile = arena.getTile(sweepKbPos);
                            if (tile != null && tile.isWalkable()) {
                                arena.moveEntity(sweepTarget, sweepKbPos);
                                if (sweepTarget.getMobEntity() != null) {
                                    var bp = arena.gridToBlockPos(sweepKbPos);
                                    sweepTarget.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                                }
                            }
                        }
                    }
                }
            } else {
                // Base sword sweep (affinity-scaled, unchanged)
                int slashingPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.SLASHING) : 0;
                double sweepChance = 0.10 + (slashingPts * 0.05) + luckBonus;
                if (Math.random() < sweepChance) {
                    List<CombatEntity> sweepTargets = findAdjacentEnemies(arena, target, 1);
                    for (CombatEntity sweepTarget : sweepTargets) {
                        int sweepDmg = sweepTarget.takeDamage(baseDamage / 2);
                        extraTargets.add(sweepTarget);
                        totalExtra += sweepDmg;
                        messages.add("§e⚔ Sweep! " + sweepTarget.getDisplayName() + " takes " + sweepDmg + " splash damage!");
                    }
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
        // Base 5% chance + 3% per Cleaving affinity point + 2% per Luck point
        // When triggered, this attack ignores all target defense
        if (isAxe(weapon)) {
            int cleavingPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.CLEAVING) : 0;
            double armorIgnoreChance = 0.05 + (cleavingPts * 0.03) + luckBonus;

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
        // Base 5% chance + 3% per Blunt affinity point + 2% per Luck point
        if (weapon == Items.MACE || weapon == Items.STICK || weapon == Items.BAMBOO) {
            int bluntPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.BLUNT) : 0;
            double stunChance = 0.05 + (bluntPts * 0.03) + luckBonus;

            if (Math.random() < stunChance) {
                target.setStunned(true);
                messages.add("§8✦ STUNNED! " + target.getDisplayName() + " can't move next turn!");
            }
        }

        // === WATER: Chance-based Knockback + Wet ===
        // Base 5% chance + 3% per Water affinity point + 2% per Luck point
        // Applies to Trident and Coral weapons
        if (weapon == Items.TRIDENT || DamageType.isCoral(weapon)) {
            int waterPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.WATER) : 0;
            double waterChance = 0.05 + (waterPts * 0.03) + luckBonus;

            if (Math.random() < waterChance) {
                // Apply Soaked (Wet) debuff
                target.stackSoaked(2, 1);

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

        // === MACE: Density (gravity pull) + Breach (permanent def shred) + Wind Burst (knockback + chain bonus) ===
        if (weapon == Items.MACE) {
            int densityLevel = PlayerCombatStats.getDensity(player);
            int breachLevel = PlayerCombatStats.getBreach(player);
            int windBurstLevel = PlayerCombatStats.getWindBurst(player);

            // Breach: permanently reduce target defense
            if (breachLevel > 0) {
                target.addPermanentDefReduction(breachLevel);
                int remaining = target.getEffectiveDefense();
                messages.add("§4Breach! Shattered " + breachLevel + " DEF. " + target.getDisplayName() + " has " + remaining + " DEF remaining.");
            }

            // Density: gravity well — pull nearby enemies toward impact point
            if (densityLevel > 0) {
                int pullRadius = densityLevel <= 1 ? 2 : 3;
                int pullDist = densityLevel <= 2 ? 1 : 2;
                int crushBonus = densityLevel;
                GridPos impactPos = target.getGridPos();
                for (int dx = -pullRadius; dx <= pullRadius; dx++) {
                    for (int dz = -pullRadius; dz <= pullRadius; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        GridPos scanPos = new GridPos(impactPos.x() + dx, impactPos.z() + dz);
                        CombatEntity pullTarget = arena.getOccupant(scanPos);
                        if (pullTarget == null || !pullTarget.isAlive() || pullTarget == target || pullTarget.isAlly()) continue;
                        // Pull toward impact point
                        int pdx = Integer.signum(impactPos.x() - pullTarget.getGridPos().x());
                        int pdz = Integer.signum(impactPos.z() - pullTarget.getGridPos().z());
                        GridPos pullPos = pullTarget.getGridPos();
                        for (int step = 0; step < pullDist; step++) {
                            GridPos next = new GridPos(pullPos.x() + pdx, pullPos.z() + pdz);
                            if (!arena.isInBounds(next) || arena.isOccupied(next)) break;
                            var tile = arena.getTile(next);
                            if (tile == null || !tile.isWalkable()) break;
                            pullPos = next;
                        }
                        if (!pullPos.equals(pullTarget.getGridPos())) {
                            arena.moveEntity(pullTarget, pullPos);
                            if (pullTarget.getMobEntity() != null) {
                                var bp = arena.gridToBlockPos(pullPos);
                                pullTarget.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                            }
                            messages.add("§8✦ Gravity pulls " + pullTarget.getDisplayName() + " toward impact!");
                        }
                        // Crush bonus to enemies already adjacent to impact
                        if (impactPos.manhattanDistance(pullTarget.getGridPos()) <= 1) {
                            int crushDmg = pullTarget.takeDamage(crushBonus);
                            totalExtra += crushDmg;
                            extraTargets.add(pullTarget);
                            messages.add("§6💥 Crush! " + pullTarget.getDisplayName() + " takes " + crushDmg + " from compression!");
                        }
                    }
                }
            }

            // Base mace AoE shockwave (always active)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    GridPos aoePos = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
                    CombatEntity aoeTarget = arena.getOccupant(aoePos);
                    if (aoeTarget != null && aoeTarget.isAlive() && aoeTarget != target && !aoeTarget.isAlly()) {
                        int aoeDmg = aoeTarget.takeDamage(baseDamage / 2);
                        extraTargets.add(aoeTarget);
                        totalExtra += aoeDmg;
                        messages.add("§6💥 Shockwave hits " + aoeTarget.getDisplayName() + " for " + aoeDmg + "!");
                    }
                }
            }

            // Wind Burst: knockback ALL adjacent + accumulate bonus for next mace hit
            if (windBurstLevel > 0) {
                int wbKb = windBurstLevel;
                int wbNextBonus = windBurstLevel == 1 ? 2 : (windBurstLevel == 2 ? 3 : 4);
                GridPos pPos = arena.getPlayerGridPos();
                // Knockback all adjacent enemies (including target)
                List<CombatEntity> kbTargets = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        GridPos checkPos = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
                        CombatEntity kbTarget = arena.getOccupant(checkPos);
                        if (kbTarget != null && kbTarget.isAlive() && !kbTarget.isAlly()) {
                            kbTargets.add(kbTarget);
                        }
                    }
                }
                for (CombatEntity kbTarget : kbTargets) {
                    int kdx = Integer.signum(kbTarget.getGridPos().x() - pPos.x());
                    int kdz = Integer.signum(kbTarget.getGridPos().z() - pPos.z());
                    if (kdx == 0 && kdz == 0) kdx = 1;
                    GridPos kbPos = kbTarget.getGridPos();
                    for (int step = 0; step < wbKb; step++) {
                        GridPos next = new GridPos(kbPos.x() + kdx, kbPos.z() + kdz);
                        if (!arena.isInBounds(next) || arena.isOccupied(next)) break;
                        var tile = arena.getTile(next);
                        if (tile == null || !tile.isWalkable()) break;
                        kbPos = next;
                    }
                    if (!kbPos.equals(kbTarget.getGridPos())) {
                        arena.moveEntity(kbTarget, kbPos);
                        if (kbTarget.getMobEntity() != null) {
                            var bp = arena.gridToBlockPos(kbPos);
                            kbTarget.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                        }
                    }
                }
                messages.add("§b💨 Wind Burst! Shockwave knocks back enemies " + wbKb + " tiles. Next mace hit +" + wbNextBonus + " damage!");
                // Store bonus — CombatManager will consume it on next mace attack
                // We can't directly access CombatManager from here, so we signal via a return message tag
                // Actually, we'll handle this by reading a special message in CombatManager
                // Simpler: store on the arena (shared state). But cleanest: return it in AttackResult.
                // For now, use a convention: Wind Burst bonus message contains "[WB_BONUS:N]"
                messages.add("[WB_BONUS:" + wbNextBonus + "]");
            } else {
                // Default mace knockback (no Wind Burst)
                GridPos pPos = arena.getPlayerGridPos();
                int dx = Integer.signum(target.getGridPos().x() - pPos.x());
                int dz = Integer.signum(target.getGridPos().z() - pPos.z());
                GridPos kbPos = target.getGridPos();
                GridPos nextKb = new GridPos(kbPos.x() + dx, kbPos.z() + dz);
                if (arena.isInBounds(nextKb) && !arena.isOccupied(nextKb)) {
                    var tile = arena.getTile(nextKb);
                    if (tile != null && tile.isWalkable()) {
                        arena.moveEntity(target, nextKb);
                        if (target.getMobEntity() != null) {
                            var bp = arena.gridToBlockPos(nextKb);
                            target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                        }
                        messages.add("§6💨 Knocked back " + target.getDisplayName() + " 1 tile!");
                    }
                }
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
            double stunChance = 0.05 + (bluntPts * 0.03) + luckBonus;
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
            double stunChance = 0.05 + (bluntPts * 0.03) + luckBonus;
            if (Math.random() < stunChance) {
                target.setStunned(true);
                messages.add("§8✦ STUNNED! " + target.getDisplayName() + " can't move next turn!");
            }
        }

        // === CORAL WEAPONS ===
        if (DamageType.isCoral(weapon)) {
            // Tube Coral: Soaked (reduces speed)
            if (weapon == Items.TUBE_CORAL) {
                target.stackSoaked(1, 1);
                messages.add("\u00a73\u2716 Soaked! " + target.getDisplayName() + " is drenched and slowed!");
            }
            // Brain Coral: Confusion (chance to skip action)
            if (weapon == Items.BRAIN_CORAL) {
                if (Math.random() < 0.4 + luckBonus) {
                    target.stackConfusion(1, 1);
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
                target.stackDefensePenalty(1, 3);
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

        // === CROSSBOW: Pierce through + Bleed ===
        if (weapon == Items.CROSSBOW) {
            int piercingLevel = PlayerCombatStats.getPiercing(player);

            // Piercing: bonus damage on primary target + bleed
            if (piercingLevel > 0) {
                int pierceBonusDmg = target.takeDamage(piercingLevel);
                totalExtra += pierceBonusDmg;
                int bleedStacks = piercingLevel <= 2 ? 1 : (piercingLevel <= 3 ? 2 : 3);
                target.stackBleed(bleedStacks);
                messages.add("§b⚔ Piercing bolt! +" + pierceBonusDmg + " damage, " + bleedStacks + " bleed on " + target.getDisplayName() + ".");
            }

            int maxPierceTargets = 1 + piercingLevel; // base 1, +1 per Piercing level
            int pierceCount = 0;
            GridPos pPos = arena.getPlayerGridPos();
            int dx = Integer.signum(target.getGridPos().x() - pPos.x());
            int dz = Integer.signum(target.getGridPos().z() - pPos.z());
            // Check all tiles beyond target in line
            GridPos check = new GridPos(target.getGridPos().x() + dx, target.getGridPos().z() + dz);
            while (arena.isInBounds(check) && pierceCount < maxPierceTargets) {
                CombatEntity pierced = arena.getOccupant(check);
                if (pierced != null && pierced.isAlive()) {
                    int pierceDmg = pierced.takeDamage(baseDamage / 2);
                    extraTargets.add(pierced);
                    totalExtra += pierceDmg;
                    // Pierced targets also get bleed
                    if (piercingLevel > 0) {
                        int bleedStacks = piercingLevel <= 2 ? 1 : (piercingLevel <= 3 ? 2 : 3);
                        pierced.stackBleed(bleedStacks);
                    }
                    messages.add("§b⚔ Bolt pierces through to " + pierced.getDisplayName() + " for " + pierceDmg + "!");
                    pierceCount++;
                }
                check = new GridPos(check.x() + dx, check.z() + dz);
            }

            // === CROSSBOW: Multishot — 2 extra diagonal bolts ===
            if (PlayerCombatStats.hasMultishot(player)) {
                // Determine the two diagonal directions from the cardinal shot direction
                int[][] diagonals;
                if (dz == 0) {
                    // Shooting East/West → diagonals are (dx, -1) and (dx, 1)
                    diagonals = new int[][]{{dx, -1}, {dx, 1}};
                } else if (dx == 0) {
                    // Shooting North/South → diagonals are (-1, dz) and (1, dz)
                    diagonals = new int[][]{{-1, dz}, {1, dz}};
                } else {
                    // Already diagonal (shouldn't happen for crossbow rook pattern) — skip
                    diagonals = new int[0][];
                }
                for (int[] diag : diagonals) {
                    GridPos diagCheck = new GridPos(pPos.x() + diag[0], pPos.z() + diag[1]);
                    while (arena.isInBounds(diagCheck)) {
                        CombatEntity diagTarget = arena.getOccupant(diagCheck);
                        if (diagTarget != null && diagTarget.isAlive()) {
                            int diagDmg = diagTarget.takeDamage(baseDamage / 2);
                            extraTargets.add(diagTarget);
                            totalExtra += diagDmg;
                            messages.add("§d⚔ Multishot bolt hits " + diagTarget.getDisplayName() + " for " + diagDmg + "!");
                            break; // Each diagonal bolt hits only the first target
                        }
                        diagCheck = new GridPos(diagCheck.x() + diag[0], diagCheck.z() + diag[1]);
                    }
                }
            }
        }

        return new AttackResult(baseDamage + totalExtra, messages, extraTargets);
    }

    /** Backwards-compatible overload for callers that don't have PlayerStats. */
    public static AttackResult applyAbility(ServerPlayerEntity player, Item weapon,
                                             CombatEntity target, GridArena arena,
                                             int baseDamage) {
        return applyAbility(player, weapon, target, arena, baseDamage, null, 0);
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

    /**
     * Get the break chance for non-durable weapons (items without vanilla durability).
     * Returns 0.0 for items that don't break (durable items or non-weapons).
     * These items have a chance to be consumed on each attack.
     */
    public static double getBreakChance(Item item) {
        // Sticks — fragile
        if (item == Items.STICK) return 0.10;
        // Bamboo — slightly sturdier
        if (item == Items.BAMBOO) return 0.05;
        // Elemental rods — magical, rarely break
        if (item == Items.BLAZE_ROD) return 0.01;
        if (item == Items.BREEZE_ROD) return 0.01;
        // Live corals — organic, slightly fragile
        if (item == Items.TUBE_CORAL || item == Items.BRAIN_CORAL
            || item == Items.BUBBLE_CORAL || item == Items.FIRE_CORAL
            || item == Items.HORN_CORAL) return 0.01;
        // Dead corals — brittle
        if (item == Items.DEAD_TUBE_CORAL || item == Items.DEAD_BRAIN_CORAL
            || item == Items.DEAD_BUBBLE_CORAL || item == Items.DEAD_FIRE_CORAL
            || item == Items.DEAD_HORN_CORAL) return 0.05;
        // Live coral fans — delicate
        if (item == Items.TUBE_CORAL_FAN || item == Items.BRAIN_CORAL_FAN
            || item == Items.BUBBLE_CORAL_FAN || item == Items.FIRE_CORAL_FAN
            || item == Items.HORN_CORAL_FAN) return 0.03;
        // Dead coral fans — very brittle
        if (item == Items.DEAD_TUBE_CORAL_FAN || item == Items.DEAD_BRAIN_CORAL_FAN
            || item == Items.DEAD_BUBBLE_CORAL_FAN || item == Items.DEAD_FIRE_CORAL_FAN
            || item == Items.DEAD_HORN_CORAL_FAN) return 0.05;
        return 0.0;
    }

    /** Get a display string for the break chance (e.g., "10%" or null if no break chance). */
    public static String getBreakChanceDisplay(Item item) {
        double chance = getBreakChance(item);
        if (chance <= 0.0) return null;
        int pct = (int) (chance * 100);
        return pct + "%";
    }
}
