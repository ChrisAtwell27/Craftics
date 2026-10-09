package com.crackedgames.craftics.combat.shadow;

/**
 * What a Shadow makes of the things a player throws and the pottery sherds a player casts.
 *
 * <p>The real items cannot be reused. A snowball's code and a sherd's spell are both written
 * as "the player does this to that monster", and there is no turning them round. So each one
 * is described a second time here, as plainly as it can be: how wide it lands and the one
 * thing it does besides damage. What it costs and how far it reaches are passed in from the
 * real item every time, so a sherd the Scribe inscribed keeps its inscription.
 *
 * <p>Three things are softened on purpose, because they are fine against a monster and
 * miserable against a person:
 * <ul>
 *   <li>Nothing stuns. Entangle slows instead. Stunning a player is taking their turn.</li>
 *   <li>Nothing executes. Death Mark withers instead of killing outright under a threshold.</li>
 *   <li>Damage comes from {@link ShadowBalance} like everything else a Shadow does, not from
 *       the item's own numbers.</li>
 * </ul>
 *
 * <p>Ids rather than items, and no Minecraft types, so {@code ShadowSpellsTest} can read it.
 * When a new sherd or throwable is added to the game it does nothing in a Shadow's hands until
 * it gets a line here.
 */
public final class ShadowSpells {

    private ShadowSpells() {}

    /** How far a Shadow throws things. Matches the player's own throwing reach. */
    public static final int THROW_RANGE = 4;
    /** The most times a Shadow will throw one kind of thing in a fight. */
    public static final int MAX_THROWS = 3;
    /** An ender pearl goes wherever it is thrown. */
    private static final int PEARL_RANGE = 99;

    private static final String SHERD_SUFFIX = "_pottery_sherd";

    /**
     * The Shadow's version of a pottery sherd, or null for one it cannot use.
     *
     * @param apCost what the real sherd costs to cast, inscriptions included
     * @param range  how far the real sherd reaches, inscriptions included
     */
    public static ShadowItem forSherd(String itemId, int slot, int apCost, int range) {
        if (itemId == null || !itemId.startsWith("minecraft:") || !itemId.endsWith(SHERD_SUFFIX)) return null;
        String name = itemId.substring("minecraft:".length(), itemId.length() - SHERD_SUFFIX.length());
        return switch (name) {
            // Attacks: aimed at the target.
            case "scrape" -> attack(slot, itemId, "corrode", apCost, range).withRider("VULNERABLE", 0, 3);
            case "angler" -> attack(slot, itemId, "riptide_hook", apCost, range)
                .withRider("SOAKED", 0, 2).withKnockback(-2);
            case "heartbreak" -> attack(slot, itemId, "shatter_will", apCost, range).withRider("WEAKNESS", 1, 3);
            case "sheaf" -> attack(slot, itemId, "entangle", apCost, range).withRadius(1).withRider("SLOWNESS", 1, 2);
            case "miner" -> attack(slot, itemId, "earthen_spike", apCost, range);
            case "blade" -> attack(slot, itemId, "phantom_slash", apCost, range).withRadius(1);
            case "burn" -> attack(slot, itemId, "immolation", apCost, range).withRadius(1).withRider("BURNING", 1, 3);
            case "snort" -> attack(slot, itemId, "tectonic_charge", apCost, range).withKnockback(3);
            case "mourner" -> attack(slot, itemId, "soul_drain", apCost, range).withLifesteal();
            case "guster" -> attack(slot, itemId, "chain_lightning", apCost, range).withRadius(2);
            case "skull" -> attack(slot, itemId, "death_mark", apCost, range).withRider("WITHER", 1, 4);
            // Goes off around the Shadow itself.
            case "flow" -> attack(slot, itemId, "tidal_surge", apCost, range)
                .withRadius(2).centredOnSelf().withRider("SOAKED", 0, 2).withKnockback(2);
            // Heals and buffs: cast on itself.
            case "heart" -> ShadowItem.heal(slot, itemId, apCost, 15);
            case "plenty" -> ShadowItem.heal(slot, itemId, apCost, 10);
            case "shelter" -> ShadowItem.buff(slot, itemId, apCost, "RESISTANCE", 2, 5);
            case "arms_up" -> ShadowItem.buff(slot, itemId, apCost, "STRENGTH", 2, 4);
            case "brewer" -> ShadowItem.buff(slot, itemId, apCost, "SPEED", 1, 4);
            case "prize" -> ShadowItem.buff(slot, itemId, apCost, "STRENGTH", 1, 2);
            // Movement and company.
            case "explorer" -> ShadowItem.blink(slot, itemId, "phase_step", apCost, range);
            case "archer" -> ShadowItem.summon(slot, itemId, "seeker_vexes", apCost, 2);
            // Guardian Spirit and Petsplosion work through the caster's own pets, and Hex Trap
            // waits for an enemy to walk onto it. None of them has a mirror image.
            default -> null;
        };
    }

    /**
     * The Shadow's version of something a player throws, or null if it is not thrown.
     *
     * @param apCost what the real item costs to use
     * @param count  how many the player is carrying
     */
    public static ShadowItem forThrowable(String itemId, int slot, int apCost, int count) {
        if (itemId == null) return null;
        int throwsLeft = Math.max(1, Math.min(MAX_THROWS, count));
        return switch (itemId) {
            case "minecraft:snowball" -> thrown(slot, itemId, "a_snowball", apCost, 50, throwsLeft).withKnockback(1);
            case "minecraft:egg" -> thrown(slot, itemId, "an_egg", apCost, 50, throwsLeft);
            case "minecraft:brick" -> thrown(slot, itemId, "a_brick", apCost, 80, throwsLeft);
            case "minecraft:fire_charge" -> thrown(slot, itemId, "a_fire_charge", apCost, 60, throwsLeft)
                .withRider("BURNING", 0, 2);
            case "minecraft:wind_charge" -> thrown(slot, itemId, "a_wind_charge", apCost, 50, throwsLeft)
                .withKnockback(3);
            case "minecraft:tnt" -> thrown(slot, itemId, "tnt", apCost, 100, Math.min(2, throwsLeft)).withRadius(1);
            case "minecraft:ender_pearl" -> ShadowItem.blink(slot, itemId, "an_ender_pearl", apCost, PEARL_RANGE);
            default -> null;
        };
    }

    private static ShadowItem attack(int slot, String itemId, String label, int apCost, int range) {
        return ShadowItem.spell(slot, itemId, label, apCost, Math.max(1, range), 100);
    }

    private static ShadowItem thrown(int slot, String itemId, String label, int apCost, int potency, int charges) {
        return ShadowItem.spell(slot, itemId, label, apCost, THROW_RANGE, potency).withCharges(charges);
    }
}
