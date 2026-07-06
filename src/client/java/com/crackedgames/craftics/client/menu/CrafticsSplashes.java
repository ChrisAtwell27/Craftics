package com.crackedgames.craftics.client.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Splash-text pool for {@link CrafticsTitleScreen} - the little yellow line
 * that pulses beside the logo, vanilla-style, but all Craftics.
 *
 * <p>{@link #pick} draws from the big static pool plus a handful of
 * <em>contextual</em> lines built from the player's actual run (current biome,
 * progress, NG+ tier), so every few visits the menu ribs you personally.</p>
 */
public final class CrafticsSplashes {

    private CrafticsSplashes() {}

    private static final String[] POOL = {
        // ── tactics & combat ────────────────────────────────────────────
        "Turn-based!",
        "Now with 100% more action points!",
        "Think before you step!",
        "One tile at a time!",
        "Grid-locked and loving it!",
        "Every block is a battlefield!",
        "Isometric!",
        "Don't waste your move points!",
        "End turn responsibly!",
        "Press Y before you cry!",
        "Check the threat overlay!",
        "Red tiles mean RUN!",
        "Mind the marked tiles!",
        "The arrows show which way you'll be dragged!",
        "Get clear of the marked tiles!",
        "Step aside!",
        "Flanking optional, dying free!",
        "Positioning is 90% of the battle!",
        "The other 10% is also positioning!",
        "Diagonal is a state of mind!",
        "Your move!",
        "It's always your turn somewhere!",
        "Patience is a weapon!",
        "Action economy!",
        "Wait for it...",
        "Never skip your bonus action!",
        "Tactics over reflexes!",
        "Slow is smooth, smooth is dead enemies!",
        "Turn 47: still fine!",
        "The camera is your ally!",

        // ── biomes & campaign ───────────────────────────────────────────
        "18 biomes of pain!",
        "Also try the Nether!",
        "The End is only the middle!",
        "Warm route or cool route?",
        "Take the river, they said!",
        "The Deep Dark remembers!",
        "Beware the Dragon's Nest!",
        "Chorus fruit for the road!",
        "Soul sand can't slow a turn-based hero!",
        "Basalt? More like bad salt!",
        "It's nice in the Plains this time of year!",
        "The desert scorches, the tundra bites!",
        "Dense Jungle, denser enemies!",
        "Something stirs in the Dark Forest!",
        "Stony Peaks, stonier resolve!",
        "The caverns go deeper!",
        "Sculk happens!",
        "The Crimson Forest is not a vibe, it's a warning!",
        "Warped in the best way!",
        "Outer End? More like outta here!",
        "End City property values are through the floor!",
        "Every biome is somebody's boss fight!",
        "The path branches. You don't have to!",
        "Discover them all!",
        "??? is a biome of endless possibility!",
        "The hub missed you!",
        "There's no place like /home!",
        "Your island, your rules!",
        "Arenas built while you wait!",
        "Procedurally generated, personally survived!",

        // ── enemies & bosses ────────────────────────────────────────────
        "65+ ways to lose!",
        "Creepers wait their turn. Politely.",
        "Skeletons never miss a budget!",
        "The Warden hears your turns!",
        "The Hexweaver knits your doom!",
        "Dodge the Fang Line!",
        "Riptide Charge incoming!",
        "The Void Gale blows nobody good!",
        "Miner's Fury: now with more fury!",
        "Death Charge! Best charge!",
        "Fire Pillars: stand elsewhere!",
        "The Rockbreaker breaks more than rocks!",
        "The Hollow King is full of surprises!",
        "The Tidecaller called. It's for you!",
        "The Void Walker walks. You should run!",
        "ENRAGED!",
        "Phase two is a lifestyle!",
        "Boss defeated! (eventually)",
        "The boss is charging SOMETHING!",
        "It will charge down the marked path!",
        "A heavy blow will crush the marked tiles!",
        "Second phases have second opinions!",
        "Every boss speaks in its own visual voice!",
        "The golems remember!",
        "Piglins respect exactly one thing!",
        "Endermen hate this one trick!",
        "The dragon is just misunderstood. Also lethal!",

        // ── items & weapons ─────────────────────────────────────────────
        "No new items!*",
        "*Except the Level Select!",
        "Every item has a purpose!",
        "The Sword on a Stick bonks!",
        "Mjolnir approves!",
        "Chakrams always come back!",
        "Frostfall runs cold!",
        "Enigma does whatever it feels like!",
        "Hiveheart unleashes the swarm!",
        "The Watcher is watching!",
        "Bring a totem. Or five!",
        "Goat horns: now tactical!",
        "Trims are stats now!",
        "Maces go THUMP!",
        "Wind Burst responsibly!",
        "A fishing rod is a repositioning tool!",
        "Potions are turns in a bottle!",
        "Read the guide book! Press G!",
        "Sharpen your axe and your plan!",
        "Your hotbar is a spellbook!",
        "Slot nine is sacred!",

        // ── emeralds, traders & bartering ───────────────────────────────
        "Emeralds well spent!",
        "The piglin looks insulted!",
        "The piglin CANNOT resist this much gold!",
        "Barter responsibly!",
        "Trading Hall: open all night!",
        "2 left!",
        "SOLD OUT!",
        "Big-ticket items are one-offs!",
        "The market rotates. So should you!",
        "Haggling is a combat skill!",
        "Gold-plated diplomacy!",
        "Merchants love a returning customer!",
        "Ask about the loot insurance!",
        "Every emerald tells a story!",

        // ── party, pets & friends ───────────────────────────────────────
        "Party up!",
        "Bring friends, share the blame!",
        "Solo is a party of one!",
        "Your pets wait at the hub!",
        "Pet the pets!",
        "Beast tamers do it with friends!",
        "A wolf on the grid is worth two in the bush!",
        "Shift-right-click to recruit!",
        "The lead is a leash and a plan!",
        "Mount up!",
        "Coal golems: free with purchase of netherite golem!",
        "Teamwork makes the boss scream!",

        // ── progression & NG+ ───────────────────────────────────────────
        "NG+ scales. Do you?",
        "Death is a setback, not a verdict!",
        "Lose the fight, keep the lesson!",
        "Level up! Again!",
        "Respec without regret! Press H!",
        "Affinity is forever. Until you press J!",
        "Your build is valid!",
        "Stats don't lie. Enemies do!",
        "Second Wind is best wind!",
        "Flawless! (this once)",
        "Pacifist runs are a real thing!",
        "Speedrun the plains, savor the End!",
        "New Game Plus, same you!",

        // ── menu & meta ─────────────────────────────────────────────────
        "The menu knows where you are!",
        "You are here!",
        "Music by Minecraft Dungeons!",
        "Also try Minecraft Dungeons!",
        "Also try vanilla! (we'll wait)",
        "Now with a main menu!",
        "100% blob-free logo!",
        "Cracked, not broken!",
        "CrackedGames certified!",
        "Powered by Fabric!",
        "Made with mixins!",
        "Stonecutter approved!",
        "Multi-version!",
        "As seen on CurseForge!",
        "Turn-based since 2025!",
        "The splash text is load-bearing!",
        "Do not read this splash!",
        "This splash intentionally left blank!",
        "Ken Burns mode!",
        "Now in soft focus!",
        "The backdrop is your memory lane!",
        "Press Continue to continue!",
        "Quit is a legal move!",
        "Esc does nothing here. This is home!",
        "Achievement unlocked: reading splashes!",
        "May your rolls be crits!",
        "gg go next biome!",
    };

