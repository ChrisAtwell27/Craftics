package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * River Delta Boss - "The Tidecaller"
 * Entity: Drowned | 30HP / 5ATK / 2DEF / Speed 2 (3 on water) / Range 3 | Size 2×2
 *
 * Abilities:
 * - Tidal Wave: 2-tile-wide column floods with water, 3 turns. P2: 3-wide + permanent.
 * - Conduction: mark one random combatant; a turn later lightning strikes the mark's
 *   LIVE position and chains between every combatant within 2 tiles of the last struck.
 *   4 dmg (P2: 6), -1 per jump; Soaked links take 2x. The boss never conducts.
 * - Trident Storm: three 3×3 splash zones (player + advance + flank), 4 dmg
 *   (P2: 5) + brief Slowness. Every impacted tile shimmers blue.
 * - Riptide Charge: On water, charge 4 tiles, ATK+3, knockback 2.
 * - Call of the Deep: Summon 1-2 Drowned (9HP/3ATK) on water tiles. P2: 2-3 every 2 turns.
 *
 * Phase 2 - "Deluge": Half arena floods permanently, +2 ATK on water.
 */
public class TidecallerAI extends BossAI {
    private static final String CD_WAVE = "tidal_wave";
    private static final String CD_TRIDENT = "trident_storm";
    private static final String CD_RIPTIDE = "riptide_charge";
    private static final String CD_SUMMON = "call_deep";
    private static final String CD_CONDUCTION = "conduction";
    private boolean delugeCast = false;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // The half-arena deluge is emitted exactly once from chooseAbility (an AI can
        // only return terrain actions from there), guarded by the delugeCast one-shot.
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        boolean onWater = arena.getTile(myPos) != null
            && arena.getTile(myPos).getType() == TileType.WATER;

        // Phase 2: flood half the arena exactly once (one-shot via delugeCast,
        // replacing the old tautological turn-equality gate).
        if (isPhaseTwo() && !delugeCast) {
            int floodRows = arena.getHeight() / 2;
            List<GridPos> floodTiles = new ArrayList<>();
            for (int z = 0; z < floodRows; z++) {
                for (int x = 0; x < arena.getWidth(); x++) {
                    GridPos pos = new GridPos(x, z);
                    if (arena.isInBounds(pos) && arena.getTile(pos) != null
                            && arena.getTile(pos).getType() == TileType.NORMAL
                            && pos.manhattanDistance(playerPos) > 1) { // spare player's tile + adjacent
                        floodTiles.add(pos);
                    }
                }
            }
            if (!floodTiles.isEmpty()) {
                delugeCast = true;
                return new EnemyAction.CreateTerrain(floodTiles, TileType.WATER, 0);
            }
        }

