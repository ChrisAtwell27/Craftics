package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.PotterySherdSpells;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a {@link SherdSpell}.
 *
 * <p>One method replaces twenty-three. Every sherd used to interleave four concerns - resolve
 * targets, change game state, stage particles across ticks, build a chat line - by hand, in its
 * own order, which is why no two of them could share anything. Here they are four phases in a
 * fixed order, and a spell only supplies the data for each.
 *
 * <p>The ordering matters and is deliberate: <b>all</b> game state resolves immediately, and
 * only the visuals are staged across ticks. That is how the originals behaved (damage landed at
 * once; the fireball you watched fly was catching up to a hit that had already happened), and
 * it is what keeps a spell safe against a player disconnecting mid-animation.
 */
public final class SpellEngine {

    private SpellEngine() {}

    /**
     * Cast {@code spell} and return the chat line, with any directives for CombatManager
     * peeled onto the front. Never null.
     */
    public static String cast(SherdSpell spell, SpellContext ctx) {
        ServerWorld world = ctx.world();
        BlockPos casterBlock = ctx.casterBlock();

        // Phase 1 - the cast flourish, at once.
        spell.castVisuals().playCast(world, casterBlock);

        // Phase 2 - resolve every step against live game state.
        SpellReport report = new SpellReport();
        List<StepOutcome> outcomes = new ArrayList<>();
        for (SpellStep step : spell.steps()) {
            List<SpellTarget> targets = step.selector().resolve(ctx);
            int before = report.fragments().size();
            List<GridPos> tiles = new ArrayList<>();
            for (SpellTarget target : targets) {
                for (SpellEffect effect : step.effects()) {
                    effect.apply(ctx, target, report);
                }
                tiles.add(target.tile());
            }
            if (!step.heading().isEmpty() && report.fragments().size() > before) {
                report.fragments().add(before, step.heading());
            }
            outcomes.add(new StepOutcome(step, tiles));
        }

        // Phase 3 - stage the visuals behind the state change.
        for (StepOutcome outcome : outcomes) {
            SpellVisuals visuals = outcome.step.visuals();
            List<BlockPos> blocks = new ArrayList<>();
            for (GridPos tile : outcome.tiles) blocks.add(ctx.blockOf(tile));
            if (blocks.isEmpty()) continue;

            PotterySherdSpells.queue(visuals.trailDelay(),
                () -> visuals.playTrail(world, casterBlock, blocks));
            PotterySherdSpells.queue(visuals.impactDelay(), () -> {
                for (BlockPos block : blocks) visuals.playImpact(world, block);
            });
        }

        // Phase 4 - the sentence.
        return withDirectives(ctx, compose(spell, report));
    }

    /** Join the banner and the accumulated fragments into one readable line. */
    private static String compose(SherdSpell spell, SpellReport report) {
        if (report.isEmpty()) return spell.banner() + " " + spell.emptyMessage();
        StringBuilder sb = new StringBuilder(spell.banner());
        for (String fragment : report.fragments()) {
            sb.append(' ').append(fragment);
        }
        return sb.toString();
    }

    /**
     * Prefix the directives, innermost last, so each parser can peel one and hand the rest on.
     *
     * <p>The prefix protocol was written when exactly one sherd could emit exactly one
     * directive, so each reader takes {@code split("|", 2)} and treats the tail as the message.
     * Chaining them keeps every existing reader correct for the first directive and lets a
     * composed sherd - a trap that also summons, say - carry more than one, provided the reader
     * loops. See {@code CombatManager.applySherdDirectives}.
     */
    private static String withDirectives(SpellContext ctx, String message) {
        String out = message;
        List<String> directives = ctx.directives();
        for (int i = directives.size() - 1; i >= 0; i--) {
            out = directives.get(i) + "|" + out;
        }
        return out;
    }

    private record StepOutcome(SpellStep step, List<GridPos> tiles) {}

