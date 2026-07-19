Changelog
0.3.1
Crash Fixes

- Fixed a game crash ("Pose stack not empty") and corrupted tooltip positioning when the Artifacts umbrella - or any item another mod renders with a cancelling custom renderer - was visible in an inventory or dropped in the world. The Hilt upside-down render effect pushed a matrix frame that another mod's render cancellation could leak; the flip now wraps the render call so it balances on every exit path
- Registry health scan: on server start and stop, every game registry is swept for broken entries (null holes, unbound references - the unattributable cause of "NullPointerException in RegistrySyncManager.unmap" crashes when quitting a world). A broken slot is logged with the registry name and the mod entries around it, so the culprit mod is named in the log instead of a blank crash report
- The registry scan also runs client-side, after joining and right before disconnect cleanup - multiplayer disconnect crashes corrupt the CLIENT's registries, which the server-side scan can't see
- FIXED the disconnect crash itself: joining a server whose mods register entries the client doesn't have leaves null holes in the client's registries (Fabric registry-sync behavior), and Fabric's own disconnect cleanup then crashes iterating them. Craftics now compacts those holes right before that cleanup runs, so leaving the server no longer crashes the game. The scan still logs the mismatched registry and mod namespaces - aligning the client and server mod lists remains the proper fix

Server Fun

- /craftics scoreboard spawn (op): places a floating, live-updating INFINITE MODE top-10 board where you stand - a text display that refreshes every few seconds from the banked best scores and survives restarts. /craftics scoreboard remove clears boards near you
- Lootboxes are physical chests in the world: /craftics lootbox place <type> [cost] (op) sets a kiosk chest in front of you, at the standard price or any emerald cost you choose (0 = free to open), in five flavors - Weapon Cache (all affinities, 3% chance at a Simply Swords runic legend), Armor Cache (chainmail to netherite, trim template bonus), Material Crate, Special Cache, and Tome Cache (vanilla + Craftics enchanted books). /craftics lootbox remove (op) retires the chest you're looking at
- Opening a kiosk plays the full show - the lid swings open with vault and chest sounds and a particle burst, then the treasure-reveal screen - and costs banked emeralds (10-30 by type), or a Lootbox Key, a marked name tag only admins can grant (/craftics lootbox key), which opens any chest free. Kiosks survive restarts
- /craftics lootbox odds <type> - available to every player, no permissions - prints the exact drop table: section chances, item lists, and per-item percentages, generated from the same data the rolls use so it can never drift from reality
- All of it is earned in play or admin-granted; no purchase hooks, and full odds disclosure, in line with Minecraft's server monetization rules

New Hub

