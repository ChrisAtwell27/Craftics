package com.crackedgames.craftics.combat.ai.deeperanddarker;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.entity.mob.MobEntity;

import java.util.List;

/**
 * Deeper-and-Darker Shriek Worm (Echoing Forest / Overcast Columns).
 *
 * <p>A VERY tall, rooted turret that begins the fight as a hidden ambush: it is
 * invisible (and un-hover-targetable - vanilla {@code isInvisible()} entities are
 * skipped by {@code TileRaycast}) until ANY party member steps into its melee
 * range (adjacent to its footprint). The moment someone gets that close it rears
 * up - it reveals permanently and, being immobile, lashes out for heavy damage
 * across its full 3-tile reach, locking the player in place (root applied via its
 * {@code root} theme tag in {@link com.crackedgames.craftics.combat.MobThemeTags}).
 *
 * <p>So the danger is walking blind into a worm you can't see; once triggered, the
 * counterplay is the usual immobile-turret answer - break line of sight or leave
 * its bubble. The reveal is a one-way latch (stored in {@code aiMemory}); it never
 * goes back to hiding once it has struck.
 */
public class ShriekWormAI implements EnemyAI {

    /** Fixed reach; the worm never moves, so this is its whole threat radius. */
    private static final int REACH = 3;
    /** aiMemory key: 1 once the worm has been triggered (revealed) this fight. */
    private static final String REVEALED_KEY = "worm_revealed";

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        MobEntity mob = self.getMobEntity();
        boolean revealed = self.getAiMemory(REVEALED_KEY, 0) == 1;

        // Still hidden: stay invisible and idle until someone comes into melee
        // range of the worm's footprint. Checks EVERY party member, not just the
        // tracked player, so a co-op teammate walking up triggers it too.
        if (!revealed) {
            if (!anyPlayerInMelee(self, arena)) {
                if (mob != null) mob.setInvisible(true);
                return new EnemyAction.Idle();
            }
            // Triggered - rear up out of the dark. One-way latch.
            self.setAiMemory(REVEALED_KEY, 1);
            if (mob != null) mob.setInvisible(false);
        }

        int dist = self.minDistanceTo(playerPos);

        // In reach with a clear cardinal line: lash out. High damage + root
        // (the root rides the on-hit theme tag).
        if (dist <= REACH && AIUtils.hasCardinalLOS(arena, myPos, playerPos, REACH)) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "shriek");
        }

        // In range but no clean line (something between us): shriek anyway - a
        // rooted worm has nothing else to do and should still threaten.
        if (dist <= REACH) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "shriek");
        }

        // Revealed but out of reach: the worm is immobile, so it just waits.
        return new EnemyAction.Idle();
    }

    // NOTE: intentionally NOT overriding isHostileThreat. A hidden worm is still a
    // latent threat (it will strike the instant a player reaches it), so it must
    // keep the fight "live" for the anti-farm auto-end - otherwise a room that
    // rolled only worms could auto-complete before the player ever triggered one.
    // Its position is hidden purely at the render layer (invisible mob + danger-
    // tile skip in CombatManager), not by pretending it isn't dangerous.

    /** Spawn hidden: the worm is an ambush until a player reaches its melee range. */
    @Override
    public boolean spawnsInvisible(CombatEntity self, GridArena arena) {
        return self.getAiMemory(REVEALED_KEY, 0) == 0;
    }

    /** True if any party member stands adjacent (Chebyshev 1) to the worm's footprint. */
    private boolean anyPlayerInMelee(CombatEntity self, GridArena arena) {
        List<GridPos> players = arena.getAllPlayerGridPositions();
        if (players == null || players.isEmpty()) {
            return self.minDistanceTo(arena.getPlayerGridPos()) <= 1;
        }
        for (GridPos p : players) {
            if (self.minDistanceTo(p) <= 1) return true;
        }
        return false;
    }
}
