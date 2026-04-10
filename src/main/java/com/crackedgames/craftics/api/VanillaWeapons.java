package com.crackedgames.craftics.api;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers every vanilla weapon into the WeaponRegistry.
 * Called once during mod initialization, before VanillaContent.registerAll().
 */
public final class VanillaWeapons {
    private VanillaWeapons() {}

    /** Luck bonus per point added to all probability-based weapon effects. */
    public static final double LUCK_BONUS_PER_POINT = 0.02;

    public static void registerAll() {
        registerSwords();
        registerAxes();
        registerMace();
        registerTrident();
        registerBow();
        registerCrossbow();
        registerBluntWeapons();
        registerCorals();
        registerHoes();
        registerShovels();
    }

    // =========================================================================
    // Sword ability handler (shared by all 6 swords)
    // =========================================================================

    /**
     * Full sword ability: bleed (Sharpness), smite AoE (Smite), poison+slow (Bane),
     * directional shockwave (Knockback), whirlwind (Sweeping Edge) or base sweep,
     * Diamond crit, Netherite execute.
     */
    private static WeaponAbility.AttackResult swordAbility(ServerPlayerEntity player,
                                                            CombatEntity target,
                                                            GridArena arena,
                                                            int baseDamage,
                                                            PlayerProgression.PlayerStats playerStats,
                                                            int luckPoints) {
        double luckBonus = luckPoints * LUCK_BONUS_PER_POINT;
        List<String> messages = new ArrayList<>();
        List<CombatEntity> extraTargets = new ArrayList<>();
        int totalExtra = 0;

        // Sharpness: apply bleed stacks (each stack = +1 damage when attacked)
        int sharpness = PlayerCombatStats.getSharpness(player);
        if (sharpness > 0) {
            target.stackBleed(sharpness);
            messages.add("\u00a7c\u2726 Bleed! " + target.getDisplayName() + " has " + target.getBleedStacks() + " bleed stacks.");
        }

        // Smite: AoE radiant burst vs undead
        int smite = PlayerCombatStats.getSmite(player);
        if (smite > 0 && PlayerCombatStats.isUndead(target.getEntityTypeId())) {
            int smiteDmg = smite * 2;
            int smiteRadius = smite <= 2 ? 2 : (smite <= 4 ? 3 : 4);
            GridPos tPos = target.getGridPos();
            for (CombatEntity enemy : arena.getOccupants().values()) {
                if (enemy == target || !enemy.isAlive() || enemy.isAlly()) continue;
                if (!PlayerCombatStats.isUndead(enemy.getEntityTypeId())) continue;
                if (tPos.manhattanDistance(enemy.getGridPos()) <= smiteRadius) {
                    int burstDmg = enemy.takeDamage(smiteDmg);
                    extraTargets.add(enemy);
                    totalExtra += burstDmg;
                    messages.add("\u00a7e\u2726 Holy Radiance! " + enemy.getDisplayName() + " takes " + burstDmg + " radiant damage!");
                }
            }
        }

        // Bane of Arthropods: poison + slowness vs arthropods
        int bane = PlayerCombatStats.getBane(player);
        if (bane > 0 && PlayerCombatStats.isArthropod(target.getEntityTypeId())) {
            target.stackPoison(3, bane);
            int slowPenalty = bane <= 2 ? 1 : (bane <= 4 ? 2 : 3);
            target.stackSlowness(3, slowPenalty);
            messages.add("\u00a72\u2726 Venom! " + target.getDisplayName() + " is poisoned (" + bane + "/turn) and slowed (-" + slowPenalty + " speed).");
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
                boolean hitHazard = false;
                for (int step = 0; step < pushDist; step++) {
                    GridPos next = new GridPos(kbPos.x() + dx, kbPos.z() + dz);
                    if (!arena.isInBounds(next) || arena.isOccupied(next)) {
                        hitWall = true;
                        break;
                    }
                    var tile = arena.getTile(next);
                    if (tile == null) { hitWall = true; break; }
                    if (tile.getType() == com.crackedgames.craftics.core.TileType.OBSTACLE) {
                        hitWall = true;
                        break;
                    }
                    // Hazard tiles: land ON them, then take consequences
                    if (tile.getType() == com.crackedgames.craftics.core.TileType.VOID
                        || tile.getType() == com.crackedgames.craftics.core.TileType.DEEP_WATER
                        || tile.getType() == com.crackedgames.craftics.core.TileType.LAVA
                        || tile.getType() == com.crackedgames.craftics.core.TileType.WATER) {
                        if (pushTarget.isHazardImmune()) { hitWall = true; break; }
                        kbPos = next;
                        hitHazard = true;
                        break;
                    }
                    if (!tile.isWalkable()) { hitWall = true; break; }
                    kbPos = next;
                }
                if (!kbPos.equals(pushTarget.getGridPos())) {
                    arena.moveEntity(pushTarget, kbPos);
                    if (pushTarget.getMobEntity() != null) {
                        var bp = arena.gridToBlockPos(kbPos);
                        pushTarget.getMobEntity().requestTeleport(bp.getX() + 0.5, arena.getEntityY(kbPos), bp.getZ() + 0.5);
                    }
                    if (hitHazard) {
                        var hazardTile = arena.getTile(kbPos);
                        if (hazardTile != null) {
                            switch (hazardTile.getType()) {
                                case VOID -> {
                                    pushTarget.takeDamage(pushTarget.getCurrentHp() + 100);
                                    if (pushTarget != target) extraTargets.add(pushTarget);
                                    messages.add("\u00a74" + pushTarget.getDisplayName() + " fell into the void!");
                                }
                                case DEEP_WATER -> {
                                    pushTarget.takeDamage(pushTarget.getCurrentHp() + 100);
                                    if (pushTarget != target) extraTargets.add(pushTarget);
                                    messages.add("\u00a71" + pushTarget.getDisplayName() + " drowned in deep water!");
                                }
                                case LAVA -> {
                                    int lavaDmg = pushTarget.takeDamage(10);
                                    totalExtra += lavaDmg;
                                    if (pushTarget != target) extraTargets.add(pushTarget);
                                    messages.add("\u00a76" + pushTarget.getDisplayName() + " knocked into lava for " + lavaDmg + " damage!");
                                }
                                case WATER -> {
                                    pushTarget.stackSoaked(2, 1);
                                    messages.add("\u00a7b" + pushTarget.getDisplayName() + " splashes into water! Soaked!");
                                }
                                default -> {}
                            }
                        }
                    }
                }
                // Collision damage if hit wall/obstacle
                if (hitWall && !hitHazard) {
                    int cDmg = pushTarget.takeDamage(collisionDmg);
                    totalExtra += cDmg;
                    if (pushTarget != target) extraTargets.add(pushTarget);
                    messages.add("\u00a76\ud83d\udca8 " + pushTarget.getDisplayName() + " slammed into obstacle for " + cDmg + " collision damage!");
                } else if (!hitHazard && pushTarget != target) {
                    extraTargets.add(pushTarget);
                    messages.add("\u00a76\ud83d\udca8 " + pushTarget.getDisplayName() + " pushed back " + pushDist + " tiles!");
                }
            }
            if (!lineTargets.isEmpty()) {
                messages.add("\u00a76\ud83d\udca8 Shockwave! Knocked back " + lineTargets.size() + " enemies!");
            }
        }

