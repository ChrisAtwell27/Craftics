Changelog
0.2.6
Multiplayer

- Boss-fight (and Dig Site) intros no longer soft-lock parties on the "☠ Boss Approaching ☠ / Waiting for party..." screen. The narrator intro before a boss level is set up after the previous fight's combat state and party-leader routing map have already been torn down; the packet router that forwards a member's dismiss to the leader's combat manager only checked the event and trader pending-sets, not the intro or dig-site ones, so every non-leader's dismiss landed on their own inactive manager and the leader's gate never drained. Solo play was unaffected because the lone player routes to their own active manager. The router now resolves through the intro and dig-site gates too

0.2.5
Combat and Camera

- The camera no longer follows enemies and allies around during their turn by default. A new "Camera Follows Enemies" setting (default off) controls the panning, and enemy-turn pacing ("Cinematic Enemy Turns") now defaults off as well so enemy turns play at full speed. Both can be re-enabled in the config

0.2.4
Gameplay, Progression, and Economy

- Full content rewrite verified against the code: every bestiary stat line now shows the real base values from biome data (with a visible "stats scale with biome, level & party" note), boss entries match the live boss roster and abilities, and stale mechanics (old goat horn taunt, echo shard recall, 13% trader chance, unavoidable ambush, triangular bleed numbers, flat +1-per-tier weapon damage) are gone
- New coverage: affinity points and all eight affinity perks, per-weapon AP costs and the real damage tables, damage types explainer, bush stealth, temporary walls and fire spread, stacked enemies, food and eating (golden carrot), goat horn variants, hybrid armor sets (all 15), mob heads, every between-level event including Dig Site / Wandering Enchanter / Piglin Barter / the Something Shiny vote, a Multiplayer category, hub/campaign/NG+/achievements pages, and addon pages for Golem Overhaul, MoreTotems, Basic Weapons, instruments and Pale Garden Backport
- Bestiary additions: Bogged, Breeze, Slime, Vex, Creaking, End Crystal. Removed the Ashen Warlord entry (boss is not in the rotation; Basalt Deltas belongs to The Wither). Boss entries are renamed to their real display names
- Boss bestiary entries actually unlock now: the server unlocks the boss's display name ("The Revenant") on boss fights instead of only the base mob type, which previously left every boss entry permanently locked
- New guide book UI: responsive parchment-and-leather layout that scales with the window, item icons on every category, entry and bestiary cell, an icon grid bestiary with a discovery progress bar and hover tooltips, structured stat badges (role/HP/ATK/DEF/SPD/RNG/size) with color-coded weakness/resist lines, custom hover states, a real scrollbar, page-flip arrow keys, and gold-bordered boss cells

Combat and item tuning

- The Frostbound Huntsman's harpoon pull now telegraphs a full arena lane (row or column) in the direction of forced movement, making the direction unmistakable before resolution. Bestiary entry is updated with phase 1/2 breakdown and current ability descriptions
- Armor durability simplified: each hit dealt to the player now costs 1 durability per piece (all four slots simultaneously) instead of scaling with damage amount. A full diamond set lasts ~90 hits before breaking
- Pottery sherds are no longer guaranteed single-use. Casting now has a 10% base shatter chance that is reduced by Special affinity (points + potency bonus), and sherd tooltips were updated to show the new break-chance behavior
Stability and Fixes

- Artifacts mimics track their attack rhythm per fight instead of sharing one across the server, so simultaneous campsite events no longer desync. The per-fight AI mechanism is generalized and also covers blazes
- Hovering a phase-two boss no longer shows a bogus phase=2 status effect in the inspect panel
- Removed a leftover Artifacts debug log that printed every turn, and cached the mimic reflection lookup that retried a class load per spawn when Artifacts is absent
- The composite-action dispatcher rejects a second movement sub-action in one composite instead of silently dropping the first move
- fabric.mod.json now lists all twelve mods Craftics integrates with, so modpack tools can discover the compat surface
- The shared hit-and-run helper is now size-aware for its retreat scan
World, Arenas, and Multiplayer

- Concave shapes work now. The corner sorter ordered markers by angle around their centroid, which self-intersects on shapes like an L (its concave vertex sits at the centroid), so the playable mask covered regions outside the drawn outline and mobs, floor, and hover targeting showed up out there. Rectilinear outlines (L, T, plus, U) are now reconstructed exactly from their edge structure, and convex outlines (diamond, octagon, hexagon) keep the angle sort, which is correct for them
- Corner markers can be buried under a regular block on purpose. Each corner now resolves to the surface of whatever covers it, and the arena floor takes the most common corner surface instead of the raw highest marker. A hidden marker no longer drags the floor a block down, which was making the whole interior read as obstacles
- The border ring paints one continuous band of border concrete at the corrected floor height, so it no longer eats blocks a level below the surface and no longer shows up speckled and inconsistent
- Biome obstacle decoration is skipped for polygon arenas. The placers picked tiles across the whole bounding box with no idea of the mask, which scattered random boulders and hazards outside the outline
- The clear-above sweep, tile classification, and player-start snap are bounded to the drawn shape, so terrain outside the outline is no longer wiped, classified, or chosen as a spawn
- A polygon that fails to produce a playable mask now degrades to a plain rectangle over the marker bounding box with the ground preserved, instead of flattening the full level rectangle and laying a stone underlayer
- Two corner markers placed side by side no longer leave a marker block behind when they blend out, and spawn markers (gold, iron, copper, coal) can be buried one block under the floor like the corners

Multiplayer behavior

- Bush invisibility no longer wears off after one turn for a player who stays in the bush. The hide is a rolling buff that needs constant refreshing, and only the current turn-holder was being ticked, so a teammate's stealth lapsed as soon as the turn rotated away. Every party member is refreshed now
- The hidden fire-resistance baseline that blocks vanilla fire and lava damage is enforced for the whole party, so a teammate whose potion fire resistance expired off-turn is no longer left burning
- A teammate knocked below the arena outside their own turn is now rescued and downed through the normal combat flow, instead of falling into the void and dying through vanilla damage
- Water boats are tracked per player. With the old single shared boat, a player parked on water across a turn rotation had the next turn-holder pulled into their boat, and the boat then followed the wrong player's movement

Playtest and usability polish

