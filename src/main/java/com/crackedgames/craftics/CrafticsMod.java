package com.crackedgames.craftics;

import com.crackedgames.craftics.block.ModBlocks;
import com.crackedgames.craftics.block.ModScreenHandlers;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.network.ModNetworking;
import com.crackedgames.craftics.world.CrafticsSavedData;
import com.crackedgames.craftics.world.HubRoomBuilder;
import com.crackedgames.craftics.world.VoidChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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

    @Override
    public void onInitialize() {
        LOGGER.info("Craftics initializing...");

        // owo-lib config
        CONFIG = CrafticsConfigWrapper.createAndLoad();

        // Register custom chunk generator codec
        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of(MOD_ID, "void"), VoidChunkGenerator.CODEC);

        // Register blocks, items, screen handlers, and networking
        ModBlocks.register();
        com.crackedgames.craftics.item.ModItems.register();
        ModScreenHandlers.register();
        ModNetworking.registerServer();

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

        // When the overworld loads, check if we need to build the hub
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (world.getChunkManager().getChunkGenerator() instanceof VoidChunkGenerator) {
                    CrafticsSavedData data = CrafticsSavedData.get(world);
                    if (!data.hubBuilt || data.hubVersion < HubRoomBuilder.HUB_VERSION) {
                        HubRoomBuilder.build(world);
                        data.hubBuilt = true;
                        data.hubVersion = HubRoomBuilder.HUB_VERSION;
                        data.markDirty();
                    }
                    // Set to daytime on load
                    world.setTimeOfDay(6000);
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

                // Give guide book on first join (if they don't already have one)
                boolean hasBook = false;
                for (int i = 0; i < player.getInventory().size(); i++) {
                    if (player.getInventory().getStack(i).getItem() == com.crackedgames.craftics.item.ModItems.GUIDE_BOOK) {
                        hasBook = true;
                        break;
                    }
                }
                if (!hasBook) {
                    player.giveItemStack(new net.minecraft.item.ItemStack(com.crackedgames.craftics.item.ModItems.GUIDE_BOOK));
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
                CrafticsSavedData data = CrafticsSavedData.get(overworld);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new com.crackedgames.craftics.network.PlayerStatsSyncPayload(
                        stats.level, stats.unspentPoints, statData.toString(), data.emeralds
                    ));

                // If the player disconnected mid-battle, restart the fight after they load in
                data.loadPlayerIntoLegacy(player.getUuid());
                updateWorldIcon(server, data);
                if (data.inCombat && data.isInBiomeRun()) {
                    final ServerPlayerEntity rejoinPlayer = player;
                    final ServerWorld rejoinWorld = overworld;
                    // Schedule 2-tick delay so client finishes loading before we send combat packets
                    server.execute(() -> server.execute(() -> {
                        try {
                            com.crackedgames.craftics.level.BiomeTemplate biome = null;
                            for (var b : com.crackedgames.craftics.level.BiomeRegistry.getAllBiomes()) {
                                if (b.biomeId.equals(data.activeBiomeId)) { biome = b; break; }
                            }
                            if (biome == null) { data.inCombat = false; data.markDirty(); return; }

                            int levelIndex = data.activeBiomeLevelIndex;
                            int globalLevel = biome.startLevel + levelIndex;
                            com.crackedgames.craftics.level.LevelDefinition levelDef =
                                com.crackedgames.craftics.level.LevelRegistry.get(globalLevel, data.branchChoice);
                            if (levelDef == null) { data.inCombat = false; data.markDirty(); return; }

                            com.crackedgames.craftics.core.GridArena arena =
                                com.crackedgames.craftics.level.ArenaBuilder.build(rejoinWorld, levelDef);
                            net.minecraft.util.math.BlockPos startPos = arena.getPlayerStartBlockPos();
                            net.minecraft.util.math.BlockPos origin = arena.getOrigin();
                            float cameraYaw = com.crackedgames.craftics.level.ArenaBuilder.consumePendingCameraYaw();

                            rejoinPlayer.requestTeleport(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(rejoinPlayer,
                                new com.crackedgames.craftics.network.EnterCombatPayload(
                                    origin.getX(), origin.getY(), origin.getZ(),
                                    arena.getWidth(), arena.getHeight(), cameraYaw
                                ));
                            CombatManager.get(rejoinPlayer).startCombat(rejoinPlayer, arena, levelDef);
                            LOGGER.info("Restarted combat for {} (biome {}, level {})",
                                rejoinPlayer.getName().getString(), data.activeBiomeId, levelIndex + 1);
                        } catch (Exception ex) {
                            LOGGER.error("Failed to restart combat for {} on rejoin: {}", rejoinPlayer.getName().getString(), ex.getMessage());
                            data.inCombat = false;
                            data.markDirty();
                        }
                    }));
                }
            }
        });

        // Clean up combat on player disconnect — only affects that player's instance
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var playerUuid = handler.getPlayer().getUuid();
            CombatManager cm = CombatManager.get(playerUuid);
            if (cm.isActive()) {
                LOGGER.info("Player {} disconnected during combat — cleaning up", handler.getPlayer().getName().getString());
                cm.endCombat();
            }
            CombatManager.remove(playerUuid);
        });

        // Tick ALL active combat instances each server tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CombatManager.tickAll();
        });

        // Prevent breaking blocks during combat AND hub structure blocks
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
            (world, player, pos, state, blockEntity) -> {
                if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp
                        && CombatManager.get(sp).isActive()) {
                    return false; // cancel block breaking for THIS player during combat
                }
                // Protect hub shell (walls, floor, ceiling, roof)
                if (HubRoomBuilder.isHubShell(pos)) return false;
                return true;
            }
        );

        // Load biome definitions from JSON datapacks on server start
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            com.crackedgames.craftics.level.BiomeRegistry.loadFromDatapacks(
                server.getResourceManager()
            );
        });

        // Also reload on /reload command
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
            (server, resourceManager, success) -> {
                if (success) {
                    com.crackedgames.craftics.level.BiomeRegistry.clear();
                    com.crackedgames.craftics.level.BiomeRegistry.loadFromDatapacks(
                        server.getResourceManager()
                    );
                }
            }
        );

        // Debug / admin commands: /craftics <subcommand>
        registerCommands();

        LOGGER.info("Craftics initialized.");
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = CommandManager.literal("craftics").requires(src -> src.hasPermissionLevel(2));

            // /craftics unlock_all — unlock every biome
            root.then(CommandManager.literal("unlock_all").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                ServerWorld overworld = src.getServer().getOverworld();
                CrafticsSavedData data = CrafticsSavedData.get(overworld);
                data.loadPlayerIntoLegacy(cmdPlayer.getUuid());
                data.initBranchIfNeeded();
                int totalBiomes = com.crackedgames.craftics.level.BiomePath.getFullPath(data.branchChoice).size();
                data.highestBiomeUnlocked = totalBiomes;
                data.markDirty();
                updateWorldIcon(src.getServer(), data);
                src.sendFeedback(() -> Text.literal("§aUnlocked all " + totalBiomes + " biomes."), true);
                return 1;
            }));

            // /craftics reset_biomes — reset current biome level progress back to level 1
            root.then(CommandManager.literal("reset_biomes").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                data.loadPlayerIntoLegacy(cmdPlayer.getUuid());
                String biomeId = data.activeBiomeId;
                data.activeBiomeLevelIndex = 0;
                data.markDirty();
                String label = (biomeId != null && !biomeId.isEmpty()) ? biomeId : "current biome";
                src.sendFeedback(() -> Text.literal("§eReset " + label + " progress back to level 1."), true);
                return 1;
            }));

            // /craftics set_emeralds <amount>
            root.then(CommandManager.literal("set_emeralds")
                .then(CommandManager.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount");
                        ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                        CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                        data.loadPlayerIntoLegacy(cmdPlayer.getUuid());
                        data.emeralds = amount;
                        data.markDirty();
                        src.sendFeedback(() -> Text.literal("§aSet emeralds to " + amount + "."), true);
                        return 1;
                    })));

            // /craftics set_level <level> — set player progression level
            root.then(CommandManager.literal("set_level")
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
            root.then(CommandManager.literal("info").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity cmdPlayer = src.getPlayerOrThrow();
                CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                data.loadPlayerIntoLegacy(cmdPlayer.getUuid());
                src.sendFeedback(() -> Text.literal(
                    "§6--- Craftics Debug Info ---\n" +
                    "§fBiomes unlocked: §e" + data.highestBiomeUnlocked + "\n" +
                    "§fEmeralds: §a" + data.emeralds + "\n" +
                    "§fNG+ level: §d" + data.ngPlusLevel + "\n" +
                    "§fBranch choice: §b" + data.branchChoice + "\n" +
                    "§fActive biome: §c" + (data.activeBiomeId.isEmpty() ? "none" : data.activeBiomeId) + "\n" +
                    "§fDiscovered: §7" + (data.discoveredBiomes.isEmpty() ? "none" : data.discoveredBiomes)
                ), false);
                return 1;
            }));

            // /craftics heal — fully heal the player
            root.then(CommandManager.literal("heal").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity player = src.getPlayerOrThrow();
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(5.0f);
                src.sendFeedback(() -> Text.literal("§aFully healed."), true);
                return 1;
            }));

            // /craftics kill_enemies — kill all enemies in current combat
            root.then(CommandManager.literal("kill_enemies").executes(ctx -> {
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
            root.then(CommandManager.literal("skip_level").executes(ctx -> {
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
            root.then(CommandManager.literal("set_ap")
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
            root.then(CommandManager.literal("set_speed")
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
            root.then(CommandManager.literal("set_ngplus")
                .then(CommandManager.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level");
                        CrafticsSavedData data = CrafticsSavedData.get(src.getServer().getOverworld());
                        data.ngPlusLevel = level;
                        data.markDirty();
                        src.sendFeedback(() -> Text.literal("§aSet NG+ level to " + level + "."), true);
                        return 1;
                    })));

            // /craftics set_stat <stat> <value> — set a specific stat's allocated points
            root.then(CommandManager.literal("set_stat")
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
            root.then(CommandManager.literal("reset_stats").executes(ctx -> {
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
            root.then(CommandManager.literal("tp_hub").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity player = src.getPlayerOrThrow();
                ServerPlayerEntity cmdPlayer = src.getPlayer(); if (cmdPlayer == null) { src.sendError(Text.literal("§cMust be a player.")); return 0; } CombatManager cm = CombatManager.get(cmdPlayer);
                if (cm.isActive()) {
                    cm.endCombat();
                }
                player.requestTeleport(0.5, 65, 0.5);
                player.changeGameMode(GameMode.SURVIVAL);
                src.sendFeedback(() -> Text.literal("§aTeleported to hub."), true);
                return 1;
            }));

            // /craftics give <preset> — give a set of gear
            root.then(CommandManager.literal("give")
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
            root.then(CommandManager.literal("combat_info").executes(ctx -> {
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
            root.then(CommandManager.literal("test_range").executes(ctx -> {
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
            String[] eventNames = {"ambush", "trial", "ominous_trial", "shrine", "traveler", "vault", "dig_site", "trader", "none"};
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
                p.requestTeleport(0.5, 65, 0.5);
                p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                    new com.crackedgames.craftics.network.ExitCombatPayload(false));
                ctx.getSource().sendFeedback(() -> Text.literal("§aLeft the test range."), false);
                return 1;
            }));

            registerPartyCommands(root);

            dispatcher.register(root);
        });
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
            StringBuilder sb = new StringBuilder("§6--- Party Members ---\n");
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

        root.then(partyNode);
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
     * Writes the biome icon of the player's highest unlocked biome as the world's icon.png,
     * so the world-select screen shows progression at a glance. No-ops on non-Craftics worlds.
     */
    public static void updateWorldIcon(net.minecraft.server.MinecraftServer server, CrafticsSavedData data) {
        if (!(server.getOverworld().getChunkManager().getChunkGenerator() instanceof VoidChunkGenerator)) return;
        try {
            data.initBranchIfNeeded();
            java.util.List<String> fullPath = com.crackedgames.craftics.level.BiomePath
                .getFullPath(Math.max(0, data.branchChoice));
            int idx = Math.max(0, Math.min(data.highestBiomeUnlocked - 1, fullPath.size() - 1));
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
                LOGGER.debug("Updated world icon to biome '{}' (64x64, highestUnlocked={})", biomeId, data.highestBiomeUnlocked);
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to update world icon: {}", ex.getMessage());
        }
    }
}