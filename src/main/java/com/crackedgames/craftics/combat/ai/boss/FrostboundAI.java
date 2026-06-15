package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Snowy Tundra Boss - "The Frostbound Huntsman"
 * Entity: Stray | 25HP / 5ATK / 2DEF / Range 4 / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Harpoon Pull: telegraphed pull that drags player 2 tiles toward the huntsman. 4 dmg.
 * - Whiteout Ring: telegraphed ring blast with one safe gap tile. 4 dmg per tile.
 * - Blizzard: 3×3 AoE, 5 dmg + Slowness 2 turns. P2: center tile stuns.
 * - Ice Wall: Line of 3 obstacle tiles, 3 turns. Blocks movement + LOS.
 * - Frost Arrow: Range 4, ATK dmg + 1-turn Slowness.
 * - Glacial Trap: 2×2 freeze zone, 2 dmg + start-of-turn stun. Lasts 2 turns.
 *
 * Phase 2 - "Permafrost": Speed 3, auto-freeze 2 tiles every 2 turns,
 *           reduced cooldowns, Blizzard center stuns.
 */
public class FrostboundAI extends BossAI {
    private static final String CD_BLIZZARD = "blizzard";
    private static final String CD_ICE_WALL = "ice_wall";
    private static final String CD_TRAP = "glacial_trap";
    private static final String CD_HARPOON = "harpoon_pull";
    private static final String CD_WHITEOUT = "whiteout_ring";
    private static final String CD_WAVE = "frost_wave";
    private static final String CD_SUMMON = "frost_summon";

    // Persistent expanding wave pressure: once started, it pulses every turn
    // while the boss continues using its normal attack pattern.
    private GridPos activeWaveCenter = null;
    private int activeWaveRadius = 0;
    private int activeWaveMaxRadius = 0;
    private int activeWaveDamage = 0;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(1); // Speed 2 → 3
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        boolean p2 = isPhaseTwo();

        // Phase 2: Auto-freeze 2 random tiles every 2 turns as a passive hazard
        EnemyAction passiveFreeze = null;
        if (p2 && getTurnCounter() % 2 == 0) {
            List<GridPos> freezeTiles = findSummonPositions(arena, 2);
            if (!freezeTiles.isEmpty()) {
                passiveFreeze = new EnemyAction.CreateTerrain(freezeTiles, TileType.POWDER_SNOW, 3);
            }
        }

        // Phase 2: reinforce lane control by creating a fresh ice wall every turn.
        EnemyAction passiveWall = null;
        if (p2) {
            passiveWall = castIceWall(self, arena, myPos, playerPos, 0);
        }

        // If a frost wave is active, it expands this turn regardless of what
        // primary ability the boss picks.
        EnemyAction wavePulse = tickExpandingWave(arena);

        // Phase 2 reduces all cooldowns by 1
        int cdBlizzard = p2 ? 2 : 3;
        int cdIceWall = p2 ? 1 : 2;
        int cdTrap = p2 ? 2 : 3;
        int cdHarpoon = p2 ? 2 : 3;
        int cdWhiteout = p2 ? 3 : 4;
        int cdWave = p2 ? 3 : 4;
        int cdSummon = p2 ? 2 : 3;

        EnemyAction ability = chooseOffensiveAbility(self, arena, myPos, playerPos, dist, p2,
            cdBlizzard, cdIceWall, cdTrap, cdHarpoon, cdWhiteout, cdWave, cdSummon);

