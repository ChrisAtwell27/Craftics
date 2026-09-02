package com.crackedgames.craftics.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * Every way an incoming attack can be shrugged off entirely, added into one number and rolled once.
 *
 * <p>These used to be four separate rolls in a row - Armor Class, then Ethereal, then a shield
 * block, then Gilded Guard - each one {@code if (roll) return 0;}. That is
 * {@code 1 - PRODUCT(1 - p)}, not a sum, and it behaved nothing like the tooltips. A tank stacking
 * all four read 120% and survived 79.6% of hits. Worse, each source was worth less the more of
 * them you had: Gilded Guard's advertised 15% was worth +3.6% on a build that already had the
 * other three, because it only ever rolled on the hits everything else had already let through.
 *
 * <p>Now every layer is an addend and there is one roll. A tank can read their gear and know what
 * it does.
 *
 * <p>Free of Minecraft so the arithmetic and the attribution can be checked directly.
 */
public final class Avoidance {

    private Avoidance() {}

    /**
     * The most any stack of layers can avoid.
     *
     * <p>A tank build is meant to reach a wall, so this is high - but never certainty. An enemy
     * that can NEVER land a hit stops being a fight, and every source of pressure the mod has
     * (Sudden Death, DoTs, terrain) is designed around chip damage getting through eventually.
     */
    public static final double CAP = 0.90;

    /** Where one layer of avoidance came from. Attribution decides the message and side effects. */
    public enum Source {
        /** Armor Class, the only contested layer - its chance depends on the attacker. */
        ARMOR_CLASS,
        /** Ethereal trim set bonus. */
        ETHEREAL,
        /** Shield passive block. Consumes shield durability when it is the one that fires. */
        SHIELD,
        /** Gilded Guard hybrid set. */
        GILDED_GUARD
    }

    /** One contribution to the total. */
    public record Layer(Source source, double chance) {}

    /**
     * What a single incoming attack resolved to.
     *
     * @param avoided whether the hit was shrugged off
     * @param by      which layer gets the credit, or null when the hit landed
     * @param chance  the total avoidance chance used, after the cap
     */
    public record Result(boolean avoided, Source by, double chance) {}

    /** Convenience for building a layer list, skipping anything that contributes nothing. */
    public static List<Layer> layers(Layer... entries) {
        List<Layer> out = new ArrayList<>();
        for (Layer l : entries) {
            if (l != null && l.chance() > 0) out.add(l);
        }
        return out;
    }

    /**
     * Snap a running total back onto a clean value.
     *
     * <p>Every layer here is a percentage, but adding them in binary floating point is not exact:
     * 0.40 + 0.20 comes to 0.6000000000000001, so a "60%" stack would avoid a hit rolled at
     * exactly 0.60 and the boundary would sit a hair off wherever the tooltips said. Rounding to
     * a millionth keeps whole and fractional percents exact without constraining what a future
     * source is allowed to be worth.
     */
    private static double clean(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    /** The sum before the cap, which is what attribution divides up. */
    public static double rawTotal(List<Layer> layers) {
        double sum = 0.0;
        if (layers == null) return 0.0;
        for (Layer l : layers) sum += Math.max(0.0, l.chance());
        return clean(sum);
    }

    /** The chance actually used, capped at {@link #CAP}. */
    public static double total(List<Layer> layers) {
        return Math.min(CAP, rawTotal(layers));
    }

    /**
     * Which layer gets the credit for an avoided hit.
     *
     * <p>Weighted by each layer's share of the raw total, so a shield contributing a quarter of
     * the stack takes the credit - and the durability hit - on roughly a quarter of avoided
     * attacks. Attribution matters because the layers are not interchangeable: only a shield
     * spends durability, only Armor Class and Ethereal count as a dodge for the addon hook and
     * the Sentinel riposte.
     *
     * <p>Shares come from the RAW total rather than the capped one, so a build over the cap still
     * credits its layers in proportion to what they contribute.
     *
     * @param pick a value in [0, 1)
     * @return the credited layer, or null when there is nothing to credit
     */
    public static Source credit(List<Layer> layers, double pick) {
        double sum = rawTotal(layers);
        if (sum <= 0) return null;
        double target = clean(Math.max(0.0, Math.min(1.0, pick)) * sum);
        double cursor = 0.0;
        for (Layer l : layers) {
            cursor = clean(cursor + Math.max(0.0, l.chance()));
            if (target < cursor) return l.source();
        }
        // Only reachable on floating-point drift at the very top of the range.
        return layers.get(layers.size() - 1).source();
    }

    /**
     * Resolve one incoming attack.
     *
     * @param hitRoll    a value in [0, 1) deciding whether the attack is avoided
     * @param creditRoll a value in [0, 1) deciding which layer takes the credit
     */
    public static Result resolve(List<Layer> layers, double hitRoll, double creditRoll) {
        double chance = total(layers);
        if (chance <= 0 || hitRoll >= chance) return new Result(false, null, chance);
        return new Result(true, credit(layers, creditRoll), chance);
    }

    /** The total as a whole percent, for anything that shows it to a player. */
    public static int percent(List<Layer> layers) {
        return (int) Math.round(total(layers) * 100.0);
    }
}
