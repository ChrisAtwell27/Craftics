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
 * {@code NAME#tier} pairs (sherds from before tiers hold {@code NAME:magnitude}, still read) -
 * the same place and idiom as
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

    /** One inscription at one tier. The magnitude is read off the inscription's tier table. */
    public record Entry(SherdInscription inscription, int tier) {
        public int magnitude() { return inscription.magnitudeAt(tier); }
        public String describe() { return inscription.describe(tier); }
        public boolean canUpgrade() { return tier < inscription.maxTier(); }
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
        return parse(raw);
    }

    /**
     * Parse a stored inscription string. Pure, so the format is testable without a stack.
     *
     * <p>{@code NAME#tier} is the current form. {@code NAME:magnitude} is what sherds inscribed
     * before tiers carry; it reads as whichever tier that magnitude reaches, so an old Kindled 2
     * becomes Kindled I and keeps working. A garbled number keeps the inscription at tier I
     * rather than dropping it - it should cost the player a tier, not the whole effect.
     */
    public static List<Entry> parse(String raw) {
        List<Entry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String token : raw.split(",")) {
            boolean tiered = token.contains("#");
            String[] parts = token.split(tiered ? "#" : ":", 2);
            SherdInscription inscription = SherdInscription.byName(parts[0].trim());
            if (inscription == null) continue;
            int tier = 1;
            if (parts.length > 1) {
                try {
                    int value = Integer.parseInt(parts[1].trim());
                    tier = tiered ? value : inscription.tierOf(value);
                } catch (NumberFormatException malformed) {
                    // tier I
                }
            }
            out.add(new Entry(inscription, Math.max(1, Math.min(inscription.maxTier(), tier))));
        }
        return out;
    }

    /** The stored form of a list of entries. Inverse of {@link #parse}. */
    public static String encode(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : entries) {
            if (sb.length() > 0) sb.append(',');
            sb.append(entry.inscription().name()).append('#').append(entry.tier());
        }
        return sb.toString();
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
     * Write an inscription onto a stack: a new line at tier I, or - when the sherd already
     * carries it - one tier up on the line it has, taking no new slot.
     *
     * @return the inscription's tier on the sherd afterwards, or 0 when nothing was written
     *         (not a sherd, full, a second legendary, or already at its highest tier)
     */
    public static int inscribe(ItemStack stack, SherdInscription inscription) {
        if (stack == null || stack.isEmpty() || inscription == null) return 0;
        if (!SherdRegistry.isSherd(stack.getItem())) return 0;
        List<Entry> entries = read(stack);
        int tier = applyInscription(entries, inscription);
        if (tier > 0) write(stack, entries);
        return tier;
    }

    /**
     * {@link #inscribe} on a plain list, so the upgrade-or-add rule is testable without a stack.
     * Upgrades in place, so an upgraded line keeps its position - order matters, since
     * inscriptions fold into the spell in the order they were written.
     *
     * @return the resulting tier, or 0 when {@link #canAccept} refuses
     */
    public static int applyInscription(List<Entry> entries, SherdInscription inscription) {
        if (!canAccept(entries, inscription)) return 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry existing = entries.get(i);
            if (existing.inscription() == inscription) {
                Entry upgraded = new Entry(inscription, existing.tier() + 1);
                entries.set(i, upgraded);
                return upgraded.tier();
            }
        }
        entries.add(new Entry(inscription, 1));
        return 1;
    }

    /** The tier this inscription has on a sherd carrying {@code entries}, or 0 if absent. */
    public static int tierOn(List<Entry> entries, SherdInscription inscription) {
        for (Entry e : entries) {
            if (e.inscription() == inscription) return e.tier();
        }
        return 0;
    }

    /**
     * Whether a sherd already carrying {@code entries} may take {@code inscription}.
     *
     * <p>One it already carries is an UPGRADE: allowed below the inscription's highest tier,
     * even on a full sherd, because it takes no slot. A new one needs a free slot, and a
     * legendary needs there to be no other legendary on the sherd. Enforced here, where every
     * write passes, so no offer or command path can get around it.
     */
    public static boolean canAccept(List<Entry> entries, SherdInscription inscription) {
        if (inscription == null) return false;
        for (Entry existing : entries) {
            if (existing.inscription() == inscription) return existing.canUpgrade();
        }
        if (entries.size() >= MAX_INSCRIPTIONS) return false;
        if (inscription.isLegendary()) {
            for (Entry existing : entries) {
                if (existing.inscription() != null && existing.inscription().isLegendary()) return false;
            }
        }
        return true;
    }

    /** Whether the Scribe could still write anything on a sherd carrying {@code entries}. */
    public static boolean hasRoomOrUpgrade(List<Entry> entries) {
        if (entries.size() < MAX_INSCRIPTIONS) return true;
        for (Entry e : entries) {
            if (e.canUpgrade()) return true;
        }
        return false;
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
        final String encoded = encode(entries);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putString(KEY, encoded));
    }
}