        // If the offensive branch already created a telegraphed boss ability,
        // fold passive hazards into the same warning/resolve package.
        if (ability instanceof EnemyAction.BossAbility warned) {
            List<EnemyAction> resolvedParts = new ArrayList<>();
            if (wavePulse != null && !(wavePulse instanceof EnemyAction.Idle)) {
                resolvedParts.add(wavePulse);
            }
            if (passiveFreeze != null && !(passiveFreeze instanceof EnemyAction.Idle)) {
                resolvedParts.add(passiveFreeze);
            }
            if (passiveWall != null && !(passiveWall instanceof EnemyAction.Idle)) {
                resolvedParts.add(passiveWall);
            }
            resolvedParts.add(warned.resolvedAction());

            List<GridPos> warningTiles = new ArrayList<>();
            warningTiles.addAll(warned.warningTiles());
            warningTiles.addAll(inferWarningTiles(wavePulse, arena, playerPos));
            warningTiles.addAll(inferWarningTiles(passiveFreeze, arena, playerPos));
            warningTiles.addAll(inferWarningTiles(passiveWall, arena, playerPos));

            EnemyAction resolved = composeActions(resolvedParts);
            return new EnemyAction.BossAbility(
                warned.abilityName(),
                resolved,
                dedupeTiles(warningTiles)
            );
        }

        // Layer passive hazards + independent wave pressure on top of the chosen
        // attack so the boss can keep fighting while zone pressure escalates.
        List<EnemyAction> layered = new ArrayList<>();
        if (wavePulse != null && !(wavePulse instanceof EnemyAction.Idle)) {
            layered.add(wavePulse);
        }
        if (passiveFreeze != null && !(passiveFreeze instanceof EnemyAction.Idle)) {
            layered.add(passiveFreeze);
        }
        if (passiveWall != null && !(passiveWall instanceof EnemyAction.Idle)) {
            layered.add(passiveWall);
        }
        if (ability != null && !(ability instanceof EnemyAction.Idle)) {
            layered.add(ability);
        }
        if (layered.isEmpty()) return new EnemyAction.Idle();

        EnemyAction finalAction = composeActions(layered);
        if (!isThreateningAction(finalAction)) {
            return finalAction;
        }

