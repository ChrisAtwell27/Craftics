package com.crackedgames.craftics.combat;

/**
 * Which vanilla tier a tool belongs to, read off its name.
 *
 * <p>For the places that need to rank a tool nobody registered - a modded pickaxe swung at
 * something only a pickaxe can break. The name is the only thing every mod's tools have in
 * common, so the name is what is read. Kept free of Minecraft imports so the ranking can be
 * tested without a bootstrap.
 */
public final class ToolTiers {

    private ToolTiers() {}

    public static final int WOOD = 0;
    public static final int STONE = 1;
    public static final int IRON = 2;
    public static final int DIAMOND = 3;
    public static final int NETHERITE = 4;

    /**
     * The tier of the tool with this registry path. Aether materials sit where they mine:
     * holystone with stone, zanite with iron, gravitite and valkyrie with diamond. Gold is
     * wood, as it is in vanilla for everything but speed. Anything unrecognised is wood too -
     * the safe side to be wrong on, since the result is a damage number.
     */
    public static int of(String path) {
        if (path == null) return WOOD;
        if (path.contains("netherite")) return NETHERITE;
        if (path.contains("diamond") || path.contains("gravitite") || path.contains("valkyrie")) return DIAMOND;
        if (path.contains("iron") || path.contains("zanite")) return IRON;
        // "holystone" contains "stone", so it needs no entry of its own.
        if (path.contains("stone") || path.contains("copper")) return STONE;
        return WOOD;
    }

    /**
     * How much of a struck combatant's full health one swing of a pickaxe of this tier takes,
     * in thousandths, on top of the tool's own flat damage.
     *
     * <p>A pickaxe strike is the only thing that hurts something a pickaxe alone can hurt, so
     * it has to keep pace with a health pool that grows with every biome cleared. A flat
     * number cannot: by the depth the Aether opens at, a boss has hundreds of health and the
     * best pickaxe's flat damage barely clears its armour. A share does, at any depth: about
     * 25 swings of a diamond pickaxe and 35 of an iron one against the Slider, whose armour
     * takes two fifths off every hit.
     */
    public static int strikeShare(int tier) {
        return switch (tier) {
            case NETHERITE -> 60;
            case DIAMOND -> 50;
            case IRON -> 38;
            case STONE -> 28;
            default -> 20;
        };
    }

    /** The health a swing takes by share alone: see {@link #strikeShare}. Never below one. */
    public static int strikeShareDamage(int tier, int targetMaxHp) {
        return Math.max(1, targetMaxHp * strikeShare(tier) / 1000);
    }
}
