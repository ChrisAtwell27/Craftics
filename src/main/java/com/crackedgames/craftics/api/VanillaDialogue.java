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

        // Wounded Traveler - base intro. Food choices are appended per-player at
        // runtime (since they depend on the player's inventory).
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:traveler_intro", "minecraft:villager", "traveler_intro",
            List.of("\"Please... I haven't eaten in days...\"",
                    "The traveler eyes your pack hopefully."),
            List.of(new DialogueChoice("Walk away", "traveler:leave"))));

        // Shiny on the ground - narrator vote dialogue. Majority "Take it"
        // triggers a 50/50 between a rare reward (to one Yes voter) and an
        // ambush. Majority "Leave it" walks past safely. A tie triggers the
        // ambush. Resolution narrator lines are built at runtime.
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:shiny_intro", "", "shiny_intro",
            List.of("You see something shiny on the ground.",
                    "Approach it?"),
            List.of(new DialogueChoice("Take it", "shiny:take"),
                    new DialogueChoice("Leave it", "shiny:leave"))));

        // Wandering Enchanter - step 1 (category select). The weapon/armor lists
        // are built per-player at runtime from the player's inventory.
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:enchanter_intro", "minecraft:villager", "enchanter_intro",
            List.of("\"I can enhance your gear, traveler.\"",
                    "\"Show me a weapon, or a piece of armor.\""),
            List.of(new DialogueChoice("Enchant a weapon", "enchanter:weapons"),
                    new DialogueChoice("Enhance armor", "enchanter:armor"),
                    new DialogueChoice("Decline", "enchanter:decline"))));

        // Per-biome boss intros, one per vanilla biome. CombatManager looks
        // these up by biomeId; mod biomes without a registered intro simply
        // skip the dialogue (no generic placeholder).
        // Overworld
        bossIntro("plains",
            "The grass stops moving.",
            "A bloated zombie staggers from the haystack.");
        bossIntro("forest",
            "Runes etched into the bark begin to glow.",
            "An Evoker raises its hands. Fangs split the earth.");
        bossIntro("desert",
            "The sand shifts beneath your feet.",
            "A Sandstorm Pharaoh rises from the dunes.");
        bossIntro("jungle",
            "The canopy goes still.",
            "Eyes glitter above. The vines tremble.");
        bossIntro("mountain",
            "Boulders skitter loose from the cliffs.",
            "A Vindicator charges down the scree, axe raised.");
        bossIntro("snowy",
            "Ice cracks across the frozen lake.",
            "A Stray rises from a snowdrift, arrow nocked.");
        bossIntro("river",
            "The current pulls back from your feet.",
            "A Drowned breaks the surface, trident in hand.");
        bossIntro("cave",
            "Water drips somewhere ahead.",
            "A massive shape lurches from the dark.");
        bossIntro("deep_dark",
            "Silence. Not even your breath.",
            "The Warden listens.");
        // Nether
        bossIntro("nether_wastes",
            "Heat rolls in waves from the netherrack.",
            "A massive Magma Cube bounces into view.");
        bossIntro("crimson_forest",
            "Crimson nyliums pulse like a heartbeat.",
            "An old archer steps from the trees, bow drawn.");
        bossIntro("warped_forest",
            "Warped spores swirl thick in the air.",
            "An Enderman opens its jaws and screams.");
        bossIntro("soul_sand_valley",
            "Ghost-blue fires brighten. Souls drift higher.",
            "A Ghast wails overhead, fireballs already kindling.");
        bossIntro("basalt_deltas",
            "The basalt cracks. The air goes silent.",
            "The Wither rises, three heads searching for prey.");
        // End
        bossIntro("end_city",
            "The end city walls hum with violet light.",
            "A Shulker peels open its shell.");
        bossIntro("dragons_nest",
            "Wingbeats thunder above the obsidian pillars.",
            "The Dragon descends.");
        bossIntro("outer_end_islands",
            "The void hangs close on all sides.",
            "An Enderman teleports onto your tile.");
        bossIntro("chorus_grove",
            "The chorus stalks sway without wind.",
            "An Enderman blinks into your path.");

        // Treasure Vault narrator dialogue (no NPC). Empty speaker = narrator box.
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:vault_intro", "", "vault_intro",
            List.of("A hidden vault gleams in the lantern light.",
                    "Riches lie within. No guards. No traps."),
            List.of(new DialogueChoice("Open the vault", "vault:open"),
                    new DialogueChoice("Walk away", "vault:leave"))));

        // Dig Site push-your-luck minigame. The base intro is the brush=0 state.
        // CombatManager rebuilds the dialogue per player after each brush, showing
        // the updated pull chance and removing "Keep brushing" once it hits 100%.
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:dig_site_intro", "", "dig_site_intro",
            List.of("The ground has been disturbed. Something lies buried beneath.",
                    "Pull chance: 5%"),
            List.of(new DialogueChoice("Keep brushing", "dig:brush"),
                    new DialogueChoice("Attempt", "dig:attempt"))));

        // Trial Chamber party-vote narrator. Static registration mirrors the
        // shipped JSON so datapacks can override; CombatManager.offerTrialVote
        // rebuilds the dialogue at runtime to swap solo / party prompt framing.
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:trial_intro", "", "trial_intro",
            List.of("A mysterious trial chamber lies ahead.",
                    "Rare loot inside, but stronger foes.",
                    "Vote: enter together, or pass?"),
            List.of(new DialogueChoice("Enter the trial", "trial:enter"),
                    new DialogueChoice("Pass", "trial:pass"))));

        // Ominous Trial Chamber party-vote narrator. Same tie-favors-enter rule
        // as the regular trial, just darker flavor.
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:trial_ominous_intro", "", "trial_ominous_intro",
            List.of("A dark presence calls from the depths.",
                    "Legendary loot lies within. A Warden hunts.",
                    "Vote: accept the trial, or pass?"),
            List.of(new DialogueChoice("Accept the Ominous Trial", "trial:enter"),
                    new DialogueChoice("Not worth the risk", "trial:pass"))));
    }

    private static void intro(String type, String line1, String line2) {
        String group = "trader_intro_" + type;
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:" + group, TRADER, group,
            List.of(line1, line2),
            List.of(new DialogueChoice("Let's trade", "open_trade"),
                    new DialogueChoice("Maybe later", "close"))));
    }

    /** Register a choiceless narrator dialogue for a biome's boss intro under
     *  the id {@code craftics:boss_intro_<biomeId>}. */
    private static void bossIntro(String biomeId, String line1, String line2) {
        DialogueRegistry.register(new DialogueDefinition(
            "craftics:boss_intro_" + biomeId, "", "boss_intro",
            List.of(line1, line2),
            List.of()));
    }
}
