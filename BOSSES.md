# Bosses — Design Plan

## The Problem

Every biome boss is currently just a bigger version of a regular mob — same entity type, 1.5× scale, iron armor, higher stats. A boss Zombie plays exactly like a regular Zombie with more HP. There are no unique attacks, no warnings, no phase transitions (except Warden and Dragon), and no reason to change your strategy. Zombie is used as a boss **twice**, Enderman is used **three times**. Bosses don't feel like bosses.

## The Solution

Every boss gets:

- **A unique name and identity** — not "Big Zombie" but "The Revenant, Undead Knight of the Plains"
- **2–4 custom abilities** that no regular mob has, with telegraphed warnings so the player can react
- **A Phase 2 at ≤50% HP** that escalates the fight and forces adaptation
- **Thematic connection** to the biome — the boss IS the biome's ultimate threat
- **Summoning / terrain / area denial** — bosses reshape the arena, not just hit harder

## Warning System

All boss abilities use a **1-turn telegraph** so the player has a chance to react:

| Warning Type | Visual | Used By |
|---|---|---|
| **Tile Highlight (Red)** | Target tiles glow red/pulse | AoE attacks, charges |
| **Ground Cracks** | Fissure particles on tiles | Summons from below, seismic attacks |
| **Gathering Particles** | Particles converge on boss/target | Channeled abilities, buffs |
| **Directional Arrows** | Arrow markers on tiles | Pushes, line attacks |
| **Sound Cue** | Unique sound plays | All abilities (layered with visual) |
| **Entity Glow** | Boss flashes/glows specific color | Self-buffs, phase transitions |

The warning fires on the **boss's turn**, and the ability resolves at the **start of the boss's NEXT turn** — giving the player exactly 1 full turn to reposition. Some fast abilities (melee variants) resolve immediately but still have a brief animation delay.

---

## Overworld Bosses

---

### 1. Plains — "The Revenant" (Undead Knight)

**Entity:** Zombie (custom equipment — iron helmet with chain, stone sword, tattered cape via armor stand overlay)
**Stats:** 20 HP / 4 ATK / 2 DEF / Speed 2
**Size:** 2×2
**Theme:** The first real boss. Teaches the player that bosses summon, charge, and punish positioning. A fallen warrior who crawls out of the earth with an undead army.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Raise the Dead** | — | Summons 1–2 regular Zombies (6HP/2ATK) on random empty tiles. Capped at 3 summons alive at once. Uses this every 3 turns. | Ground cracks appear on the target tiles 1 turn ahead. Groaning sound. |
| **Death Charge** | — | Charges in a straight line up to 3 tiles, dealing ATK+2 damage to anything in the path. Stops at obstacles. | Boss stomps twice, the charge path glows red for 1 turn. |
| **Shield Bash** | — | If player is adjacent, knocks them back 2 tiles and deals half ATK damage. | Boss raises off-hand (brief shield-raise animation). |

**Phase 2 — "Undying Rage" (≤50% HP):**
- Gains Regeneration (1 HP/turn)
- Raise the Dead triggers every 2 turns instead of 3
- Death Charge deals ATK+4 damage and leaves a trail of 1-turn fire tiles
- Boss entity begins emitting soul flame particles

**Design Intent:** Teaches players to prioritize adds vs. boss, dodge telegraphed charges, and manage the arena. A gentle introduction to boss mechanics — everything is avoidable if you pay attention.

---

### 2. Dark Forest — "The Hexweaver" (Woodland Witch)

**Entity:** Evoker (custom — dark robes, glowing green eyes, potion particles)
**Stats:** 28 HP / 5 ATK / 2 DEF / Range 4 / Speed 2
**Size:** 2×2
**Theme:** An Illager sorceress who commands dark magic, summons Vexes, and controls the battlefield with fang traps and cursed fog. Fights from distance and punishes players who can't close the gap.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Vex Swarm** | — | Summons 2 Vexes (3HP/2ATK, flying — ignore obstacles, speed 3). Capped at 4 Vexes alive. Uses every 3 turns. | Magic circle drawn on boss tile, arcane humming sound. |
| **Fang Line** | — | Sends evoker fangs in a 5-tile straight line from the boss toward the player. Each fang deals 4 damage. | Ground rumbles, crack particles appear along the line 1 turn ahead. |
| **Cursed Fog** | — | Places a 3×3 dark cloud on a tile cluster. Enemies inside gain +3 DEF. Player inside loses 2 ATK. Lasts 3 turns. | Swirling dark particles gather at the target. |
| **Hex Bolt** | — | Standard ranged attack at range 4. Applies 1-turn Slowness on hit. | Green projectile wind-up. |

**Phase 2 — "Arcane Fury" (≤50% HP):**
- Fang Line becomes cross-shaped — fires in both cardinal directions simultaneously
- Vex Swarm summons 3 Vexes and cap increases to 6
- Cursed Fog also deals 2 damage/turn to the player standing inside it
- Boss teleports 3 tiles away when the player gets adjacent (once per turn)

**Design Intent:** Forces the player to close distance against a ranged caster who creates zones of denial. Kill the Vexes or they overwhelm; avoid the fang lines or eat heavy damage. Phase 2 punishes camping at range by making fog dangerous.

---

### 3. Snowy Tundra — "The Frostbound Huntsman"

**Entity:** Stray (custom — ice crystal crown, frost-enchanted bow, pale blue particles)
**Stats:** 25 HP / 5 ATK / 2 DEF / Range 4 / Speed 2
**Size:** 2×2
**Theme:** A frozen predator that slows, walls off escape routes, and forces the player into killzones. The arena becomes an icy prison.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Blizzard** | — | 3×3 AoE centered on a tile. Deals 3 damage and applies Slowness (-1 Speed) for 2 turns to all units inside. | Snowflake particles converge on the target area. Wind howling sound. |
| **Ice Wall** | — | Creates a line of 3 ice-block obstacles (indestructible, last 3 turns). Blocks movement and line of sight. | Frost creeps across the target tiles. Crackling ice sound. |
| **Frost Arrow** | — | Enhanced ranged attack at range 4. Deals ATK damage + applies 1 turn of Slowness on hit. | Bow drawn with icy glow. |
| **Glacial Trap** | — | Freezes a 2×2 area. Any unit that starts their turn on frozen tiles is Stunned for 1 turn. Lasts 2 turns. | Ice crystals rise from the ground on target tiles. |

