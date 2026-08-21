package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.world.CrafticsSavedData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A PARKED infinite run must never read as the run driving the current fight.
 *
 * <p>These pin the fix for a real bug: suspendRun leaves infiniteActive set (that is how
 * the run survives) and only sets infiniteSuspended, so every predicate that read the bare
 * flag answered yes while the host was clearing an ordinary campaign level - mislabelled
 * Go Home button, campaign clears banking infinite score, campaign boss kills routed into
 * the rest room, and a campaign wipe calling endRun on the parked run and destroying its
 * saved inventory. parkAndRestore also re-stamps infiniteRunHost at the host itself, so
 * the pointer route has to be pinned separately from the direct one.
 */
class LiveInfiniteRunPredicateTest {

    private static void park(CrafticsSavedData data, UUID host) {
        CrafticsSavedData.PlayerData pd = data.getPlayerData(host);
        pd.infiniteActive = true;
        pd.infiniteSuspended = true;
        pd.infiniteRunHost = host.toString(); // parkAndRestore re-stamps this at itself
    }

    @Test
    void parkedHostIsNotLive() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID host = UUID.randomUUID();
        park(data, host);
        assertFalse(InfiniteRunManager.isInLiveRun(data, host));
        assertFalse(InfiniteRunManager.isHostOfActiveRun(data, host));
        assertTrue(InfiniteRunManager.hasParkedRun(data, host));
    }

    @Test
    void parkedHostDoesNotResolveSolo() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID host = UUID.randomUUID();
        park(data, host);
        // Solo campaign victory: candidate and participant are both the parked host, and
        // its own infiniteRunHost self-stamp must not smuggle it back in via parseHostRef.
        assertNull(InfiniteRunManager.resolveActiveHost(data, host, host));
    }

    @Test
    void parkedMemberDoesNotDragCampaignPartyIntoTheRun() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID host = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        park(data, host);
        data.getPlayerData(member).infiniteRunHost = host.toString(); // stale pointer
        assertNull(InfiniteRunManager.resolveActiveHost(data, leader, member));
    }

    @Test
    void liveHostStillResolvesDirectlyAndThroughAMemberPointer() {
        CrafticsSavedData data = new CrafticsSavedData();
        UUID host = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        CrafticsSavedData.PlayerData pd = data.getPlayerData(host);
        pd.infiniteActive = true;
        pd.infiniteSuspended = false;
        data.getPlayerData(member).infiniteRunHost = host.toString();
        assertTrue(InfiniteRunManager.isInLiveRun(data, host));
        assertEquals(host, InfiniteRunManager.resolveActiveHost(data, host, host));
        assertEquals(host, InfiniteRunManager.resolveActiveHost(data, leader, member));
    }

    @Test
    void nullUuidsAreNeverLive() {
        CrafticsSavedData data = new CrafticsSavedData();
        assertFalse(InfiniteRunManager.isInLiveRun(data, null));
        assertNull(InfiniteRunManager.resolveActiveHost(data, null, null));
    }
}
