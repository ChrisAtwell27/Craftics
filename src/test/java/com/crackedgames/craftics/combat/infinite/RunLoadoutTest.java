package com.crackedgames.craftics.combat.infinite;

import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bookkeeping half of {@link RunLoadout}: which parts exist, and how a snapshot maps onto
 * the stash and parked fields of a player's record. The swap itself needs a live player; what
 * can be pinned down without one is that the two sides can never be confused for each other
 * and that a part's key never drifts, since saves carry it.
 */
class RunLoadoutTest {

    @Test
    void partKeysAreFrozen() {
        // A renamed key reads every existing stash as "part never captured", and the restore
        // then keeps the RUN's version of that storage - a silent leak on the next update.
        // Adding a part is fine; renaming or removing one is a save migration.
        List<String> keys = RunLoadout.PARTS.stream().map(RunLoadout.Part::key).toList();
        assertEquals(List.of("xp", "enderChest", "backpacks"), keys);
    }

    @Test
    void partKeysAreUniqueAndNeverTheOverflowKey() {
        Set<String> seen = new HashSet<>();
        for (RunLoadout.Part part : RunLoadout.PARTS) {
            assertTrue(seen.add(part.key()), "duplicate part key " + part.key());
            assertNotEquals(RunLoadout.LOOSE, part.key());
        }
    }

    private static RunLoadout.Snapshot sample() {
        RunLoadout.Snapshot s = new RunLoadout.Snapshot();
        NbtList inventory = new NbtList();
        NbtCompound slot = new NbtCompound();
        slot.putByte("Slot", (byte) 3);
        inventory.add(slot);
        s.inventory = inventory;
        s.selectedSlot = 4;
        s.accessories = new NbtList();
        s.accessoriesCaptured = true;
        s.extras.put("xp", new NbtCompound());
        s.extras.put("backpacks", new NbtCompound());
        return s;
    }

    @Test
    void stashBridgeRoundTrips() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        RunLoadout.toStash(pd, sample());
        RunLoadout.Snapshot back = RunLoadout.fromStash(pd);
        assertEquals(sample().inventory, back.inventory);
        assertEquals(4, back.selectedSlot);
        assertTrue(back.accessoriesCaptured);
        assertTrue(back.has("xp"));
        assertTrue(back.has("backpacks"));
        assertFalse(back.has("enderChest"));
    }

    @Test
    void stashAndParkedNeverAlias() {
        // Parking writes the run's loadout; the stash holds the real one. A bridge that wrote
        // one side's fields from the other would hand run loot back as real items.
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        RunLoadout.toStash(pd, sample());
        assertTrue(RunLoadout.fromParked(pd).extras.isEmpty());
        assertFalse(RunLoadout.fromParked(pd).accessoriesCaptured);

        RunLoadout.Snapshot parked = new RunLoadout.Snapshot();
        parked.extras.put("enderChest", new NbtList());
        RunLoadout.toParked(pd, parked);
        assertTrue(RunLoadout.fromParked(pd).has("enderChest"));
        assertFalse(RunLoadout.fromStash(pd).has("enderChest"));
        assertTrue(RunLoadout.fromStash(pd).has("backpacks"));
    }

    @Test
    void clearingEmptiesEveryPart() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        RunLoadout.toStash(pd, sample());
        RunLoadout.toParked(pd, sample());
        RunLoadout.clearStash(pd);
        RunLoadout.clearParked(pd);
        for (RunLoadout.Snapshot s : List.of(RunLoadout.fromStash(pd), RunLoadout.fromParked(pd))) {
            assertTrue(s.inventory.isEmpty());
            assertTrue(s.extras.isEmpty());
            assertFalse(s.accessoriesCaptured);
            assertEquals(0, s.selectedSlot);
        }
    }

    @Test
    void aFreshSnapshotHasCapturedNothingButTheInventory() {
        // The empty snapshot is what an uncaptured side looks like. It must claim no extra
        // part and no accessories, so a mask built from it moves nothing it cannot give back.
        RunLoadout.Snapshot empty = new RunLoadout.Snapshot();
        assertFalse(empty.accessoriesCaptured);
        for (RunLoadout.Part part : RunLoadout.PARTS) {
            assertFalse(empty.has(part.key()));
        }
    }
}