    /**
     * Pick one splash. {@code snap} (nullable) contributes a few lines built
     * from the player's actual run so the pool stays personal.
     */
    public static String pick(Random rng, MenuWorldState.Snapshot snap) {
        List<String> contextual = new ArrayList<>();
        if (snap != null && snap.hasWorld()) {
            contextual.add("Currently surviving " + snap.biomeName() + "!");
            contextual.add(snap.biomeName() + " won't clear itself!");
            int cleared = Math.max(0, Math.min(snap.highestUnlocked() - 1, snap.totalBiomes()));
            int left = Math.max(0, snap.totalBiomes() - cleared);
            if (cleared == 0) {
                contextual.add("A fresh run! Nothing cleared, nothing lost!");
            } else {
                contextual.add(cleared + " down, " + left + " to go!");
            }
            if (snap.ngPlus() > 0) {
                contextual.add("NG+" + snap.ngPlus() + "?! Touch grass!");
                contextual.add("Still not tired of winning?");
            }
        }

        // Contextual lines are rarer than pool lines but not vanishing:
        // ~1 in 8 visits shows a personal one (when any exist).
        if (!contextual.isEmpty() && rng.nextInt(8) == 0) {
            return contextual.get(rng.nextInt(contextual.size()));
        }
        return POOL[rng.nextInt(POOL.length)];
    }
}
