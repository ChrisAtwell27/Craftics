package com.crackedgames.craftics.compat.immersivearmors;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.ArmorSetEntry;
import com.crackedgames.craftics.api.registry.ArmorSetRegistry;
import com.crackedgames.craftics.combat.ArmorSetEffects;
import com.crackedgames.craftics.combat.DamageType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Compatibility module for the Immersive Armors mod (Luke100000).
 *
 * <p>Immersive Armors adds ten vanilla-styled armor sets. Each is registered here with a
 * damage-type affinity and an Armor Class, so it slots into Craftics' AC/dodge system and
 * its affinity scaling exactly like a vanilla material. The Armor Class of each set tracks
 * the protection values the mod itself gives that set, so a set that is strong in vanilla
 * is strong here.
 *
 * <p>Each set also carries a signature mechanic that a flat stat line cannot express -
 * Wooden's padding against arrows, Warrior's rage as it bleeds, Prismarine's discharge.
 * Those live in {@link ArmorSetEffects} and are fired from {@code CombatManager} at each
 * effect's natural moment; this class only declares the numbers.
 *
 * <p>No compile-time dependency: every item resolves by registry id, and the module
 * silently does nothing when the mod is absent.
 */
public final class ImmersiveArmorsCompat {

    public static final String MOD_ID = "immersive_armors";
    public static final String NAMESPACE = "immersive_armors";

    /** The ten set keys, in the mod's own registry-path spelling. */
    static final String[] SETS = {
        ArmorSetEffects.WOODEN, ArmorSetEffects.BONE, ArmorSetEffects.ROBE,
        ArmorSetEffects.WITHER, ArmorSetEffects.SLIME, ArmorSetEffects.WARRIOR,
        ArmorSetEffects.STEAMPUNK, ArmorSetEffects.DIVINE,
        ArmorSetEffects.PRISMARINE, ArmorSetEffects.HEAVY,
    };

    private static boolean loaded = false;
    private static boolean registered = false;

    private ImmersiveArmorsCompat() {}

