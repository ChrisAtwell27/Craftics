package com.crackedgames.craftics.command;

import com.crackedgames.craftics.world.CrafticsSavedData;
import com.crackedgames.craftics.world.HubTeleports;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Server-operator utilities: lobby spawn, rescue, status, island admin
 *  (info/tp/unload/reset), plus the player-facing visit subtree (request/
 *  accept/deny/kick, delegating to VisitManager). Attached to the /craftics root. */
public final class CrafticsServerCommands {
    private CrafticsServerCommands() {}

    public static void register(LiteralArgumentBuilder<ServerCommandSource> root) {
        var lobby = CommandManager.literal("lobby")
            .executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                if (com.crackedgames.craftics.combat.CombatManager.isEngaged(p.getUuid())) {
                    ctx.getSource().sendError(Text.literal("§cYou cannot leave mid-combat."));
                    return 0;
                }
                // Leaving a scene the same way combat is guarded above: the client learns it is
                // in a scene from a payload and unlearns it only from one, so teleporting the
                // body out without telling it leaves every right-click silently swallowed.
                com.crackedgames.craftics.CrafticsMod.clearClientRunState(p);
                HubTeleports.toLobby(p);
                ctx.getSource().sendFeedback(() -> Text.literal("§aTeleported to lobby."), false);
                return 1;
            })
            .then(CommandManager.literal("setspawn")
                .requires(CrafticsPermissions.require("command.lobby.setspawn"))
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    CrafticsSavedData data = CrafticsSavedData.get(p.getServerWorld());
                    data.lobbySpawnX = p.getBlockPos().getX();
                    data.lobbySpawnY = p.getBlockPos().getY();
                    data.lobbySpawnZ = p.getBlockPos().getZ();
                    data.lobbySpawnYaw = p.getYaw();
                    data.markDirty();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§aLobby spawn set to " + p.getBlockPos().toShortString()), true);
                    return 1;
                }))
            .then(CommandManager.literal("rebuild")
                .requires(CrafticsPermissions.require("command.lobby.rebuild"))
                .executes(ctx -> {
                    // Re-paste the central hub from the bundled schematic in place.
                    // Anything placed inside its footprint since (lootbox chests,
                    // scoreboards) is overwritten - re-place those afterwards.
                    var overworld = ctx.getSource().getServer().getOverworld();
                    com.crackedgames.craftics.world.HubRoomBuilder.buildLobby(overworld);
                    CrafticsSavedData data = CrafticsSavedData.get(overworld);
                    data.hubBuilt = true;
                    data.hubVersion = com.crackedgames.craftics.world.HubRoomBuilder.LOBBY_VERSION;
                    data.markDirty();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§aCentral hub rebuilt from the bundled schematic."), true);
                    return 1;
                }));

        var rescue = CommandManager.literal("rescue")
            .requires(CrafticsPermissions.require("command.rescue"))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> {
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                    HubTeleports.toHub(target);
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§aSent " + target.getName().getString() + " home."), true);
                    return 1;
                }));

        var status = CommandManager.literal("status")
            .requires(CrafticsPermissions.require("command.status"))
            .executes(ctx -> {
                var server = ctx.getSource().getServer();
                StringBuilder sb = new StringBuilder("§6== Craftics status ==");
                int worlds = 0;
                for (net.minecraft.server.world.ServerWorld w : server.getWorlds()) {
                    worlds++;
                    sb.append("\n§e").append(w.getRegistryKey().getValue())
                      .append(" §7players=").append(w.getPlayers().size())
                      .append(" chunks=").append(w.getChunkManager().getTotalChunksLoadedCount());
                }
                sb.append("\n§7worlds=").append(worlds);

                var islands = com.crackedgames.craftics.world.IslandDimensions.loadedIslands();
                sb.append("\n§6islands loaded=").append(islands.size());
                for (var entry : islands.entrySet()) {
                    sb.append("\n§e").append(entry.getKey())
                      .append(" §7players=").append(entry.getValue().getPlayers().size());
                }

                sb.append("\n§7combat active=").append(
                    com.crackedgames.craftics.combat.CombatManager.activeCount());
                sb.append(" scenes active=").append(
                    com.crackedgames.craftics.scene.SceneController.activeCount());

                final String msg = sb.toString();
                ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                return 1;
            });

        var island = CommandManager.literal("island").requires(CrafticsPermissions.require("command.island"));
        // Both take a raw name-or-UUID string rather than an online-player argument. A
        // moderator investigating an island is very often doing it precisely BECAUSE the
        // owner is not connected, and an argument type that can only name online players
        // makes the tool useless in the case it exists for.
        island.then(CommandManager.literal("info")
            .then(CommandManager.argument("player", StringArgumentType.word())
                .suggests(CrafticsServerCommands::suggestKnownPlayers)
                .executes(ctx -> islandInfo(ctx.getSource(),
                    StringArgumentType.getString(ctx, "player")))));
        island.then(CommandManager.literal("tp")
            .then(CommandManager.argument("player", StringArgumentType.word())
                .suggests(CrafticsServerCommands::suggestKnownPlayers)
                .executes(ctx -> islandTp(ctx.getSource(),
                    StringArgumentType.getString(ctx, "player")))));
        island.then(CommandManager.literal("unload").then(CommandManager.argument("player", EntityArgumentType.player())
            .executes(ctx -> {
                var server = ctx.getSource().getServer();
                CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
                java.util.UUID owner = data.getEffectiveWorldOwner(
                    EntityArgumentType.getPlayer(ctx, "player").getUuid());
                boolean done = com.crackedgames.craftics.world.IslandDimensions.unloadIfEmpty(server, owner);
                ctx.getSource().sendFeedback(() -> Text.literal(done
                    ? "§aIsland dim unloaded." : "§ePlayers still inside, not unloaded."), true);
                return done ? 1 : 0;
            })));
        island.then(CommandManager.literal("reset").then(CommandManager.argument("player", EntityArgumentType.player())
            .then(CommandManager.literal("confirm").executes(ctx -> {
                // Wipe = kick occupants to lobby, delete the runtime dim files, clear island flags
                // so the next join/home rebuilds fresh.
                ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "player");
                var server = ctx.getSource().getServer();
                CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
                java.util.UUID owner = data.getEffectiveWorldOwner(t.getUuid());
                net.minecraft.server.world.ServerWorld dim =
                    com.crackedgames.craftics.world.IslandDimensions.getLoaded(server, owner);
                if (dim != null) for (ServerPlayerEntity p2 : new java.util.ArrayList<>(dim.getPlayers()))
                    HubTeleports.toLobby(p2);
                com.crackedgames.craftics.world.IslandDimensions.delete(server, owner);
                var pd = data.getPlayerData(owner);
                pd.personalHubBuilt = false;
                data.markDirty();
                ctx.getSource().sendFeedback(() -> Text.literal("§aIsland reset."), true);
                return 1;
            }))));

        var visit = CommandManager.literal("visit")
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> {
                    com.crackedgames.craftics.world.VisitManager.request(
                        ctx.getSource().getPlayerOrThrow(),
                        EntityArgumentType.getPlayer(ctx, "player"));
                    return 1;
                }))
            .then(CommandManager.literal("accept").then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> {
                    com.crackedgames.craftics.world.VisitManager.respond(
                        ctx.getSource().getPlayerOrThrow(),
                        EntityArgumentType.getPlayer(ctx, "player"), true);
                    return 1;
                })))
            .then(CommandManager.literal("deny").then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> {
                    com.crackedgames.craftics.world.VisitManager.respond(
                        ctx.getSource().getPlayerOrThrow(),
                        EntityArgumentType.getPlayer(ctx, "player"), false);
                    return 1;
                })))
            .then(CommandManager.literal("kick").then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> {
                    com.crackedgames.craftics.world.VisitManager.kick(
                        ctx.getSource().getPlayerOrThrow(),
                        EntityArgumentType.getPlayer(ctx, "player"));
                    return 1;
                })));

        root.then(lobby);
        root.then(rescue);
        root.then(status);
        root.then(island);
        root.then(visit);
    }

    /** Online names only. The command itself accepts any name or UUID, including players the
     *  server has never seen; this just saves typing for the common case. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestKnownPlayers(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return net.minecraft.command.CommandSource.suggestMatching(
            ctx.getSource().getPlayerNames(), builder);
    }

    /**
     * Resolve a name or UUID string to a player UUID, working for offline players.
     * Returns null when nothing matches.
     *
     * <p>Order matters: a literal UUID is taken at face value first, because it is the only
     * form that stays correct after a name change and is what an admin pastes from a log.
     * Then online players, then the server's user cache for anyone who has logged in before.
     */
    private static java.util.UUID resolveOwnerUuid(net.minecraft.server.MinecraftServer server,
                                                    String nameOrUuid) {
        try {
            return java.util.UUID.fromString(nameOrUuid);
        } catch (IllegalArgumentException notAUuid) {
            // Fall through to a name lookup.
        }
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(nameOrUuid);
        if (online != null) return online.getUuid();
        var cache = server.getUserCache();
        if (cache == null) return null;
        return cache.findByName(nameOrUuid)
            .map(com.mojang.authlib.GameProfile::getId)
            .orElse(null);
    }

    /** A recorded epoch-millis stamp as readable UTC, or "unknown" when unrecorded. */
    private static String formatStamp(long epochMillis) {
        if (epochMillis <= 0L) return "unknown (predates this record)";
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(java.time.ZoneOffset.UTC)
            .format(java.time.Instant.ofEpochMilli(epochMillis));
    }

    /** Report where and when a player's island was created, plus its live state. */
    private static int islandInfo(ServerCommandSource src, String query) {
        net.minecraft.server.MinecraftServer server = src.getServer();
        java.util.UUID target = resolveOwnerUuid(server, query);
        if (target == null) {
            src.sendError(Text.literal("§cNo player found for '" + query
                + "'. Try their exact name or a UUID."));
            return 0;
        }
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        java.util.UUID owner = data.getEffectiveWorldOwner(target);
        if (!data.hasPersonalWorld(owner)) {
            src.sendError(Text.literal("§c" + query + " has no island."));
            return 0;
        }
        var record = data.getIslandCreation(owner);
        net.minecraft.server.world.ServerWorld dim =
            com.crackedgames.craftics.world.IslandDimensions.getLoaded(server, owner);
        // The dimension the island is addressed by TODAY, which is worth showing next to the
        // recorded one: if they ever disagree, that difference is the whole answer.
        String liveDim = com.crackedgames.craftics.world.IslandDimensions.dimensionIdOf(owner);
        net.minecraft.util.math.BlockPos hubNow = data.getHubTeleportPos(owner);

        src.sendFeedback(() -> Text.literal("§6Island of §f" + query), false);
        src.sendFeedback(() -> Text.literal("§7  owner   §f" + owner
            + (owner.equals(target) ? "" : " §7(party owner, queried player is a member)")), false);
        src.sendFeedback(() -> Text.literal("§7  created §f"
            + formatStamp(record != null ? record.createdAtMillis() : 0L)), false);
        if (record != null && !record.ownerName().isEmpty()) {
            src.sendFeedback(() -> Text.literal("§7  as      §f" + record.ownerName()), false);
        }
        src.sendFeedback(() -> Text.literal("§7  dim     §f"
            + (record != null && !record.dimensionId().isEmpty() ? record.dimensionId() : liveDim)
            + (record != null && !record.dimensionId().isEmpty()
               && !record.dimensionId().equals(liveDim) ? " §c(now " + liveDim + ")" : "")), false);
        src.sendFeedback(() -> Text.literal("§7  origin  §f"
            + (record != null && record.hasOrigin()
               ? record.x() + ", " + record.y() + ", " + record.z()
               : "unknown (predates this record)")), false);
        src.sendFeedback(() -> Text.literal("§7  hub now §f"
            + (hubNow != null ? hubNow.getX() + ", " + hubNow.getY() + ", " + hubNow.getZ()
                              : "unset")), false);
        src.sendFeedback(() -> Text.literal("§7  loaded  §f" + (dim != null
            ? "yes (" + dim.getPlayers().size() + " inside)" : "no")), false);
        return 1;
    }

    /** Teleport the running operator into the island dimension recorded for a player. */
    private static int islandTp(ServerCommandSource src, String query)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity admin = src.getPlayerOrThrow();
        net.minecraft.server.MinecraftServer server = src.getServer();
        java.util.UUID target = resolveOwnerUuid(server, query);
        if (target == null) {
            src.sendError(Text.literal("§cNo player found for '" + query
                + "'. Try their exact name or a UUID."));
            return 0;
        }
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        java.util.UUID owner = data.getEffectiveWorldOwner(target);
        if (!data.hasPersonalWorld(owner)) {
            src.sendError(Text.literal("§c" + query + " has no island to visit."));
            return 0;
        }
        // getOrCreate rather than getLoaded: the island is very likely unloaded precisely
        // because its owner is offline, which is the case this command exists to serve.
        net.minecraft.server.world.ServerWorld dim =
            com.crackedgames.craftics.world.IslandDimensions.getOrCreate(server, owner);
        net.minecraft.util.math.BlockPos hub = data.getHubTeleportPos(owner);
        HubTeleports.adminTeleport(admin, dim, hub);
        src.sendFeedback(() -> Text.literal("§aTeleported to the island of " + query
            + " §7(" + com.crackedgames.craftics.world.IslandDimensions.dimensionIdOf(owner) + ")"), true);
        return 1;
    }
}
