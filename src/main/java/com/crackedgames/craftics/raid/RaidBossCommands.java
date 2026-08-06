package com.crackedgames.craftics.raid;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.ai.boss.InfiniteAbilityPool;
import com.crackedgames.craftics.core.TileType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /raidboss (and /craftics raidboss). Three layers: a bare join command for
 * players, admin scheduling, and authoring subcommands that write the same JSON
 * files the loader reads, so a command-authored boss and a hand-edited one stay
 * one artifact.
 */
public final class RaidBossCommands {
    private RaidBossCommands() {}

    public static void register(LiteralArgumentBuilder<ServerCommandSource> crafticsRoot,
                                CommandDispatcher<ServerCommandSource> dispatcher) {
        crafticsRoot.then(build());
        dispatcher.register(build());
    }

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("raidboss")
            .executes(RaidBossCommands::joinOrInfo)
            .then(CommandManager.literal("info").executes(RaidBossCommands::info))
            .then(CommandManager.literal("list").executes(RaidBossCommands::list))
            .then(adminStart())
            .then(CommandManager.literal("cancel").requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> {
                    RaidBossSchedule.cancel(ctx.getSource().getServer());
                    ctx.getSource().sendFeedback(() -> Text.literal("§aRaid cancelled."), true);
                    return 1;
                }))
            .then(CommandManager.literal("reload").requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> {
                    RaidBossRegistry.reload(ctx.getSource().getServer());
                    int n = RaidBossRegistry.all().size();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§aReloaded " + n + " raid boss definition(s)."), true);
                    return 1;
                }))
            .then(CommandManager.literal("schedule").requires(s -> s.hasPermissionLevel(2))
                .executes(RaidBossCommands::schedule))
            .then(authoring());
    }

    // ---- player ----

    private static int joinOrInfo(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        if (!RaidBossLobby.isOpen()) return info(ctx);
        RaidBossLobby.JoinResult result = RaidBossLobby.join(p);
        switch (result) {
            case JOINED -> ctx.getSource().sendFeedback(() -> Text.literal(
                "§aYou joined the raid on " + RaidBossLobby.boss().name()
                + ". §7Raiders so far: " + RaidBossLobby.count()), false);
            case ALREADY_JOINED -> ctx.getSource().sendError(Text.literal("§cYou already joined."));
            case BUSY -> ctx.getSource().sendError(Text.literal(
                "§cFinish your run before joining a raid."));
            case VISITING -> ctx.getSource().sendError(Text.literal(
                "§cYou cannot join a raid while visiting another island."));
            case FULL -> ctx.getSource().sendError(Text.literal(
                "§cEvery raid arena is full."));
            case WINDOW_CLOSED, NO_RAID -> ctx.getSource().sendError(Text.literal(
                "§cThere is no raid to join right now."));
        }
        return result == RaidBossLobby.JoinResult.JOINED ? 1 : 0;
    }

    private static int info(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        if (RaidBossLobby.isOpen()) {
            src.sendFeedback(() -> Text.literal("§4§l" + RaidBossLobby.boss().name()
                + " §r§cis here! §6/raidboss§c to join, "
                + RaidBossLobby.secondsLeft() + "s left, "
                + RaidBossLobby.count() + " joined."), false);
            return 1;
        }
        if (RaidBossSchedule.phase() == RaidBossSchedule.Phase.ANNOUNCED) {
            src.sendFeedback(() -> Text.literal("§c" + RaidBossSchedule.pendingBoss().name()
                + " §7has been announced and is on its way."), false);
            return 1;
        }
        int seconds = RaidBossSchedule.secondsUntilNextRaid();
        String slot = RaidBossSchedule.nextSlotDescription();
        if (seconds < 0) {
            src.sendFeedback(() -> Text.literal("§7No raids are scheduled."), false);
            return 0;
        }
        final int hours = seconds / 3600;
        final int minutes = (seconds % 3600) / 60;
        src.sendFeedback(() -> Text.literal("§7Next raid at §e" + slot
            + " §7server time, in §e" + hours + "h " + minutes + "m§7."), false);
        return 1;
    }

    private static int list(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        List<RaidBossDefinition> all = RaidBossRegistry.all();
        if (all.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("§7No raid bosses are defined."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("§6Raid bosses (" + all.size() + "):");
        for (RaidBossDefinition d : all) sb.append("\n§e").append(d.id()).append(" §7- ").append(d.name());
        final String msg = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return all.size();
    }

    // ---- admin ----

    private static LiteralArgumentBuilder<ServerCommandSource> adminStart() {
        return CommandManager.literal("start").requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.argument("id", StringArgumentType.word())
                .suggests((c, b) -> {
                    for (RaidBossDefinition d : RaidBossRegistry.all()) b.suggest(d.id());
                    return b.buildFuture();
                })
                .executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    if (RaidBossSchedule.forceStart(ctx.getSource().getServer(), id)) {
                        ctx.getSource().sendFeedback(() -> Text.literal("§aRaid '" + id + "' starting."), true);
                        return 1;
                    }
                    ctx.getSource().sendError(Text.literal(
                        "§cCould not start '" + id + "': unknown boss, or a raid is already in progress."));
                    return 0;
                }));
    }

    private static int schedule(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        var server = ctx.getSource().getServer();
        var data = com.crackedgames.craftics.world.CrafticsSavedData.get(server.getOverworld());
        RaidBossState state = RaidBossState.parse(data.raidBossState);
        StringBuilder sb = new StringBuilder("§6== Raid schedule ==");
        sb.append("\n§7enabled=").append(CrafticsMod.CONFIG.raidBossesEnabled())
          .append(" phase=").append(RaidBossSchedule.phase());
        for (String slot : RaidBossScheduleMath.parseSlots(CrafticsMod.CONFIG.raidBossTimes())) {
            sb.append("\n§e").append(slot).append(" §7lastFiredDay=")
              .append(state.slotLastFiredDay().getOrDefault(slot, -1L));
        }
        sb.append("\n§6History (last ").append(CrafticsMod.CONFIG.raidBossNoRepeatDays()).append(" days):");
        for (RaidBossState.HistoryEntry e : state.history()) {
            sb.append("\n§7day ").append(e.day()).append(" - ").append(e.bossId());
        }
        sb.append("\n§7instances active=").append(RaidBossInstance.active().size());
        final String msg = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    // ---- authoring ----

    private static LiteralArgumentBuilder<ServerCommandSource> authoring() {
        LiteralArgumentBuilder<ServerCommandSource> create =
            CommandManager.literal("create").requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.argument("id", StringArgumentType.word())
                .then(CommandManager.argument("entity", StringArgumentType.string())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        if (RaidBossRegistry.get(id) != null) {
                            ctx.getSource().sendError(Text.literal("§cRaid boss '" + id + "' already exists."));
                            return 0;
                        }
                        // A brand new definition needs a non-empty movepool or persist()'s
                        // parser round trip rejects it outright ("no valid moves"). Seed it
                        // with six real, simple ability ids pulled from different bosses in
                        // InfiniteAbilityPool rather than loosening that rule: the admin gets
                        // an immediately playable default and edits the pool afterward with
                        // /raidboss edit moves.
                        List<String> starterMoves = List.of(
                            "shield_bash", "hex_bolt", "frost_arrow",
                            "ground_pound", "rampage", "sonic_boom");
                        RaidBossDefinition def = new RaidBossDefinition(
                            id, id, StringArgumentType.getString(ctx, "entity"),
                            600, 10, 4, 1, 2,
                            starterMoves, RaidBossPower.doubleMove(), -1, "plains", 32,
                            List.of(new RaidBossLootEntry("minecraft:diamond", 5, 1, 3)), List.of(), 10);
                        return persist(ctx, def, "§aCreated '" + id
                            + "'. Edit its moves with §e/raidboss edit moves " + id + " add <ability>§a.");
                    })));

        LiteralArgumentBuilder<ServerCommandSource> set =
            CommandManager.literal("set").requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.argument("id", StringArgumentType.word()).suggests(RaidBossCommands::suggestIds)
                .then(CommandManager.argument("field", StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (String f : new String[]{"name", "entity", "hp", "attack", "defense",
                                "range", "speed", "bounty", "weight", "arena", "environment"}) b.suggest(f);
                        return b.buildFuture();
                    })
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> setField(ctx,
                            StringArgumentType.getString(ctx, "id"),
                            StringArgumentType.getString(ctx, "field"),
                            StringArgumentType.getString(ctx, "value"))))));

        LiteralArgumentBuilder<ServerCommandSource> moves =
            CommandManager.literal("moves").requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.argument("id", StringArgumentType.word()).suggests(RaidBossCommands::suggestIds)
                .then(CommandManager.literal("list").executes(ctx -> {
                    RaidBossDefinition def = require(ctx, StringArgumentType.getString(ctx, "id"));
                    if (def == null) return 0;
                    final String msg = "§6" + def.id() + " moves: §e" + String.join(", ", def.moves());
                    ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                    return 1;
                }))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("ability", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String a : InfiniteAbilityPool.allIds()) b.suggest(a);
                            return b.buildFuture();
                        })
                        .executes(ctx -> editMoves(ctx, true))))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("ability", StringArgumentType.word())
                        .executes(ctx -> editMoves(ctx, false)))));

        LiteralArgumentBuilder<ServerCommandSource> loot =
            CommandManager.literal("loot").requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.argument("id", StringArgumentType.word()).suggests(RaidBossCommands::suggestIds)
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("item", StringArgumentType.string())
                        .then(CommandManager.argument("weight", IntegerArgumentType.integer(1))
                            .then(CommandManager.argument("min", IntegerArgumentType.integer(1))
                                .then(CommandManager.argument("max", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        RaidBossDefinition def = require(ctx, StringArgumentType.getString(ctx, "id"));
                                        if (def == null) return 0;
                                        List<RaidBossLootEntry> rows = new ArrayList<>(def.loot());
                                        int min = IntegerArgumentType.getInteger(ctx, "min");
                                        rows.add(new RaidBossLootEntry(
                                            StringArgumentType.getString(ctx, "item"),
                                            IntegerArgumentType.getInteger(ctx, "weight"),
                                            min, Math.max(min, IntegerArgumentType.getInteger(ctx, "max"))));
                                        return persist(ctx, withLoot(def, rows), "§aLoot entry added.");
                                    }))))))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("item", StringArgumentType.string())
                        .executes(ctx -> {
                            RaidBossDefinition def = require(ctx, StringArgumentType.getString(ctx, "id"));
                            if (def == null) return 0;
                            String item = StringArgumentType.getString(ctx, "item");
                            List<RaidBossLootEntry> rows = new ArrayList<>(def.loot());
                            boolean removed = rows.removeIf(r -> r.itemId().equals(item));
                            if (!removed) {
                                ctx.getSource().sendError(Text.literal("§cNo loot entry for '" + item + "'."));
                                return 0;
                            }
                            return persist(ctx, withLoot(def, rows), "§aLoot entry removed.");
                        }))));

        LiteralArgumentBuilder<ServerCommandSource> obstacles =
            CommandManager.literal("obstacles").requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.argument("id", StringArgumentType.word()).suggests(RaidBossCommands::suggestIds)
                .then(CommandManager.literal("list").executes(ctx -> {
                    RaidBossDefinition def = require(ctx, StringArgumentType.getString(ctx, "id"));
                    if (def == null) return 0;
                    if (def.obstacles().isEmpty()) {
                        ctx.getSource().sendFeedback(() -> Text.literal(
                            "§7" + def.id() + " has no obstacles."), false);
                        return 0;
                    }
                    StringBuilder sb = new StringBuilder("§6" + def.id() + " obstacles:");
                    for (RaidBossObstacle o : def.obstacles()) {
                        sb.append("\n§e").append(o.tileType().toLowerCase(Locale.ROOT))
                          .append(" §7count=").append(o.minCount());
                        if (o.minCount() != o.maxCount()) sb.append('-').append(o.maxCount());
                        sb.append(" cluster=").append(o.cluster());
                        if (!o.blockId().isEmpty()) sb.append(" block=").append(o.blockId());
                    }
                    final String msg = sb.toString();
                    ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                    return def.obstacles().size();
                }))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("tile", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (TileType t : TileType.values()) b.suggest(t.name().toLowerCase(Locale.ROOT));
                            return b.buildFuture();
                        })
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                            .executes(ctx -> addObstacle(ctx, IntegerArgumentType.getInteger(ctx, "count"), 1, ""))
                            .then(CommandManager.argument("cluster", IntegerArgumentType.integer(1))
                                .executes(ctx -> addObstacle(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                    IntegerArgumentType.getInteger(ctx, "cluster"), ""))
                                .then(CommandManager.argument("block", StringArgumentType.string())
                                    .executes(ctx -> addObstacle(ctx, IntegerArgumentType.getInteger(ctx, "count"),
                                        IntegerArgumentType.getInteger(ctx, "cluster"),
                                        StringArgumentType.getString(ctx, "block"))))))))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("tile", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (TileType t : TileType.values()) b.suggest(t.name().toLowerCase(Locale.ROOT));
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            RaidBossDefinition def = require(ctx, StringArgumentType.getString(ctx, "id"));
                            if (def == null) return 0;
                            String rawTile = StringArgumentType.getString(ctx, "tile");
                            String tile = resolveTileType(rawTile);
                            if (tile == null) {
                                ctx.getSource().sendError(Text.literal("§cUnknown tile type '" + rawTile + "'."));
                                return 0;
                            }
                            List<RaidBossObstacle> rows = new ArrayList<>(def.obstacles());
                            boolean removed = rows.removeIf(r -> r.tileType().equals(tile));
                            if (!removed) {
                                ctx.getSource().sendError(Text.literal(
                                    "§cNo obstacle entry for '" + rawTile + "'."));
                                return 0;
                            }
                            return persist(ctx, withObstacles(def, rows), "§aObstacle entry(ies) removed.");
                        }))));

        LiteralArgumentBuilder<ServerCommandSource> power =
            CommandManager.literal("power").requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.argument("id", StringArgumentType.word()).suggests(RaidBossCommands::suggestIds)
                .then(CommandManager.literal("double_move").executes(ctx -> {
                    RaidBossDefinition def = require(ctx, StringArgumentType.getString(ctx, "id"));
                    if (def == null) return 0;
                    return persist(ctx, withPower(def, RaidBossPower.doubleMove()), "§aPower set to double move.");
                }))
                .then(CommandManager.literal("buff")
                    .then(CommandManager.argument("effect", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (RaidBossBuff buff : RaidBossBuff.values()) {
                                b.suggest(buff.name().toLowerCase(Locale.ROOT));
                            }
                            return b.buildFuture();
                        })
                        .then(CommandManager.argument("amplifier", IntegerArgumentType.integer(0, 4))
                            .executes(ctx -> {
                                RaidBossDefinition def = require(ctx, StringArgumentType.getString(ctx, "id"));
                                if (def == null) return 0;
                                String effect = StringArgumentType.getString(ctx, "effect");
                                if (RaidBossBuff.of(effect) == null) {
                                    ctx.getSource().sendError(Text.literal("§cUnknown buff '" + effect + "'."));
                                    return 0;
                                }
                                return persist(ctx, withPower(def, RaidBossPower.buff(
                                        effect, IntegerArgumentType.getInteger(ctx, "amplifier"))),
                                    "§aPower set to " + effect + ".");
                            })))));

        LiteralArgumentBuilder<ServerCommandSource> delete =
            CommandManager.literal("delete").requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.argument("id", StringArgumentType.word()).suggests(RaidBossCommands::suggestIds)
                .then(CommandManager.literal("confirm").executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    if (require(ctx, id) == null) return 0;
                    RaidBossJsonLoader.delete(id);
                    RaidBossRegistry.remove(id);
                    ctx.getSource().sendFeedback(() -> Text.literal("§aDeleted '" + id + "'."), true);
                    return 1;
                })));

        // One literal per authoring verb, all hung off the same parent below.
        return CommandManager.literal("edit").requires(s -> s.hasPermissionLevel(2))
            .then(create).then(set).then(moves).then(loot).then(obstacles).then(power).then(delete);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestIds(com.mojang.brigadier.context.CommandContext<ServerCommandSource> c,
                       com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        for (RaidBossDefinition d : RaidBossRegistry.all()) b.suggest(d.id());
        return b.buildFuture();
    }

    private static RaidBossDefinition require(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, String id) {
        RaidBossDefinition def = RaidBossRegistry.get(id);
        if (def == null) ctx.getSource().sendError(Text.literal("§cUnknown raid boss '" + id + "'."));
        return def;
    }

    private static int editMoves(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                 boolean add) {
        RaidBossDefinition def = require(ctx, StringArgumentType.getString(ctx, "id"));
        if (def == null) return 0;
        String ability = StringArgumentType.getString(ctx, "ability");
        if (add && InfiniteAbilityPool.byId(ability) == null) {
            ctx.getSource().sendError(Text.literal("§cUnknown ability '" + ability + "'."));
            return 0;
        }
        List<String> moves = new ArrayList<>(def.moves());
        if (add) {
            if (moves.contains(ability)) {
                ctx.getSource().sendError(Text.literal("§cAlready in the movepool."));
                return 0;
            }
            if (moves.size() >= 8) {
                ctx.getSource().sendError(Text.literal("§cA raid boss can hold at most 8 moves."));
                return 0;
            }
            moves.add(ability);
        } else if (!moves.remove(ability)) {
            ctx.getSource().sendError(Text.literal("§cNot in the movepool."));
            return 0;
        }
        return persist(ctx, withMoves(def, moves),
            "§aMovepool is now: §e" + String.join(", ", moves));
    }

    /** Add one obstacle row for the boss named by the "id" argument. Rejects an unknown
     *  tile type the same way {@code moves add} rejects an unknown ability. */
    private static int addObstacle(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                   int count, int cluster, String block) {
        RaidBossDefinition def = require(ctx, StringArgumentType.getString(ctx, "id"));
        if (def == null) return 0;
        String rawTile = StringArgumentType.getString(ctx, "tile");
        String tile = resolveTileType(rawTile);
        if (tile == null) {
            ctx.getSource().sendError(Text.literal("§cUnknown tile type '" + rawTile + "'."));
            return 0;
        }
        List<RaidBossObstacle> rows = new ArrayList<>(def.obstacles());
        rows.add(new RaidBossObstacle(tile, block, count, count, cluster));
        return persist(ctx, withObstacles(def, rows), "§aObstacle entry added.");
    }

    /** Upper-cased {@link TileType} name for a case-insensitive user string, or null when
     *  the string names no tile type. Mirrors RaidBossParser's own private resolver. */
    private static String resolveTileType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return TileType.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static int setField(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                String id, String field, String value) {
        RaidBossDefinition d = require(ctx, id);
        if (d == null) return 0;
        try {
            RaidBossDefinition updated = switch (field) {
                case "name" -> new RaidBossDefinition(d.id(), value, d.entityTypeId(), d.hp(), d.attack(),
                    d.defense(), d.range(), d.speed(), d.moves(), d.power(), d.arenaVariant(),
                    d.environmentId(), d.bounty(), d.loot(), d.obstacles(), d.weight());
                case "entity" -> new RaidBossDefinition(d.id(), d.name(), value, d.hp(), d.attack(),
                    d.defense(), d.range(), d.speed(), d.moves(), d.power(), d.arenaVariant(),
                    d.environmentId(), d.bounty(), d.loot(), d.obstacles(), d.weight());
                case "hp" -> new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(),
                    Integer.parseInt(value), d.attack(), d.defense(), d.range(), d.speed(), d.moves(),
                    d.power(), d.arenaVariant(), d.environmentId(), d.bounty(), d.loot(), d.obstacles(), d.weight());
                case "attack" -> new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(),
                    Integer.parseInt(value), d.defense(), d.range(), d.speed(), d.moves(), d.power(),
                    d.arenaVariant(), d.environmentId(), d.bounty(), d.loot(), d.obstacles(), d.weight());
                case "defense" -> new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(),
                    d.attack(), Integer.parseInt(value), d.range(), d.speed(), d.moves(), d.power(),
                    d.arenaVariant(), d.environmentId(), d.bounty(), d.loot(), d.obstacles(), d.weight());
                case "range" -> new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(),
                    d.attack(), d.defense(), Integer.parseInt(value), d.speed(), d.moves(), d.power(),
                    d.arenaVariant(), d.environmentId(), d.bounty(), d.loot(), d.obstacles(), d.weight());
                case "speed" -> new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(),
                    d.attack(), d.defense(), d.range(), Integer.parseInt(value), d.moves(), d.power(),
                    d.arenaVariant(), d.environmentId(), d.bounty(), d.loot(), d.obstacles(), d.weight());
                case "bounty" -> new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(),
                    d.attack(), d.defense(), d.range(), d.speed(), d.moves(), d.power(),
                    d.arenaVariant(), d.environmentId(), Integer.parseInt(value), d.loot(), d.obstacles(), d.weight());
                case "weight" -> new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(),
                    d.attack(), d.defense(), d.range(), d.speed(), d.moves(), d.power(),
                    d.arenaVariant(), d.environmentId(), d.bounty(), d.loot(), d.obstacles(), Integer.parseInt(value));
                // Authors think 1-based; -1 or 0 means "roll a variant per instance".
                case "arena" -> new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(),
                    d.attack(), d.defense(), d.range(), d.speed(), d.moves(), d.power(),
                    Math.max(-1, Integer.parseInt(value) - 1), d.environmentId(), d.bounty(),
                    d.loot(), d.obstacles(), d.weight());
                case "environment" -> new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(),
                    d.attack(), d.defense(), d.range(), d.speed(), d.moves(), d.power(),
                    d.arenaVariant(), value, d.bounty(), d.loot(), d.obstacles(), d.weight());
                default -> null;
            };
            if (updated == null) {
                ctx.getSource().sendError(Text.literal("§cUnknown field '" + field + "'."));
                return 0;
            }
            return persist(ctx, updated, "§aSet " + field + " = " + value);
        } catch (NumberFormatException e) {
            ctx.getSource().sendError(Text.literal("§c'" + value + "' is not a whole number."));
            return 0;
        }
    }

    private static RaidBossDefinition withMoves(RaidBossDefinition d, List<String> moves) {
        return new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(), d.attack(),
            d.defense(), d.range(), d.speed(), List.copyOf(moves), d.power(), d.arenaVariant(),
            d.environmentId(), d.bounty(), d.loot(), d.obstacles(), d.weight());
    }

    private static RaidBossDefinition withLoot(RaidBossDefinition d, List<RaidBossLootEntry> loot) {
        return new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(), d.attack(),
            d.defense(), d.range(), d.speed(), d.moves(), d.power(), d.arenaVariant(),
            d.environmentId(), d.bounty(), List.copyOf(loot), d.obstacles(), d.weight());
    }

    private static RaidBossDefinition withObstacles(RaidBossDefinition d, List<RaidBossObstacle> obstacles) {
        return new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(), d.attack(),
            d.defense(), d.range(), d.speed(), d.moves(), d.power(), d.arenaVariant(),
            d.environmentId(), d.bounty(), d.loot(), List.copyOf(obstacles), d.weight());
    }

    private static RaidBossDefinition withPower(RaidBossDefinition d, RaidBossPower power) {
        return new RaidBossDefinition(d.id(), d.name(), d.entityTypeId(), d.hp(), d.attack(),
            d.defense(), d.range(), d.speed(), d.moves(), power, d.arenaVariant(),
            d.environmentId(), d.bounty(), d.loot(), d.obstacles(), d.weight());
    }

    /**
     * Validate, then write and hot-reload. An edit that would make the definition
     * unloadable is refused with the reason and the file is left untouched.
     */
    private static int persist(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                               RaidBossDefinition def, String successMessage) {
        java.util.Set<String> known = new java.util.HashSet<>(InfiniteAbilityPool.allIds());
        RaidBossParser.Result check = RaidBossParser.parse(
            RaidBossJsonWriter.toJson(def), def.id(), known);
        if (!check.ok()) {
            ctx.getSource().sendError(Text.literal(
                "§cRefused: " + String.join("; ", check.errors())));
            return 0;
        }
        if (!RaidBossJsonLoader.save(def)) {
            ctx.getSource().sendError(Text.literal("§cCould not write the definition file."));
            return 0;
        }
        RaidBossRegistry.put(def);
        ctx.getSource().sendFeedback(() -> Text.literal(successMessage), true);
        return 1;
    }
}