**Phase 2 — "Permafrost" (≤50% HP):**
- Blizzard also Stuns the unit on the center tile for 1 turn
- Boss gains Speed 3
- Every 2 turns, 2 random tiles become frozen (Glacial Trap effect) automatically

**Design Intent:** Positioning puzzle. The arena gradually fills with walls and frozen zones, restricting movement more and more. The player must break through or navigate around while avoiding the slowing ranged attacks. Phase 2 makes the arena a genuine labyrinth — if you haven't closed in by now, you're in trouble.

---

### 4. Stony Peaks — "The Rockbreaker" (Mountain Warlord)

**Entity:** Vindicator (custom — stone-plated armor, war hammer instead of axe, dust particles)
**Stats:** 30 HP / 6 ATK / 3 DEF / Speed 2
**Size:** 2×2
**Theme:** A hulking brute who uses the mountain itself as a weapon. Seismic attacks, thrown boulders, and unbreakable defense. An unstoppable force that punishes players who stand still.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Seismic Slam** | — | Slams the ground. Deals 5 damage in a + cross pattern (3 tiles each cardinal direction from the boss). | Cracks radiate outward from boss tile. Deep rumble. |
| **Boulder Toss** | — | Throws a rock at a tile (range 4). Deals 4 damage and creates a 1-tile stone obstacle where it lands. | Boss bends down, rips a chunk of ground. Tile reticle appears. |
| **Fortify** | — | Gains +5 DEF for 2 turns. Cannot be knocked back while fortified. | Stomp animation, stone shell particles encase the boss. |
| **Avalanche** | — | Full-row attack: an entire row of the grid takes 3 damage. | Rocks tumble from above, dust along target row 1 turn ahead. |

**Phase 2 — "Earthquake" (≤50% HP):**
- Seismic Slam range extends to 4 tiles per direction and destroys obstacles in its path
- Boulder Toss throws 2 boulders at different tiles
- Fortify is always passively active (+3 DEF permanent)
- Avalanche targets 2 rows instead of 1

**Design Intent:** The pure brute. Massive AoE damage that forces constant repositioning. The player has to weave between seismic crosses and avalanche rows while the arena fills with boulder obstacles. Fortify punishes chip damage — you need burst or you'll never get through.

---

### 5. River Delta — "The Tidecaller"

**Entity:** Drowned (custom — coral crown, enchanted trident, water drip particles)
**Stats:** 30 HP / 5 ATK / 2 DEF / Speed 2 (3 on water tiles) / Range 3 (trident)
**Size:** 2×2
**Theme:** A drowned king who floods the arena and commands the water. The fight becomes a battle against the rising tide as much as the boss itself.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Tidal Wave** | — | Floods a 2-tile-wide column with water tiles for 3 turns. Water tiles reduce Speed by 1. | Water particles rise from the target tiles. Rushing water sound. |
| **Trident Storm** | — | Throws 3 tridents in a spread arc (each deals 4 damage at range 4). | Raises trident arm, 3 target tiles shimmer blue. |
| **Riptide Charge** | — | If standing on a water tile, charges up to 4 tiles through water, dealing ATK+3 and knocking back 2 tiles. | Spinning water vortex forms around boss. |
| **Call of the Deep** | — | Summons 1–2 Drowned (9HP/3ATK) from water tiles. Can only summon onto water tiles. | Bubbles rise aggressively from random water tiles. |

**Phase 2 — "Deluge" (≤50% HP):**
- Half the arena becomes permanently flooded water tiles
- Tidal Wave width increases to 3 tiles
- Call of the Deep summons 2–3 Drowned and triggers every 2 turns
- Boss gains permanent +2 ATK while standing on water
- Riptide Charge can change direction once mid-charge

**Design Intent:** Environmental transformation boss. The arena CHANGES as the fight progresses — from mostly dry to mostly flooded. The player needs to stay on high ground (non-water tiles) to maintain mobility while the boss thrives in water. Forces the player to think about terrain, not just the enemy.

---

### 6. Scorching Desert — "The Sandstorm Pharaoh"

**Entity:** Husk (custom — gold and lapis headdress, golden sword, sand swirl particles)
**Stats:** 25 HP / 6 ATK / 1 DEF / Speed 2
**Size:** 2×2
**Theme:** Changed from Creeper to Husk — a cursed desert king who plants traps, buries enemies in sand, and controls the battlefield through deception and area denial. The floor is never safe.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Plant Mine** | — | Places an invisible mine on a tile (faint sand shimmer to observant players). Triggers on contact for 6 damage. Max 4 active mines. | Barely visible sand particles — reward for paying attention. |
| **Sand Burial** | — | 2×2 area becomes quicksand for 2 turns. Units ending their turn on quicksand are Stunned for 1 turn. | Sand shifts and darkens on target tiles. |
| **Sandstorm** | — | 3×3 AoE: deals 3 damage and applies -1 Accuracy (ranged attacks have 50% miss chance) for 2 turns. | Swirling sand pillar builds on the target zone. |
| **Curse of the Sands** | — | Marks the player. For 3 turns, every tile the player moves OFF of becomes quicksand for 1 turn. | Golden eye symbol appears above the player. |