- Pets returning to the hub now land on the same floor as the player. A pet's offset landing spot could previously resolve on top of a tree, or past the island edge it found no ground at all and left the pet in midair to fall into the void, which silently killed returning pets
- Tile highlights now draw on top of snow layers, so boss telegraphs and the move grid stay visible on snowy arena tiles and under snow golem trails
- Skeletons and other ranged mobs summoned from spawn eggs now fight as ranged kiters with proper range and their iconic weapon in hand (bow, crossbow, trident) instead of bare-hand melee
- Arena schematics with unsupported sand or gravel are stabilized at build time, so terrain no longer collapses when a fight starts and snow resting on top no longer breaks
- Chicken taming is now documented: any seeds work (wheat, melon, pumpkin, beetroot). Seed tooltips cover who they tame, and a new Taming Foods page in the guide book lists every taming item
UI, UX, and Presentation

- Every mob type picks its attack animation from a style registry: spiders pounce, golems and ravagers slam, wolves and cats dash, slimes hop and crash, endermen blink, archers draw. Three new styles add ram (goats, camels), jab (insects, small critters), and channel (witches, evokers). Addon mobs can register any style via CrafticsAPI.registerAttackAnimation, and unregistered mobs keep the classic lunge
- Mob poses got their missing beats: arms snap forward on the hit and ease back to neutral instead of staying cocked, bosses channel with raised arms during telegraphs and roar at phase two, and stunned enemies slump and wobble through their skipped turn
- Co-op avatars no longer share one attack-animation timer, so a teammate's swing can't cut yours short or freeze their avatar, and combat end cleans up every avatar

- The enemy roster is now heads-only: a compact grid of mob portraits with no per-enemy HP bars. Hover an enemy for its bar and numbers in the inspect panel. The boss keeps the one always-visible HP bar at the top of the roster
- The act-order strip's gold acting-now highlight appears for every action, not just attacks: walks, teleports, and ceiling hops all show, and the camera follows the mover
AI and Encounter Design

- Chorus Mind's Resonance Cascade now hits the warned tiles instead of the boss's own tile. Its phase-two spread grows real chorus obstacles, it blinks beside plants instead of onto them, and its abilities aim from where it lands
- Shulker Architect's Bullet Storm is now a real telegraphed volley with the advertised bullet count, and Teleport Link no longer drops the boss onto its own turret
- The Void Herald's phase-two platform collapses resolve reliably instead of being cancelled when another telegraph fires the same turn, and its blink assault respects its 2x2 body
- The Molten King no longer teleport-erupts onto your tile or clips its 4x4 body into walls in a crowded arena, and a blocked leap no longer wastes the cooldown
- Across seven bosses, abilities no longer burn their cooldown when they can't find room to fire, so a crowded arena no longer locks out summons, charges, and rifts
- The Bastion Brute's gore charge stops at deep water, the Wailing Revenant throws a weak fireball instead of idling on full cooldown, and the Wither's decay aura is genuinely passive now
- Phantoms each build their own dive-speed streak instead of sharing one, and no longer park on top of you or your pets while circling

- Zombified piglin pack aggro is now per fight instead of a global flag that never reset. Hit one and its arena packmates turn on you, even if the victim dies to the first hit, and your allied piglins no longer feed the enemy pack's damage bonus
- Magma cubes complete multi-tile bounces instead of laying the fire trail without moving. Both bounce types move and leave the trail, and fire only lands on burnable floor. The same dispatcher fix restores follow-up moves bosses queued behind a telegraph
- Wither skeletons patrol independently instead of sharing one heading, so they no longer march in lockstep
- Hoglins gained the ground stomp their description promised: surrounded by two or more attackers they slam everything around them, and they no longer charge through hazards
- Blazes time their barrage around your pets too, backing off a wolf in their face while keeping you in fireball range. Ghasts panic away from nearby threats and find the around-the-corner drift instead of freezing
- Endermites refuse to blink onto water, like their enderman cousins

- Fixed a state leak in nearly every boss: one shared AI object served all fights, so a boss killed in phase two left the next one starting in phase two with stale cooldowns. The Broodmother's nest cycle and the Hollow King's darkness leaked between fights. Every boss now gets a fresh brain per fight
- Phase two is now a moment: a combat-log callout, a PHASE 2 banner for the party, a roar with a particle burst, and a camera shake with a dark-red flash. The boss HP bar keeps the news with a gold frame and a II badge
- Killing a boss got its payoff: a golden defeat banner, explosion bloom with totem rain, a wither-death knell, and a screen shake and flash. The Molten King's fragments no longer read as a defeat until the last one falls
- Boss intros name the boss in the title card instead of the level, with a heavier sound sting
- Boss telegraphs are easier to read: warned tiles get a pulsing outline and ghost through walls, so a telegraph hidden behind terrain can still be dodged
- Boss minion summons no longer drop reinforcements into lava or fire when safe tiles exist

- Archers and casters kite away from your pets too, not just you. Their retreats and firing spots use tiles they can actually reach this turn, and none back into lava to dodge a sword
- Creepers defuse if everyone leaves the blast radius while the fuse hisses, resuming the chase instead of detonating an empty tile. A creeper about to die blows anyway, and the blast check counts your pets
- Ravager ground stomp now works: surrounded by two or more attackers, it slams an AoE instead of tusking one target
- Vindicators no longer rook-dash through lava or fire
- Spiders retreat to the ceiling to reset their ambush when badly hurt and stop webbing a player who already has a web. Cave spiders bite and scuttle out of reach so the poison does the work
- Husks now get the undead horde bonus like zombies. Wounded zombie villagers panic with +1 attack and +1 movement below half HP. The horde bonus no longer counts the mover's own old tile or your undead allies
- Silverfish swarm: hurt one and the group speeds up. Bee swarms enrage even when the stung bee dies in one hit
- Endermen never teleport onto water, and goats lined up with you deal a true ram, more damage the longer the run-up
- Polar bears use their full 2x2 bulk for reach and their maul knocks you back a tile. Enraged wolves get +1 damage per packmate biting the same victim. Foxes, ocelots, and angry cats strike and spring back out with leftover movement
- The witch's self-heal actually heals now instead of just walking away, and each witch rotates her own brews
- Fixed state-sharing bugs where the evoker, enderman, drowned, and witch kept per-fight state on their shared AI. Evokers stopped summoning vexes after one fight, one frenzied enderman frenzied all future ones, and the first drowned's trident roll fixed every drowned's loadout. Per-mob state now lives on the mob
- Pillagers fire at their stated range 3 instead of 4, and llamas honor their registered spit range
- Evokers summon a second vex when first wounded below half HP

