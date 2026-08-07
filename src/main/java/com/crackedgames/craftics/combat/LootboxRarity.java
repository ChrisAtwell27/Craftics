package com.crackedgames.craftics.combat;

/**
 * How impressive a rolled reward looks when the reveal plays. Ported from the
 * CrackedGamesLobbyPlugin's {@code Rarity}: display only. The chance of winning something is
 * its weight/section chance and nothing else - a mislabelled rarity here is a cosmetic bug,
 * never a probability bug.
 */
public enum LootboxRarity {

    COMMON("Common", "§f", 0xFFFFFF, 20),
    UNCOMMON("Uncommon", "§a", 0x55FF55, 40),
    RARE("Rare", "§b", 0x55FFFF, 70),
    EPIC("Epic", "§d", 0xFF55FF, 110),
    LEGENDARY("Legendary", "§6", 0xFFAA00, 160);

    /** Shown in the reveal announcement, e.g. "(Legendary)". */
    public final String label;
    /** The §-code matching {@link #color}, for chat text. */
    public final String legacyColor;
    /** RGB used for the reveal's dust particles. */
    public final int color;
    /** Particles in the finishing burst. */
    public final int burstParticles;

    LootboxRarity(String label, String legacyColor, int color, int burstParticles) {
        this.label = label;
        this.legacyColor = legacyColor;
        this.color = color;
        this.burstParticles = burstParticles;
    }
}
