Changelog

0.2.2

Events and dialogue

- Events now play out in third person: your whole party walks up to the trader together before trading begins, with the camera following each player
- New dialogue system introduces the trader before you shop, with a unique greeting for each of the 8 trader types, text that types out one letter at a time with the speaker's voice, and a click to skip ahead
- After you close the shop, the trader asks if you're done; choosing "No" reopens the shop so you can keep browsing, and the run continues once everyone in the party is finished
- Dialogue is fully data-driven (JSON files under data/craftics/dialogue) and reusable for future NPCs and content
- Shrine of Fortune migrated to the dialogue + cinematic flow: you walk up to the shrine on an approach path, the narrator opens a choiceless intro line (no portrait, no speaker name), and your offering choices come through dialogue buttons. Reward results read back as a narrator line you click to dismiss
- Shrine area now extends an approach walkway out the entrance side and seals everything else with barriers
- New NOTHING reward band on the shrine: small offerings have a 25% bust chance, medium 15%, large 8%. The narrator says "The offering vanishes... nothing remains." when it triggers
- Insufficient emeralds now re-offer the full shrine menu with a narrator preamble instead of bouncing you back to a chat error
- Wounded Traveler uses the same cinematic + dialogue flow as the trader, the villager waits at the back of the path while the party walks up to make their offer, each player sees their own list of foods sorted by quality with a walk-away option, and a narrator line confirms the gift before the run continues
- Wandering Enchanter is now a two-step dialogue, first you pick between enchanting a weapon or enhancing armor, then you choose a specific item from the filtered list with a Back option to switch categories without leaving, and the enchanter no longer offers no-op enchantments on sticks, bamboo, blaze and breeze rods, or corals
- Treasure Vault opens with a narrator dialogue, the party walks in and rings the lodestone centerpiece, choosing to open delivers loot to each player and narrates the count, walking away keeps everyone moving
- Ambush encounters are now a party vote: the narrator says "You see something shiny on the ground" and every member chooses Take it or Leave it. A strict Take majority rolls 50/50 between a rare reward (handed to one random Yes voter, scaled to biome tier) and the ambush combat. A strict Leave majority walks past safely. A tie triggers the ambush
- Removed the legacy event-room button screen, every event now flows through the same dialogue UI for a consistent feel
- Dig Site is now a push-your-luck dialogue minigame: each player sees a Keep brushing / Attempt choice. Brushing has a 10% chance per click of breaking the relic (you're out, empty-handed), but each successful brush raises your Attempt pull chance from 5% by +15% up to 100% (at which point the Keep brushing button drops off and only Attempt remains). Sweet spot lands around 5-6 brushes for the best expected pottery-sherd payout. Replaces the suspicious-block right-click flow
- Removed the Crafting Station event. The room never quite earned its slot in the cascade (it took a between-level turn to give you what a hub crafting table already provides), and its bell-exit mechanic was the only event that broke the dialogue-driven pattern. The probability it occupied is redistributed to the remaining events automatically, so no config edit is needed
- Trial Chamber and Ominous Trial Chamber are now full party-vote dialogue events: every member sees the narrator with "Enter the trial" / "Pass" choices and a tie or strict-majority Enter takes the trial, while strict-majority Pass skips straight to the next level. Disconnects count as Pass. Replaces the previous leader-only Accept/Decline screen entirely; the choice no longer routes through the post-battle screen
- Ominous Trial loot rebuilt to deliver on its "legendary" reputation: each surviving player rolls one heavily-enchanted hero piece (50/50 weapon or armor — netherite-tier sword/axe/shovel/hoe or bow/crossbow/mace for weapons; any material tier for armor, all four slots) carrying 3-5 vanilla-max enchantments from the slot-appropriate pool, plus 2-3 supply consumables (enchanted golden apple, golden apple, golden carrot, cake, or honey bottle). Replaces the previous mixed table that mostly dropped Diamonds, Wind Charges, and Trial Keys
- While a trial or addon-event intro is waiting on the rest of the party, dismissers now see a "Waiting for party..." loading overlay instead of being dropped back into an empty arena
- Addon events can declare optional narrator intro lines via the new EventEntry introLines field. When non-empty, the leader's Accept/Decline screen is gated behind an all-dismiss narrator dialogue, consistent with built-in events. When empty, the legacy chat-line preface still fires. The 6-arg EventEntry constructor remains so addons compiled against 0.2.0 keep loading unchanged
- Abandoned Campsite (from the Artifacts compat layer) now opens with "You find an abandoned campsite. Embers still glow in the firepit. Something feels off." as a narrator dialogue, replacing its chat-msg preface
- Fixed a soft-lock where the trial or addon-event intro would stall the whole party if the would-be leader disconnected between the narrator dismiss and the Accept/Decline screen. The dismiss gate now auto-declines on the party's behalf so everyone proceeds to the next level
- Addon event probabilities now scale by the same pity-timer discount as built-in events, so addons no longer creep up to a disproportionate share of the cascade as the pity counter ramps
- Boss fights now open with a narrator dialogue. All 18 vanilla biomes ship with unique flavor lines matched to their boss (Plains zombie, Dark Forest evoker, Scorching Desert pharaoh, Dense Jungle spider, Stony Peaks vindicator, Snowy Tundra stray, River Delta drowned, Underground Caverns hulk, Deep Dark warden, Nether Wastes magma cube, Crimson Forest archer, Warped Forest enderman, Soul Sand Valley ghast, Basalt Deltas wither, End City shulker, Dragon's Nest dragon, Outer End Islands enderman, Chorus Grove enderman). Every dismisser sees a "☠ Boss Approaching ☠" waiting overlay until the rest of the party clicks through, then the arena loads. Mod authors can register their own per-biome intro at id craftics:boss_intro_&lt;biomeId&gt; (or ship a JSON at data/&lt;modid&gt;/dialogue/boss_intro_&lt;biomeId&gt;.json); biomes without one skip the intro entirely rather than show a placeholder

Arena creation

- Arenas can now be non-rectangular: drop three or more of the new craftics:arena_corner blocks around any shape and ArenaBuilder picks them up as a polygon outline (legacy DIAMOND/EMERALD corner pairs still produce a rectangle, no schematic edits needed). The polygon mask propagates through the single isInBounds gate so pathfinding, enemy AI, VFX, and occupancy all respect the shape automatically
- New /craftics build_arena <shape> [radius] dev command terraforms a flat polygon around the caster and drops the corner markers for you. Presets: square, diamond, octagon, hexagon, plus, cross, l_shape, t_shape. Default radius 8 (17x17 bbox); accepts 2-64. Clears 6 blocks of air above the floor inside the polygon so existing terrain doesn't poke into the playspace

Combat

- Co-op feeding: holding a food item and clicking an adjacent party member feeds them instead of healing yourself
- /home (and /craftics world home) are now blocked during combat for non-ops. Previously a player could chip away at a tough fight and bail out to the hub at full inventory whenever they were about to die, which trivialized any boss attempt. Ops still have access for debugging and world maintenance
- Non-boss mob base damage is now capped per biome pair on a 3 + (biomeOrdinal / 2) curve: first two biomes cap at 3, next two at 4, next two at 5, and so on, so early-game enemies can't shred a fresh player with a near-one-shot hit while late-game still ramps up. Bosses bypass the cap on purpose
- Bosses (and any mob) with Sharpness on their mainhand now get the enchant level subtracted from their base attack at spawn, so the on-hit total (base + Sharpness) stays close to the designed value instead of double-stacking into a one-shot

Enemy AI

- Fixed ghost rider mobs left behind by stack enemies: when a Zombie Stack / Skeleton Horseman / Slime Tower etc. was force-killed (counterattack, smite, sweep, ranged kill) or combat ended with one still alive, the cosmetic baby zombie / skeleton / second slime riding on top stayed in the world as an untracked "ghost mob" — visible but no longer participating in combat. killEnemy now clears stack passengers before the death-shrink animation, the dying-mob discard tick clears them as a safety net, and endCombat sweeps any craftics_stack_visual entity that somehow survived
- Fixed melee mobs going idle while standing right next to you: the wander fallback now attacks when already adjacent instead of shuffling around or sitting still
- Fixed enemy melee in MP playing the attack windup against a non-host party member, then silently dropping the hit because the range check measured to the host's tile instead of the target's. The mob now resolves the closest live party member as its melee target and the damage routes to that player (their armor / shield / dodge / absorb all apply), so attacks that look like they connected actually connect and the right player takes the damage
- Mobs winding up an attack now turn to face the player they're actually swinging at, not just the host. In MP the swing animation lined up wrong when a mob walked over to a non-host teammate
- The whole post-hit chain (bleed / burn / poison / knockback / smite extra hit / thorns / counterattack / death check) now resolves against the actual melee target in MP, not the host. The death handler also fires on the right player when their HP hits the clamp, fixing the "I'm at 0 HP but completely fine" leader-can't-die softlock that happened when the hit landed on a non-host
- Desert boss (Sandstorm Pharaoh) no longer carries Sharpness II on its golden sword. With the base-attack tuning + Fire Aspect already on it, Sharpness on top was stacking into near-one-shots in an early biome. Replaced with Knockback I to keep the "blow you off the dune" feel without the damage spike

Loot

- Bosses now roll each equipment slot at 50% instead of guaranteed dropping their full set
- Per-mob loot (equipment drops, mob heads, generic mob loot tables, goat horns) now goes to the player who killed that mob instead of being handed to every party member; arena/level-completion bonuses still go to everyone

Inventory and economy

- Emeralds awarded by post-battle loot, traveler events, vault events, and shrine jackpots now go straight to your virtual emerald balance instead of taking up inventory slots; trader event emerald grants are unchanged because the trader needs physical emeralds to spend

Multiplayer fixes

- Fixed event routing in MP so non-leader party members' shrine / trader / event UI choices reach the host's CombatManager instead of dropping into their own inactive instance and softlocking the room
- Fixed the post-event return transition treating whoever dismissed the result last as the new combat leader: turn order, leaderUuid, partyPlayers, and PARTY_COMBAT_LEADER mapping now stay anchored on the original host so the wrong player no longer goes first and gets stuck unable to end their turn
- Fixed the Move item being stripped from non-leader party members every server tick: MoveSlotManager now resolves combat state through getActiveCombat so non-leaders see the host's active flag
- Fixed Vitality and Host trim HP bonuses only applying to the host: non-leader party members now get the same HEALTH_BOOST during combat start and level transitions, with HP ratio preserved across levels
- Fixed every other party member's avatar playing the walking animation in place whenever any one teammate moved: CombatAnimations now only triggers the local walk anim when the local player is the current turn-holder
- Added server-driven entity position broadcast for combat moves and cinematic walkers so the host's pawn (and any other walker) is seen traversing by every other observer's client
- Fixed damage flash + screen shake retriggering on every turn rotation in MP: the local HP delta now reads from per-UUID partyHpData instead of the shared current-turn-player HP field
- Fixed turn-end click being ignored after a shrine/trader/event when routing pointed at the wrong CombatManager
- Removed the auto-teleport that snapped the surviving party leader onto the spot where a downed teammate fell. The next-in-line takes over the fight from their own current tile instead of jumping to the corpse
- Status effects are now per-player: bleed, burn, poison, slowness, fire / soaked / freeze and the rest land on the player who was actually hit, tick on their turn, modify their speed, and show under their HP bar. Previously combat effects were stored in a single host-only slot so any hit on player 2 visually applied to player 1 and the wrong player took the DoT damage. Tooltip and HUD effect strips now display each player's own active effects in MP
- Fixed creeper explosions checking distance only to the host's tile, so a creeper detonating next to player 2 with the host out of range did zero damage to anyone. Every party member in the blast radius now takes the hit (and knockback / blast effects) routed through their own armor / shield / dodge stats
- HUD HP bar now reads from per-UUID partyHpData so after a player dies their screen keeps showing 0 instead of jumping to the new turn-holder's HP ("player 1 took player 2's health" no longer happens — that was the host CM's this.player swapping to the next alive member after a death handoff, which then flowed into the shared playerHp broadcast field)
- Fixed the host's combat walk / attack / cinematic animations dying from their own POV after a void death or any respawn. The static animation state (wasAnimating, currentLayer, attack timer, cinematic walk tracking) now resets when the local client player entity changes, so the new respawn entity gets fresh animation handling instead of inheriting stuck flags from the dead entity
- Fixed knockback launching the host across the arena when a mob hit them after a non-host teammate had just taken a turn. The knockback handler read the "from" tile out of arena.getPlayerGridPos() (which only tracks one position — whoever last took a player turn) so on the host's hit it computed knockback origin from player 2's tile and teleported the host to land next to where player 2 was standing. It now reads the actual target player's block position directly, so each knockback comes from where the player really is
- Fixed ranged enemy attacks (skeleton, stray, pillager, blaze, ghast, witch, shulker, drowned-with-trident, breeze, llama, evoker fangs) routing damage to the wrong player in MP — same root cause as the melee fix but the ranged path was missed. Target resolution + post-hit chain (damage, sound, message, flame ignite, punch knockback, overwatch counter, death check) now swap this.player to the actual ranged target for the full chain, so the hit lands on the right player and any mid-chain death handoff doesn't bleed follow-on damage onto whoever the handler swapped this.player to. This was likely producing the "skeleton attacked twice in one turn" reports where player 2 died on the first shot and player 1 took follow-on damage that should have stayed on player 2
- Level Select block now reads from the party leader's progression data (highest biome unlocked, branch choice, discovered biomes) instead of the visiting player's own data. In MP every member opening the level select saw their OWN run state — player 1 saw Desert while player 2 saw Tundra — even though only the leader can queue the match. Every party member now sees the same shared run state on the level select screen

0.2.1

Combat

- Most Nether, End, and many Overworld enemies now resist unarmed Physical attacks, taking half damage from fists and the leather Brawler set, while soft and low-tier mobs (zombies, slimes, silverfish, phantoms, and the like) stay fist-vulnerable

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
