package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.sherd.SherdModifiers;
import com.crackedgames.craftics.combat.sherd.SherdRegistry;
import com.crackedgames.craftics.combat.sherd.SherdSpell;
import com.crackedgames.craftics.combat.sherd.SpellContext;
import com.crackedgames.craftics.combat.sherd.SpellEngine;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pottery Sherd Spells: ancient magic sealed within pottery sherds.
 *
 * <p>This class used to <em>be</em> the sherd system - 1,573 lines holding twenty-three
 * hand-written {@code useXSherd} methods and four parallel {@code if (item == ...)} tables for
 * cost, range, self-cast and tooltip. Every sherd's targeting, damage, status effects, particle
 * choreography and chat line were tangled together in one method, so no two sherds could share
 * anything and no aspect of one could be changed without editing code.
 *
 * <p>It is now a facade. The spells themselves are data in
 * {@link com.crackedgames.craftics.combat.sherd.SherdRegistry}, executed by
 * {@link SpellEngine}. What survives here is the API the rest of the codebase already calls -
 * CombatManager, ItemUseHandler, RandomEvents and the client tooltip - plus the staged-visual
 * queue, which CombatManager ticks and which therefore has to stay the single shared one.
 *
 * <p><b>Stacks, not items.</b> The {@code Item} overloads are kept for callers that genuinely
 * only have an item, but they answer for an <em>uninscribed</em> sherd. Anything holding a real
 * stack should pass it: the Scribe writes inscriptions per stack, so two Burn sherds in one
 * inventory can legitimately have different costs, ranges and behaviour.
 */
public class PotterySherdSpells {

    // ─────────────────────────────────────────────────────────────────────
    // Staged visuals - owned here because CombatManager drains this queue
    // ─────────────────────────────────────────────────────────────────────

    /** Queued visual effects (particles/sounds) staged across ticks for dramatic flair. */
    public static final List<DelayedSpellEffect> PENDING_EFFECTS = new ArrayList<>();

    /** A particle/sound effect scheduled to fire after a tick delay. */
    public static class DelayedSpellEffect {
        public int ticksRemaining;
        public final Runnable effect;
        public DelayedSpellEffect(int ticks, Runnable effect) {
            this.ticksRemaining = ticks;
            this.effect = effect;
        }
    }

    /** Queue a visual effect to fire after the given number of server ticks (20 ticks = 1 second). */
    public static void queue(int delayTicks, Runnable effect) {
        PENDING_EFFECTS.add(new DelayedSpellEffect(delayTicks, effect));
    }

