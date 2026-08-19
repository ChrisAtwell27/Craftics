package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Combat stats for modded weapons and armor nobody registered, inferred from the item itself.
 *
 * <h2>The problem</h2>
 *
 * <p>An unregistered weapon used to deal bare-fist damage, and unregistered armor used to have
 * an Armor Class of zero. Both are silent: a mod's entire arsenal looks present, equips
 * normally, shows a tooltip, and does nothing. A player installing a weapon pack found every
 * blade in it identical and useless, with nothing to tell them why.
 *
 * <p>{@link FoodValues} already solved the same problem for food by reading the item's own
 * nutrition instead of keeping a table of every edible in existence. This is that idea applied
 * to gear, and the reasoning is the same: a mod that bothered to give its steel longsword more
 * attack damage than its iron one has already said what it is worth, and Craftics can listen
 * instead of asking to be told twice.
 *
 * <h2>How the numbers are picked</h2>
 *
 * <p><b>Not by formula.</b> Craftics' own weapon values are hand-tuned and do not track vanilla
 * damage: a netherite axe hits for 27 where vanilla gives it 10, and gold is deliberately far
 * off the ladder its stats imply. Any formula fitted to that would be fitting noise.
 *
 * <p>Instead, a modded item is placed on Craftics' <b>existing ladder</b> by interpolation. The
 * ladder is built at runtime by reading the real vanilla items and pairing each with the value
 * Craftics authored for it, so a modded sword sitting between iron and diamond in vanilla terms
 * gets a Craftics number between iron and diamond too. Two consequences worth having:
 *
 * <ul>
 *   <li>A vanilla item fed through inference comes out at exactly its authored value, so the
 *       inference cannot silently disagree with the game it is extending.</li>
 *   <li>Nothing about vanilla is hardcoded. The ladder is whatever the running version and the
 *       current config say it is, so it cannot drift as Mojang retunes a material or a server
 *       owner retunes Craftics.</li>
 * </ul>
 *
 * <p>Shape - blade, axe, polearm, blunt, ranged - decides damage type, AP cost and reach, and is
 * read from the item's name. That is cruder than the damage numbers and deliberately so: there
 * is no attribute that says "this is a halberd", and mods name their content descriptively.
 *
 * <h2>What this never does</h2>
 *
 * <p>Inference runs <b>only</b> for items nothing has registered. An explicit registration, from
 * code or from a datapack, always wins - so a compat module or a pack author correcting a guess
 * is never fighting this class. It is the floor, not the authority.
 */
public final class GearInference {

    private GearInference() {}

    // ─────────────────────────────────────────────────────────────────────
    // Shape
    // ─────────────────────────────────────────────────────────────────────

    /**
     * What kind of weapon an item reads as, which decides everything except raw damage.
     *
     * <p>The profiles are Craftics' own, lifted from the shapes it already supports through the
     * Simply Swords module rather than invented here. A modded chakram should behave like the
     * chakram Craftics already knows how to hold, and a modded halberd should reach a tile the
     * way Craftics' halberds do - otherwise the same weapon means two different things
     * depending on which mod shipped it.
     */
    public enum Shape {
        /** Daggers, knives, sai. Fast, short, no frills. */
        DAGGER(DamageType.SLASHING, 1, 1, false),
        /** Swords, katanas, rapiers, cutlasses. The default melee weapon. */
        LIGHT_BLADE(DamageType.SLASHING, 1, 1, false),
        /** Paired warglaives: cleaving damage at arm's length for one AP. */
        WARGLAIVE(DamageType.CLEAVING, 1, 1, false),
        /** Greatswords and claymores. Three AP, and they hit everything nearby. */
        GREATBLADE(DamageType.CLEAVING, 3, 1, false),
        /** Axes, battleaxes, cleavers. */
        AXE(DamageType.CLEAVING, 2, 1, false),
        /** Greataxes. Heavier than an axe and bites through armor. */
        GREATAXE(DamageType.CLEAVING, 3, 1, false),
        /** Spears, pikes, lances. Thrusting reach: two AP, one tile out. */
        SPEAR(DamageType.SLASHING, 2, 2, false),
        /** Halberds, glaives, naginata. Hafted and cleaving, one tile out. */
        POLEARM(DamageType.CLEAVING, 2, 2, false),
        /** Scythes. A polearm that sweeps rather than chops. */
        SCYTHE(DamageType.CLEAVING, 2, 2, false),
        /** Maces, clubs, flails. */
        HAMMER(DamageType.BLUNT, 2, 1, false),
        /** Warhammers and mauls. */
        GREATHAMMER(DamageType.BLUNT, 3, 1, false),
        /** Chakrams and boomerangs: thrown, and back in your hand for the next turn. */
        THROWN(DamageType.RANGED, 1, 3, true),
        /** Bows, crossbows, anything that fires ammunition. */
        BOW(DamageType.RANGED, 1, 4, true);

