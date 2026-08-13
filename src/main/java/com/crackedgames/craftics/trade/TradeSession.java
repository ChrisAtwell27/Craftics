package com.crackedgames.craftics.trade;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Two players agreeing to swap, with nothing held in between.
 *
 * <p><b>There is no escrow, and that is the whole design.</b> The obvious way to build a trade
 * screen is to take both sides' items into the window and hand them out when both agree, which
 * means that between those two moments the items exist somewhere that is neither inventory. A
 * crash, a disconnect, a server restart or a shutdown mid-trade then has to decide who owns
 * them, and every wrong answer either destroys an item or creates one. That is where duping
 * comes from - not from clever clicking, but from a state a crash can strand.
 *
 * <p>So an offer here is a DESCRIPTION of items the player still physically holds. Nothing
 * moves, nothing is reserved, and nothing is persisted, because there is nothing to persist: a
 * session that dies for any reason at all leaves both inventories exactly as they were. The
 * swap happens in {@link TradeMenus}, in one tick, and only after re-verifying that both sides
 * still hold everything they offered - so an item spent, dropped or traded away elsewhere while
 * the window was open cancels the trade instead of being conjured.
 *
 * <p>Kept free of world and registry access so the session rules can be reasoned about (and
 * tested) on their own, the same split the auction store uses.
 */
public final class TradeSession {

    /** How many stacks one side may put up. Two rows of the screen. */
    public static final int MAX_OFFER = 18;

    /** Every player currently in a trade, mapped to the session they are in. */
    private static final Map<UUID, TradeSession> ACTIVE = new HashMap<>();

    /** Outstanding invitations: target -> who asked. One pending invite per target. */
    private static final Map<UUID, PendingInvite> INVITES = new HashMap<>();

    /** An invitation waits this long before it is treated as ignored. */
    public static final long INVITE_TIMEOUT_MS = 60_000L;

    public record PendingInvite(UUID from, String fromName, long sentAtMs) {}

    private final UUID a;
    private final UUID b;
    private final String nameA;
    private final String nameB;
    private final List<ItemStack> offerA = new ArrayList<>();
    private final List<ItemStack> offerB = new ArrayList<>();
    private boolean confirmedA = false;
    private boolean confirmedB = false;
    private boolean finished = false;

    private TradeSession(UUID a, String nameA, UUID b, String nameB) {
        this.a = a;
        this.nameA = nameA;
        this.b = b;
        this.nameB = nameB;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Record an invitation from {@code from} to {@code to}, replacing any earlier one. */
    public static void invite(ServerPlayerEntity from, ServerPlayerEntity to, long nowMs) {
        INVITES.put(to.getUuid(),
            new PendingInvite(from.getUuid(), from.getName().getString(), nowMs));
    }

    /** The invitation {@code target} is holding from {@code from}, or null when there is none
     *  or it has gone stale. A stale invite is dropped as it is read. */
    public static PendingInvite inviteFrom(UUID target, UUID from, long nowMs) {
        PendingInvite invite = INVITES.get(target);
        if (invite == null) return null;
        if (nowMs - invite.sentAtMs() > INVITE_TIMEOUT_MS) {
            INVITES.remove(target);
            return null;
        }
        return invite.from().equals(from) ? invite : null;
    }

    public static void clearInvite(UUID target) {
        INVITES.remove(target);
    }

    /** Open a session between two players, or null if either is already trading. */
    public static TradeSession open(ServerPlayerEntity a, ServerPlayerEntity b) {
        if (ACTIVE.containsKey(a.getUuid()) || ACTIVE.containsKey(b.getUuid())) return null;
        TradeSession session = new TradeSession(
            a.getUuid(), a.getName().getString(), b.getUuid(), b.getName().getString());
        ACTIVE.put(a.getUuid(), session);
        ACTIVE.put(b.getUuid(), session);
        INVITES.remove(a.getUuid());
        INVITES.remove(b.getUuid());
        return session;
    }

    public static TradeSession of(UUID player) {
        return ACTIVE.get(player);
    }

    /** End the session for both sides. Safe to call twice; nothing is owed either way. */
    public void end() {
        finished = true;
        ACTIVE.remove(a);
        ACTIVE.remove(b);
    }

    public boolean isFinished() {
        return finished;
    }

    /** Wipe every session. For shutdown and tests; no items are involved. */
    public static void clearAll() {
        ACTIVE.clear();
        INVITES.clear();
    }

    // ── Sides ────────────────────────────────────────────────────────────────

    public boolean has(UUID player) {
        return a.equals(player) || b.equals(player);
    }

    public UUID other(UUID player) {
        return a.equals(player) ? b : a;
    }

    public String nameOf(UUID player) {
        return a.equals(player) ? nameA : nameB;
    }

    public List<ItemStack> offerOf(UUID player) {
        return a.equals(player) ? offerA : offerB;
    }

    public boolean confirmed(UUID player) {
        return a.equals(player) ? confirmedA : confirmedB;
    }

    public boolean bothConfirmed() {
        return confirmedA && confirmedB;
    }

    // ── Mutation ─────────────────────────────────────────────────────────────

    /**
     * Add a stack to a side's offer.
     *
     * <p>The stack is copied rather than referenced: the original stays in the player's
     * inventory and they may keep using it, and the copy is only ever a description of what
     * they promised. Verification at swap time is what makes that safe.
     *
     * @return false when the offer is full
     */
    public boolean addOffer(UUID player, ItemStack stack) {
        List<ItemStack> offer = offerOf(player);
        if (offer.size() >= MAX_OFFER || stack.isEmpty()) return false;
        offer.add(stack.copy());
        unconfirmBoth();
        return true;
    }

    /** Take one stack back out of a side's offer. */
    public void removeOffer(UUID player, int index) {
        List<ItemStack> offer = offerOf(player);
        if (index < 0 || index >= offer.size()) return;
        offer.remove(index);
        unconfirmBoth();
    }

    /** Flip one side's confirmation. */
    public void toggleConfirm(UUID player) {
        if (a.equals(player)) confirmedA = !confirmedA; else confirmedB = !confirmedB;
    }

    /**
     * Any change to either offer un-confirms BOTH sides.
     *
     * <p>The standard defence against the oldest trade scam there is: agree on a deal, wait for
     * the other side to confirm, then swap your stack of diamonds for a stack of dirt. If a
     * confirmation could survive a change to what it was confirming, it would not mean anything.
     */
    private void unconfirmBoth() {
        confirmedA = false;
        confirmedB = false;
    }
}