        // Sweeping Edge: 360 spin hitting ALL adjacent enemies
        int sweepingEdge = PlayerCombatStats.getSweepingEdge(player);
        if (sweepingEdge > 0) {
            double sweepDmgPct = sweepingEdge == 1 ? 0.60 : (sweepingEdge == 2 ? 0.75 : 0.90);
            int sweepKb = sweepingEdge >= 3 ? 1 : 0;
            List<CombatEntity> sweepTargets = Abilities.findAdjacentEnemies(arena, target, 99); // all adjacent
            for (CombatEntity sweepTarget : sweepTargets) {
                int sweepDmg = sweepTarget.takeDamage((int)(baseDamage * sweepDmgPct));
                extraTargets.add(sweepTarget);
                totalExtra += sweepDmg;
                messages.add("\u00a7e\u2694 Whirlwind! " + sweepTarget.getDisplayName() + " takes " + sweepDmg + " damage!");
                // Lv3: knockback 1 tile
                if (sweepKb > 0) {
                    GridPos pPos2 = target.getGridPos();
                    int sdx = Integer.signum(sweepTarget.getGridPos().x() - pPos2.x());
                    int sdz = Integer.signum(sweepTarget.getGridPos().z() - pPos2.z());
                    GridPos sweepKbPos = new GridPos(sweepTarget.getGridPos().x() + sdx, sweepTarget.getGridPos().z() + sdz);
                    if (arena.isInBounds(sweepKbPos) && !arena.isOccupied(sweepKbPos)) {
                        var tile = arena.getTile(sweepKbPos);
                        if (tile != null && tile.getType() != com.crackedgames.craftics.core.TileType.OBSTACLE) {
                            boolean sweepHazard = tile.getType() == com.crackedgames.craftics.core.TileType.VOID
                                || tile.getType() == com.crackedgames.craftics.core.TileType.DEEP_WATER
                                || tile.getType() == com.crackedgames.craftics.core.TileType.LAVA
                                || tile.getType() == com.crackedgames.craftics.core.TileType.WATER;
                            if (sweepHazard && sweepTarget.isHazardImmune()) sweepHazard = false;
                            if (tile.isWalkable() || sweepHazard) {
                                arena.moveEntity(sweepTarget, sweepKbPos);
                                if (sweepTarget.getMobEntity() != null) {
                                    var bp = arena.gridToBlockPos(sweepKbPos);
                                    sweepTarget.getMobEntity().requestTeleport(bp.getX() + 0.5, arena.getEntityY(sweepKbPos), bp.getZ() + 0.5);
                                }
                                if (sweepHazard) {
                                    switch (tile.getType()) {
                                        case VOID -> {
                                            sweepTarget.takeDamage(sweepTarget.getCurrentHp() + 100);
                                            messages.add("\u00a74" + sweepTarget.getDisplayName() + " fell into the void!");
                                        }
                                        case DEEP_WATER -> {
                                            sweepTarget.takeDamage(sweepTarget.getCurrentHp() + 100);
                                            messages.add("\u00a71" + sweepTarget.getDisplayName() + " drowned in deep water!");
                                        }
                                        case LAVA -> {
                                            int lavaDmg = sweepTarget.takeDamage(10);
                                            totalExtra += lavaDmg;
                                            messages.add("\u00a76" + sweepTarget.getDisplayName() + " knocked into lava for " + lavaDmg + " damage!");
                                        }
                                        case WATER -> {
                                            sweepTarget.stackSoaked(2, 1);
                                            messages.add("\u00a7b" + sweepTarget.getDisplayName() + " splashes into water! Soaked!");
                                        }
                                        default -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Base sword sweep (affinity-scaled)
            int slashingPts = playerStats != null ? playerStats.getAffinityPoints(PlayerProgression.Affinity.SLASHING) : 0;
            double sweepChance = 0.10 + (slashingPts * 0.05) + luckBonus;
            if (Math.random() < sweepChance) {
                List<CombatEntity> sweepTargets = Abilities.findAdjacentEnemies(arena, target, 1);
                for (CombatEntity sweepTarget : sweepTargets) {
                    int sweepDmg = sweepTarget.takeDamage(baseDamage / 2);
                    extraTargets.add(sweepTarget);
                    totalExtra += sweepDmg;
                    messages.add("\u00a7e\u2694 Sweep! " + sweepTarget.getDisplayName() + " takes " + sweepDmg + " splash damage!");
                }
            }
        }

        // Diamond Sword: 30% crit (double damage)
        Item weapon = player.getMainHandStack().getItem();
        if (weapon == Items.DIAMOND_SWORD && Math.random() < 0.3) {
            messages.add("\u00a76\u2726 CRITICAL HIT! Double damage!");
            return new WeaponAbility.AttackResult(baseDamage * 2 + totalExtra, messages, extraTargets);
        }

        // Netherite Sword: execute (triple damage if target below 30% HP)
        if (weapon == Items.NETHERITE_SWORD && target.getCurrentHp() < target.getMaxHp() * 0.3) {
            messages.add("\u00a74\u2726 EXECUTE! Triple damage on wounded target!");
            return new WeaponAbility.AttackResult(baseDamage * 3 + totalExtra, messages, extraTargets);
        }

        return new WeaponAbility.AttackResult(baseDamage + totalExtra, messages, extraTargets);
    }

    // =========================================================================
    // Water knockback + soaked handler (shared by trident and all corals)
    // =========================================================================

    /**
     * Water affinity proc: chance-based knockback 1 tile + Soaked debuff.
     * Applies to Trident and all Coral weapons.
     */
    private static WeaponAbility.AttackResult waterProc(ServerPlayerEntity player,
                                                         CombatEntity target,
                                                         GridArena arena,
                                                         int baseDamage,
                                                         PlayerProgression.PlayerStats playerStats,
                                                         int luckPoints) {
        double luckBonus = luckPoints * LUCK_BONUS_PER_POINT;
        List<String> messages = new ArrayList<>();
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
            messages.add("\u00a73\u2726 SOAKED! " + target.getDisplayName() + " is drenched and slowed" + kbMsg);
        }

        return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
    }

    // =========================================================================
    // Dead coral weakness handler (shared by all dead corals and dead coral fans)
    // =========================================================================

    private static WeaponAbility.AttackResult deadCoralAbility(ServerPlayerEntity player,
                                                                CombatEntity target,
                                                                GridArena arena,
                                                                int baseDamage,
                                                                PlayerProgression.PlayerStats playerStats,
                                                                int luckPoints) {
        List<String> messages = new ArrayList<>();
        target.setAttackPenalty(Math.max(target.getAttackPenalty(), 2));
        messages.add("\u00a77\u2716 Weakened! " + target.getDisplayName() + " loses 2 ATK for 1 turn!");
        return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
    }

    // =========================================================================
    // Coral fan AoE splash handler (shared by all live coral fans)
    // Note: does NOT filter allies — matches original behavior
    // =========================================================================

    private static WeaponAbility.AttackResult coralFanSplash(ServerPlayerEntity player,
                                                              CombatEntity target,
                                                              GridArena arena,
                                                              int baseDamage,
                                                              PlayerProgression.PlayerStats playerStats,
                                                              int luckPoints) {
        List<String> messages = new ArrayList<>();
        List<CombatEntity> extraTargets = new ArrayList<>();
        int totalExtra = 0;
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
        return new WeaponAbility.AttackResult(baseDamage + totalExtra, messages, extraTargets);
    }

    // =========================================================================
    // Registration methods
    // =========================================================================

    // ===== Swords (SLASHING, range 1, apCost 1) =====

    private static void registerSwords() {
        WeaponAbilityHandler swordHandler = VanillaWeapons::swordAbility;

        WeaponRegistry.register(Items.WOODEN_SWORD, WeaponEntry.builder(Items.WOODEN_SWORD)
            .damageType(DamageType.SLASHING)
            .attackPower(CrafticsMod.CONFIG::dmgWoodenSword)
            .apCost(1).range(1).ability(swordHandler)
            .build());
        WeaponRegistry.register(Items.STONE_SWORD, WeaponEntry.builder(Items.STONE_SWORD)
            .damageType(DamageType.SLASHING)
            .attackPower(CrafticsMod.CONFIG::dmgStoneSword)
            .apCost(1).range(1).ability(swordHandler)
            .build());
        WeaponRegistry.register(Items.IRON_SWORD, WeaponEntry.builder(Items.IRON_SWORD)
            .damageType(DamageType.SLASHING)
            .attackPower(CrafticsMod.CONFIG::dmgIronSword)
            .apCost(1).range(1).ability(swordHandler)
            .build());
        WeaponRegistry.register(Items.GOLDEN_SWORD, WeaponEntry.builder(Items.GOLDEN_SWORD)
            .damageType(DamageType.SLASHING)
            .attackPower(CrafticsMod.CONFIG::dmgGoldenSword)
            .apCost(1).range(1).ability(swordHandler)
            .build());
        WeaponRegistry.register(Items.DIAMOND_SWORD, WeaponEntry.builder(Items.DIAMOND_SWORD)
            .damageType(DamageType.SLASHING)
            .attackPower(CrafticsMod.CONFIG::dmgDiamondSword)
            .apCost(1).range(1).ability(swordHandler)
            .build());
        WeaponRegistry.register(Items.NETHERITE_SWORD, WeaponEntry.builder(Items.NETHERITE_SWORD)
            .damageType(DamageType.SLASHING)
            .attackPower(CrafticsMod.CONFIG::dmgNetheriteSword)
            .apCost(1).range(1).ability(swordHandler)
            .build());
    }

    // ===== Axes (CLEAVING, range 1, apCost 2) =====

    private static void registerAxes() {
        WeaponAbilityHandler axeHandler = Abilities.armorIgnore(0.05, 0.03);

        WeaponRegistry.register(Items.WOODEN_AXE, WeaponEntry.builder(Items.WOODEN_AXE)
            .damageType(DamageType.CLEAVING)
            .attackPower(CrafticsMod.CONFIG::dmgWoodenAxe)
            .apCost(2).range(1).ability(axeHandler)
            .build());
        WeaponRegistry.register(Items.STONE_AXE, WeaponEntry.builder(Items.STONE_AXE)
            .damageType(DamageType.CLEAVING)
            .attackPower(CrafticsMod.CONFIG::dmgStoneAxe)
            .apCost(2).range(1).ability(axeHandler)
            .build());
        WeaponRegistry.register(Items.IRON_AXE, WeaponEntry.builder(Items.IRON_AXE)
            .damageType(DamageType.CLEAVING)
            .attackPower(CrafticsMod.CONFIG::dmgIronAxe)
            .apCost(2).range(1).ability(axeHandler)
            .build());
        WeaponRegistry.register(Items.GOLDEN_AXE, WeaponEntry.builder(Items.GOLDEN_AXE)
            .damageType(DamageType.CLEAVING)
            .attackPower(CrafticsMod.CONFIG::dmgGoldenAxe)
            .apCost(2).range(1).ability(axeHandler)
            .build());
        WeaponRegistry.register(Items.DIAMOND_AXE, WeaponEntry.builder(Items.DIAMOND_AXE)
            .damageType(DamageType.CLEAVING)
            .attackPower(CrafticsMod.CONFIG::dmgDiamondAxe)
            .apCost(2).range(1).ability(axeHandler)
            .build());
        WeaponRegistry.register(Items.NETHERITE_AXE, WeaponEntry.builder(Items.NETHERITE_AXE)
            .damageType(DamageType.CLEAVING)
            .attackPower(CrafticsMod.CONFIG::dmgNetheriteAxe)
            .apCost(2).range(1).ability(axeHandler)
            .build());
    }

    // ===== Mace (BLUNT, range 1, apCost 2) =====

    private static void registerMace() {
        // Mace: stun + breach + density + base AoE + wind burst / default knockback
        WeaponAbilityHandler maceHandler = (player, target, arena, baseDamage, stats, luckPoints) -> {
            double luckBonus = luckPoints * LUCK_BONUS_PER_POINT;
            List<String> messages = new ArrayList<>();
            List<CombatEntity> extraTargets = new ArrayList<>();
            int totalExtra = 0;

            // Blunt stun check (shared with stick/bamboo)
            int bluntPts = stats != null ? stats.getAffinityPoints(PlayerProgression.Affinity.BLUNT) : 0;
            double stunChance = 0.05 + (bluntPts * 0.03) + luckBonus;
            if (Math.random() < stunChance) {
                target.setStunned(true);
                messages.add("\u00a78\u2726 STUNNED! " + target.getDisplayName() + " can't move next turn!");
            }

            int densityLevel = PlayerCombatStats.getDensity(player);
            int breachLevel = PlayerCombatStats.getBreach(player);
            int windBurstLevel = PlayerCombatStats.getWindBurst(player);

            // Breach: permanently reduce target defense
            if (breachLevel > 0) {
                target.addPermanentDefReduction(breachLevel);
                int remaining = target.getEffectiveDefense();
                messages.add("\u00a74Breach! Shattered " + breachLevel + " DEF. " + target.getDisplayName() + " has " + remaining + " DEF remaining.");
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
                            messages.add("\u00a78\u2726 Gravity pulls " + pullTarget.getDisplayName() + " toward impact!");
                        }
                        // Crush bonus to enemies already adjacent to impact
                        if (impactPos.manhattanDistance(pullTarget.getGridPos()) <= 1) {
                            int crushDmg = pullTarget.takeDamage(crushBonus);
                            totalExtra += crushDmg;
                            extraTargets.add(pullTarget);
                            messages.add("\u00a76\ud83d\udca5 Crush! " + pullTarget.getDisplayName() + " takes " + crushDmg + " from compression!");
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
                        messages.add("\u00a76\ud83d\udca5 Shockwave hits " + aoeTarget.getDisplayName() + " for " + aoeDmg + "!");
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
                messages.add("\u00a7b\ud83d\udca8 Wind Burst! Shockwave knocks back enemies " + wbKb + " tiles. Next mace hit +" + wbNextBonus + " damage!");
                // Convention: CombatManager reads [WB_BONUS:N] from messages
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
                        messages.add("\u00a76\ud83d\udca8 Knocked back " + target.getDisplayName() + " 1 tile!");
                    }
                }
            }

            return new WeaponAbility.AttackResult(baseDamage + totalExtra, messages, extraTargets);
        };

        WeaponRegistry.register(Items.MACE, WeaponEntry.builder(Items.MACE)
            .damageType(DamageType.BLUNT)
            .attackPower(CrafticsMod.CONFIG::dmgMace)
            .apCost(2).range(1).ability(maceHandler)
            .build());
    }

    // ===== Trident (WATER, range 5, apCost 1, ranged) =====

    private static void registerTrident() {
        // Trident: water knockback + soaked proc only
        WeaponAbilityHandler tridentHandler = VanillaWeapons::waterProc;

        WeaponRegistry.register(Items.TRIDENT, WeaponEntry.builder(Items.TRIDENT)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgTrident)
            .apCost(1).range(PlayerCombatStats.TRIDENT_THROW_RANGE).ranged(true)
            .ability(tridentHandler)
            .build());
    }

    // ===== Bow (RANGED, range 3, apCost 1, ranged) =====

    private static void registerBow() {
        // No ability
        WeaponRegistry.register(Items.BOW, WeaponEntry.builder(Items.BOW)
            .damageType(DamageType.RANGED)
            .attackPower(CrafticsMod.CONFIG::dmgBow)
            .apCost(1).range(3).ranged(true)
            .build());
    }

    // ===== Crossbow (RANGED, range ROOK (-1), apCost 4, ranged) =====

    private static void registerCrossbow() {
        // Crossbow: pierce-through + Piercing enchant bleed + Multishot diagonal bolts
        WeaponAbilityHandler crossbowHandler = (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            List<CombatEntity> extraTargets = new ArrayList<>();
            int totalExtra = 0;

            int piercingLevel = PlayerCombatStats.getPiercing(player);

            // Piercing: bonus damage on primary target + bleed
            if (piercingLevel > 0) {
                int pierceBonusDmg = target.takeDamage(piercingLevel);
                totalExtra += pierceBonusDmg;
                int bleedStacks = piercingLevel <= 2 ? 1 : (piercingLevel <= 3 ? 2 : 3);
                target.stackBleed(bleedStacks);
                messages.add("\u00a7b\u2694 Piercing bolt! +" + pierceBonusDmg + " damage, " + bleedStacks + " bleed on " + target.getDisplayName() + ".");
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
                    messages.add("\u00a7b\u2694 Bolt pierces through to " + pierced.getDisplayName() + " for " + pierceDmg + "!");
                    pierceCount++;
                }
                check = new GridPos(check.x() + dx, check.z() + dz);
            }

            // Multishot — 2 extra diagonal bolts
            if (PlayerCombatStats.hasMultishot(player)) {
                // Determine the two diagonal directions from the cardinal shot direction
                int[][] diagonals;
                if (dz == 0) {
                    // Shooting East/West -> diagonals are (dx, -1) and (dx, 1)
                    diagonals = new int[][]{{dx, -1}, {dx, 1}};
                } else if (dx == 0) {
                    // Shooting North/South -> diagonals are (-1, dz) and (1, dz)
                    diagonals = new int[][]{{-1, dz}, {1, dz}};
                } else {
                    // Already diagonal (shouldn't happen for crossbow rook pattern) -- skip
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
                            messages.add("\u00a7d\u2694 Multishot bolt hits " + diagTarget.getDisplayName() + " for " + diagDmg + "!");
                            break; // Each diagonal bolt hits only the first target
                        }
                        diagCheck = new GridPos(diagCheck.x() + diag[0], diagCheck.z() + diag[1]);
                    }
                }
            }

            return new WeaponAbility.AttackResult(baseDamage + totalExtra, messages, extraTargets);
        };

