package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.CrafticsMod;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The attack-type chart, and which types each mob defends as.
 *
 * <p>See {@link AttackTypeEntry} for what an attack type is and why it is a separate idea
 * from {@code DamageType} and {@code Affinity}.
 *
 * <p>Two halves, registered independently:
 *
 * <ul>
 *   <li><b>The chart</b> - each attacking type says what it is strong and weak against.</li>
 *   <li><b>Defending types</b> - each mob says which types it IS, by {@code aiKey} or
 *       entity type id.</li>
 * </ul>
 *
 * <p>Nothing here does anything until an attack actually carries a type. A mod that
 * registers no attack types pays a single map lookup that misses, which is why this can
 * sit in the damage path unconditionally.
 *
 * @since 0.3.9
 */
public final class AttackTypeRegistry {

    private AttackTypeRegistry() {}

    private static final Map<String, AttackTypeEntry> TYPES = new LinkedHashMap<>();
    /** Mob key ({@code aiKey} preferred, entity type id as fallback) to the types it IS. */
    private static final Map<String, List<String>> DEFENDING = new HashMap<>();
    /** Mob key to the type its ordinary attacks carry when it does not say otherwise. */
    private static final Map<String, String> DEFAULT_ATTACK = new HashMap<>();
    /**
     * Supplies the types the PLAYER defends as. A function rather than a fixed list because
     * a player's typing is usually derived from something that changes mid-run - which
     * creature is out, what they are wearing - and a mod that wants a constant can return one.
     */
    private static java.util.function.Function<
        net.minecraft.server.network.ServerPlayerEntity, List<String>> PLAYER_DEFENDING = null;

    // ── Registration ─────────────────────────────────────────────────────────

    /** Register an attacking type and its chart. Re-registering the same id replaces it,
     *  which is how an addon overrides a built-in or another addon's entry. */
    public static void register(AttackTypeEntry entry) {
        if (entry == null) return;
        TYPES.put(entry.id(), entry);
    }

    /**
     * Declare which types a mob defends as.
     *
     * <p>{@code mobKey} is matched the same way spawn customizers are: against the
     * combatant's {@code aiKey} first, its entity type id second. That ordering is what
     * lets a mod ship one entity type for hundreds of creatures and still give each its
     * own typing.
     *
     * <p>Order does not matter. Passing none clears the entry.
     */
    public static void setDefendingTypes(String mobKey, String... typeIds) {
        if (mobKey == null || mobKey.isBlank()) return;
        if (typeIds == null || typeIds.length == 0) {
            DEFENDING.remove(mobKey);
            return;
        }
        DEFENDING.put(mobKey, List.of(typeIds));
    }

    /**
     * The type a mob's ordinary attacks carry.
     *
     * <p>Without this, only the player's weapons could be typed and every enemy would hit
     * untyped - half of a type chart doing nothing. Set it once per creature and its melee,
     * ranged and ability damage are all typed; an AI that wants a specific move to differ
     * overrides it per action with {@code CombatEntity.setPendingAttackType}.
     */
    public static void setDefaultAttackType(String mobKey, String typeId) {
        if (mobKey == null || mobKey.isBlank()) return;
        if (typeId == null) {
            DEFAULT_ATTACK.remove(mobKey);
            return;
        }
        DEFAULT_ATTACK.put(mobKey, typeId);
    }