        public final DamageType damageType;
        public final int apCost;
        public final int range;
        public final boolean ranged;

        Shape(DamageType damageType, int apCost, int range, boolean ranged) {
            this.damageType = damageType;
            this.apCost = apCost;
            this.range = range;
            this.ranged = ranged;
        }

        /** True when damage comes from ammunition rather than the item's own attack rating. */
        public boolean firesAmmunition() {
            return this == BOW;
        }
    }

    /**
     * Read a weapon's shape from its registry path.
     *
     * <p>Order matters, and most of this list is about that. "greataxe" holds "axe",
     * "warhammer" holds "hammer", "warglaive" holds "glaive", and "greatsword" holds "sword" -
     * so every compound name is tested before the plain one it contains. Tools are excluded
     * first: a modded pickaxe holds "axe" and should not become a battleaxe.
     *
     * <p>Registry-free and case-insensitive so it can be unit-tested against a list of names
     * with no Minecraft bootstrap.
     *
     * @param path an item's registry path, e.g. {@code steel_longsword}
     * @return the shape, or null when the name does not read as a weapon
     */
    @Nullable
    public static Shape shapeOf(String path) {
        if (path == null || path.isBlank()) return null;
        String p = path.toLowerCase(java.util.Locale.ROOT);

        if (containsAny(p, "pickaxe", "shovel", "spade", "hoe", "shears", "fishing_rod")) {
            return null;
        }

        // Thrown before ranged: a chakram is not a bow, and it keeps its own attack rating.
        if (containsAny(p, "chakram", "boomerang", "discus", "throwing_star", "shuriken")) {
            return Shape.THROWN;
        }
        if (containsAny(p, "crossbow", "longbow", "shortbow", "bow", "sling", "blowgun",
                "musket", "rifle", "pistol", "flintlock", "handcannon")) {
            return Shape.BOW;
        }

        // Compound names before the simple ones they contain.
        if (containsAny(p, "warglaive", "war_glaive")) return Shape.WARGLAIVE;
        if (containsAny(p, "greataxe", "great_axe", "greatax")) return Shape.GREATAXE;
        if (containsAny(p, "warhammer", "war_hammer", "greathammer", "great_hammer",
                "maul", "sledge")) {
            return Shape.GREATHAMMER;
        }
        if (containsAny(p, "greatsword", "great_sword", "claymore", "zweihander",
                "executioner", "flamberge")) {
            return Shape.GREATBLADE;
        }
        if (containsAny(p, "scythe")) return Shape.SCYTHE;
        if (containsAny(p, "halberd", "glaive", "naginata", "bardiche", "poleaxe",
                "pole_axe", "polearm", "guisarme")) {
            return Shape.POLEARM;
        }
        if (containsAny(p, "spear", "pike", "lance", "javelin", "partisan", "trident",
                "harpoon")) {
            return Shape.SPEAR;
        }
        if (containsAny(p, "hammer", "mace", "club", "flail", "cudgel", "bludgeon",
                "quarterstaff", "morningstar", "morning_star")) {
            return Shape.HAMMER;
        }
        if (containsAny(p, "dagger", "knife", "sai", "tanto", "kris", "dirk", "stiletto",
                "shiv")) {
            return Shape.DAGGER;
        }
        if (containsAny(p, "axe", "cleaver", "chopper", "hatchet")) return Shape.AXE;
        if (containsAny(p, "sword", "rapier", "katana", "saber", "sabre", "cutlass",
                "scimitar", "estoc", "machete", "twinblade", "falchion", "blade")) {
            return Shape.LIGHT_BLADE;
        }
        return null;
    }