    /** Returns the max delay across all pending effects (for input-blocking duration). */
    public static int getMaxPendingDelay() {
        int max = 0;
        for (DelayedSpellEffect e : PENDING_EFFECTS) max = Math.max(max, e.ticksRemaining);
        return max;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Directive prefixes - the protocol for work only CombatManager can do
    // ─────────────────────────────────────────────────────────────────────

    /** Prefix for double damage next attack (Prize sherd). CombatManager parses this. */
    public static final String DOUBLE_NEXT_PREFIX = "§6DOUBLE_NEXT:";

    /**
     * Prefix for the Danger sherd's hex-trap tile effect.
     *
     * <p>Aliases {@link ItemUseHandler#TILE_EFFECT_PREFIX} rather than restating the literal:
     * a sherd placing a tile effect speaks the same protocol as any other tile-effect item,
     * and this was previously a second declaration of the identical string. Two constants with
     * one value invite exactly one bug - the two parsers drifted apart, one matching with
     * {@code startsWith} and the other with {@code contains} - so they are now provably the
     * same prefix.
     */
    public static final String HEX_TRAP_PREFIX = ItemUseHandler.TILE_EFFECT_PREFIX;

    /** Prefix for the Archer sherd's seeker volley (count:damage). CombatManager parses this. */
    public static final String SEEKERS_PREFIX = "§bSEEKERS:";

    // ─────────────────────────────────────────────────────────────────────
    // Membership and metadata
    // ─────────────────────────────────────────────────────────────────────

    /**
     * All pottery sherd items that function as spells.
     *
     * <p>Derived from the registry rather than restated. The old hand-written set and the
     * hand-written AP-cost chain had already drifted apart once; a sherd that exists now has a
     * cost by construction, because membership and definition are the same object.
     */
    public static final Set<Item> POTTERY_SHERDS = SherdRegistry.items();

    public static boolean isPotterySherd(Item item) {
        return SherdRegistry.isSherd(item);
    }

    /** AP cost for an uninscribed sherd of this item. Prefer {@link #getSherdApCost(ItemStack)}. */
    public static int getSherdApCost(Item item) {
        SherdSpell spell = SherdRegistry.get(item);
        return spell != null ? spell.apCost() : 3;
    }

    /** AP cost for this exact sherd, inscriptions included. */
    public static int getSherdApCost(ItemStack stack) {
        SherdSpell spell = SherdModifiers.resolve(stack);
        return spell != null ? spell.apCost() : 3;
    }

    /**
     * Maximum targeting range, or 0 for a self-cast spell that needs no target tile.
     * Prefer {@link #getSherdRange(ItemStack)}.
     */
    public static int getSherdRange(Item item) {
        SherdSpell spell = SherdRegistry.get(item);
        return spell != null ? spell.range() : 3;
    }

    /** Targeting range for this exact sherd, inscriptions included. */
    public static int getSherdRange(ItemStack stack) {
        SherdSpell spell = SherdModifiers.resolve(stack);
        return spell != null ? spell.range() : 3;
    }

    /** Returns true if this sherd spell targets self (no target tile required). */
    public static boolean isSelfCast(Item item) {
        SherdSpell spell = SherdRegistry.get(item);
        return spell != null && spell.isSelfCast();
    }

    public static boolean isSelfCast(ItemStack stack) {
        SherdSpell spell = SherdModifiers.resolve(stack);
        return spell != null && spell.isSelfCast();
    }

    /** Tooltip description for an uninscribed sherd, or null if the item is not a sherd spell. */
    public static String getSherdTooltip(Item item) {
        SherdSpell spell = SherdRegistry.get(item);
        return spell != null ? String.join("\n", spell.tooltipLines()) : null;
    }

    /** Tooltip for this exact sherd, with a line per inscription appended. */
    public static String getSherdTooltip(ItemStack stack) {
        SherdSpell spell = SherdModifiers.resolve(stack);
        return spell != null ? String.join("\n", spell.tooltipLines()) : null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Casting
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Use a pottery sherd spell. Returns a message string, or null if the sherd isn't a spell.
     * Handles consuming the sherd, dealing damage, applying effects, and spawning particles.
     */
    public static String useSherd(ServerPlayerEntity player, GridArena arena, GridPos targetTile,
                                   List<CombatEntity> enemies, CombatEffects combatEffects) {
        ItemStack held = player.getMainHandStack();
        SherdSpell spell = SherdModifiers.resolve(held);
        if (spell == null) return null;

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        SpellContext ctx = new SpellContext(player, arena, world, enemies, combatEffects,
            held, targetTile, spell);

        String result = SpellEngine.cast(spell, ctx);

        if (rollSherdBreak(player, spell)) {
            held.decrement(1);
            result += " §8(The sherd shattered.)";
        }
        return result;
    }

    /**
     * Roll whether the sherd shatters on this cast.
     *
     * <p>The base chance is the spell's own, so an Enduring inscription setting it to 0 makes
     * the sherd genuinely unbreakable rather than merely unlikely to break. Special affinity
     * and potency still reduce it, and a Robe-armored caster never shatters one at all.
     */
    private static boolean rollSherdBreak(ServerPlayerEntity player, SherdSpell spell) {
        if (spell.breakPercent() <= 0) return false;
        if (ArmorSetEffects.sherdsNeverBreak(PlayerCombatStats.getArmorSet(player))) return false;
        int reducedPercent = spell.breakPercent()
            - SpecialAffinity.points(player)
            - SpecialAffinity.potencyBonus(player);
        int breakChancePercent = Math.max(0, reducedPercent);
        return player.getRandom().nextInt(100) < breakChancePercent;
    }

    /**
     * Validate a sherd spell can be cast. Returns an error message, or null if valid.
     *
     * <p>The {@code Item} form answers for an uninscribed sherd; the range a Farsighted sherd
     * actually has comes from the stack, so the stack overload is the one CombatManager uses.
     */
    public static String validateSherd(Item item, GridArena arena, GridPos targetTile,
                                       List<CombatEntity> enemies) {
        SherdSpell spell = SherdRegistry.get(item);
        if (spell == null) return null;
        return SpellEngine.validate(spell, arena, targetTile);
    }

    public static String validateSherd(ItemStack stack, GridArena arena, GridPos targetTile,
                                       List<CombatEntity> enemies) {
        SherdSpell spell = SherdModifiers.resolve(stack);
        if (spell == null) return null;
        return SpellEngine.validate(spell, arena, targetTile);
    }
}
