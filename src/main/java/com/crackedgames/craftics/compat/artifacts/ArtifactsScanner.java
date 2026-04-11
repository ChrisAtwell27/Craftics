package com.crackedgames.craftics.compat.artifacts;

import com.crackedgames.craftics.api.EquipmentScanner;
import com.crackedgames.craftics.api.StatModifiers;
import com.crackedgames.craftics.combat.TrimEffects.Bonus;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Reads the player's equipped Artifacts items and translates them into Craftics
 * stat bonuses + combat effect handlers.
 * <p>
 * Called via {@code EquipmentScannerRegistry.scanAll} from {@code TrimEffects.scan},
 * which Craftics re-runs on combat start and at the start of every player turn.
 */
public final class ArtifactsScanner implements EquipmentScanner {

    @Override
    public StatModifiers scan(ServerPlayerEntity player) {
        StatModifiers mods = new StatModifiers();
        Set<String> seen = new HashSet<>(); // dedupe duplicates of the same artifact

        ArtifactsReflect.iterateEquipment(player, (ItemStack stack) -> {
            if (stack == null || stack.isEmpty()) return;
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (id == null || !ArtifactsCompat.MOD_ID.equals(id.getNamespace())) return;
            String path = id.getPath();
            if (!seen.add(path)) return; // ignore duplicates of the same artifact
            applyArtifact(mods, path);
        });

        if (DEBUG_LOG_SCAN_RESULT) {
            com.crackedgames.craftics.CrafticsMod.LOGGER.info(
                "[Craftics × Artifacts] Scanner found {} unique artifact(s) for {}: {}",
                seen.size(), player.getName().getString(), seen);
        }
        return mods;
    }

    /** Toggle via {@code /craftics force_event artifacts:abandoned_campsite} cycle — off after testing. */
    private static final boolean DEBUG_LOG_SCAN_RESULT = true;