    // ─────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Every tile this spell may be aimed at, for the range indicator.
     *
     * <p>Derived by asking {@link #validate} about each tile in reach rather than by
     * re-deriving the rule. A highlight that promises a cast the server then refuses is worse
     * than no highlight, and the only way to be sure the two agree is for the indicator to BE
     * the validator - the attack-tile highlight next to it keeps its own copy of the reach
     * rules in step by hand, and that is exactly the drift this avoids.
     *
     * <p>Empty for a self-cast spell, which has no tile to aim at; see
     * {@link #affectedAreaTiles} for what those show instead.
     */
    public static java.util.Set<GridPos> targetableTiles(
            SherdSpell spell, com.crackedgames.craftics.core.GridArena arena) {
        java.util.Set<GridPos> out = new java.util.LinkedHashSet<>();
        if (spell == null || arena == null || spell.isSelfCast()) return out;

        GridPos caster = arena.getPlayerGridPos();
        if (caster == null) return out;
        int range = spell.range();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > range) continue;
                GridPos tile = new GridPos(caster.x() + dx, caster.z() + dz);
                if (validate(spell, arena, tile) == null) out.add(tile);
            }
        }
        return out;
    }

    /**
     * The ground a self-cast spell will actually touch.
     *
     * <p>A self-cast sherd has no aim tile, but several of them still have a shape - the tidal
     * surge sweeps two tiles around the caster, a petsplosion erupts around every pet. Showing
     * nothing for those would read as "this sherd does not target", when the truth is that it
     * targets a fixed area the player positions themselves into.
     *
     * <p>Purely geometric: it asks where the steps reach, not who is standing there, so the
     * indicator is stable while the player walks around deciding rather than flickering as
     * enemies enter and leave the radius.
     */
    public static java.util.Set<GridPos> affectedAreaTiles(
            SherdSpell spell, com.crackedgames.craftics.core.GridArena arena,
            java.util.List<CombatEntity> combatants) {
        java.util.Set<GridPos> out = new java.util.LinkedHashSet<>();
        if (spell == null || arena == null || !spell.isSelfCast()) return out;

        GridPos caster = arena.getPlayerGridPos();
        if (caster == null) return out;

        for (SpellStep step : spell.steps()) {
            Selector selector = step.selector();
            int radius = selector.radius();
            if (radius <= 0) continue;
            java.util.List<GridPos> centres = new java.util.ArrayList<>();
            switch (selector.origin()) {
                case CASTER, TARGET_TILE -> centres.add(caster);
                case EACH_PET -> {
                    for (CombatEntity e : combatants) {
                        if (e.isAlive() && e.isAlly() && !e.isSeekerProjectile()) {
                            centres.add(e.getGridPos());
                        }
                    }
                }
            }
            for (GridPos centre : centres) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > radius) continue;
                        GridPos tile = new GridPos(centre.x() + dx, centre.z() + dz);
                        if (arena.isInBounds(tile)) out.add(tile);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Whether this spell can legally be cast at {@code targetTile}. Returns an error to show
     * the player, or null when the cast is fine.
     *
     * <p>Reads the spell's own {@link SherdSpell.TargetMode} instead of naming sherds. The old
     * validator special-cased Explorer and Danger by item identity and lumped everything else
     * into "needs an enemy", so a new sherd that targeted a tile was invalid by default until
     * somebody remembered to add a branch here.
     */
    public static String validate(SherdSpell spell, com.crackedgames.craftics.core.GridArena arena,
                                  GridPos targetTile) {
        if (spell.isSelfCast()) return null;
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";

        GridPos casterPos = arena.getPlayerGridPos();
        if (casterPos.manhattanDistance(targetTile) > spell.range()) {
            return "§cOut of range! (max " + spell.range() + " tiles)";
        }

        switch (spell.targetMode()) {
            case EMPTY_TILE -> {
                if (arena.isOccupied(targetTile)) return "§cTile must be empty!";
                var tile = arena.getTile(targetTile);
                if (tile == null || !tile.isWalkable()) return "§cInvalid tile!";
            }
            case WALKABLE_TILE -> {
                if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
                var tile = arena.getTile(targetTile);
                if (tile == null || !tile.isWalkable()) return "§cCan't teleport there!";
            }
            case ENEMY -> {
                CombatEntity target = arena.getOccupant(targetTile);
                if (target == null || !target.isAlive() || target.isAlly()) {
                    return "§cNo enemy at target!";
                }
            }
            case SELF -> { }
        }
        return null;
    }
}