        WeaponRegistry.register(Items.CROSSBOW, WeaponEntry.builder(Items.CROSSBOW)
            .damageType(DamageType.RANGED)
            .attackPower(CrafticsMod.CONFIG::dmgCrossbow)
            .apCost(4).range(PlayerCombatStats.RANGE_CROSSBOW_ROOK).ranged(true)
            .ability(crossbowHandler)
            .build());
    }

    // ===== Blunt weapons (BLUNT, range 1, apCost 1, with break chances) =====

    private static void registerBluntWeapons() {
        WeaponAbilityHandler stunOnly = Abilities.stun(0.05, 0.03);
        WeaponAbilityHandler blazeHandler = Abilities.fireDamage(1).and(Abilities.stun(0.05, 0.03));
        WeaponAbilityHandler breezeHandler = Abilities.knockbackDirection(1).and(Abilities.stun(0.05, 0.03));

        WeaponRegistry.register(Items.STICK, WeaponEntry.builder(Items.STICK)
            .damageType(DamageType.BLUNT)
            .attackPower(CrafticsMod.CONFIG::dmgStick)
            .apCost(1).range(1).breakChance(0.10).ability(stunOnly)
            .build());
        WeaponRegistry.register(Items.BAMBOO, WeaponEntry.builder(Items.BAMBOO)
            .damageType(DamageType.BLUNT)
            .attackPower(CrafticsMod.CONFIG::dmgBamboo)
            .apCost(1).range(1).breakChance(0.05).ability(stunOnly)
            .build());
        WeaponRegistry.register(Items.BLAZE_ROD, WeaponEntry.builder(Items.BLAZE_ROD)
            .damageType(DamageType.BLUNT)
            .attackPower(CrafticsMod.CONFIG::dmgBlazeRod)
            .apCost(1).range(1).breakChance(0.01).ability(blazeHandler)
            .build());
        WeaponRegistry.register(Items.BREEZE_ROD, WeaponEntry.builder(Items.BREEZE_ROD)
            .damageType(DamageType.BLUNT)
            .attackPower(CrafticsMod.CONFIG::dmgBreezeRod)
            .apCost(1).range(1).breakChance(0.01).ability(breezeHandler)
            .build());
    }

    // ===== Corals (WATER, range 1, apCost 1, with break chances) =====

    private static void registerCorals() {
        // The waterProc handler is composed with each coral's unique ability via .and()
        WeaponAbilityHandler waterProcHandler = VanillaWeapons::waterProc;
        WeaponAbilityHandler deadHandler = VanillaWeapons::deadCoralAbility;
        WeaponAbilityHandler fanSplash = VanillaWeapons::coralFanSplash;

        // --- Tube Coral: stackSoaked + water proc ---
        WeaponAbilityHandler tubeAbility = ((WeaponAbilityHandler) (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            target.stackSoaked(1, 1);
            messages.add("\u00a73\u2716 Soaked! " + target.getDisplayName() + " is drenched and slowed!");
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        }).and(waterProcHandler);

        WeaponRegistry.register(Items.TUBE_CORAL, WeaponEntry.builder(Items.TUBE_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralTube)
            .apCost(1).range(1).breakChance(0.01).ability(tubeAbility)
            .build());

        // --- Brain Coral: 40% confusion chance + water proc ---
        WeaponAbilityHandler brainAbility = ((WeaponAbilityHandler) (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            double luckBonus = luckPoints * LUCK_BONUS_PER_POINT;
            if (Math.random() < 0.4 + luckBonus) {
                target.stackConfusion(1, 1);
                messages.add("\u00a7d\u2716 Confused! " + target.getDisplayName() + " is disoriented!");
            }
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        }).and(waterProcHandler);

        WeaponRegistry.register(Items.BRAIN_CORAL, WeaponEntry.builder(Items.BRAIN_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralBrain)
            .apCost(1).range(1).breakChance(0.01).ability(brainAbility)
            .build());

        // --- Bubble Coral: knockback 1 tile + water proc ---
        WeaponAbilityHandler bubbleAbility = ((WeaponAbilityHandler) (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
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
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        }).and(waterProcHandler);

        WeaponRegistry.register(Items.BUBBLE_CORAL, WeaponEntry.builder(Items.BUBBLE_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralBubble)
            .apCost(1).range(1).breakChance(0.01).ability(bubbleAbility)
            .build());

        // --- Fire Coral: bonus damage to burning enemies + water proc ---
        WeaponAbilityHandler fireAbility = ((WeaponAbilityHandler) (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            int totalDamage = baseDamage;
            if (target.getBurningTurns() > 0) {
                int bonusDmg = target.takeDamage(3);
                totalDamage += bonusDmg;
                messages.add("\u00a76\u2716 Searing sting! +" + bonusDmg + " bonus damage to burning target!");
            }
            return new WeaponAbility.AttackResult(totalDamage, messages, List.of());
        }).and(waterProcHandler);

        WeaponRegistry.register(Items.FIRE_CORAL, WeaponEntry.builder(Items.FIRE_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralFire)
            .apCost(1).range(1).breakChance(0.01).ability(fireAbility)
            .build());

        // --- Horn Coral: defense pierce (ignores 3 defense) + water proc ---
        WeaponAbilityHandler hornAbility = ((WeaponAbilityHandler) (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> messages = new ArrayList<>();
            target.stackDefensePenalty(1, 3);
            messages.add("\u00a7e\u2716 Armor pierced! " + target.getDisplayName() + " loses 3 DEF for 1 turn!");
            return new WeaponAbility.AttackResult(baseDamage, messages, List.of());
        }).and(waterProcHandler);

        WeaponRegistry.register(Items.HORN_CORAL, WeaponEntry.builder(Items.HORN_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralHorn)
            .apCost(1).range(1).breakChance(0.01).ability(hornAbility)
            .build());

        // --- Dead corals: weakness (reduce attack) + water proc ---
        WeaponAbilityHandler deadWithWater = deadHandler.and(waterProcHandler);

        WeaponRegistry.register(Items.DEAD_TUBE_CORAL, WeaponEntry.builder(Items.DEAD_TUBE_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadWithWater)
            .build());
        WeaponRegistry.register(Items.DEAD_BRAIN_CORAL, WeaponEntry.builder(Items.DEAD_BRAIN_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadWithWater)
            .build());
        WeaponRegistry.register(Items.DEAD_BUBBLE_CORAL, WeaponEntry.builder(Items.DEAD_BUBBLE_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadWithWater)
            .build());
        WeaponRegistry.register(Items.DEAD_FIRE_CORAL, WeaponEntry.builder(Items.DEAD_FIRE_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadWithWater)
            .build());
        WeaponRegistry.register(Items.DEAD_HORN_CORAL, WeaponEntry.builder(Items.DEAD_HORN_CORAL)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadWithWater)
            .build());

        // --- Live coral fans: AoE splash + water proc ---
        WeaponAbilityHandler fanWithWater = fanSplash.and(waterProcHandler);

        WeaponRegistry.register(Items.TUBE_CORAL_FAN, WeaponEntry.builder(Items.TUBE_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralFan)
            .apCost(1).range(1).breakChance(0.03).ability(fanWithWater)
            .build());
        WeaponRegistry.register(Items.BRAIN_CORAL_FAN, WeaponEntry.builder(Items.BRAIN_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralFan)
            .apCost(1).range(1).breakChance(0.03).ability(fanWithWater)
            .build());
        WeaponRegistry.register(Items.BUBBLE_CORAL_FAN, WeaponEntry.builder(Items.BUBBLE_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralFan)
            .apCost(1).range(1).breakChance(0.03).ability(fanWithWater)
            .build());
        WeaponRegistry.register(Items.FIRE_CORAL_FAN, WeaponEntry.builder(Items.FIRE_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralFan)
            .apCost(1).range(1).breakChance(0.03).ability(fanWithWater)
            .build());
        WeaponRegistry.register(Items.HORN_CORAL_FAN, WeaponEntry.builder(Items.HORN_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralFan)
            .apCost(1).range(1).breakChance(0.03).ability(fanWithWater)
            .build());

        // --- Dead coral fans: weakness + water proc ---
        WeaponAbilityHandler deadFanWithWater = deadHandler.and(waterProcHandler);

        WeaponRegistry.register(Items.DEAD_TUBE_CORAL_FAN, WeaponEntry.builder(Items.DEAD_TUBE_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadFanWithWater)
            .build());
        WeaponRegistry.register(Items.DEAD_BRAIN_CORAL_FAN, WeaponEntry.builder(Items.DEAD_BRAIN_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadFanWithWater)
            .build());
        WeaponRegistry.register(Items.DEAD_BUBBLE_CORAL_FAN, WeaponEntry.builder(Items.DEAD_BUBBLE_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadFanWithWater)
            .build());
        WeaponRegistry.register(Items.DEAD_FIRE_CORAL_FAN, WeaponEntry.builder(Items.DEAD_FIRE_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadFanWithWater)
            .build());
        WeaponRegistry.register(Items.DEAD_HORN_CORAL_FAN, WeaponEntry.builder(Items.DEAD_HORN_CORAL_FAN)
            .damageType(DamageType.WATER)
            .attackPower(CrafticsMod.CONFIG::dmgCoralDead)
            .apCost(1).range(1).breakChance(0.05).ability(deadFanWithWater)
            .build());
    }

    // ===== Hoes (SPECIAL, range 1, apCost 1, fixed damage, no config) =====

    private static void registerHoes() {
        // No abilities
        WeaponRegistry.register(Items.WOODEN_HOE, WeaponEntry.builder(Items.WOODEN_HOE)
            .damageType(DamageType.SPECIAL).attackPower(1).apCost(1).range(1).build());
        WeaponRegistry.register(Items.STONE_HOE, WeaponEntry.builder(Items.STONE_HOE)
            .damageType(DamageType.SPECIAL).attackPower(1).apCost(1).range(1).build());
        WeaponRegistry.register(Items.IRON_HOE, WeaponEntry.builder(Items.IRON_HOE)
            .damageType(DamageType.SPECIAL).attackPower(2).apCost(1).range(1).build());
        WeaponRegistry.register(Items.GOLDEN_HOE, WeaponEntry.builder(Items.GOLDEN_HOE)
            .damageType(DamageType.SPECIAL).attackPower(2).apCost(1).range(1).build());
        WeaponRegistry.register(Items.DIAMOND_HOE, WeaponEntry.builder(Items.DIAMOND_HOE)
            .damageType(DamageType.SPECIAL).attackPower(3).apCost(1).range(1).build());
        WeaponRegistry.register(Items.NETHERITE_HOE, WeaponEntry.builder(Items.NETHERITE_HOE)
            .damageType(DamageType.SPECIAL).attackPower(3).apCost(1).range(1).build());
    }

    // ===== Shovels (PET, range 1, apCost 1, fixed damage, no config) =====

    private static void registerShovels() {
        // No abilities
        WeaponRegistry.register(Items.WOODEN_SHOVEL, WeaponEntry.builder(Items.WOODEN_SHOVEL)
            .damageType(DamageType.PET).attackPower(2).apCost(1).range(1).build());
        WeaponRegistry.register(Items.STONE_SHOVEL, WeaponEntry.builder(Items.STONE_SHOVEL)
            .damageType(DamageType.PET).attackPower(3).apCost(1).range(1).build());
        WeaponRegistry.register(Items.IRON_SHOVEL, WeaponEntry.builder(Items.IRON_SHOVEL)
            .damageType(DamageType.PET).attackPower(4).apCost(1).range(1).build());
        WeaponRegistry.register(Items.GOLDEN_SHOVEL, WeaponEntry.builder(Items.GOLDEN_SHOVEL)
            .damageType(DamageType.PET).attackPower(3).apCost(1).range(1).build());
        WeaponRegistry.register(Items.DIAMOND_SHOVEL, WeaponEntry.builder(Items.DIAMOND_SHOVEL)
            .damageType(DamageType.PET).attackPower(5).apCost(1).range(1).build());
        WeaponRegistry.register(Items.NETHERITE_SHOVEL, WeaponEntry.builder(Items.NETHERITE_SHOVEL)
            .damageType(DamageType.PET).attackPower(6).apCost(1).range(1).build());
    }
}
