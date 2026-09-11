package com.crackedgames.craftics.combat.sherd;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * The inscriptions written on one particular sherd, and how they reach the spell.
 *
 * <p>The Scribe modifies a <b>stack</b>, not an item. Two Burn sherds in the same inventory can
 * differ, which is what makes the villager interesting and what forces the resolved spell to be
 * per-stack: {@code SherdRegistry.get(item)} is only the starting point, and
 * {@link #resolve(ItemStack)} is what anything actually casting or pricing a sherd must ask.
 *
 * <p>Stored in the vanilla {@code CUSTOM_DATA} component as a single comma-separated string of
 * {@code NAME:magnitude} pairs - the same place and idiom as
 * {@link com.crackedgames.craftics.item.SeasonStamp}. Deliberately not a registered item per
 * combination: thirteen inscriptions with magnitudes would be a combinatorial explosion of item
 * ids, and an inscribed sherd should still be a pottery sherd to every other system that looks
 * at it.
 *
 * <p>Two inscribed sherds stop stacking with plain ones, which is correct - they are not the
 * same object any more - and the component is removed outright rather than emptied when the
 * last inscription comes off, so a stripped sherd stacks again.
 */
public final class SherdModifiers {

    private SherdModifiers() {}

    /** Key inside {@code CUSTOM_DATA}. */
    private static final String KEY = "craftics_inscriptions";

    /** How many inscriptions one sherd may carry. */
    public static final int MAX_INSCRIPTIONS = 3;

    /** One inscription at one strength. */
    public record Entry(SherdInscription inscription, int magnitude) {
        public String describe() { return inscription.describe(magnitude); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reading
    // ─────────────────────────────────────────────────────────────────────

    /** The inscriptions on this stack, in the order they were applied. Never null. */
    public static List<Entry> read(ItemStack stack) {
        List<Entry> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return out;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return out;
        NbtCompound nbt = data.copyNbt();
        String raw;
        //? if <=1.21.4 {
        raw = nbt.contains(KEY) ? nbt.getString(KEY) : "";
        //?} else {
        /*raw = nbt.getString(KEY, "");
        *///?}
        if (raw == null || raw.isEmpty()) return out;
        for (String token : raw.split(",")) {
            String[] parts = token.split(":", 2);
            SherdInscription inscription = SherdInscription.byName(parts[0].trim());
            if (inscription == null) continue;
            int magnitude = inscription.defaultMagnitude();
            if (parts.length > 1) {
                try {
                    magnitude = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException malformed) {
                    // Keep the inscription at its default rather than dropping it: a garbled
                    // number should cost the player a tuning value, not the whole effect.
                }
            }
            out.add(new Entry(inscription, magnitude));
        }
        return out;
    }

    public static boolean isInscribed(ItemStack stack) { return !read(stack).isEmpty(); }

    /**
     * The spell this particular stack casts: the item's base definition with every inscription
     * folded in, in order.
     *
     * <p>Returns null when the item is not a sherd spell at all. An uninscribed sherd short-
     * circuits to the shared base instance, so the common case allocates nothing.
     */
    public static SherdSpell resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        SherdSpell base = SherdRegistry.get(stack.getItem());
        if (base == null) return null;
        List<Entry> entries = read(stack);
        if (entries.isEmpty()) return base;

        SherdSpell.Builder builder = base.toBuilder();
        for (Entry entry : entries) {
            entry.inscription().applyTo(builder, entry.magnitude());
        }
        for (Entry entry : entries) {
            builder.tooltip(entry.describe());
        }
        return builder.build();
    }

    /**
     * The full tooltip for this sherd: its own lines, a line per inscription, and how likely it
     * is to shatter.
     *
     * <p>The break line is computed from the resolved spell rather than quoting a hard-coded
     * "10% base", which stopped being true the moment an Enduring inscription could set it to
     * zero. A sherd that cannot break says so.
     */
    public static String tooltipFor(ItemStack stack) {
        SherdSpell spell = resolve(stack);
        if (spell == null) return null;
        StringBuilder sb = new StringBuilder(String.join("\n", spell.tooltipLines()));
        // Targeting range, stated outright. The per-sherd tooltip text describes what the spell
        // does and occasionally mentions a radius, but none of them ever said how far away it
        // could be aimed - the number the range indicator draws on the floor.
        sb.append("\n").append(rangeLine(spell));
        if (spell.breakPercent() <= 0) {
            sb.append("\n\u00a7fThis sherd never shatters.");
        } else {
            sb.append("\n\u00a78Break chance: ").append(spell.breakPercent())
              .append("% base (reduced by Special affinity points + bonus)");
        }
        return sb.toString();
    }

    /**
     * The "how far, and at what" line.
     *
     * <p>Reads the resolved spell, so an inscribed sherd quotes its real reach, and names what
     * the tile must contain - aiming a hex trap at an enemy fails as surely as aiming a
     * corrosion at bare ground, and that was never written down anywhere.
     */
    private static String rangeLine(SherdSpell spell) {
        if (spell.isSelfCast()) {
            return "\u00a7bSelf-cast \u00a77- no target tile needed";
        }
        String what = switch (spell.targetMode()) {
            case ENEMY -> "an enemy";
            case EMPTY_TILE -> "an empty tile";
            case WALKABLE_TILE -> "a walkable tile";
            case SELF -> "yourself";
        };
        return "\u00a7bRange: \u00a7f" + spell.range() + " tile" + (spell.range() == 1 ? "" : "s")
            + " \u00a77- target " + what;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Writing
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Add an inscription to a stack.
     *
     * @return false when the sherd is full, already carries that inscription, or is not a sherd
     */
    public static boolean inscribe(ItemStack stack, SherdInscription inscription, int magnitude) {
        if (stack == null || stack.isEmpty() || inscription == null) return false;
        if (!SherdRegistry.isSherd(stack.getItem())) return false;
        List<Entry> entries = read(stack);
        if (entries.size() >= MAX_INSCRIPTIONS) return false;
        for (Entry existing : entries) {
            if (existing.inscription() == inscription) return false;
        }
        entries.add(new Entry(inscription, magnitude));
        write(stack, entries);
        return true;
    }

    /** Strip every inscription, restoring a plain, stackable sherd. */
    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return;
        NbtCompound nbt = data.copyNbt();
        if (!nbt.contains(KEY)) return;
        nbt.remove(KEY);
        // Removed outright, not left empty: a stack carrying an empty CUSTOM_DATA is not equal
        // to one carrying none, and would silently refuse to stack. Same reasoning as
        // SeasonStamp.unstamp.
        if (nbt.isEmpty()) {
            stack.remove(DataComponentTypes.CUSTOM_DATA);
        } else {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
    }

    private static void write(ItemStack stack, List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : entries) {
            if (sb.length() > 0) sb.append(',');
            sb.append(entry.inscription().name()).append(':').append(entry.magnitude());
        }
        final String encoded = sb.toString();
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putString(KEY, encoded));
    }
}
