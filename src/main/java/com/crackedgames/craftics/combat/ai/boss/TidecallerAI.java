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
 * - Tidal Wave: a wall of water spanning the FULL arena width, 3 tiles thick, spawns at the
 *   end of the arena's long axis farther from the player and marches 3 tiles per turn until
 *   it exits the far side. It ticks autonomously (CombatManager, like the dragon's breath
 *   wave), so the boss keeps acting while it sweeps. It deals NO damage: anyone caught in the
 *   band is carried the same 3 tiles the wave moved, which is how you end up in the drowned
 *   pack or standing in water when the lightning lands. See SweepingWave.
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

    /**
     * Manhattan reach of a Conduction chain jump: the bolt arcs from each struck
     * combatant to every combatant within this many tiles. This is the single
     * source of truth for the chain range - both the warning paint (the radius-2
     * diamond around the mark) and the chain walk itself
     * ({@link com.crackedgames.craftics.combat.CombatManager#resolveConductionChain})
     * read it, so the telegraph can never claim a range the mechanic does not use.
     */
    public static final int CONDUCTION_CHAIN_RANGE = 2;

    private boolean delugeCast = false;

    // ─── Tidal Wave system ────────────────────────────────────────────────
    // The wave is a persistent hazard that advances on its own every turn, not on the
    // boss's action. CombatManager calls tickWave() at each turn start, exactly as it
    // does for the dragon's breath waves. At most one sweeps at a time.

    private com.crackedgames.craftics.combat.SweepingWave activeWave = null;

    /** The sweeping wave, or null when none is crossing. Read by CombatManager to tick it. */
    public com.crackedgames.craftics.combat.SweepingWave getActiveWave() { return activeWave; }

    /**
     * Advance the wave one turn and report the tiles it now covers, which the caller floods
     * and paints. Returns an empty list when no wave is crossing.
     *
     * <p>The returned list IS the flood list and the carry footprint - one call, one set of
     * tiles, so what is shown and what resolves cannot drift apart. The wave clears itself
     * once its trailing edge leaves the far side.
     */
    public List<GridPos> tickWave() {
        if (activeWave == null) return List.of();
        activeWave.advance();
        if (activeWave.hasExited()) {
            activeWave = null;
            return List.of();
        }
        return activeWave.tiles();
    }

    /** Per-turn carry vector of the live wave: {dx, dz}, or null when none is crossing. */
    public int[] getWaveCarry() {
        if (activeWave == null) return null;
        return new int[] { activeWave.carryDx(), activeWave.carryDz() };
    }

    /** True when {@code pos} is inside the live wave band right now. */
    public boolean waveCovers(GridPos pos) {
        return activeWave != null && activeWave.covers(pos);
    }

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

        // Conduction FIRST: this is the Tidecaller's signature mechanic and it fires on its
        // own cooldown rhythm rather than on a leftover turn. Below the others it was
        // effectively dead code - Call of the Deep, Riptide and Tidal Wave are cheap, and
        // Tidal Wave in particular takes the turn on a bare cooldown check with nothing else
        // to satisfy, so a turn rarely reached this far and players never saw the ability.
        // The cooldown (3 in phase 2, 4 in phase 1) is what keeps it from dominating; the
        // priority only guarantees it is not starved.
        //
        // Mark one combatant, then a turn later lightning strikes it and arcs between
        // everything within 2 tiles of the last link - the boss's own drowned included. The
        // flood already Soaks whoever stands in it, and Soaked doubles lightning, so the water
        // is the setup and this is the payoff. The mark FOLLOWS its wearer (see the tracker),
        // so counterplay is spacing, not dodging: you cannot escape the bolt, only decide who
        // stands near you when it lands.
        if (!isOnCooldown(CD_CONDUCTION)) {
            setCooldown(CD_CONDUCTION, isPhaseTwo() ? 3 : 4);
            int markId = pickConductionMarkId(self, arena);
            EnemyAction strike = new EnemyAction.AreaAttack(
                null, 0, isPhaseTwo() ? 6 : 4, "conduction:" + markId);
            // Paint the CHAIN RADIUS around the mark, not just the mark tile: the
            // bolt jumps between combatants within CONDUCTION_CHAIN_RANGE tiles of
            // each link (CombatManager.resolveConductionChain, ConductionChain.walk),
            // so the player must be able to read "stand this close and the bolt
            // reaches me". The warning still TRACKS - the diamond is recomputed
            // around the mark's live position on every read (BossWarning), so it
            // follows the mark as it walks. The radius is the SAME constant the
            // chain uses, so the telegraph cannot lie about range.
            pendingWarning = BossWarning.tracking(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                markId, liveTracker(arena), 1, strike, 0xFFFFDD33,
                CONDUCTION_CHAIN_RANGE);
            return new EnemyAction.Idle();
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

        // Tidal Wave: a real wave, not a puddle on the player's column. It spans the full
        // arena width, is 3 tiles thick, and marches 3 tiles per turn from the far end until
        // it leaves the other side. Only one sweeps at a time - a second wall of water
        // crossing the first would leave the player nowhere legible to stand.
        //
        // The wave is NOT aimed and does NOT follow: it is moving terrain. The threat is
        // being carried, not being hit. It deals no damage at all; whoever it catches rides
        // 3 tiles with it, which is how you get dumped into the drowned pack or left standing
        // in fresh water right before Conduction picks a mark. The water it leaves behind
        // Soaks whoever wades it, and Soaked doubles the bolt, so this ability is the setup
        // and Conduction is the payoff.
        //
        // The wave spawns here, then ticks itself in CombatManager (same pattern as the
        // dragon's breath wave), so the Tidecaller keeps acting while it sweeps. Because it
        // costs the boss no turns after the cast, the cooldown stays at 3.
        if (!isOnCooldown(CD_WAVE) && activeWave == null) {
            setCooldown(CD_WAVE, 3);
            activeWave = com.crackedgames.craftics.combat.SweepingWave.spawn(
                arena.getWidth(), arena.getHeight(), playerPos);
            // Telegraph the band on its spawn row before it starts moving: the wave gets a
            // turn of paint where it stands, then advances on the next tick.
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                activeWave.tiles(), 1, new EnemyAction.Idle(), 0xFF3388CC);
            return new EnemyAction.Idle();
        }

        // Trident Storm at range - a true volley: three 3×3 impact zones (one on
        // the player, one ahead toward the boss, one on the flank), so a lazy
        // one-tile sidestep can walk straight into the next splash.
        //
        // The zones OVERLAP by design (centres sit 2 apart, radius 1), and that overlap is
        // exactly why this resolves as ONE attack over the union of the three footprints
        // rather than one attack per zone. Three independent splashes damaged a seam tile
        // once each - up to 12 in phase 1, 15 in phase 2, a near one-shot - while the
        // telegraph deduped for display and painted that tile identically to a 4-damage one.
        // The volley is meant to punish a lazy sidestep by covering ground, not to hide a
        // triple-damage seam the player cannot see. Union first: every covered tile is hit
        // exactly once, so the paint and the damage are the same set by construction.
        if (!isOnCooldown(CD_TRIDENT) && dist >= 2 && dist <= 4) {
            setCooldown(CD_TRIDENT, 2);
            int[] toward = getDirectionToward(playerPos, myPos);
            GridPos second = new GridPos(playerPos.x() + toward[0] * 2, playerPos.z() + toward[1] * 2);
            GridPos third = new GridPos(playerPos.x() + toward[1] * 2, playerPos.z() + toward[0] * 2);
            List<GridPos> centers = new ArrayList<>();
            centers.add(playerPos);
            if (arena.isInBounds(second)) centers.add(second);
            if (arena.isInBounds(third)) centers.add(third);

            // Union of the splash footprints, clipped to the arena. This IS the warning
            // list and the damage list - deliberately the same object, so the two can
            // never drift apart again.
            List<GridPos> impactTiles = new ArrayList<>();
            for (GridPos t : com.crackedgames.craftics.combat.AoeShapes.unionOfDiscs(centers, 1)) {
                if (arena.isInBounds(t)) impactTiles.add(t);
            }

            EnemyAction tridentAction = new EnemyAction.TileAreaAttack(
                impactTiles, playerPos, isPhaseTwo() ? 5 : 4, "trident_storm");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                impactTiles, 1, tridentAction, 0xFF2299DD);
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
