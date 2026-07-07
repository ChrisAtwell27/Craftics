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
        com.crackedgames.craftics.api.VanillaAllies.registerAll();
        com.crackedgames.craftics.api.VanillaHybridSets.registerAll();
        com.crackedgames.craftics.api.VanillaEnvironments.registerAll();
        // Seed the built-in craftics:vanilla campaign from code (safety net mirroring
        // data/craftics/craftics/campaigns/vanilla.json). Registered BEFORE the addon
        // entrypoint loop so addon/datapack campaigns win over this built-in one.
        com.crackedgames.craftics.level.campaign.VanillaCampaign.register();

        // Optional mod compatibility: each call no-ops if its target mod isn't loaded.
        com.crackedgames.craftics.compat.artifacts.ArtifactsCompat.init();
        com.crackedgames.craftics.compat.creeperoverhaul.CreeperOverhaulCompat.init();
        com.crackedgames.craftics.compat.variantsandventures.VariantsAndVenturesCompat.init();
        com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.init();
        com.crackedgames.craftics.compat.palegardenbackport.PaleGardenBackportCompat.init();
        com.crackedgames.craftics.compat.moretotems.MoreTotemsCompat.init();
        com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat.init();
        com.crackedgames.craftics.compat.golemoverhaul.GolemOverhaulCompat.init();
        com.crackedgames.craftics.compat.instruments.InstrumentsCompat.init();
        com.crackedgames.craftics.compat.paladins.PaladinsCompat.init();
        com.crackedgames.craftics.compat.simplyswords.SimplySwordsCompat.init();

        // Addon entrypoint: invoked after all built-in content and compat modules are
        // registered, so addon registrations run last and win deterministically over
        // any same-keyed built-in entry. Addons declare a "craftics" entrypoint in
        // their fabric.mod.json pointing at a CrafticsAddon implementation.
        for (com.crackedgames.craftics.api.CrafticsAddon addon :
                net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getEntrypoints("craftics", com.crackedgames.craftics.api.CrafticsAddon.class)) {
            try {
                addon.onCrafticsInit();
            } catch (Throwable t) {
                LOGGER.error("Craftics addon failed during onCrafticsInit()", t);
            }
        }

        // JSON datapack loaders: run on server start and after /reload.
        com.crackedgames.craftics.data.CrafticsDataLoaders.init();

        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of(MOD_ID, "void"), VoidChunkGenerator.CODEC);

        ModBlocks.register();
        com.crackedgames.craftics.item.ModItems.register();
        ModScreenHandlers.register();
        com.crackedgames.craftics.sound.ModSounds.register();
        ModNetworking.registerServer();

        // Level-select phantom-half click routing is now handled by the
        // LevelSelectGhostBlock placed alongside the real block, no global
        // UseBlockCallback needed. The previous callback scanned all 4 horizontal
        // neighbors and hijacked clicks on any adjacent block (a chest placed
        // next to the level-select opened the level-select screen instead).

        // Battle party: Shift+Right-Click (empty main hand) any passive or neutral
        // mob on your island to add it to, or remove it from, your battle party.
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND) return net.minecraft.util.ActionResult.PASS;
            if (com.crackedgames.craftics.world.VisitProtection.isForeignVisitor(player)) {
                return net.minecraft.util.ActionResult.FAIL;
            }
            if (!player.isSneaking() || !player.getMainHandStack().isEmpty()) return net.minecraft.util.ActionResult.PASS;
            if (!(entity instanceof net.minecraft.entity.mob.MobEntity mob)) return net.minecraft.util.ActionResult.PASS;
            if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
            // A single right-click fires this callback twice: once for the
            // INTERACT_AT packet (hitResult != null) and once for INTERACT
            // (hitResult == null). Consume both so vanilla interaction is fully
            // blocked, but toggle the party only on the INTERACT pass so one
            // click is one toggle.
            if (hitResult != null) return net.minecraft.util.ActionResult.SUCCESS;
            return com.crackedgames.craftics.combat.PartyMobs.toggleParty(sp, mob);
        });

        // Trial keys: right-clicking during combat queues a guaranteed trial
        // chamber (or ominous trial chamber) on the next level transition.
        // Consumes one key per use. Outside combat the keys do nothing.
        //? if <=1.21.1 {
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND) {
                return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
            }
            if (com.crackedgames.craftics.world.VisitProtection.isForeignVisitor(player)) {
                return net.minecraft.util.TypedActionResult.fail(player.getStackInHand(hand));
            }
            net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            net.minecraft.item.Item item = stack.getItem();
            String forced;
            String label;
            if (item == net.minecraft.item.Items.TRIAL_KEY) {
                forced = "trial";
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
                    "\u00a7d\u00a7l" + label + " queued! \u00a7r\u00a7dIt will appear after this fight."),
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
            if (com.crackedgames.craftics.world.VisitProtection.isForeignVisitor(player)) {
                return net.minecraft.util.ActionResult.FAIL;
            }
            net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            net.minecraft.item.Item item = stack.getItem();
            String forced;
            String label;
            if (item == net.minecraft.item.Items.TRIAL_KEY) {
                forced = "trial";
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
                    "\u00a7d\u00a7l" + label + " queued! \u00a7r\u00a7dIt will appear after this fight."),
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
            // Fantasy saves/unloads its own runtime worlds on stop; this only drops
            // our stale UUID->handle map so it doesn't leak into the next session.
            com.crackedgames.craftics.world.IslandDimensions.clear();
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
            // Infinite mode: refresh the leaderboard name and, if this player
            // disconnected mid-run, hand their stashed items/levels back.
            com.crackedgames.craftics.combat.InfiniteRunManager.onPlayerJoin(player);
            if (overworld.getChunkManager().getChunkGenerator() instanceof VoidChunkGenerator) {
                LOGGER.info("Craftics: Teleporting player {} to hub", player.getName().getString());
                // Land on the HIGHEST solid block at the lobby column, never a hard y=65
                // that can sit inside or under the lobby island. Honors a custom stored
                // lobby spawn (/craftics lobby setspawn) when one has been set.
                CrafticsSavedData joinLobbyData = CrafticsSavedData.get(overworld);
                net.minecraft.util.math.BlockPos joinLobbySpawn = joinLobbyData.getLobbySpawn();
                double joinX = joinLobbySpawn != null ? joinLobbySpawn.getX() + 0.5 : 0.5;
                double joinZ = joinLobbySpawn != null ? joinLobbySpawn.getZ() + 0.5 : 0.5;
                float joinYaw = joinLobbySpawn != null ? joinLobbyData.lobbySpawnYaw : 0f;
                int joinFallbackY = joinLobbySpawn != null ? joinLobbySpawn.getY() : 65;
                int joinY = hubLandingY(overworld, (int) Math.floor(joinX), (int) Math.floor(joinZ), joinFallbackY);
                if (joinY == Integer.MIN_VALUE) joinY = joinFallbackY;
                // De-laned: a player who logged out inside their island dim rejoins THERE,
                // so routing to the overworld lobby is now cross-dim. requestTeleport is
                // same-dim only (it would land them at island-(0,65,0) instead of the lobby);
                // use the version-split cross-dim teleport when they aren't already here.
                if (player.getServerWorld() != overworld) {
                    //? if <=1.21.1 {
                    player.teleport(overworld, joinX, joinY, joinZ,
                        java.util.Collections.emptySet(), joinYaw, 0f);
                    //?} else {
                    /*player.teleport(overworld, joinX, joinY, joinZ,
                        java.util.Collections.emptySet(), joinYaw, 0f, true);
                    *///?}
                } else {
                    player.refreshPositionAndAngles(joinX, joinY, joinZ, joinYaw, 0f);
                    player.requestTeleport(joinX, joinY, joinZ);
                }
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

                // NOTE: the hollow-island fix deliberately does NOT auto-migrate
                // existing worlds. Solid placement applies only to NEWLY built
                // islands; older saves are left exactly as they are (an automatic
                // on-join repair shipped briefly and misfired on pre-schematic and
                // re-laid-out saves). Owners of pre-fix islands get a ONE-TIME chat
                // notice pointing at the opt-in commands - a message, never a world
                // edit - then the version is stamped so it never repeats.
                if (joinPd.personalHubBuilt
                        && joinPd.personalHubVersion < com.crackedgames.craftics.world.HubRoomBuilder.HUB_VERSION) {
                    player.sendMessage(Text.literal(
                        "§6Your island predates the hollow-island fix.§7 If its interior is "
                        + "hollow, §e/craftics world repairhollow§7 fills it. If a recent dev "
                        + "build pasted unwanted blocks into it, §e/craftics world undorepair§7 "
                        + "removes exactly those blocks. Both are optional and reversible."), false);
                    joinPd.personalHubVersion = com.crackedgames.craftics.world.HubRoomBuilder.HUB_VERSION;
                    joinData.markDirty();
                }

                // Always stock the Move item into the locked slot on join.
                com.crackedgames.craftics.item.MoveSlotManager.enforce(player);

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

                // Sync battle-party membership so the client can label party mobs
                com.crackedgames.craftics.network.PartyMobSync.sync(player);

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
                // restart the fight, just end the run cleanly and send them home.
                // Trying to rebuild arenas on rejoin is fragile and causes corruption.
                if (pd.inCombat) {
                    LOGGER.info("Player {} had stale inCombat flag, clearing and ending biome run",
                        player.getName().getString());
                    pd.inCombat = false;
                    pd.endBiomeRun();
                    data.markDirty();
                    CombatManager.remove(player.getUuid());
                }
            }
        });

        // Clean up combat on player disconnect: handle both leader and party member cases
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var playerUuid = handler.getPlayer().getUuid();
            String playerName = handler.getPlayer().getName().getString();
            addonBonusCache.remove(playerUuid);

            // Check if this player is in someone else's party combat (non-leader)
            CombatManager leaderCm = CombatManager.getActiveCombat(playerUuid);
            // isActive() alone misses the between-level event interlude: there the leader's
            // CM has already ended combat (active=false) but is still holding this player in
            // an event gate (eventPending/intro/trader/loot/digSite). Without the
            // holdsPendingPlayer() term, a disconnect there would never release the gate and
            // the rest of the party would softlock waiting on the departed player.
            if (leaderCm != null && !leaderCm.equals(CombatManager.get(playerUuid))
                    && (leaderCm.isActive() || leaderCm.holdsPendingPlayer(playerUuid))) {
                // Party member disconnected: remove from leader's combat, notify party
                leaderCm.removePartyMember(playerUuid);
                if (leaderCm.getEventManager() != null) {
                    leaderCm.getEventManager().removeParticipant(playerUuid);
                    ServerWorld world = server.getOverworld();
                    leaderCm.getEventManager().broadcastMessage(world,
                        "\u00a7c" + playerName + " disconnected from the party.");
                }
                LOGGER.info("Party member {} disconnected during combat", playerName);
            }

            // Clean up this player's own CombatManager. hasPendingEvent() covers the leader
            // disconnecting during a between-level event interlude, where the CM is already
            // inactive but still gating the party - without it the remaining members would be
            // stranded (never sent home, gate never released).
            CombatManager cm = CombatManager.get(playerUuid);
            if (cm.isActive() || cm.hasPendingEvent()) {
                LOGGER.info("Player {} disconnected during combat, cleaning up", playerName);

                // If leader disconnects, send all remaining party members home first
                if (cm.getEventManager() != null) {
                    ServerWorld world = server.getOverworld();
                    for (ServerPlayerEntity member : cm.getEventManager().getOnlineParticipants(world)) {
                        if (!member.getUuid().equals(playerUuid)) {
                            member.sendMessage(net.minecraft.text.Text.literal(
                                "\u00a7cParty leader disconnected. Returning to hub..."), false);
                            net.minecraft.util.math.BlockPos hub = CrafticsSavedData.get(world)
                                .getHubTeleportPos(member.getUuid());
                            int memberHubY = hubLandingY(world, hub.getX(), hub.getZ(), hub.getY());
                            member.requestTeleport(hub.getX() + 0.5,
                                memberHubY != Integer.MIN_VALUE ? memberHubY : hub.getY(), hub.getZ() + 0.5);
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
            com.crackedgames.craftics.scene.SceneController.onDisconnect(playerUuid);

            // Visiting (Task 8) is transient/in-memory only, so a disconnecting player
            // must drop its bookkeeping here or it leaks forever. Two cases:
            //  - the leaver was themselves visiting someone: drop that tracking.
            //  - the leaver IS an island owner: every visitor currently on their island
            //    must be kicked to the lobby (nobody should be left standing in a
            //    dimension whose owner is now offline), then the now-visitor-free
            //    island is unloaded if empty.
            com.crackedgames.craftics.world.VisitManager.clearVisit(playerUuid);
            CrafticsSavedData disconnectData = CrafticsSavedData.get(server.getOverworld());
            if (disconnectData.getEffectiveWorldOwner(playerUuid).equals(playerUuid)) {
                com.crackedgames.craftics.world.VisitManager.onOwnerLogout(server, playerUuid);
            }

            // De-laned islands are per-owner runtime dims that should cost zero tick
            // time when empty. If this player logged out inside an island dim, unload
            // it once nobody remains. Deferred one tick via server.execute so the
            // leaving player entity is fully removed from the world's player list
            // before unloadIfEmpty checks getPlayers().isEmpty().
            net.minecraft.world.World leftWorld = handler.getPlayer().getEntityWorld();
            if (com.crackedgames.craftics.world.IslandDimensions.isIslandWorld(leftWorld)) {
                java.util.UUID leftOwner =
                    com.crackedgames.craftics.world.IslandDimensions.ownerOf(leftWorld);
                if (leftOwner != null) {
                    server.execute(() ->
                        com.crackedgames.craftics.world.IslandDimensions.unloadIfEmpty(server, leftOwner));
                }
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            var deathProtection = CrafticsComponents.DEATH_PROTECTION.get(newPlayer);
            if (!deathProtection.hasPendingRestore()) return;

            deathProtection.restoreTo(newPlayer);
            // Trinkets (Accessories slots) are snapshotted in memory on the
            // dying player's component; restore them from there so a recovery
            // compass keeps them just like the main inventory.
            com.crackedgames.craftics.compat.artifacts.AccessoriesReflect.restoreAccessories(
                newPlayer, CrafticsComponents.DEATH_PROTECTION.get(oldPlayer).getSavedAccessories());
            // Particles on respawn to confirm restoration
            if (newPlayer.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                    newPlayer.getX(), newPlayer.getY() + 1.0, newPlayer.getZ(),
                    30, 0.5, 0.8, 0.5, 0.05);
                newPlayer.getWorld().playSound(null, newPlayer.getBlockPos(),
                    net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
            newPlayer.sendMessage(Text.literal("\u00a76\u00a7lRecovery Compass \u00a7r\u00a76- Your inventory has been restored!"), false);
        });

        // Tick ALL active combat instances each server tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                CombatManager.tickAll();
            } catch (Throwable t) {
                LOGGER.error("CombatManager.tickAll() crashed; continuing server tick", t);
            }

            try {
                com.crackedgames.craftics.scene.SceneController.tickAll();
            } catch (Throwable t) {
                LOGGER.error("SceneController.tickAll() crashed; continuing server tick", t);
            }

            try {
                com.crackedgames.craftics.scene.SceneOfferStore.tick();
            } catch (Throwable t) {
                LOGGER.error("SceneOfferStore.tick() crashed; continuing server tick", t);
            }

            try {
                com.crackedgames.craftics.world.VisitManager.tick();
            } catch (Throwable t) {
                LOGGER.error("VisitManager.tick() crashed; continuing server tick", t);
            }

            try {
                com.crackedgames.craftics.combat.EventRoomCleanup.tick();
            } catch (Throwable t) {
                LOGGER.error("EventRoomCleanup.tick() crashed; continuing server tick", t);
            }

            try {
                com.crackedgames.craftics.combat.RunInviteManager.tick(server);
            } catch (Throwable t) {
                LOGGER.error("RunInviteManager.tick() crashed; continuing server tick", t);
            }

            try {
                com.crackedgames.craftics.vfx.PhaseScheduler.tickAll();
                for (net.minecraft.server.world.ServerWorld w : server.getWorlds()) {
                    com.crackedgames.craftics.vfx.VfxBlockTracker.of(w).tick(w);
                }
            } catch (Throwable t) {
                LOGGER.error("VFX tick crashed; continuing server tick", t);
            }

            // Keep the Move item locked to its hotbar slot for every player. Cheap
            // when nothing's out of place; auto-repairs drag/drop/Q-throw attempts.
            try {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    com.crackedgames.craftics.item.MoveSlotManager.enforce(p);
                }
            } catch (Throwable t) {
                LOGGER.error("MoveSlotManager.enforce crashed; continuing server tick", t);
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
                        // Change detection fingerprint: payload data PLUS the combat
                        // effect handler names. Effect-only artifacts (Cross Necklace,
                        // Umbrella, ...) contribute no stat ints, so without the names
                        // an equip/unequip mid-combat would go completely unnoticed.
                        StringBuilder fp = new StringBuilder(newData);
                        for (var eff : addonMods.getCombatEffects()) {
                            fp.append("|").append(eff.name());
                        }
                        String fingerprint = fp.toString();
                        // Only act if changed (avoid unnecessary packets)
                        String lastData = addonBonusCache.getOrDefault(p.getUuid(), "");
                        if (!fingerprint.equals(lastData)) {
                            addonBonusCache.put(p.getUuid(), fingerprint);
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                                new com.crackedgames.craftics.network.AddonBonusSyncPayload(newData));
                            // Mid-combat equipment change: refresh the active fight so
                            // stat artifacts (Running Shoes, ...) apply immediately.
                            CombatManager cm = CombatManager.getActiveCombat(p.getUuid());
                            if (cm.isActive()) {
                                cm.onAddonEquipmentChanged(p);
                            }
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
                if (com.crackedgames.craftics.world.VisitProtection.isForeignVisitor(player)) return false;
                if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp
                        && CombatManager.get(sp).isActive()) {
                    return false; // cancel block breaking for THIS player during combat
                }
                // Protect central lobby (floating island, prevent breaking the platform)
                if (HubRoomBuilder.isLobbyProtected(pos)) return false;
                // Personal worlds are fully modifiable, no shell protection
                return true;
            }
        );

        // Infinite mode: registers the boss:infinite AI key and the rest-room
        // continue-bell interaction.
        com.crackedgames.craftics.combat.InfiniteRunManager.init();

        // Look-only visitors: block/entity interaction and block-hit (attack) are denied
        // in a foreign island dim; PASS lets vanilla/other handlers decide otherwise.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
            com.crackedgames.craftics.world.VisitProtection.isForeignVisitor(player)
                ? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
            com.crackedgames.craftics.world.VisitProtection.isForeignVisitor(player)
                ? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);

        // Deferred copper-tier registration. WeaponRegistry needs the actual Item
        // instances from copperagebackport, but Fabric doesn't guarantee that mod's
        // main entrypoint has run by the time ours does, so finish the registration
        // on SERVER_STARTING, which fires after every mod's main-phase init.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(
            server -> {
                com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.registerDeferred();
                com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat.registerDeferred();
                com.crackedgames.craftics.compat.instruments.InstrumentsCompat.registerDeferred();
                com.crackedgames.craftics.compat.paladins.PaladinsCompat.registerDeferred();
                com.crackedgames.craftics.compat.simplyswords.SimplySwordsCompat.registerDeferred();
            });

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

            // /craftics warp: Artifacts Warp Drive activator. Arms the next attack to
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
                    src.sendFeedback(() -> Text.literal("§dWarp Drive is already armed, your next attack will warp."), false);
                    return 1;
                }
                if (cm.armWarpDrive()) {
                    src.sendFeedback(() -> Text.literal("§5§lWarp Drive armed! §r§dYour next attack ignores range and warps you adjacent to the target."), false);
                    return 1;
                }
                src.sendError(Text.literal("§cCould not arm Warp Drive."));
                return 0;
            }));

            // /craftics hp_per_level <on|off|status>: toggle per-level HP scaling
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

            // /craftics unlock_all: unlock every biome (op only)
            root.then(CommandManager.literal("unlock_all").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                ServerWorld overworld = src.getServer().getOverworld();
                CrafticsSavedData data = CrafticsSavedData.get(overworld);
                CrafticsSavedData.PlayerData pd = data.getPlayerData(cmdPlayer.getUuid());
                pd.initBranchIfNeeded();
                int totalBiomes = com.crackedgames.craftics.level.campaign.CampaignManager.totalBiomes();
                pd.highestBiomeUnlocked = totalBiomes;
                data.markDirty();
                updateWorldIcon(src.getServer(), pd);
                src.sendFeedback(() -> Text.literal("§aUnlocked all " + totalBiomes + " biomes."), true);
                return 1;
            }));

            // /craftics reset_combat: force-clear corrupted combat state (inCombat, biome run)
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
                com.crackedgames.craftics.world.HubTeleports.toLobby(cmdPlayer);
                cmdPlayer.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                cmdPlayer.clearStatusEffects();
                src.sendFeedback(() -> Text.literal("§aCombat state reset. Teleported to hub."), true);
                return 1;
            }));

            // /craftics rebuild_arenas [biome]: wipe and regenerate arena blocks,
            // either all of them or just one biome's worth. Useful when a
            // schematic change or world-edit has left the pre-built arenas in a
            // broken state, since the next combat will otherwise still scan the bad
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
                    "§aRebuilt §e" + finalCount + "§a arena" + (finalCount == 1 ? "" : "s") + "."), true);
                return 1;
            };

            // Gate is config-driven: admin-only when rebuildArenasAdminOnly is set,
            // otherwise open to any player (they can only rebuild their own world).
            // The predicate reads the config live so toggling it takes effect without
            // re-registering commands.
            var rebuildArenasNode = CommandManager.literal("rebuild_arenas")
                .requires(src -> !CONFIG.rebuildArenasAdminOnly() || src.hasPermissionLevel(2))
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
                        "§aRebuilt §e" + finalCount + "§a " + biome.biomeId + " arena"
                            + (finalCount == 1 ? "" : "s") + "."), true);
                    return 1;
                }));
            }
            root.then(rebuildArenasNode);

            // /craftics reset_biomes: reset current biome level progress back to level 1
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

            // /craftics set_level <level>: set player progression level
            root.then(CommandManager.literal("set_level").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        ServerPlayerEntity player = src.getPlayerOrThrow();
                        int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level");
                        var progression = com.crackedgames.craftics.combat.PlayerProgression.get(src.getServer().getOverworld());
                        var stats = progression.getStats(player);
                        stats.level = level;
                        // Credit the stat points this level owes; affinity points are
                        // derived from the level and become available in the respec menu.
                        stats.reconcilePoints();
                        progression.saveStats(player);
                        com.crackedgames.craftics.network.ModNetworking.syncPlayerStats(player);
                        src.sendFeedback(() -> Text.literal("§aSet player level to " + level
                            + " (" + stats.unspentPoints + " unspent stat points)."), true);
                        return 1;
                    })));

            // /craftics info: display current save state
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

            // /craftics heal: fully heal the player
            root.then(CommandManager.literal("heal").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity player = src.getPlayerOrThrow();
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(5.0f);
                src.sendFeedback(() -> Text.literal("§aFully healed."), true);
                return 1;
            }));

            // /craftics kill_enemies: kill all enemies in current combat
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

            // /craftics skip_level: win current combat instantly
            root.then(CommandManager.literal("skip_level").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayer(); if (cmdPlayer == null) { src.sendError(Text.literal("§cMust be a player.")); return 0; } CombatManager cm = CombatManager.get(cmdPlayer);
                if (!cm.isActive()) {
                    src.sendError(Text.literal("§cNo active combat."));
                    return 0;
                }
                cm.adminWinCombat();
                src.sendFeedback(() -> Text.literal("§aLevel skipped, victory triggered."), true);
                return 1;
            }));

            // /craftics set_ap <amount>: set AP remaining during combat
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

            // /craftics set_speed <amount>: set move points remaining during combat
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

            // /craftics set_ngplus <level>: set NG+ cycle
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

            // /craftics set_stat <stat> <value>: set a specific stat's allocated points
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
                            com.crackedgames.craftics.network.ModNetworking.syncPlayerStats(player);
                            src.sendFeedback(() -> Text.literal("§aSet " + stat.displayName + " to " + value + " (was " + oldValue + ")."), true);
                            return 1;
                        }))));

            // /craftics reset_stats: reset all player stats to defaults
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
                com.crackedgames.craftics.network.ModNetworking.syncPlayerStats(player);
                int refunded = stats.unspentPoints;
                src.sendFeedback(() -> Text.literal("§aReset all stats. " + refunded + " unspent points available."), true);
                return 1;
            }));

            // /craftics give <preset>: give a set of gear
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

            // /craftics combat_info: show detailed combat state
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

            // /craftics force_event <event>: force the next between-level event
            var forceEventNode = CommandManager.literal("force_event");
            // Built-in event names: bare strings that the dispatch in
            // CombatManager.rollEvent compares against. Must match the
            // {@code forced.equals("...")} arms over there exactly.
            java.util.List<String> eventNames = new java.util.ArrayList<>(java.util.List.of(
                "ambush", "trial", "ominous_trial", "shrine", "traveler", "vault", "dig_site", "enchanter", "trader", "piglin_barter", "none"
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

            // /craftics build_arena <shape> [radius]: terraform a flat polygon
            // arena around the caster and drop ArenaCornerBlock markers at each
            // vertex. The polygon shape ships in ArenaShapes; the radius
            // defaults to 8 (a 17×17 bounding box). Wipes blocks inside the
            // polygon up to 6 above floor level so existing terrain doesn't
            // poke into the playspace. Caster's Y becomes the arena floor.
            var buildArenaNode = CommandManager.literal("build_arena")
                .requires(src -> src.hasPermissionLevel(2));
            for (String preset : com.crackedgames.craftics.level.ArenaShapes.PRESET_NAMES) {
                var presetNode = CommandManager.literal(preset);
                presetNode.executes(ctx -> buildArenaCommand(ctx.getSource(), preset, 8));
                presetNode.then(CommandManager.argument("radius",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(2, 64))
                    .executes(ctx -> buildArenaCommand(ctx.getSource(), preset,
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"))));
                buildArenaNode.then(presetNode);
            }
            root.then(buildArenaNode);

            registerPartyCommands(root);
            registerWorldCommands(root);
            com.crackedgames.craftics.command.CrafticsServerCommands.register(root);

            // /craftics infinite [start|stop|top] - the command-line door into
            // infinite mode (the Level Select block's button is the other one).
            var startInfinite = (com.mojang.brigadier.Command<ServerCommandSource>) (ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                com.crackedgames.craftics.combat.RunInviteManager.requestStart(player,
                    com.crackedgames.craftics.combat.InfiniteRunManager.START_ID);
                return 1;
            });
            root.then(CommandManager.literal("infinite")
                .executes(startInfinite)
                .then(CommandManager.literal("start").executes(startInfinite))
                .then(CommandManager.literal("top").executes(ctx -> {
                    com.crackedgames.craftics.combat.InfiniteRunManager
                        .sendLeaderboard(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("stop").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    CrafticsSavedData stopData = CrafticsSavedData.get(player.getServer().getOverworld());
                    if (stopData.getPlayerData(player.getUuid()).infiniteRunHost.isEmpty()) {
                        ctx.getSource().sendError(Text.literal("§cYou're not in an infinite run."));
                        return 0;
                    }
                    if (CombatManager.get(player).isActive()
                            || CombatManager.getActiveCombat(player.getUuid()) != null) {
                        ctx.getSource().sendError(Text.literal(
                            "§cFinish (or flee) the current fight first."));
                        return 0;
                    }
                    com.crackedgames.craftics.combat.InfiniteRunManager.onHomeExit(player);
                    com.crackedgames.craftics.world.HubTeleports.toHub(player);
                    return 1;
                })));

            dispatcher.register(root);

            // Shortcut commands

            // /new and /craftics new: create a new personal island
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
                // demand the first time the player enters that level; see
                // ArenaPreGenerator.ensureArena called from CombatManager.buildArena.
                // The old path pre-built every arena here synchronously, which froze
                // lower-end servers for multiple seconds.
                overworld.getServer().execute(() -> {
                    CrafticsSavedData d = CrafticsSavedData.get(finalOverworld);
                    d.allocateWorldSlot(playerUuid); // dormant marker; no longer a coordinate
                    // Build the hub in the OWNER'S island dim at the fixed hub origin.
                    ServerWorld islandWorld = com.crackedgames.craftics.world.IslandDimensions
                        .getOrCreate(finalOverworld.getServer(), playerUuid);
                    net.minecraft.util.math.BlockPos hubCenter = d.getHubOrigin(playerUuid);
                    net.minecraft.util.math.BlockPos spawnPos = HubRoomBuilder.build(islandWorld, hubCenter);
                    CrafticsSavedData.PlayerData pd = d.getPlayerData(playerUuid);
                    pd.personalHubBuilt = true;
                    pd.personalHubVersion = HubRoomBuilder.HUB_VERSION;
                    pd.hubSpawnX = spawnPos.getX();
                    pd.hubSpawnY = spawnPos.getY();
                    pd.hubSpawnZ = spawnPos.getZ();
                    d.markDirty();

                    // Flip the lazy-mode flag on (no actual arenas built).
                    com.crackedgames.craftics.level.ArenaPreGenerator.generateAll(islandWorld, playerUuid);

                    // Everything is ready: teleport (cross-dim into the island) and dismiss loading screen
                    var p = finalOverworld.getServer().getPlayerManager().getPlayer(playerUuid);
                    if (p != null) {
                        com.crackedgames.craftics.world.HubTeleports.toHub(p);
                        p.sendMessage(Text.literal(
                            "\u00a7a\u00a7lPersonal world created! \u00a7r\u00a7aUse \u00a7e/home\u00a7a to return anytime."), false);
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

            // /home and /craftics home: teleport to personal hub
            var shortcutHomeCmd = (com.mojang.brigadier.Command<ServerCommandSource>) (ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                // Always operate against the overworld; personal hubs only exist there.
                ServerWorld overworld = player.getServer().getOverworld();
                CrafticsSavedData data = CrafticsSavedData.get(overworld);

                // Check effective world owner (party leader's world if in a party)
                java.util.UUID effectiveOwner = data.getEffectiveWorldOwner(player.getUuid());
                if (!data.hasPersonalWorld(effectiveOwner)) {
                    ctx.getSource().sendError(Text.literal(
                        "\u00a7cYou don't have a personal world yet. Use \u00a7e/new\u00a7c to create one."));
                    return 0;
                }

                // /home is an escape hatch out of combat, which trivializes
                // a tough fight, so block it mid-combat for non-ops. Ops can
                // still teleport home for debugging / world maintenance.
                CombatManager playerCm = CombatManager.get(player);
                CombatManager activeCm = CombatManager.getActiveCombat(player.getUuid());
                boolean inCombat = (activeCm != null && activeCm.isActive()) || playerCm.isActive();
                if (inCombat && !ctx.getSource().hasPermissionLevel(2)) {
                    ctx.getSource().sendError(Text.literal(
                        "§cYou can't use §e/home§c during combat."));
                    return 0;
                }

                // Handle party combat: gracefully remove this player instead of ending for everyone
                if (activeCm != null && activeCm.isActive()) {
                    // Player is in party combat, remove them gracefully
                    activeCm.leavePartyCombat(player);
                } else if (playerCm.isActive()) {
                    // Solo combat, end normally
                    playerCm.endCombat();
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                        new com.crackedgames.craftics.network.ExitCombatPayload(false));
                }

                // Show loading screen, load chunks, then teleport
                ServerPlayNetworking.send(player,
                    new com.crackedgames.craftics.network.LoadingScreenPayload(
                        true, "Returning Home...", ""));

                final ServerPlayerEntity homePlayer = player;
                overworld.getServer().execute(() -> {
                    // Infinite mode: /home banks and leaves the run (host = run over,
                    // member = steps out). Must run BEFORE the teleport so the stash
                    // restore replaces the run items while state is still coherent.
                    com.crackedgames.craftics.combat.InfiniteRunManager.onHomeExit(homePlayer);
                    // HubTeleports.toHub resolves the owner's island dim (re-opening it
                    // if unloaded), clamps the landing Y against that world, and unloads
                    // the island the player is leaving behind once it's empty - the same
                    // cross-dim path every other hub/lobby return already uses.
                    com.crackedgames.craftics.world.HubTeleports.toHub(homePlayer);
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

            // /craftics dev_arena: test arena with every obstacle type
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

            // /craftics cleanup_events: sweep every world's fixed event room for stray
            // trader NPCs (orphaned piglins / zombified piglins / villagers / wandering
            // traders). Fixes existing worlds on demand instead of waiting for the next
            // event to build at the room. Live event rooms are skipped.
            dispatcher.register(CommandManager.literal("craftics").then(
                CommandManager.literal("cleanup_events").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ServerWorld world = player.getServerWorld();
                    CrafticsSavedData data = CrafticsSavedData.get(world);

                    java.util.List<net.minecraft.util.math.BlockPos> origins = new java.util.ArrayList<>();
                    for (java.util.UUID id : data.getAllPlayerIds()) {
                        net.minecraft.util.math.BlockPos o = data.getTraderOrigin(id);
                        if (o != null) origins.add(o);
                    }
                    if (origins.isEmpty()) {
                        ctx.getSource().sendFeedback(() -> Text.literal(
                            "§eNo event rooms to clean (no personal worlds yet)."), false);
                        return 0;
                    }

                    int roomCount = origins.size();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§7Cleaning " + roomCount + " event room(s)..."), false);
                    var src = ctx.getSource();
                    com.crackedgames.craftics.combat.EventRoomCleanup.request(world, origins, removed ->
                        src.sendFeedback(() -> Text.literal(
                            "§aEvent cleanup complete: removed " + removed
                                + " stray NPC(s) from " + roomCount + " room(s)."), false));
                    return 1;
                })
            ));

            // /lobby: shortcut to central lobby
            dispatcher.register(CommandManager.literal("lobby").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                CombatManager cm = CombatManager.get(player);
                if (cm.isActive()) cm.endCombat();

                com.crackedgames.craftics.world.HubTeleports.toLobby(player);
                player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                ctx.getSource().sendFeedback(() -> Text.literal("\u00a7aTeleported to the lobby."), false);
                return 1;
            }));
        });
    }

    private void registerWorldCommands(com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> root) {
        var worldNode = CommandManager.literal("world");

        // /craftics world create: create a personal world
        worldNode.then(CommandManager.literal("create").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = player.getServerWorld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);

            if (data.hasPersonalWorld(player.getUuid())) {
                ctx.getSource().sendError(Text.literal("\u00a7cYou already have a personal world!"));
                return 0;
            }

            int slot = data.allocateWorldSlot(player.getUuid()); // dormant marker; no longer a coordinate
            net.minecraft.util.math.BlockPos hubCenter = data.getHubOrigin(player.getUuid());

            // Build the hub in the OWNER'S island dim, then cross-dim teleport into it.
            ServerWorld islandWorld = com.crackedgames.craftics.world.IslandDimensions
                .getOrCreate(overworld.getServer(), player.getUuid());
            net.minecraft.util.math.BlockPos spawnPos = HubRoomBuilder.build(islandWorld, hubCenter);
            CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
            pd.personalHubBuilt = true;
            pd.personalHubVersion = HubRoomBuilder.HUB_VERSION;
            pd.hubSpawnX = spawnPos.getX();
            pd.hubSpawnY = spawnPos.getY();
            pd.hubSpawnZ = spawnPos.getZ();
            data.markDirty();

            com.crackedgames.craftics.world.HubTeleports.toHub(player);

            ctx.getSource().sendFeedback(() -> Text.literal(
                "\u00a7a\u00a7l\u2726 Personal world created! \u00a7r\u00a7a(Slot " + slot + ")"), true);
            ctx.getSource().sendFeedback(() -> Text.literal(
                "\u00a77Use \u00a7e/craftics world home\u00a77 to return here anytime."), false);
            return 1;
        }));

        // /craftics world home: teleport to personal hub
        worldNode.then(CommandManager.literal("home").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = player.getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);

            if (!data.hasPersonalWorld(player.getUuid())) {
                ctx.getSource().sendError(Text.literal(
                    "\u00a7cYou don't have a personal world yet. Use \u00a7e/craftics world create\u00a7c first."));
                return 0;
            }

            // Block mid-combat hub teleport for non-ops (matches /home shortcut).
            CombatManager cm = CombatManager.get(player);
            CombatManager activeCm = CombatManager.getActiveCombat(player.getUuid());
            boolean inCombat = (activeCm != null && activeCm.isActive()) || cm.isActive();
            if (inCombat && !ctx.getSource().hasPermissionLevel(2)) {
                ctx.getSource().sendError(Text.literal(
                    "\u00a7cYou can't teleport home during combat."));
                return 0;
            }

            if (cm.isActive()) cm.endCombat();

            // HubTeleports.toHub resolves the effective owner (party leader when in a
            // party) and the island dim itself, same as the manual lookup this replaced.
            com.crackedgames.craftics.world.HubTeleports.toHub(player);
            player.changeGameMode(GameMode.SURVIVAL);
            ctx.getSource().sendFeedback(() -> Text.literal("\u00a7aTeleported to your personal hub."), false);
            return 1;
        }));

        // /craftics world undorepair: revert the briefly-shipped automatic
        // hollow-island fill on YOUR island. Removes exactly the buried
        // schematic blocks that fill could have placed - and only where the
        // world block still matches the schematic - so player-placed blocks
        // and everything else are untouched.
        worldNode.then(CommandManager.literal("undorepair").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = player.getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);
            if (!data.hasPersonalWorld(player.getUuid())) {
                ctx.getSource().sendError(Text.literal("§cYou don't have a personal world."));
                return 0;
            }
            ServerWorld islandWorld = com.crackedgames.craftics.world.IslandDimensions
                .getOrCreate(player.getServer(), player.getUuid());
            int removed = HubRoomBuilder.undoRepair(islandWorld, data.getHubOrigin(player.getUuid()));
            ctx.getSource().sendFeedback(() -> Text.literal(removed > 0
                ? "§aRemoved §e" + removed + "§a repair-filled blocks from your island."
                : "§7Nothing to undo - no repair-filled blocks found."), false);
            return 1;
        }));

        // /craftics world repairhollow: opt IN to filling a hollow island
        // interior (islands built before the hollow-generation fix). Fills only
        // buried schematic blocks where the world currently has air.
        worldNode.then(CommandManager.literal("repairhollow").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ServerWorld overworld = player.getServer().getOverworld();
            CrafticsSavedData data = CrafticsSavedData.get(overworld);
            if (!data.hasPersonalWorld(player.getUuid())) {
                ctx.getSource().sendError(Text.literal("§cYou don't have a personal world."));
                return 0;
            }
            ServerWorld islandWorld = com.crackedgames.craftics.world.IslandDimensions
                .getOrCreate(player.getServer(), player.getUuid());
            int filled = HubRoomBuilder.repair(islandWorld, data.getHubOrigin(player.getUuid()));
            ctx.getSource().sendFeedback(() -> Text.literal(filled > 0
                ? "§aFilled §e" + filled + "§a hollow interior blocks on your island."
                : "§7Nothing to fill - your island interior is already solid."), false);
            return 1;
        }));

        // /craftics world lobby: teleport to central lobby
        worldNode.then(CommandManager.literal("lobby").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

            CombatManager cm = CombatManager.get(player);
            if (cm.isActive()) cm.endCombat();

            com.crackedgames.craftics.world.HubTeleports.toLobby(player);
            player.changeGameMode(GameMode.SURVIVAL);
            ctx.getSource().sendFeedback(() -> Text.literal("\u00a7aTeleported to the central lobby."), false);
            return 1;
        }));

        // /craftics world visit <player>: legacy alias, delegates to VisitManager
        // (Task 8). Party members get the instant fast-path; anyone else now gets a
        // real invite prompt to the target instead of the old hard "party only" block.
        worldNode.then(CommandManager.literal("visit")
            .then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "player");
                    com.crackedgames.craftics.world.VisitManager.request(player, target);
                    return 1;
                })));

        root.then(worldNode);
    }

    /** Implementation for {@code /craftics build_arena <shape> [radius]}.
     *  Centers a polygon preset from {@link com.crackedgames.craftics.level.ArenaShapes}
     *  on the caster's tile, clears blocks inside the polygon up to 6 above
     *  floor level, paints a stone floor on every interior tile, and drops
     *  {@code ArenaCornerBlock} markers at each vertex. The next time an arena
     *  is built from this area, ArenaBuilder picks up the corners and serves
     *  the shape as the in-bounds polygon mask. */
    private static int buildArenaCommand(net.minecraft.server.command.ServerCommandSource src,
                                         String preset, int radius) {
        ServerPlayerEntity caster = src.getPlayer();
        if (caster == null) {
            src.sendError(Text.literal("§cMust be a player."));
            return 0;
        }
        java.util.List<com.crackedgames.craftics.level.ArenaShapes.Offset> offsets =
            com.crackedgames.craftics.level.ArenaShapes.get(preset, radius);
        if (offsets == null || offsets.isEmpty()) {
            src.sendError(Text.literal("§cUnknown shape: " + preset));
            return 0;
        }
        net.minecraft.server.world.ServerWorld world =
            (net.minecraft.server.world.ServerWorld) caster.getEntityWorld();
        net.minecraft.util.math.BlockPos centerBlock = caster.getBlockPos();
        int floorY = centerBlock.getY() - 1;
        int cx = centerBlock.getX();
        int cz = centerBlock.getZ();

        // Bounding box from offsets. Offsets are the vertex positions (the outer
        // border ring); the playable interior is the outline eroded one tile inward
        // (below), matching ArenaBuilder's loader, so the markers end up just outside
        // the floor at every vertex, convex tips and concave armpits alike.
        int minDx = Integer.MAX_VALUE, maxDx = Integer.MIN_VALUE;
        int minDz = Integer.MAX_VALUE, maxDz = Integer.MIN_VALUE;
        for (var o : offsets) {
            minDx = Math.min(minDx, o.dx());
            maxDx = Math.max(maxDx, o.dx());
            minDz = Math.min(minDz, o.dz());
            maxDz = Math.max(maxDz, o.dz());
        }
        int gridMinX = cx + minDx;
        int gridMaxX = cx + maxDx;
        int gridMinZ = cz + minDz;
        int gridMaxZ = cz + maxDz;

        // Sort vertices by angle around centroid (same algorithm ArenaBuilder
        // uses) so consecutive vertices form a closed loop for the point-in-
        // polygon test below.
        double centroidX = 0, centroidZ = 0;
        for (var o : offsets) { centroidX += o.dx(); centroidZ += o.dz(); }
        centroidX /= offsets.size();
        centroidZ /= offsets.size();
        final double finalCx = centroidX, finalCz = centroidZ;
        java.util.List<com.crackedgames.craftics.level.ArenaShapes.Offset> sortedOffsets =
            new java.util.ArrayList<>(offsets);
        sortedOffsets.sort((a, b) -> {
            double angA = Math.atan2(a.dz() - finalCz, a.dx() - finalCx);
            double angB = Math.atan2(b.dz() - finalCz, b.dx() - finalCx);
            return Double.compare(angA, angB);
        });

        int placedFloor = 0;
        int clearedAir = 0;
        int n = sortedOffsets.size();
        int gw = gridMaxX - gridMinX + 1;
        int gh = gridMaxZ - gridMinZ + 1;

        // Vertex tiles get a corner marker (below), never painted floor, mirroring
        // the loader, which clears marker tiles from the playable mask. Keeps markers
        // off the floor (incl. a concave armpit vertex that survives erosion) and
        // stops placedFloor over-counting tiles a marker would overwrite.
        java.util.Set<Long> vertexKeys = new java.util.HashSet<>();
        for (var o : sortedOffsets) {
            vertexKeys.add(net.minecraft.util.math.BlockPos.asLong(cx + o.dx(), floorY, cz + o.dz()));
        }

        // Outer mask over the full vertex bbox (point-in-polygon). Integer-grid
        // sampling with the outline grown a hair outward from its centroid, so
        // axis-aligned edges never sit on a sample point; this keeps symmetric
        // shapes symmetric and matches ArenaBuilder's loader sampling exactly, so
        // the previewed floor and the eventual playable mask agree.
        final double GROW = 0.001;
        double[] vx = new double[n];
        double[] vz = new double[n];
        for (int i = 0; i < n; i++) {
            double x = cx + sortedOffsets.get(i).dx();
            double z = cz + sortedOffsets.get(i).dz();
            vx[i] = x + (x - (cx + finalCx)) * GROW;
            vz[i] = z + (z - (cz + finalCz)) * GROW;
        }
        boolean[][] outer = new boolean[gw][gh];
        for (int tx = 0; tx < gw; tx++) {
            for (int tz = 0; tz < gh; tz++) {
                double px = gridMinX + tx;
                double pz = gridMinZ + tz;
                boolean inside = false;
                for (int i = 0, j = n - 1; i < n; j = i++) {
                    boolean intersects = ((vz[i] > pz) != (vz[j] > pz))
                        && (px < (vx[j] - vx[i]) * (pz - vz[i]) / (vz[j] - vz[i]) + vx[i]);
                    if (intersects) inside = !inside;
                }
                outer[tx][tz] = inside;
            }
        }
        // Paint floor + clear air on the eroded interior (one tile inside the
        // outline; 4-neighbour erosion), matching ArenaBuilder's loader so the
        // corner markers placed at the vertices sit one tile outside the floor.
        for (int tx = 0; tx < gw; tx++) {
            for (int tz = 0; tz < gh; tz++) {
                if (!outer[tx][tz]) continue;
                boolean playable = tx > 0 && tx < gw - 1 && tz > 0 && tz < gh - 1
                    && outer[tx - 1][tz] && outer[tx + 1][tz]
                    && outer[tx][tz - 1] && outer[tx][tz + 1];
                if (!playable) continue;
                int wx = gridMinX + tx;
                int wz = gridMinZ + tz;
                // A vertex marker owns this tile (loader treats it as border), so don't
                // paint floor under it, so build and reload agree and the count is honest.
                if (vertexKeys.contains(net.minecraft.util.math.BlockPos.asLong(wx, floorY, wz))) continue;
                net.minecraft.util.math.BlockPos floorPos =
                    new net.minecraft.util.math.BlockPos(wx, floorY, wz);
                world.setBlockState(floorPos, net.minecraft.block.Blocks.STONE.getDefaultState(), 3);
                placedFloor++;
                // Clear 6 blocks of air above floor so existing terrain (trees,
                // hills, schematic decoration) doesn't poke into the arena.
                for (int dy = 1; dy <= 6; dy++) {
                    net.minecraft.util.math.BlockPos clearPos =
                        new net.minecraft.util.math.BlockPos(wx, floorY + dy, wz);
                    if (!world.getBlockState(clearPos).isAir()) {
                        world.setBlockState(clearPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                        clearedAir++;
                    }
                }
            }
        }

        // Drop ArenaCornerBlock at each vertex AT floor level: markers double as
        // floor-plane border tiles, matching the DIAMOND/EMERALD convention and what
        // the loader expects (arenaFloorY = max marker Y). A stone block underneath
        // keeps the marker from floating over a cleared tile.
        for (var o : sortedOffsets) {
            int wx = cx + o.dx();
            int wz = cz + o.dz();
            net.minecraft.util.math.BlockPos cornerPos =
                new net.minecraft.util.math.BlockPos(wx, floorY, wz);
            world.setBlockState(cornerPos,
                com.crackedgames.craftics.block.ModBlocks.ARENA_CORNER_BLOCK.getDefaultState(), 3);
            net.minecraft.util.math.BlockPos supportPos =
                new net.minecraft.util.math.BlockPos(wx, floorY - 1, wz);
            if (world.getBlockState(supportPos).isAir()) {
                world.setBlockState(supportPos, net.minecraft.block.Blocks.STONE.getDefaultState(), 3);
            }
        }

        final int floorCount = placedFloor;
        final int clearCount = clearedAir;
        final int cornerCount = n;
        src.sendFeedback(() -> Text.literal(
            "§aBuilt " + preset + " arena (radius " + radius + "): "
            + cornerCount + " corner markers, "
            + floorCount + " floor tiles, "
            + clearCount + " air clears."), true);
        return 1;
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
     * Highest safe standing Y in the hub column at {@code (x,z)}: scans DOWN from well above
     * the island for the highest solid block with air above it, so the player ALWAYS lands on
     * the top surface. The old logic scanned UP from the stored hub Y and stopped at the first
     * solid-with-air-above, which dropped players onto a hollow island's interior floor (or
     * under the island) whenever that stored Y sat below the surface. Returns
     * {@link Integer#MIN_VALUE} if the whole column is empty.
     */
    public static int hubLandingY(ServerWorld world, int x, int z, int fallbackY) {
        net.minecraft.util.math.BlockPos.Mutable probe =
            new net.minecraft.util.math.BlockPos.Mutable(x, fallbackY, z);
        int top = fallbackY + 96;     // comfortably above any hub island top
        int bottom = fallbackY - 64;  // and below it
        for (int yy = top; yy > bottom; yy--) {
            probe.setY(yy);
            net.minecraft.block.BlockState st = world.getBlockState(probe);
            if (!st.isAir() && st.isSolidBlock(world, probe)
                    && world.getBlockState(probe.up()).isAir()) {
                return yy + 1; // stand on top of the highest solid block
            }
        }
        return Integer.MIN_VALUE;
    }

    // teleportToHub(player, island, hub) and its non-void-world fallback
    // teleportToRespawn were removed here (Task 7): every caller now routes through
    // HubTeleports.toHub/adminTeleport, which cover the same clamp-landing-Y +
    // cross-dim-move logic. The non-void-world guard those two methods existed for
    // is dead in the island-dim architecture - IslandDimensions.getOrCreate always
    // builds a VoidChunkGenerator world, so the "island" argument could never be a
    // mis-resolved non-Craftics dimension by the time it reached these methods.

    /**
     * Writes the biome icon of the player's highest unlocked biome as the world's icon.png,
     * so the world-select screen shows progression at a glance. No-ops on non-Craftics worlds.
     */
    public static void updateWorldIcon(net.minecraft.server.MinecraftServer server, CrafticsSavedData.PlayerData pd) {
        if (!(server.getOverworld().getChunkManager().getChunkGenerator() instanceof VoidChunkGenerator)) return;
        try {
            pd.initBranchIfNeeded();
            java.util.List<String> fullPath = com.crackedgames.craftics.level.campaign.CampaignManager
                .orderedBiomeIds(Math.max(0, pd.branchChoice));
            if (fullPath.isEmpty()) return; // no active campaign, nothing to index
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