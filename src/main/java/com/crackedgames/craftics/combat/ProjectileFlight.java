package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * One thrown thing, in the air, over several ticks.
 *
 * <p>Projectiles used to be a line of particles drawn in a single tick with the damage already
 * applied before the first particle appeared. This is the other half: a real entity that
 * travels, lands, and only THEN does what it came to do.
 *
 * <p><b>The chain is fixed at release.</b> Every hop is decided before the first one leaves,
 * from the grid as it stands at that moment. It is deliberately not recomputed mid-flight: the
 * player should be able to see what they committed to, and a bounce that re-picks its target
 * would chase someone who moved after the throw. A hop whose target died earlier in the same
 * chain is skipped, and a hop that kills ends the chain there rather than continuing to bounce
 * off a corpse.
 *
 * <p><b>The lock is the dangerous part.</b> While a flight is up, the fight refuses player
 * input, which is what makes the next action wait. Every way out of a flight - the target
 * dies, the entity is culled, the chunk unloads, the fight ends mid-air - must release it, so
 * the finish callback is invoked exactly once from {@link #finish} and nowhere else, and the
 * caller arms a tick ceiling that clears the lock regardless.
 */
public final class ProjectileFlight {

    /** One leg of the journey: fly to {@code target}, then hit {@code entityId} for {@code damage}. */
    public record Hop(GridPos target, int entityId, int damage) {}

    /** What actually happens when a hop lands. Returns true if the target died. */
    public interface ImpactHandler {
        boolean onImpact(Hop hop);
    }

    /**
     * Point {@code e} along the direction it is travelling, pitch included.
     *
     * <p>Yaw alone leaves an arrow flying sideways the moment the shot is not perfectly level,
     * and every shot has some rise: the projectile leaves at chest height and arrives lower.
     * Item entities ignore both and spin on their own, which is the right look for a thrown
     * disc anyway - this is here for the ones that are shaped like their direction.
     */
    public static void faceAlong(Entity e, double dx, double dy, double dz) {
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.0e-4 && Math.abs(dy) < 1.0e-4) return;
        e.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        e.setPitch((float) Math.toDegrees(-Math.atan2(dy, flat)));
        e.setHeadYaw(e.getYaw());
    }

    /**
     * THE mover for every flight: hold the entity exactly where the walker says, facing the
     * way it is going.
     *
     * <p>Teleporting alone is not enough. A projectile is still a live entity and its own tick
     * runs after this one: an item overlapping any block is ejected upward by vanilla's
     * push-out, leftover velocity is re-applied on top of the position just set, and an arrow
     * recomputes its own yaw and pitch from that velocity. What you get is something that
     * drifts off on its own instead of flying where it was sent - a chakram rising over the
     * thrower's head rather than crossing the arena. Gravity off, velocity zeroed and
     * collision disabled EVERY tick is what makes the walker the only thing moving it.
     */
    public static EntityWalker.Mover holdingMover(Entity e) {
        double[] prev = {Double.NaN, 0, 0};
        return (x, y, z, yaw) -> {
            e.setNoGravity(true);
            e.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
            e.velocityModified = false;
            e.noClip = true;
            e.setPosition(x, y, z);
            e.requestTeleport(x, y, z);
            if (!Double.isNaN(prev[0])) faceAlong(e, x - prev[0], y - prev[1], z - prev[2]);
            prev[0] = x; prev[1] = y; prev[2] = z;
        };
    }

    /** Where a hop's flight should start and end in world space, and how the visual moves. */
    public interface Flightpath {
        double[] originOf(Hop hop);          // {x, y, z} the hop leaves from
        double[] arrivalOf(Hop hop);         // {x, y, z} the hop lands at
        EntityWalker.Mover moverFor(Entity visual);
    }

    /**
     * Where a leg goes to be ticked.
     *
     * <p>Flights do NOT run their own tick loop. They hand each leg to the combat manager's
     * {@code activeWalkers} list, which is the one animation path in this codebase that is
     * proven to move entities - the trader walk-ups and the elytra launch ride it, it is
     * ticked unconditionally before every guard, and it already handles a callback adding the
     * next leg mid-iteration. A second bespoke loop was how the first version of this ended up
     * spawning projectiles that never moved.
     */
    public interface LegScheduler {
        void schedule(EntityWalker leg);
    }

    private final Entity visual;
    private final List<Hop> hops;
    private final ImpactHandler impact;
    private final Flightpath path;
    private final Runnable onFinished;
    private final LegScheduler scheduler;
    private final double[] homeXyz;          // null for a projectile that does not come back
    private final int ticksPerHop;

    private int index = 0;
    private boolean finished = false;
    private EntityWalker leg;

    public ProjectileFlight(Entity visual, List<Hop> hops, ImpactHandler impact, Flightpath path,
                            double[] homeXyz, int ticksPerHop, LegScheduler scheduler,
                            Runnable onFinished) {
        this.visual = visual;
        this.hops = new ArrayList<>(hops);
        this.impact = impact;
        this.path = path;
        this.homeXyz = homeXyz;
        this.ticksPerHop = Math.max(1, ticksPerHop);
        this.scheduler = scheduler;
        this.onFinished = onFinished;
    }

    /** Total ticks this flight can take, for the caller's input lock and its ceiling. */
    public int plannedTicks() {
        return ticksPerHop * (hops.size() + (homeXyz != null ? 1 : 0)) + 2;
    }

    public boolean isFinished() { return finished; }

    /** Begin the first leg. Safe to call once; a flight with no hops finishes immediately. */
    public void start() {
        if (hops.isEmpty()) { finish(); return; }
        beginLeg(hops.get(0));
    }

    /**
     * Watchdog only. The legs are ticked by the scheduler's list, not from here; this exists
     * so a flight whose visual disappeared still settles its damage and releases the turn.
     */
    public void tick() {
        if (finished) return;
        if (visual == null || visual.isRemoved()) resolveRemaining();
    }

    private void beginLeg(Hop hop) {
        double[] from = index == 0 ? path.originOf(hop) : path.arrivalOf(hops.get(index - 1));
        double[] to = path.arrivalOf(hop);
        leg = new EntityWalker(path.moverFor(visual),
            from[0], from[1], from[2], to[0], to[1], to[2], ticksPerHop, () -> onLegLanded(hop));
        scheduler.schedule(leg);
    }

    private void onLegLanded(Hop hop) {
        boolean died = impact.onImpact(hop);
        index++;
        // A kill ends the chain: bouncing off a corpse onto a third target is not what the
        // player aimed at, and the hop list was built against a board that no longer holds.
        if (died || index >= hops.size()) {
            goHomeOrFinish();
            return;
        }
        beginLeg(hops.get(index));
    }

    private void goHomeOrFinish() {
        if (homeXyz == null) { finish(); return; }
        double[] from = index > 0 ? path.arrivalOf(hops.get(index - 1)) : homeXyz;
        leg = new EntityWalker(path.moverFor(visual),
            from[0], from[1], from[2], homeXyz[0], homeXyz[1], homeXyz[2],
            ticksPerHop, this::finish);
        scheduler.schedule(leg);
    }

    /** Apply every hop that has not landed yet, without animation. Used when the visual is
     *  gone: the damage the player paid for still happens, it just stops being a spectacle. */
    private void resolveRemaining() {
        while (index < hops.size()) {
            boolean died = impact.onImpact(hops.get(index));
            index++;
            if (died) break;
        }
        finish();
    }

    /** The one exit. Idempotent, and the only place the lock is released. */
    private void finish() {
        if (finished) return;
        finished = true;
        leg = null;
        if (visual != null && !visual.isRemoved()) visual.discard();
        if (onFinished != null) onFinished.run();
    }

    /** Force the flight to end now, resolving anything still owed. For combat teardown. */
    public void abort() {
        if (finished) return;
        resolveRemaining();
    }
}