    /**
     * The signature trick a shape gets, matching what Craftics already gives that shape.
     *
     * <p>Deliberately modest. An inferred weapon should feel like its family rather than like a
     * hand-authored one - a mod that wants its greatsword to do something particular registers
     * it and gets exactly what it asked for.
     */
    @Nullable
    public static com.crackedgames.craftics.api.WeaponAbilityHandler abilityFor(Shape shape) {
        if (shape == null) return null;
        return switch (shape) {
            case LIGHT_BLADE, AXE -> com.crackedgames.craftics.api.Abilities.sweepAdjacent(0.10, 0.05);
            case GREATBLADE, POLEARM, SCYTHE -> com.crackedgames.craftics.api.Abilities.sweepAdjacent(0.15, 0.03);
            case GREATAXE -> com.crackedgames.craftics.api.Abilities.armorIgnore(0.15, 0.03);
            case HAMMER, GREATHAMMER -> com.crackedgames.craftics.api.Abilities.stun(0.10, 0.03);
            // Daggers, warglaives, spears, and anything thrown or fired stay plain: their
            // profile already is the advantage.
            default -> null;
        };
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────
    // The ladder
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Place {@code x} on a ladder of reference points and read off the matching value.
     *
     * <p>Piecewise linear between the points and clamped at both ends, so a modded weapon
     * stronger than netherite gets netherite's number rather than an extrapolated one. A pack
     * that hands out a sword with 400 attack damage should not get 400 damage in Craftics -
     * the ladder is the balance statement, and the top of it is the top.
     *
     * <p>{@code xs} must be sorted ascending and the same length as {@code ys}.
     *
     * @return the interpolated value, rounded to the nearest integer
     */
    public static int onLadder(double[] xs, int[] ys, double x) {
        if (xs == null || ys == null || xs.length == 0 || xs.length != ys.length) return 0;
        if (x <= xs[0]) return ys[0];
        int last = xs.length - 1;
        if (x >= xs[last]) return ys[last];
        for (int i = 0; i < last; i++) {
            if (x <= xs[i + 1]) {
                double span = xs[i + 1] - xs[i];
                if (span <= 0) return ys[i];
                double t = (x - xs[i]) / span;
                return (int) Math.round(ys[i] + t * (ys[i + 1] - ys[i]));
            }
        }
        return ys[last];
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reading the item
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Total value an item's own attribute modifiers add to one attribute.
     *
     * <p>Matched on the attribute's registry <b>id</b> rather than a constant, because Mojang
     * renamed every one of them from {@code generic.x} to {@code x} in 1.21.2 and this mod
     * builds for versions either side of that. Accepting both spellings costs one string
     * comparison and removes a per-shard branch that would have to be right four times.
     *
     * <p>Only additive modifiers count. A multiplier on a bare item is a mod doing something
     * unusual, and guessing at what it meant is worse than ignoring it.
     */
    public static double additiveModifier(Item item, String attributeName) {
        if (item == null) return 0;
        try {
            var component = item.getComponents().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (component == null) return 0;
            double total = 0;
            for (var entry : component.modifiers()) {
                Identifier id = Registries.ATTRIBUTE.getId(entry.attribute().value());
                if (id == null) continue;
                String path = id.getPath();
                if (!path.equals(attributeName) && !path.equals("generic." + attributeName)) {
                    continue;
                }
                if (entry.modifier().operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                    total += entry.modifier().value();
                }
            }
            return total;
        } catch (Throwable t) {
            // A mod with an unusual component setup should cost itself an inference, not the
            // ability to hold the item.
            return 0;
        }
    }

    /** An item's attack damage the way the vanilla tooltip counts it: the player's base 1 plus modifiers. */
    public static double attackDamageOf(Item item) {
        return 1.0 + additiveModifier(item, "attack_damage");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Weapons
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Infer combat stats for a weapon nobody registered, or null if the item does not read as
     * a weapon at all.
     *
     * <p>Null is the important half. A modded chair should not become a club because it happens
     * to carry an attack damage modifier, and returning something for everything would replace
     * a visible gap with an invisible wrong answer.
     */
    @Nullable
    public static WeaponEntry inferWeapon(Item item) {
        if (item == null) return null;
        Identifier id = Registries.ITEM.getId(item);
        if (id == null) return null;

        Shape shape = shapeOf(id.getPath());
        if (shape == null) return null;

        int power;
        if (shape.firesAmmunition()) {
            // A bow's damage lives in its ammunition, not its attribute modifiers, so there is
            // nothing on the item to place on a ladder. Craftics' own bow value is the honest
            // answer for "a bow we know nothing else about".
            power = id.getPath().contains("crossbow")
                ? CrafticsMod.CONFIG.dmgCrossbow()
                : CrafticsMod.CONFIG.dmgBow();
        } else {
            double attackDamage = attackDamageOf(item);
            if (attackDamage <= 1.0) return null;   // no attack modifier at all: not a weapon
            power = onLadder(ladderX(shape), ladderY(shape), attackDamage);
        }
        if (power <= 0) return null;

        final int finalPower = power;
        var builder = WeaponEntry.builder(item)
            .damageType(shape.damageType)
            .attackPower(() -> finalPower)
            .apCost(shape.apCost)
            .range(shape.range)
            .ranged(shape.ranged);
        var ability = abilityFor(shape);
        if (ability != null) builder.ability(ability);
        return builder.build();
    }

    /**
     * Vanilla attack damage of the reference items for this shape, ascending.
     *
     * <p>Read live rather than hardcoded, so the ladder is always the running version's.
     * Gold is deliberately absent from every ladder: Craftics prices it far above what its
     * vanilla stats imply, on purpose, and including it would put a kink in the curve that
     * dragged every modded weapon near that damage upward with it.
     */
    /**
     * Whether a shape is priced against axes rather than swords.
     *
     * <p>Craftics' axe ladder runs far steeper than its sword ladder - 8 to 27 against 5 to 15
     * - and that gap is the whole difference between a heavy weapon and a fast one. Anything
     * that swings like an axe has to be priced like one, or a modded warhammer ends up cheaper
     * per swing than a dagger.
     */
    private static boolean usesAxeLadder(Shape shape) {
        return switch (shape) {
            case AXE, GREATAXE, GREATBLADE, HAMMER, GREATHAMMER, POLEARM, SCYTHE, WARGLAIVE -> true;
            default -> false;
        };
    }

    private static double[] ladderX(Shape shape) {
        if (usesAxeLadder(shape)) {
            return new double[]{
                attackDamageOf(net.minecraft.item.Items.WOODEN_AXE),
                attackDamageOf(net.minecraft.item.Items.STONE_AXE),
                attackDamageOf(net.minecraft.item.Items.IRON_AXE),
                attackDamageOf(net.minecraft.item.Items.DIAMOND_AXE),
                attackDamageOf(net.minecraft.item.Items.NETHERITE_AXE)};
        }
        return new double[]{
            attackDamageOf(net.minecraft.item.Items.WOODEN_SWORD),
            attackDamageOf(net.minecraft.item.Items.STONE_SWORD),
            attackDamageOf(net.minecraft.item.Items.IRON_SWORD),
            attackDamageOf(net.minecraft.item.Items.DIAMOND_SWORD),
            attackDamageOf(net.minecraft.item.Items.NETHERITE_SWORD)};
    }

    /** What Craftics authored for those same reference items. */
    private static int[] ladderY(Shape shape) {
        var c = CrafticsMod.CONFIG;
        if (usesAxeLadder(shape)) {
            return new int[]{c.dmgWoodenAxe(), c.dmgStoneAxe(), c.dmgIronAxe(),
                c.dmgDiamondAxe(), c.dmgNetheriteAxe()};
        }
        return new int[]{c.dmgWoodenSword(), c.dmgStoneSword(), c.dmgIronSword(),
            c.dmgDiamondSword(), c.dmgNetheriteSword()};
    }

    // ─────────────────────────────────────────────────────────────────────
    // Armor
    // ─────────────────────────────────────────────────────────────────────

    /**
     * How defensive a piece is in vanilla terms, as one number.
     *
     * <p>Toughness is weighted because it has to be: diamond and netherite carry identical
     * armor points and differ only in toughness, yet Craftics prices netherite four Armor Class
     * above diamond. Armor points alone cannot tell them apart, so a ladder built on points
     * alone would hand every modded end-tier armor diamond's number.
     */
    public static double defensiveScore(double armorPoints, double toughness) {
        return armorPoints + 2.0 * toughness;
    }

    /**
     * Infer the base Armor Class of an armor piece nobody registered, or 0 when the item does
     * not read as armor.
     *
     * <p>Placed on the ladder for its own slot. Slots are not interchangeable - a vanilla
     * chestplate carries three times a helmet's points - so one shared ladder would have every
     * inferred helmet come out as leather and every chestplate as netherite.
     */
    public static int inferArmorBaseAC(Item item) {
        if (item == null) return 0;
        ArmorClassTable.Slot slot = ArmorClassTable.slotOf(item);
        if (slot == null) return 0;

        double score = defensiveScore(
            additiveModifier(item, "armor"), additiveModifier(item, "armor_toughness"));
        if (score <= 0) return 0;

        return onLadder(armorLadderX(slot), ARMOR_LADDER_Y, score);
    }

    /**
     * The affinity a material's armor boosts, inferred from its name.
     *
     * <p>Every armor set in Craftics grants an affinity, and a set granting none is a set that
     * reads as broken next to the seven that do. So an inferred set gets one too, chosen the
     * only way an unknown material allows: by what the material is called.
     *
     * <p>Vanilla's own pairings are the anchor, so a modded material that names itself after
     * one lands on the same affinity Craftics already gives it - a mod's "reinforced iron"
     * boosts Cleaving exactly like iron does, and a player's mental model survives the mod.
     * Materials naming nothing familiar fall to Physical, which is leather's: the plainest
     * affinity, and the right answer for armor that has not said anything about itself.
     *
     * <p>Registry-free, so the whole mapping is unit-testable against a list of names.
     */
    public static DamageType affinityForMaterial(String material) {
        if (material == null || material.isBlank()) return DamageType.PHYSICAL;
        String m = material.toLowerCase(java.util.Locale.ROOT);

        // Vanilla materials first, by their own name, so a modded variant of one agrees with it.
        if (containsAny(m, "chainmail", "chain", "mail", "scale", "brigandine", "lamellar")) {
            return DamageType.SLASHING;
        }
        if (containsAny(m, "netherite", "iron", "steel", "tungsten", "titanium", "cobalt",
                "obsidian", "plate", "knight", "adamant", "mithril")) {
            return DamageType.CLEAVING;
        }
        if (containsAny(m, "diamond", "crystal", "gem", "ruby", "sapphire", "amethyst",
                "emerald", "topaz", "quartz")) {
            return DamageType.BLUNT;
        }
        if (containsAny(m, "gold", "golden", "brass", "bronze", "electrum", "silver")) {
            return DamageType.SPECIAL;
        }
        if (containsAny(m, "turtle", "prismarine", "nautilus", "coral", "aquatic", "tide",
                "abyssal", "kelp")) {
            return DamageType.WATER;
        }
        if (containsAny(m, "leather", "hide", "fur", "cloth", "wool", "linen", "pelt")) {
            return DamageType.PHYSICAL;
        }
        return DamageType.PHYSICAL;
    }

    /**
     * A whole armor set inferred for a material nobody registered.
     *
     * <p>Affinity from the name, Armor Class from the pieces that actually exist in the
     * registry. Deliberately no flat stat bonuses - no extra AP, speed or attack: those are
     * what makes a vanilla set feel special, and handing them to every modded material would
     * flatten the difference rather than honour it.
     *
     * @return the inferred set, or null when no piece of that material could be found to
     *         measure, since a set with no measurable armor is a guess with nothing behind it
     */
    @Nullable
    public static com.crackedgames.craftics.api.registry.ArmorSetEntry inferArmorSet(String material) {
        if (material == null || material.isBlank()) return null;

        // Measure whichever pieces the material actually ships. A set need not be complete -
        // plenty of mods add a helmet alone - so the base AC is taken from any piece present.
        int baseAc = 0;
        for (ArmorClassTable.Slot slot : ArmorClassTable.Slot.values()) {
            Item piece = pieceOf(material, slot);
            if (piece == null) continue;
            baseAc = Math.max(baseAc, inferArmorBaseAC(piece));
        }
        if (baseAc <= 0) return null;

        DamageType affinity = affinityForMaterial(material);
        return com.crackedgames.craftics.api.registry.ArmorSetEntry.builder(material)
            .damageBonus(affinity, 1)
            .armorClass(baseAc)
            .description("§7" + capitalize(material) + ": +1 "
                + affinity.displayName + " affinity per 2 pieces")
            .build();
    }

    /** Any registered item named {@code <material>_<slot>}, in any namespace. */
    @Nullable
    private static Item pieceOf(String material, ArmorClassTable.Slot slot) {
        String suffix = switch (slot) {
            case HELMET -> "_helmet";
            case CHESTPLATE -> "_chestplate";
            case LEGGINGS -> "_leggings";
            case BOOTS -> "_boots";
        };
        for (Item candidate : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(candidate);
            if (id != null && id.getPath().equals(material + suffix)) return candidate;
        }
        return null;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Base AC Craftics gives each vanilla material, in the same order as the reference pieces.
     *
     * <p>Gold is left out for the same reason it is left out of the weapon ladders: Craftics
     * prices it at leather's Armor Class despite better vanilla stats, and putting that on the
     * curve would drag every modded mid-tier armor down through it.
     */
    private static final int[] ARMOR_LADDER_Y = {2, 3, 4, 6, 7};

    private static double[] armorLadderX(ArmorClassTable.Slot slot) {
        return new double[]{
            scoreOf(referencePiece(slot, "leather")),
            scoreOf(referencePiece(slot, "chainmail")),
            scoreOf(referencePiece(slot, "iron")),
            scoreOf(referencePiece(slot, "diamond")),
            scoreOf(referencePiece(slot, "netherite"))};
    }

    private static double scoreOf(@Nullable Item item) {
        if (item == null) return 0;
        return defensiveScore(
            additiveModifier(item, "armor"), additiveModifier(item, "armor_toughness"));
    }

    @Nullable
    private static Item referencePiece(ArmorClassTable.Slot slot, String material) {
        String suffix = switch (slot) {
            case HELMET -> "_helmet";
            case CHESTPLATE -> "_chestplate";
            case LEGGINGS -> "_leggings";
            case BOOTS -> "_boots";
        };
        Identifier id = Identifier.ofVanilla(material + suffix);
        return Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : null;
    }
}
