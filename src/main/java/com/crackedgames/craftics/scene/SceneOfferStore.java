package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.combat.TraderSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-island village-booth trade offers, refreshed every 10 minutes of world time regardless of
 *  scene occupancy. Decoupled from SceneController so offers persist across scene teardown and the
 *  refresh timer runs while the world is open. Barter booths need no stored offers (their category
 *  is fixed by occupant; rewards roll at choice time). */
public final class SceneOfferStore {
    private SceneOfferStore() {}

    /** 10 minutes at 20 ticks/second. */
    public static final int REFRESH_TICKS = 12000;

    // island owner -> (boothIndex -> rolled offer)
    private static final Map<UUID, Map<Integer, TraderSystem.TraderOffer>> OFFERS = new HashMap<>();
    private static int counter = REFRESH_TICKS;

    /** Pure cadence: true exactly on the tick the countdown reaches its wrap point. */
    public static boolean shouldRefresh(int counterBeforeDecrement) {
        return counterBeforeDecrement <= 1;
    }

    /** Pure cadence: the next counter value after a decrement (wraps to REFRESH_TICKS at 0). */
    public static int nextCounter(int counterBeforeDecrement) {
        return counterBeforeDecrement <= 1 ? REFRESH_TICKS : counterBeforeDecrement - 1;
    }

    /** Tick once per server tick; clears all offers on the 10-min boundary so they re-roll lazily. */
    public static void tick() {
        if (shouldRefresh(counter)) OFFERS.clear();
        counter = nextCounter(counter);
    }

    /** Current offer for a village booth, rolled lazily (per fixed type + tier) if absent. */
    public static TraderSystem.TraderOffer getOffers(UUID owner, int boothIndex,
                                                     TraderSystem.TraderType type, int tier) {
        Map<Integer, TraderSystem.TraderOffer> byBooth =
            OFFERS.computeIfAbsent(owner, k -> new HashMap<>());
        return byBooth.computeIfAbsent(boothIndex,
            k -> TraderSystem.generateOfferForType(type, tier, new java.util.Random()));
    }

    /** Test/reset hook. */
    public static void clearAll() {
        OFFERS.clear();
        counter = REFRESH_TICKS;
    }
}
