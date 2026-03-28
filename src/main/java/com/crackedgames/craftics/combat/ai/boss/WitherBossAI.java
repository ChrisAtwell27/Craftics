package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Basalt Deltas Boss — "The Wither"
 * Entity: Wither | 65HP / 8ATK / 5DEF / Range 5 / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Wither Skull Barrage: Launches 3 (P2: 5) Wither Skull mobs that travel 2 (P2: 3) tiles/turn
 *   toward the player's position. Skulls are 3HP entities — destroyable. Deal 5 dmg + Wither on contact.
 * - Decay Aura: Passive — tiles within 2 (P2: 3) of the Wither become FIRE (wither decay).
 *   Refreshed every turn.
 * - Summon Wither Skeletons: Spawns 2 (P2: 3) Wither Skeletons. Max 4 (P2: 6) alive.
 * - Charge: Dashes up to 4 tiles in a line, dealing ATK+3 damage. Leaves decay in P2.
 *
 * Phase 2 — "Wither Armor" (≤50% HP):
 * - Immune to ranged attacks (melee only) — handled by CombatManager via isPhaseTwo()
 * - Transition explosion: 8 damage to all within 3 tiles
 * - More skulls (5), faster (3/turn), more skeletons, larger decay aura
 */
public class WitherBossAI extends BossAI {
    @Override public int getGridSize() { return 2; }

    private static final String CD_SKULLS = "skull_barrage";
    private static final String CD_SUMMON = "summon_skeletons";
    private static final String CD_CHARGE = "charge";
    private static final String CD_DECAY = "decay_aura";

    private static final int MAX_SKULLS_P1 = 6;
    private static final int MAX_SKULLS_P2 = 10;
    private static final int MAX_SKELETONS_P1 = 4;
    private static final int MAX_SKELETONS_P2 = 6;

