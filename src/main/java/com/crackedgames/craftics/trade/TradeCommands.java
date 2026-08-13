package com.crackedgames.craftics.trade;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/**
 * {@code /trade <player>} to ask, {@code /trade accept <player>} to answer, {@code /trade
 * cancel} to walk away.
 *
 * <p>Asking is deliberately separate from opening. A command that threw a screen up in
 * somebody's face would interrupt whatever they were doing, which in this mod can mean a fight,
 * so the invitation is a chat line they can click when they are ready. It goes stale on its own
 * after a minute so a forgotten invite cannot be accepted an hour later.
 */
public final class TradeCommands {

    private TradeCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("trade")
            .then(CommandManager.literal("accept")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> accept(ctx.getSource(),
                        EntityArgumentType.getPlayer(ctx, "player")))))
            .then(CommandManager.literal("cancel")
                .executes(ctx -> cancel(ctx.getSource())))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> ask(ctx.getSource(),
                    EntityArgumentType.getPlayer(ctx, "player")))));
    }

    private static int ask(ServerCommandSource source, ServerPlayerEntity target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity asker = source.getPlayerOrThrow();
        if (asker.getUuid().equals(target.getUuid())) {
            asker.sendMessage(Text.literal("§cYou cannot trade with yourself."), false);
            return 0;
        }
        if (TradeSession.of(asker.getUuid()) != null) {
            asker.sendMessage(Text.literal("§cYou are already in a trade. §7/trade cancel"), false);
            return 0;
        }
        if (TradeSession.of(target.getUuid()) != null) {
            asker.sendMessage(Text.literal("§c" + target.getName().getString()
                + " is already in a trade."), false);
            return 0;
        }

        TradeSession.invite(asker, target, System.currentTimeMillis());
        asker.sendMessage(Text.literal("§eTrade offered to " + target.getName().getString()
            + ". §7Waiting for them to accept."), false);

        String accept = "/trade accept " + asker.getName().getString();
        target.sendMessage(Text.literal("§e" + asker.getName().getString()
            + " wants to trade with you. §a[Click to accept]")
            .setStyle(Style.EMPTY.withClickEvent(
                //? if <=1.21.4 {
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, accept)
                //?} else {
                /*new ClickEvent.RunCommand(accept)
                *///?}
            )), false);
        target.sendMessage(Text.literal("§7Or type §f" + accept), false);
        return 1;
    }

    private static int accept(ServerCommandSource source, ServerPlayerEntity from)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me = source.getPlayerOrThrow();
        var invite = TradeSession.inviteFrom(me.getUuid(), from.getUuid(), System.currentTimeMillis());
        if (invite == null) {
            me.sendMessage(Text.literal("§cNo trade offer from " + from.getName().getString()
                + " (offers expire after a minute)."), false);
            return 0;
        }
        TradeSession session = TradeSession.open(from, me);
        if (session == null) {
            me.sendMessage(Text.literal("§cOne of you is already in a trade."), false);
            return 0;
        }
        TradeMenus.open(me);
        TradeMenus.open(from);
        from.sendMessage(Text.literal("§a" + me.getName().getString()
            + " accepted your trade."), false);
        return 1;
    }

    private static int cancel(ServerCommandSource source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity me = source.getPlayerOrThrow();
        TradeSession session = TradeSession.of(me.getUuid());
        if (session == null) {
            TradeSession.clearInvite(me.getUuid());
            me.sendMessage(Text.literal("§7You are not in a trade."), false);
            return 0;
        }
        TradeMenus.cancel(me, session,
            "§c" + me.getName().getString() + " cancelled the trade. Nothing was traded.");
        return 1;
    }
}