**Phase 2 — "Tomb Wrath" (≤50% HP):**
- Mines are placed 2 per turn instead of 1
- Sand Burial area becomes 3×3
- Boss summons 2 Husks (8HP/3ATK) once (not repeating — this is a pharaoh's guard, not a factory)
- Curse of the Sands now also deals 1 damage per tile moved while active

**Design Intent:** Mind game boss. The arena becomes a minefield — you have to remember or guess where the mines are. Quicksand restricts safe movement, the sandstorm punishes ranged builds. Curse of the Sands is a brilliant reversal — YOUR movement creates hazards. Phase 2 turns the entire floor into a puzzle.

---

### 7. Dense Jungle — "The Broodmother"

**Entity:** Spider (custom — 3×3 footprint, glowing green eyes, web-draped, egg sacs visible on back)
**Stats:** 35 HP / 6 ATK / 2 DEF / Speed 3
**Size:** 3×3
**Theme:** A monstrous spider queen who fills the arena with webs, spawns waves of spiderlings, and uses pounce attacks to close distance. The arena becomes her web.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Spawn Brood** | — | Hatches 2–3 Cave Spiders (3HP/2ATK/Speed 2) from egg sacs placed around the arena at fight start (3 egg sacs, 4HP each, destructible). Triggers every 3 turns if egg sacs remain. | Egg sacs pulse and crack 1 turn before hatching. Chittering sounds. |
| **Web Spray** | — | Shoots webbing at a 3×3 area. Units inside are Stunned for 1 turn and Slowed for 1 additional turn. | Spider rears up, web particles gather at mouth. |
| **Venomous Bite** | — | Melee attack that deals ATK damage + applies Poison (2 damage/turn for 3 turns). | Fangs drip green. |
| **Pounce** | — | Leaps over obstacles to land up to 3 tiles away, dealing ATK+2 damage in a 2×2 area on landing. | Crouches low, target tiles shake and flash. |

**Phase 2 — "Nest Awakening" (≤50% HP):**
- 3 new egg sacs appear at random empty tiles
- Web Spray also applies Poison (1 damage/turn for 2 turns)
- Pounce range increases to 4 tiles
- Boss gains +2 Speed (total 5)
- Destroyed egg sacs respawn once after 2 turns

**Design Intent:** Action economy boss. If you ignore the egg sacs, spiderlings overwhelm you. If you focus the egg sacs, the Broodmother pounces on you. The stun from Web Spray sets up Venomous Bite or a spiderling swarm. Phase 2 is a panic moment — more eggs appear and everything speeds up. Destroy the eggs early to make Phase 2 manageable.

---

### 8. Underground Caverns — "The Hollow King" (Corrupted Miner)

**Entity:** Zombie (custom — mining helmet with headlamp, glowing pickaxe, ore-encrusted body, green particle aura)
**Stats:** 40 HP / 7 ATK / 3 DEF / Speed 2
**Size:** 2×2
**Theme:** A miner who dug too deep and was consumed by the dark. Uses cave-ins to reshape the arena, summons swarmers from the walls, and weaponizes darkness itself. The cavern is collapsing around you.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Cave-In** | — | Collapses ceiling on a 3×3 area. Deals 5 damage to all units inside and creates rubble obstacles on those tiles. | Dust particles fall from above, tiles flash red. Rumbling sound. |
| **Miner's Fury** | — | Charges in a line (up to 3 tiles), destroying any obstacles in the path and dealing ATK+2 damage. | Pickaxe raised overhead, charge path glows orange. |
| **Swarm Call** | — | 3–4 Silverfish (2HP/1ATK/Speed 3) crawl from arena edges. Silverfish are weak but fast — they waste the player's AP to clear. | Stone blocks at arena edges crack and crumble. |
| **Lights Out** | — | Every tile more than 3 tiles from the player goes completely dark for 2 turns. Enemies in darkness gain +2 ATK. | Torches/light sources flicker wildly. Deep rumbling. |

**Phase 2 — "Total Collapse" (≤50% HP):**
- Cave-In happens every 2 turns automatically on random 2×2 sections (the arena is literally closing in)
- Lights Out is permanent — only tiles within 3 of the player are visible
- Boss gains Regeneration (2 HP/turn) while in darkness (always, since Lights Out is permanent)
- Silverfish spawn continuously from destroyed rubble tiles

**Design Intent:** Survival horror boss. The arena shrinks from Cave-Ins, darkness limits visibility, and silverfish are a constant drain on your resources. Miner's Fury destroys the rubble the boss created, opening paths but also threatening the player. Phase 2 is oppressive by design — the cavern is collapsing and you need to close this fight fast or run out of room.

---

### 9. The Deep Dark — "The Warden" (Existing + Enhanced)

**Entity:** Warden (keep existing model — sculk particles, heartbeat sound)
**Stats:** 50 HP / 8 ATK / 4 DEF / Speed 3
**Size:** 2×2
**Theme:** The apex predator. Blind — hunts entirely by vibration. Already has phase transition (Enrage at 50% + Sonic Boom). Adding vibration-based targeting, sculk terrain mechanics, and darkness abilities to make the fight feel like a genuine horror encounter where sound is your weapon and your weakness.

**Core Mechanic — Vibration Sense:**

The Warden is **blind**. It doesn't track the player by position — it tracks by **vibration**. This is the defining mechanic that separates the Warden from every other boss in the game.

- **Projectile Distraction:** The player can throw any projectile (snowball, egg, ender pearl, arrow, etc.) at an **empty tile**. This requires allowing empty-tile targeting for projectiles during the Warden fight. The tile where the projectile lands becomes the Warden's **vibration target**. On its next turn, the Warden **prioritizes moving toward and attacking the vibration target** instead of the player. If it reaches the tile and finds nothing, it idles for 1 turn (confused/sniffing), then resumes hunting the player.
- **Movement Vibrations:** The player's own movement creates vibrations. Moving 3+ tiles in a single turn causes the Warden to **lock on** — ignoring any previous projectile distraction and turning toward the player's current position. Standing still or moving only 1–2 tiles keeps you quieter.
- **Vibration Priority:** Vibrations have a hierarchy: Projectile impact (highest) > Player movement 3+ tiles > Shrieker trigger > Player movement 1–2 tiles (lowest). The Warden always chases the highest-priority vibration from the previous turn.
- **Visual Feedback:** When the Warden is tracking a vibration, a faint pulse-ring animation radiates from the source tile. A small ear icon on the Warden's health bar shows what it's currently tracking: 🎯 projectile, 👣 footsteps, or ❓ lost/confused.

**Why this matters:** This turns the Warden fight into a stealth puzzle boss. You can't just tank-and-spank — you have to manage your movement carefully, use projectiles to buy safe turns, and time your attacks around the Warden's confused state. Throwing a snowball to bait it across the arena, then sprinting in for a free hit while it's sniffing the empty tile — that's the kind of moment this creates.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Sculk Spread** | — | After the Warden attacks, the tile it attacked from becomes Sculk. Each adjacent sculk tile gives the Warden +1 damage on attacks. Sculk tiles persist for the fight. | Dark tendrils seep into the ground after each melee swing. |
| **Sculk Shrieker** | — | Places a Shrieker on a tile. If any unit moves within 2 tiles of it, it triggers — dealing 3 damage to the triggering unit and creating a high-priority vibration that overrides projectile distractions. Shriekers have 3HP and can be destroyed. | Sculk tendrils visibly grow on the tile. Vibration particles. |
| **Darkness Pulse** | — | Every 4 turns, ALL tiles go dark for 1 full turn. Player can only see 2 tiles around them. Enemy actions during darkness are hidden. During darkness, the Warden gains +2 Speed (total 5) — it hunts faster when you can't see. | Heartbeat sound intensifies dramatically over several ticks. |
| **Tremor Stomp** | — | If the Warden is confused (reached a distraction tile and found nothing), instead of idling it has a 50% chance to stomp — sending a tremor in all 4 cardinal directions (3 tiles each). Deals 3 damage and reveals the player's exact position regardless of vibration tier. | The ground cracks radiating from the Warden. Deep rumble. |
| *Existing: Melee* | — | Devastating melee attack, speed 3 approach. Generates a vibration event at the attack location. | Ground trembles on approach. |
| *Existing: Sonic Boom (Phase 2)* | — | Range 4 attack that ignores line of sight. Targets the highest-priority vibration source, not necessarily the player. | Vibration particles fire outward. |

**Phase 2 — "The Ancient Awakens" (≤50% HP, existing enrage +):**
- Existing: +3 bonus damage, Sonic Boom unlocked
- NEW: Sculk tiles regenerate the Warden 1 HP/turn per sculk tile on the field (rewards the sculk spread mechanic)
- NEW: Darkness Pulse triggers every 2 turns instead of 4
- NEW: 2 Sculk Shriekers are placed automatically when Phase 2 begins
- NEW: Projectile distractions only work for **1 turn** instead of until the Warden reaches the tile — it learns faster
- NEW: Movement vibration threshold drops to **2+ tiles** (you must move even more carefully)
- NEW: Tremor Stomp always fires when confused (no longer 50% chance) and range extends to 4 tiles per direction

**Design Intent:** The Warden fight is a stealth-action puzzle boss. The vibration system makes it unlike any other fight — you have to think about SOUND, not just positioning. Throwing a snowball to bait it away, sneaking in for a hit, then freezing when it turns around — that's the Warden fantasy. Sculk Spread punishes fighting in one spot. Shriekers punish careless movement by creating vibrations that override your distractions. Darkness Pulse turns it into genuine horror where a faster, angrier Warden is hunting you by sound and you can't see it coming. Phase 2 tightens every window — distractions expire faster, quieter movement is required, and Tremor Stomp guarantees the Warden WILL find you eventually. You can't hide forever.

---

## Nether Bosses

---

### 10. Nether Wastes — "The Molten King"

**Entity:** Magma Cube (custom — massive, golden-veined core visible through magma cracks, lava drip particles)
**Stats:** 35 HP / 7 ATK / 2 DEF / Speed 2
**Size:** 2×2 (Medium splits are 1×1, Smalls are 1×1)
**Theme:** Splitting, lava trail, and area denial. The arena becomes a lava field. Killing the boss wrong creates more enemies.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Magma Eruption** | — | Leaps to a target tile, dealing 5 damage on landing in a 3×3 AoE and creating a ring of fire tiles (2 turns) around the landing zone. | Red reticle marks the target tile 1 turn ahead. Boss compresses. |
| **Split** | — | When the boss takes more than 8 damage in a single hit, it immediately splits into 2 Medium Cubes (15HP/4ATK) as a reactive defense. Mediums split into 2 Small Cubes (5HP/2ATK) on death. | Cracks appear across the boss's body when damaged. |
| **Lava Trail** | — | All movement by the boss leaves fire tiles that last 2 turns. Passive ability — always active. | Ground glows beneath the boss on approach. |
| **Absorb** | — | Can merge with an adjacent Small or Medium cube, healing for the absorbed cube's remaining HP. Sacrifice a minion to heal. | Boss reaches toward the adjacent cube. Pulling particles. |

**Phase 2 — "Meltdown" (≤50% HP):**
- Lava Trail tiles become permanent — the arena floor is progressively destroyed
- Magma Eruption fire ring is also permanent
- On death (if not split), explodes in a 5×5 AoE dealing 8 damage — the player MUST split it or face the death explosion
- Absorb can target cubes up to 2 tiles away (magnetic pull)

**Design Intent:** Damage management boss. Hit it too hard and it splits into more problems. Hit it too soft and fire tiles consume the arena. The Absorb mechanic means you also need to kill the splits or the boss heals back up. Phase 2's death explosion creates a genuine dilemma — do you split it and fight the adds, or burst it down and eat the explosion?

---

### 11. Soul Sand Valley — "The Wailing Revenant"

**Entity:** Ghast (custom — spectral chains, blue soul fire tears, darker model with ghostly trail)
**Stats:** 40 HP / 8 ATK / 1 DEF / Range 6 / Speed 1 (but teleports)
**Size:** 2×2
**Theme:** Artillery boss that pins the player with soul chains, rains fire from safety, and screams to debuff. The player has to find a way to close distance against a hovering spirit that keeps teleporting away.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Soul Barrage** | — | Fires 3 soul fireballs at different tiles. Each deals 4 damage and leaves a soul fire tile (1 damage/turn for 2 turns). | 3 target tiles are marked with blue flame 1 turn ahead. Wailing cry. |
| **Wail of Despair** | — | AoE debuff: all entities within 4 tiles of the boss lose 2 ATK for 2 turns. | Ear-splitting scream, shockwave particles ripple outward. |
| **Soul Chain** | — | Tethers to the player with a spectral chain. While chained, player takes 2 damage per turn. Chain breaks if player moves more than 5 tiles from the chain anchor point (the tile where it was cast). Lasts until broken. | Ghostly chain fires toward player, anchor point glows blue. |
| **Phase Shift** | — | Teleports to a random corner or edge of the arena. Used when player gets within 3 tiles. Once per turn. | Boss becomes translucent briefly before vanishing. |

**Phase 2 — "Requiem" (≤50% HP):**
- Soul Barrage fires 5 fireballs instead of 3
- Wail of Despair also applies 1-turn Slowness
- Two Soul Chains can be active simultaneously
- Soul fire tiles from barrage are permanent
- Speed increases to 2

**Design Intent:** Chase boss. The Wailing Revenant punishes you for staying at range (soul barrage carpet bombing) and punishes you for getting close (phase shift teleport). Soul Chain forces awkward positioning — you have to move away from the anchor to break it, which might push you into fire tiles. The player needs to cornertrap it — predict the Phase Shift and cut off escape routes.

---

### 12. Crimson Forest — "The Crimson Ravager"

**Entity:** Hoglin (custom — massive crimson fungal growths covering body, red-mist breath, oversized tusks)
**Stats:** 45 HP / 8 ATK / 3 DEF / Speed 3
**Size:** 2×2
**Theme:** Pure aggression. The most relentless boss in the Nether — charges, gores, tramples, and never stops attacking. Fungal terrain heals it. The player needs to kite and burst.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Gore Charge** | — | Charges in a straight line (up to 4 tiles), dealing ATK+3 damage to the first unit hit and knocking them back 3 tiles. Destroys obstacles in its path. | Scrapes the ground, snorts crimson particles. Charge path turns red. |
| **Fungal Growth** | — | Corrupts a 3×3 area with crimson nylium. Standing on corrupted tiles heals the boss 2 HP/turn. Corrupted tiles persist for 3 turns. | Fungal spores drift from the boss and settle on target tiles. |
| **Rampage** | — | Attacks every adjacent tile simultaneously (up to 8 tiles around the boss). Deals ATK damage to each. | Eyes glow bright red, stance widens, snort cloud bursts. |
| **Summon Pack** | — | Calls 2 Piglins (8HP/4ATK/Range 3) that provide ranged support. Once per fight. | War horn sound, nether portal particles at summon tiles. |

**Phase 2 — "Blood Frenzy" (≤50% HP):**
- Gains +4 ATK permanently
- Gore Charge leaves fire tiles in its wake
- Rampage hits a 2-tile radius (attacks all tiles within 2 of the boss)
- Cannot be knocked back
- Speed increases to 4

**Design Intent:** DPS check boss. Fungal Growth means the fight gets harder the longer it goes — if the boss is standing on corrupted tiles, it out-heals chip damage. Gore Charge is devastating but telegraphed and dodgeable. Rampage punishes melee-range combat. The player needs to kite, burst during safe windows, and destroy corrupted tiles (stand on them to prevent healing, or avoid them to deny the boss access). Phase 2 is a genuine sprint to the finish — +4 ATK and Speed 4 means you are RUNNING.

---

### 13. Warped Forest — "The Void Walker"

**Entity:** Enderman (custom — void particles, glowing purple rift in chest, extra-long limbs, teleport afterimages)
**Stats:** 50 HP / 9 ATK / 2 DEF / Speed 3 (+ free teleports)
**Size:** 2×2
**Theme:** Reality distortion. Portals, clones, positional manipulation. The boss doesn't just teleport — it weaponizes teleportation. The arena's spatial rules stop applying.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Void Rift** | — | Opens a portal pair on 2 tiles. Any entity that steps on one is teleported to the other. Lasts 4 turns. Max 2 rift pairs active. | Reality cracks open with purple lightning on both tiles. |
| **Mirror Image** | — | Creates 2 clones (8HP/3ATK each). Clones look identical to the boss but take double damage. They use basic melee attacks. | Boss splits into shadowy copies — brief distortion effect. |
| **Phase Strike** | — | Teleports behind the player and attacks in the same action. Cannot be dodged by positioning. Deals ATK damage. | Static noise and a brief purple flash around the player. |
| **Void Pull** | — | Pulls the player 2 tiles toward the boss. If the pull moves the player onto a Void Rift, the rift activates. | Vortex particles spiral toward the boss from the player. |

**Phase 2 — "Reality Shatter" (≤50% HP):**
- Mirror Image creates 3 clones instead of 2
- Void Rifts are permanent
- Phase Strike hits twice (teleports behind, attacks, teleports to a different side, attacks again)
- Boss blinks to a random tile as a free action at the start of every turn
- Void Pull range extends to 3 tiles

**Design Intent:** The trickster boss. Void Rifts force the player to think about the spatial layout — a rift near the boss can actually work FOR you (step on it to escape) or AGAINST you (get pulled onto it and teleported into danger). Mirror Images waste your AP if you attack the wrong one. Phase Strike is the "no safe position" ability that keeps pressure high. Phase 2 with permanent rifts and constant blinking makes this a chaotic, high-skill fight.

---

### 14. Basalt Deltas — "The Ashen Warlord"

**Entity:** Wither Skeleton (custom — nether-brick crown, flaming sword, blackstone pauldrons, wither rose particle trail)
**Stats:** 55 HP / 10 ATK / 4 DEF / Speed 3
**Size:** 2×2
**Theme:** The Nether's general. Commands fire and wither, summons elite guards, and hits like a truck. This is the gatekeeper boss — the last fight before the End. It should be the hardest Nether boss by a significant margin.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Wither Slash** | — | Melee attack that deals ATK damage and applies Wither (max HP reduced by 3 for the rest of the fight). Wither stacks. | Blade drips black particles on the backswing. |
| **Summon Blaze Guard** | — | Calls 2 Blazes (10HP/5ATK/Range 4) as fire support. Uses every 4 turns. Max 3 alive. | Fire erupts from the ground at summon tiles. Horn blast sound. |
| **Ash Storm** | — | Blankets a 4×4 area in volcanic ash. Reduces Speed by 1 and ranged accuracy by 50% for 2 turns. | Ash particles swirl aggressively over the zone. |
| **Fire Pillar** | — | Strikes the ground creating a pillar of fire — 1 tile wide, 4 tiles long line. Deals 6 damage to everything in the line. | Ground turns dark orange along the line. Hissing fire sound. |

**Phase 2 — "Warlord's Command" (≤50% HP):**
- Wither Slash hits a 180° arc (hits all 3 tiles in front of the boss)
- Summons 2 Wither Skeletons (12HP/6ATK, also apply Wither) instead of Blazes
- Speed increases to 4
- Wither from all sources stacks twice as fast (max HP reduced by 4 per hit instead of 3)
- Fire Pillar can be cast in an X pattern (two crossing diagonal lines)

**Design Intent:** The final exam before the End. Tests everything — can you handle summons (Blazes + Wither Skeletons), area denial (Ash Storm + Fire Pillar), a debuff that permanently weakens you (Wither stacking), AND a fast aggressive melee boss? Wither stacking is the timer — every hit you take permanently reduces your max HP. You cannot play passively. Phase 2's Wither Skeleton summons create a horrifying situation where multiple enemies all apply permanent debuffs.

---

## End Bosses

---

### 15. Outer End Islands — "The Void Herald"

**Entity:** Enderman (custom — storm particles, crackling lightning, void-edge glow, floating debris orbit)
**Stats:** 55 HP / 10 ATK / 3 DEF / Speed 3
**Size:** 2×2
**Theme:** The void is the weapon. This boss pushes you toward instant-death void edges, collapses the arena itself, and uses lightning to punish clumped positioning. The arena shrinks. Time is against you.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Void Gale** | — | Pushes ALL entities 2 tiles in one direction (always toward the nearest void edge relative to the player). Affects boss's own summons too. | Wind particles build with directional arrows on tiles. Howling sound. |
| **Lightning Strike** | — | Marks a tile. Next turn, lightning hits in a + pattern (center + 4 adjacent). Deals 6 damage each tile. | Electricity crackles on the target tile for 1 full turn. |
| **Platform Collapse** | — | Permanently removes a 2×2 section of arena floor, turning it into void. This makes the arena smaller. | Target tiles crack and darken 1 turn ahead. Falling debris sound. |
| **Blink Assault** | — | Teleports and attacks 3 times in quick succession at 3 different tiles (player's current tile + 2 adjacent). | Afterimages appear at each target location briefly. |

**Phase 2 — "Oblivion" (≤50% HP):**
- Platform Collapse triggers automatically every 2 turns (the arena is SHRINKING)
- Void Gale pushes 3 tiles instead of 2
- Lightning Strike marks 2 tiles simultaneously
- Speed increases to 4
- Every 3 turns, summons 2 Endermites (2HP/2ATK) at the player's position

**Design Intent:** The clock is ticking — literally. The arena gets smaller every 2 turns in Phase 2. Void Gale pushes you toward the edges. Lightning blocks safe tiles. This boss is pure tension — you're fighting the boss AND the disappearing floor. If the fight goes too long, there's nowhere left to stand. Forces aggressive play and smart positioning near the arena center.

---

### 16. End City — "The Shulker Architect"

**Entity:** Shulker (custom — golden shell with purple runes, crown-like protrusion, command particles)
**Stats:** 50 HP / 9 ATK / 4 DEF / Range 5 / Speed 1
**Size:** 2×2
**Theme:** Turret warfare. This boss builds its defenses, deploys turrets, creates bullet hell, and hides behind its shell. It's a siege — you have to dismantle its infrastructure piece by piece while dodging constant projectile fire.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Bullet Storm** | — | Fires 4 homing bullets in one turn. Each deals 3 damage and applies Levitation (moved 1 tile in a random direction at end of turn) for 1 turn. | Shell opens fully, core glows bright. Charging sound builds. |
| **Deploy Turret** | — | Spawns a stationary Shulker turret (6HP, 1 bullet/turn at range 4, deals 2 damage). Max 3 active turrets. | Purple launch particles where the turret will appear. |
| **Fortify Shell** | — | Closes shell, gaining 80% damage reduction for 1 turn. Used reactively when damaged. | Shell snaps shut with a heavy clunk. |
| **Teleport Link** | — | Teleports to any active turret's position (destroying that turret). Used to escape melee range. | Energy beam links boss to target turret before teleport. |

**Phase 2 — "Defense Protocol" (≤50% HP):**
- Bullet Storm fires 6 bullets
- All turrets gain Levitation bullets (same as boss)
- Fortify Shell also returns 50% of incoming damage to the attacker
- Turret limit increases to 5
- Turrets auto-deploy every 3 turns if below the cap

**Design Intent:** Infrastructure siege. The boss itself isn't that dangerous alone — but surrounded by turrets, the bullet spam becomes overwhelming. Players must prioritize: destroy turrets to reduce bullet count, but every AP spent on turrets is AP not spent on the boss. Fortify Shell is the frustration mechanic — you time your burst and the boss shells up. Teleport Link lets it escape to a turret, making turret placement strategically important. Phase 2 turret auto-deploy means you can never fully clear the turrets; you have to balance between the two.

---

### 17. Chorus Grove — "The Chorus Mind"

**Entity:** Enderman (custom — chorus plant growths, magenta bioluminescent glow, tendril-like arms, pulsing core)
**Stats:** 60 HP / 12 ATK / 3 DEF / Speed 2 (+ chorus teleport)
**Size:** 2×2
**Theme:** A hive-mind entity fused with the chorus plants. The arena comes alive — chorus plants grow, spread, entangle, and explode. The boss teleports between them like a network. Destroying the plants weakens the boss. Ignoring them gives it total map control.

**Abilities:**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **Chorus Bloom** | — | Grows chorus plant obstacles on 4–5 tiles. The boss can teleport to any chorus plant tile as a free action. Plants have 4HP and are destructible. | Seeds scatter and embed in target tiles. Growth particles. |
| **Entangle** | — | Tendril roots in a 2×2 area. Entities inside are immobilized for 1 turn and take 4 damage. | Tendrils visibly snake upward from the ground in target area. |
| **Chorus Bomb** | — | Lobs a chorus fruit at a tile (range 4). Explodes in 2×2 AoE dealing 5 damage and teleporting anyone hit to a random tile. | Arcing purple projectile with obvious trajectory. |
| **Resonance Cascade** | — | Every chorus plant on the field pulses, dealing 3 damage to all tiles adjacent to any chorus plant. The more plants, the more damage. | ALL chorus plants glow simultaneously. Harmonic hum. |

**Phase 2 — "Overgrowth" (≤50% HP):**
- Chorus plants spread — each existing plant grows 1 adjacent plant per turn automatically
- Resonance Cascade happens every 2 turns as a free action
- Entangle area becomes 3×3
- Boss teleports to a random chorus plant at the start of every turn (free action)
- Chorus Bomb teleports the player toward the boss instead of to a random tile

**Design Intent:** Network boss. The chorus plants ARE the boss's power source. More plants = more Resonance damage, more teleport options, more map control. The player has to burn AP clearing plants to weaken the boss, but clearing plants means not hitting the boss. Phase 2's auto-spreading plants create an escalation — if you haven't thinned the garden, Resonance Cascade becomes a full-arena nuke. The teleport-to-chorus-plant system means the boss is always where you don't expect it.

---

### 18. Dragon's Nest — "The Ender Dragon" (Existing + Enhanced)

**Entity:** Ender Dragon (keep existing model)
**Stats:** 80 HP / 15 ATK / 3 DEF / Speed 4
**Size:** 3×3
**Theme:** The final boss. A multi-phase spectacle with End Crystals, dragon breath, and escalating destruction. Already has Swoop and Fury phases — adding crystal shields, breath pools, and arena control to make it a proper final encounter.

**NEW Abilities (added to existing Swoop + Fury):**

| Ability | AP | Description | Warning |
|---|---|---|---|
| **End Crystal Shield** | — | 2 End Crystals (8HP each) spawn at random arena edges when the fight begins. Each active crystal regenerates the Dragon 3 HP/turn. Crystals must be destroyed as priority targets. | Beam visually connects crystals to dragon. Crystals hum loudly. |
| **Dragon Breath Pool** | — | After a Swoop attack, leaves a 3×3 lingering damage zone for 3 turns. Deals 3 damage/turn to anything inside. Stacks if pools overlap. | Purple particles build at the impact zone. Acid sizzle sound. |
| **Wing Gust** | — | Pushes ALL entities 2 tiles away from the Dragon. Used when surrounded or when player is adjacent. | Wings spread wide and glow with energy. Wind sound. |
| **Summon Endermites** | — | Calls 3–4 Endermites (3HP/2ATK/Speed 3) as swarm fodder. Used every 4 turns. | Ground cracks at spawn points, purple particles burst. |
| *Existing: Swoop (Phase 1)* | — | Flies 4 tiles in a cardinal line, damaging everything in path. | Lines up on same axis as player. |
| *Existing: Fury Charge (Phase 2)* | — | Charges 4 tiles + AoE radius 2, applies Weakness. | Roar and glow before charge. |

**Phase 2 — "Dragon's Fury" (≤50% HP, existing + enhanced):**
- Existing: Fury charge + AoE explosion + Weakness
- NEW: End Crystals respawn once each if destroyed (you have to destroy them TWICE)
- NEW: Dragon Breath Pools become permanent
- NEW: Wing Gust pushes 3 tiles
- NEW: Swoop attacks leave fire tiles along the flight path
- Every 5 turns, the Dragon perches on an edge of the arena and does a full-width breath attack (covers an entire row or column). Warning: perches for 1 turn, breath particles gather. This is the dodging moment — move off that row/column.

**Design Intent:** The ultimate fight. Crystals must die first or the Dragon out-heals you (6 HP/turn from 2 crystals). Dragon Breath Pools deny chunks of the arena. Wing Gust prevents camping at melee range. Endermites waste AP. The full-width breath attack in Phase 2 is the signature spectacle moment — see the Dragon land, see the breath charging, and RUN. Phase 2 crystal respawn means the player has to kill 4 crystals total, creating a genuine multi-target priority puzzle.

---

## Boss Rewards

Every boss already drops emeralds (+3 bonus) and has a 35% chance for a Trim Template. Additional unique drops per boss:

| Boss | Unique Reward |
|---|---|
| The Revenant | Rotten Flesh ×16 (trades for emeralds) + unlocks Dark Forest & Snowy Tundra |
| The Hexweaver | Totem of Undying (1 free death save, 1 use) |
| The Frostbound Huntsman | Powder Snow Bucket (usable in combat — creates a slow tile, 1 use) |
| The Rockbreaker | Heavy Core ×1 (mace crafting component) |
| The Tidecaller | Trident (if you don't have one yet) |
| The Sandstorm Pharaoh | Golden Apple ×3 |
| The Broodmother | Cobweb ×8 (usable in combat as stun item) |
| The Hollow King | Diamond ×4 + Enchanted Book (Fortune III) |
| The Warden | Echo Shard ×4 + Swift Sneak III book |
| The Molten King | Blaze Rod ×8 + Magma Cream ×6 |
| The Wailing Revenant | Ghast Tear ×3 + Soul Lantern ×4 (placeable in hub) |
| The Crimson Ravager | Netherite Scrap ×2 |
| The Void Walker | Ender Pearl ×8 + Chorus Fruit ×6 |
| The Ashen Warlord | Wither Skull ×1 (extremely rare — Wither fight potential?) |
| The Void Herald | Elytra Fragment (collect 2 to craft Elytra — combat glider) |
| The Shulker Architect | Shulker Shell ×2 (Shulker Box crafting) |
| The Chorus Mind | Chorus Flower ×4 + End Rod ×8 (hub decoration) |
| The Ender Dragon | Dragon Egg + Elytra Fragment + NG+ unlock |

---

## Implementation Notes

### New EnemyAction Types Needed

| Action | Fields | Used By |
|---|---|---|
| `SummonMinions` | `entityType, count, positions, hp, atk` | Revenant, Hexweaver, Broodmother, etc. |
| `AreaAttack` | `centerTile, radius, damage, effectName` | Blizzard, Rampage, Seismic Slam |
| `CreateTerrain` | `tiles, terrainType, duration` | Ice Wall, Fungal Growth, Quicksand |
| `LineAttack` | `startTile, direction, length, damage` | Fang Line, Fire Pillar, Gore Charge |
| `PlaceTrap` | `tile, triggerRadius, damage, effectName` | Plant Mine, Sculk Shrieker, Glacial Trap |
| `ChanneledAbility` | `warmupTurns, ability` | Dragon full-width breath, Chain Blast |
| `ModifySelf` | `statName, amount, duration` | Fortify, Blood Frenzy |
| `ForcedMovement` | `target, direction, tiles` | Void Gale, Void Pull, Shield Bash |

### Boss AI Architecture

Each boss gets a dedicated AI class extending a new `BossAI` base class:

```
BossAI (abstract)
├── phase: int (1 or 2)
├── turnCounter: int
├── abilityCooldowns: Map<String, Integer>
├── isPhaseTwo(): boolean → checks HP ≤ maxHP/2
├── onPhaseTransition(): abstract → called once when Phase 2 triggers
├── chooseAbility(): abstract → returns EnemyAction
├── decideAction(CombatEntity, GridArena): final → handles phase check → delegates to chooseAbility()
│
├── RevenantAI extends BossAI
├── HexweaverAI extends BossAI
├── FrostboundAI extends BossAI
├── RockbreakerAI extends BossAI
├── TidecallerAI extends BossAI
├── SandstormPharaohAI extends BossAI
├── BroodmotherAI extends BossAI
├── HollowKingAI extends BossAI
├── WardenAI extends BossAI (refactored from current)
├── MoltenKingAI extends BossAI
├── WailingRevenantAI extends BossAI
├── CrimsonRavagerAI extends BossAI
├── VoidWalkerAI extends BossAI
├── AshenWarlordAI extends BossAI
├── VoidHeraldAI extends BossAI
├── ShulkerArchitectAI extends BossAI
├── ChorusMindAI extends BossAI
├── EnderDragonAI extends BossAI (refactored from current)
```

### Warning System Implementation

```
BossWarning
├── warningType: TILE_HIGHLIGHT | GROUND_CRACK | GATHERING_PARTICLES | DIRECTIONAL | GLOW | SOUND
├── affectedTiles: List<GridPos>
├── turnsUntilResolve: int (usually 1)
├── resolveAction: EnemyAction (the ability that fires when warning expires)
├── color: int (ARGB for the highlight)
├── soundEvent: SoundEvent
```

CombatManager tracks active warnings and:
1. Renders warning particles/highlights on the HUD during player's turn
2. Resolves the ability at the START of the boss's next turn
3. Clears the warning after resolution

### Boss Health Bar

Bosses should have a dedicated health bar at the top of the screen (like vanilla Ender Dragon / Wither bar) showing:
- Boss name in bold colored text
- HP bar with phase transition marker at 50%
- Current phase indicator
- Active ability name during telegraphs

### Phase Transition Event

Add a `BOSS_PHASE_TRANSITION` event that:
1. Pauses combat for ~20 ticks
2. Plays a dramatic particle burst + sound effect on the boss
3. Camera briefly zooms toward the boss
4. Displays the phase 2 name (e.g., "§4§lUndying Rage") as floating text
5. Resumes combat

### Biome JSON Updates

Each biome JSON's `boss` field needs updating from a simple `MobPoolEntry` to a `BossDefinition`:

```json
"boss": {
  "id": "plains_revenant",
  "displayName": "The Revenant",
  "entityType": "minecraft:zombie",
  "hp": 20,
  "atk": 4,
  "def": 2,
  "speed": 2,
  "size": 2,
  "aiClass": "RevenantAI",
  "phase2Name": "Undying Rage",
  "phase2Threshold": 0.5,
  "abilities": ["raise_dead", "death_charge", "shield_bash"],
  "rewards": [...]
}
```
