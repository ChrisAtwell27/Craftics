package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.dialogue.DialogueChoice;
import com.crackedgames.craftics.combat.dialogue.DialogueDefinition;
import com.crackedgames.craftics.combat.dialogue.DialogueRegistry;
import java.util.List;

/** Built-in dialogue registered from code so it always exists even without datapacks.
 *  Shipped JSON under data/craftics/dialogue/ mirrors this content. */
public final class VanillaDialogue {

    private VanillaDialogue() {}

    private static final String TRADER = "minecraft:wandering_trader";

    public static void register() {
        intro("weaponsmith", "Steel and sweat, friend.", "Blades that'll outlast your foes. Care to look?");
        intro("armorer", "Sturdy gear for sturdy travelers.", "Plate, mail, the works. Shall we trade?");
        intro("provisioner", "Hungry from the road?", "Fresh provisions to keep you fighting. Interested?");
        intro("alchemist", "Ahh, a customer with potential...", "Potions, poisons, powders. What'll it be?");
        intro("supplier", "Need raw materials?", "Ingots, redstone, and more. Take a look.");
        intro("decorator", "A discerning eye, I see.", "Flowers, banners, fineries. Care to browse?");
        intro("craftsman", "Every adventurer needs the right tools.", "Workstations and more. Have a look?");
        intro("curiosity_dealer", "Heh heh... curious, are we?", "Oddities and rarities, for the right price.");

        DialogueRegistry.register(new DialogueDefinition(
            "craftics:trader_done", TRADER, "trader_done",
            List.of("Are you done shopping?"),
            List.of(new DialogueChoice("Yes, I'm done", "finish"),
                    new DialogueChoice("No, keep shopping", "reopen_shop"))));

        // Shrine of Fortune narrator dialogue (no NPC). Empty speaker = narrator box.
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:shrine_intro", "", "shrine_intro",
            List.of("A shrine of fortune hums before you.",
                    "Make an offering and test your luck."),
            List.of(new DialogueChoice("Small Offering (2 emeralds)", "shrine:small"),
                    new DialogueChoice("Medium Offering (5 emeralds)", "shrine:medium"),
                    new DialogueChoice("Large Offering (10 emeralds)", "shrine:large"),
                    new DialogueChoice("Walk away", "shrine:leave"))));
    }

    private static void intro(String type, String line1, String line2) {
        String group = "trader_intro_" + type;
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:" + group, TRADER, group,
            List.of(line1, line2),
            List.of(new DialogueChoice("Let's trade", "open_trade"),
                    new DialogueChoice("Maybe later", "close"))));
    }
}
