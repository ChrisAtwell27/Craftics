package com.crackedgames.craftics.command;

import com.crackedgames.craftics.world.CrafticsSavedData;
import com.crackedgames.craftics.world.HubTeleports;
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
                HubTeleports.toLobby(p);
                ctx.getSource().sendFeedback(() -> Text.literal("§aTeleported to lobby."), false);
                return 1;
            })
            .then(CommandManager.literal("setspawn")
                .requires(src -> src.hasPermissionLevel(2))
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
                .requires(src -> src.hasPermissionLevel(2))
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
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> {
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                    HubTeleports.toHub(target);
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§aSent " + target.getName().getString() + " home."), true);
                    return 1;
                }));

        var status = CommandManager.literal("status")
            .requires(src -> src.hasPermissionLevel(2))
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

        var island = CommandManager.literal("island").requires(src -> src.hasPermissionLevel(2));
        island.then(CommandManager.literal("info").then(CommandManager.argument("player", EntityArgumentType.player())
            .executes(ctx -> {
                ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "player");
                var server = ctx.getSource().getServer();
                CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
                java.util.UUID owner = data.getEffectiveWorldOwner(t.getUuid());
                net.minecraft.server.world.ServerWorld dim =
                    com.crackedgames.craftics.world.IslandDimensions.getLoaded(server, owner);
                String line = "§6Island of " + t.getName().getString()
                    + "§7 owner=" + owner
                    + " loaded=" + (dim != null)
                    + (dim != null ? " players=" + dim.getPlayers().size() : "");
                ctx.getSource().sendFeedback(() -> Text.literal(line), false);
                return 1;
            })));
        island.then(CommandManager.literal("tp").then(CommandManager.argument("player", EntityArgumentType.player())
            .executes(ctx -> {
                ServerPlayerEntity admin = ctx.getSource().getPlayerOrThrow();
                ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "player");
                var server = ctx.getSource().getServer();
                CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
                java.util.UUID owner = data.getEffectiveWorldOwner(t.getUuid());
                net.minecraft.server.world.ServerWorld dim =
                    com.crackedgames.craftics.world.IslandDimensions.getOrCreate(server, owner);
                net.minecraft.util.math.BlockPos hub = data.getHubTeleportPos(t.getUuid());
                // cross-dim teleport, same pattern as HubTeleports.toHub
                com.crackedgames.craftics.world.HubTeleports.adminTeleport(admin, dim, hub);
                return 1;
            })));
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
}
