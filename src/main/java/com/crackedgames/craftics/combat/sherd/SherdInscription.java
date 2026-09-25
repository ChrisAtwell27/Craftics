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
 * <p>Each has tiers, and a table of what it does at each one (the numbers after the description
 * in every constant below). Farsighted II is a bigger Farsighted I, not a different inscription:
 * the Scribe offering one a sherd already carries upgrades it a tier instead of spending a slot.
 * The enum name and the tier are the entire serialized form - see {@link SherdModifiers} -
 * which keeps an inscribed sherd a plain item with a short string on it rather than a new
 * registered item per combination.
 *
 * <p>Three shapes, and which one an inscription is decides where it can land:
 * <ul>
 *   <li><b>On-hit</b> ({@link SherdSpell.Builder#augmentEnemySteps}) - rides along on every step
 *       that strikes enemies. Never on a step that targets your own pets: Kindled on Guardian
 *       Spirit used to set the whole pack on fire.</li>
 *   <li><b>Added step</b> - a new step after the spell's own, for effects on the caster or the
 *       pack. Lands on any sherd.</li>
 *   <li><b>Stat</b> - edits a number on the spell itself: cost, range, shatter chance.</li>
 * </ul>
 * {@link #appliesTo} says whether an inscription would do anything on a given sherd, so the
 * Scribe never offers one that would be written and then silently ignored.
 */
public enum SherdInscription {

    // ── On-hit: ride along on every enemy-facing step ────────────────────

    /** Also sets targets alight. Magnitude is turns of burning. */
    KINDLED("Kindled", "§6", "Also burns for %d turns", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.burn(magnitude, 0, 60 * magnitude));
        }
    },

    /** Also shoves targets back. Magnitude is tiles. */
    FORCEFUL("Forceful", "§7", "Also knocks back %d tiles", 1, 2) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.knockback(magnitude, 0, 0, false));
        }
    },

    /** Rots what it touches. Magnitude is turns of wither. */
    WITHERING("Withering", "§5", "Also withers for %d turns", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.wither(magnitude, 1));
        }
    },

    /** Strips guard. Magnitude is defense removed, for 3 turns. */
    SUNDERING("Sundering", "§8", "Also strips %d DEF", 5, 8, 11) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.defensePenalty(3, magnitude));
        }
    },

    /** Opens wounds. Magnitude is Bleed stacks. */
    SERRATED("Serrated", "§c", "Also inflicts %d Bleed", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.bleed(magnitude));
        }
    },

    /** Poisons. Magnitude is turns. */
    VENOMOUS("Venomous", "§2", "Also poisons for %d turns", 3, 4, 5) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.poison(magnitude));
        }
    },

    /** Soaks, which doubles lightning and puts out fire. Magnitude is turns. */
    DRENCHING("Drenching", "§3", "Also Soaks for %d turns", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.soak(magnitude));
        }
    },

    /** Slows. Magnitude is turns. */
    CHILLING("Chilling", "§b", "Also slows for %d turns", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.slow(magnitude, 1));
        }
    },

    /** Blinds. Magnitude is turns. */
    BLINDING("Blinding", "§8", "Also blinds for %d turns", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.blind(magnitude));
        }
    },

    /**
     * Sometimes stuns. Magnitude is the percent chance, rolled per target.
     *
     * <p>A chance rather than a certainty: a guaranteed stun riding on an area spell would
     * lock down a whole wave every cast, which no sherd does on its own.
     */
    DAZING("Dazing", "§e", "%d%% chance to stun each target", 25, 35, 45) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.chance(magnitude / 100.0, Effects.stun()));
        }
    },

    /** Saps strength. Magnitude is attack removed. */
    ENFEEBLING("Enfeebling", "§7", "Also lowers ATK by %d", 3, 5, 7) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.attackPenalty(magnitude));
        }
    },

    /** Hits harder. Magnitude is extra damage per enemy struck. */
    EMPOWERED("Empowered", "§c", "+%d damage to every enemy hit", 4, 7, 10) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.damage(magnitude).plain().build());
        }
    },

    /** Returns some of the damage as health. Magnitude is unused. */
    LEECHING("Leeching", "§c", "Heals you for the damage dealt", 1) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self()).effect(Effects.lifesteal()));
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
    REAPING("Reaping", "§4", "Executes non-boss targets below %d%% HP", 25, 30, 35) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.enemy()
                    .onlyBelowHp(magnitude / 100.0).excludeBosses())
                .heading("§4§lReaped!")
                .effect(Effects.execute()));
        }
    },

    // ── Shape: how far and how wide the enemy steps reach ────────────────

    /** Reaches further. Magnitude is extra tiles of range. */
    FARSIGHTED("Farsighted", "§b", "+%d tiles of range", 1, 2, 3) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.adjustRange(magnitude);
        }
    },

    /** Every impact becomes an area. Magnitude is extra radius. */
    RESONANT("Resonant", "§d", "Impacts spread %d tiles further", 1, 2) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentRadius(magnitude);
        }
    },

    /** Bounces on. Magnitude is extra links; a spell with no chain gains one. */
    ARCING("Arcing", "§e", "+%d extra chains", 1, 2, 3) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentChain(magnitude, 2);
        }
    },

    // ── Added step: the caster and the pack ──────────────────────────────

    /** Mends the pack alongside the cast. Magnitude is HP restored to each pet. */
    NURTURING("Nurturing", "§a", "Also heals every pet %d HP", 8, 12, 16) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.pets())
                .heading("§aThe pack is mended!")
                .effect(Effects.healTarget(magnitude)));
        }
    },

    /** Rouses the pack. Magnitude is attack added to every pet, for 3 turns. */
    RALLYING("Rallying", "§a", "Every pet gains +%d ATK for 3 turns", 3, 5, 7) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.pets())
                .heading("§aThe pack is roused!")
                .effect(Effects.buffTargetAttack(magnitude, 3)));
        }
    },

    /** Mends the caster. Magnitude is HP. */
    HEARTENING("Heartening", "§a", "Also heals you %d HP", 6, 10, 14) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self()).effect(Effects.healCaster(magnitude)));
        }
    },

    /** Steadies the caster after the cast. Magnitude is turns of Resistance II. */
    WARDED("Warded", "§9", "Grants Resistance II for %d turns", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.RESISTANCE, magnitude, 1)));
        }
    },

    /** Strength after the cast. Magnitude is turns of Strength I. */
    EMBOLDENING("Emboldening", "§6", "Grants Strength for %d turns", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.STRENGTH, magnitude, 0)));
        }
    },

    /** Speed after the cast. Magnitude is turns of Speed I. */
    SWIFT("Swift", "§b", "Grants Speed for %d turns", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.SPEED, magnitude, 0)));
        }
    },

    /** A shield of extra health after the cast. Magnitude is turns of Absorption I. */
    BULWARK("Bulwark", "§e", "Grants Absorption for %d turns", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.ABSORPTION, magnitude, 0)));
        }
    },

    /** Luck after the cast. Magnitude is turns of Luck I. */
    FORTUNATE("Fortunate", "§6", "Grants Luck for %d turns", 3, 4, 5) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.LUCK, magnitude, 0)));
        }
    },

    // ── Stat: numbers on the spell itself ────────────────────────────────

    /** Never shatters. Magnitude is unused. */
    ENDURING("Enduring", "§f", "This sherd never shatters", 1) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.unbreakable();
        }
    },

    /** Harder to shatter. Magnitude is percentage points off the shatter chance. */
    TEMPERED("Tempered", "§7", "Shatter chance -%d%%", 4, 7) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.adjustBreakPercent(-magnitude);
        }
    },

    /** Costs less to cast. Magnitude is AP saved. */
    FLUENT("Fluent", "§3", "Costs %d less AP", 1, 2) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.adjustAp(-magnitude);
        }
    },

    // ── Legendary: rare offers, one per sherd ────────────────────────────
    //
    // Each is several ordinary inscriptions' worth at once, or something no ordinary one can
    // do. The Scribe only sometimes offers one (LEGENDARY_OFFER_CHANCE), and a sherd can carry
    // one at most - see SherdModifiers.canAccept - so they stay a find rather than a build.

    /**
     * The spell happens twice. Magnitude is unused.
     *
     * <p>Only offered for aimed sherds that strike enemies. Self-casts and placements carry
     * effects that must happen once per cast - summoning seekers, arming a double hit, setting
     * a trap - and repeating those would be a different bug per sherd.
     */
    ECHOING("Echoing", "§d", "The spell casts twice (+1 AP)", 1) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.repeatAllSteps();
            b.adjustAp(1);
        }
    },

    /** Storm: more chains, and everything struck is Soaked. Magnitude is extra chains. */
    TEMPEST("Tempest", "§e", "+%d chains, and Soaks everything it hits", 2, 3, 4) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentChain(magnitude, 2);
            b.augmentEnemySteps(Effects.soak(2));
        }
    },

    /** Impacts erupt wider and burn. Magnitude is turns of burning. */
    CATACLYSM("Cataclysm", "§6", "Impacts spread 1 tile further and burn for %d turns", 3, 4, 5) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentRadius(1);
            b.augmentEnemySteps(Effects.burn(magnitude, 1, 60 * magnitude));
        }
    },

    /** Bleeds what it hits and drinks the damage. Magnitude is Bleed stacks. */
    SANGUINE("Sanguine", "§4", "Inflicts %d Bleed and heals you for the damage dealt", 3, 4, 5) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.augmentEnemySteps(Effects.bleed(magnitude));
            b.step(SpellStep.of(Selector.self()).effect(Effects.lifesteal()));
        }
    },

    /** A far deeper execute than Reaping. Bosses still excluded. Magnitude is percent. */
    DOOM("Doom", "§4", "Executes non-boss targets below %d%% HP", 40, 45, 50) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.enemy()
                    .onlyBelowHp(magnitude / 100.0).excludeBosses())
                .heading("§4§l☠ Doomed!")
                .effect(Effects.execute()));
        }
    },

    /** Strength II, Resistance II and Speed after every cast. Magnitude is turns. */
    WARLORD("Warlord", "§6", "Grants Strength II, Resistance II and Speed for %d turns", 3, 4, 5) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.STRENGTH, magnitude, 1))
                .effect(Effects.casterEffect(EffectType.RESISTANCE, magnitude, 1))
                .effect(Effects.casterEffect(EffectType.SPEED, magnitude, 0)));
        }
    },

    /** The whole pack mended by half and made fiercer. Magnitude is attack added, 3 turns. */
    PACKMASTER("Packmaster", "§a", "Every pet heals 50%% of its max HP and gains +%d ATK", 5, 7, 9) {
        @Override public void applyTo(SherdSpell.Builder b, int magnitude) {
            b.step(SpellStep.of(Selector.pets())
                .heading("§a§lThe pack answers!")
                .effect(Effects.healTargetPercent(0.5))
                .effect(Effects.buffTargetAttack(magnitude, 3)));
        }
    };

    /**
     * Chance, per player per visit, that the Scribe's offer includes one legendary.
     * Rolled once when the event opens, like the rest of the offer.
     */
    public static final double LEGENDARY_OFFER_CHANCE = 0.05;

    /** Rare inscriptions: offered only sometimes, and at most one per sherd. */
    public boolean isLegendary() {
        return switch (this) {
            case ECHOING, TEMPEST, CATACLYSM, SANGUINE, DOOM, WARLORD, PACKMASTER -> true;
            default -> false;
        };
    }

    /** The name as shown to players, with a gold star on a legendary. */
    public String label() {
        return (isLegendary() ? "§6★ " : "") + color + displayName;
    }

    private final String displayName;
    private final String color;
    private final String descriptionFormat;
    /**
     * Magnitude at each tier, tier I first. The length is the highest tier the inscription can
     * reach, so a one-entry table (Leeching, Enduring, Echoing - on/off effects with nothing to
     * scale) can never be upgraded. Retuning an inscription is editing this table; sherds store
     * their TIER, not the number, so every sherd already in the world picks the change up.
     */
    private final int[] tierMagnitudes;

    SherdInscription(String displayName, String color, String descriptionFormat, int... tierMagnitudes) {
        this.displayName = displayName;
        this.color = color;
        this.descriptionFormat = descriptionFormat;
        this.tierMagnitudes = tierMagnitudes.length > 0 ? tierMagnitudes : new int[]{1};
    }

    /** Fold this inscription into a spell under construction. */
    public abstract void applyTo(SherdSpell.Builder builder, int magnitude);

    public String displayName() { return displayName; }
    public String color() { return color; }

    /** The highest tier this inscription reaches. 1 means it cannot be upgraded. */
    public int maxTier() { return tierMagnitudes.length; }

    /** The magnitude at {@code tier}, clamped into {@code 1..maxTier()}. */
    public int magnitudeAt(int tier) {
        return tierMagnitudes[Math.max(1, Math.min(maxTier(), tier)) - 1];
    }

    /**
     * The tier a stored magnitude corresponds to: the highest tier whose magnitude it reaches.
     * Used only to read sherds inscribed before tiers existed, which stored the number itself.
     */
    public int tierOf(int magnitude) {
        int tier = 1;
        for (int t = 1; t <= maxTier(); t++) {
            if (magnitude >= magnitudeAt(t)) tier = t;
        }
        return tier;
    }

    /** The name at a tier, e.g. {@code "§6Kindled II"}. No numeral on a single-tier inscription. */
    public String labelAt(int tier) {
        return maxTier() > 1 ? label() + " " + roman(Math.max(1, Math.min(maxTier(), tier))) : label();
    }

    /** Tooltip line at a tier, e.g. {@code "§6Kindled II §7- Also burns for 3 turns"}. */
    public String describe(int tier) {
        return labelAt(tier) + " §7- " + String.format(descriptionFormat, magnitudeAt(tier));
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    /**
     * Whether writing this onto {@code spell} would change anything.
     *
     * <p>The Scribe filters its offer through this. An on-hit inscription on a pure buff has no
     * enemy step to ride, range means nothing to a self-cast, a 1 AP sherd cannot get cheaper,
     * and an unbreakable one cannot get tougher - each of those would spend the visit's one
     * inscription on a line that does nothing.
     */
    public boolean appliesTo(SherdSpell spell) {
        if (spell == null) return false;
        return switch (this) {
            case KINDLED, FORCEFUL, WITHERING, SUNDERING, SERRATED, VENOMOUS, DRENCHING,
                 CHILLING, BLINDING, DAZING, ENFEEBLING, EMPOWERED, LEECHING,
                 RESONANT, ARCING -> spell.hitsEnemies();
            // Reaping and Farsighted both work off the aimed tile, which a self-cast has none of.
            case REAPING, DOOM, ECHOING -> spell.hitsEnemies() && !spell.isSelfCast();
            case TEMPEST, CATACLYSM, SANGUINE -> spell.hitsEnemies();
            case WARLORD, PACKMASTER -> true;
            case FARSIGHTED -> !spell.isSelfCast();
            case ENDURING, TEMPERED -> spell.breakPercent() > 0;
            case FLUENT -> spell.apCost() > 1;
            case NURTURING, RALLYING, HEARTENING, WARDED, EMBOLDENING, SWIFT, BULWARK,
                 FORTUNATE -> true;
        };
    }

    /** Strength at tier I - what a fresh inscription writes. */
    public int defaultMagnitude() {
        return magnitudeAt(1);
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