- The central lobby is now a hand-built hub pasted from a bundled schematic (177x160), replacing the old procedural floating island. Players spawn on its 2x2 crying obsidian pad; the builder finds the pad automatically, sets both the world spawn and the join teleport to it, and /craftics lobby setspawn still overrides
- The whole hub build is protected from block breaking (overworld only - personal islands are untouched even near their dimension origin). Existing servers rebuild the lobby automatically on next load via the lobby version bump
- Schematics now restore their saved block entities: sign text, chest contents, banner patterns and the rest survive placement (previously only the blocks were pasted, which left the hub's signs blank). Applies to every schematic the mod places - hub, home islands, arenas, scenes
- /craftics lobby rebuild (op): re-paste the central hub from the bundled schematic in place, no world reset needed. Re-place lootbox chests and scoreboards inside its footprint afterwards
- Fresh dedicated servers now default to the Craftics world type on their own: if no world exists yet and level-type was left at vanilla default, server.properties is set to the Craftics preset before the world generates. Existing worlds and deliberate level-type choices are never touched; level-type=default keeps vanilla terrain

Placement Rules

- Special blocks (campfire, banner, torch, lantern, jukebox, scaffolding, honey/slime block, cactus, cake, spore blossom, lightning rod, powder snow) now require flat, solid ground: no more planting a campfire over the void or a banner in lava. Void, sunken pits, water, deep water, lava, fire, powder snow, obstacles and stairs all refuse placement with a clear message

Water Rules Cleanup

- The guide book now tells the truth about water: regular water tiles are wadeable by anyone (Soaked for 2 turns unless a boat is consumed), and only DEEP water blocks movement and drowns you on a knockback. The Tile Types page gained a separate Deep Water card
- The Turtle Helmet now actually does what the guide always claimed: wearing it lets you wade water without getting Soaked (it already saved you from a deep-water dunk)
- Clicking an unreachable deep-water tile now says deep water is too deep to wade instead of wrongly asking for a boat

Server Administration

- /craftics config reload (op): re-reads craftics-config from disk, so scaling, timers and toggles can be tuned on a live server without a restart
- Admin commands now take an optional [player] target: reset_combat, heal, give presets, set_emeralds, set_level, set_ngplus, set_ap, set_speed, set_stat, reset_stats. They all work from the server console or command blocks now, and reset_combat can rescue a stuck player who can't run it themselves
- /craftics infinite stop <player> (op): force-end another player's infinite run - live or parked - the admin recovery for a hung run
- Idle performance: combat managers for players not in a fight no longer evaluate music selection every tick - the one per-tick cost that scaled with how many players had EVER fought instead of how many are fighting
- Config note: dedicated servers should enable turnTimerEnabled so an AFK party member can't stall a fight
- Download size: the soundtrack re-encoded from ~140-160kbps to ~80kbps Vorbis, and the three 15-minute marathon tracks (Spider Den, Basalt Deltas, Crypt) trimmed to clean 5-minute loops with a fade - 190MB of music is now 75MB with no audible change under gameplay
- Arena prefetch: the next level's arena now builds while the party sits on the victory screen, so first-time entry into a level no longer hitches at the transition. Repeat visits were already cached and stay instant

Infinite Mode Classes

- Starting an infinite run now opens a class selection: one class per affinity (Slashing, Cleaving, Blunt, Ranged, Water, Special, Pet, Physical) or Skip to go in with nothing but the two logs
- A class grants +1 point in its affinity and a modest starter weapon - stone sword/axe/hoe/shovel by class, a stick for Blunt, a bow with 8 arrows for Ranged, a horn coral for Water, a shield for Physical. A leg up on the from-nothing start, not a power spike
- Each party member picks their own class. Rejoining a resumed run as a fresh participant offers the pick again; the host's original choice stands. Closing the screen counts as Skip, and a pick can never be claimed twice

Sixteen New Enchantments

Weapons:

- Undertow (sword): an enemy whose attack you dodge or deflect is dragged 1 tile toward you. the drag obeys real knockback rules, so hazards between you count
- Hemorrhage (sword): knocking back a Bleeding enemy detonates its Bleed stacks into one burst and clears them
- Ambush (sword): killing an enemy before it acts this round frightens the next enemy in the order (-2 ATK for 1 turn)
- Timberfall (axe): obstacles you Demolish fall onto the enemies beside them - damage and a Stun under the falling block
- Pole Vault (blunt): gap jumps cost the plain walk price, and you can vault clean over enemies. an occupied tile counts as a jumpable gap
- Midas (blunt): slamming an enemy into a wall shakes 1-2 emeralds into your bank, once per enemy per fight
- Tag Team (shovel): once per turn, command a pet onto your own tile to swap places with it as a free action
- Trapper (hoe): a splash potion thrown at an empty tile with no enemy in range buries as a hidden trap; the first enemy to stand there eats the full potion - and knocking an enemy onto a trap springs it too

Armor (a first - armor enchants read from the piece actually worn):

- Iron Will (helmet): Confusion, Blindness and Darkness on you tick out at double speed
- Beacon (helmet): you count as a walking banner. Party members within 2 tiles gain the banner defense aura
- Phalanx (chest): +1 AC for you and each adjacent party member, both sides of every pairing
- Grudgeplate (chest): the last enemy to damage you takes +2 from the whole party
- Trailblazer (legs): party members moving along tiles you crossed pay 1 less Speed until your next turn
- Longstride (legs): your jumps clear gaps up to 3 tiles wide
- Ledgegrip (boots): once per combat, a knockback into a pit or deep water becomes a caught edge - 2 damage instead of death
- Shockstep (boots): landing a gap jump stomps adjacent enemies for damage and a 1-turn Slow

- The enchanter, trial keys, traders and mob gear all roll the new enchants from their matching pools automatically. Craftics armor enchants join the vanilla armor pools per slot

0.3.0
Boss Identities

- The Tidecaller gained Conduction: it marks a random combatant, then a turn later lightning strikes the mark and chains between everyone within 2 tiles of each link. Standing in water doubles the hit, so the boss floods the arena then electrocutes it. The mark follows its target, and the boss's own drowned conduct the chain
- The Tidecaller's flood is now a real wave: a full-width wall 3 tiles thick that sweeps across the arena 3 tiles a turn and carries anyone it catches
- The Bastion Brute gained Momentum: it hits harder the further it moved to reach you, so hugging it is safest and kiting feeds it. Three destructible war banners feed it (March gives speed, Fury attack, Horde its piglin summons); break the one that is hurting you
- The Revenant now fights around graves: 50 HP blocks that raise zombies every other turn, plus two more in phase two. Living graves cap the zombie count. Shield Bash became Burrow: it digs under, untargetable for a round, then erupts from a random grave. No graves means no burrow, and breaking the last grave while it is under drags it back out

Boss Balance

- Boss attack is now capped so scaling can never push a boss to a near one-shot. A late-biome boss was reaching 16+ attack

Enchantments

- Facade (axe): your axe hits for 1.5x while you have any negative effect. Works on modded and cleaving weapons, not just vanilla axes
- Eight more: Conductive (copy your debuffs onto what you hit), Rabid (pets copy theirs), Hilt and Dull (convert a weapon to Physical or Blunt affinity at a damage cost; Hilt renders upside down), Reversal (below 25% HP a hit cleanses a debuff and hits harder), Executioner (more damage per debuff on the target), Pack Bond (pets hit harder per other pet), Serrated (lose the sword sweep for a bleed on every hit)

Artifacts

- The Flame Pendant now burns its wearer instead of nearby enemies: permanent Burning, 2 HP a turn. A pure drawback that pairs with Facade and the debuff-copy enchants

Arena Fixes

- Fixed arenas generating with holes or missing floors, floor sand no longer falls away, and cliff edges no longer carve into the hollow beneath the arena
- Trial chambers no longer generate inside another arena
- Fixed events after level 3 of a biome sending you back to replay level 3

Trading Hall & Bartering Station

- The Trading Hall and Bartering Station are now real markets. Each booth hosts a named merchant (three villager traders per hall visit, three piglin barter personalities per station visit), and booths re-roll each visit
- Click a booth to walk up and open its merchant; booth floors glow and pulse under your cursor. Open floor is still a plain walk-to
- Villager booths open a parchment shop screen: real item icons and tooltips, emerald costs that redden when unaffordable, per-visit stock with SOLD OUT rows, and purchases straight from your emerald bank. Stock is shared across the party live
- Trade quality scales with your island's highest unlocked biome
- The piglin barter stepper got quick-adjust buttons (-5/-1/+1/+5/Max), a greed meter, the piglin reacting to your offer, gold-ingot icons, and win/loss sounds
- Trading is fully multiplayer-safe: everyone can shop at once, and a disconnect never wedges the market

Arenas

- Eight new hand-built arenas: two more for Plains, and Desert and Snowy both go from 3 and 2 variants up to 4 each

Totems & Regeneration

- Totems of Undying now save you from the void, and from untracked vanilla damage (explosions, fire, fall) that used to kill through a totem without consuming it
- Vanilla Regeneration no longer applies to players in combat, from any source; it healed on a real-time tick and undercut the damage economy. Craftics' own turn-start Regeneration is the only regen you get

Trading Hall Fixes

- Trades scale with tier in kind: from tier 5 weapons and armor can roll enchanted (god rolls from tier 8), and building blocks come in bigger stacks
- The armorer now stocks any armor strong enough for your tier, netherite and turtle shells included, and reads modded armor from the registry automatically, instead of only the four diamond pieces
- High-tier armor enchants now draw from the full slot-appropriate pool (Respiration, Feather Falling, Depth Strider, Swift Sneak, and more) instead of one fixed four-enchant list
- God rolls now roll in the upper half of their range rather than always at max, so endgame pieces differ
- Potions and enchanted gear now preview correctly in the shop (the trade list was stripping them to bare item ids)
- Booth glow highlights now come from the hall's real booths instead of a phantom re-derived layout
- The perimeter wall now walls every standable fall edge, not just reachable ones
- Walking to a merchant now stops two tiles in front instead of against them

The Raid

- New event: defend against a pillager raid. It dominates the event roll (75%) on a fresh island until you win one, then drops to a normal rate. Accept or decline by party vote
- Waves of 3-4 reinforcements arrive every 3 turns, telegraphed in red a turn ahead with a horn and "Wave N" title. Clearing the field early brings the next wave immediately
- Raid size scales with progress, 10 to 25 pillagers, with the last wave bringing an evoker or ravager
- The Trading Hall now requires BOTH meeting a trader at an event AND defeating a raid. The Bartering Station is unchanged
- Admins: /craftics force_event raid, and /craftics merchants meet_all now also marks the raid defeated

Movement & Jumping

- You can now jump gaps up to 2 tiles wide (void, deep water, lava, fire, water), vaulting across in one arc. A jump costs the walk-equivalent plus 1
- Pathfinding picks a jump only when it is cheaper than going around; jump destinations show in reachable highlights and price into the move cost. Jumped tiles are marked with arrows instead of dots
- You cannot jump over obstacles, powder snow, or enemies
- Fixed the Pathfinder trim routing players through the inside of obstacle blocks; they now step up and over

New Enchantments

- Seven new enchantments, each a mechanic rather than a stat stick:
- Matador (sword): every attack you dodge, deflect or block Exposes the attacker (-2 DEF for 1 turn), turning armor-class builds into opening machines
- Phantom Edge (sword, max III): attacking from tall grass now flattens it and reveals you - this is new; ranged stealth used to be permanent - and Phantom Edge preserves your cover once per turn per level
- Demolisher (axe): attack an adjacent obstacle to chop it off the battlefield for 1 AP, refunding 1 Speed. Cover, chokepoints and boss walls become targets
- Crater (blunt): your knockback sends enemies 1 tile further, and slamming into a wall, obstacle, cactus or another enemy deals extra damage and Stuns
- Momentum (blunt): your killing blow banks +1 AP for the next party member in the turn order (solo: your own next turn), once per turn
- Vengeful Bond (shovel): an enemy that kills one of your pets is Marked for 2 turns, taking double damage from every source
- Terraform (hoe): Special items cast at a tile also normalize it - douse fire, drain water, fill sunken ground
- Blunt weapons (mace, clubs, hammers, quarterstaffs, greathammers) now have their own enchant pool at the enchanter, including "might" ordering fixes: on 1.21.1-1.21.4 the blunt pool was unreachable and clubs rolled sword enchants like Sweeping Edge

Modded Weapon Enchant Compat

- Hilt and Dull can now actually be applied to Simply Swords and Basic Weapons gear. Three separate gates each blocked it: the enchantable item tags only covered vanilla swords/axes (modded weapons live in c: tags, not #minecraft:swords), the runtime sword filter only recognized the six vanilla swords so a force-applied Hilt silently did nothing, and blunt weapons were shadowed into the wrong enchant pool
- Every Simply Swords and Basic Weapons weapon is whitelisted into the matching enchantable tag by damage class (slashing weapons take sword enchants, cleaving take axe enchants, blunt take the new blunt pool), so anvils and enchanting tables accept the books, and the enchants actually fire in combat
- Hilt now also fits the vanilla Mace and all blunt weapons via the new #craftics:enchantable/blunt tag

Status Effect Icons

- Every status effect now shows as a colored symbol above whoever has it, player or enemy, in a row that lasts as long as the effect. Each effect has its own symbol and color; unknown addon effects show a neutral marker
- Physical effects give off particles too (burning sparks, poison haze, soaked drips, wither smoke, bleed, frozen snow, enraged steam). Abstract effects (Marked, Exposed, Haste) stay clean
- Enemy effects that were never sent to the client are now visible: Wither, Frozen, Taunting, and every mob buff (Regeneration, Absorption, Resistance, Strength, Haste, Slow Falling)
- In multiplayer you now see teammates' effects, not just your own
- Hovering a combatant floats a green arrow over them to confirm your target

Spectral Arrows

- Spectral arrows now Mark their target for a turn, making it take 2x damage (1.5x for a boss) from every source. The Mark does not stack or refresh
- Spectral arrows now count as ammo, and the weaponsmith stocks them from tier 4

Tooltips

- Hoe and shovel tooltips no longer list which Craftics enchantments the tool can carry. Each enchantment already prints its own line with real numbers once it is actually on the tool, so naming them up front just described enchants you didn't have

Combat Fixes

- Per-turn effects were losing their final tick (durations counted down before the payout), so a 3-turn Regeneration healed twice. Poison, Wither, Burning, and Bleeding all now tick their full duration; Wither, which peaks on its last tick, was hit hardest
- Getting Soaked now puts out Burning immediately, and a drenched target can't be re-lit until it dries. Players and mobs alike
- A burning enemy now flees to the nearest reachable water instead of standing in the fire, without fleeing into deep water. Bosses and un-soakable mobs (drowned, guardians) do not
- Pale Gardens: creaking from decorative tree hearts no longer harass you mid-fight; battle start clears naturally-spawned hostiles and petrifies nearby creaking hearts
- Addon mob variants (Creeper Overhaul, Variants and Ventures) now inherit their base mob's weaknesses and resistances
- Mobs on water tiles get water breathing, so a combatant knocked into water no longer drowns between turns
- The Tidecaller's arena drains between battles instead of the Deluge water surviving into every revisit
- The Host trim and quartz trim material each give +4 max HP per piece (was +8), so a full set of both is +32
- Daggers and sais now have identical stats, and dual wielding wears down the offhand blade
- With 4+ players, the ally roster stacks below the party HP bars instead of over them

0.2.10
Stats, Pets, Armor, and Arena Fixes

Stats & Max HP

- Vitality now applies the moment you spend the point, instead of waiting until the next fight starts
- Max HP bonuses now come from a real max-health attribute instead of the vanilla Health Boost effect, which only came in 4 HP steps and forced every HP bonus to round to a multiple of 4
- Quartz trim is now +6 max HP per piece (+24 for a full set). It said +2 in the guide book and granted +8
- Vitality is +8 max HP per point and Host trim is +8 per piece. Both were listed as "+2" in the guide book

Pets

- Pet affinity now grants ally HP, raised from +3 to +10 max HP per level. Only tamed mobs were getting it before. Hub battle party pets, spawn-egg summons, totem-revive summons, and ability summons all spawned with no Pet-affinity scaling
- In co-op, pets scale off their own owner's gear and affinity rather than the fight leader's. This already worked for ally damage, now it works for ally HP
- The Lead can now move allies, not just order attacks. Select a pet and green tiles show where it can walk, red marks enemies it can reach. Click a green tile to move it, or a red enemy to strike. Commanding an ally still doesn't use its own turn
- Lead commands now cost 1 AP instead of 2, for both moving and attacking
- Selecting an ally with the Lead used to show no highlights at all, leaving you clicking blind

Enchantments

- Shovels and hoes are now focus tools with eight new Craftics enchantments. They still swing badly. Their value is what they carry, and a focus works from anywhere in your inventory - you never have to hold it. Carrying two of the same enchant does nothing; only the highest level counts
- Shovel enchants arm your PETS: Honed (max V, +1 pet damage per level), Fire Fang (max III, pets set targets alight for 2/3/4 turns), Water Fang (max III, pets apply Soaked for 2/3/4 turns), and Thunder Fang (max III, pets shock every other enemy within 1/2/3 tiles of the target for 3 lightning damage)
- The three Fangs are exclusive, so one shovel takes one element. Carrying a Water Fang shovel and a Thunder Fang shovel is the combo: the Soak lands first, and lightning does double damage to a Soaked target
- Hoe enchants ride on your Special-item casts (potions, banners, horns, charges, pearls, pottery sherds): Reserving (max III, +5% per level that the cast costs no AP), Performative (max III, 5% per level to cast it twice for free, with no extra item and no extra AP), Radiant (max V, +2 damage per level against undead), and Medic (max III, +2 HP per level to any healing it does, including feeding a teammate)
- All eight are found the normal ways: enchanting tables, enchanted book drops, the Wandering Enchanter, trial chamber loot, and traders. Shovels and hoes previously rolled nothing but Unbreaking and Mending
- Every enchant has its own effect VFX, and item tooltips explain what each one does at its current level

Pottery Sherds

- Howl is now Petsplosion (3 AP, down from 5). Every pet detonates a 2-tile blast around itself for anvil-grade damage (half an enemy's max HP, minimum 10). Blasts stack where they overlap. Pets take no damage. Replaces Dread Howl
- Friend is now pet-only. Guardian Spirit heals every pet to full and grants +3 ATK and +1 Speed for 3 turns, still 2 AP. It used to heal the caster and buff a single pet
- Archer is now Seeker Vexes. Summons 2 vexes that fly at the nearest enemy on their own, 3 tiles a round, and destroy themselves on attack for 9 damage. They re-target if their quarry dies. Luck can summon a third
- Seeker vexes have 1 HP so enemies can shoot them down, and vanish after 5 rounds. They don't scale with Pet affinity and don't use a party slot
- Archer is now self-cast, where it used to need an enemy tile to target

Armor

- Gold armor was giving 0 Armor Class. It now has 2 base AC, on a par with leather. The Gambler set pays out in crit chance and emeralds, not protection

Weapons

- The Simply Swords spear could hit for 5x damage, enough for a 400-damage opening turn at high Speed. Its movement bonus is now +20% per tile up to a real 2x cap, and both spears cost 2 AP
- The Basic Weapons and Simply Swords spears and glaives now behave identically, since both mods ship the same recipe and you can't pick which you get
- Weapon tooltips now generate their numbers from the real values, so they can't disagree with what the weapon does
- Enchantments show on modded weapons again. Compat tooltips were wiping the vanilla lines below the first one, taking the enchantment list with them

Interface

- The AP and Speed display no longer runs into your hotbar. It's now a fixed-size plaque with two segmented meters (gold for AP, blue for Speed) that never changes size: segments get thinner as your totals grow, tick marks keep points countable, and the exact numbers sit alongside
- The plaque dims when it isn't your turn, so a teammate's spending doesn't read as your own

Arenas

- Arenas are sealed with a barrier wall so outside mobs can't wander in. Only grass and walkable blocks are replaced, so the terrain still reads naturally
- You can no longer spawn on an isolated block with no way off. Spawns now verify an escape route and relocate to the nearest safe tile
- Two mobs can no longer stack on the same tile (a pair of witches sharing one square). Placement and movement both refuse an occupied tile
- Obstacles from legendary weapons (rose bushes, bubble columns) no longer bake permanently into arenas you revisit. Existing saves are healed on load
- Standing on a water tile no longer drowns you. Players wearing Artifacts' Shrinking Charm took drowning damage on water obstacles; you now get water breathing while on one

0.2.9
Infinite Mode, Multiplayer, Enemies, Combat, Tools, and Interface

Infinite Mode

- A new endless run type. Start it from the Infinite Mode button on the Level Select screen or with §e/craftics infinite§r. Your inventory and progression are stashed away and you drop into plains at level 1 with nothing. The only thing you keep from a run is the emeralds you earn.
- Every biome is five levels ending in a randomized boss, then a fresh random biome, over and over. Each boss is built on the spot from a random mob body, a generated "The ___ ___" name, and a movepool pulled from every boss ability in the game, so no two play the same. Clearing a boss banks +1 score, gives a level-up that alternates between a stat point and an affinity pick, and drops the party into a rest room.
- The rest room is a between-biome breather with a crafting table, a furnace, and a smithing station. Ring its bell to start the next biome, or use §e/home§r to bank the run. Difficulty scales with how many biomes you have cleared, and every 10 biomes the bosses gain an extra move and an extra action per turn.
- A run ends when you go home, the party wipes, or the host logs out. Your stash comes back and your best score is saved to the leaderboard, viewable with §e/craftics infinite top§r. Runs are fully save and load safe, and the whole party shares the host's run.
- Homing projectiles arrived with it. Shulker bullets, grave skulls, and scarabs now fly as real seeking projectiles, and several bosses gained phase 2 kits built on them: the Revenant's grave skulls, the Sandstorm Pharaoh's scarabs, the Hexweaver's Hex Bloom and dual-action turns, and the Rockbreaker's retaliation shockwave.

Crafting Stations

- Crafting stations can now be used during a fight. Hold a crafting table, smithing table, loom, stonecutter, grindstone, cartography table, or enchanting table and click on your turn to spend 1 AP and open its screen. No block is placed and the item stays in your hand. Furnaces and other smelters are not included.

Co-op and Party Fixes

- Disconnecting during a between-level event no longer softlocks the party. When a player left during a trader, shrine, trial, dig site, vault, barter, or boss intro gate, the cleanup that passes the event onward read an already-cleared party list and never ran, so everyone else waited forever. It now resolves through the live event roster and finishes the event.
- A second run can no longer start on top of a parked one. Run invites and the infinite bell checked only for active combat, not for a player sitting at a between-level gate, so a new lobby could open over a parked event and paste a second arena onto the island. Both now check whether anyone is still engaged in the run.
- Boss projectiles and area attacks now hit the right players in co-op. Fireballs, wither skulls, shulker bullets, grave skulls, scarabs, and boss area attacks could previously only land on whoever acted last, and projectiles passed straight through other party members. They now damage and apply status to every party member actually in the blast, and a projectile stops on the first member in its path.
- A stale infinite-run host reference can no longer brick an island. If run leadership changed mid-run, the leftover reference used to refuse every future run on that island with no way to recover. It now validates itself and clears when the run ends or the host is offline.
- The trader event no longer destroys emeralds. A full inventory used to delete your whole balance instead of banking what did not fit, a disconnect mid-trade stranded emeralds as loose items, and a repeated done could zero the bank. Now only what actually reaches your inventory is deducted, the rest stays banked, and a disconnect reclaims your emeralds.

Main Menu

- Craftics now has its own main menu. The vanilla title screen is replaced with a cinematic front door built around YOUR run: the backdrop is the card art of the biome you're currently on in your most recent world, slowly drifting and zooming, and it cross-fades through every biome you've discovered. The menu tours your own journey. A caption in the corner names each biome as it goes by ("YOU ARE HERE" when it's your current one)
- The new cracked-stone CRAFTICS logo headlines the screen, and the menu column carries your region's accent color (green in the Overworld, red in the Nether, purple in the End) with drifting ember/spore motes to match
- A hero CONTINUE card shows your world's name, the biome you're on, your campaign progress (like "7/18"), and your NG+ tier. One click boots straight into the world, no world-select detour. With no worlds yet it becomes BEGIN YOUR RUN
- The full campaign path is laid out along the bottom as cleared / current / locked nodes, the same progression the in-game world select shows, with the current biome pulsing gold. Hover a node for its name; undiscovered biomes stay "???"
- The menu finally has music: it plays the battle theme of the biome you're on (the title screen used to be silent, since Craftics suppresses all vanilla music), with a "now playing" credit in the corner. The theme fades out cleanly the moment you enter a world
- Progress is read straight from your save files on a background thread. The menu knows where you are without loading the world, works with branch-swapped run orders, New Game+, and custom campaigns, and falls back gracefully on fresh installs or corrupted saves

World

- Fixed the home base island generating hollowed out. The island schematic was being placed through the arena terrain optimizer, which skips every fully-buried block, fine for arena landscapes, catastrophic for a solid island you live on. Newly created islands now place every block. EXISTING worlds are deliberately left untouched: opt in with §e/craftics world repairhollow§r to fill your island's hollow interior (fills only buried schematic blocks where the world has air), and §e/craftics world undorepair§r reverts that fill (it removes exactly the blocks the fill could have placed, so anything you built or mined stays as-is). An earlier dev build briefly ran the repair automatically on login and could paste blocks into older-layout saves. If that hit your island, run §e/craftics world undorepair§r once to clean it up

Boss Fights

- Every telegraphed boss ability now visibly charges instead of silently painting red tiles: a low resonant toll sounds, the boss's aura flares in its own colors, and particles converge on the doomed tiles in tightening pulses across your whole turn. The warning tiles themselves got scarier. On top of the red pulse, an inner square repeatedly collapses toward each tile's center, the noose tightening until the hit lands
- The "prepares X!" message now tells you how to survive it: every telegraph carries a plain-words hint like "a heavy blow will crush the marked tiles, get clear!" or "it will charge down the marked path, step aside!", so you're never guessing what a named ability is about to do
- Every boss attack resolves with a themed, shaped impact instead of a flat particle flash. Each boss speaks in its own visual voice (the Wither in souls and smoke, the Tidecaller in splashes, the Void Walker in portal motes, the Warden in sculk...), and the attack's nature decides its shape: slams detonate traveling ground shockwaves that bounce nearby minions, line and charge attacks sweep a flash down their tiles in order so you see the direction of travel, spells converge and burst with a tinted screen flash, summons breathe souls out of cracked earth, and terrain attacks ripple across the tiles they change. Camera shake, hit-pause and heavy smash sounds scale with the category, and when YOU are standing in the blast, the hit flashes the screen red and freezes for a beat
- Late-biome bosses that skip telegraphs no longer skip the presentation too: their attacks land with the same themed impact instead of an unheralded damage tick
- Directional boss attacks now SHOW their direction. Charges, pulls, pushes, and gales draw bright marching arrow glyphs on the battlefield pointing the way the attack will travel. The brightness ripples from tile to tile along the direction, so even a glance reads "it's coming THIS way", instead of every telegraph being the same red pattern. Wired across the roster: the Revenant's Death Charge, the Rockbreaker's charge, seismic shove, boulder knockback and avalanche, the Hollow King's Miner's Fury, the Void Herald's Void Gale, the Hexweaver's Hex Snare and single-lane Fang Line, the Tidecaller's Riptide Charge, the Void Walker's Void Beam, and the Ashen Warlord's Fire Pillar, with matching wind-drift particles streaming the same way over the marked tiles. Multi-directional variants (the phase-2 four-way fang cross, the bidirectional pillar X) deliberately stay arrow-less rather than show one misleading direction
- The Frostbound Huntsman's harpoon gale got the full wind treatment: instead of painting one red lane (which lied about the damage area and still didn't say which way you'd be dragged), red danger paint now marks only the tile where the harpoon actually hits, while arrow glyphs sweep across the ENTIRE arena showing the direction the gust will drag you. The telegraph hints were reworded to match ("the arrows show which way you'll be dragged")
- Phase 2 transitions are now a moment: a dragon growl, a shockwave rolling out from the boss that knocks its own minions into the air, a blood-red flash, heavy camera shake, and ENRAGED floating over its head, on top of the existing roar pose and title card

Simply Swords Compatibility

- Full Craftics combat support for the Simply Swords mod. All fifteen standard weapon types across every tier (iron, gold, diamond, netherite, runic) now fight properly in tactical combat, each with its own damage class, AP weight, and signature move: longswords and cutlasses sweep, katanas open bleeding wounds, rapiers riposte, paired sais and twinblades strike twice, spears share the Basic Weapons charge-momentum bonus, warglaives cleave when dual-wielded, halberds poke at reach, scythes reap a bleeding arc, claymores and glaives carve wide arcs, greataxes shatter armor, and greathammers slam like maces
- The chakram is its own thing: a thrown disc with 3-tile range that always returns to your hand (no ammo), and ricochets off your target into a nearby enemy
- Simply Swords UNIQUE weapons are now legendary boss drops. Beat a boss and each party member has a chance (configurable, Luck helps) at one of ~45 uniques, rolled to favor weapons you don't own yet. Every unique carries its own affinity, signature effect, and proc animation. Mjolnir calls down thunder, Frostfall flash-freezes, the lichblades drink souls, Hiveheart unleashes the swarm, The Watcher screams with the Warden's voice, Enigma does... whatever it feels like, and the Sword on a Stick bonks
- Simply Swords tooltips are rewritten in Craftics terms: the mod's own effect text, gem-socket lines, and attack-speed blocks are stripped away, replaced with the real in-combat stats and effects. Uniques get the full "⚔ Legendary Boss Weapon" treatment. Crafting materials (runic tablets, gems, relics) keep their original tooltips
- The area-effect uniques now catch diagonally-adjacent enemies. Cinder Slam, Depth Charge, Thunderstrike, and Starfall only hit the four orthogonal neighbors of the target, so an enemy on a diagonal took nothing. They now cover the full 3x3 around the target, matching the mace slam. Soulrender's tooltip was also corrected to show its real +2 HP per enemy heal.

Interface

- The Victory, Game Over, and reward reveal screens were polished to a professional standard while keeping the parchment theme. All three now ease in with a curtain-up scale-settle instead of popping into existence, and share upgraded coin rendering: soft drop shadows, rim lighting, and a continuous tumble instead of a two-frame flip
- Game Over is now a somber cinematic: letterbox bars close in, a blood-red vignette edges the screen, grey ash (with the occasional ember) drifts down for the duration, and the title lands as a large, slowly-pulsing "GAME OVER" instead of one more line of text
- Victory earns its name: a larger shimmering gold title, a "+N" gain readout riding the emerald counter while it ticks up, and a shower of gold sparks when the last reward settles. The barter gamble's coin now pays off in sparks too, a gold shower on a win, a dull grey puff on a dud, and "(click to continue)" gently pulses so the exit is never missed


Combat Feel

- Mace ground slams are now true shockwaves. The impact detonates outward ring by ring over time. Dust and cloud rings expanding under a pale tile flash that travels with the wave front, falling-pitch thumps per ring, and every mob the wave passes under visibly bouncing into the air (a parabolic hop with a little rebound, purely visual). A Wind Burst mace upgrades to a heavier three-ring wave with the heavy smash sound and a brief white flash
- Mobs caught in a slam's area no longer re-detonate the full explosion each: extra victims get a light poof while the single traveling shockwave carries the drama, so a crowded slam reads as one big hit instead of five stacked ones
- Impacts are now directional: sword, axe, bow, crossbow, trident, mace and even fist hits spray their particles along the actual line of the blow, so a strike from the west visibly carries through to the east. Diamond crits, netherite executes, axe cleaves and heavy shovels also give the target a visible knock-up on impact, and the netherite execute scorches the ground tile red
- The mace uses the real mace smash sounds (air whoosh on the windup, ground smash on impact, heavy smash for Wind Burst)
- The attack preview now flows with your aim: while hovering a target, the highlighted damage and effect tiles pulse in a wave that travels outward from you through the shape, a cone visibly sweeps away from you, a slam radiates from the impact point, so you can read at a glance where the hit is going

Multiplayer

- Loot is now rolled per player instead of shared. When several players land the killing blow on the same mob, each one rolls the equipment-drop chance independently, so two players who co-kill an armored mob no longer always get the same outcome (one can get a piece the other doesn't). The dropped gear is also copied per recipient, and any armor trim is re-rolled per player, so two players who both win the same helmet see different trims
- The victory boss trim template is rolled per player too. Each reward recipient rolls their own smithing template from the eligible pool instead of the whole party receiving one identical template
- The Wandering Trader event no longer lets idle players wander the event room. The trader is a single shared merchant that serves one player at a time, so while one player shops the rest are now held on a locked "Waiting for the rest of the party..." overlay with no free movement, instead of being handed back first-person control to walk around mid-event. Everyone is released together once the last player finishes. A player who disconnects while actively trading now also releases the merchant lock and hands it to the next in line, so the rest of the party can never be frozen waiting on someone who left

Scaling

- Enemy HP now scales harder with party size, and the rate is configurable. The per-extra-player HP bonus moved from a hardcoded +25% to a config value (partyHpPerPlayer, default +75%), applied at every enemy spawn path (normal mobs, the creaking heart, end crystals, and stacked riders). At the default a 2-player fight has roughly 1.75x enemy HP, 3-player 2.5x, 4-player 3.25x, so multiplayer is no longer trivially easy
- Bosses now get tougher each time you beat them on the same island. A new per-island kill counter (stored per world owner, so a party shares one count and other islands are unaffected) adds a linear HP bonus on every repeat encounter (bossKillHpScale, default +50% per prior kill: 2nd fight 1.5x, 3rd 2x, and so on). The count is applied at spawn time and persists across world reloads. This stacks with party scaling, so a repeatedly-farmed boss in a full party ramps up meaningfully

Interface

- The inventory stat panels got a full visual overhaul to match the Guide Book. Both the right-side Stats panel and the left-side Damage Affinities panel now render on the same parchment-and-leather frame the field manual uses (shared via a new GuideTheme so the styles can never drift apart), instead of the old flat dark-blue boxes
- Each panel now has a minimize button in its top corner. Clicking it collapses the panel to a thin sidebar showing just each stat's icon; hovering an icon pops a tooltip with the full name and value. Click again to expand. The collapsed/expanded choice is remembered for the rest of the session
- The panels now scale down automatically when the window is small or the GUI scale is high, so they always stay fully on-screen and their minimize buttons stay clickable under the cursor
- The "Next Level" victory screen got the same parchment Guide Book makeover, and now shows everything you collected during the level as an icon grid with x amounts (emeralds included), instead of just a line of text. Identical drops are merged into a single icon with a count, the grid wraps to fit, and hovering an item shows its tooltip. In multiplayer each player sees their own collected loot, and the event-prompt screens (Trial Chamber, Treasure Vault) share the new styling
- When your whole party wipes mid-run, you now get a Game Over screen that shows your at-risk items and runs a quick coin-flip on each one. A gold coin tumbles in above the panel, then flies down and lands on each item to reveal its fate: a gold star (kept), an orange hyphen (you lost some of the stack), or a red X (the whole stack is gone). Identical items spread across different slots are merged into a single stack, and each unit in it is rolled individually, so a stack of 5 might lose 2 and keep 3. The odds are the real death odds the game already used (backpack items are far likelier to be lost than gear) with the Luck stat improving your keep chance on every unit. The coins have easing, landing pops, and sound effects, and you leave the screen with an explicit Continue button. Each player sees their own flips; items are only actually removed once you continue (or after a short timeout), so a disconnect can't dodge the penalty. The old "lose ALL items" warning was corrected to "you risk losing items"
- The reward and Game Over screens no longer clip the item count: counts are now drawn in a separate pass layered above the item icons, so a neighbouring icon can never paint over a number (no more half-hidden stack sizes)
- The rest of the menus now share the Guide Book's parchment look. The NPC dialogue box, the trader shop, the level-up stat/affinity picker, and the stat and affinity respec screens were all reskinned onto the same parchment-and-leather frame with ink-on-parchment text (no more flat dark boxes with white drop-shadowed text), and their buttons use a new shared parchment button style so the whole UI is consistent

Bug Fixes

- Landing the killing blow with an anvil, TNT, or another consumable/special item no longer soft-locks the level. Those deferred and item-use damage paths despawned the last enemy but never ran the "all enemies defeated" check, so the fight hung; they now end the level the same way a normal weapon hit does
- The Game Over coin-flip screen now shows on any death during a biome run, including the first level of a biome. It was previously gated to deaths past level 1, so dying early just sent you home with no screen
- Digging a sunken pit or void with a pickaxe no longer fills in an adjacent pit or void you already dug. The cobblestone wall that keeps a fresh hole from opening into a big air gap now skips neighbouring tiles that are themselves a void, pit, or water/lava
- The Abandoned Campsite (Artifacts mimic) addon event can actually trigger now. The between-level event roll iterated the built-in events a second time (they are also listed in the addon registry with no handler) before ever reaching real addon events, so it kept landing on a handler-less entry and silently skipped to the next level. The roll now ignores those handler-less listing entries, so registered addon events like the campsite are reachable again
- The spider boss (Broodmother) no longer pounces into walls and gets stuck. As a 2x2 boss it was only checking that a single tile next to the player was clear before pouncing, so it could land with three of its four footprint tiles overlapping walls, egg sacs, or other mobs. It now validates its full 2x2 footprint before pouncing, the same way the regular spider does
- The Plenty pottery sherd no longer pushes Action Points past the cap. Its AP restore was added directly to your remaining AP with no ceiling, so it could stack you above your real per-turn maximum. The restore is now clamped to your effective AP ceiling for the turn (base AP plus set and Haste bonuses, minus Mining Fatigue), so it refills toward the cap instead of over it
- Thrown tridents are no longer lost when the target stands behind a line of obstacles. If the throw path was blocked, the trident "landed" on the enemy's own occupied tile, which you can never step onto, so it was gone for good. A blocked throw now drops the trident on a tile you can actually reach (next to you, or your own tile), so it is always retrievable
- Equipping an artifact mid-battle now actually works. Three holes were plugged: solo players' equipment was only scanned once at combat start (the per-turn re-scan only ran for parties), party members after the first never had their trim/artifact Speed and AP bonuses added on turn switch, and there was no mid-turn refresh at all. Now every turn start re-scans for the acting player, and equipment changes are picked up within a second even mid-turn. Slip on Running Shoes and the +3 SPD lands on your current turn, with an "Equipment updated!" readout
- The event pity timer was working against you. Each level without an event was supposed to ADD 5% to the next roll's event chance, but the math multiplied every event window by (1 minus bonus), SHRINKING them, so the longer your dry streak, the rarer events got, which especially starved low-probability entries like the Abandoned Campsite. The boost now genuinely grows the windows (capped so the full cascade, addon events included, always stays reachable)

Enemies

- Armored mobs can now spawn with trimmed armor. When a humanoid mob already rolls a piece of armor, each piece has a 5% chance to also receive an armor trim with a fully random material (iron, copper, gold, lapis, emerald, diamond, netherite, redstone, amethyst, quartz, or resin). Purely a visual flourish on the gear they already carry
- An EXTREMELY rare netherite miniboss can now appear. Any humanoid mob has a 0.001% spawn roll to instead deck out in a full, heavily enchanted netherite armor set plus a heavily enchanted netherite sword. It carries no extra stat buffs beyond what that god-tier gear provides, so it is a gear check rather than a scripted boss
- With the Artifacts mod installed, rare artifact-carrier enemies now spawn. Any non-boss arena mob has a 1% roll (about armor-trim rarity) to carry a random artifact: it holds the curio in its offhand, gets a "✦" name marker, and a chat line announces the find. The artifact genuinely works for the enemy, an enemy-side reading of what it does for you (Running Shoes +3 Speed, Power Glove +3 Attack, Crystal Heart +6 HP, Night Vision Goggles +1 range, and so on; every artifact grants at least a small bump). Kill the carrier and the artifact has a 25% chance to drop for you

Combat

- Reflected ghast fireballs now detonate the ghast that fired them. Hitting a ghast's fireball reverses it as before, but a redirected fireball that reaches a regular ghast enemy now kills it outright instead of chipping its health. This never applies to the Wailing Revenant boss (or any boss): bosses still take only the scaled reflected-fireball damage they always did
- Smite, Bane of Arthropods, and Impaling no longer fall off late-game. Their mob-type bonuses now scale as a percentage of the hit's base damage (+25% per level, so a level 5 enchant adds +125%) instead of a fixed flat amount, with the old flat curve kept as a floor so they are never weaker than before. Smite's radiant burst and Bane's poison-per-turn both ramp with weapon and stat growth
- Impaling now also rewards wet targets. On top of its percentage scaling, an Impaling hit against a Soaked enemy deals a further +50% on the Impaling bonus, leaning into its anti-aquatic identity within the combat system

Tools

- Pickaxes can now reshape the arena floor. Mining an adjacent normal floor tile digs a 1-deep sunken pit (a walkable dip), and mining an existing sunken pit deepens it into a bottomless void hole that anything falling in is lost to. Both digs auto-wall the hole with cobblestone so you never break through into a large air gap underneath, and the void hole keeps cobblestone interior walls with a black-concrete bottom. Mining a breakable obstacle still just clears it to walkable ground as before. All of this is restored when combat ends
- Anvils now always deal at least 10 damage. The per-stage fraction of the target's max HP still applies, but the minimum floor was raised from 1 to 10 so an anvil drop is always a meaningful hit, even against very low max-HP targets

Wording

- The combat "Dodge" mechanic is now called "Deflected" everywhere it shows to the player: the on-hit feedback, the Vex / Ethereal armor and artifact tooltips, the Aegis hybrid description, and the relevant guide pages all read "deflect" / "deflected" now

Loot

- Copper now drops far more often. As an early-game material it was a very low weight in its biome loot pools (weight 1 of roughly 70 on Plains and River), so it almost never appeared. Its weight was raised across the board: Plains and River 1 to 6, Cave 4 to 10, Mountain and Deep Dark 2 to 8
- Gravel is now a completion-loot drop in River and Mountain (weight 4 each). It was previously only obtainable from Piglin Barter
- A batch of common, useful blocks and materials that were never in any loot pool are now dropped from a thematically fitting biome. Several of these are literally a biome's own floor or obstacle block that you could see but never collect: clay (River), tuff plus andesite, diorite and granite (Mountain, completing the polished-stone set), moss block and mud (Jungle), calcite and dripstone block (Cave), and glass plus terracotta (Desert). A handful of handy materials that previously only came from specific mob kills were also added to completion loot: charcoal and honeycomb (Forest), slime ball (Jungle), glowstone dust (Nether Wastes), and bone meal (Plains)

0.2.8
Combat, Bosses, and Addon Compat

- Genshin Instruments addon: landing the killing blow with an instrument no longer soft-locks the level. Instrument attacks route through their own handler and break out before reaching the normal weapon handler, so they skipped the "all enemies dead -> end the fight" check every other attack runs. The kill itself was credited and the mob despawned, but combat never ended. The instrument handler now runs the same win-condition check after resolving, so an instrument can finish a fight (including a boss) like any weapon
- Killing a boss with only instruments no longer wrongly grants Pacifist General. That achievement means the player never personally dealt damage (pets did all the work), and it keys off a "player dealt damage" flag that only the weapon-attack path set. Instrument, lightning rod, placed-TNT, and similar player-initiated "special" damage all flow through one shared helper that never set the flag, so the game thought the player was a pacifist. The shared helper now records player-dealt damage whenever it actually damages an enemy, so every form of player damage counts
- Plains boss can no longer start a fight with only 6 HP. The stacked-enemy replacement pass (Zombie Stack, etc.) ran over every spawn with no boss guard, so on an unlucky roll it converted the plains boss into a stacked trash mob with placeholder HP. Boss selection then failed to find a matching boss and flagged a stray 6-HP zombie add as the boss. The replacement pass now skips the boss spawn, and boss selection falls back to the highest-HP spawn if no type match is found

Balance

- Anvil reworked to scale with the target instead of a flat 15 damage, and to wear out with use so its impact matches its cost. A pristine anvil now deals half the target's max HP, then wears to a chipped anvil (a third of max HP), then a damaged anvil (a quarter), then shatters. Each use wears the anvil one stage unless Special affinity saves it: every Special point gives a 10% additive chance to skip the wear (10 points keeps it pristine forever). Stacks degrade one anvil at a time, so a stack of pristine anvils becomes one chipped plus the rest pristine after a single use. Tooltips and the guide describe each stage and the affinity save

World and Arenas

- Anvils no longer linger on the stage after use. The falling anvil converts to a real anvil block on landing (in a handful of ticks), but the cleanup waited 14 ticks and only tried to discard the visual entity, which was already gone, so the block stayed. The anvil now records its landing tile, clears the landed block when it resolves, and is restored on combat end, so it plays the fall animation and then disappears
- Player-placed blocks no longer persist between arenas. Water and lava from buckets, powder snow, campfire, scaffolding, spore blossom, bell, jukebox, sponge, honey, slime, banner, and cactus were written into the world as real blocks but never cleared when combat ended; because arenas are cached per level and revisits rebuild from whatever blocks are physically present, the leftovers (water most visibly) got baked into the arena permanently and even bled into New Game+. Every placeable now records the block it overwrote and is restored to the original on combat end
- New Game+ no longer loads a mismatched arena for a boss (e.g. entering the mountain boss but getting the jungle arena). The cached arena was keyed by level number alone, so a New Game+ branch re-roll that points a level at a different biome would replay a stale arena from a prior cycle while the boss followed the new ordering. Cached arenas now carry a biome stamp; on load, if the stamp does not match the level's current biome, the arena is wiped and rebuilt to match. Older saves with no stamp self-correct the first time each arena rebuilds

Loot

- Every wood type's sapling now drops from a fitting biome: oak (Plains), acacia (Desert), jungle (Jungle), dark oak + birch + pale oak (Dark Forest), mangrove propagule (River Delta), spruce (Snowy Tundra), and cherry (Stony Peaks). Pale oak only exists on 1.21.4+, so on 1.21.1 / 1.21.3 that one entry is harmlessly skipped at load

0.2.7
World, Arenas, and Tile Classification

- Fences, cobblestone walls, glass panes, iron bars, and fence gates now count as obstacles. Tile classification only treated full solid cubes as obstacles (with a lone special case for cactus), so these partial-collision blocks were classified as plain ground: the player and auto-routing walked straight through walls that physically block movement in the world. A single shared check now flags any block above the floor that has a real collision shape, so pathfinding routes around all of them
- Tiles with a block one level up no longer mislabel as "Sunken Pit" or "Void" in the hover tooltip. When the cursor pointed at a raised obstacle block, the tooltip read the cell underneath it instead of the block itself, and an air cell down there fell into the sunken-pit / void branch and described the wrong layer. The tooltip now identifies an obstacle above the floor before the air-floor check, using the same obstacle test as tile classification so the tooltip, the move highlight, and pathfinding all agree

Bosses

- Boss attack telegraph tiles no longer get stuck on screen across levels. A boss killed on the same turn it telegraphed left its warning in the server's pending list forever. Dead bosses' telegraphs are now pruned every tick and the list clears on combat teardown.

Items and Loot

- Fished-up enchanted books now come with a real random enchantment instead of a blank book.

Events

- Ambushes now scale with progression instead of always spawning 2 or 3 enemies. Enemy count ramps with biome depth plus a surcharge for campaign position and NG+, so a late-game ambush hits harder than the regular fight.
- Trial chambers and ominous trials stay harder than the surrounding levels at any depth. Their mobs now get the same per-biome stat scaling normal levels do, with the trial multiplier kept as a surcharge on top.

Balance

- Rogue (Chainmail) set reworked. The old "attacks cost 1 less AP" did nothing for 1-AP weapons but doubled a 2-AP weapon's attacks per turn. It now keeps +1 Speed and +1 Slashing, and gives light (1-AP) weapons +2 damage and +20% crit, rewarding fast weapons instead.
- Melee and Ranged Power are now hybrid. Each point gives +1 flat damage plus +6% of total weapon damage, so the stat keeps scaling late-game instead of the old flat +2 that fell off.
- Armor Class and Max HP trims and materials rebalanced to match a level-up point. AC bonuses went from +1 to +2 per piece and Max HP trims from +4 to +8 per piece. Power affinity bonuses were already +3 damage per piece, so they were left alone.
- Blunt-resistant mobs are now immune to stun, which was too strong against them. This covers spiders, cave spiders, magma cubes, hoglins, piglin brutes, zoglins, ravagers, iron golems, goats, the Warden, and the Wither. Every stun source (mace, breeze, sherds, arrows, hybrid sets) respects it through a single gate.
- Bosses now shrug off half of every stun attempt (on top of full immunity for the blunt-resistant ones), so they can't be stun-locked.
- The Rockbreaker, Bastion Brute, and Hollow King now have blunt resistance, so they take half blunt damage and are stun-immune. This uses a per-boss override keyed on the boss id, so the regular mobs they are based on are unaffected.

Mob Heads

- Worn mob heads now grant a thematic combat buff on top of their damage-type affinity, so each head feels distinct.
- Skeleton Skull: +1 attack range with bows and crossbows.
- Wither Skeleton Skull: melee hits have a 25% chance to inflict Wither.
- Zombie Head: heal 2 HP on every kill.
- Creeper Head: a hit knocks the enemies in the 3x3 around the target one tile outward.
- Piglin Head: immune to fire and lava, plus 1 bonus emerald per kill.

Text and Display

- Sharpness tooltip now says it adds Bleed stacks per hit, not just melee damage, matching the guide and its real effect.
- Power enchant tooltip now lists its range bonus alongside the damage.
- Cleaving affinity wording changed from "armor ignore" to "armor shatter", since the effect permanently destroys defense rather than ignoring it once.
- Armor shatter chat message and the enemy hover panel now show the real reduced DEF after a shatter, instead of the original unbroken value.

Combat and Enchantments

- The wandering enchanter no longer rolls enchant levels above an enchant's real cap, so items can no longer come out with Mending II or Knockback III.
- Knockback now works on axes (and other melee weapons the enchanter can put it on), pushing the target like it does on swords.
- Fire-immune mobs such as blazes, magma cubes, striders, ghasts, and the Wither no longer take fire or burning damage.
- Swift Sneak's tooltip no longer promises a sneaking speed bonus. Sneaking has no role in tactical combat, so it now states plainly that it has no combat effect.
- Trial keys now actually queue a trial chamber after the fight. The regular trial key set an event id the spawner did not recognize, so it silently did nothing (ominous keys already worked).
- Moving right after an attack no longer wastes the AP. The hit's delayed damage now resolves before the move instead of being dropped mid-animation.

Achievements

- Armor Crush now unlocks. It watched for an "ARMOR CRUSH" message that never existed (the real one says "SHATTER ARMOR") and now records the defense actually destroyed.
- Phase Skipper now unlocks. The flag for killing a boss before it reaches Phase 2 was never set on boss death.
- Jack of All Trades now unlocks after respeccing into one point in every affinity, not only through normal level-up allocation.

Offensive Items

- Offensive special items now add a percent of the target's max HP on top of their flat damage, so they keep mattering against late-game enemies instead of falling off. Bosses take a third of the percent.
- TNT deals 24 / 15 / 9% max HP by blast ring (center / adjacent / outer) plus its flat damage, and its AP cost went from 1 to 2.
- Harming splash potions add 12% max HP. Single-target damage sherds add 8% (10% for the heavy Earthen Spike and Phantom Slash), the area sherds (Tidal Surge, Dread Howl, Chain Lightning) add 6% per enemy, and fire charge adds 8%.
- Damage-over-time effects now all scale with the target's max HP. Poison and wither already did; burning and bleed now add the same per-tick max-HP bonus so they keep up against tougher enemies instead of staying flat.

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