    // Track skull IDs separately from skeleton IDs
    private final List<Integer> skullMinionIds = new ArrayList<>();

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);

        // Phase 2 transition explosion — 8 damage to all within 3 tiles
        // Telegraphed: warning for 1 turn before detonation
        List<GridPos> blastTiles = getAreaTiles(arena, self.getGridPos(), 3);
        EnemyAction explosion = new EnemyAction.AreaAttack(self.getGridPos(), 3, 8, "wither_explosion");
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.ENTITY_GLOW,
            blastTiles, 1, explosion, 0xFFFFFFFF // white flash
        );
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int maxSkulls = isPhaseTwo() ? MAX_SKULLS_P2 : MAX_SKULLS_P1;
        int maxSkeletons = isPhaseTwo() ? MAX_SKELETONS_P2 : MAX_SKELETONS_P1;

        // Clean up dead skulls
        skullMinionIds.removeIf(id -> {
            for (CombatEntity e : arena.getOccupants().values()) {
                if (e.getEntityId() == id && e.isAlive()) return false;
            }
            return true;
        });

        // === Decay Aura (passive, every turn) ===
        if (!isOnCooldown(CD_DECAY)) {
            setCooldown(CD_DECAY, 1); // refreshes every turn
            int decayRadius = isPhaseTwo() ? 3 : 2;
            List<GridPos> decayTiles = getAreaTiles(arena, myPos, decayRadius);
            // Remove tiles the boss is standing on (don't decay own position)
            decayTiles.removeIf(p -> p.equals(myPos));
            // Decay = fire tiles (2 damage/turn), 2 turns duration (refreshed each turn)
            // This is a secondary action — we'll compose it with the main ability
        }

        // === Wither Skull Barrage (every 2 turns) ===
        if (!isOnCooldown(CD_SKULLS) && skullMinionIds.size() < maxSkulls) {
            setCooldown(CD_SKULLS, 2);
            int skullCount = isPhaseTwo() ? 5 : 3;
            // Skulls spawn adjacent to the Wither and travel toward the player
            List<GridPos> skullSpawnPositions = findSummonPositionsNear(arena, myPos, 1, skullCount);
            if (!skullSpawnPositions.isEmpty()) {
                // Skulls are low-HP mobs that the CombatManager will move each turn
                // Using wither_skeleton as the skull entity (small, dark)
                // 3 HP, 5 ATK, 0 DEF — fragile but dangerous
                EnemyAction spawnSkulls = new EnemyAction.SummonMinions(
                    "minecraft:wither_skeleton", skullSpawnPositions.size(), skullSpawnPositions,
                    3, 5, 0
                );

                // Also apply decay aura as part of this turn
                int decayRadius = isPhaseTwo() ? 3 : 2;
                List<GridPos> decayTiles = getAreaTiles(arena, myPos, decayRadius);
                decayTiles.removeIf(p -> p.equals(myPos));

                // Telegraph the skull launch
                List<GridPos> warningTiles = new ArrayList<>(skullSpawnPositions);
                EnemyAction combined = new EnemyAction.CompositeAction(List.of(
                    spawnSkulls,
                    new EnemyAction.CreateTerrain(decayTiles, TileType.FIRE, 2)
                ));
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                    warningTiles, 1, combined, 0xFF222222 // dark particles
                );
                return new EnemyAction.Idle();
            }
        }

        // === Summon Wither Skeletons (every 4 turns) ===
        if (!isOnCooldown(CD_SUMMON) && getAliveMinionCount() - skullMinionIds.size() < maxSkeletons) {
            setCooldown(CD_SUMMON, 4);
            int summonCount = isPhaseTwo() ? 3 : 2;
            List<GridPos> summonPositions = findSummonPositions(arena, summonCount);
            if (!summonPositions.isEmpty()) {
                EnemyAction summon = new EnemyAction.SummonMinions(
                    "minecraft:wither_skeleton", summonPositions.size(), summonPositions,
                    10, 5, 1
                );
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    summonPositions, 1, summon, 0xFF553300 // dark brown
                );
                return new EnemyAction.Idle();
            }
        }

        // === Charge (when player is at range 3+) ===
        if (!isOnCooldown(CD_CHARGE) && dist >= 3) {
            setCooldown(CD_CHARGE, 3);
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> chargePath = getChargePath(arena, myPos, dir[0], dir[1], 4);
            if (!chargePath.isEmpty()) {
                List<EnemyAction> chargeActions = new ArrayList<>();
                chargeActions.add(new EnemyAction.Swoop(chargePath, self.getAttackPower() + 3));
                // In Phase 2, charge leaves decay trail
                if (isPhaseTwo()) {
                    chargeActions.add(new EnemyAction.CreateTerrain(chargePath, TileType.FIRE, 3));
                }
                EnemyAction chargeComposite = chargeActions.size() == 1
                    ? chargeActions.get(0)
                    : new EnemyAction.CompositeAction(chargeActions);

                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    chargePath, 1, chargeComposite, 0xFF4400AA // dark purple
                );
                return new EnemyAction.Idle();
            }
        }

        // === Decay aura as standalone if no other ability fired ===
        int decayRadius = isPhaseTwo() ? 3 : 2;
        List<GridPos> decayTiles = getAreaTiles(arena, myPos, decayRadius);
        decayTiles.removeIf(p -> p.equals(myPos));
        if (!decayTiles.isEmpty()) {
            // Create decay terrain + melee/approach
            EnemyAction decay = new EnemyAction.CreateTerrain(decayTiles, TileType.FIRE, 2);
            if (dist <= 1) {
                return new EnemyAction.CompositeAction(List.of(
                    decay,
                    new EnemyAction.Attack(self.getAttackPower())
                ));
            }
            return decay;
        }

        // Fallback: melee or approach
        return meleeOrApproach(self, arena, playerPos, 0);
    }

    /**
     * Check if the Wither is in Phase 2 (ranged immunity).
     * Called by CombatManager to block ranged damage.
     */
    public boolean isRangedImmune() {
        return isPhaseTwo();
    }
}
