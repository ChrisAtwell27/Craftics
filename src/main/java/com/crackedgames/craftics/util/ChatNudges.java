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

    private static final String DISCORD_MESSAGE =
        "§d§lCraftics §7- join the Discord for updates and help: §b" + DISCORD_URL;

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

        // The URL goes out as plain text on purpose: the vanilla client links URLs it finds in
        // chat by itself, and building a ClickEvent by hand would need a version split (the
        // class changed shape in 1.21.5) for no gain.
        //
        // Only the Discord line is live. The bug-report nudge is held back until the report
        // intake is pointed at its final endpoint - telling players to file bugs before the
        // thing that receives them is settled just loses their reports.
        //
        // To restore it: pick between DISCORD_MESSAGE and BUG_REPORT_MESSAGE on
        // showDiscordNext below, then flip showDiscordNext each time one fires.
        Text message = Text.literal(DISCORD_MESSAGE);

        for (ServerPlayerEntity player : players) {
            // Mid-fight is the wrong moment for either of these.
            if (com.crackedgames.craftics.combat.CombatManager.isEngaged(player.getUuid())) continue;
            player.sendMessage(message, false);
        }
    }
}