    /**
     * Declare how to work out what types the player defends as.
     *
     * <p>Called on every hit the player takes, so keep it cheap. Returning null or an empty
     * list means untyped, which is the default and makes incoming damage behave exactly as
     * it did before typings existed.
     */
    public static void setPlayerDefendingTypesProvider(
            java.util.function.Function<net.minecraft.server.network.ServerPlayerEntity,
                                        List<String>> provider) {
        PLAYER_DEFENDING = provider;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /** The type this mob's attacks carry by default, or null when it has none. */
    public static String defaultAttackTypeOf(String aiKey, String entityTypeId) {
        if (DEFAULT_ATTACK.isEmpty()) return null;
        String t = aiKey != null ? DEFAULT_ATTACK.get(aiKey) : null;
        if (t == null && entityTypeId != null) t = DEFAULT_ATTACK.get(entityTypeId);
        return t;
    }

    /** The types the player defends as, or empty when nothing was registered. */
    public static List<String> playerDefendingTypes(
            net.minecraft.server.network.ServerPlayerEntity player) {
        if (PLAYER_DEFENDING == null || player == null) return List.of();
        try {
            List<String> t = PLAYER_DEFENDING.apply(player);
            return t == null ? List.of() : t;
        } catch (Throwable ex) {
            CrafticsMod.LOGGER.error("Player defending-type provider threw; treating as untyped", ex);
            return List.of();
        }
    }

    /**
     * The multiplier for an attack landing on a defender described by an explicit type list.
     * The list-taking form exists for the player, who has no {@code aiKey} to look up.
     */
    public static double multiplierForTypes(String attackingTypeId, List<String> defendingTypes) {
        if (attackingTypeId == null || TYPES.isEmpty()
                || defendingTypes == null || defendingTypes.isEmpty()) {
            return 1.0;
        }
        AttackTypeEntry attacking = TYPES.get(attackingTypeId);
        if (attacking == null) return 1.0;
        double mult = 1.0;
        for (String d : defendingTypes) {
            mult *= attacking.multiplierAgainst(d);
            if (mult == 0.0) return 0.0;
        }
        return mult;
    }


    public static AttackTypeEntry getOrNull(String typeId) {
        return typeId == null ? null : TYPES.get(typeId);
    }

    public static boolean isRegistered(String typeId) {
        return typeId != null && TYPES.containsKey(typeId);
    }

    public static Collection<AttackTypeEntry> getAll() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /** The types this mob defends as, or empty when it has none. */
    public static List<String> defendingTypesOf(String aiKey, String entityTypeId) {
        if (DEFENDING.isEmpty()) return List.of();
        List<String> t = aiKey != null ? DEFENDING.get(aiKey) : null;
        if (t == null && entityTypeId != null) t = DEFENDING.get(entityTypeId);
        return t == null ? List.of() : t;
    }

    /**
     * The damage multiplier for {@code attackingTypeId} landing on this defender.
     *
     * <p>Multiplied through every type the defender has, so strong-against-one and
     * weak-against-the-other cancels to 1.0 - the standard dual-type rule. Returns
     * {@code 1.0} whenever the attack is untyped, the type is unregistered, or the
     * defender declares no types, so an untyped mod and an untyped attack both behave
     * exactly as they did before this existed.
     */
    public static double multiplierFor(String attackingTypeId, String defenderAiKey,
                                       String defenderEntityTypeId) {
        if (attackingTypeId == null || TYPES.isEmpty() || DEFENDING.isEmpty()) return 1.0;
        AttackTypeEntry attacking = TYPES.get(attackingTypeId);
        if (attacking == null) return 1.0;
        List<String> defending = defendingTypesOf(defenderAiKey, defenderEntityTypeId);
        if (defending.isEmpty()) return 1.0;
        double mult = 1.0;
        for (String d : defending) {
            mult *= attacking.multiplierAgainst(d);
            // Immunity is absorbing: nothing later in the list can bring it back above
            // zero, and bailing here keeps a 0 from being multiplied into a rounding
            // artifact by a subsequent 2.0.
            if (mult == 0.0) return 0.0;
        }
        return mult;
    }

    /**
     * A player-facing description of a multiplier, or null when it is an ordinary hit.
     * Used for the combat feedback line so effectiveness is legible without a wiki.
     */
    public static String describeMultiplier(double multiplier) {
        if (multiplier == 0.0) return "§7It has no effect...";
        if (multiplier >= 2.0) return "§aIt's super effective!";
        if (multiplier > 1.0) return "§aIt's effective.";
        if (multiplier <= 0.5) return "§cIt's not very effective...";
        if (multiplier < 1.0) return "§cIt's resisted.";
        return null;
    }

    /** Clear every registration. Test hook. */
    public static void clear() {
        TYPES.clear();
        DEFENDING.clear();
        DEFAULT_ATTACK.clear();
        PLAYER_DEFENDING = null;
    }

    /** Log a one-line summary at startup so an addon author can see its chart loaded. */
    public static void logSummary() {
        if (TYPES.isEmpty()) return;
        CrafticsMod.LOGGER.info("[Craftics] {} attack type(s) registered, {} mob typing(s)",
            TYPES.size(), DEFENDING.size());
    }
}
