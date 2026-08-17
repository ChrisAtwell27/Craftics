package com.crackedgames.craftics.util;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * The occasional reminder that the Discord exists and that bugs can be reported in game.
 *
 * <p>Chat, deliberately, rather than the hint system: hints are teaching aids tied to what the
 * player is doing right now and they get dismissed for good once learned. These two are not
 * lessons, they are standing information, and chat is where a player expects to find a link
 * they can scroll back to.
 *
 * <p>One message every {@link #INTERVAL_MINUTES} minutes. Only the Discord line is live right
 * now; the bug-report nudge is written and commented out in {@link #tick} until the report
 * intake is pointed at its final endpoint.
 * Nothing fires when the server is empty, and nothing fires during a fight - a link dropped
 * into the combat log in the middle of a boss turn is noise at exactly the wrong moment.
 */
public final class ChatNudges {

    private ChatNudges() {}

    /** How long between nudges. */
    public static final int INTERVAL_MINUTES = 30;

    private static final int INTERVAL_TICKS = INTERVAL_MINUTES * 60 * 20;

    public static final String DISCORD_URL = "https://discord.gg/kY9F5Sjf4d";

    private static final String DISCORD_PREFIX =
        "§d§lCraftics §7- join the Discord for updates and help: ";

    /** Written and ready; not sent yet. See the note in {@link #tick}. */
    private static final String BUG_REPORT_MESSAGE =
        "§d§lCraftics §7- found a bug? §f/bug§7 opens a report form, or "
            + "§f/bug 1 what happened§7 sends one straight away with your latest screenshot.";

    private static int ticks = 0;
    /** Flips each time one fires, so the two alternate instead of rolling randomly. */
    private static boolean showDiscordNext = true;

    /** Reset between worlds so a fresh session does not inherit the last one's timer. */
    public static void reset() {
        ticks = 0;
        showDiscordNext = true;
    }

    /** Called once per server tick. Cheap: an int compare on all but one tick in 36,000. */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (++ticks < INTERVAL_TICKS) return;
        ticks = 0;

        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        // Only the Discord line is live. The bug-report nudge is held back until the report
        // intake is pointed at its final endpoint - telling players to file bugs before the
        // thing that receives them is settled just loses their reports.
        //
        // To restore it: pick between discordMessage() and BUG_REPORT_MESSAGE on
        // showDiscordNext below, then flip showDiscordNext each time one fires.
        Text message = discordMessage();

        for (ServerPlayerEntity player : players) {
            // Mid-fight is the wrong moment for either of these.
            if (com.crackedgames.craftics.combat.CombatManager.isEngaged(player.getUuid())) continue;
            player.sendMessage(message, false);
        }
    }

    /**
     * The link half carries a real OPEN_URL click event. A bare URL in a server-sent message is
     * inert text - the client only linkifies what it renders from its own chat parsing - so the
     * event has to be attached by hand, version split and all.
     */
    private static Text discordMessage() {
        return Text.literal(DISCORD_PREFIX)
            .append(Text.literal("§b§n" + DISCORD_URL)
                .styled(s -> s.withClickEvent(openUrl(DISCORD_URL))
                    .withHoverEvent(openUrlHover())));
    }

    //? if <=1.21.4 {
    private static net.minecraft.text.ClickEvent openUrl(String url) {
        return new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.OPEN_URL, url);
    }

    private static net.minecraft.text.HoverEvent openUrlHover() {
        return new net.minecraft.text.HoverEvent(
            net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Click to open the Discord invite"));
    }
    //?} else {
    /*private static net.minecraft.text.ClickEvent openUrl(String url) {
        return new net.minecraft.text.ClickEvent.OpenUrl(java.net.URI.create(url));
    }

    private static net.minecraft.text.HoverEvent openUrlHover() {
        return new net.minecraft.text.HoverEvent.ShowText(Text.literal("Click to open the Discord invite"));
    }
    *///?}
}
