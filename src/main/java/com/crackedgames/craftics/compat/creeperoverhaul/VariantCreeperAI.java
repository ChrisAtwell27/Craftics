package com.crackedgames.craftics.compat.creeperoverhaul;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared AI for Creeper Overhaul variants. Config-driven so each variant can
 * pick its fuse length, blast radius, blast damage multiplier, camouflage
 * distance, and a list of {@link EnemyAction.BlastEffect status effects} to
 * apply on detonation.
 * <p>
 * Behaves like the vanilla {@link com.crackedgames.craftics.combat.ai.CreeperAI}
 * with two tweaks:
 * <ol>
 *   <li><b>Fuse length</b>: {@code fuseTurns=0} means "detonate the same turn
 *       you reach adjacency" (desert_creeper short fuse). {@code fuseTurns=1}
 *       matches vanilla (move+prime, detonate next turn).</li>
 *   <li><b>Camouflage</b>: if {@code camouflageRange > 0} and the player is
 *       farther than that distance, the variant just idles on its turn
 *       instead of advancing. Used by jungle/bamboo variants that hide
 *       inside foliage until you get close.</li>
 * </ol>
 */
public final class VariantCreeperAI implements EnemyAI {

    /**
     * Per-variant tuning. Use the Builder ({@link #of(String)}) to construct.
     */
    public static final class Config {
        final String debugName;
        int baseRadius = 1;
        int chargedRadius = 2;
        int damageBonus = 3;
        int fuseTurns = 1;
        int camouflageRange = 0;
        List<EnemyAction.BlastEffect> blastEffects = new ArrayList<>();
        int extraSpeed = 0;

        private Config(String debugName) { this.debugName = debugName; }

        public Config radius(int base, int charged) { this.baseRadius = base; this.chargedRadius = charged; return this; }
        public Config damageBonus(int bonus) { this.damageBonus = bonus; return this; }
        public Config shortFuse() { this.fuseTurns = 0; return this; }
        public Config camouflage(int range) { this.camouflageRange = range; return this; }
        public Config extraSpeed(int bonus) { this.extraSpeed = bonus; return this; }
        public Config blast(CombatEffects.EffectType effect, int turns) {
            this.blastEffects.add(new EnemyAction.BlastEffect(effect, turns));
            return this;
        }
        public Config blast(CombatEffects.EffectType effect, int turns, int amplifier) {
            this.blastEffects.add(new EnemyAction.BlastEffect(effect, turns, amplifier));
            return this;
        }
    }

    public static Config of(String debugName) { return new Config(debugName); }

    private final Config cfg;

    public VariantCreeperAI(Config cfg) { this.cfg = cfg; }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Apply the extraSpeed bonus as a persistent speed offset. CombatEntity
        // tracks a speedBonus field that stacks with base move speed.
        if (cfg.extraSpeed > 0 && self.getSpeedBonus() < cfg.extraSpeed) {
            self.setSpeedBonus(cfg.extraSpeed);
        }

        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Charge check — ranged hit while not fusing makes it a charged creeper
        if (self.wasDamagedSinceLastTurn() && !self.isEnraged() && self.getFuseTimer() == 0) {
            self.setEnraged(true);
        }

        int explosionRadius = self.isEnraged() ? cfg.chargedRadius : cfg.baseRadius;
        int explosionDamage = self.isEnraged()
            ? self.getAttackPower() * 2
            : self.getAttackPower() + cfg.damageBonus;

        // Fuse already active → explode now, carry the variant's blast effects
        if (self.getFuseTimer() > 0) {
            return new EnemyAction.Explode(explosionDamage, explosionRadius, cfg.blastEffects);
        }

        // Camouflage: player is far enough away that we hide in foliage.
        // Still processes the "got damaged → charge" branch above so ranged
        // pokes don't leave the mob stuck inactive.
        if (cfg.camouflageRange > 0 && dist > cfg.camouflageRange && !self.isEnraged()) {
            return new EnemyAction.Idle();
        }

        // Adjacent — prime fuse
        if (dist <= 1) {
            if (cfg.fuseTurns <= 0) {
                // Short fuse: detonate immediately instead of priming for next turn
                return new EnemyAction.Explode(explosionDamage, explosionRadius, cfg.blastEffects);
            }
            self.setFuseTimer(cfg.fuseTurns);
            return new EnemyAction.StartFuse();
        }

        // Rush toward player
        GridPos target = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, self.getMoveSpeed());
        if (target == null) target = playerPos;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, target, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) <= 1) {
            if (cfg.fuseTurns <= 0) {
                // Short-fuse creepers still need to move first this turn, then
                // detonate next turn — the "instant detonate" branch only
                // triggers when already adjacent. This matches the vanilla
                // "move+prime" cadence so the player has a fair chance to
                // disengage.
                self.setFuseTimer(1);
            } else {
                self.setFuseTimer(cfg.fuseTurns);
            }
            return new EnemyAction.Move(path);
        }

        return new EnemyAction.Move(path);
    }
}
