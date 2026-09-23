package com.crackedgames.craftics.world;

import org.junit.jupiter.api.Test;

import java.util.UUID;

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

    // ── Extra loadout parts (XP, ender chest, worn backpacks) ───────────────────
    //
    // These carry the player's REAL XP, ender chest and backpacks while a run is on. A shard
    // that wrote them and failed to read them back would restore nothing at the end of a run:
    // the part would read as "never captured" and the run's version would be kept - exactly
    // the leak the parts exist to close.

    private static net.minecraft.nbt.NbtCompound sampleExtras() {
        net.minecraft.nbt.NbtCompound xp = new net.minecraft.nbt.NbtCompound();
        xp.putInt("level", 30);
        xp.putFloat("progress", 0.5f);
        xp.putInt("total", 1395);
        net.minecraft.nbt.NbtCompound packs = new net.minecraft.nbt.NbtCompound();
        packs.put("packs", new net.minecraft.nbt.NbtList());
        net.minecraft.nbt.NbtCompound extras = new net.minecraft.nbt.NbtCompound();
        extras.put("xp", xp);
        extras.put("enderChest", new net.minecraft.nbt.NbtList());
        extras.put("backpacks", packs);
        return extras;
    }

    @Test
    void stashExtrasRoundTrip() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        pd.infiniteStashActive = true;
        pd.infiniteStashExtras = sampleExtras();
        CrafticsSavedData.PlayerData back = CrafticsSavedData.PlayerData.fromNbt(pd.toNbt());
        assertEquals(pd.infiniteStashExtras, back.infiniteStashExtras,
            "every captured stash part must survive a save/load on every shard");
    }

    @Test
    void parkedExtrasRoundTrip() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        pd.infiniteSuspended = true;
        pd.infiniteParkedExtras = sampleExtras();
        CrafticsSavedData.PlayerData back = CrafticsSavedData.PlayerData.fromNbt(pd.toNbt());
        assertEquals(pd.infiniteParkedExtras, back.infiniteParkedExtras,
            "every parked run part must survive a save/load on every shard");
    }

    @Test
    void absentExtrasLoadAsNothingCaptured() {
        // A stash taken before the parts existed has no extras key at all. It must load as an
        // empty compound - "no part captured", so the restore leaves that storage alone -
        // never as null, and never as a part captured empty (which would wipe it).
        net.minecraft.nbt.NbtCompound legacy = new CrafticsSavedData.PlayerData().toNbt();
        legacy.remove("infiniteStashExtras");
        legacy.remove("infiniteParkedExtras");
        CrafticsSavedData.PlayerData back = CrafticsSavedData.PlayerData.fromNbt(legacy);
        assertNotNull(back.infiniteStashExtras);
        assertNotNull(back.infiniteParkedExtras);
        assertTrue(back.infiniteStashExtras.isEmpty());
        assertTrue(back.infiniteParkedExtras.isEmpty());
    }

    @Test
    void stashAndParkedExtrasAreIndependent() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        net.minecraft.nbt.NbtCompound onlyXp = new net.minecraft.nbt.NbtCompound();
        onlyXp.put("xp", new net.minecraft.nbt.NbtCompound());
        pd.infiniteStashExtras = sampleExtras();
        pd.infiniteParkedExtras = onlyXp;
        CrafticsSavedData.PlayerData back = CrafticsSavedData.PlayerData.fromNbt(pd.toNbt());
        assertTrue(back.infiniteStashExtras.contains("backpacks"));
        assertFalse(back.infiniteParkedExtras.contains("backpacks"));
    }

    // ── A stash outlives a record reset ─────────────────────────────────────────

    private static CrafticsSavedData dataWithActiveStash(UUID id) {
        CrafticsSavedData data = new CrafticsSavedData();
        CrafticsSavedData.PlayerData pd = data.getPlayerData(id);
        pd.worldSlot = 2;
        pd.infiniteRunHost = id.toString();
        pd.infiniteStashActive = true;
        pd.infiniteStashEmeralds = 900;
        pd.infiniteStashStats = "stats";
        pd.infiniteStashExtras = sampleExtras();
        return data;
    }

    @Test
    void forgetIslandKeepsAnActiveStash() {
        // Deleting an island mid-run used to drop the stash with the record, and the run's
        // loadout became the player's real one for good.
        UUID id = UUID.randomUUID();
        CrafticsSavedData data = dataWithActiveStash(id);
        data.forgetIsland(id);
        CrafticsSavedData.PlayerData fresh = data.getPlayerData(id);
        assertEquals(-1, fresh.worldSlot, "the island itself is still forgotten");
        assertTrue(fresh.infiniteStashActive);
        assertEquals(900, fresh.infiniteStashEmeralds);
        assertEquals("stats", fresh.infiniteStashStats);
        assertEquals(id.toString(), fresh.infiniteRunHost);
        assertEquals(sampleExtras(), fresh.infiniteStashExtras);
    }

    @Test
    void resetPlayerDataKeepsAnActiveStash() {
        UUID id = UUID.randomUUID();
        CrafticsSavedData data = dataWithActiveStash(id);
        data.resetPlayerData(id);
        CrafticsSavedData.PlayerData fresh = data.getPlayerData(id);
        assertTrue(fresh.infiniteStashActive);
        assertEquals(900, fresh.infiniteStashEmeralds);
        assertEquals(sampleExtras(), fresh.infiniteStashExtras);
    }

    @Test
    void resetWithoutAStashCarriesNothing() {
        UUID id = UUID.randomUUID();
        CrafticsSavedData data = new CrafticsSavedData();
        CrafticsSavedData.PlayerData pd = data.getPlayerData(id);
        pd.infiniteStashEmeralds = 5;   // stale leftovers, stash not active
        data.forgetIsland(id);
        CrafticsSavedData.PlayerData fresh = data.getPlayerData(id);
        assertFalse(fresh.infiniteStashActive);
        assertEquals(0, fresh.infiniteStashEmeralds);
    }
}