- Tanks (iron golem, turtle, goat) interpose: when the biggest threat is too far to strike, they plant themselves between it and you instead of leaving you open
- Supports (axolotl, frog, villager) hold the player-adjacent tile farthest from the nearest enemy, out of the charge lane
- Melee allies no longer walk past a kill they could secure: scoring favors enemies they can reach and finish this turn
- Flyers (parrot, bee, allay) dive the weakest enemy they can reach and kill this turn, not the globally weakest
- Ranged allies (llama, snow golem) pick a kiting tile that gains the most distance while keeping the shot lined up, instead of hopping two tiles straight back
- Fleeing allies find the around-the-corner escape when the straight line is blocked, and skittish farm animals do the same instead of freezing

HUD, grid, and interaction

- Cursor picking now tests mob hitboxes first, so clicking a tall mob's body no longer selects the tile behind it. It honors wall occlusion and skips invisible mobs so stealth isn't leaked
- Fixed the turn banner fade, which was dead code, and its per-frame timer that made the collapse vary with FPS. The same FPS dependence affected warning-tile and hover pulses
- AP/SPD pips show one per point with adaptive sizing, instead of a fixed 3-slot layout that didn't drain until below 3
- Fixed the +N more enemy collapse double-counting the boss and duplicating a head in the mini list
- Tall grass, ferns, cobwebs, and stairs now read as walkable in client previews, matching the server
- Deep water now reads as unwalkable in the move preview, matching its instant-kill server tile, and the hover cursor no longer flickers on tile boundaries

- Combat HUD: a clickable End Turn button showing the live keybind, smooth HP bars with damage-ghost drain, attack AP cost on the mode pill, an N SPD cost tag at the cursor, an act-order strip during the enemy phase, a theme hint in the inspect panel, HP numbers only on damaged enemies, and a red screen edge below 30% HP
- Grid: perimeter outlines on move/attack/AoE regions, a hover cursor ring, and a movement path preview. Occluded highlights ghost through walls, the preview threads through allies, and the renderer is de-duplicated across both Stonecutter branches
- Threat overlay: press Y to see every tile enemies can reach and strike this turn, drawn under your highlights and hidden while Blinded
- Level select: clickable cards with hover feedback, clickable progress dots, Enter-to-play, tab tooltips, ??? on undiscovered locked biomes, a focused card that swells, a scrolling tab bar, and Up/Down to cycle dimensions

Economy and event systems

