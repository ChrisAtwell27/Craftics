package com.crackedgames.craftics;

import com.crackedgames.craftics.block.ModBlocks;
import com.crackedgames.craftics.block.ModScreenHandlers;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.crackedgames.craftics.component.CrafticsComponents;
import com.crackedgames.craftics.world.CrafticsSavedData;
import com.crackedgames.craftics.world.HubRoomBuilder;
import com.crackedgames.craftics.world.VoidChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrafticsMod implements ModInitializer {
    public static final String MOD_ID = "craftics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static CrafticsConfigWrapper CONFIG;
    private static final java.util.Map<java.util.UUID, String> addonBonusCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Craftics initializing...");

        // owo-lib config
        CONFIG = CrafticsConfigWrapper.createAndLoad();
        com.crackedgames.craftics.api.VanillaWeapons.registerAll();
        com.crackedgames.craftics.api.VanillaContent.registerAll();

        // Optional mod compatibility — each call no-ops if its target mod isn't loaded.
        com.crackedgames.craftics.compat.artifacts.ArtifactsCompat.init();
        com.crackedgames.craftics.compat.creeperoverhaul.CreeperOverhaulCompat.init();
        com.crackedgames.craftics.compat.variantsandventures.VariantsAndVenturesCompat.init();
        com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.init();
        com.crackedgames.craftics.compat.palegardenbackport.PaleGardenBackportCompat.init();

        // Register custom chunk generator codec
        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of(MOD_ID, "void"), VoidChunkGenerator.CODEC);

        // Register blocks, items, screen handlers, and networking
        ModBlocks.register();
        com.crackedgames.craftics.item.ModItems.register();
        ModScreenHandlers.register();
        ModNetworking.registerServer();

        // Level-select phantom-half click routing is now handled by the
        // LevelSelectGhostBlock placed alongside the real block — no global
        // UseBlockCallback needed. The previous callback scanned all 4 horizontal
        // neighbors and hijacked clicks on any adjacent block (a chest placed
        // next to the level-select opened the level-select screen instead).

        // Dig site event: intercept right-clicks on suspicious blocks
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND) return net.minecraft.util.ActionResult.PASS;
            net.minecraft.block.Block clickedBlock = world.getBlockState(hitResult.getBlockPos()).getBlock();
            if (clickedBlock != net.minecraft.block.Blocks.SUSPICIOUS_SAND
                && clickedBlock != net.minecraft.block.Blocks.SUSPICIOUS_GRAVEL) {
                return net.minecraft.util.ActionResult.PASS;
            }
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                var cm = com.crackedgames.craftics.combat.CombatManager.get(sp);
                if (cm.isDigSitePending()) {
                    cm.handleDigSiteInteraction(sp);
                    return net.minecraft.util.ActionResult.SUCCESS;
                }
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        // Trial keys: right-clicking during combat queues a guaranteed trial
        // chamber (or ominous trial chamber) on the next level transition.
        // Consumes one key per use. Outside combat the keys do nothing.
        //? if <=1.21.1 {
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND) {
                return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
            }
            net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            net.minecraft.item.Item item = stack.getItem();
            String forced;
            String label;
            if (item == net.minecraft.item.Items.TRIAL_KEY) {
                forced = "trial_chamber";
                label = "Trial Chamber";
            } else if (item == net.minecraft.item.Items.OMINOUS_TRIAL_KEY) {
                forced = "ominous_trial";
                label = "Ominous Trial Chamber";
            } else {
                return net.minecraft.util.TypedActionResult.pass(stack);
            }

            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                var cm = com.crackedgames.craftics.combat.CombatManager.get(sp);
                if (!cm.isActive()) {
                    sp.sendMessage(net.minecraft.text.Text.literal(
                        "\u00a7eThe " + (item == net.minecraft.item.Items.OMINOUS_TRIAL_KEY ? "ominous " : "")
                            + "trial key hums faintly... use it during a fight to summon a trial chamber next."),
                        true);
                    return net.minecraft.util.TypedActionResult.pass(stack);
                }
                if (cm.getForcedNextEvent() != null) {
                    sp.sendMessage(net.minecraft.text.Text.literal(
                        "\u00a77A trial is already queued for the next transition."), true);
                    return net.minecraft.util.TypedActionResult.fail(stack);
                }
                cm.setForcedNextEvent(forced);
                stack.decrement(1);
                sp.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7d\u00a7l\u2728 " + label + " queued! \u00a7r\u00a7dIt will appear after this fight."),
                    false);
                sp.getWorld().playSound(null, sp.getBlockPos(),
                    net.minecraft.sound.SoundEvents.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
                return net.minecraft.util.TypedActionResult.success(stack, false);
            }
            return net.minecraft.util.TypedActionResult.pass(stack);
        });
        //?} else {
        /*net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND) {
                return net.minecraft.util.ActionResult.PASS;
            }
            net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            net.minecraft.item.Item item = stack.getItem();
            String forced;
            String label;
            if (item == net.minecraft.item.Items.TRIAL_KEY) {
                forced = "trial_chamber";
                label = "Trial Chamber";
            } else if (item == net.minecraft.item.Items.OMINOUS_TRIAL_KEY) {
                forced = "ominous_trial";
                label = "Ominous Trial Chamber";
            } else {
                return net.minecraft.util.ActionResult.PASS;
            }

            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                var cm = com.crackedgames.craftics.combat.CombatManager.get(sp);
                if (!cm.isActive()) {
                    sp.sendMessage(net.minecraft.text.Text.literal(
                        "\u00a7eThe " + (item == net.minecraft.item.Items.OMINOUS_TRIAL_KEY ? "ominous " : "")
                            + "trial key hums faintly... use it during a fight to summon a trial chamber next."),
                        true);
                    return net.minecraft.util.ActionResult.PASS;
                }
                if (cm.getForcedNextEvent() != null) {
                    sp.sendMessage(net.minecraft.text.Text.literal(
                        "\u00a77A trial is already queued for the next transition."), true);
                    return net.minecraft.util.ActionResult.FAIL;
                }
                cm.setForcedNextEvent(forced);
                stack.decrement(1);
                sp.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7d\u00a7l\u2728 " + label + " queued! \u00a7r\u00a7dIt will appear after this fight."),
                    false);
                sp.getWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
                return net.minecraft.util.ActionResult.SUCCESS;
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        *///?}

        // Clear static combat state between world loads (prevents leaking across saves in singleplayer)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CombatManager.clearAll();
            com.crackedgames.craftics.level.BiomeRegistry.clear();
        });

        // When the overworld loads, check if we need to build the hub
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (world.getChunkManager().getChunkGenerator() instanceof VoidChunkGenerator) {
                    CrafticsSavedData data = CrafticsSavedData.get(world);
                    if (!data.hubBuilt || data.hubVersion < HubRoomBuilder.LOBBY_VERSION) {
                        HubRoomBuilder.buildLobby(world);
                        data.hubBuilt = true;
                        data.hubVersion = HubRoomBuilder.LOBBY_VERSION;
                        data.markDirty();
                    }
                    // Set to daytime on load
                    world.setTimeOfDay(6000);
                    // Disable mob spawning in Craftics void worlds
                    world.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false, server);
                    world.getGameRules().get(net.minecraft.world.GameRules.DO_INSOMNIA).set(false, server);
                }
            }
        });

        // Teleport player to hub and set Adventure mode on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            ServerWorld overworld = server.getOverworld();
            if (overworld.getChunkManager().getChunkGenerator() instanceof VoidChunkGenerator) {
                LOGGER.info("Craftics: Teleporting player {} to hub", player.getName().getString());
                player.refreshPositionAndAngles(0, 65, 0, 0f, 0f);
                player.requestTeleport(0, 65, 0);
                player.changeGameMode(GameMode.SURVIVAL);

                // Give the starter guide once per saved player profile.
                com.crackedgames.craftics.world.CrafticsSavedData joinData =
                    com.crackedgames.craftics.world.CrafticsSavedData.get(overworld);
                com.crackedgames.craftics.world.CrafticsSavedData.PlayerData joinPd =
                    joinData.getPlayerData(player.getUuid());
                if (!joinPd.starterGuideGranted) {
                    player.giveItemStack(new net.minecraft.item.ItemStack(com.crackedgames.craftics.item.ModItems.GUIDE_BOOK));
                    joinPd.starterGuideGranted = true;
                    joinData.markDirty();
                }

                // Sync player stats to client for inventory display
                com.crackedgames.craftics.combat.PlayerProgression progression =
                    com.crackedgames.craftics.combat.PlayerProgression.get(overworld);
                com.crackedgames.craftics.combat.PlayerProgression.PlayerStats stats =
                    progression.getStats(player);
                StringBuilder statData = new StringBuilder();
                for (com.crackedgames.craftics.combat.PlayerProgression.Stat s :
                        com.crackedgames.craftics.combat.PlayerProgression.Stat.values()) {
                    if (statData.length() > 0) statData.append(":");
                    statData.append(stats.getPoints(s));
                }
                StringBuilder affData = new StringBuilder();
                for (com.crackedgames.craftics.combat.PlayerProgression.Affinity a :
                        com.crackedgames.craftics.combat.PlayerProgression.Affinity.values()) {
                    if (affData.length() > 0) affData.append(":");
                    affData.append(stats.getAffinityPoints(a));
                }
                CrafticsSavedData data = CrafticsSavedData.get(overworld);
                CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new com.crackedgames.craftics.network.PlayerStatsSyncPayload(
                        stats.level, stats.unspentPoints, statData.toString(), pd.emeralds,
                        affData.toString()
                    ));

                // Sync addon equipment scanner bonuses to client
                {
                    com.crackedgames.craftics.api.StatModifiers addonMods =
                        com.crackedgames.craftics.api.registry.EquipmentScannerRegistry.scanAll(player);
                    StringBuilder addonData = new StringBuilder();
                    for (var e : addonMods.getAll().entrySet()) {
                        if (addonData.length() > 0) addonData.append(",");
                        addonData.append(e.getKey().name()).append(":").append(e.getValue());
                    }
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                        new com.crackedgames.craftics.network.AddonBonusSyncPayload(addonData.toString()));
                }

                // Sync guide book unlocks to client
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new com.crackedgames.craftics.network.GuideBookSyncPayload(
                        String.join("|", pd.getUnlockedGuideEntries())
                    ));

                // Grant the Craftics advancement root so the tab is visible,
                // plus re-grant any already-unlocked achievements (persisted in PlayerProgression)
                com.crackedgames.craftics.achievement.AchievementManager.syncVanillaAdvancements(player);

                // If the player disconnected mid-battle, restart the fight after they load in
                updateWorldIcon(server, pd);
                // Safety: if inCombat is set but biomes haven't loaded yet, clear the flag
                // to prevent the player from being stuck in a broken combat loop
                if (pd.inCombat && com.crackedgames.craftics.level.BiomeRegistry.getAllBiomes().isEmpty()) {
                    LOGGER.warn("Clearing stale inCombat flag for {} (biome registry empty)", player.getName().getString());
                    pd.inCombat = false;
                    pd.endBiomeRun();
                    data.markDirty();
                }
                // If the player was in combat when they disconnected, don't try to
                // restart the fight — just end the run cleanly and send them home.
                // Trying to rebuild arenas on rejoin is fragile and causes corruption.
                if (pd.inCombat) {
                    LOGGER.info("Player {} had stale inCombat flag — clearing and ending biome run",
                        player.getName().getString());
                    pd.inCombat = false;
                    pd.endBiomeRun();
                    data.markDirty();
                    CombatManager.remove(player.getUuid());
                }
            }
        });

        // Clean up combat on player disconnect — handle both leader and party member cases
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var playerUuid = handler.getPlayer().getUuid();
            String playerName = handler.getPlayer().getName().getString();
            addonBonusCache.remove(playerUuid);

            // Check if this player is in someone else's party combat (non-leader)
            CombatManager leaderCm = CombatManager.getActiveCombat(playerUuid);
            if (leaderCm != null && leaderCm.isActive() && !leaderCm.equals(CombatManager.get(playerUuid))) {
                // Party member disconnected — remove from leader's combat, notify party
                leaderCm.removePartyMember(playerUuid);
                if (leaderCm.getEventManager() != null) {
                    leaderCm.getEventManager().removeParticipant(playerUuid);
                    ServerWorld world = server.getOverworld();
                    leaderCm.getEventManager().broadcastMessage(world,
                        "\u00a7c" + playerName + " disconnected from the party.");
                }
                LOGGER.info("Party member {} disconnected during combat", playerName);
            }

            // Clean up this player's own CombatManager
            CombatManager cm = CombatManager.get(playerUuid);
            if (cm.isActive()) {
                LOGGER.info("Player {} disconnected during combat — cleaning up", playerName);

                // If leader disconnects, send all remaining party members home first
                if (cm.getEventManager() != null) {
                    ServerWorld world = server.getOverworld();
                    for (ServerPlayerEntity member : cm.getEventManager().getOnlineParticipants(world)) {
                        if (!member.getUuid().equals(playerUuid)) {
                            member.sendMessage(net.minecraft.text.Text.literal(
                                "\u00a7cParty leader disconnected. Returning to hub..."), false);
                            net.minecraft.util.math.BlockPos hub = CrafticsSavedData.get(world)
                                .getHubTeleportPos(member.getUuid());
                            member.requestTeleport(hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5);
                            member.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                            member.clearStatusEffects();
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(member,
                                new com.crackedgames.craftics.network.ExitCombatPayload(false));
                        }
                    }
                }

                cm.endCombat();
            }
            CombatManager.remove(playerUuid);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            var deathProtection = CrafticsComponents.DEATH_PROTECTION.get(newPlayer);
            if (!deathProtection.hasPendingRestore()) return;

            deathProtection.restoreTo(newPlayer);
            // Particles on respawn to confirm restoration
            if (newPlayer.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                    newPlayer.getX(), newPlayer.getY() + 1.0, newPlayer.getZ(),
                    30, 0.5, 0.8, 0.5, 0.05);
                newPlayer.getWorld().playSound(null, newPlayer.getBlockPos(),
                    net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
            newPlayer.sendMessage(Text.literal("\u00a76\u00a7l\u2728 Recovery Compass \u00a7r\u00a76\u2014 Your inventory has been restored!"), false);
        });

        // Tick ALL active combat instances each server tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                CombatManager.tickAll();
            } catch (Throwable t) {
                LOGGER.error("CombatManager.tickAll() crashed; continuing server tick", t);
            }

            try {
                com.crackedgames.craftics.vfx.PhaseScheduler.tickAll();
                for (net.minecraft.server.world.ServerWorld w : server.getWorlds()) {
                    com.crackedgames.craftics.vfx.VfxBlockTracker.of(w).tick(w);
                }
            } catch (Throwable t) {
                LOGGER.error("VFX tick crashed; continuing server tick", t);
            }

            // Re-sync addon equipment scanner bonuses every second (20 ticks)
            // so the inventory UI updates when players equip/unequip addon items.
            // Each player is wrapped in try/catch so one failing scanner can't
            // break sync for every other player on the server.
            if (server.getTicks() % 20 == 0) {
                for (ServerPlayerEntity p : new java.util.ArrayList<>(server.getPlayerManager().getPlayerList())) {
                    if (p == null || p.isRemoved() || p.isDisconnected() || p.networkHandler == null) continue;
                    try {
                        com.crackedgames.craftics.api.StatModifiers addonMods =
                            com.crackedgames.craftics.api.registry.EquipmentScannerRegistry.scanAll(p);
                        StringBuilder sb = new StringBuilder();
                        for (var e : addonMods.getAll().entrySet()) {
                            if (sb.length() > 0) sb.append(",");
                            sb.append(e.getKey().name()).append(":").append(e.getValue());
                        }
                        String newData = sb.toString();
                        // Only send if changed (avoid unnecessary packets)
                        String lastData = addonBonusCache.getOrDefault(p.getUuid(), "");
                        if (!newData.equals(lastData)) {
                            addonBonusCache.put(p.getUuid(), newData);
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                                new com.crackedgames.craftics.network.AddonBonusSyncPayload(newData));
                        }
                    } catch (Throwable t) {
                        LOGGER.error("Addon bonus sync failed for {}: {}",
                            p.getName().getString(), t.toString(), t);
                    }
                }
            }

            // Sync scoreboard to all players every 5 seconds
            if (server.getTicks() % 100 == 0) {
                try {
                    syncScoreboard(server);
                } catch (Throwable t) {
                    LOGGER.error("Scoreboard sync failed: {}", t.toString(), t);
                }
            }
        });

        // Prevent breaking blocks during combat AND hub structure blocks
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
            (world, player, pos, state, blockEntity) -> {
                if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp
                        && CombatManager.get(sp).isActive()) {
                    return false; // cancel block breaking for THIS player during combat
                }
                // Protect central lobby (floating island — prevent breaking the platform)
                if (HubRoomBuilder.isLobbyProtected(pos)) return false;
                // Personal worlds are fully modifiable — no shell protection
                return true;
            }
        );

        // Deferred copper-tier registration. WeaponRegistry needs the actual Item
        // instances from copperagebackport, but Fabric doesn't guarantee that mod's
        // main entrypoint has run by the time ours does — so finish the registration
        // on SERVER_STARTING, which fires after every mod's main-phase init.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(
            server -> com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.registerDeferred());

        // Load biome definitions from JSON datapacks on server start
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            com.crackedgames.craftics.level.BiomeRegistry.clear();
            com.crackedgames.craftics.level.BiomeRegistry.loadFromDatapacks(
                server.getResourceManager()
            );
            // Apply compat overrides AFTER the base JSON pools are in place
            // so our mutations aren't wiped by the datapack load.
            com.crackedgames.craftics.compat.creeperoverhaul.CreeperOverhaulCompat.applyBiomeOverrides();
            com.crackedgames.craftics.compat.variantsandventures.VariantsAndVenturesCompat.applyBiomeOverrides();
            com.crackedgames.craftics.compat.springtolife.SpringToLifeCompat.applyBiomeOverrides();
        });

        // Also reload on /reload command
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
            (server, resourceManager, success) -> {
                if (success) {
                    com.crackedgames.craftics.level.BiomeRegistry.clear();
                    com.crackedgames.craftics.level.BiomeRegistry.loadFromDatapacks(
                        server.getResourceManager()
                    );
                    com.crackedgames.craftics.compat.creeperoverhaul.CreeperOverhaulCompat.applyBiomeOverrides();
                    com.crackedgames.craftics.compat.variantsandventures.VariantsAndVenturesCompat.applyBiomeOverrides();
                    com.crackedgames.craftics.compat.springtolife.SpringToLifeCompat.applyBiomeOverrides();
                }
            }
        );

        // Debug / admin commands: /craftics <subcommand>
        registerCommands();

        LOGGER.info("Craftics initialized.");
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = CommandManager.literal("craftics");

            // /craftics warp — Artifacts Warp Drive activator. Arms the next attack to
            // ignore range/LOS and teleport adjacent to the target. Once per combat.
            root.then(CommandManager.literal("warp").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                if (!com.crackedgames.craftics.compat.artifacts.ArtifactsCompat.isLoaded()) {
                    src.sendError(Text.literal("§cArtifacts mod is not installed."));
                    return 0;
                }
                if (!com.crackedgames.craftics.compat.artifacts.ArtifactsCompat.playerHasArtifact(cmdPlayer, "warp_drive")) {
                    src.sendError(Text.literal("§cYou must have a Warp Drive equipped."));
                    return 0;
                }
                CombatManager cm = CombatManager.get(cmdPlayer);
                if (cm == null || !cm.isActive()) {
                    src.sendError(Text.literal("§cYou can only arm Warp Drive in combat."));
                    return 0;
                }
                if (cm.isWarpDriveUsed()) {
                    src.sendError(Text.literal("§cWarp Drive has already been used this combat."));
                    return 0;
                }
                if (cm.isWarpDriveArmed()) {
                    src.sendFeedback(() -> Text.literal("§dWarp Drive is already armed — your next attack will warp."), false);
                    return 1;
                }
                if (cm.armWarpDrive()) {
                    src.sendFeedback(() -> Text.literal("§5§l✦ Warp Drive armed! §r§dYour next attack ignores range and warps you adjacent to the target."), false);
                    return 1;
                }
                src.sendError(Text.literal("§cCould not arm Warp Drive."));
                return 0;
            }));

            // /craftics hp_per_level <on|off|status> — toggle per-level HP scaling
            // for the caller's OWN island. Guests in someone else's world cannot
            // change this; the owner's setting applies to everyone in their party.
            var hpPerLevelNode = CommandManager.literal("hp_per_level");
            com.mojang.brigadier.Command<ServerCommandSource> showStatus = ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                java.util.UUID owner = data.getEffectiveWorldOwner(cmdPlayer.getUuid());
                boolean enabled = data.getPlayerData(owner).scaleHpPerLevelEnabled;
                boolean self = owner.equals(cmdPlayer.getUuid());
                String label = self ? "your island" : "the party leader's island";
                src.sendFeedback(() -> Text.literal("§ePer-level HP scaling on " + label + ": "
                    + (enabled ? "§aON" : "§cOFF")), false);
                return 1;
            };
            hpPerLevelNode.executes(showStatus);
            hpPerLevelNode.then(CommandManager.literal("status").executes(showStatus));
            hpPerLevelNode.then(CommandManager.literal("on").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                java.util.UUID owner = data.getEffectiveWorldOwner(cmdPlayer.getUuid());
                if (!owner.equals(cmdPlayer.getUuid())) {
                    src.sendError(Text.literal("§cOnly the island owner can change this setting."));
                    return 0;
                }
                data.getPlayerData(owner).scaleHpPerLevelEnabled = true;
                data.markDirty();
                src.sendFeedback(() -> Text.literal("§aPer-level HP scaling §lON§r§a for your island. "
                    + "Enemies gain +HP per level within a biome."), true);
                return 1;
            }));
            hpPerLevelNode.then(CommandManager.literal("off").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                java.util.UUID owner = data.getEffectiveWorldOwner(cmdPlayer.getUuid());
                if (!owner.equals(cmdPlayer.getUuid())) {
                    src.sendError(Text.literal("§cOnly the island owner can change this setting."));
                    return 0;
                }
                data.getPlayerData(owner).scaleHpPerLevelEnabled = false;
                data.markDirty();
                src.sendFeedback(() -> Text.literal("§aPer-level HP scaling §lOFF§r§a for your island. "
                    + "Only the biome-ordinal base HP bonus still applies."), true);
                return 1;
            }));
            root.then(hpPerLevelNode);

            // /craftics unlock_all — unlock every biome (op only)
            root.then(CommandManager.literal("unlock_all").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                ServerWorld overworld = src.getServer().getOverworld();
                CrafticsSavedData data = CrafticsSavedData.get(overworld);
                CrafticsSavedData.PlayerData pd = data.getPlayerData(cmdPlayer.getUuid());
                pd.initBranchIfNeeded();
                int totalBiomes = com.crackedgames.craftics.level.BiomePath.getFullPath(pd.branchChoice).size();
                pd.highestBiomeUnlocked = totalBiomes;
                data.markDirty();
                updateWorldIcon(src.getServer(), pd);
                src.sendFeedback(() -> Text.literal("§aUnlocked all " + totalBiomes + " biomes."), true);
                return 1;
            }));

            // /craftics reset_combat — force-clear corrupted combat state (inCombat, biome run)
            root.then(CommandManager.literal("reset_combat").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                CrafticsSavedData.PlayerData pd = data.getPlayerData(cmdPlayer.getUuid());
                pd.inCombat = false;
                pd.endBiomeRun();
                data.markDirty();
                CombatManager cm = CombatManager.get(cmdPlayer);
                if (cm.isActive()) cm.endCombat();
                CombatManager.remove(cmdPlayer.getUuid());
                // Tell the client to exit combat mode (camera, UI, controls)
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(cmdPlayer,
                    new com.crackedgames.craftics.network.ExitCombatPayload(false));
                cmdPlayer.requestTeleport(0, 65, 0);
                cmdPlayer.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                cmdPlayer.clearStatusEffects();
                src.sendFeedback(() -> Text.literal("§aCombat state reset. Teleported to hub."), true);
                return 1;
            }));

            // /craftics rebuild_arenas [biome] — wipe and regenerate arena blocks,
            // either all of them or just one biome's worth. Useful when a
            // schematic change or world-edit has left the pre-built arenas in a
            // broken state — the next combat will otherwise still scan the bad
            // blocks. Corrupted arenas already auto-repair on fight entry, but
            // this command lets an admin force a full sweep without waiting.
            var rebuildArenasExec = (com.mojang.brigadier.Command<ServerCommandSource>) ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                ServerWorld overworld = src.getServer().getOverworld();
                CrafticsSavedData data = CrafticsSavedData.get(overworld);
                java.util.UUID uid = data.getEffectiveWorldOwner(cmdPlayer.getUuid());
                if (!data.hasPersonalWorld(uid)) {
                    src.sendError(Text.literal("§cNo personal world found."));
                    return 0;
                }
                // End any active combat so we're not scanning mid-fight.
                CombatManager cm = CombatManager.get(cmdPlayer);
                if (cm.isActive()) cm.endCombat();

                String biomeFilter = null;
                try {
                    biomeFilter = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "biome");
                } catch (IllegalArgumentException ignored) {}

                final String filter = biomeFilter;
                src.sendFeedback(() -> Text.literal(
                    "§eRebuilding arenas" + (filter != null ? " for biome §6" + filter : "") + "... (this may take a few seconds)"), true);

                int count = com.crackedgames.craftics.level.ArenaPreGenerator
                    .regenerate(overworld, uid, filter);
                final int finalCount = count;
                src.sendFeedback(() -> Text.literal(
                    "§a✓ Rebuilt §e" + finalCount + "§a arena" + (finalCount == 1 ? "" : "s") + "."), true);
                return 1;
            };

            var rebuildArenasNode = CommandManager.literal("rebuild_arenas")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(rebuildArenasExec);

            // Register a literal child per biome so tab-completion suggests valid ids.
            for (var biome : com.crackedgames.craftics.level.BiomeRegistry.getAllBiomes()) {
                rebuildArenasNode.then(CommandManager.literal(biome.biomeId).executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                    ServerWorld overworld = src.getServer().getOverworld();
                    CrafticsSavedData data = CrafticsSavedData.get(overworld);
                    java.util.UUID uid = data.getEffectiveWorldOwner(cmdPlayer.getUuid());
                    if (!data.hasPersonalWorld(uid)) {
                        src.sendError(Text.literal("§cNo personal world found."));
                        return 0;
                    }
                    CombatManager cm = CombatManager.get(cmdPlayer);
                    if (cm.isActive()) cm.endCombat();
                    src.sendFeedback(() -> Text.literal(
                        "§eRebuilding §6" + biome.biomeId + "§e arenas..."), true);
                    int count = com.crackedgames.craftics.level.ArenaPreGenerator
                        .regenerate(overworld, uid, biome.biomeId);
                    final int finalCount = count;
                    src.sendFeedback(() -> Text.literal(
                        "§a✓ Rebuilt §e" + finalCount + "§a " + biome.biomeId + " arena"
                            + (finalCount == 1 ? "" : "s") + "."), true);
                    return 1;
                }));
            }
            root.then(rebuildArenasNode);

            // /craftics reset_biomes — reset current biome level progress back to level 1
            root.then(CommandManager.literal("reset_biomes").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                CrafticsSavedData.PlayerData pd = data.getPlayerData(cmdPlayer.getUuid());
                String biomeId = pd.activeBiomeId;
                pd.activeBiomeLevelIndex = 0;
                data.markDirty();
                String label = (biomeId != null && !biomeId.isEmpty()) ? biomeId : "current biome";
                src.sendFeedback(() -> Text.literal("§eReset " + label + " progress back to level 1."), true);
                return 1;
            }));

            // /craftics set_emeralds <amount>
            root.then(CommandManager.literal("set_emeralds").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
                        ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                        CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                        data.getPlayerData(cmdPlayer.getUuid()).emeralds = amount;
                        data.markDirty();
                        src.sendFeedback(() -> Text.literal("§aSet emeralds to " + amount + "."), true);
                        return 1;
                    })));

            // /craftics set_level <level> — set player progression level
            root.then(CommandManager.literal("set_level").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        ServerPlayerEntity player = src.getPlayerOrThrow();
                        int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level");
                        var progression = com.crackedgames.craftics.combat.PlayerProgression.get(src.getServer().getOverworld());
                        var stats = progression.getStats(player);
                        int oldLevel = stats.level;
                        stats.level = level;
                        stats.unspentPoints = Math.max(0, stats.unspentPoints + (level - oldLevel));
                        progression.saveStats(player);
                        src.sendFeedback(() -> Text.literal("§aSet player level to " + level + " (" + stats.unspentPoints + " unspent points)."), true);
                        return 1;
                    })));

            // /craftics info — display current save state
            root.then(CommandManager.literal("info").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                CrafticsSavedData.PlayerData pd = data.getPlayerData(cmdPlayer.getUuid());
                src.sendFeedback(() -> Text.literal(
                    "§6--- Craftics Debug Info ---\n" +
                    "§fBiomes unlocked: §e" + pd.highestBiomeUnlocked + "\n" +
                    "§fEmeralds: §a" + pd.emeralds + "\n" +
                    "§fNG+ level: §d" + pd.ngPlusLevel + "\n" +
                    "§fBranch choice: §b" + pd.branchChoice + "\n" +
                    "§fActive biome: §c" + (pd.activeBiomeId.isEmpty() ? "none" : pd.activeBiomeId) + "\n" +
                    "§fDiscovered: §7" + (pd.discoveredBiomes.isEmpty() ? "none" : pd.discoveredBiomes)
                ), false);
                return 1;
            }));

            // /craftics heal — fully heal the player
            root.then(CommandManager.literal("heal").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity player = src.getPlayerOrThrow();
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(5.0f);
                src.sendFeedback(() -> Text.literal("§aFully healed."), true);
                return 1;
            }));

            // /craftics kill_enemies — kill all enemies in current combat
            root.then(CommandManager.literal("kill_enemies").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayer(); if (cmdPlayer == null) { src.sendError(Text.literal("§cMust be a player.")); return 0; } CombatManager cm = CombatManager.get(cmdPlayer);
                if (!cm.isActive()) {
                    src.sendError(Text.literal("§cNo active combat."));
                    return 0;
                }
                cm.adminKillAllEnemies();
                src.sendFeedback(() -> Text.literal("§aAll enemies killed."), true);
                return 1;
            }));

            // /craftics skip_level — win current combat instantly
            root.then(CommandManager.literal("skip_level").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayer(); if (cmdPlayer == null) { src.sendError(Text.literal("§cMust be a player.")); return 0; } CombatManager cm = CombatManager.get(cmdPlayer);
                if (!cm.isActive()) {
                    src.sendError(Text.literal("§cNo active combat."));
                    return 0;
                }
                cm.adminWinCombat();
                src.sendFeedback(() -> Text.literal("§aLevel skipped — victory triggered."), true);
                return 1;
            }));

            // /craftics set_ap <amount> — set AP remaining during combat
            root.then(CommandManager.literal("set_ap").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        ServerPlayerEntity cmdPlayer = src.getPlayer(); if (cmdPlayer == null) { src.sendError(Text.literal("§cMust be a player.")); return 0; } CombatManager cm = CombatManager.get(cmdPlayer);
                        if (!cm.isActive()) {
                            src.sendError(Text.literal("§cNo active combat."));
                            return 0;
                        }
                        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
                        cm.setApRemaining(amount);
                        src.sendFeedback(() -> Text.literal("§aSet AP to " + amount + "."), true);
                        return 1;
                    })));

            // /craftics set_speed <amount> — set move points remaining during combat
            root.then(CommandManager.literal("set_speed").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        ServerPlayerEntity cmdPlayer = src.getPlayer(); if (cmdPlayer == null) { src.sendError(Text.literal("§cMust be a player.")); return 0; } CombatManager cm = CombatManager.get(cmdPlayer);
                        if (!cm.isActive()) {
                            src.sendError(Text.literal("§cNo active combat."));
                            return 0;
                        }
                        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
                        cm.setMovePointsRemaining(amount);
                        src.sendFeedback(() -> Text.literal("§aSet move points to " + amount + "."), true);
                        return 1;
                    })));

            // /craftics set_ngplus <level> — set NG+ cycle
            root.then(CommandManager.literal("set_ngplus").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level");
                        ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                        CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                        data.getPlayerData(cmdPlayer.getUuid()).ngPlusLevel = level;
                        data.markDirty();
                        src.sendFeedback(() -> Text.literal("§aSet NG+ level to " + level + "."), true);
                        return 1;
                    })));

            // /craftics set_stat <stat> <value> — set a specific stat's allocated points
            root.then(CommandManager.literal("set_stat").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("stat", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (com.crackedgames.craftics.combat.PlayerProgression.Stat s :
                                com.crackedgames.craftics.combat.PlayerProgression.Stat.values()) {
                            builder.suggest(s.name().toLowerCase());
                        }
                        return builder.buildFuture();
                    })
                    .then(CommandManager.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                        .executes(ctx -> {
                            ServerCommandSource src = ctx.getSource();
                            ServerPlayerEntity player = src.getPlayerOrThrow();
                            String statName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "stat").toUpperCase();
                            int value = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
                            com.crackedgames.craftics.combat.PlayerProgression.Stat stat;
                            try {
                                stat = com.crackedgames.craftics.combat.PlayerProgression.Stat.valueOf(statName);
                            } catch (IllegalArgumentException e) {
                                src.sendError(Text.literal("§cUnknown stat: " + statName + ". Valid: speed, ap, melee_power, ranged_power, vitality, defense, luck, resourceful"));
                                return 0;
                            }
                            var progression = com.crackedgames.craftics.combat.PlayerProgression.get(src.getServer().getOverworld());
                            var stats = progression.getStats(player);
                            int oldValue = stats.getPoints(stat);
                            stats.setPoints(stat, value);
                            progression.saveStats(player);
                            src.sendFeedback(() -> Text.literal("§aSet " + stat.displayName + " to " + value + " (was " + oldValue + ")."), true);
                            return 1;
                        }))));

            // /craftics reset_stats — reset all player stats to defaults
            root.then(CommandManager.literal("reset_stats").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity player = src.getPlayerOrThrow();
                var progression = com.crackedgames.craftics.combat.PlayerProgression.get(src.getServer().getOverworld());
                var stats = progression.getStats(player);
                int totalAllocated = 0;
                for (com.crackedgames.craftics.combat.PlayerProgression.Stat s :
                        com.crackedgames.craftics.combat.PlayerProgression.Stat.values()) {
                    totalAllocated += stats.getPoints(s);
                    stats.setPoints(s, 0);
                }
                stats.unspentPoints += totalAllocated;
                progression.saveStats(player);
                int refunded = stats.unspentPoints;
                src.sendFeedback(() -> Text.literal("§aReset all stats. " + refunded + " unspent points available."), true);
                return 1;
            }));

            // /craftics tp_hub — teleport back to hub room (ends combat if active)
            root.then(CommandManager.literal("tp_hub").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity player = src.getPlayerOrThrow();
                ServerPlayerEntity cmdPlayer = src.getPlayer(); if (cmdPlayer == null) { src.sendError(Text.literal("§cMust be a player.")); return 0; } CombatManager cm = CombatManager.get(cmdPlayer);
                if (cm.isActive()) {
                    cm.endCombat();
                }
                CrafticsSavedData hubData = CrafticsSavedData.get(player.getServerWorld());
                net.minecraft.util.math.BlockPos hub = hubData.getHubTeleportPos(player.getUuid());
                player.requestTeleport(hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5);
                player.changeGameMode(GameMode.SURVIVAL);
                src.sendFeedback(() -> Text.literal("§aTeleported to hub."), true);
                return 1;
            }));

            // /craftics give <preset> — give a set of gear
            root.then(CommandManager.literal("give").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("wood_gear").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    giveItems(player, net.minecraft.item.Items.WOODEN_SWORD, net.minecraft.item.Items.WOODEN_AXE,
                        net.minecraft.item.Items.SHIELD, net.minecraft.item.Items.LEATHER_HELMET,
                        net.minecraft.item.Items.LEATHER_CHESTPLATE, net.minecraft.item.Items.LEATHER_LEGGINGS,
                        net.minecraft.item.Items.LEATHER_BOOTS);
                    ctx.getSource().sendFeedback(() -> Text.literal("§aGave wood + leather gear."), true);
                    return 1;
                }))
                .then(CommandManager.literal("stone_gear").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    giveItems(player, net.minecraft.item.Items.STONE_SWORD, net.minecraft.item.Items.STONE_AXE,
                        net.minecraft.item.Items.SHIELD, net.minecraft.item.Items.CHAINMAIL_HELMET,
                        net.minecraft.item.Items.CHAINMAIL_CHESTPLATE, net.minecraft.item.Items.CHAINMAIL_LEGGINGS,
                        net.minecraft.item.Items.CHAINMAIL_BOOTS);
                    ctx.getSource().sendFeedback(() -> Text.literal("§aGave stone + chainmail gear."), true);
                    return 1;
                }))
                .then(CommandManager.literal("iron_gear").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    giveItems(player, net.minecraft.item.Items.IRON_SWORD, net.minecraft.item.Items.IRON_AXE,
                        net.minecraft.item.Items.SHIELD, net.minecraft.item.Items.IRON_HELMET,
                        net.minecraft.item.Items.IRON_CHESTPLATE, net.minecraft.item.Items.IRON_LEGGINGS,
                        net.minecraft.item.Items.IRON_BOOTS, net.minecraft.item.Items.BOW);
                    giveStack(player, net.minecraft.item.Items.ARROW, 64);
                    ctx.getSource().sendFeedback(() -> Text.literal("§aGave iron gear + bow."), true);
                    return 1;
                }))
                .then(CommandManager.literal("diamond_gear").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    giveItems(player, net.minecraft.item.Items.DIAMOND_SWORD, net.minecraft.item.Items.DIAMOND_AXE,
                        net.minecraft.item.Items.SHIELD, net.minecraft.item.Items.DIAMOND_HELMET,
                        net.minecraft.item.Items.DIAMOND_CHESTPLATE, net.minecraft.item.Items.DIAMOND_LEGGINGS,
                        net.minecraft.item.Items.DIAMOND_BOOTS, net.minecraft.item.Items.BOW,
                        net.minecraft.item.Items.CROSSBOW, net.minecraft.item.Items.TRIDENT);
                    giveStack(player, net.minecraft.item.Items.ARROW, 64);
                    ctx.getSource().sendFeedback(() -> Text.literal("§aGave diamond gear + ranged weapons."), true);
                    return 1;
                }))
                .then(CommandManager.literal("netherite_gear").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    giveItems(player, net.minecraft.item.Items.NETHERITE_SWORD, net.minecraft.item.Items.NETHERITE_AXE,
                        net.minecraft.item.Items.MACE, net.minecraft.item.Items.SHIELD,
                        net.minecraft.item.Items.NETHERITE_HELMET, net.minecraft.item.Items.NETHERITE_CHESTPLATE,
                        net.minecraft.item.Items.NETHERITE_LEGGINGS, net.minecraft.item.Items.NETHERITE_BOOTS,
                        net.minecraft.item.Items.BOW, net.minecraft.item.Items.CROSSBOW, net.minecraft.item.Items.TRIDENT);
                    giveStack(player, net.minecraft.item.Items.ARROW, 64);
                    ctx.getSource().sendFeedback(() -> Text.literal("§aGave netherite gear + all weapons."), true);
                    return 1;
                }))
                .then(CommandManager.literal("potions").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    givePotions(player);
                    ctx.getSource().sendFeedback(() -> Text.literal("§aGave combat potions."), true);
                    return 1;
                })));

            // /craftics combat_info — show detailed combat state
            root.then(CommandManager.literal("combat_info").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayer(); if (cmdPlayer == null) { src.sendError(Text.literal("§cMust be a player.")); return 0; } CombatManager cm = CombatManager.get(cmdPlayer);
                if (!cm.isActive()) {
                    src.sendError(Text.literal("§cNo active combat."));
                    return 0;
                }
                StringBuilder sb = new StringBuilder("§6--- Combat Debug Info ---\n");
                sb.append("§fPhase: §e").append(cm.getPhase()).append("\n");
                sb.append("§fTurn: §e").append(cm.getTurnNumber()).append("\n");
                sb.append("§fAP: §e").append(cm.getApRemaining()).append("  §fMove: §e").append(cm.getMovePointsRemaining()).append("\n");
                if (cm.getArena() != null) {
                    sb.append("§fArena: §e").append(cm.getArena().getWidth()).append("x").append(cm.getArena().getHeight()).append("\n");
                }
                if (cm.getEnemies() != null) {
                    long aliveCount = cm.getEnemies().stream().filter(com.crackedgames.craftics.combat.CombatEntity::isAlive).count();
                    sb.append("§fEnemies alive: §c").append(aliveCount).append("/").append(cm.getEnemies().size()).append("\n");
                    for (com.crackedgames.craftics.combat.CombatEntity e : cm.getEnemies()) {
                        if (e.isAlive()) {
                            sb.append("  §7- §f").append(e.getDisplayName())
                              .append(" §c").append(e.getCurrentHp()).append("/").append(e.getMaxHp())
                              .append(" §7ATK:").append(e.getAttackPower())
                              .append(" DEF:").append(e.getDefense())
                              .append(" @(").append(e.getGridPos().x()).append(",").append(e.getGridPos().z()).append(")\n");
                        }
                    }
                }
                if (cm.getPlayer() != null) {
                    sb.append("§fPlayer HP: §a").append((int) cm.getPlayer().getHealth())
                      .append("/").append((int) cm.getPlayer().getMaxHealth());
                }
                String info = sb.toString();
                src.sendFeedback(() -> Text.literal(info), false);
                return 1;
            }));

            // /craftics test_range — enter a test arena with a training dummy
            root.then(CommandManager.literal("test_range").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                com.crackedgames.craftics.combat.CombatManager cm = com.crackedgames.craftics.combat.CombatManager.get(p);
                if (cm.isActive()) {
                    ctx.getSource().sendError(Text.literal("§cYou are already in combat! Use /craftics leave to exit first."));
                    return 0;
                }
                cm.startTestRange(p);
                return 1;
            }));

            // /craftics force_event <event> — force the next between-level event
            var forceEventNode = CommandManager.literal("force_event");
            // Built-in event names
            java.util.List<String> eventNames = new java.util.ArrayList<>(java.util.List.of(
                "ambush", "trial", "ominous_trial", "shrine", "traveler", "vault", "dig_site", "enchanter", "trader", "none"
            ));
            // Add addon-registered events from EventRegistry
            for (var entry : com.crackedgames.craftics.api.registry.EventRegistry.getAll()) {
                String id = entry.id();
                if (!eventNames.contains(id)) {
                    eventNames.add(id);
                }
            }
            for (String eventName : eventNames) {
                forceEventNode.then(CommandManager.literal(eventName).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    CombatManager cm = CombatManager.get(p);
                    if ("none".equals(eventName)) {
                        cm.setForcedNextEvent(null);
                        ctx.getSource().sendFeedback(() -> Text.literal("§eCleared forced event."), true);
                    } else {
                        cm.setForcedNextEvent(eventName);
                        ctx.getSource().sendFeedback(() -> Text.literal("§aNext level transition will trigger: §e" + eventName), true);
                    }
                    return 1;
                }));
            }
            root.then(forceEventNode);

            // /craftics leave — leave the test range (or any combat)
            root.then(CommandManager.literal("leave").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                com.crackedgames.craftics.combat.CombatManager cm = com.crackedgames.craftics.combat.CombatManager.get(p);
                if (!cm.isActive()) {
                    ctx.getSource().sendError(Text.literal("§cYou are not in combat."));
                    return 0;
                }
                if (!cm.isTestRange()) {
                    ctx.getSource().sendError(Text.literal("§cYou can only use /craftics leave in the test range."));
                    return 0;
                }
                p.setHealth(p.getMaxHealth());
                cm.endCombat();
                CrafticsSavedData leaveData = CrafticsSavedData.get(p.getServerWorld());
                net.minecraft.util.math.BlockPos leaveHub = leaveData.getHubTeleportPos(p.getUuid());
                p.requestTeleport(leaveHub.getX() + 0.5, leaveHub.getY(), leaveHub.getZ() + 0.5);
                p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                    new com.crackedgames.craftics.network.ExitCombatPayload(false));
                ctx.getSource().sendFeedback(() -> Text.literal("§aLeft the test range."), false);
                return 1;
            }));

            registerPartyCommands(root);
            registerWorldCommands(root);

            dispatcher.register(root);

            // === Shortcut commands ===

            // /new and /craftics new — create a new personal island
            var shortcutNewCmd = (com.mojang.brigadier.Command<ServerCommandSource>) (ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                ServerWorld overworld = player.getServerWorld();
                CrafticsSavedData data = CrafticsSavedData.get(overworld);

                if (data.hasPersonalWorld(player.getUuid())) {
                    ctx.getSource().sendError(Text.literal("\u00a7cYou already have a personal world! Use \u00a7e/home\u00a7c to go there."));
                    return 0;
                }

                // Show loading screen immediately
                ServerPlayNetworking.send(player,
                    new com.crackedgames.craftics.network.LoadingScreenPayload(
                        true, "Creating World...", "Building hub..."));

                final java.util.UUID playerUuid = player.getUuid();
                final ServerWorld finalOverworld = overworld;

                // Island creation only builds the hub now. Arenas are created on
                // demand the first time the player enters that level — see
                // ArenaPreGenerator.ensureArena called from CombatManager.buildArena.
                // The old path pre-built every arena here synchronously, which froze
                // lower-end servers for multiple seconds.
                overworld.getServer().execute(() -> {
                    CrafticsSavedData d = CrafticsSavedData.get(finalOverworld);
                    d.allocateWorldSlot(playerUuid);
                    net.minecraft.util.math.BlockPos hubCenter = d.getHubOrigin(playerUuid);
                    net.minecraft.util.math.BlockPos spawnPos = HubRoomBuilder.build(finalOverworld, hubCenter);
                    CrafticsSavedData.PlayerData pd = d.getPlayerData(playerUuid);
                    pd.personalHubBuilt = true;
                    pd.personalHubVersion = HubRoomBuilder.HUB_VERSION;
                    pd.hubSpawnX = spawnPos.getX();
                    pd.hubSpawnY = spawnPos.getY();
                    pd.hubSpawnZ = spawnPos.getZ();
                    d.markDirty();

                    // Flip the lazy-mode flag on (no actual arenas built).
                    com.crackedgames.craftics.level.ArenaPreGenerator.generateAll(finalOverworld, playerUuid);

                    // Everything is ready — teleport and dismiss loading screen
                    var p = finalOverworld.getServer().getPlayerManager().getPlayer(playerUuid);
                    if (p != null) {
                        p.requestTeleport(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                        p.sendMessage(Text.literal(
                            "\u00a7a\u00a7l\u2726 Personal world created! \u00a7r\u00a7aUse \u00a7e/home\u00a7a to return anytime."), false);
                        ServerPlayNetworking.send(p,
                            new com.crackedgames.craftics.network.LoadingScreenPayload(
                                false, "", ""));
                    }
                });

                return 1;
            });
            dispatcher.register(CommandManager.literal("new").executes(shortcutNewCmd));
            dispatcher.register(CommandManager.literal("craftics").then(
                CommandManager.literal("new").executes(shortcutNewCmd)));

            // /home and /craftics home — teleport to personal hub
            var shortcutHomeCmd = (com.mojang.brigadier.Command<ServerCommandSource>) (ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                // Always operate against the overworld — personal hubs only exist there.
                ServerWorld overworld = player.getServer().getOverworld();
                CrafticsSavedData data = CrafticsSavedData.get(overworld);

                // Check effective world owner (party leader's world if in a party)
                java.util.UUID effectiveOwner = data.getEffectiveWorldOwner(player.getUuid());
                if (!data.hasPersonalWorld(effectiveOwner)) {
                    ctx.getSource().sendError(Text.literal(
                        "\u00a7cYou don't have a personal world yet. Use \u00a7e/new\u00a7c to create one."));
                    return 0;
                }

                // Handle party combat: gracefully remove this player instead of ending for everyone
                CombatManager playerCm = CombatManager.get(player);
                CombatManager activeCm = CombatManager.getActiveCombat(player.getUuid());
                if (activeCm != null && activeCm.isActive()) {
                    // Player is in party combat — remove them gracefully
                    activeCm.leavePartyCombat(player);
                } else if (playerCm.isActive()) {
                    // Solo combat — end normally
                    playerCm.endCombat();
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                        new com.crackedgames.craftics.network.ExitCombatPayload(false));
                }

                // Show loading screen, load chunks, then teleport
                ServerPlayNetworking.send(player,
                    new com.crackedgames.craftics.network.LoadingScreenPayload(
                        true, "Returning Home...", ""));

                final ServerPlayerEntity homePlayer = player;
                final ServerWorld homeWorld = overworld;
                overworld.getServer().execute(() -> {
                    net.minecraft.util.math.BlockPos hub = CrafticsSavedData.get(homeWorld)
                        .getHubTeleportPos(homePlayer.getUuid());

                    // Force-load hub chunks before teleporting
                    int minCX = (hub.getX() - 48) >> 4;
                    int maxCX = (hub.getX() + 48) >> 4;
                    int minCZ = (hub.getZ() - 48) >> 4;
                    int maxCZ = (hub.getZ() + 48) >> 4;
                    for (int cx = minCX; cx <= maxCX; cx++) {
                        for (int cz = minCZ; cz <= maxCZ; cz++) {
                            homeWorld.getChunk(cx, cz);
                        }
                    }

                    // Safety net: if the hub floor is missing (build failed in the past,
                    // chunks were wiped, etc.), rebuild it so we never teleport into void.
                    ensureHubExists(homeWorld, hub);

                    teleportToHub(homePlayer, homeWorld, hub);
                    homePlayer.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                    homePlayer.sendMessage(Text.literal("\u00a7aTeleported home."), false);
                    ServerPlayNetworking.send(homePlayer,
                        new com.crackedgames.craftics.network.LoadingScreenPayload(
                            false, "", ""));
                });
                return 1;
            });
            dispatcher.register(CommandManager.literal("home").executes(shortcutHomeCmd));
            dispatcher.register(CommandManager.literal("craftics").then(
                CommandManager.literal("home").executes(shortcutHomeCmd)));

            // /craftics dev_arena — test arena with every obstacle type
            dispatcher.register(CommandManager.literal("craftics").then(
                CommandManager.literal("dev_arena").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ServerWorld world = player.getServerWorld();

                    CombatManager cm = CombatManager.get(player);
                    if (cm.isActive()) cm.endCombat();

                    var levelDef = new com.crackedgames.craftics.level.DevArenaDefinition();
                    CrafticsSavedData data = CrafticsSavedData.get(world);
                    java.util.UUID uid = player.getUuid();
                    net.minecraft.util.math.BlockPos devOrigin = data.hasPersonalWorld(uid)
                        ? data.getArenaOrigin(uid, levelDef.getLevelNumber())
                        : new net.minecraft.util.math.BlockPos(0, 100, -500);
                    var arena = com.crackedgames.craftics.level.ArenaBuilder.buildAt(world, levelDef, devOrigin);
                    levelDef.placeHazards(world, arena);
                    cm.startDevArena(player, arena, levelDef);

                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "\u00a7a\u00a7lDev Arena loaded! \u00a7r\u00a77Every obstacle type + 4 Husks."), false);
                    return 1;
                })
            ));

            // /lobby — shortcut to central lobby
            dispatcher.register(CommandManager.literal("lobby").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                CombatManager cm = CombatManager.get(player);
                if (cm.isActive()) cm.endCombat();

                player.requestTeleport(0.5, 65, 0.5);
                player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                ctx.getSource().sendFeedback(() -> Text.literal("\u00a7aTeleported to the lobby."), false);
                return 1;
            }));
        });
    }

    private void registerWorldCommands(com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> root) {
        var worldNode = CommandManager.literal("world");

        // /craftics world create — create a personal world
        worldNode.then(CommandManager.literal("create").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = player.getServerWorld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);

            if (data.hasPersonalWorld(player.getUuid())) {
                ctx.getSource().sendError(Text.literal("\u00a7cYou already have a personal world!"));
                return 0;
            }

            int slot = data.allocateWorldSlot(player.getUuid());
            net.minecraft.util.math.BlockPos hubCenter = data.getHubOrigin(player.getUuid());

            // Build the personal hub
            net.minecraft.util.math.BlockPos spawnPos = HubRoomBuilder.build(overworld, hubCenter);
            CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
            pd.personalHubBuilt = true;
            pd.personalHubVersion = HubRoomBuilder.HUB_VERSION;
            pd.hubSpawnX = spawnPos.getX();
            pd.hubSpawnY = spawnPos.getY();
            pd.hubSpawnZ = spawnPos.getZ();
            data.markDirty();

            // Teleport player to their new hub
            player.requestTeleport(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

            ctx.getSource().sendFeedback(() -> Text.literal(
                "\u00a7a\u00a7l\u2726 Personal world created! \u00a7r\u00a7a(Slot " + slot + ")"), true);
            ctx.getSource().sendFeedback(() -> Text.literal(
                "\u00a77Use \u00a7e/craftics world home\u00a77 to return here anytime."), false);
            return 1;
        }));

        // /craftics world home — teleport to personal hub
        worldNode.then(CommandManager.literal("home").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = player.getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);

            if (!data.hasPersonalWorld(player.getUuid())) {
                ctx.getSource().sendError(Text.literal(
                    "\u00a7cYou don't have a personal world yet. Use \u00a7e/craftics world create\u00a7c first."));
                return 0;
            }

            // End combat if active
            CombatManager cm = CombatManager.get(player);
            if (cm.isActive()) cm.endCombat();

            net.minecraft.util.math.BlockPos hub = data.getHubTeleportPos(player.getUuid());
            ensureHubExists(overworld, hub);
            teleportToHub(player, overworld, hub);
            player.changeGameMode(GameMode.SURVIVAL);
            ctx.getSource().sendFeedback(() -> Text.literal("\u00a7aTeleported to your personal hub."), false);
            return 1;
        }));

        // /craftics world lobby — teleport to central lobby
        worldNode.then(CommandManager.literal("lobby").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

            CombatManager cm = CombatManager.get(player);
            if (cm.isActive()) cm.endCombat();

            player.requestTeleport(0.5, 65, 0.5);
            player.changeGameMode(GameMode.SURVIVAL);
            ctx.getSource().sendFeedback(() -> Text.literal("\u00a7aTeleported to the central lobby."), false);
            return 1;
        }));

        // /craftics world visit <player> — visit another player's hub (party members only)
        worldNode.then(CommandManager.literal("visit")
            .then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "player");
                    ServerWorld overworld = player.getServerWorld();
                    CrafticsSavedData data = CrafticsSavedData.get(overworld);

                    if (!data.hasPersonalWorld(target.getUuid())) {
                        ctx.getSource().sendError(Text.literal(
                            "\u00a7c" + target.getName().getString() + " doesn't have a personal world."));
                        return 0;
                    }

                    // Check if they're in the same party
                    com.crackedgames.craftics.world.Party playerParty = data.getPlayerParty(player.getUuid());
                    if (playerParty == null || !playerParty.isMember(target.getUuid())) {
                        ctx.getSource().sendError(Text.literal(
                            "\u00a7cYou can only visit party members' worlds."));
                        return 0;
                    }

                    CombatManager cm = CombatManager.get(player);
                    if (cm.isActive()) cm.endCombat();

                    net.minecraft.util.math.BlockPos targetHub = data.getHubOrigin(target.getUuid());
                    player.requestTeleport(targetHub.getX() + 0.5, targetHub.getY(), targetHub.getZ() + 0.5);
                    player.changeGameMode(GameMode.SURVIVAL);
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "\u00a7aVisiting " + target.getName().getString() + "'s world."), false);
                    return 1;
                })));

        root.then(worldNode);
    }

    private void registerPartyCommands(com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> root) {
        var partyNode = CommandManager.literal("party");

        // /craftics party create
        partyNode.then(CommandManager.literal("create").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = ctx.getSource().getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);
            if (data.getPlayerParty(player.getUuid()) != null) {
                ctx.getSource().sendError(Text.literal("§cYou are already in a party. Use /craftics party leave first."));
                return 0;
            }
            data.createParty(player.getUuid());
            ctx.getSource().sendFeedback(() -> Text.literal("§aParty created! Use /craftics party invite <player> to add members."), false);
            return 1;
        }));

        // /craftics party invite <player>
        partyNode.then(CommandManager.literal("invite")
            .then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "player");
                    ServerWorld overworld = ctx.getSource().getServer().getOverworld();
                    CrafticsSavedData data = CrafticsSavedData.get(overworld);
                    com.crackedgames.craftics.world.Party party = data.getPlayerParty(player.getUuid());
                    if (party == null) {
                        ctx.getSource().sendError(Text.literal("§cYou are not in a party. Use /craftics party create first."));
                        return 0;
                    }
                    if (!party.isLeader(player.getUuid())) {
                        ctx.getSource().sendError(Text.literal("§cOnly the party leader can invite players."));
                        return 0;
                    }
                    if (party.size() >= CONFIG.maxPartySize()) {
                        ctx.getSource().sendError(Text.literal("§cParty is full (max " + CONFIG.maxPartySize() + ")."));
                        return 0;
                    }
                    if (party.isMember(target.getUuid())) {
                        ctx.getSource().sendError(Text.literal("§c" + target.getName().getString() + " is already in your party."));
                        return 0;
                    }
                    if (data.getPlayerParty(target.getUuid()) != null) {
                        ctx.getSource().sendError(Text.literal("§c" + target.getName().getString() + " is already in another party."));
                        return 0;
                    }
                    data.addPartyInvite(party.getPartyId(), target.getUuid());
                    ctx.getSource().sendFeedback(() -> Text.literal("§aInvited " + target.getName().getString() + " to the party."), false);
                    //? if <=1.21.4 {
                    net.minecraft.text.MutableText acceptText = Text.literal("§a[ACCEPT]")
                        .styled(s -> s.withClickEvent(new net.minecraft.text.ClickEvent(
                            net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/craftics party accept"))
                            .withHoverEvent(new net.minecraft.text.HoverEvent(
                                net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Click to accept"))));
                    net.minecraft.text.MutableText declineText = Text.literal("§c[DECLINE]")
                        .styled(s -> s.withClickEvent(new net.minecraft.text.ClickEvent(
                            net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/craftics party decline"))
                            .withHoverEvent(new net.minecraft.text.HoverEvent(
                                net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Click to decline"))));
                    //?} else {
                    /*net.minecraft.text.MutableText acceptText = Text.literal("§a[ACCEPT]")
                        .styled(s -> s.withClickEvent(new net.minecraft.text.ClickEvent.RunCommand("/craftics party accept"))
                            .withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(Text.literal("Click to accept"))));
                    net.minecraft.text.MutableText declineText = Text.literal("§c[DECLINE]")
                        .styled(s -> s.withClickEvent(new net.minecraft.text.ClickEvent.RunCommand("/craftics party decline"))
                            .withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(Text.literal("Click to decline"))));
                    *///?}
                    target.sendMessage(Text.literal("§e" + player.getName().getString() + " invited you to their party! ")
                        .append(acceptText).append(Text.literal(" ")).append(declineText), false);
                    return 1;
                })));

        // /craftics party accept
        partyNode.then(CommandManager.literal("accept").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = ctx.getSource().getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);
            for (var entry : data.getAllParties().entrySet()) {
                com.crackedgames.craftics.world.Party party = entry.getValue();
                if (party.hasInvite(player.getUuid())) {
                    if (data.joinParty(player.getUuid(), party.getPartyId())) {
                        ctx.getSource().sendFeedback(() -> Text.literal("§aYou joined the party!"), false);
                        for (java.util.UUID memberUuid : party.getMemberUuids()) {
                            ServerPlayerEntity member = ctx.getSource().getServer().getPlayerManager().getPlayer(memberUuid);
                            if (member != null && !member.equals(player)) {
                                member.sendMessage(Text.literal("§e" + player.getName().getString() + " joined the party!"), false);
                            }
                        }
                        return 1;
                    }
                }
            }
            ctx.getSource().sendError(Text.literal("§cNo pending party invite."));
            return 0;
        }));

        // /craftics party decline
        partyNode.then(CommandManager.literal("decline").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = ctx.getSource().getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);
            boolean found = false;
            for (var entry : data.getAllParties().entrySet()) {
                com.crackedgames.craftics.world.Party party = entry.getValue();
                if (party.hasInvite(player.getUuid())) {
                    data.removePartyInvite(party.getPartyId(), player.getUuid());
                    found = true;
                }
            }
            if (found) {
                ctx.getSource().sendFeedback(() -> Text.literal("§7Invite declined."), false);
                return 1;
            }
            ctx.getSource().sendError(Text.literal("§cNo pending party invite."));
            return 0;
        }));

        // /craftics party leave
        partyNode.then(CommandManager.literal("leave").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = ctx.getSource().getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);
            com.crackedgames.craftics.world.Party party = data.getPlayerParty(player.getUuid());
            if (party == null) {
                ctx.getSource().sendError(Text.literal("§cYou are not in a party."));
                return 0;
            }
            boolean wasLeader = party.isLeader(player.getUuid());
            // Snapshot members before leaving
            java.util.Set<java.util.UUID> remainingMembers = new java.util.HashSet<>(party.getMemberUuids());
            data.leaveParty(player.getUuid());
            ctx.getSource().sendFeedback(() -> Text.literal("§7You left the party."), false);
            remainingMembers.remove(player.getUuid());
            for (java.util.UUID memberUuid : remainingMembers) {
                ServerPlayerEntity member = ctx.getSource().getServer().getPlayerManager().getPlayer(memberUuid);
                if (member != null) {
                    String leaderMsg = wasLeader && party.isLeader(memberUuid) ? " §b" + member.getName().getString() + " is now the leader." : "";
                    member.sendMessage(Text.literal("§e" + player.getName().getString() + " left the party." + leaderMsg), false);
                }
            }
            return 1;
        }));

        // /craftics party kick <player>
        partyNode.then(CommandManager.literal("kick")
            .then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "player");
                    ServerWorld overworld = ctx.getSource().getServer().getOverworld();
                    CrafticsSavedData data = CrafticsSavedData.get(overworld);
                    com.crackedgames.craftics.world.Party party = data.getPlayerParty(player.getUuid());
                    if (party == null || !party.isLeader(player.getUuid())) {
                        ctx.getSource().sendError(Text.literal("§cOnly the party leader can kick players."));
                        return 0;
                    }
                    if (!party.isMember(target.getUuid())) {
                        ctx.getSource().sendError(Text.literal("§c" + target.getName().getString() + " is not in your party."));
                        return 0;
                    }
                    if (target.equals(player)) {
                        ctx.getSource().sendError(Text.literal("§cYou can't kick yourself. Use /craftics party leave."));
                        return 0;
                    }
                    data.kickFromParty(party.getPartyId(), target.getUuid());
                    ctx.getSource().sendFeedback(() -> Text.literal("§aKicked " + target.getName().getString() + " from the party."), false);
                    target.sendMessage(Text.literal("§cYou were kicked from the party."), false);
                    return 1;
                })));

        // /craftics party list
        partyNode.then(CommandManager.literal("list").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = ctx.getSource().getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);
            com.crackedgames.craftics.world.Party party = data.getPlayerParty(player.getUuid());
            if (party == null) {
                ctx.getSource().sendFeedback(() -> Text.literal("§7You are not in a party."), false);
                return 1;
            }
            StringBuilder sb = new StringBuilder("§6--- " + party.getDisplayName() + " ---\n");
            for (java.util.UUID memberUuid : party.getMemberUuids()) {
                ServerPlayerEntity member = ctx.getSource().getServer().getPlayerManager().getPlayer(memberUuid);
                String name = member != null ? member.getName().getString() : memberUuid.toString().substring(0, 8) + "...";
                boolean isLeader = party.isLeader(memberUuid);
                boolean isOnline = member != null;
                sb.append(isOnline ? "§a" : "§7").append(name);
                if (isLeader) sb.append(" §e[Leader]");
                if (!isOnline) sb.append(" §8[Offline]");
                sb.append("\n");
            }
            String result = sb.toString();
            ctx.getSource().sendFeedback(() -> Text.literal(result), false);
            return 1;
        }));

        // /craftics party disband
        partyNode.then(CommandManager.literal("disband").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = ctx.getSource().getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);
            com.crackedgames.craftics.world.Party party = data.getPlayerParty(player.getUuid());
            if (party == null || !party.isLeader(player.getUuid())) {
                ctx.getSource().sendError(Text.literal("§cOnly the party leader can disband."));
                return 0;
            }
            for (java.util.UUID memberUuid : party.getMemberUuids()) {
                ServerPlayerEntity member = ctx.getSource().getServer().getPlayerManager().getPlayer(memberUuid);
                if (member != null && !member.equals(player)) {
                    member.sendMessage(Text.literal("§cThe party has been disbanded."), false);
                }
            }
            data.disbandParty(party.getPartyId());
            ctx.getSource().sendFeedback(() -> Text.literal("§7Party disbanded."), false);
            return 1;
        }));

        // /craftics party name <name>
        partyNode.then(CommandManager.literal("name")
            .then(CommandManager.argument("name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ServerWorld overworld = ctx.getSource().getServer().getOverworld();
                    CrafticsSavedData data = CrafticsSavedData.get(overworld);
                    com.crackedgames.craftics.world.Party party = data.getPlayerParty(player.getUuid());
                    if (party == null) {
                        ctx.getSource().sendError(Text.literal("§cYou are not in a party."));
                        return 0;
                    }
                    if (!party.isLeader(player.getUuid())) {
                        ctx.getSource().sendError(Text.literal("§cOnly the party leader can rename the party."));
                        return 0;
                    }
                    String newName = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                    if (newName.length() > 24) {
                        ctx.getSource().sendError(Text.literal("§cParty name too long (max 24 characters)."));
                        return 0;
                    }
                    party.setName(newName);
                    data.markDirty();
                    String displayName = party.getDisplayName();
                    for (java.util.UUID memberUuid : party.getMemberUuids()) {
                        ServerPlayerEntity member = ctx.getSource().getServer().getPlayerManager().getPlayer(memberUuid);
                        if (member != null) {
                            member.sendMessage(Text.literal("§aParty renamed to: §e" + displayName), false);
                        }
                    }
                    return 1;
                })));

        root.then(partyNode);
    }

    /**
     * Calculate a player's "Power Score" for the server scoreboard.
     * Formula: (level * 100) + (highestBiome * 50) + (ngPlus * 500) + (emeralds / 10) + (totalBossKills * 25)
     */
    private static int calculatePowerScore(com.crackedgames.craftics.combat.PlayerProgression.PlayerStats stats,
                                            com.crackedgames.craftics.world.CrafticsSavedData.PlayerData pd) {
        int levelScore = stats.level * 100;
        int biomeScore = pd.highestBiomeUnlocked * 50;
        int ngScore = pd.ngPlusLevel * 500;
        int emeraldScore = pd.emeralds / 10;
        // Sum boss kills across all biomes
        int totalBossKills = 0;
        for (var biomeId : new String[]{
            "plains", "forest", "desert", "river", "mountain", "snowy", "cave", "jungle",
            "deep_dark", "basalt_deltas", "crimson_forest", "nether_wastes", "soul_sand_valley",
            "warped_forest", "chorus_grove", "end_city", "outer_end_islands", "dragons_nest",
            "trial_chamber"
        }) {
            totalBossKills += stats.getBossKills(biomeId);
        }
        int killScore = totalBossKills * 25;
        return levelScore + biomeScore + ngScore + emeraldScore + killScore;
    }

    /** Sync the power scoreboard to all online players. */
    private static void syncScoreboard(net.minecraft.server.MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        com.crackedgames.craftics.world.CrafticsSavedData data =
            com.crackedgames.craftics.world.CrafticsSavedData.get(overworld);
        com.crackedgames.craftics.combat.PlayerProgression prog =
            com.crackedgames.craftics.combat.PlayerProgression.get(overworld);

        java.util.List<String[]> scores = new java.util.ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p == null || p.isRemoved()) continue;
            com.crackedgames.craftics.world.CrafticsSavedData.PlayerData pd = data.getPlayerData(p.getUuid());
            com.crackedgames.craftics.combat.PlayerProgression.PlayerStats stats = prog.getStats(p);
            int score = calculatePowerScore(stats, pd);
            scores.add(new String[]{ p.getName().getString(), String.valueOf(score) });
        }
        // Sort descending by score
        scores.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));

        StringBuilder sb = new StringBuilder();
        for (String[] entry : scores) {
            if (sb.length() > 0) sb.append("|");
            sb.append(entry[0]).append(",").append(entry[1]);
        }

        com.crackedgames.craftics.network.ScoreboardSyncPayload payload =
            new com.crackedgames.craftics.network.ScoreboardSyncPayload(sb.toString());
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p == null || p.isRemoved() || p.isDisconnected() || p.networkHandler == null) continue;
            try {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, payload);
            } catch (Throwable ignored) {}
        }
    }

    private static void giveItems(ServerPlayerEntity player, net.minecraft.item.Item... items) {
        for (net.minecraft.item.Item item : items) {
            player.giveItemStack(new net.minecraft.item.ItemStack(item));
        }
    }

    private static void giveStack(ServerPlayerEntity player, net.minecraft.item.Item item, int count) {
        player.giveItemStack(new net.minecraft.item.ItemStack(item, count));
    }

    private static void givePotions(ServerPlayerEntity player) {
        givePotion(player, net.minecraft.potion.Potions.STRENGTH);
        givePotion(player, net.minecraft.potion.Potions.SWIFTNESS);
        givePotion(player, net.minecraft.potion.Potions.FIRE_RESISTANCE);
        givePotion(player, net.minecraft.potion.Potions.REGENERATION);
        givePotion(player, net.minecraft.potion.Potions.HEALING);
        givePotion(player, net.minecraft.potion.Potions.HEALING);
    }

    private static void givePotion(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion> potion) {
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(net.minecraft.item.Items.POTION);
        stack.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS,
            new net.minecraft.component.type.PotionContentsComponent(potion));
        player.giveItemStack(stack);
    }

    /**
     * Verify the personal hub has a solid floor below the teleport target. If not
     * (build never ran, chunks were wiped, saved data out of sync with actual blocks),
     * rebuild the hub at the given center so /home never drops a player into the void.
     */
    private static void ensureHubExists(ServerWorld world, net.minecraft.util.math.BlockPos hubCenter) {
        net.minecraft.util.math.BlockPos floor = hubCenter.down();
        net.minecraft.block.BlockState floorState = world.getBlockState(floor);
        if (floorState.isAir() || !floorState.isSolidBlock(world, floor)) {
            LOGGER.warn("Personal hub at {} is missing its floor — rebuilding", hubCenter);
            // Rebuild returns the podzol-based spawn, but we don't update PlayerData here
            // because we don't have the player UUID. The stored spawn pos is still valid
            // from the original build — the schematic is deterministic.
            HubRoomBuilder.build(world, hubCenter);
        }
    }

    /**
     * Teleport a player to their personal hub, switching dimension if they are not
     * already in the overworld (hubs only exist in the overworld).
     */
    private static void teleportToHub(ServerPlayerEntity player, ServerWorld overworld,
                                       net.minecraft.util.math.BlockPos hub) {
        double x = hub.getX() + 0.5;
        double z = hub.getZ() + 0.5;
        // Find a safe landing spot — scan upward first (island may be above),
        // then downward as a fallback, to handle both old and new spawn positions.
        double y = hub.getY();
        net.minecraft.util.math.BlockPos.Mutable probe = new net.minecraft.util.math.BlockPos.Mutable(
            hub.getX(), (int) y, hub.getZ());
        boolean found = false;
        // Scan upward first (covers raised islands)
        for (int dy = 0; dy < 60; dy++) {
            probe.setY((int) y + dy);
            net.minecraft.block.BlockState below = overworld.getBlockState(probe);
            net.minecraft.block.BlockState at = overworld.getBlockState(probe.up());
            if (!below.isAir() && below.isSolidBlock(overworld, probe) && at.isAir()) {
                y = probe.getY() + 1;
                found = true;
                break;
            }
        }
        // Fallback: scan downward
        if (!found) {
            for (int dy = 1; dy < 40; dy++) {
                probe.setY((int) hub.getY() - dy);
                net.minecraft.block.BlockState below = overworld.getBlockState(probe);
                net.minecraft.block.BlockState at = overworld.getBlockState(probe.up());
                if (!below.isAir() && below.isSolidBlock(overworld, probe) && at.isAir()) {
                    y = probe.getY() + 1;
                    break;
                }
            }
        }
        if (player.getServerWorld() != overworld) {
            //? if <=1.21.1 {
            player.teleport(overworld, x, y, z,
                java.util.Collections.emptySet(), player.getYaw(), player.getPitch());
            //?} else {
            /*player.teleport(overworld, x, y, z,
                java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), true);
            *///?}
        } else {
            player.requestTeleport(x, y, z);
        }
    }

    /**
     * Writes the biome icon of the player's highest unlocked biome as the world's icon.png,
     * so the world-select screen shows progression at a glance. No-ops on non-Craftics worlds.
     */
    public static void updateWorldIcon(net.minecraft.server.MinecraftServer server, CrafticsSavedData.PlayerData pd) {
        if (!(server.getOverworld().getChunkManager().getChunkGenerator() instanceof VoidChunkGenerator)) return;
        try {
            pd.initBranchIfNeeded();
            java.util.List<String> fullPath = com.crackedgames.craftics.level.BiomePath
                .getFullPath(Math.max(0, pd.branchChoice));
            int idx = Math.max(0, Math.min(pd.highestBiomeUnlocked - 1, fullPath.size() - 1));
            String biomeId = fullPath.get(idx);
            String resourcePath = "/assets/craftics/textures/gui/biomes/" + biomeId + ".png";
            try (java.io.InputStream iconStream = CrafticsMod.class.getResourceAsStream(resourcePath)) {
                if (iconStream == null) {
                    LOGGER.warn("World icon: no biome icon asset found for '{}'", biomeId);
                    return;
                }
                java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(iconStream);
                if (src == null) {
                    LOGGER.warn("World icon: failed to decode image for '{}'", biomeId);
                    return;
                }
                java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(src, 0, 0, 64, 64, null);
                g.dispose();
                java.nio.file.Path iconFile = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                    .resolve("icon.png");
                try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(iconFile,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                    javax.imageio.ImageIO.write(scaled, "PNG", out);
                }
                LOGGER.debug("Updated world icon to biome '{}' (64x64, highestUnlocked={})", biomeId, pd.highestBiomeUnlocked);
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to update world icon: {}", ex.getMessage());
        }
    }
}