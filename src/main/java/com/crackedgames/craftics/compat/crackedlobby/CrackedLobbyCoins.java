package com.crackedgames.craftics.compat.crackedlobby;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reflection shim over the CrackedGames lobby mod ("crackedlobby") coin economy.
 * <p>
 * Network coins are the hub-side currency players spend on cosmetic lootboxes. They are
 * separate from anything Craftics tracks in a run: awarding is one-way and Craftics never
 * reads or spends a balance. Everything here is a no-op when the lobby mod is absent, so
 * single-player and non-CrackedGames servers are unaffected and there is no compile-time
 * dependency on the lobby jar.
 * <p>
 * The upstream entry point is {@code dev.crackedgames.lobby.fabric.CrackedCoins#award},
 * which is deliberately award-only and returns a {@code CompletableFuture<CoinResult>}
 * that completes normally even on failure. Awards run off-thread; nothing here blocks the
 * server thread and nothing here is required for a run to complete.
 */
public final class CrackedLobbyCoins {

    /** Fabric mod id of the lobby mod. */
    public static final String MOD_ID = "crackedlobby";

    /** Recorded against every transaction so hub-side auditing can attribute it. */
    public static final String SOURCE = "craftics";

    /**
     * Coins paid to every participant for a boss defeat, campaign or infinite alike.
     * The hub applies the player's rank multiplier on top of this.
     */
    public static final long BOSS_COIN_REWARD = 10L;

    /**
     * The transactions table declares {@code UNIQUE KEY uk_idem (idempotency_key)} on a
     * {@code VARCHAR(64)} column, so keys are unique network-wide rather than per player.
     * Every key built here embeds the player's UUID or two players clearing the same boss
     * would collide and the second award would be dropped as a duplicate.
     */
    private static final int MAX_KEY_LENGTH = 64;

    private static final Method AWARD;      // CrackedCoins.award(UUID, long, String, String)
    private static final Method IS_SUCCESS; // CoinResult.isSuccess()
    private static final boolean AVAILABLE;

    static {
        Method award = null, isSuccess = null;
        boolean ok = false;
        Throwable failure = null;
        if (FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            try {
                Class<?> coinsCls = Class.forName("dev.crackedgames.lobby.fabric.CrackedCoins");
                award = coinsCls.getMethod("award", UUID.class, long.class, String.class, String.class);
                Class<?> resultCls = Class.forName("dev.crackedgames.lobby.api.CoinResult");
                isSuccess = resultCls.getMethod("isSuccess");
                ok = true;
            } catch (Throwable t) {
                failure = t;
            }
        }
        AWARD = award;
        IS_SUCCESS = isSuccess;
        AVAILABLE = ok;
        if (ok) {
            CrafticsMod.LOGGER.info("[Craftics x CrackedLobby] Resolved CrackedCoins via reflection; boss coin awards enabled");
        } else if (failure != null) {
            CrafticsMod.LOGGER.warn("[Craftics x CrackedLobby] Lobby mod present but coin API not resolvable: {}", failure.toString());
        }
    }

    private CrackedLobbyCoins() {}

    /** True when the lobby mod is installed and its coin API resolved. */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Pays a player network coins, doing nothing when the lobby mod is absent.
     * <p>
     * The lobby applies the player's rank multiplier on its side, so a ranked player may
     * bank more than {@code amount}. That is intentional and lives with the hub, not here.
     *
     * @param uuid           the player to pay
     * @param amount         coins before any hub-side rank multiplier; ignored when not positive
     * @param idempotencyKey identifies the occasion, not the call. Must already embed the
     *                       player UUID; see {@link #bossKey}. Truncated to 64 characters.
     */
    public static void award(UUID uuid, long amount, String idempotencyKey) {
        if (!AVAILABLE || uuid == null || amount <= 0) {
            return;
        }
        String key = idempotencyKey == null || idempotencyKey.isEmpty()
                ? null
                : (idempotencyKey.length() > MAX_KEY_LENGTH
                        ? idempotencyKey.substring(0, MAX_KEY_LENGTH)
                        : idempotencyKey);
        try {
            Object future = AWARD.invoke(null, uuid, amount, SOURCE, key);
            if (future instanceof CompletableFuture<?> cf) {
                cf.whenComplete((result, error) -> report(uuid, amount, key, result, error));
            }
        } catch (Throwable t) {
            CrafticsMod.LOGGER.warn("[Craftics x CrackedLobby] Coin award threw for {}: {}", uuid, t.toString());
        }
    }

    /**
     * Pays every participant of a cleared boss fight, silently doing nothing when the
     * lobby mod is absent. Called once per boss defeat from the victory branch.
     *
     * @param recipients   the players credited for the clear, usually the whole party
     * @param occasion     identifies this fight; see {@link #bossKey}
     */
    public static void awardBossDefeat(List<? extends ServerPlayerEntity> recipients, String occasion) {
        if (!AVAILABLE || recipients == null) {
            return;
        }
        for (ServerPlayerEntity recipient : recipients) {
            if (recipient == null) continue;
            UUID uuid = recipient.getUuid();
            award(uuid, BOSS_COIN_REWARD, bossKey(uuid, occasion));
        }
    }

    /**
     * Builds an idempotency key for one player clearing one boss on one occasion.
     * <p>
     * Layout is {@code cfxb:<32-char uuid>:<occasion>}, which leaves 26 characters for the
     * occasion before the 64-character column truncates it. The occasion should identify
     * the fight (boss id plus run depth or biome ordinal), never something that varies per
     * call, or a retry after a timeout would pay twice.
     */
    public static String bossKey(UUID uuid, String occasion) {
        String flat = uuid == null ? "unknown" : uuid.toString().replace("-", "");
        String tail = occasion == null ? "" : occasion.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        String key = "cfxb:" + flat + ":" + tail;
        return key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH) : key;
    }

    private static void report(UUID uuid, long amount, String key, Object result, Throwable error) {
        if (error != null) {
            CrafticsMod.LOGGER.warn("[Craftics x CrackedLobby] Coin award failed for {} ({}): {}",
                    uuid, key, error.toString());
            return;
        }
        try {
            if (IS_SUCCESS != null && result != null && !((Boolean) IS_SUCCESS.invoke(result))) {
                // DUPLICATE is expected on a retry and is not an error; the rest are worth seeing.
                CrafticsMod.LOGGER.info("[Craftics x CrackedLobby] Coin award of {} for {} not applied ({}): {}",
                        amount, uuid, key, result);
            }
        } catch (Throwable ignored) {
            // Reporting must never disturb a run.
        }
    }
}
