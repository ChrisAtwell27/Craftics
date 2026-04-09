package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Snowy Tundra Boss — "The Frostbound Huntsman"
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
 * Phase 2 — "Permafrost": Speed 3, auto-freeze 2 tiles every 2 turns,
 *           reduced cooldowns, Blizzard center stuns.
 */
public class FrostboundAI extends BossAI {
    private static final String CD_BLIZZARD = "blizzard";
    private static final String CD_ICE_WALL = "ice_wall";
    private static final String CD_TRAP = "glacial_trap";
    private static final String CD_HARPOON = "harpoon_pull";
    private static final String CD_WHITEOUT = "whiteout_ring";

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

        // Phase 2 reduces all cooldowns by 1
        int cdBlizzard = p2 ? 2 : 3;
        int cdIceWall = p2 ? 3 : 4;
        int cdTrap = p2 ? 2 : 3;
        int cdHarpoon = p2 ? 2 : 3;
        int cdWhiteout = p2 ? 3 : 4;

        EnemyAction ability = chooseOffensiveAbility(self, arena, myPos, playerPos, dist, p2,
            cdBlizzard, cdIceWall, cdTrap, cdHarpoon, cdWhiteout);

        // Layer passive freeze on top of chosen ability
        if (passiveFreeze != null && ability != null) {
            return new EnemyAction.CompositeAction(List.of(passiveFreeze, ability));
        }
        if (passiveFreeze != null) {
            return passiveFreeze;
        }
        return ability != null ? ability : new EnemyAction.Idle();
    }

    private EnemyAction chooseOffensiveAbility(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, int dist, boolean p2,
            int cdBlizzard, int cdIceWall, int cdTrap, int cdHarpoon, int cdWhiteout) {

        // Harpoon pull: drag player into follow-up threat zones
        if (!isOnCooldown(CD_HARPOON) && dist >= 2 && dist <= 5) {
            EnemyAction harpoon = castHarpoonPull(self, arena, myPos, playerPos, cdHarpoon);
            if (harpoon != null) return harpoon;
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

        // Ice Wall: cut off escape routes
        if (!isOnCooldown(CD_ICE_WALL) && dist >= 2) {
            EnemyAction wall = castIceWall(self, arena, myPos, playerPos, cdIceWall);
            if (wall != null) return wall;
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

    private EnemyAction castHarpoonPull(CombatEntity self, GridArena arena,
            GridPos myPos, GridPos playerPos, int cooldown) {
        int[] pullDir = getDirectionToward(playerPos, myPos);
        if (pullDir[0] == 0 && pullDir[1] == 0) return null;

        List<GridPos> warningTiles = new ArrayList<>();
        warningTiles.add(playerPos);
        warningTiles.addAll(getLineTiles(arena, playerPos, pullDir[0], pullDir[1], 2));

        EnemyAction resolve = new EnemyAction.CompositeAction(List.of(
            new EnemyAction.AreaAttack(playerPos, 0, 4, "frost_harpoon"),
            new EnemyAction.ForcedMovement(-1, pullDir[0], pullDir[1], 2)
        ));

        setCooldown(CD_HARPOON, cooldown);
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.DIRECTIONAL,
            warningTiles, 1, resolve, 0xFF66DDFF);
        return new EnemyAction.Idle();
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
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
            dangerTiles, 1, new EnemyAction.CompositeAction(blasts), 0xFFBDEFFF);
        return new EnemyAction.Idle();
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

        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
            blizzardTiles, 1, blizzardAction, 0xFF88CCFF);
        return new EnemyAction.Idle();
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
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
            wallTiles, 1, wallAction, 0xFF44AAFF);
        return new EnemyAction.Idle();
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
        // Radius 0 for each tile — matches the 2×2 warning area exactly
        List<EnemyAction> traps = new ArrayList<>();
        for (GridPos tile : trapTiles) {
            traps.add(new EnemyAction.AreaAttack(tile, 0, 2, "glacial_trap"));
        }
        EnemyAction trapAction = new EnemyAction.CompositeAction(traps);

        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
            trapTiles, 1, trapAction, 0xFF66DDFF);
        return new EnemyAction.Idle();
    }
}