- The Nether has its own trader: a piglin bartering station replaces the wandering trader. Offer gold ingots with plus and minus buttons, and the more you offer the better your odds. A failed barter still costs the gold and returns junk like gravel, soul sand, or crying obsidian
- Five piglin barter categories, hinted by the piglin but never showing the odds: Warmonger (combat gear), Hoarder (gems like diamonds, emeralds, and iron, never gold), Flesh Dealer (food, potions, and brewing items), Relic Trader (rare curiosities, fire charges, blaze rods, and supported addon curios), and Beast Tamer (Nether mob allies)
- Overpaying past the hidden threshold can earn a bonus second item
- Each player in co-op makes their own offer and gets their own outcome
- Addon support: mods and datapacks can add new barter categories and contribute items to existing pools through the Craftics API or data/<namespace>/craftics/barter/*.json files

Progression and reward scaling

- Enemy count now ramps up within a biome instead of being driven by how deep you are in the campaign. Every biome starts at 3 enemies on its first level and adds 1 per level, then resets to 3 at the start of the next biome. Later biomes still get harder through enemy stats, not by piling on bodies from the first level
- Level completion rewards now scale with how many enemies you fought, so an early few-enemy level pays less loot and emeralds than a later full one
- Once you have beaten a biome's boss, every level in that biome spawns the biome's peak enemy count, so replays stay full strength (and pay full rewards)

Follow-up stability fixes

- Speed and Haste buffs gained mid-turn now take effect the same turn instead of the next one, so a Speed boost from an instrument, potion, goat horn, or pottery sherd immediately adds the extra movement or action points you would expect

0.2.3

Bug fixes

- Flint and steel now needs an adjacent target with line of sight instead of hitting any enemy anywhere on the map
- Taming now needs an adjacent target with line of sight instead of taming from anywhere
- Thrown items (pufferfish, snowball, egg, water throwables) now use a 4 tile range with line of sight like bows
- Poison and other damage over time can no longer kill a Creaking that still has its heart, and the Creaking heart no longer lingers as an indestructible block across runs
- Broodmother eggs now only spawn on safe tiles the player can reach, fixing the soft lock when an egg spawned behind a wall
- Unknown loot item ids in biome configs are now skipped instead of being handed out as air rewards
- Tamed pets no longer fail to follow to the next scene when the tiles around the player start are full
- Pets no longer spawn inside cobwebs
- A fallen pet can no longer rejoin the player alive
- Water bucket now returns an empty bucket like milk, places a real water tile, and only pours onto open floor
- Lava bucket now returns an empty bucket, places a real visible lava tile, and only pours onto open floor
- Powder snow now sits on the ground instead of floating a block above it, and can be scooped back up with an empty bucket
- Fishing is blocked when no enemies are present, ending the safe room fishing exploit
- Melee weapons can hit and highlight all eight surrounding tiles including diagonals, and this now works on spiders and magma cubes too
- Skeletons with no clear shot now fire anyway instead of walking in place
- Slimes and magma cubes no longer move so their 2x2 body overlaps the player, fixing the clip into the mob
- Attacks that cover more than one tile now flash those tiles during the attack so it is clear where an area attack is landing, both for instruments and for base-game area weapons, lit amber for damage and cyan for support
- Weakness now reduces physical (melee) damage to 0, so a weakened bare fist deals nothing
- Splash potions now also apply their debuffs to you when you are caught in the blast (for example throwing a Weakness potion at your own feet), matching vanilla; previously only positive effects landed on yourself
- Enemy on-hit effects (knockback, weapon debuffs, thorns, and the rest) no longer apply when you fully dodge, block, or negate an attack
- "0x Air" no longer appears in victory rewards; empty and unknown loot entries are filtered
- Bosses can no longer permanently wall off the arena or delete its floor: Chorus Mind plants, Rockbreaker boulders, and Void Herald floor-collapse now decay back to normal, fixing an unrecoverable soft lock
- The Broodmother no longer freezes in place forever if it loses all its egg sacs in phase one; it returns to hunting instead of idling
- The Pale Garden level now loads its dedicated arena from the packaged build instead of falling back to a generic one (the sub-biome schematic lookup treated "forest/pale_garden" as a folder and missed the file)
- Sandstorm Pharaoh, Tidecaller, and Void Walker bosses now use a fresh AI instance each fight, so leftover state (planted mines, the flood, the phase) no longer carries into later fights or between co-op parties
- Void Walker mirror images shoved into a wall, the void, or lava no longer pay out loot, XP, or kill-streak credit
- A lethal Sandstorm Pharaoh sand mine now actually downs you instead of reviving you at 1 health
- Lit coal golems now use the correct ignited texture; Craftics flips Golem Overhaul's own lit state since the vanilla flame overlay never shows on fire-immune mobs
- Mounts and party pets now carry over to the next level after a Trial Chamber or Ambush victory instead of being sent home
- Hardened the return-to-battle transition after any interactive event (Shrine, Wounded Traveler, Treasure Vault, Dig Site, Enchanter, Wandering Trader): each carried-over golem/mount now respawns independently so one failure can't drop the whole party, the transition is wrapped so an error can no longer silently lose your pets (they fall back to the hub instead), and it no longer runs the new battle's first tick prematurely
- Dying in battle now loses your mounts and party pets like any other dropped items
- The /craftics skip_level and /craftics kill_enemies debug commands no longer kill your own golems and mount along with the enemies, so a skipped fight's party correctly carries over to the next level and returns to the hub

Combat balance

- Fights end automatically when only passive or unprovoked neutral mobs remain and no kills happen for four rounds, starting after the first five rounds, preventing infinite farming
- Revenant Shield Bash now has a cooldown, so the boss alternates the no-damage shove with real melee hits instead of only ever pushing you away
- Void Walker mirror images are now weak decoys (8 health, 3 attack) that take double damage, instead of full-strength copies of the boss
- Sandstorm Pharaoh's Plant Mine is now a real, lightly telegraphed buried trap that deals 6 damage and a one-turn stun on contact, instead of doing nothing and disabling itself after four casts
- Wither Boss's phase-two charge fire trail is now short-lived and never covers the boss's own tile, so a melee fighter can always reach it without being walled out by fire
- Tidecaller's phase-two arena flood now happens exactly once and spares the tile you are standing on

Mod compatibility

- Content-accessibility pass: audited every addon item, ally, weapon, and armor to confirm it can be obtained somewhere (loot, drops, traders, events, or hub crafting), since the personal world is a void with no wild spawns or world-gen. Three real gaps were closed (below); copper gear, basic weapons, totems, artifacts, and the golems were already reachable via their craft recipes + grantable materials
- Basic Weapons support: the weaponsmith now stocks all six weapon types (was only ever offering daggers and clubs) and the gold material tier is now sold (it previously had no acquisition channel at all), so every Basic Weapons type and tier is obtainable
- Instruments support: the 15 Genshin/Even More instruments are now sold by the Curiosity Dealer, since their craft materials are unavailable in the void hub
- Vanilla mounts: horse, donkey, mule, skeleton horse, zombie horse, camel, and llama spawn eggs now drop from combat, so these mount allies (which can't spawn in the void hub) can finally be brought to your island, tamed, and recruited
- Golem Overhaul support: all nine golems are now built combat allies with distinct roles. Terracotta golem is a taunt tank that forces enemies to attack it, hay golem heals the lowest health nearby ally each round, candle golem fights at range and burns its target, kelp golem soaks its target on hit. Coal golem can be lit with flint and steel into a fast heavy hitter that burns on hit and dies after one attack. Honey golem holds station and summons a bee ally each round. Slime golem splits into two small slimes when it dies. Barrel golem rolls bonus loot from kills it lands. Each golem is healed in combat by the material it is built from. The golem behaviors plug into the core through generic ally hooks, so nothing changes when the mod is absent
- Golem Overhaul: the Netherite golem is a rideable combat mount. Riding it: movement locks to golem pace (1 + speed bonuses); it blocks a 1x3 wall footprint shown in steel-blue highlights that reorient as you turn; you are immune to lava, water, and powder-snow tile effects; attacks erupt a lava line toward the target that burns anything it covers. Mount Ability (M, 3 AP) summons two pre-lit coal golems
- Golem Overhaul: honey golem bees now charge the nearest enemy on the round they are summoned, instead of drifting toward a distant low-health straggler
- Golem Overhaul: golems join battle by adding them to your party with Shift+Right-Click, same as vanilla golems; no automatic hub-yard scan
- MoreTotems support: the mod's seven totems of undying now auto-revive you in combat at 50% health, each with its own Craftics effect (explosion, mark all enemies, teleport to safety, set enemies ablaze plus Fire Resistance, summon bees or zombies, or blind all enemies), with rewritten tooltips, drop as rare rewards from bosses, trial chambers, vaults, and the Shrine of Fortune, and can be bought from the curiosity trader at high tiers
- Multi Arrow Effects support: combined arrows now apply every recognized effect in combat instead of only the first, so a multi-effect mixed arrow lands all its debuffs from one shot
- Basic Weapons support: all six new weapon types work in combat with fitting affinities, action-point costs, reach, and unique effects (dual-wield daggers strike twice, clubs slow, hammers knock back and stun, glaives cleave, spears and quarterstaves reach two tiles), each with its own attack animation, Craftics tooltips, the mod's Might enchantment boosting blunt-weapon damage and stun, stock in the weaponsmith trader, and Might offered by the enchanter
- Genshin Instruments and Even More Instruments support: the fifteen held instruments become Special-affinity combat performances played by holding the instrument and clicking. Attack instruments deal Special damage plus a debuff across a player-centered shape (ring, cone, star, diagonals, scatter, expanding pulse, full burst, or the whole arena), while support instruments buff you, your allies, and any teammates standing in their shape. Directional shapes like the cone fan out toward the tile you click, and hovering a tile while holding an instrument previews exactly where the performance will land. Four signature instruments go further with knockback, a flat heal, an arena-wide heal, or a debuff cleanse. Each instrument plays its own note sounds, with the music-note particles bursting on the actual tiles being hit so the shape reads clearly, and the mods' tooltips are replaced with Craftics combat tooltips showing the shape, the damage or effect, and the action-point cost

Music

- Music changes based on the biome, boss fight, or event you are in
- Each biome has its own battle track and a separate track for its boss level
- Wandering trader, trial chamber, ambush, treasure vault, and wounded traveler events each have their own track
- Trader music depends on the trader type
- Tracks loop and cross-fade between transitions
- A "Now Playing" popup in the bottom-left shows the song name and source when a track starts
- Vanilla Minecraft music no longer plays
- Craftics music uses the Jukebox and Note Blocks volume slider
- Music is synced across co-op parties and stops when a run ends

0.2.2

Events and dialogue

- Events now play out in third person: your party walks up to NPCs together with the camera following each player
- New dialogue system introduces NPCs with unique greetings, letter-by-letter text with voice acting, and a click to skip
- Post-transaction dialogue lets you reopen shops or move on
- All dialogue is data-driven JSON and reusable for future NPCs
- Shrine of Fortune uses the dialogue system: walk up via an approach path, choose offerings through dialogue buttons, get narrator results you click to dismiss
- Shrine area now has an approach walkway and sealed barriers
- New NOTHING reward band on the shrine: 25% bust on small, 15% on medium, 8% on large offerings
- Insufficient emeralds now re-offer the shrine menu with a narrator note instead of a chat error
- Wounded Traveler: villager waits at path's end, each player sees foods sorted by quality with a walk-away option, narrator confirms the gift before continuing
- Wandering Enchanter: pick weapon enchant or armor enhance, then choose from filtered items. No longer offers no-op enchants on weak tools or corals
- Treasure Vault: walk in and ring the lodestone to open for loot or walk away to continue
- Ambush encounters: party votes Take it or Leave it on shiny items. Take majority rolls 50/50 between rare reward and combat. Leave majority walks past. Tie triggers combat
- Removed the legacy event-room button screen. All events now use the same dialogue UI
- Dig Site is now a push-your-luck minigame: brush successfully to raise your pull chance 5% up to 100%, or break the relic and lose. Sweet spot is around 5-6 brushes
- Removed Crafting Station event (redistributed to other events automatically)
- Trial Chambers are now full party votes: Enter majority takes the trial, Pass majority skips to the next level. Disconnects count as Pass
- Ominous Trial loot now drops heavily-enchanted hero pieces (weapon or armor) with 3-5 vanilla-max enchantments plus supply consumables
- Trial intros show "Waiting for party..." overlay instead of dropping to an empty arena
- Addon events can declare optional narrator intros via the EventEntry introLines field
- Abandoned Campsite now opens with narrator dialogue
- Fixed soft-lock when trial intro leader disconnects between dismiss and Accept screen
- Addon probabilities now scale with pity-timer like built-in events
- Boss fights open with narrator dialogue: all 18 vanilla biomes have unique boss flavor lines. Mod authors can register per-biome intros at craftics:boss_intro_&lt;biomeId&gt;

Arena creation

- Arenas can now be non-rectangular: drop craftics:arena_corner blocks around any shape for a polygon outline (legacy DIAMOND/EMERALD pairs still work). The polygon propagates through pathfinding, AI, VFX, and occupancy
- New /craftics build_arena <shape> [radius] command terraforms a polygon and drops corner markers. Presets: square, diamond, octagon, hexagon, plus, cross, l_shape, t_shape. Default radius 8 (2-64 supported)

Combat

- Co-op feeding: holding a food item and clicking an adjacent party member feeds them instead of healing yourself
- /home is now blocked during combat for non-ops (prevents trivializing boss fights via hub runs)
- Non-boss base damage is capped per biome pair: 3 + (biomeOrdinal / 2), preventing early-game one-shots
- Mobs with Sharpness have enchant level subtracted from base damage to prevent double-stacking

Enemy AI

- Fixed ghost rider mobs left behind by stack enemies (Zombie Stack, Skeleton Horseman, Slime Tower)
- Fixed melee mobs going idle when standing next to you
- Enemy melee in MP now targets the actual closest player instead of the host, and damage routes correctly
- Mobs now turn to face the player they're actually attacking
- Post-hit effects (bleed, burn, knockback, etc.) resolve against the correct target in MP
- Desert boss (Sandstorm Pharaoh) no longer carries Sharpness II on its golden sword. With the base-attack tuning + Fire Aspect already on it, Sharpness on top was stacking into near-one-shots in an early biome. Replaced with Knockback I

Loot

- Bosses now roll each equipment slot at 50% instead of guaranteed dropping their full set
- Per-mob loot (equipment drops, mob heads, generic mob loot tables, goat horns) now goes to the player who killed that mob instead of being handed to every party member; arena/level-completion bonuses still go to everyone

Inventory and economy

- Emeralds awarded by post-battle loot, traveler events, vault events, and shrine jackpots now go straight to your virtual emerald balance instead of taking up inventory slots; trader event emerald grants are unchanged because the trader needs physical emeralds to spend

Multiplayer fixes

- Fixed event routing in MP: non-leader choices now reach the host's CombatManager instead of their own inactive instance
- Fixed post-event transitions: turn order and leader mapping stay anchored on the original host
- Fixed Move item being stripped from non-leaders every server tick
- Fixed Vitality and Host trim HP bonuses only applying to the host
- Fixed every teammate's avatar walking in place instead of only the turn-holder
- Added server-driven position broadcast for all combat moves and cinematic walkers
- Fixed damage flash retriggering on every turn rotation (now reads per-UUID HP)
- Fixed turn-end clicks being ignored after events
- Removed auto-teleport that snapped survivors to a downed teammate's corpse
- Status effects are now per-player: each hit lands on the right player and ticks on their turn
- Fixed creeper blasts checking distance only to the host's tile
- HUD HP bar reads per-UUID data so dead players show 0 instead of next turn-holder's HP
- Fixed host animations dying after void deaths and respawns
- Fixed knockback reading the wrong player's tile and teleporting hosts
- Fixed ranged enemy attacks (skeletons, pillagers, etc.) routing damage to the wrong player in MP
- Level Select block now reads the leader's progression data instead of each player's own
- Per-turn status effects now tick for every party member, not just the last actor
- Boss area attacks now hit every party member in the blast radius
- Boss single-target abilities now land on the actual targeted player
- Boss push/pull now displaces the correct player from their own tile
- Defensive bonuses now follow the player being hit (Ocean's Blessing is per-UUID)
- Riptide, knockback, wind-charge, and blink teleports are now broadcast to all observers
- Fixed teammates losing turns when a player disconnects mid-round
- Fixed survivors getting a free extra action when a teammate dies
- Party HUD shows correct HP and turn order the instant combat starts
- AP and Speed pips now show full when it isn't your turn
- "Hidden" stealth indicator now shows per-player based on their own tile
- Fixed stray damage flash on re-entering combat at different HP

0.2.1

Combat

- Most Nether, End, and many Overworld enemies now resist unarmed Physical attacks, taking half damage from fists and the leather Brawler set, while soft and low-tier mobs (zombies, slimes, silverfish, phantoms, and the like) stay fist-vulnerable
- Fixed enemy weapon Sharpness being counted twice (added to attack power and again on hit), which inflated the damage of every sharpened enemy
- The Revenant (Plains boss) no longer one-shots unarmored players, it now wields a stone sword with Sharpness I and lands a 5 damage hit plus a 3t stack of bleed
- Fixed Weakness doing nothing to the player, it now actually lowers your outgoing attack damage by 2 per level while active, as the tooltip says
- Fixed spawn-egg allies (including hostile ones) being sent back to your hub after combat, summoned mobs now fight for the current battle only and are not kept
- Fixed a batch of status effects that did nothing to the player despite their tooltips: Haste now grants AP, Mining Fatigue removes AP (now applied on every turn), Blindness and Darkness shrink your weapon range (down to a minimum of 1), Levitation reduces movement, Luck adds crit chance, Absorption grants extra hearts, Slow Falling negates knockback, and Invisibility makes enemies skip you
- Fixed Golden Apples, Enchanted Golden Apples, and the Totem of Undying granting absorption/resistance/regeneration that never registered in combat, their buffs now apply properly
- Darkness now shows a black screen vignette like other status effects, and the Hollow King's Lights Out ability actually inflicts Darkness now

New status: Marked

- New Marked status: marked targets take 2x damage from all attacks (1.5x for bosses), including damage over time
- Use a Spyglass (2 AP) on any enemy in the arena to Mark it for the rest of this turn and the next, marked enemies glow and show their stats; only one enemy can be marked at a time

0.2.0

Party and allies

- Shift + Right Click a passive or neutral mob on your island with an empty hand to bring it into your battle party, click again to remove it, the action bar reports party status like "Active In Party (1/1)"
- Party capacity is 1 plus your Pet affinity level, and always-hostile mobs (zombies, skeletons, creepers, and friends) can never be recruited
- Spawn eggs are now rare loot drops, throw one at a tile in battle to summon that mob as an ally for 2 AP within 5 tiles, it fights alongside you for the current battle and counts toward your party cap
- Leads command pets, hold a lead to enter command mode, click an ally to select it, then click an adjacent enemy to order an attack or any walkable tile to order a move, each command costs 2 AP and does not use up the ally's own turn
- Bee allies inflict poison, any bee that lands a sting applies 3 turns of poison and takes recoil damage equal to a quarter of its max HP, mirroring vanilla bee behavior
- Jukebox buff, placing a jukebox in battle plays music across the arena and grants every ally +3 speed for the rest of that battle

Hybrid classes

- Two distinct armor materials worn in an exact 2/2 split form a hybrid class with its own name and combat effect, 15 vanilla material pairs are registered (leather and chainmail, leather and iron, and so on)
- Fixed hybrid classes triggering on a 3/1 (or 1/3) split, the detector now requires exactly two of each of two distinct materials across all four armor slots and rejects any other distribution

Movement and the Move item

- New custom Move item replaces the feather, a single-stack uncommon item shown as green "Move"
- The Move item is force-locked to a single hotbar slot during combat, MoveSlotManager re-seats it every tick and restocks it if it is dropped or moved
- New keybinds rotate the locked Move slot left or right so you can put it where you want without losing the lock
- Wind Charge works as a self-movement special, target an adjacent empty tile to launch yourself up to 2 tiles the opposite way and arm a 1.5x momentum bonus on your next attack, or target an enemy to deal 1 damage and knock it back up to 3 tiles

Combat items and weapons

- Rocket Crossbow AOE, loading a firework rocket in the off hand turns crossbow shots into a 3x3 explosive blast that deals half damage around the target, with Multishot it fires two extra rockets along the perpendicular diagonals, each with its own 3x3 blast
- Attack AOE preview, an on-grid preview now shows which tiles a weapon will hit (amber for damage, blue for effect-only) for maces, crossbows, swords, bows, and coral fans, driven by a shared AoeShapes geometry library covering slam, plus, sweeping edge, cone, ring, line, and pierce shapes

Status effect rework

- Wither now scales up as it wears off, base damage of (1 + level + Special affinity) is multiplied each turn by how far the curse has progressed toward its end, so the final ticks hit hardest, peak duration is tracked so re-applying does not reset the ramp
- Fire is now flat damage from the source for its whole duration, (1 + level + Special affinity) per turn, blocked entirely by Fire Resistance
- Poison damage is (2 x level) + remaining turns + Special affinity per turn, minimum 1

Battlefield obstacles

- Fire spreads to flammable obstacles, each turn a burning tile ignites flammable orthogonal neighbors for 3 turns, flammables include tall grass and fern, cactus, plus logs, planks, leaves, wool, wooden fences, saplings, flowers, carpets, hay, bookshelves, scaffolding, bamboo, dried kelp, target blocks, and cobwebs
- Any non-functional full-cube block can be placed on the ground in battle as a temporary wall for 4 turns, block-entity blocks (chests, furnaces, jukeboxes, and the like), half-blocks, tall or hinged blocks, and an explicit blocklist (TNT, slime, honey, magma, beacon, spawner, and more) are excluded
- Tall grass can now be broken from diagonal tiles too, breaking uses Chebyshev distance so all 8 surrounding tiles count as adjacent, costs 1 AP, and clears both halves

Stacked enemies

- New stacked enemy variants where a rider sits on a mount, killing the lower layer drops the upper layer to keep fighting
- Zombie Stack, an adult zombie carrying a baby zombie, kill the adult and the faster baby (speed 3) takes over
- Skeleton Horseman and Zombie Horseman, a skeleton or zombie riding its matching undead horse, the mount is the fast frontline and the rider drops as the final layer
- Piglin Cavalry, a piglin riding a hoglin, Nether only and the toughest stack at 18 HP on the mount
- Slime Tower, three slimes stacked into a tower whose attack scales with the layers left (8, then 6, then 4), the final slime still splits on death into mini slimes that deal only 1 damage each
- Blaze Tower, three immobile blazes stacked into a stationary turret that fires fireballs at range 3

Wither boss rework

- The Wither boss fight is rebuilt as a two-phase encounter at 65 HP, 8 ATK, 5 DEF, range 5, on a 2x2 footprint
- Phase 1, a barrage of 3 tougher skulls every 2 turns (6 HP, 7 damage, up to 6 active), a passive radius-3 decay aura dealing 2 damage a turn, summoning 2 wither skeletons every 4 turns (up to 4 alive), and a 4-tile charge when the player keeps distance
- Phase 2 below half HP, an enraging explosion deals 8 damage in a radius-3 burst, then skull volleys grow to 5 (up to 10 active), summons to 3 (up to 6 alive), the decay aura expands to radius 4, charges leave a decay trail, and the boss becomes immune to ranged attacks

UI and loot management

- Player hover stats, hovering a party player on the grid opens an inspect panel with HP bar, ATK, AC, SPD, AP, and active effects, tinted blue to set it apart from enemy and ally panels
- Toggle button for stats in the inventory, a stats panel on the right of the inventory screen shows level, unspent points, all six stats with base and spent breakdowns, emeralds, and the damage-type affinity panel, toggled on and off
- Respec affinities, a dedicated screen lets you allocate unspent affinity points for free or refund spent points at a cost of 1 XP level each, sent to the server as per-affinity deltas
- Full inventory loot management, the post-victory loot screen is now a chest-style GUI with your inventory below, a Take All button, and a Continue button that warns before leaving items behind
- Trinkets are now lost on death like the rest of your inventory, unless keepInventory is on or a Recovery Compass triggers, in which case accessories from the Accessories slots are saved alongside your inventory and restored on respawn
- F1 hides all combat UI, the combat HUD and tile overlays now respect the vanilla hud-hidden flag and skip rendering entirely
- More in-game hints covering newer features (moving without a feather, ending your turn, healing at low HP, first combat, and the hub level-select arrow)
- Fixed the Trial Chamber arena grid merging into itself, it now renders as a crisp, properly aligned grid like every other arena

Goat horn overhaul

- All 8 horn variants now actually work in combat (Admire, Yearn, and Call previously did nothing or fired only once with no duration tracking)
- Horns from any source work: goat-kill drops, raid loot, structure chests, trader trades, and /give all identify correctly via the vanilla INSTRUMENT data component instead of relying on a custom name
- Each horn now plays its own sound instead of a random horn sound
- Re-using a buff horn refreshes duration to max(remaining, fresh) instead of clobbering the existing effect
- Weakness debuff lifecycle: enemies hit by Admire now lose the -2 ATK after the listed turn count, ticked alongside the existing defense-penalty system
- Stale "Taunt all enemies" tooltip removed
- Pre-overhaul horns with the legacy custom-name tag continue to work (backward-compatible)
- Goat horns are now Special-class items: the player's Special affinity scales horn duration and amplifier the same way it scales potions
- Re-using a horn now stacks the amplifier (capped) and refreshes duration
- All four version shards (1.21.1, 1.21.3, 1.21.4, 1.21.5) compile and run; vanilla INSTRUMENT API drift in 1.21.5 isolated to a single helper class

Banner overhaul

- Banners now place a real, color-correct banner block on the target tile (previously the use was silent: no block, no visible AOE, no way to tell it had worked)
- Each turn, the +DEF aura is outlined by sparse happy-villager particles on every tile within manhattan distance 2 of any planted banner
- Allies inside the banner aura now actually receive the DEF bonus
- New "Banner aura reduced damage by N%" message when incoming damage to the player is mitigated by a banner zone
- Banners are now Special-class items: the +2 DEF base scales with the player's Special affinity at placement time and is frozen into the tile-effect entry, so later affinity gains don't retroactively buff old banners
- Overlapping banners take the max DEF of the strongest single banner instead of stacking (no infinite +DEF from carpet-bombing)
- Banner color is preserved through the tile-effect lifecycle so all 16 vanilla colors stay visually distinct
- 16-banner enumeration is now explicit (BannerEffects helper) instead of `item.toString().contains("banner")` string sniffing
- Special-class scaling unified into a SpecialAffinity helper used by potions, banners, and goat horns
- All four version shards compile and run

0.1.4

Crafting Station event

- New non-combat Crafting Station event with build/teleport, bell-based per-player exit, and pending-player tracking
- Disconnect cleanup paths and a fallback to the central lobby when no safe hub landing is found
- Added to event roll probabilities alongside Trader, with trader finalization tidied alongside

VFX framework

- Server-driven VFX core: PhaseScheduler, Vfx, VfxPrimitive and VfxAnchorResolver, with a sealed VfxAnchor interface
- Client VfxClientPayload codec and VfxClientDispatcher handle screen shake, colored flashes, hit-pause, floating text, and vignette primitives
- VfxBlockTracker manages falling-block entities and marks landings as VFX obstacles, GridArena tracks and clears them on combat end
- New combat hooks: hit and ricochet descriptors, ACTION_MINE so a pickaxe gesture mines VFX obstacles for 1 AP, riptide dash interpolation animation with particles and dropped-trident owner tracking
- HitPauseState freezes client animations while hit-pause is active, CombatVisualEffects honors hit-pause in tick
- Config flags vfxBlockEntitiesEnabled, hitPauseEnabled, and vfxIntensity
- JUnit 5 harness added for pure-logic VFX tests

Mob animation system

- AnimState enum, MobAnimations helper, and CrafticsAnimComponent integration ferry pose state from server to client
- Client mixins BipedAnimMixin, LivingEntityRendererAnimMixin, and LivingEntityRenderStateAnimMixin apply pose overrides through CrafticsAnimHolder in setAngles
- Server sets WINDUP on attack start, mob AnimState set on hit, per-tick decay in CombatManager
- Client shows hurt flash by setting hurtTime on entities from damage payloads

Stealth tiles

- New StealthTiles utility applies vanilla INVISIBILITY to occupants and provides isConcealedFrom gating
- CombatManager applies stealth visuals each tick and gates AI target selection so distant hunters cannot see concealed targets
- CombatEntity gained a frozen flag and move-speed handling, CreakingAI respects it for gaze-freeze behavior
- Tall grass and fern can be broken with a held item for 1 AP, removes both halves, plays particles and sound, and clears the stealth tile
- Creaking heart death now removes the in-world heart block and kills the linked Creaking

Copper Age and Pale Garden backport compatibility

- CopperAgeCompat and PaleGardenBackportCompat registered in CrafticsMod.init() with deferred registration on server SERVER_STARTING and client CLIENT_STARTED
- Marksman ricochet for ranged hits when wearing the full copper set, with chance and damage multiplier constants centralized in CopperAgeCompat
- PlayerCombatStats.hasCopperSet() for set detection
- Copper tools register in their natural affinity lanes via shared Abilities handlers for sword and axe, pickaxe skipped as a combat weapon
- Copper armor set description registered describing ricochet behavior, client tooltips added for copper tools and armor
- DamageTypePanel and tooltips updated to recognize copper set, show Marksman set bonus and Ranged Power affinity
- PaleGarden helpers used for creaking entity and heart detection and block placement

Damage and affinity refactor

- DamageType exposes DAMAGE_PER_AFFINITY_POINT and separates getTotalAffinityPoints from getTotalBonus
- Mob-head helper renamed to getMobHeadAffinityPoints, new damage-returning getMobHeadBonus added
- DamageTypePanel.computeBonus now returns affinity points instead of raw damage, accounting for trims, partial sets, mob-head points, and level-up affinity points

Combat fixes and polish

- Totem of Undying handling refactored into safer helper methods
- Stealth checks are now world-aware
- Client keybind conflict resolution at startup
- Hover tile tooltips for obstacles and hazards aligned with the enemy roster

0.1.3

Client fixes for all versions

- Leaving a world mid-combat no longer locks the camera into isometric view on the title screen or in every subsequent world, disconnect now resets inCombat, camera pan, focus, arena origin, trader state, and all combat stats
- Ghost hit boxes from the previous fight no longer render, tile sets and teammate hovers are cleared on disconnect too
- Guide book unlock state resets to defaults on disconnect instead of leaking bestiary unlocks from the previous world into the next
- Combat client packet receivers now dispatch to the render thread via context.client().execute, EnterCombat ExitCombat CombatSync CombatEvent VictoryChoice PlayerStats AddonBonus LevelUp TraderOffer Scoreboard Achievement and GuideBookSync were all mutating MinecraftClient state from the netty IO thread

Multiplayer fixes for all versions

- Party combat no longer deadlocks when the current turn holder disconnects, removePartyMember now reassigns this.player via switchToTurnPlayer so remaining party members can keep acting

Level select table

- Both visual halves are now clickable from every angle, the phantom half is backed by a new invisible LevelSelectGhostBlock that delegates onUse to the real block entity
- Placing an interactable block next to the level select no longer opens the level select screen when the neighbor is clicked, the overly broad UseBlockCallback that scanned all four horizontal neighbors was removed
- Breaking either half cleanly removes both and drops exactly one level select item
- Breaking the phantom half now shows the normal block breaking crack animation, ghost block renders a slab shaped cuboid with a transparent texture on the cutout render layer so the overlay has faces to draw on
- Placement now fails cleanly if the phantom position is already occupied

Bestiary

- Added entries for Zombie Villager, Cave Spider, Silverfish, Ravager, Piglin Brute, Endermite, and Llama which all spawn in biome data but had no guide book coverage
- Filled in real stats for Evoker, Phantom, Zombified Piglin, Warden, and Ender Dragon, numbers come from biomes json and the matching EnemyAI classes
- Bestiary entry count went from 53 to 60

Packaging

- owo-sentinel is no longer bundled in the jar, Modrinth auto rejects jars that include it
- owo-lib promoted from suggests to depends in fabric.mod.json so Fabric Loader itself surfaces the missing lib error that sentinel used to handle

0.1.2

1.21.5 support

- Mod now compiles and runs on Minecraft 1.21.5 alongside 1.21.1, 1.21.3, and 1.21.4
- Migrated PersistentState to PersistentStateType with codec-based registration
- NBT getters updated for the new Optional returns across all component and save-data classes
- PlayerInventory.selectedSlot and armor field access replaced with getSelectedSlot / EquipmentSlot iteration
- Entity prevX/Y/Z renamed to lastX/Y/Z in combat move tweening
- DyedColorComponent, SwordItem, ClickEvent, HoverEvent, TameableEntity owner lookups all ported
- Howl Sherd now plays warden roar instead of the removed wolf howl sound
- PlayerEntityMixin dropItem injection split for the new two-argument signature
- Client particle call sites use addParticleClient
- TileOverlayRenderer rewritten to use VertexConsumer and RenderLayer.getDebugQuads since the old immediate-mode render calls were removed
- Input.movementVector accessor replaces the removed movementForward / movementSideways fields
- WorldEdit bumped to 7.3.12 for the 1.21.5 dev runtime

1.21.5 only features

- Cow, pig, and chicken spawn with the new Spring to Life variants based on biome climate
- Warm biomes are desert, jungle, nether_wastes, basalt_deltas, and crimson_forest
- Cold biomes are snowy, mountain, and deep_dark
- Cow and pig removed from plains, chicken removed from forest
- Cow, pig, and chicken added to desert, jungle, snowy, and mountain so the new variants are actually visible
- Tamed animals keep their specific variant when returning to the hub
- Blue egg and brown egg are throwable combat items that deal 3 damage instead of 1
- Blue and brown egg tooltips added to the combat UI
- Cactus obstacles in desert arenas have a 50 percent chance to grow a cactus flower on top
- River arenas always spawn at least one firefly bush on the grass border around the grid

Fixes for all versions

- Players no longer spawn in the void or water after dying in combat, the hub teleport now scans for solid ground
- Tamed animals returning to the hub no longer spawn below the base, same solid-ground scan applied
- Tamed animals are no longer frozen at the hub, combat arena flags NoAI NoGravity Invulnerable Silent and the craftics_arena tag are stripped on restore
- Tipped arrows dropped from enemies or biome loot now actually apply their potion effect, bare tipped arrow stacks get a random vanilla variant assigned at delivery time
- Recipes for chainmail armor, guide book, and level select block parse on 1.21.1 again, rewritten from the string ingredient shorthand to the object form