        // Call of the Deep
        int summonInterval = isPhaseTwo() ? 2 : 3;
        int summonCount = isPhaseTwo() ? 3 : 2;
        if (!isOnCooldown(CD_SUMMON) && getAliveMinionCount() < 4) {
            List<GridPos> waterTiles = getWaterTiles(arena);
            List<GridPos> emptyWater = new ArrayList<>();
            for (GridPos p : waterTiles) {
                if (!arena.isOccupied(p)) emptyWater.add(p);
            }
            java.util.Collections.shuffle(emptyWater);
            List<GridPos> positions = emptyWater.subList(0, Math.min(summonCount, emptyWater.size()));
            if (!positions.isEmpty()) {
                setCooldown(CD_SUMMON, summonInterval);
                EnemyAction summon = new EnemyAction.SummonMinions(
                    "minecraft:drowned", positions.size(), positions, 9, 3, 0);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    positions, 1, summon, 0xFF4488CC);
                return new EnemyAction.Idle();
            }
        }

        // Riptide Charge if on water and at range
        if (!isOnCooldown(CD_RIPTIDE) && onWater && dist >= 2 && dist <= 5) {
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> chargePath = getChargePath(arena, myPos, dir[0], dir[1], 4);
            if (!chargePath.isEmpty()) {
                setCooldown(CD_RIPTIDE, 3);
                int chargeDmg = self.getAttackPower() + 3;
                EnemyAction riptide = new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.LineAttack(myPos, dir[0], dir[1], chargePath.size(), chargeDmg),
                    new EnemyAction.ForcedMovement(-1, dir[0], dir[1], 2)
                ));
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                    chargePath, 1, riptide, 0xFF2266AA, dir[0], dir[1]);
                return new EnemyAction.Idle();
            }
        }

        // Tidal Wave
        if (!isOnCooldown(CD_WAVE)) {
            int waveWidth = isPhaseTwo() ? 3 : 2;
            int waveDuration = isPhaseTwo() ? 0 : 3;
            // Pick the column the player is on
            int targetCol = playerPos.x();
            List<GridPos> waveTiles = new ArrayList<>();
            for (int dx = 0; dx < waveWidth; dx++) {
                int col = targetCol + dx - waveWidth / 2;
                if (col >= 0 && col < arena.getWidth()) {
                    waveTiles.addAll(getColumnTiles(arena, col));
                }
            }
            setCooldown(CD_WAVE, 3);
            EnemyAction waveAction = new EnemyAction.CreateTerrain(waveTiles, TileType.WATER, waveDuration);
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                waveTiles, 1, waveAction, 0xFF3388CC);
            return new EnemyAction.Idle();
        }

        // Conduction: mark one combatant, then a turn later lightning strikes it and
        // arcs between everything within 2 tiles of the last link - the boss's own
        // drowned included. The flood already Soaks whoever stands in it, and Soaked
        // doubles lightning, so the water is the setup and this is the payoff. The
        // mark FOLLOWS its wearer (see the tracker), so counterplay is spacing, not
        // dodging: you cannot escape the bolt, only decide who stands near you when
        // it lands.
        if (!isOnCooldown(CD_CONDUCTION)) {
            setCooldown(CD_CONDUCTION, isPhaseTwo() ? 3 : 4);
            int markId = pickConductionMarkId(self, arena);
            EnemyAction strike = new EnemyAction.AreaAttack(
                null, 0, isPhaseTwo() ? 6 : 4, "conduction:" + markId);
            pendingWarning = BossWarning.tracking(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                markId, liveTracker(arena), 1, strike, 0xFFFFDD33);
            return new EnemyAction.Idle();
        }

        // Trident Storm at range - a true volley: three 3×3 impact zones (one on
        // the player, one ahead toward the boss, one on the flank), so a lazy
        // one-tile sidestep can walk straight into the next splash. The warning
        // paints every tile that will actually be hit.
        if (!isOnCooldown(CD_TRIDENT) && dist >= 2 && dist <= 4) {
            setCooldown(CD_TRIDENT, 2);
            int[] toward = getDirectionToward(playerPos, myPos);
            GridPos second = new GridPos(playerPos.x() + toward[0] * 2, playerPos.z() + toward[1] * 2);
            GridPos third = new GridPos(playerPos.x() + toward[1] * 2, playerPos.z() + toward[0] * 2);
            List<GridPos> centers = new ArrayList<>();
            centers.add(playerPos);
            if (arena.isInBounds(second)) centers.add(second);
            if (arena.isInBounds(third)) centers.add(third);
            List<GridPos> warnTiles = new ArrayList<>();
            List<EnemyAction> splashes = new ArrayList<>();
            for (GridPos c : centers) {
                for (GridPos t : getAreaTiles(arena, c, 1)) {
                    if (!warnTiles.contains(t)) warnTiles.add(t);
                }
                splashes.add(new EnemyAction.AreaAttack(c, 1, isPhaseTwo() ? 5 : 4, "trident_storm"));
            }
            EnemyAction tridentAction = splashes.size() == 1
                ? splashes.get(0) : new EnemyAction.CompositeAction(splashes);
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                warnTiles, 1, tridentAction, 0xFF2299DD);
            return new EnemyAction.Idle();
        }

        return meleeOrApproach(self, arena, playerPos, isPhaseTwo() ? 2 : 0);
    }

    /**
     * Pick who wears the Conduction mark: any living combatant except the Tidecaller
     * itself - the player included, but deliberately at the same odds as everyone else.
     * Sometimes the chain starts inside the boss's own minion pack and never reaches the
     * player, which is what makes positioning a decision rather than a fixed dodge.
     */
    private int pickConductionMarkId(CombatEntity self, GridArena arena) {
        List<Integer> ids = new ArrayList<>();
        ids.add(BossWarning.MARK_PLAYER_ID); // the player is always a candidate
        for (CombatEntity e : arena.getOccupants().values()) {
            // The occupant map lists a multi-tile entity once per tile it covers.
            if (e == self || !e.isAlive() || e.isProjectile() || e.isBackgroundBoss()) continue;
            if (!ids.contains(e.getEntityId())) ids.add(e.getEntityId());
        }
        java.util.Collections.shuffle(ids);
        return ids.get(0);
    }

    /**
     * Resolves a marked id to its LIVE position, queried from the arena on every read -
     * never captured up front. The telegraph repaints wherever the mark walks, and a mark
     * that died or left resolves to null so the warning paints nothing and the bolt
     * fizzles. Capturing a position here instead would freeze the mark to its cast tile,
     * turning the spacing game into a one-tile sidestep.
     */
    private java.util.function.IntFunction<GridPos> liveTracker(GridArena arena) {
        return id -> {
            if (id == BossWarning.MARK_PLAYER_ID) return arena.getPlayerGridPos();
            for (CombatEntity e : arena.getOccupants().values()) {
                if (e.getEntityId() == id && e.isAlive()) return e.getGridPos();
            }
            return null;
        };
    }

    private List<GridPos> getWaterTiles(GridArena arena) {
        List<GridPos> tiles = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos pos = new GridPos(x, z);
                if (arena.getTile(pos) != null && arena.getTile(pos).getType() == TileType.WATER) {
                    tiles.add(pos);
                }
            }
        }
        return tiles;
    }
}
