Changelog

0.2.2

Mod compatibility

- MoreTotems support: the mod's seven totems of undying now auto-revive you in combat at 50% health with a unique Craftics effect each, instead of their vanilla potion effects
- Explosive Totem detonates a 4x4 blast dealing 50% of every nearby enemy's max health plus a biome-scaled bonus
- Skeletal Totem marks every enemy for 2 turns (marked enemies take double damage)
- Teleporting Totem warps you to the safest open tile, farthest from any enemy
- Ghastly Totem sets all enemies on fire for 5 turns and grants you Fire Resistance
- Stinging Totem summons 5 allied bees and Rotting Totem summons 3 allied zombies, both bypassing the party cap for the rest of the battle
- Tentacled Totem blinds every enemy for 2 turns, making them fumble their attacks
- Each totem's tooltip is rewritten to describe its Craftics combat effect
- The totems also drop as rare rewards from boss kills, trial chambers, treasure vaults, and the Shrine of Fortune

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
