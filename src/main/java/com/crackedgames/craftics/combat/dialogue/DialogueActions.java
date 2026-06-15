package com.crackedgames.craftics.combat.dialogue;

import com.crackedgames.craftics.CrafticsMod;

/** Maps a dialogue choice's action key to a coarse outcome the event code acts on.
 *  Unknown/missing keys degrade to {@link Outcome#CLOSE} so bad data never soft-locks. */
public final class DialogueActions {

    public enum Outcome { OPEN_TRADE, REOPEN_SHOP, FINISH, CLOSE }

    private DialogueActions() {}

    public static Outcome resolve(String action) {
        if (action == null) return Outcome.CLOSE;
        switch (action) {
            case "open_trade":  return Outcome.OPEN_TRADE;
            case "reopen_shop": return Outcome.REOPEN_SHOP;
            case "finish":      return Outcome.FINISH;
            case "close":       return Outcome.CLOSE;
            default:
                CrafticsMod.LOGGER.warn("Unknown dialogue action '{}' - treating as close", action);
                return Outcome.CLOSE;
        }
    }
}