    public static boolean isLoaded() { return loaded; }
    public static boolean isRegistered() { return registered; }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /** Flag mod presence; do NOT touch the registry yet (mod load order is unspecified). */
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug("[Craftics × Immersive Armors] mod not loaded - skipping registration");
            return;
        }
        loaded = true;
    }

    /** Late-phase registration. Idempotent; silent on the retry path (called from the tooltip render). */
    public static void registerDeferred() {
        if (registered || !loaded) return;
        if (!anyArmorPresent()) return; // mod flagged loaded but its items never appeared

        // Armor Class (B) mirrors the mod's own protection curve, mapped onto Craftics'
        // vanilla scale (leather/gold 2, chainmail 3, iron 4, diamond 6, netherite 7).

        // Wooden - the starter set. Springy rather than rigid: it soaks arrows and blasts
        // but splinters, and can shatter outright when struck.
        set(ArmorSetEffects.WOODEN, DamageType.SLASHING, 2,
            "Wooden: " + pct(ArmorSetEffects.WOODEN_TYPE_RESIST)
            + " less damage from Ranged and Blunt hits; each piece has a "
            + pct(ArmorSetEffects.FRAGILE_BREAK_CHANCE) + " chance to shatter when you are hit");

        // Bone - light, and the quiver never seems to empty.
        set(ArmorSetEffects.BONE, DamageType.BLUNT, 2,
            "Bone: " + pct(ArmorSetEffects.BONE_AMMO_SAVE)
            + " chance to fire without spending an arrow; each piece has a "
            + pct(ArmorSetEffects.FRAGILE_BREAK_CHANCE) + " chance to shatter when you are hit");

        // Robe - no protection worth the name, but the spells come cheap and never fail.
        set(ArmorSetEffects.ROBE, DamageType.SPECIAL, 2,
            "Robe: pottery sherds never shatter and cost "
            + ArmorSetEffects.ROBE_SHERD_AP_DISCOUNT + " less AP (min 1)");

        // Wither - the nether-tier bone set. Better quiver, and the decay bites back.
        set(ArmorSetEffects.WITHER, DamageType.SPECIAL, 4,
            "Wither: " + pct(ArmorSetEffects.WITHER_AMMO_SAVE)
            + " chance to fire without spending an arrow; melee attackers wither for "
            + ArmorSetEffects.WITHER_RETALIATION_TURNS + " turns");

        // Slime - everything that touches it bounces, including the wearer.
        set(ArmorSetEffects.SLIME, DamageType.SPECIAL, 4,
            "Slime: attackers are knocked back " + ArmorSetEffects.SLIME_KNOCKBACK_TILES
            + " tile and so are you; your own hits knock back "
            + ArmorSetEffects.SLIME_KNOCKBACK_TILES + " tile");

        // Warrior - the more it hurts, the harder you swing.
        set(ArmorSetEffects.WARRIOR, DamageType.CLEAVING, 5,
            "Warrior: +" + ArmorSetEffects.WARRIOR_DAMAGE_PER_MISSING_HEART
            + " Cleaving damage for every heart of health you are missing");

        // Steampunk - a light frame full of gears: fast, and it sees what is coming.
        set(ArmorSetEffects.STEAMPUNK, DamageType.PHYSICAL, 4, 1,
            "Steampunk: +1 Speed; its radar paints each enemy's next route in yellow "
            + "and the tiles they will strike in red");

        // Divine - the mod's own armor deflects a hit once a minute; here, once a fight.
        set(ArmorSetEffects.DIVINE, DamageType.SPECIAL, 6,
            "Divine: the first hit you take each battle is deflected entirely");

        // Prismarine - a guardian's shell, and a guardian's beam.
        set(ArmorSetEffects.PRISMARINE, DamageType.WATER, 6,
            "Prismarine: at the start of your turn, every enemy within "
            + ArmorSetEffects.PRISMARINE_RADIUS + " tiles takes "
            + ArmorSetEffects.PRISMARINE_DAMAGE + " Water damage and is Soaked");

        // Heavy - netherite-grade plate. Nothing shifts it, and it shifts slowly.
        set(ArmorSetEffects.HEAVY, DamageType.BLUNT, 7, -1,
            "Heavy: immune to knockback, but -1 Speed");

        registered = true;
        CrafticsMod.LOGGER.info("[Craftics × Immersive Armors] enabled - registered {} armor sets", SETS.length);
    }

    private static void set(String key, DamageType affinity, int armorClass, String description) {
        set(key, affinity, armorClass, 0, description);
    }

    private static void set(String key, DamageType affinity, int armorClass, int speedBonus,
                            String description) {
        ArmorSetRegistry.register(ArmorSetEntry.builder(key)
            .damageBonus(affinity, 1)
            .armorClass(armorClass)
            .speedBonus(speedBonus)
            .description(description)
            .build());
    }

    /** Render a 0..1 chance as a whole-percent string for a tooltip line. */
    private static String pct(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    // =========================================================================
    // Item gating
    // =========================================================================

    /** Look up one of the mod's items by path, or {@code null} if it isn't present. */
    static Item lookupItem(String path) {
        Identifier id = Identifier.of(NAMESPACE, path);
        if (!Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }

    /** True once at least one of the mod's armor pieces exists in the item registry. */
    private static boolean anyArmorPresent() {
        for (String key : SETS) {
            if (lookupItem(key + "_helmet") != null) return true;
        }
        return false;
    }

    /** True if {@code item} is an armor piece from Immersive Armors that Craftics registered. */
    public static boolean isImmersiveArmor(Item item) {
        if (item == null || !loaded) return false;
        Identifier id = Registries.ITEM.getId(item);
        if (!NAMESPACE.equals(id.getNamespace())) return false;
        String key = com.crackedgames.craftics.combat.ArmorClassTable.armorSetKeyOf(item);
        return key != null && ArmorSetRegistry.isRegistered(key);
    }
}
