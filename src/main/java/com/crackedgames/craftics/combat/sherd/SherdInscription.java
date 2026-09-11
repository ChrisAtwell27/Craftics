package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;

import java.util.Locale;

/**
 * One thing the Scribe can add to a sherd.
 *
 * <p>An inscription is a named transformation of a {@link SherdSpell.Builder}, so it is written
 * once against the vocabulary rather than against any particular sherd. "Kindled" means "also
 * burn what you hit", and that sentence is equally true of chain lightning, a phantom slash and
 * a tidal surge - which is exactly why the spell had to become data before the Scribe could
 * exist at all. Against the old code, every inscription would have needed a hand-written
 * variant of all twenty-three methods.
 *
 * <p>Each carries a magnitude so one entry covers a range of strengths: {@code RANGE:2} is a
 * bigger version of {@code RANGE:1}, not a different inscription. The enum name and that number
 * are the entire serialized form - see {@link SherdModifiers} - which keeps an inscribed sherd
 * a plain item with a short string on it rather than a new registered item per combination.
 */
public enum SherdInscription {

    /** Also sets targets alight. Magnitude is turns of burning. */
    KINDLED("Kindled", "§6", "Also burns for %d turns") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentAllSteps(Effects.burn(magnitude, 0, 60 * magnitude));
        }
    },

    /** Also shoves targets back. Magnitude is tiles. */
    FORCEFUL("Forceful", "§7", "Also knocks back %d tiles") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentAllSteps(Effects.knockback(magnitude, 0, 0, false));
        }
    },

    /** Reaches further. Magnitude is extra tiles of range. */
    FARSIGHTED("Farsighted", "§b", "+%d tiles of range") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.adjustRange(magnitude);
        }
    },

    /** Every impact becomes an area. Magnitude is extra radius. */
    RESONANT("Resonant", "§d", "Impacts spread %d tiles further") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentRadius(magnitude);
        }
    },

    /** Bounces on. Magnitude is extra links; a spell with no chain gains one. */
    ARCING("Arcing", "§e", "+%d extra chains") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentChain(magnitude, 2);
        }
    },

    /** Rots what it touches. Magnitude is turns of wither. */
    WITHERING("Withering", "§5", "Also withers for %d turns") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentAllSteps(Effects.wither(magnitude, 1));
        }
    },

    /** Strips guard. Magnitude is defense removed, for 3 turns. */
    SUNDERING("Sundering", "§8", "Also strips %d DEF") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentAllSteps(Effects.defensePenalty(3, magnitude));
        }
    },

    /** Returns some of the damage as health. Magnitude is unused. */
    LEECHING("Leeching", "§c", "Heals you for the damage dealt") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self()).effect(Effects.lifesteal()));
        }
    },

    /** Mends the pack alongside the cast. Magnitude is HP restored to each pet. */
    NURTURING("Nurturing", "§a", "Also heals every pet %d HP") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.pets())
                .heading("§aThe pack is mended!")
                .effect(Effects.healTarget(magnitude)));
        }
    },

    /**
     * Finishes the wounded. Magnitude is the threshold in percent.
     *
     * <p>Bosses are excluded, unlike Death Mark's own execute. An inscription can be put on any
     * sherd and stacked with anything else, so an executing sherd is far easier to arrive at by
     * accident than the one spell deliberately built around it - and a boss that dies at 30%
     * to a modified Corrode is a broken fight rather than a clever build.
     */
    REAPING("Reaping", "§4", "Executes non-boss targets below %d%% HP") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.enemy()
                    .onlyBelowHp(magnitude / 100.0).excludeBosses())
                .heading("§4§lReaped!")
                .effect(Effects.execute()));
        }
    },

    /** Steadies the caster after the cast. Magnitude is turns of Resistance II. */
    WARDED("Warded", "§9", "Grants Resistance II for %d turns") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.RESISTANCE, magnitude, 1)));
        }
    },

    /** Never shatters. Magnitude is unused. */
    ENDURING("Enduring", "§f", "This sherd never shatters") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.unbreakable();
        }
    },

    /** Costs less to cast. Magnitude is AP saved. */
    FLUENT("Fluent", "§3", "Costs %d less AP") {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.adjustAp(-magnitude);
        }
    };

    private final String displayName;
    private final String color;
    private final String descriptionFormat;

    SherdInscription(String displayName, String color, String descriptionFormat) {
        this.displayName = displayName;
        this.color = color;
        this.descriptionFormat = descriptionFormat;
    }

    /** Fold this inscription into a spell under construction. */
    public abstract void applyTo(SherdSpell.Builder builder, int magnitude);

    public String displayName() { return displayName; }
    public String color() { return color; }

    /** Tooltip line for an inscribed sherd, e.g. {@code "§6Kindled §7- Also burns for 2 turns"}. */
    public String describe(int magnitude) {
        return color + displayName + " §7- " + String.format(descriptionFormat, magnitude);
    }

    /** Default strength when the Scribe rolls this inscription. */
    public int defaultMagnitude() {
        return switch (this) {
            case KINDLED, WITHERING, WARDED -> 2;
            case FORCEFUL, FARSIGHTED, RESONANT, ARCING, FLUENT -> 1;
            case SUNDERING -> 5;
            case NURTURING -> 8;
            case REAPING -> 25;
            case LEECHING, ENDURING -> 1;
        };
    }

    /** Parse a stored name, case-insensitively. Null when it names nothing. */
    public static SherdInscription byName(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            // An inscription removed in a later version: the sherd stays castable, it just
            // loses that one line. Better than refusing to resolve the spell at all.
            return null;
        }
    }
}