    /** Stat bonuses + combat effect handler for one equipped artifact id (e.g. "cowboy_hat"). */
    private static void applyArtifact(StatModifiers mods, String id) {
        switch (id) {
            // ===== Head slot =====
            case "night_vision_goggles" -> mods.add(Bonus.ATTACK_RANGE, 1);
            case "superstitious_hat" ->
                mods.addCombatEffect("Superstitious Hat", new ArtifactEffects.SuperstitiousHat());
            case "villager_hat" ->
                mods.addCombatEffect("Villager Hat", new ArtifactEffects.VillagerHat());
            case "cowboy_hat" ->
                mods.addCombatEffect("Cowboy Hat", new ArtifactEffects.CowboyHat());
            case "anglers_hat" ->
                mods.addCombatEffect("Anglers Hat", new ArtifactEffects.AnglersHat());
            case "snorkel" -> {
                mods.add(Bonus.WATER_POWER, 1);
                mods.addCombatEffect("Snorkel", new ArtifactEffects.Snorkel());
            }
            case "plastic_drinking_hat" ->
                mods.addCombatEffect("Plastic Drinking Hat", new ArtifactEffects.PlasticDrinkingHat());
            case "novelty_drinking_hat" ->
                mods.addCombatEffect("Novelty Drinking Hat", new ArtifactEffects.NoveltyDrinkingHat());

            // ===== Necklace slot =====
            case "cross_necklace" ->
                mods.addCombatEffect("Cross Necklace", new ArtifactEffects.CrossNecklace());
            case "flame_pendant" ->
                mods.addCombatEffect("Flame Pendant", new ArtifactEffects.FlamePendant());
            case "thorn_pendant" ->
                mods.addCombatEffect("Thorn Pendant", new ArtifactEffects.ThornPendant());
            case "panic_necklace" ->
                mods.addCombatEffect("Panic Necklace", new ArtifactEffects.PanicNecklace());
            case "shock_pendant" ->
                mods.addCombatEffect("Shock Pendant", new ArtifactEffects.ShockPendant());
            case "charm_of_sinking" -> {
                mods.add(Bonus.DEFENSE, 1);
                mods.addCombatEffect("Charm of Sinking", new ArtifactEffects.CharmOfSinking());
            }
            case "charm_of_shrinking" ->
                mods.addCombatEffect("Charm of Shrinking", new ArtifactEffects.CharmOfShrinking());
            case "scarf_of_invisibility" -> {
                mods.add(Bonus.STEALTH_RANGE, 1);
                mods.addCombatEffect("Scarf of Invisibility", new ArtifactEffects.ScarfOfInvisibility());
            }
            case "lucky_scarf" ->
                mods.addCombatEffect("Lucky Scarf", new ArtifactEffects.LuckyScarf());

            // ===== Ring slot =====
            case "onion_ring" -> {
                mods.add(Bonus.REGEN, 1);
                mods.add(Bonus.MAX_HP, 1);
            }
            case "golden_hook" ->
                mods.addCombatEffect("Golden Hook", new ArtifactEffects.GoldenHook());
            case "pickaxe_heater" ->
                mods.addCombatEffect("Pickaxe Heater", new ArtifactEffects.PickaxeHeater());
            case "withered_bracelet" -> {
                mods.add(Bonus.ARMOR_PEN, 1);
                mods.addCombatEffect("Withered Bracelet", new ArtifactEffects.WitheredBracelet());
            }

            // ===== Hand slot =====
            case "digging_claws" ->
                mods.addCombatEffect("Digging Claws", new ArtifactEffects.DiggingClaws());
            case "feral_claws" ->
                mods.addCombatEffect("Feral Claws", new ArtifactEffects.FeralClaws());
            case "power_glove" -> mods.add(Bonus.MELEE_POWER, 3);
            case "fire_gauntlet" ->
                mods.addCombatEffect("Fire Gauntlet", new ArtifactEffects.FireGauntlet());
            case "pocket_piston" ->
                mods.addCombatEffect("Pocket Piston", new ArtifactEffects.PocketPiston());
            case "vampiric_glove" ->
                mods.addCombatEffect("Vampiric Glove", new ArtifactEffects.VampiricGlove());

            // ===== Belt slot =====
            case "crystal_heart" -> mods.add(Bonus.MAX_HP, 6);
            case "helium_flamingo" ->
                mods.addCombatEffect("Helium Flamingo", new ArtifactEffects.HeliumFlamingo());
            case "chorus_totem" ->
                mods.addCombatEffect("Chorus Totem", new ArtifactEffects.ChorusTotem());
            case "obsidian_skull" ->
                mods.addCombatEffect("Obsidian Skull", new ArtifactEffects.ObsidianSkull());
            case "cloud_in_a_bottle" -> {
                mods.add(Bonus.SPEED, 2);
                mods.addCombatEffect("Cloud in a Bottle", new ArtifactEffects.CloudInABottle());
            }
            case "antidote_vessel" ->
                mods.addCombatEffect("Antidote Vessel", new ArtifactEffects.AntidoteVessel());
            case "universal_attractor" ->
                mods.addCombatEffect("Universal Attractor", new ArtifactEffects.UniversalAttractor());
            case "warp_drive" -> {
                // Warp Drive has no per-event combat handler — it's activated by the
                // /craftics warp command, which calls CombatManager.armWarpDrive(). The
                // command itself enforces "must be equipped" via ArtifactsCompat.playerHasArtifact.
                // No-op here so the case is recognized rather than falling through to default.
            }
            case "everlasting_beef", "eternal_steak" -> {
                // Non-consuming food: handled inside ItemUseHandler.useFood by id check.
                // No-op here.
            }

            // ===== Misc slot =====
            case "umbrella" ->
                mods.addCombatEffect("Umbrella", new ArtifactEffects.Umbrella());

            // ===== Feet slot =====
            case "bunny_hoppers" -> {
                mods.add(Bonus.SPEED, 1);
                mods.addCombatEffect("Bunny Hoppers", new ArtifactEffects.BunnyHoppers());
            }
            case "kitty_slippers" -> mods.add(Bonus.STEALTH_RANGE, 2);
            case "running_shoes" -> mods.add(Bonus.SPEED, 3);
            case "aqua_dashers" ->
                mods.addCombatEffect("Aqua Dashers", new ArtifactEffects.AquaDashers());
            case "rooted_boots" ->
                mods.addCombatEffect("Rooted Boots", new ArtifactEffects.RootedBoots());
            case "snowshoes" ->
                mods.addCombatEffect("Snowshoes", new ArtifactEffects.Snowshoes());
            case "steadfast_spikes" ->
                mods.addCombatEffect("Steadfast Spikes", new ArtifactEffects.SteadfastSpikes());
            case "flippers" -> {
                mods.add(Bonus.WATER_POWER, 2);
                mods.add(Bonus.SPEED, 1);
                mods.addCombatEffect("Flippers", new ArtifactEffects.Flippers());
            }
            case "strider_shoes" -> {
                mods.add(Bonus.SPEED, 1);
                mods.addCombatEffect("Strider Shoes", new ArtifactEffects.StriderShoes());
            }
            case "whoopee_cushion" ->
                mods.addCombatEffect("Whoopee Cushion", new ArtifactEffects.WhoopeeCushion());

            default -> {
                // Unknown / unsupported artifact (e.g. mimic_spawn_egg) — ignore.
            }
        }
    }
}
