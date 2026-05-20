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
 * - Wither Skull Barrage: Spawns 3 (P2: 5) Wither Skull projectile entities that travel
 *   2 tiles/turn in a straight line. Skulls are 3HP — killable by the player.
 *   Deal 5 dmg + Wither on player contact. No AOE.
 * - Decay Aura: Passive — tiles within 2 (P2: 3) of the Wither become FIRE (wither decay).
 *   Refreshed every turn.
 * - Summon Wither Skeletons: Spawns 2 (P2: 3) Wither Skeletons. Max 4 (P2: 6) alive.
 * - Charge: Dashes up to 4 tiles in a line, dealing ATK+3 damage. Leaves decay in P2.
 *
 * Phase 2 — "Wither Armor" (≤50% HP):
 * - Immune to ranged attacks (melee only) — handled by CombatManager via isPhaseTwo()
 * - Transition explosion: 8 damage to all within 3 tiles
 * - More skulls (5), more skeletons, larger decay aura
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

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);

        // Phase 2 transition explosion — heavier hit + Wither application
        // signal via the effectName so the AreaAttack resolver can apply Wither
        // to the player on contact (handled in CombatManager).
        List<GridPos> blastTiles = getAreaTiles(arena, self.getGridPos(), 3);
        EnemyAction explosion = new EnemyAction.AreaAttack(
            self.getGridPos(), 3, 12, "wither_explosion");
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

        // === Decay Aura (passive, every turn) ===
        if (!isOnCooldown(CD_DECAY)) {
            setCooldown(CD_DECAY, 1); // refreshes every turn
        }

        // === Wither Skull Barrage (every 2 turns) — spawn projectile entities ===
        if (!isOnCooldown(CD_SKULLS) && getAliveProjectileCount() < maxSkulls) {
            setCooldown(CD_SKULLS, 2);
            int skullCount = isPhaseTwo() ? 5 : 3;
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> skullSpawnPositions = getProjectileSpawnPositions(arena, myPos, getGridSize(), playerPos, skullCount);

            if (!skullSpawnPositions.isEmpty()) {
                List<int[]> directions = new ArrayList<>();
                for (int i = 0; i < skullSpawnPositions.size(); i++) {
                    directions.add(new int[]{dir[0], dir[1]});
                }

                // Tougher skulls — 6 HP and 7 damage. Still killable by the
                // player, but no longer one-tap-killable by a basic bow shot.
                // Entity type is the actual wither skull (was a copy-paste
                // bug referencing "minecraft:wither_skeleton").
                EnemyAction spawnSkulls = new EnemyAction.SpawnProjectile(
                    "minecraft:wither_skull", skullSpawnPositions, directions,
                    6, 7, 0, "wither_skull"
                );

                List<GridPos> warningTiles = new ArrayList<>(skullSpawnPositions);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                    warningTiles, 1, spawnSkulls, 0xFF222222 // dark particles
                );
                return new EnemyAction.Idle();
            }
        }

        // === Summon Wither Skeletons (every 4 turns) ===
        if (!isOnCooldown(CD_SUMMON) && getAliveMinionCount() < maxSkeletons) {
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

        // === Decay aura — Wither application instead of fire tiles ===
        // The Wither pulses a corrupted aura around itself. Anyone inside
        // takes a chunk of damage AND gets the Wither effect refreshed, which
        // bypasses Fire Resistance and makes standing near the boss
        // genuinely dangerous. Larger radius in Phase 2.
        int decayRadius = isPhaseTwo() ? 4 : 3;
        EnemyAction decay = new EnemyAction.AreaAttack(
            myPos, decayRadius, 2, "wither_decay");
        if (dist <= 1) {
            // In melee, hit the player AND tick the aura.
            return new EnemyAction.CompositeAction(List.of(
                decay,
                new EnemyAction.Attack(self.getAttackPower())
            ));
        }
        // Outside melee, fire the aura and step toward the player so the
        // boss closes the gap instead of standing still firing pulses.
        EnemyAction approach = meleeOrApproach(self, arena, playerPos, 0);
        if (approach instanceof EnemyAction.Idle) {
            return decay;
        }
        return new EnemyAction.CompositeAction(List.of(decay, approach));
    }

    /**
     * Check if the Wither is in Phase 2 (ranged immunity).
     * Called by CombatManager to block ranged damage.
     */
    public boolean isRangedImmune() {
        return isPhaseTwo();
    }
}
