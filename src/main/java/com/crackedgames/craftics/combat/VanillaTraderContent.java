package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.registry.TraderCategoryRegistry;

/**
 * Registers the eight built-in villager trader categories and their stock. Called once from
 * {@code VanillaContent}, mirroring {@code VanillaBarterContent} on the piglin side.
 *
 * <p>REGISTRATION ORDER IS LOAD-BEARING. The Trading Hall seats booths in registry order, so the
 * order here is the order of the stalls, and it must stay stable across versions or every
 * player's hall rearranges itself. Append new traders at the END; never reorder these.
 *
 * @since 0.2.10
 */
public final class VanillaTraderContent {

    public static final String WEAPONSMITH      = "craftics:weaponsmith";
    public static final String ARMORER          = "craftics:armorer";
    public static final String PROVISIONER      = "craftics:provisioner";
    public static final String ALCHEMIST        = "craftics:alchemist";
    public static final String SUPPLIER         = "craftics:supplier";
    public static final String DECORATOR        = "craftics:decorator";
    public static final String CRAFTSMAN        = "craftics:craftsman";
    public static final String CURIOSITY_DEALER = "craftics:curiosity_dealer";

    private VanillaTraderContent() {}

    public static void register() {
        // minBiomeTier 0: every vanilla trader is available from the first biome. Addons can gate
        // theirs later with a higher tier.
        TraderCategoryRegistry.register(
            new TraderCategory(WEAPONSMITH, "Weaponsmith", "§c⚔", 0),
            TraderSystem::buildWeaponTrades);
        TraderCategoryRegistry.register(
            new TraderCategory(ARMORER, "Armorer", "§9⛨", 0),
            TraderSystem::buildArmorTrades);
        TraderCategoryRegistry.register(
            new TraderCategory(PROVISIONER, "Provisioner", "§a⚘", 0),
            TraderSystem::buildFoodTrades);
        TraderCategoryRegistry.register(
            new TraderCategory(ALCHEMIST, "Alchemist", "§d⚗", 0),
            TraderSystem::buildAlchemistTrades);
        TraderCategoryRegistry.register(
            new TraderCategory(SUPPLIER, "Supplier", "§e⛏", 0),
            TraderSystem::buildSupplierTrades);
        TraderCategoryRegistry.register(
            new TraderCategory(DECORATOR, "Decorator", "§b⌂", 0),
            TraderSystem::buildDecoratorTrades);
        TraderCategoryRegistry.register(
            new TraderCategory(CRAFTSMAN, "Craftsman", "§6⚒", 0),
            TraderSystem::buildCraftsmanTrades);
        TraderCategoryRegistry.register(
            new TraderCategory(CURIOSITY_DEALER, "Curiosity Dealer", "§5✦", 0),
            TraderSystem::buildCuriosityTrades);
    }
}
