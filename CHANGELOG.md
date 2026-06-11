Changelog

Unreleased

Battle HUD fixes

- Enemy HP numbers are now genuinely hover-only: the always-visible top-right roster keeps its slim HP bars but no longer prints hp/max for every damaged enemy (the hover inspect panel is where the exact numbers live), and the panel got narrower with the text gone
- The act-order strip's gold "acting now" highlight appears for every enemy and ally action, not just attacks — walks, teleports, ceiling hops and drops all announce themselves now, and the camera follows the mover

Nether and End boss fixes

- Chorus Mind's Resonance Cascade actually hits now — the old resolve struck the boss's own tile instead of the warned plant-adjacent tiles. Its phase-two plant spread grows real chorus obstacles (it used to grow an invisible bookkeeping list), the boss blinks beside plants instead of standing on them, and its abilities aim from where it lands rather than where it stood before teleporting
- Shulker Architect's Bullet Storm is a real telegraphed volley — the advertised bullet count used to be discarded in favor of one untelegraphed blast — and its Teleport Link no longer drops the boss on top of its own turret
- The Void Herald's phase-two platform collapses were silently cancelled whenever another telegraph fired the same turn; they resolve reliably now, and its blink assault respects its 2x2 body
- The Molten King can no longer teleport-erupt directly onto your tile (or clip its 4x4 body into walls) when the arena is crowded, and a blocked leap no longer wastes the ability's cooldown
- Across seven bosses, abilities no longer burn their cooldown when they fail to find room to fire — a crowded arena used to lock summons, charges, rifts and collapses out for the full cooldown with nothing to show for it
- The Bastion Brute's gore charge stops at deep water instead of ending its run somewhere it can't stand; the Wailing Revenant throws a weak fireball instead of idling when everything is on cooldown; the Wither's dead decay-aura cooldown bookkeeping is gone (the aura is genuinely passive)
- Phantoms each build their own dive-speed streak — all phantoms on the server used to share one — and no longer park themselves on top of you or your pets while circling

Nether AI improvements

- Zombified piglin pack aggro no longer outlives the fight: the old flag was global and never reset, so hitting one zombified piglin made every zombified piglin in every later fight on the server spawn already hostile. The pack now riles per fight — hit one and all its packmates in that arena turn on you, including when the victim died to the first hit — and your own allied piglins no longer feed the enemy pack's damage bonus
- Magma cubes actually bounce now: a multi-tile bounce used to lay its fire trail and then silently never move the cube (only 1-tile hops worked). Both bounce types move correctly and leave the burning trail, and the fire only lands on plain floor that can burn. The same dispatcher fix restores follow-up moves that bosses queued behind a resolving telegraph
- Wither skeletons patrol independently — they all shared one patrol heading, so the whole map marched in lockstep and one reversal turned every skeleton around
- Hoglins gained the ground stomp their description always promised (shared with the ravager: surrounded by two or more attackers, they slam everything around them) and no longer charge through hazards
- Blazes time their barrage around your pets too, backing off a wolf in their face while keeping you in fireball range; ghasts panic away from any nearby threat and find the around-the-corner drift instead of freezing
- Endermites refuse to blink onto water, like their enderman cousins

Boss improvements

- Fixed a state leak affecting nearly every boss: one shared AI object served all fights, so a boss killed in phase two left the next boss of its kind starting in phase two with stale cooldowns. The Broodmother's nest cycle and egg sacs leaked between fights, and the Hollow King could start a rematch in permanent darkness. Every boss now gets a fresh brain per fight (previously only three did)
- Phase two is now a moment: a combat-log callout, a "PHASE 2" banner for the whole party, a roar with a particle burst on the boss, and a camera shake with a dark-red screen flash. The boss HP bar keeps the news afterwards — its frame turns gold and a "II" badge appears
- Killing a boss got its payoff: a golden defeat banner, explosion bloom with golden totem rain, a wither-death knell, and a celebratory screen shake and flash. The Molten King splitting into fragments no longer reads as a defeat — only the last fragment gets the fanfare
- Boss intros now name the boss itself in the title card ("The Hollow King") instead of the level, with a heavier sound sting
- Boss attack telegraphs are easier to read: the warned tiles get a crisp pulsing outline around the region and ghost faintly through walls, so a telegraph hidden behind terrain at a low camera angle can still be dodged
- Boss minion summons no longer drop reinforcements straight into lava or fire when safe tiles exist

Overworld AI improvements

Smarter enemies:

