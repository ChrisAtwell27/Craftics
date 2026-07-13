package com.crackedgames.craftics.sound;

import com.crackedgames.craftics.combat.TraderCategory;
import com.crackedgames.craftics.combat.VanillaTraderContent;

/**
 * Server-side soundtrack selection. Pure mapping from combat/biome/boss/event state to a
 * {@link MusicTracks}. {@link com.crackedgames.craftics.combat.CombatManager} gathers its
 * current state and calls {@link #select} on every transition; the result key is broadcast
 * to clients via {@link com.crackedgames.craftics.network.MusicSyncPayload}.
 *
 * <p>Selection priority: <b>trader &gt; cinematic event &gt; boss level &gt; biome battle &gt; stop</b>.</p>
 */
public final class MusicDirector {

    private MusicDirector() {}

    /**
     * @param active     whether a combat run is currently active
     * @param biomeId    current biome id (e.g. "snowy"); may be null
     * @param bossLevel  true if the current level is this biome's boss level
     * @param eventRoom  active cinematic event room id ("trial"/"vault"/"traveler"/"ambush"/
     *                   "shrine"/"enchanter"/"shiny"/"dig_site"), or null/empty if none
     * @param trader     active trader's type, or null if no trader is being offered
     * @param ominous    true for the ominous variant of a trial
     * @return the track to play, or {@code null} to fade out / play nothing
     */
    public static MusicTracks select(boolean active, String biomeId, boolean bossLevel,
                                     String eventRoom, TraderCategory trader, boolean ominous) {
        // 1. Trader offer overrides everything (it's a between-level interlude).
        if (trader != null) return traderTrack(trader);

        // 2. Cinematic event rooms with a dedicated track.
        if (eventRoom != null && !eventRoom.isEmpty()) {
            MusicTracks ev = eventTrack(eventRoom, ominous);
            if (ev != null) return ev;
            // Event room without its own track (shrine/enchanter/dig-site-without-file):
            // fall through so we keep the biome track playing instead of going silent.
        }

        if (!active || biomeId == null) return null;

        // 3. Boss level → boss track (falls back to biome battle if none mapped).
        if (bossLevel) {
            MusicTracks boss = bossTrack(biomeId);
            if (boss != null) return boss;
        }

        // 4. Regular biome battle.
        return biomeTrack(biomeId);
    }

    public static MusicTracks biomeTrack(String biomeId) {
        if (biomeId == null) return null;
        return switch (biomeId) {
            case "plains" -> MusicTracks.ARENA2;
            case "forest" -> MusicTracks.TARGETED;
            case "jungle" -> MusicTracks.FRIEND_OR_FOE;
            case "desert" -> MusicTracks.CACTI_CANYON;
            case "snowy" -> MusicTracks.FROZEN_FJORD;
            case "mountain" -> MusicTracks.FLURRY;
            case "river" -> MusicTracks.SQUID_COAST;
            case "cave" -> MusicTracks.REDSTONE_MINES;
            case "deep_dark" -> MusicTracks.CRYPT;
            case "nether_wastes" -> MusicTracks.NETHER_WASTES;
            case "basalt_deltas" -> MusicTracks.BASALT_DELTAS;
            case "crimson_forest" -> MusicTracks.CRIMSON_FOREST;
            case "warped_forest" -> MusicTracks.WARPED_FOREST;
            case "soul_sand_valley" -> MusicTracks.SOULSAND_VALLEY;
            case "end_city" -> MusicTracks.CITY;
            case "outer_end_islands" -> MusicTracks.END_WILDS;
            case "chorus_grove" -> MusicTracks.ASTRAY_ARCHIPELAGO;
            case "dragons_nest" -> MusicTracks.BROKEN_HEART_OF_ENDER;
            case "trial_chamber" -> MusicTracks.GALE_SANCTUM;
            case "trial_chamber_ominous" -> MusicTracks.GARRISON;
            default -> null;
        };
    }

    public static MusicTracks bossTrack(String biomeId) {
        if (biomeId == null) return null;
        return switch (biomeId) {
            case "plains" -> MusicTracks.BATTLE_FOR_DAYLIGHT;
            case "forest" -> MusicTracks.EVOKER;
            case "jungle" -> MusicTracks.SPIDER_DEN;
            case "desert" -> MusicTracks.DESERT_TEMPLE;
            case "snowy" -> MusicTracks.RIVER_LOCK;
            case "mountain" -> MusicTracks.RUSH;
            case "river" -> MusicTracks.SOGGY_SWAMP;
            case "cave" -> MusicTracks.NECROMANCER;
            case "deep_dark" -> MusicTracks.REDSTONE_MONSTROSITY;
            case "nether_wastes" -> MusicTracks.KERMETIC;
            case "basalt_deltas" -> MusicTracks.MENTA_MENARDI;
            case "soul_sand_valley" -> MusicTracks.GHAST;
            case "crimson_forest" -> MusicTracks.ANCIENT;
            case "warped_forest" -> MusicTracks.TORN_ACINDER;
            case "end_city" -> MusicTracks.SHIP;
            case "outer_end_islands" -> MusicTracks.ENDERMAN;
            case "chorus_grove" -> MusicTracks.SHATTERED;
            case "dragons_nest" -> MusicTracks.BROKEN_HEART_OF_ENDER;
            case "trial_chamber", "trial_chamber_ominous" -> MusicTracks.GARRISON;
            default -> null;
        };
    }

    /**
     * Soundtrack for a trader booth. Keyed by trader id rather than an enum, so an addon trader
     * simply lands on the default track instead of failing to compile or throwing.
     */
    public static MusicTracks traderTrack(TraderCategory type) {
        if (type == null) return null;
        return switch (type.id()) {
            case VanillaTraderContent.WEAPONSMITH, VanillaTraderContent.ARMORER -> MusicTracks.STUGA;
            case VanillaTraderContent.DECORATOR, VanillaTraderContent.CURIOSITY_DEALER -> MusicTracks.MARY;
            // Provisioner, Alchemist, Supplier, Craftsman, and every addon trader.
            default -> MusicTracks.BEACH_HOUSE;
        };
    }

    public static MusicTracks eventTrack(String eventRoom, boolean ominous) {
        if (eventRoom == null) return null;
        return switch (eventRoom) {
            case "trial" -> ominous ? MusicTracks.GARRISON : MusicTracks.GALE_SANCTUM;
            case "vault" -> MusicTracks.SKOGSSTUGA;
            case "traveler" -> MusicTracks.LOST_SETTLEMENT;
            // The "ambush" event is presented through the "shiny" choice room.
            case "ambush", "shiny" -> MusicTracks.HUNTERS;
            // "shrine", "enchanter", "dig_site": no dedicated track supplied
            default -> null;
        };
    }
}