        List<GridPos> warningTiles = inferWarningTiles(finalAction, arena, playerPos);
        if (warningTiles.isEmpty()) {
            warningTiles = List.of(playerPos);
        }
        return new EnemyAction.BossAbility("frostbound_attack", finalAction, warningTiles);
    }

    private EnemyAction chooseOffensiveAbility(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, int dist, boolean p2,
            int cdBlizzard, int cdIceWall, int cdTrap, int cdHarpoon, int cdWhiteout,
            int cdWave, int cdSummon) {

        // Start a persistent expanding frost wave centered on the boss.
        if (activeWaveCenter == null && !isOnCooldown(CD_WAVE) && dist <= 5) {
            return startExpandingWave(self, arena, cdWave, p2);
        }

        // Summon one reinforcement to keep pressure up.
        if (!isOnCooldown(CD_SUMMON) && getAliveMinionCount() < (p2 ? 3 : 2)) {
            EnemyAction summon = castFrozenReinforcement(arena, myPos, cdSummon, p2);
            if (summon != null) return summon;
        }

        // Harpoon pull: drag player into follow-up threat zones
        if (!isOnCooldown(CD_HARPOON) && dist >= 2 && dist <= 5) {
            EnemyAction harpoon = castHarpoonPull(self, arena, myPos, playerPos, cdHarpoon);
            if (harpoon != null) return harpoon;
        }

        // Keep pressure on kiting players with regular bow shots at long range.
        if (dist >= 4) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "frost_arrow");
        }

        // Ice Wall: common lane-control tool in phase 1.
        // Phase 2 already places walls passively every turn.
        if (!p2 && !isOnCooldown(CD_ICE_WALL) && dist >= 2) {
            EnemyAction wall = castIceWall(self, arena, myPos, playerPos, cdIceWall);
            if (wall != null) return wall;
        }

        // Whiteout ring: telegraphed ring burst with one safe tile
        if (!isOnCooldown(CD_WHITEOUT) && dist <= 5) {
            EnemyAction whiteout = castWhiteoutRing(self, arena, myPos, playerPos, cdWhiteout, p2);
            if (whiteout != null) return whiteout;
        }

        // Blizzard on player cluster
        if (!isOnCooldown(CD_BLIZZARD) && dist <= 5) {
            return castBlizzard(self, arena, playerPos, cdBlizzard, p2);
        }

        // Glacial Trap on tiles near player
        if (!isOnCooldown(CD_TRAP) && dist <= 4) {
            return castGlacialTrap(self, arena, playerPos, cdTrap);
        }

        // Frost Arrow: standard ranged attack
        if (dist >= 2 && dist <= 4) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "frost_arrow");
        }

        // Kite away if too close
        if (dist <= 1) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Flee(path);
                }
            }
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    private EnemyAction startExpandingWave(CombatEntity self, GridArena arena, int cooldown, boolean p2) {
        activeWaveCenter = self.getGridPos();
        activeWaveRadius = 1;
        activeWaveMaxRadius = p2 ? 5 : 4;
        activeWaveDamage = p2 ? 5 : 4;
        setCooldown(CD_WAVE, cooldown);
        return tickExpandingWave(arena);
    }

    private EnemyAction tickExpandingWave(GridArena arena) {
        if (activeWaveCenter == null || activeWaveRadius <= 0 || activeWaveRadius > activeWaveMaxRadius) {
            activeWaveCenter = null;
            activeWaveRadius = 0;
            activeWaveMaxRadius = 0;
            activeWaveDamage = 0;
            return null;
        }

        List<GridPos> ring = new ArrayList<>();
        int r = activeWaveRadius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                GridPos p = new GridPos(activeWaveCenter.x() + dx, activeWaveCenter.z() + dz);
                if (arena == null || arena.isInBounds(p)) {
                    ring.add(p);
                }
            }
        }

        activeWaveRadius++;
        if (activeWaveRadius > activeWaveMaxRadius) {
            activeWaveCenter = null;
        }

        if (ring.isEmpty()) return null;

        List<EnemyAction> pulses = new ArrayList<>(ring.size());
        for (GridPos tile : ring) {
            pulses.add(new EnemyAction.AreaAttack(tile, 0, activeWaveDamage, "frost_wave"));
        }
        return pulses.size() == 1 ? pulses.get(0) : new EnemyAction.CompositeAction(pulses);
    }

    private EnemyAction castFrozenReinforcement(GridArena arena, GridPos myPos, int cooldown, boolean p2) {
        String entityTypeId = pickSummonEntity();
        List<GridPos> positions = findSummonPositionsNear(arena, myPos, 3, 1);
        if (positions.isEmpty()) {
            positions = findSummonPositions(arena, 1);
        }
        if (positions.isEmpty()) return null;

        setCooldown(CD_SUMMON, cooldown);
        int hp = p2 ? 10 : 8;
        int atk = p2 ? 4 : 3;
        return new EnemyAction.SummonMinions(entityTypeId, 1, positions, hp, atk, 0);
    }

    private String pickSummonEntity() {
        List<String> pool = new ArrayList<>();
        pool.add("minecraft:stray");
        if (FabricLoader.getInstance().isModLoaded("variantsandventures")) {
            pool.add("variantsandventures:gelid");
        }
        if (FabricLoader.getInstance().isModLoaded("creeperoverhaul")) {
            pool.add("creeperoverhaul:snowy_creeper");
        }
        return pool.get(getTurnCounter() % pool.size());
    }

    private EnemyAction castHarpoonPull(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, int cooldown) {
        int[] pullDir = getDirectionToward(playerPos, myPos);
        if (pullDir[0] == 0 && pullDir[1] == 0) return null;

        // Big telegraph: paint the full lane the player will be dragged along,
        // not just the next 2 tiles, so the push direction is obvious.
        List<GridPos> warningTiles = pullDir[0] != 0
            ? getRowTiles(arena, playerPos.z())
            : getColumnTiles(arena, playerPos.x());

        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.AreaAttack(playerPos, 0, 4, "frost_harpoon"),
            new EnemyAction.ForcedMovement(-1, pullDir[0], pullDir[1], 2)
        ));

        setCooldown(CD_HARPOON, cooldown);
        return new EnemyAction.BossAbility("frost_harpoon", resolve, warningTiles);
    }

    private EnemyAction castWhiteoutRing(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, int cooldown, boolean p2) {
        List<GridPos> ring = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                GridPos p = new GridPos(playerPos.x() + dx, playerPos.z() + dz);
                if (arena.isInBounds(p)) {
                    ring.add(p);
                }
            }
        }
        if (ring.size() < 3) return null;

        GridPos safeTile = ring.get(0);
        int best = Integer.MIN_VALUE;
        for (GridPos p : ring) {
            int score = p.manhattanDistance(myPos);
            if (score > best) {
                best = score;
                safeTile = p;
            }
        }

        List<GridPos> dangerTiles = new ArrayList<>(ring);
        dangerTiles.remove(safeTile);
        if (dangerTiles.isEmpty()) return null;

        int ringDamage = p2 ? 5 : 4;
        List<EnemyAction> blasts = new ArrayList<>();
        for (GridPos pos : dangerTiles) {
            blasts.add(new EnemyAction.AreaAttack(pos, 0, ringDamage, "whiteout_ring"));
        }

        setCooldown(CD_WHITEOUT, cooldown);
        return new EnemyAction.BossAbility(
            "whiteout_ring",
            new EnemyAction.CompositeAction(blasts),
            dangerTiles
        );
    }

    private EnemyAction castBlizzard(CombatEntity self, GridArena arena,
            GridPos playerPos, int cooldown, boolean p2) {
        List<GridPos> blizzardTiles = getAreaTiles(arena, playerPos, 1); // 3x3
        setCooldown(CD_BLIZZARD, cooldown);

        int blizzardDmg = p2 ? 6 : 5;
        EnemyAction blizzardAction;
        if (p2) {
            // Phase 2: center tile stuns via separate higher-damage hit
            blizzardAction = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(playerPos, 1, blizzardDmg, "blizzard"),
                new EnemyAction.AreaAttack(playerPos, 0, 2, "blizzard_stun")
            ));
        } else {
            blizzardAction = new EnemyAction.AreaAttack(playerPos, 1, blizzardDmg, "blizzard");
        }

        return new EnemyAction.BossAbility("blizzard", blizzardAction, blizzardTiles);
    }

    private EnemyAction castIceWall(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, int cooldown) {
        int[] dir = getDirectionToward(myPos, playerPos);
        int wallDx = dir[1] != 0 ? 1 : 0; // perpendicular
        int wallDz = dir[0] != 0 ? 1 : 0;
        GridPos midpoint = new GridPos(
            (myPos.x() + playerPos.x()) / 2,
            (myPos.z() + playerPos.z()) / 2);
        List<GridPos> wallTiles = new ArrayList<>();
        wallTiles.add(midpoint);
        wallTiles.add(new GridPos(midpoint.x() + wallDx, midpoint.z() + wallDz));
        wallTiles.add(new GridPos(midpoint.x() - wallDx, midpoint.z() - wallDz));
        // Filter to in-bounds and unoccupied
        wallTiles.removeIf(pos -> !arena.isInBounds(pos) || arena.isOccupied(pos));
        if (wallTiles.isEmpty()) return null;

        setCooldown(CD_ICE_WALL, cooldown);
        EnemyAction wallAction = new EnemyAction.CreateTerrain(wallTiles, TileType.OBSTACLE, 3);
        return new EnemyAction.BossAbility("ice_wall", wallAction, wallTiles);
    }

    private EnemyAction castGlacialTrap(CombatEntity self, GridArena arena,
            GridPos playerPos, int cooldown) {
        List<GridPos> trapTiles = new ArrayList<>();
        trapTiles.add(playerPos);
        trapTiles.add(new GridPos(playerPos.x() + 1, playerPos.z()));
        trapTiles.add(new GridPos(playerPos.x(), playerPos.z() + 1));
        trapTiles.add(new GridPos(playerPos.x() + 1, playerPos.z() + 1));
        trapTiles.removeIf(pos -> !arena.isInBounds(pos));

        setCooldown(CD_TRAP, cooldown);
        // Radius 0 for each tile - matches the 2×2 warning area exactly
        List<EnemyAction> traps = new ArrayList<>();
        for (GridPos tile : trapTiles) {
            traps.add(new EnemyAction.AreaAttack(tile, 0, 2, "glacial_trap"));
        }
        EnemyAction trapAction = new EnemyAction.CompositeAction(traps);

        return new EnemyAction.BossAbility("glacial_trap", trapAction, trapTiles);
    }

    private EnemyAction composeActions(List<EnemyAction> actions) {
        if (actions.isEmpty()) return new EnemyAction.Idle();
        if (actions.size() == 1) return actions.get(0);
        return new EnemyAction.CompositeAction(actions);
    }

    private boolean isThreateningAction(EnemyAction action) {
        return switch (action) {
            case EnemyAction.Attack ignored -> true;
            case EnemyAction.MoveAndAttack ignored -> true;
            case EnemyAction.AttackWithKnockback ignored -> true;
            case EnemyAction.MoveAndAttackWithKnockback ignored -> true;
            case EnemyAction.RangedAttack ignored -> true;
            case EnemyAction.AreaAttack ignored -> true;
            case EnemyAction.LineAttack ignored -> true;
            case EnemyAction.SummonMinions ignored -> true;
            case EnemyAction.CreateTerrain ignored -> true;
            case EnemyAction.ForcedMovement ignored -> true;
            case EnemyAction.TeleportAndAttack ignored -> true;
            case EnemyAction.CompositeAction ca -> {
                boolean anyThreat = false;
                for (EnemyAction sub : ca.actions()) {
                    if (isThreateningAction(sub)) {
                        anyThreat = true;
                        break;
                    }
                }
                yield anyThreat;
            }
            default -> false;
        };
    }

    private List<GridPos> inferWarningTiles(EnemyAction action, GridArena arena, GridPos playerPos) {
        List<GridPos> tiles = new ArrayList<>();
        if (action == null) return tiles;
        switch (action) {
            case EnemyAction.Attack ignored -> tiles.add(playerPos);
            case EnemyAction.MoveAndAttack ignored -> tiles.add(playerPos);
            case EnemyAction.AttackWithKnockback ignored -> tiles.add(playerPos);
            case EnemyAction.MoveAndAttackWithKnockback ignored -> tiles.add(playerPos);
            case EnemyAction.RangedAttack ignored -> tiles.add(playerPos);
            case EnemyAction.ForcedMovement ignored -> tiles.add(playerPos);
            case EnemyAction.TeleportAndAttack ignored -> tiles.add(playerPos);
            case EnemyAction.AreaAttack aa -> tiles.addAll(getAreaTiles(arena, aa.center(), aa.radius()));
            case EnemyAction.LineAttack la -> {
                GridPos current = la.start();
                for (int i = 0; i < la.length(); i++) {
                    current = new GridPos(current.x() + la.dx(), current.z() + la.dz());
                    if (arena.isInBounds(current)) {
                        tiles.add(current);
                    }
                }
            }
            case EnemyAction.SummonMinions sm -> tiles.addAll(sm.positions());
            case EnemyAction.CreateTerrain ct -> tiles.addAll(ct.tiles());
            case EnemyAction.BossAbility ba -> tiles.addAll(ba.warningTiles());
            case EnemyAction.CompositeAction ca -> {
                for (EnemyAction sub : ca.actions()) {
                    tiles.addAll(inferWarningTiles(sub, arena, playerPos));
                }
            }
            default -> {
            }
        }
        return dedupeTiles(tiles);
    }

    private List<GridPos> dedupeTiles(List<GridPos> tiles) {
        List<GridPos> out = new ArrayList<>();
        for (GridPos tile : tiles) {
            if (tile != null && !out.contains(tile)) {
                out.add(tile);
            }
        }
        return out;
    }
}