- Archers and casters (skeleton, stray, pillager, witch, evoker) now kite away from your pets too, not just from you — a wolf in their face triggers the retreat. Their retreats and firing positions are picked from tiles they can actually walk to this turn, and none of them will back into lava to dodge a sword
- Creepers finally defuse: if everyone leaves the blast radius while the fuse is hissing, the creeper stops, stops glowing, and resumes the chase instead of detonating an empty tile. A creeper about to die blows anyway. The blast check also counts your pets, so it will happily trade itself for your iron golem
- Ravager ground stomp implemented (it was documented but never coded): surrounded by two or more of you, it slams an AoE around its body instead of tusking one target
- Vindicators no longer rook-dash through lava or fire
- Spiders break off to the ceiling to reset their ambush when badly hurt, and stop wasting turns webbing a player who already has a web next to them; cave spiders bite and scuttle back out of reach so the poison does the work
- Husks now benefit from the undead horde bonus like their zombie cousins (they never did); wounded zombie villagers panic — +1 attack and +1 movement below half HP; the horde bonus no longer counts the mover's own old tile or your own undead allies
- Silverfish swarm: hurt one and the whole group speeds up; bee swarms now enrage even when the stung bee was killed in one hit
- Endermen never teleport onto water, and goats lined up with you deliver a true ram — extra damage the longer the run-up
- Polar bears use their full 2x2 bulk for reach (you could previously stand inside their melee range without triggering them) and their maul knocks you back a tile; enraged wolves get +1 damage per packmate already biting the same victim; foxes, ocelots and angry cats all strike and spring back out with leftover movement (the ocelot's reposition was documented but never implemented)
- The witch's self-heal is real now — she used to just walk away and call it healing — and each witch rotates her own brews
- Fixed a class of state-sharing bugs: one AI object serves every mob of a type, but the evoker, enderman, drowned and witch kept per-fight state on it. After your first fight, no evoker ever summoned a vex again; one frenzied enderman made all future endermen frenzy; the first drowned's trident roll decided every drowned's loadout forever. Per-mob state now lives on the mob
- Pillagers were firing at range 4 while their stat block says 3 — they now respect their stats. Llamas likewise honor their registered spit range
- Evokers summon a second vex when first wounded below half HP

Smarter allies:

- Tanks (iron golem, turtle, goat) interpose: when the biggest threat is too far to strike this turn, they plant themselves between it and you instead of sprinting across the arena and leaving you open
- Supports (axolotl, frog, villager) hold the player-adjacent tile farthest from the nearest enemy — your healer-adjacent pets stop standing in the charge lane
- Melee allies stop walking past a kill they could secure: target scoring now favors enemies they can reach this turn and enemies they can finish outright
- Flyers (parrot, bee, allay) dive the weakest enemy they can actually reach and kill this turn before chasing the globally weakest
- Ranged allies (llama, snow golem) pick their kiting tile properly — gain the most distance while keeping the parting shot lined up — instead of hopping two tiles straight back
- Every ally that flees now finds the around-the-corner escape when the straight line away is blocked, and skittish farm animals do the same instead of freezing

0.2.4

Cursor picking ignored entities — at the combat camera's angle, clicking a tall mob's body selected the tile behind it. The ray now tests mob hitboxes first (with wall occlusion, and skipping invisible mobs so stealth isn't leaked).
Turn banner fade was dead code (alpha computed then overwritten) and its timer ran per-frame, so timing varied with FPS. Same FPS-dependence affected the warning-tile and hover pulses.
AP/SPD pips lied: a fixed 3-slot layout meant spending from 5→4→3 AP changed nothing on screen. Now one pip per point with adaptive sizing.
"+N more" enemy collapse double-counted the boss and could duplicate a head in the mini list.
Client/server walkability mismatch: tall grass, ferns, cobwebs and stairs are walkable server-side but the client previews treated them as obstacles.
The client move preview also treated deep (2-block) water as walkable when server-side it is an instant-kill tile, and now refuses it. The hover cursor no longer flickers when the mouse rides a tile boundary.
Headline UX additions:

Combat HUD: a clickable End Turn button (shows the live keybind, pulses when you're out of resources — previously ending a turn required knowing the R key existed), smooth HP bars with damage-ghost drain on every bar, attack AP cost on the mode pill, an "N SPD" cost tag at the cursor when hovering a move tile, an act-order strip of mob portraits during the enemy phase that highlights the unit acting right now, a theme hint on the inspect panel showing what a water, jungle, or cold enemy's hit will inflict, HP numbers only on damaged enemies, and a pulsing red screen edge below 30% HP.
Grid: crisp perimeter outlines on move/attack/AoE regions, a proper hover cursor ring, and a movement path preview (breadcrumbs along the BFS route). Occluded highlights and breadcrumbs now ghost faintly through walls, and the preview threads through allies the way the server allows. The renderer was also de-duplicated so both Stonecutter branches share one quad-building core.
Threat overlay: press Y in combat to see every tile enemies could reach and strike this turn, drawn under your own highlights and hidden while Blinded.
Level select: cards are now clickable (click side card to select, focused card to enter) with hover feedback, clickable progress dots (cleared/next/locked), Enter-to-play, tab tooltips with region progress, "???" on undiscovered locked biomes, a focused card that swells as the carousel slides, a dimension tab bar that scrolls when addon pages overflow it, and Up/Down to cycle dimensions alongside Q/E.


Nether

- The Nether now has its own trader: a piglin bartering station replaces the wandering trader there. Offer gold ingots from your inventory with plus and minus buttons; the more you offer, the better your odds of a successful barter. A failed barter still costs the gold and returns bland junk like gravel, soul sand, or crying obsidian
- Five piglin barter categories, hinted by the piglin but never showing the odds: Warmonger (combat gear), Hoarder (gems like diamonds, emeralds, and iron, never gold), Flesh Dealer (food, potions, and brewing items), Relic Trader (rare curiosities, fire charges, blaze rods, and supported addon curios), and Beast Tamer (Nether mob allies)
- Overpaying past the hidden threshold can earn a bonus second item
- Each player in co-op makes their own offer and gets their own outcome
- Addon support: mods and datapacks can add new barter categories and contribute items to existing pools through the Craftics API or data/<namespace>/craftics/barter/*.json files

Combat balance

- Enemy count now ramps up within a biome instead of being driven by how deep you are in the campaign. Every biome starts at 3 enemies on its first level and adds 1 per level, then resets to 3 at the start of the next biome. Later biomes still get harder through enemy stats, not by piling on bodies from the very first level
- Level completion rewards now scale with how many enemies you fought, so an early few-enemy level pays less loot and emeralds than a later full one
- Once you have beaten a biome's boss, every level in that biome spawns the biome's peak enemy count, so replays stay full strength (and pay full rewards)

Bug fixes

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
- Weakness now reduces physical (melee) damage all the way to 0 — a weakened bare fist deals nothing instead of always chipping 1, so punching is no longer a free weakness-proof attack
- Splash potions now also apply their debuffs to you when you are caught in the blast (for example throwing a Weakness potion at your own feet), matching vanilla; previously only positive effects landed on yourself
- Enemy on-hit effects (knockback, weapon debuffs, thorns, and the rest) no longer apply when you fully dodge, block, or negate an attack
- "0x Air" no longer appears in victory rewards — empty and unknown loot entries are filtered out of loot pools and the reward list
- Bosses can no longer permanently wall off the arena or delete its floor: Chorus Mind plants, Rockbreaker boulders, and Void Herald floor-collapse now decay back to normal, fixing an unrecoverable soft lock
- The Broodmother no longer freezes in place forever if it loses all its egg sacs in phase one; it returns to hunting instead of idling
- The Pale Garden level now loads its dedicated arena from the packaged build instead of falling back to a generic one (the sub-biome schematic lookup treated "forest/pale_garden" as a folder and missed the file)
- Sandstorm Pharaoh, Tidecaller, and Void Walker bosses now use a fresh AI instance each fight, so leftover state (planted mines, the flood, the phase) no longer carries into later fights or between co-op parties
- Void Walker mirror images shoved into a wall, the void, or lava no longer pay out loot, XP, or kill-streak credit
- A lethal Sandstorm Pharaoh sand mine now actually downs you instead of reviving you at 1 health
- Lit coal golems (flint-and-steel or the Netherite mount's summon) now switch to their proper ignited texture instead of staying the plain golem — they are fire-immune, so the vanilla flame overlay never showed; Craftics now flips Golem Overhaul's own lit state
- Mounts and party pets no longer get sent home mid-run after a Trial Chamber or Ambush victory — surviving allies (including the mount you are riding) are now carried into the next level instead of being "rescued" back to the hub and lost
- Hardened the return-to-battle transition after any interactive event (Shrine, Wounded Traveler, Treasure Vault, Dig Site, Enchanter, Wandering Trader): each carried-over golem/mount now respawns independently so one failure can't drop the whole party, the transition is wrapped so an error can no longer silently lose your pets (they fall back to the hub instead), and it no longer runs the new battle's first tick prematurely
- Dying in battle no longer sends your mounts and party mobs back home — a failed run now loses the golems and pets you brought into it, the same as the items you drop on death
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
- Instruments support: the 15 Genshin/Even More instruments are now sold by the Curiosity Dealer at mid-to-high tiers — they are combat weapons whose craft materials the void hub can't reliably supply, so they previously had no way to be obtained
- Vanilla mounts: horse, donkey, mule, skeleton horse, zombie horse, camel, and llama spawn eggs now drop from combat, so these mount allies (which can't spawn in the void hub) can finally be brought to your island, tamed, and recruited
- Golem Overhaul support: all nine golems are now built combat allies with distinct roles. Terracotta golem is a taunt tank that forces enemies to attack it, hay golem heals the lowest health nearby ally each round, candle golem fights at range and burns its target, kelp golem soaks its target on hit. Coal golem can be lit with flint and steel into a fast heavy hitter that burns on hit and dies after one attack. Honey golem holds station and summons a bee ally each round. Slime golem splits into two small slimes when it dies. Barrel golem rolls bonus loot from kills it lands. Each golem is healed in combat by the material it is built from. The golem behaviors plug into the core through generic ally hooks, so nothing changes when the mod is absent
- Golem Overhaul: the Netherite golem is now a rideable combat mount — add it to your battle party and it carries you into the fight. Riding it locks your movement to the golem's slow pace (1 plus your speed bonuses, regardless of your Speed stat); it occupies a 1x3 wall (its tile plus the two tiles to either side of the way it faces) that enemies cannot path through or stand beside — the two side tiles are now shown as steel-blue highlights and follow you, reorienting as you turn, so the footprint is visible at all times and no longer looks like a single tile; you are immune to lava, water, and powder-snow tile effects while mounted (you ride above the ground); each of your normal weapon attacks also erupts a lava line toward the target as a bonus — now a proper spectacle: the golem's furnace chest flares open, a molten bolt launches down the line, and each tile bursts with a lava/flame geyser, explosion sound, and amber flash, immediately burning any enemy it covers and leaving lingering lava; the furnace chest also glows open while the golem walks and while it roars out its summoned coal golems (which now erupt in fire as they land); and a new Mount Ability key (default M, 3 action points) has it summon two coal golems that spawn already lit, so they land a heavy fire hit and then burn out
- Golem Overhaul: honey golem bees now charge the nearest enemy on the round they are summoned, instead of drifting toward a distant low-health straggler
- Golem Overhaul: clarified that golems join a battle (and return home afterward) by adding them to your party with Shift+Right-Click, the same as vanilla iron and snow golems — there is no automatic hub-yard scan
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
- Each horn plays its own instrument sound (Ponder plays Ponder, Yearn plays Yearn, etc.) — the previous code picked a random sound from the goat-horn list on every use
- Re-using a buff horn refreshes duration to max(remaining, fresh) instead of clobbering the existing effect
- Weakness debuff lifecycle: enemies hit by Admire now lose the -2 ATK after the listed turn count, ticked alongside the existing defense-penalty system
- Stale "Taunt all enemies" tooltip removed
- Pre-overhaul horns with the legacy custom-name tag continue to work (backward-compatible)
- Goat horns are now Special-class items: the player's Special affinity scales horn duration and amplifier the same way it scales potions
- Re-using a horn before its previous effect expires now stacks the amplifier (capped at MAX_HORN_AMPLIFIER) and refreshes duration — consecutive casts make the buff or debuff stronger, not shorter
- All four version shards (1.21.1, 1.21.3, 1.21.4, 1.21.5) compile and run; vanilla INSTRUMENT API drift in 1.21.5 isolated to a single helper class

Banner overhaul

- Banners now place a real, color-correct banner block on the target tile (previously the use was silent: no block, no visible AOE, no way to tell it had worked)
- Each turn, the +DEF aura is outlined by sparse happy-villager particles on every tile within manhattan distance 2 of any planted banner
- Allies inside the aura now actually get the banner DEF bonus — previously the placement message claimed they did, but only the player's damage path applied it
- New "Banner aura reduced damage by N%" message when incoming damage to the player is mitigated by a banner zone
- Banners are now Special-class items: the +2 DEF base scales with the player's Special affinity at placement time and is frozen into the tile-effect entry, so later affinity gains don't retroactively buff old banners
- Overlapping banners take the max DEF of the strongest single banner instead of stacking (no infinite +DEF from carpet-bombing)
- Banner color is preserved through the tile-effect lifecycle so all 16 vanilla colors stay visually distinct
- 16-banner enumeration is now explicit (BannerEffects helper) instead of `item.toString().contains("banner")` string sniffing
- Special-class scaling extracted into a SpecialAffinity helper used by potions, banners, and goat horns — one source of truth for the formula
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
