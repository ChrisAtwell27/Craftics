package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.MobTrait.Polarity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every mob trait, and the one place that decides which mob carries which.
 *
 * <p>A mob's list comes from two sources, merged:
 * <ul>
 *   <li><b>Derived.</b> Traits that restate a flag or table the fight already consults -
 *       hazard immunity, stun immunity, the undead list, the footprint. These are read off
 *       the mob, so they are right by construction and need no upkeep.</li>
 *   <li><b>Declared.</b> Traits that describe what a mob's AI or on-hit switch does, which no
 *       flag records - teleporting, charging, poisoning on hit. These live in the table at the
 *       bottom of this file, keyed by entity type id or by boss AI key
 *       ({@code "boss:<biome>"}), and compat modules add their own with {@link #declare}.</li>
 * </ul>
 *
 * <p>Registration order is display order: a trait sits in the same place on every mob that
 * has it, which is what lets a player find "Undead" at a glance instead of reading the row.
 *
 * <p>Kept free of Minecraft imports so the resolution can be unit-tested without a bootstrap.
 * The two facts that do need a live game (is this entity type fire-immune, how big is this
 * boss) are passed in through {@link Facts} by whoever has them.
 */
public final class MobTraits {

    private MobTraits() {}

    /** id -> trait, in registration (= display) order. */
    private static final Map<String, MobTrait> REGISTRY = new LinkedHashMap<>();
    /** Entity type id or AI key -> ids of the traits declared for it. */
    private static final Map<String, Set<String>> DECLARED = new HashMap<>();

    // ── Built-ins. Order here is the order pills are drawn in. ──
    public static final MobTrait INFALLIBLE = builtin("infallible", "Infallible",
        "Cannot be knocked into instakill tiles like the void or lava.", Polarity.POSITIVE);
    public static final MobTrait INDOMITABLE = builtin("indomitable", "Indomitable",
        "Resists half of all stuns.", Polarity.POSITIVE);
    public static final MobTrait THICK_SKULLED = builtin("thick_skulled", "Thick-skulled",
        "Cannot be stunned.", Polarity.POSITIVE);
    public static final MobTrait IMMOVABLE = builtin("immovable", "Immovable",
        "Cannot be knocked back or moved.", Polarity.POSITIVE);
    public static final MobTrait INVULNERABLE = builtin("invulnerable", "Invulnerable",
        "Cannot be attacked through conventional means.",
        Polarity.POSITIVE);
    public static final MobTrait FLAMEBORNE = builtin("flameborne", "Flameborne",
        "Immune to fire.", Polarity.POSITIVE);
    public static final MobTrait FORCEFUL = builtin("forceful", "Forceful",
        "Attacks have knockback.", Polarity.POSITIVE);
    public static final MobTrait ETHEREAL = builtin("ethereal", "Ethereal",
        "Can teleport.", Polarity.POSITIVE);
    public static final MobTrait ACROBATIC = builtin("acrobatic", "Acrobatic",
        "Can leap off screen when there are no targets within range.", Polarity.POSITIVE);
    public static final MobTrait BERZERKER = builtin("berzerker", "Berzerker",
        "Charges in a straight line, like a rook.", Polarity.POSITIVE);
    public static final MobTrait TOXIC = builtin("toxic", "Toxic",
        "Attacks apply Poison.", Polarity.POSITIVE);
    public static final MobTrait DECAYED = builtin("decayed", "Decayed",
        "Attacks apply Wither.", Polarity.POSITIVE);
    public static final MobTrait SUPPRESSOR = builtin("suppressor", "Suppressor",
        "Attacks apply Slowness.", Polarity.POSITIVE);
    public static final MobTrait LARGE = builtin("large", "Large",
        "Takes up multiple tiles.", Polarity.NEUTRAL);
    public static final MobTrait INANIMATE = builtin("inanimate", "Inanimate",
        "Is nonliving.", Polarity.NEUTRAL);
    public static final MobTrait UNDEAD = builtin("undead", "Undead",
        "Takes more damage from anti-undead attacks like Smite.", Polarity.NEGATIVE);
    public static final MobTrait ARTHROPOD = builtin("arthropod", "Arthropod",
        "Bane of Arthropods poisons and slows it.", Polarity.NEGATIVE);
    public static final MobTrait MARTYR = builtin("martyr", "Martyr",
        "Dies during its attack.", Polarity.NEGATIVE);

    private static MobTrait builtin(String id, String name, String description, Polarity polarity) {
        return register(new MobTrait(id, name, description, polarity));
    }

    // ── Registry ──

    /**
     * Add a trait. Re-registering an id replaces the definition but keeps its place in the
     * display order, so an addon can reword a built-in without shuffling everyone's pills.
     */
    public static MobTrait register(MobTrait trait) {
        if (trait == null) throw new IllegalArgumentException("trait is null");
        REGISTRY.put(trait.id(), trait);
        return trait;
    }

    /** The trait registered under {@code id}, or null. */
    public static MobTrait get(String id) {
        return id == null ? null : REGISTRY.get(id);
    }

    /** Every registered trait, in display order. */
    public static Collection<MobTrait> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * Declare that a mob carries these traits.
     *
     * @param typeOrAiKey an entity type id ({@code "minecraft:husk"}) or an AI key
     *                    ({@code "boss:mountain"}). A mob collects the declarations for both
     *                    its entity type and its AI key.
     */
    public static void declare(String typeOrAiKey, MobTrait... traits) {
        if (typeOrAiKey == null || traits == null) return;
        Set<String> set = DECLARED.computeIfAbsent(typeOrAiKey, k -> new HashSet<>());
        for (MobTrait t : traits) {
            if (t != null) set.add(t.id());
        }
    }

    /** Every key something has been declared for. Feeds the bestiary catalog. */
    public static Set<String> declaredKeys() {
        return Collections.unmodifiableSet(DECLARED.keySet());
    }

    // ── Resolution ──

    /**
     * What is known about one mob. The live path fills this from a {@link CombatEntity}; the
     * bestiary path, which has no entity to ask, fills it from the type alone.
     *
     * @param aiKey        the mob's AI key; equal to {@code entityTypeId} unless it is a boss
     *                     or a registered enemy that borrows another mob's AI
     * @param ally         true for a unit fighting on the player's side. Allies run their own
     *                     turn logic, which applies none of the hostile on-hit effects and
     *                     none of the hostile movement tricks, so declared traits are skipped.
     * @param stunImmune   {@link CombatEntity#isStunImmune()}
     * @param immovable    cannot be displaced at all
     * @param invulnerable cannot be damaged directly (a Creaking with a living heart)
     */
    public record Facts(String entityTypeId, String aiKey, boolean boss, boolean ally,
                        boolean hazardImmune, boolean stunImmune, boolean immovable,
                        boolean invulnerable, boolean fireImmune, boolean large,
                        boolean inanimate) {}

    /** The traits a mob with these facts carries, in display order. */
    public static List<MobTrait> resolve(Facts f) {
        Set<String> ids = new HashSet<>();
        String type = f.entityTypeId();

        if (f.hazardImmune()) ids.add(INFALLIBLE.id());
        // Stun immunity outranks the boss coin flip in CombatEntity.setStunned, so a boss
        // that is fully immune is not also described as resisting half.
        if (f.stunImmune()) ids.add(THICK_SKULLED.id());
        else if (f.boss()) ids.add(INDOMITABLE.id());
        if (f.immovable()) ids.add(IMMOVABLE.id());
        if (f.invulnerable()) ids.add(INVULNERABLE.id());
        if (f.fireImmune()) ids.add(FLAMEBORNE.id());
        if (f.large()) ids.add(LARGE.id());
        if (f.inanimate()) ids.add(INANIMATE.id());
        if (type != null) {
            if (PlayerCombatStats.isUndead(type)) ids.add(UNDEAD.id());
            if (PlayerCombatStats.isArthropod(type)) ids.add(ARTHROPOD.id());
        }

        if (!f.ally()) {
            // Jungle-themed mobs poison on hit through MobThemeTags, which compat modules
            // already populate - so their variants are Toxic without declaring anything.
            if (MobThemeTags.isJungle(type)) ids.add(TOXIC.id());
            addDeclared(ids, type);
            if (f.aiKey() != null && !f.aiKey().equals(type)) addDeclared(ids, f.aiKey());
        }

        List<MobTrait> out = new ArrayList<>();
        for (MobTrait t : REGISTRY.values()) {
            if (ids.contains(t.id())) out.add(t);
        }
        return out;
    }

    private static void addDeclared(Set<String> ids, String key) {
        if (key == null) return;
        Set<String> declared = DECLARED.get(key);
        if (declared != null) ids.addAll(declared);
    }

    /** The traits of a mob in a fight, read off its live state. */
    public static List<MobTrait> forEntity(CombatEntity e) {
        if (e == null) return List.of();
        String type = e.getEntityTypeId();
        boolean heartBound = e.getLinkedHeartId() >= 0
            && com.crackedgames.craftics.compat.palegardenbackport.PaleGardenBackportCompat
                .isCreakingEntity(type);
        // A guard a boss is holding counts too, and comes off the panel the turn it drops.
        return resolve(new Facts(type, e.getAiKey(), e.isBoss(), e.isAlly(),
            e.isHazardImmune(), e.isStunImmune(),
            e.isImmovable() || e.isBackgroundBoss(),
            heartBound || e.isDamageImmune(), e.isFireImmune(), e.isMultiTile(), e.isInertObject()));
    }

    /**
     * The traits of a mob known only by its type - the bestiary's view, where there is no
     * entity to read flags from. Everything a spawn would set is inferred the way the spawn
     * code sets it: every boss is hazard-immune, and a Creaking arrives bound to its heart.
     *
     * @param fireImmune whether the entity type is fire-immune in vanilla
     * @param large      whether it occupies more than one tile
     */
    public static List<MobTrait> forType(String entityTypeId, String aiKey, boolean boss,
                                         boolean fireImmune, boolean large) {
        String key = aiKey != null ? aiKey : entityTypeId;
        boolean stunImmune = MobResistances.isResistant(entityTypeId, key, DamageType.BLUNT);
        boolean creaking = com.crackedgames.craftics.compat.palegardenbackport
            .PaleGardenBackportCompat.isCreakingEntity(entityTypeId);
        // Immovable and Inanimate are flags on a live entity. Here they can only come from the
        // declared table, which resolve() merges in below.
        return resolve(new Facts(entityTypeId, key, boss, false,
            boss, stunImmune, false, creaking, fireImmune, large, false));
    }

    // ── Wire format ──

    /** {@code "undead,forceful"}; empty string for no traits. */
    public static String encode(Collection<MobTrait> traits) {
        if (traits == null || traits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MobTrait t : traits) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t.id());
        }
        return sb.toString();
    }

    /**
     * Inverse of {@link #encode}. Ids this side has never heard of are dropped rather than
     * shown as raw keys: a server running an addon the client lacks should lose a pill, not
     * render "addon_trait_17".
     */
    public static List<MobTrait> decode(String csv) {
        List<MobTrait> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String id : csv.split(",")) {
            MobTrait t = REGISTRY.get(id.trim());
            if (t != null && !out.contains(t)) out.add(t);
        }
        return out;
    }

    // ── Declared traits: what a mob's AI or on-hit switch does ──
    // Each line is a claim about behaviour that lives somewhere else, so each names where.
    // Add a mob here only when its own attack or AI really does the thing.
    static {
        // Forceful: the hit shoves the player.
        declare("minecraft:husk", FORCEFUL);          // applyEnemyHitEffect, 1 tile
        declare("minecraft:vindicator", FORCEFUL);    // applyEnemyHitEffect, 2 tiles
        declare("minecraft:piglin_brute", FORCEFUL);  // VindicatorAI, once enraged
        declare("minecraft:ravager", FORCEFUL);       // RavagerAI AttackWithKnockback
        declare("minecraft:hoglin", FORCEFUL);        // HoglinAI extends RavagerAI
        declare("minecraft:polar_bear", FORCEFUL);    // PolarBearAI
        declare("minecraft:goat", FORCEFUL);          // GoatAI ram, once provoked
        declare("minecraft:breeze", FORCEFUL);        // wind charge gust
        declare("minecraft:creeper", FORCEFUL);       // the blast throws you
        declare("boss:nether_wastes", FORCEFUL);      // Molten King slam
        declare("boss:nether_wastes_g1", FORCEFUL);
        declare("boss:mountain", FORCEFUL);           // Rockbreaker charge
        declare("boss:river", FORCEFUL);              // Tidecaller surge
        declare("boss:outer_end_islands", FORCEFUL);  // Void Herald push

        // Ethereal: the AI emits Teleport actions.
        declare("minecraft:enderman", ETHEREAL);
        declare("minecraft:endermite", ETHEREAL);
        declare("minecraft:shulker", ETHEREAL);
        declare("boss:forest", ETHEREAL);             // Hexweaver
        declare("boss:nether_wastes", ETHEREAL);      // Molten King
        declare("boss:nether_wastes_g1", ETHEREAL);
        declare("boss:warped_forest", ETHEREAL);      // Void Walker
        declare("boss:outer_end_islands", ETHEREAL);  // Void Herald
        declare("boss:chorus_grove", ETHEREAL);       // Chorus Mind

        // Acrobatic: the AI emits CeilingAscend.
        declare("minecraft:spider", ACROBATIC);
        declare("minecraft:cave_spider", ACROBATIC);
        declare("boss:jungle", ACROBATIC);            // Broodmother

        // Berzerker: straight-line charge.
        declare("minecraft:vindicator", BERZERKER);
        declare("minecraft:piglin_brute", BERZERKER);
        declare("minecraft:ravager", BERZERKER);
        declare("minecraft:hoglin", BERZERKER);
        declare("minecraft:goat", BERZERKER);
        declare("boss:mountain", BERZERKER);          // Rockbreaker
        declare("boss:river", BERZERKER);             // Tidecaller
        declare("boss:cave", BERZERKER);              // Hollow King
        declare("boss:crimson_forest", BERZERKER);    // Bastion Brute
        declare("boss:basalt_deltas", BERZERKER);     // The Wither

        // Toxic / Decayed / Suppressor: applyEnemyHitEffect, plus the bosses whose own
        // attack carries the effect.
        declare("minecraft:witch", TOXIC);
        declare("minecraft:bee", TOXIC);
        declare("minecraft:cave_spider", TOXIC);
        declare("minecraft:bogged", TOXIC);
        declare("boss:jungle", TOXIC);                // Broodmother venomous bite
        declare("minecraft:wither_skeleton", DECAYED);
        declare("minecraft:wither", DECAYED);
        declare("boss:basalt_deltas", DECAYED);
        declare("minecraft:stray", SUPPRESSOR);
        declare("minecraft:shulker", SUPPRESSOR);
        declare("minecraft:breeze", SUPPRESSOR);
        declare("boss:snowy", SUPPRESSOR);            // Frostbound, every hit

        // Martyr: the attack is the death.
        declare("minecraft:creeper", MARTYR);
        declare("minecraft:end_crystal", MARTYR, INANIMATE);

        // Immovable and Inanimate are flags on a live entity, so a fight needs none of these.
        // They are restated here for the bestiary, which only knows the type.
        declare("boss:dragons_nest", IMMOVABLE);      // background boss
        declare("boss:soul_sand_valley", IMMOVABLE);  // background boss
        for (String object : new String[]{
                "craftics:grave", "craftics:war_banner", "craftics:creaking_heart",
                "craftics:bee_hive", "craftics:wild_bee_hive", "craftics:decoy_stand",
                "craftics:end_crystal"}) {
            declare(object, IMMOVABLE, INANIMATE);
        }
        declare("craftics:egg_sac", INANIMATE);       // inert, but it can be shoved
    }
}
