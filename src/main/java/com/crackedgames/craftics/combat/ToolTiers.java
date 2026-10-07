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
}
