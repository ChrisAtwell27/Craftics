package com.crackedgames.craftics.sound;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Catalog of every Craftics soundtrack. Single source of truth shared by:
 *   - {@link ModSounds} (registers a {@code SoundEvent} per entry),
 *   - {@code MusicDirector} (server-side track selection),
 *   - the client {@code MusicManager} / {@code MusicToast} (playback + "now playing" toast).
 *
 * <p>Each entry maps to {@code assets/craftics/sounds/music/<key>.ogg} via
 * {@code sounds.json} (top-level key {@code "music.<key>"}). The {@code displayName}
 * and {@code source} are factual attribution shown in the bottom-left toast.</p>
 */
public enum MusicTracks {
    // --- biome battle tracks ---
    ARENA2("arena2", "Arena II", "Minecraft Dungeons"),
    TARGETED("targeted", "Targeted...", "Minecraft Legends"),
    FRIEND_OR_FOE("friend_or_foe", "Friend or Foe?", "Minecraft Legends"),
    CACTI_CANYON("cacti_canyon", "Cacti Canyon", "Minecraft Dungeons"),
    FROZEN_FJORD("frozen_fjord", "Frozen Fjord", "Minecraft Dungeons: Creeping Winter"),
    FLURRY("flurry", "Flurry", "Minecraft Dungeons: Howling Peaks"),
    SQUID_COAST("squid_coast", "Squid Coast", "Minecraft Dungeons"),
    NETHER_WASTES("nether_wastes", "Nether Wastes", "Minecraft Dungeons: Flames of the Nether"),
    BASALT_DELTAS("basalt_deltas", "Basalt Deltas", "Minecraft Dungeons: Flames of the Nether"),
    CRIMSON_FOREST("crimson_forest", "Crimson Forest", "Minecraft Dungeons: Flames of the Nether"),
    WARPED_FOREST("warped_forest", "Warped Forest", "Minecraft Dungeons: Flames of the Nether"),
    SOULSAND_VALLEY("soulsand_valley", "Soulsand Valley", "Minecraft Dungeons: Flames of the Nether"),
    CITY("city", "City", "Minecraft Dungeons: Echoing Void"),
    END_WILDS("end_wilds", "End Wilds", "Minecraft Dungeons: Echoing Void"),
    ASTRAY_ARCHIPELAGO("astray_archipelago", "Astray Archipelago", "Minecraft Dungeons: Echoing Void"),
    BROKEN_HEART_OF_ENDER("broken_heart_of_ender", "Broken Heart of Ender", "Minecraft Dungeons: Echoing Void"),
    GALE_SANCTUM("gale_sanctum", "Gale Sanctum", "Minecraft Dungeons: Howling Peaks"),
    GARRISON("garrison", "Garrison", "Minecraft Dungeons: Howling Peaks"),
    REDSTONE_MINES("redstone_mines", "Redstone Mines", "Minecraft Dungeons"),
    CRYPT("crypt", "Crypt", "Minecraft Dungeons"),

    // --- boss tracks ---
    BATTLE_FOR_DAYLIGHT("battle_for_daylight", "Battle for Daylight", "Minecraft Legends"),
    EVOKER("evoker", "Evoker", "Minecraft Dungeons"),
    SPIDER_DEN("spider_den", "Spider Den", "Minecraft Dungeons"),
    DESERT_TEMPLE("desert_temple", "Desert Temple", "Minecraft Dungeons"),
    RIVER_LOCK("river_lock", "River Lock", "Minecraft Dungeons: Creeping Winter"),
    RUSH("rush", "Rush", "Minecraft Dungeons: Howling Peaks"),
    SOGGY_SWAMP("soggy_swamp", "Soggy Swamp", "Minecraft Dungeons"),
    NECROMANCER("necromancer", "Necromancer", "Minecraft Dungeons"),
    REDSTONE_MONSTROSITY("redstone_monstrosity", "Redstone Monstrosity", "Minecraft Dungeons"),
    KERMETIC("kermetic", "Kermetic", "Minecraft Dungeons: Flames of the Nether"),
    MENTA_MENARDI("menta_menardi", "Menta Menardi", "Minecraft Dungeons: Flames of the Nether"),
    GHAST("ghast", "Ghast", "Minecraft Dungeons: Flames of the Nether"),
    ANCIENT("ancient", "Ancient", "Minecraft Dungeons: Flames of the Nether"),
    TORN_ACINDER("torn_acinder", "Torn Acinder", "Minecraft Dungeons: Flames of the Nether"),
    SHIP("ship", "Ship", "Minecraft Dungeons: Echoing Void"),
    ENDERMAN("enderman", "Enderman", "Minecraft Dungeons"),
    SHATTERED("shattered", "Shattered", "Minecraft Dungeons: Echoing Void"),

    // --- event tracks ---
    STUGA("stuga", "Stuga", "Minecraft Dungeons"),
    MARY("mary", "Mary", "Minecraft Dungeons"),
    BEACH_HOUSE("beach_house", "Beach House", "Minecraft Dungeons"),
    HUNTERS("hunters", "Hunters in a Horrendous Hurry", "Minecraft Legends"),
    SKOGSSTUGA("skogsstuga", "Skogsstuga", "Minecraft Dungeons"),
    LOST_SETTLEMENT("lost_settlement", "Lost Settlement", "Minecraft Dungeons: Creeping Winter");

    public final String key;
    public final String displayName;
    public final String source;

    MusicTracks(String key, String displayName, String source) {
        this.key = key;
        this.displayName = displayName;
        this.source = source;
    }

    /** Registry / SoundEvent identifier, e.g. {@code craftics:music.frozen_fjord}. */
    public Identifier id() {
        return Identifier.of(CrafticsMod.MOD_ID, "music." + key);
    }

    private static final Map<String, MusicTracks> BY_KEY = new HashMap<>();
    static {
        for (MusicTracks t : values()) BY_KEY.put(t.key, t);
    }

    /** Look up a track by its {@link #key}; returns {@code null} for unknown / "" (stop). */
    public static MusicTracks byKey(String key) {
        if (key == null || key.isEmpty()) return null;
        return BY_KEY.get(key);
    }
}
