package com.crackedgames.craftics.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip guards for the infinite-run stash and parked-run wallets.
 *
 * <p>These exist because of a real data-loss bug: both emerald fields were WRITTEN by the
 * shared {@code toNbt}, and read back in the 1.21.1-1.21.4 branch of {@code fromNbt}, but
 * the 1.21.5 branch never read either one. On that shard a player who logged out mid-run
 * came back with their real emerald balance replaced by zero, because
 * {@code infiniteStashEmeralds} is exactly what gets restored to {@code pd.emeralds} when
 * the run ends.
 *
 * <p><b>Why the bug survived so long:</b> it compiles perfectly on every shard, and a test
 * only exercises whichever stonecutter branch is active in the shard running it. Nothing
 * catches a one-sided read except a round-trip assertion that runs on ALL FOUR shards,
 * which is what {@code ./gradlew build} does. A test that only ever ran on 1.21.1 would
 * have passed throughout.
 *
 * <p>So: when adding a persisted field, add a round-trip case here (or in a sibling test)
 * rather than trusting that the write and the two reads agree. The compiler cannot check
 * it and neither can a single-shard test run.
 */
class InfiniteStashNbtTest {

    @Test
    void stashEmeraldsRoundTrip() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        pd.infiniteStashActive = true;
        pd.infiniteStashEmeralds = 1234;
        CrafticsSavedData.PlayerData back = CrafticsSavedData.PlayerData.fromNbt(pd.toNbt());
        assertTrue(back.infiniteStashActive);
        assertEquals(1234, back.infiniteStashEmeralds,
            "stashed emeralds must survive a save/load on every shard");
    }

    @Test
    void parkedEmeraldsRoundTrip() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        pd.infiniteSuspended = true;
        pd.infiniteParkedEmeralds = 4321;
        CrafticsSavedData.PlayerData back = CrafticsSavedData.PlayerData.fromNbt(pd.toNbt());
        assertTrue(back.infiniteSuspended);
        assertEquals(4321, back.infiniteParkedEmeralds,
            "a parked run's wallet must survive a save/load on every shard");
    }

    @Test
    void bothWalletsAreIndependent() {
        // They are adjacent ints with near-identical names, which is how one read got
        // written and the other did not. Prove they do not alias.
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        pd.infiniteStashEmeralds = 7;
        pd.infiniteParkedEmeralds = 9;
        CrafticsSavedData.PlayerData back = CrafticsSavedData.PlayerData.fromNbt(pd.toNbt());
        assertEquals(7, back.infiniteStashEmeralds);
        assertEquals(9, back.infiniteParkedEmeralds);
    }

    @Test
    void absentWalletsDefaultToZero() {
        // An existing save predating either field must load as zero, not throw.
        CrafticsSavedData.PlayerData back =
            CrafticsSavedData.PlayerData.fromNbt(new CrafticsSavedData.PlayerData().toNbt());
        assertEquals(0, back.infiniteStashEmeralds);
        assertEquals(0, back.infiniteParkedEmeralds);
    }
}
