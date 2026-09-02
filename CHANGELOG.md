Changelog

0.4.6

Mounts Work In A Party

Mount state was five plain fields on the combat manager, and in a party that manager is shared - it swaps between members as the turn passes. So every mount in the fight was really one mount, belonging to whoever got there first.

- **Fixed a second player's mount never mounting them.** The auto-mount was gated on "is anybody already mounted", so once one player was riding, everyone else's saddled animal walked on as an ordinary ally
- **Fixed a passenger being dragged onto the mount when their turn began.** A per-tick safety net re-seated whoever was holding the turn, reading a mount flag that belonged to the whole party - so a teammate standing on the ground was teleported onto somebody else's animal the moment their turn started
- **Fixed passengers being unable to get off.** Shift-to-dismount checked the mount YOU own rather than the animal you are ON, so every rider but the owner was told "you are not riding anything" and stayed stuck for the rest of the fight. Combined with the re-seating above, a teammate who got on could not act again
- **A mount is mounted by its OWNER**, not by whoever happens to be holding the turn - which in a party is usually somebody else
- **Nobody is pulled off their own mount** to be a passenger on someone else's
- **Fixed the animal you ride being a blank one of its species.** The arena mob was built from the entity type alone, so your saddled, armoured, named horse fought as an unsaddled stranger - and if the game went down mid-run you were left sitting on that blank one. It now wears its own gear
- **Leaving a fight takes your animals with you.** Walking out with `/home` left your mount standing in the arena still carrying a rider who was no longer in the dimension, and the rest of your party mobs taking turns for a player who had gone. They are returned to their owner's island now - routed by the animal's owner, so a guest's wolf goes to the guest's island
- Every player's mount, its type, its rider and the netherite golem's furnace timer are now per player, the same way status effects already were

Loader

- Built against Fabric Loader 0.19.0

Defence Adds Up Too

The four ways an attack can be shrugged off entirely - Armor Class, Ethereal, a shield block, Gilded Guard - each rolled separately, one after another. Same problem crit had: that is a product, not a sum. A tank wearing all four read 120% on their tooltips and avoided 79.6% of hits.

- **Every avoidance layer is now an addend, rolled once.** A tank can read their gear and know what it does
- **A layer is worth its face value whatever else you have on.** Gilded Guard's advertised 15% used to be worth +3.6% on a build that already had the other three, because it only ever rolled on the hits everything else had already let through
- **Stacking caps at 90%**, so a tank build reaches a real wall without becoming untouchable. An enemy that can never land a hit stops being a fight, and everything else the mod applies pressure with assumes chip damage eventually gets through
- **The layer that gets the credit is picked in proportion to what it contributed**, so a shield covering a quarter of your defence takes the durability hit on about a quarter of avoided attacks. The layers are not interchangeable: only a shield spends durability, and only Armor Class or Ethereal count as a dodge for the Sentinel riposte
- Divine armour's once-per-fight deflect is a guarantee rather than a chance, so it stays out of the stack - and after it, so a hit the layers would have turned aside anyway does not burn it
- Percentages are exact now. Adding 40% and 20% in floating point came to 60.00000000000001%, so a "60%" stack was avoiding a hit rolled at exactly 60%

**This is a substantial buff to defensive builds.** AC 30% with a shield goes from 47.5% to 55%; add Ethereal and it goes from 58% to 75%. Any build with three or four layers now reaches the 90% wall. A single layer on its own is unchanged - a shield alone is 20% (now)

Crit Chance Adds Up, And Overcrit

Crit sources used to be separate sequential rolls: each one rolled in turn, first success wins. That is not addition, and it never behaved like the numbers said. Three 30% sources read as 90% and behaved as 65.7%.

- **Every crit source is now a straight addend, rolled once.** 30 + 30 + 30 is 90. Your sheet is your crit rate
- **A bonus is worth its face value whatever else you have on.** Under the old rolls a source late in the chain was only ever rolled on the hits everything before it had already missed, so "+15% crit" from a set was worth LESS the more crit you already had. That is now simply +15%
- **Nothing is capped. Every whole 100% wraps the counter round again as an Overcrit.** 100% is a guaranteed crit. 200% is a guaranteed crit AND a guaranteed Overcrit. 300% is a guaranteed Double Overcrit, 400% a Triple, and so on
- **Whatever is left over is a roll for one more tier.** At 248% you land two tiers for certain with a 48% shot at a third. At 44% you land nothing for certain with a 44% shot at one - which is an ordinary critical hit, exactly as it has always worked
- **An Overcrit is another crit.** Each one multiplies again: a crit is 1.5x, one Overcrit 2.25x, a Double Overcrit 3.38x, a Triple 5.06x
- Gladiator's +50% crit damage stays a proportional bonus at every tier rather than compounding into an exponential one, so a Gladiator Double Overcrit is 4.5x against an ordinary 3.38x - half again, the same as it is on a plain crit
- Ambush is unchanged and still guarantees the first attack of a fight, still spent only when the roll would not have crit anyway. It grants exactly one tier: a guaranteed crit, never a free Overcrit
- The server-wide baseline crit chance still only applies to players with a crit source of their own, exactly as before

**This is a buff at every level of investment**, because addition beats the old product. Gold set plus full Luck trims goes from 38.6% to 44%; add five Luck points and it goes from 63.2% to 84%. With full Luck gear a guaranteed crit arrives at 7 Luck points where it used to take 13, a guaranteed Overcrit at 20, and a guaranteed Double Overcrit at 32. Luck stat on its own is unchanged: 5 points was 40% and still is, and a build under 100% behaves exactly as it always did.

New Event: The Disenchanter

The enchanter's opposite number, in the same room, with none of the same words. It only ever takes enchantments off.

- **~3% chance between levels**, rarer than the enchanter it mirrors, because meeting one should read as a lucky draw rather than a regular stop
- **Only enchanted gear is listed.** A bare sword is not shown at all: offering it would be a pick that cannot do anything, and would blur what the event is for. Each row says how many enchantments the piece carries, and hovering lists them
- **A second menu opens on the piece you choose**, listing every enchantment bound to it. Click to mark one for removal, click again to unmark, then confirm. The confirm button always names how many will go, and the screen warns you when your marks would leave the piece completely bare
- **Every line the disenchanter speaks is about removal** - it opens by saying it is not an enchanter and that anything chosen is gone for good. The staging is deliberately identical to the enchanter's, so the words are the only thing between a player and losing something they meant to keep
- You can back out at any point, and backing out of a piece clears your marks rather than carrying them onto the next one
- **The menu refuses to act on a list that changed underneath it.** Marks are positions, and the list is redrawn from the live item on every click - so if the piece gains, loses or levels an enchantment between opening the list and confirming it, the event says so and starts you over rather than tearing out whatever now sits in that position
- Every player's picks, marks and open menu are their own, cleared together when they back out, disconnect or finish. Confirming twice, clicking a menu that has moved on, or a stale click arriving after the event ended all resolve to a re-render rather than a second removal
- Truncation is stated rather than hidden: the item list and the rune list both say how many did not fit, and the result line counts the overflow instead of running off the side of the box

0.4.5

Addon API: Selection, Targeting and Grid Highlights

Craftics already knew how to point at things. Pick an ally and the grid stops describing you and starts describing it - green where it can walk on its own speed budget, red on the enemies it can reach - and the click that follows either walks it there along a pathfound route or has it strike. All of it was gated on holding a Lead, and the click arrived on Craftics' own packet with nowhere for an addon to answer it. So a mod whose fights are commanded rather than swung had to rebuild the visible half of the fight inside a screen: a Move button that could not reach Craftics' walk, and attack buttons standing in for grid targeting that was already there and already worked.

- **An addon can select an ally itself**, with nothing in hand. The ally glows for the party, the highlights become the ally's, and the next grid click is an order rather than a swing. The item was never the feature; it was the only way in
- **The command click can be answered by the addon.** Both branches of it: a click on bare ground brings the tile, a click on an enemy brings the tile and the enemy. That is real grid targeting, with Craftics' own highlighting, in place of a row of buttons in a screen
- **Nothing is spent for a command an addon claims** - no AP, no ally turn, no message. Craftics charges for its own walk-or-strike; an addon's move is its own to price. Decline the clicks you have no answer for and the ally still walks and strikes exactly as it did
- **The selection is never cleared out from under the addon.** Craftics clears its own after a Lead command, because that is one order and then the ally is done. An addon issuing several orders from one pick would find the creature deselected mid-gesture, so its selection stands until it says otherwise
- **Craftics' walk and strike are callable directly.** The real walk - pathfound, capped at the ally's move speed, lerped a tile at a time rather than teleported - and the real attack, through the same damage, resistance, typing and accuracy handling the ally's own turn uses
- **An addon can paint the grid.** Its own tiles on any of the four overlays (move, attack, danger, warning), either on top of Craftics' own or instead of them. Replacing matters as much as adding: a mod whose Move button is its own was drawing its targeting on top of the weapon range it was replacing. The lists are rebuilt from scratch on every refresh, which is why tiles an addon drew for itself used to last exactly one click
- **Warnings are addon-writable too** - the flashing red boss telegraph, with the marching arrows that say which way a push, pull or charge travels. And tile flashes, which are pushed the moment they are called in any phase, so an addon can mark what is happening while the enemies are the ones acting
- **The mode pill tells the truth about an addon selection.** It read "COMMAND: MOVE OR STRIKE - 1 AP" for every selection, which is a price Craftics does not take and a pair of options it does not own once an addon has the click
- Overlays and selections are cleared when the fight ends, so nothing an addon left up can leak into the next arena

Nothing here changes a fight that has no addon in it. The Lead works exactly as it did, at the same price, and every refusal it could give is still given by the same code - the walk and the strike were lifted out whole so both routes into them run the identical checks

Turn Order Is An Addon's To Decide

Craftics acted in spawn order. The first mob the level put on the board moved first, every round, for the whole fight, and nothing a creature was could change it. For a mod whose creatures have a Speed stat that was the one number the fight would not read - you could describe a fast creature perfectly and it still waited behind a slow one.

- **An addon can order the round.** It is handed every creature acting that round - enemies and the player's own allies in one list, because they take their turns in one pass - and hands back the order they should act in. A fast enemy can now act before the player's creature, which is the whole point of having a Speed stat
- **Asked fresh at the top of every round**, so a creature slowed or hasted mid-fight is ordered on what it is now rather than what it was when the fight started
- **This was the missing half of a pick-then-resolve round.** The other half already worked: an order left on an ally is obeyed on the creature's own turn rather than the instant the player clicks, and carries its attack type and accuracy with it. So the player picks, the enemy picks when its turn comes, and Speed decides who goes first
- **An addon cannot add a combatant to the round or take one out of it.** A creature left out of the answer still acts, after the ones that were named, in the order it already had; one named twice acts once; one that is not in the fight is ignored. The list a provider returns is what the round walks, and a dropped creature reads as the fight being stuck rather than as an ordering choice
- **A provider that throws is skipped** and the round runs in Craftics' own order. The first provider to answer wins, so two mods cannot both hold an opinion about initiative
- The player's own turn still comes first. They are the one choosing, and choosing is what their turn is for - what Speed decides is whose creature moves first

Nothing changes for a fight with no provider registered: with nothing listening the ordering pass is skipped entirely and the round is walked in spawn order, exactly as before.

Blocks Left Behind In Arenas

The mace and the shovel throw a few blocks of the floor into the air when they hit, and those blocks are meant to come down and land - a slam that leaves nothing behind is a light show. What they were not meant to do is stay. A landed block is a real block, so a swing paved a tile for the rest of the fight, stacked a second block on the first, and left both in the world afterwards. Arenas are read back from whatever blocks are physically in them on your next visit, so anything left behind stopped being litter and became terrain.

- **Landed debris crumbles like a block you placed yourself.** It stands for three turns as real terrain to path around, mine or hide behind, cracking a little deeper each turn, and then breaks. Same countdown, same crack overlay, same removal as a wall block - it just gives nothing back when it goes, because nobody paid for it
- **Anything the grid cannot adopt is put back when the fight ends, wherever it landed.** The cleanup only ever recognised a block that came to rest at the arena's own floor height, in bounds, on a tile it still thought was empty. Everything else - perched on a wall, sitting on top of earlier debris, or clear of the arena entirely - was a block nobody owned, and it stayed for good
- **A landing is now identified by where the block flew**, not by a guess at where it should have come down. The tracker watches the cells each thrown block passes through, so a cell that was empty one tick and holds that block the next is known to be its landing, at any height and anywhere on the map
- **It restores what was there rather than clearing to air**, and only if our block is still the one standing there. A block you mined during the fight, or one a boss built over, has already been answered for and is left alone
- The fix is in the machinery rather than in the two weapons, so it covers everything that throws a block: the boss slam, the collapse rubble, the pillar timbers, and whatever an addon launches

Arenas already polluted by this keep their leftovers - once a block is down it is indistinguishable from real floor. **`/craftics rebuild_arenas`** regenerates them and clears it out.

Fishing Is A Gamble Again

- **30% of casts now catch nothing.** Fishing used to hand out an item every single time, which made it the most reliable loot in the game and the least earned
- **5% of casts hook a Drowned**, scaled to how far the run has come. It surfaces beside you and joins the fight, so casting a line is no longer free of risk
- **The table scales with progress.** On the first biome it is mostly plain fish and treasure is a real surprise; deeper in, the good tiers open up. Both are capped, so even the longest run still pulls up cod
- **You cannot fish water you poured yourself.** A water bucket is reusable and a poured tile costs nothing, so a fishable puddle on demand was an unlimited loot tap that only cost turns. Arena water is still fair game - you just cannot bring your own pond
- The odds live in one place with tests that count every possible roll, because "about a third of casts catch nothing" is a claim about a distribution and not something you can check by reading the branches

Bone Armor Is No Longer Disposable

- **Bone armor now has a 1% chance per piece to shatter when you are hit**, down from 5%. Wooden is unchanged at 5%
- The two brittle sets shared one rate, which suited Wooden - a starter set you expect to lose - but not Bone. Bone is an archer's set built around a quiver that does not empty, so it is worn for whole runs rather than replaced between them, and losing pieces at Wooden's rate meant rarely keeping it long enough for its own perk to matter
- Still brittle, not immortal: 1% is the floor the shatter roll already had, so Luck cannot take Bone any lower and no amount of it makes a piece safe

Performative No Longer Duplicates Potions

- **Fixed Performative minting an item when Special affinity conserved the cast.** The encore handed an item over BEFORE the second cast, assuming it would be spent - but the conserve roll can decide not to consume it, and then the item handed over up front was created out of nothing. On a potion, with both perks on the same cast, that was a reliable duplication
- The refund happens after the encore now, and only for what the encore actually spent. All four combinations of "an item had to be lent" and "the encore spent one" are checked by a test that asserts the player's stock is unchanged, because the bug was one of those four going the wrong way

Loot Stops Calling Itself "Air"

- **Fixed fishing announcing every catch as "Caught: Air!"** The correct item was always delivered; only the message was wrong. Handing a stack to the inventory empties it in place, and the name was read afterwards, so it described what was left over - nothing
- The same bug was in **mining Fortune finds** and **enchanted mob loot drops**, both of which announced "Air" too
- A test now fails on any place that names a stack after delivering it

Anvils Stop Leaving Blocks Behind

- **Fixed spam-clicking a mob with an anvil leaving permanent anvil blocks on the battlefield** - solid to look at, walked straight through, and still there after the fight. Anvil damage lands 14 ticks after the click, so several drops could be queued before the first one touched down. Vanilla turns each falling block into a real anvil the moment it lands, so the second landed on the first and the third on that, climbing out of both the band the cleanup scans and the single position recorded for the end-of-fight restore
- Only one anvil is ever physically in flight per tile now. Extra clicks still cost their AP and their anvil and still deal their damage; they just do not drop a second block onto the first, which nobody could see anyway

Eight Unearnable Feats Now Work

Eight achievements could never be earned by anyone. Each one was fully built - defined, given an advancement, listed in the guide book, with a grant condition reading a counter - and in every case nothing ever wrote to that counter. No error, no log line, no symptom except a player eventually wondering why the feat never came.

- **Alchemist** - five different buffs at once. Sampled when a buff lands and on each player's turn, because the feat is about a peak: anything counting at the end of the fight would be counting buffs that had already expired. Distinct buffs only, so drinking the same potion five times does not earn it
- **Chef's Kiss** - five different foods in one fight
- **Fortress Builder** - five utility items placed. "Placed" means it leaves something standing on the battlefield; eating, drinking and throwing are uses, not construction
- **Pearl Clutch** - pearling out of a boss's telegraphed tiles. It has to be a real escape: in the red before, out of it after, with the attack still winding up. Pearling INTO the red does not count
- **Milk Save** - three or more debuffs cleared in one drink, counted before the wipe, which is the only moment the number exists
- **Fisherman's Luck** - a cast pulling up the top loot tier
- **Lightning Strike** - a Soaked enemy killed by the rod, credited at the strike where what did the killing is still known
- **Drowned** - a Soaked enemy finished with Water damage
- **Mind Games** - a confused enemy killing the enemy it was turned on. Credited when the victim dies rather than when the swing lands, so a confused hit that leaves something on 1 HP still counts when a second confused enemy finishes it
- Dead tracking for a feat that was deliberately removed (Spear Wall, no spears in 1.21.1) is gone rather than left looking like a ninth bug
- A test now fails if any achievement counter has nothing writing to it, including through a forwarder - the way this hid in the first place

Players No Longer Render Bent

- **Fixed a combat animation being left running on the player forever.** The pose Craftics installs during a fight was only retired if the fight happened to end while that player was mid-walk on their own turn. Any other ending - the ordinary one, where the last enemy dies on somebody else's turn - left it applied, and it reports itself as active indefinitely, so nothing ever took it off. The animation rotates the torso and arms and never touches the head, so the body kept its animated angle while the head went on tracking the camera: the player rendered bent, on their island, while walking and looking around, until the game was restarted. The cleanup that existed for this had no callers at all
- **Fixed a player's head and body facing different directions** after a between-level event, which stuck until the game was restarted. A player carries three angles - where they look, where the head model points, and where the torso points - and the six event scenes (trader, barter, shrine, traveler, enchanter, vault) seated two of the three. The head snapped round to face the merchant, the shoulders stayed pointing wherever you were walking when the level ended, and nothing turns a standing player's body back
- **The same bug in the riptide dash** is fixed too: the torso kept its pre-dash angle for the whole flight, so you flew sideways
- A test now walks the source and fails on any place that turns a player without seating all three angles, so a seventh scene added later cannot bring it back

The Enchanter Is Less Likely To Ruin Your Sword

- **Hilt and Dull are now a sixth as likely as anything else** the enchanter can offer. They sat in the pool at exactly the same odds as Sharpness, which on a sword meant roughly one roll in ten quartered or halved your damage on a weapon you cannot un-enchant. That is now about one in fifty
- **They are rarer, not gone.** The enchanter is a gamble, and a gamble with no losing face is just a reward
- **They still appear in the shortlist just as often.** The preview is the warning, and a warning about something that never happens stops being read - so the dread when Dull shows up on the list is unchanged, only the odds of it being the real one moved

Sudden Death Has Teeth

- Sudden Death now deals **flat damage to every player each round**, on top of enraging the enemies. It does not go through armour, Resistance or AC
- **The drain climbs by 1 every round**: 2 on the first, 3 on the next, and so on. A fixed drain is only a clock while the party cannot out-heal it; ramping means whatever they can sustain, it is passed eventually - and soon enough to matter. Twenty rounds of stalling costs 2 HP, thirty costs 77
- Its whole job is to end a fight that has gone on too long, and the only pressure it applied was making enemies hit harder - which a well-built party simply out-tanks. A clock you can armour against is not a clock

Creaking In The Dark Forest

- Fixed live Creaking appearing in Dark Forest arenas and killing players outside the turn system. **They cannot spawn there at all now**, rather than being cleaned up afterwards
- The source was Craftics' own doing: the boss-fight heart is a REAL Creaking Heart block placed in the arena, and a real heart is a live spawner. It kept producing Creakings all fight. The heart Craftics places is now inert - the same block and the same texture, with the properties that drive its ticker turned off
- A Creaking that still arrives inside a live arena is refused the moment it loads, before it ticks or moves or hits anyone. Craftics tags its own the instant before spawning them, so the fight's real creaking is untouched. That backstop exists because the backport gates the inert state on a config a server can switch off, which makes "inert" a strong default rather than a guarantee
- The sweep that was supposed to catch these also never saw them: it asked whether a mob was a HostileEntity, and the backport implements the Creaking as an ANIMAL. Every one already standing in the terrain walked straight through it. It matches by registry id now, like the rest of the mod
- That is a fair choice by the backport for a mob that stands motionless until you look away, and it defeats every class-based test in this mod. Craftics identifies a Creaking by registry id everywhere else; the sweep is now the same
- The same root cause had a second symptom: the list of mobs that may never join a battle party named only the vanilla Creaking, so on the shards where the backport supplies it, one could be walked into a party as an ordinary passive animal. Both flavours are named now
- That list moved somewhere it can be tested. It was pure data trapped inside a class that needs a live registry to initialise, which meant the one thing keeping certain mobs out of a party could never be checked

One Rule For Being Shoved Across The Grid

- Every knockback, pull, sweep, wind burst and sonic boom in the game now walks the grid by the same rule. There were seven hand-written copies of it - in the combat manager, the weapon abilities, the vanilla weapon table and the Deeper and Darker compat - and they had quietly stopped agreeing with each other
- That disagreement is what the arena-border exploit was. One copy killed anything crossing the edge and the rest treated it as a wall, and the only way to discover that was to be exploited by it
- The rule now lives in one place and is unit-tested: the edge stops a push, a hazard is stepped into, a cactus scratches without stopping you, a boss stops at a hazard's rim, and the full footprint of a big mob is checked rather than just its corner. Each of those had at least one copy that got it wrong
- The callers keep what genuinely differs - Crater's slam damage, a cactus scratch, whether a particular hazard is fatal - because those are real differences. How far something travels never was one

The Arena Border Is A Wall, Not A Kill Zone

- Fixed knocking an enemy over the edge of the battle area killing it outright, whatever its health. A Punch bow made that free and repeatable: shoot from range, shove the mob past the line, and it was gone - past every resistance, from somewhere it could not answer
- Ring-outs still exist and are still deliberate. A pit INSIDE the arena is a placed hazard and still kills; the difference is that a designed pit is on the board and the boundary is where the board stops
- Only one of the four knockback paths ever did this. The others already treated out-of-bounds as a wall and killed only on a real void tile, so this was the odd one out rather than the rule

Nobody Spawns Marooned

- Fixed a party member starting a fight stranded on an isolated patch of ground with no legal move for the whole battle, which the Dark Forest's terrain made easy to hit
- Spawn placement checked that a tile had a floor under it, which keeps players out of pits, but never checked they could walk anywhere from it. A lump of terrain across a gap passes the floor test perfectly
- Party members are now placed only on tiles reachable from the leader. The enemy placement has always worked this way; the party never did

Campaign Progress Belongs To The Island

- Fixed helping on someone else's island permanently unlocking biomes on your own. Beating a boss used to write every participant's personal record, and SET it rather than stepping it: a player sitting at biome 2 who joined a party fighting at the leader's frontier of 6 went home with 7 unlocked, having never met bosses 3, 4 or 5. Somebody added to an in-progress island inherited its whole campaign for one fight
- Only the island's own record advances now. Nothing is lost by that, because everything you can see and do was already island-scoped: while you are on someone's island you read THEIR progress and can play every biome it has opened, which is the credit for helping. Go home and you resolve to your own record, exactly where you left it
- This also settles a stranger side of the same line: because the whole unlock was gated on the leader being at their own frontier, helping a newcomer through a boss the leader had already beaten granted absolutely nobody anything. A newcomer on an in-progress island now simply has that island's progress available from the moment they arrive

Guests' Tamed Animals Really Do Come Home Now

- Fixed a tamed animal vanishing when the person who tamed it was not the island owner. The previous fix routed each animal to its owner's island, but a party plays on the LEADER's island and returns there together - and the two halves of that lookup disagreed. The hub COORDINATES resolved a party member to their leader; the island DIMENSION did not. A guest's animal was spawned at the leader's hub coordinates inside the guest's own island: a real position, in a world nobody was standing in
- It only ever worked for guests who had no island of their own, because then the dimension lookup failed and fell back to the right world by accident
- Both halves resolve the same way now, so the position and the world cannot disagree

Splash Potions Reach Your Party

- Fixed a thrown splash potion affecting only the person who threw it. It reached enemies, allies and the thrower and stopped there, so a Regeneration potion thrown into your own party healed one player - which is exactly what "only the one giving the effect heals" looks like from the inside
- Every party member standing in the splash is now caught by it, buff or debuff, the same as the thrower


0.4.4

Modpack Additions

- Ex Barrels (created by me) so we can farm dirt on our islands now.
- EMI and EMI addons for crafting recipes and enchantment descriptions

Battle Party: A Real Keybind, and Glowing Members

- **Adding a mob to your battle party has a keybind now** (**P** by default), so it no longer depends on Shift + Right-Click. Look at a mob and press it. Being a keybind means it is listed in the controls screen, you can move it, and Minecraft arbitrates conflicts between binds itself - none of which was true of a hardcoded modifier and click
- This was a real clash rather than a tidy-up: Carry On picks mobs up with exactly that gesture, so with both mods installed one click both picked the mob up and toggled your party
- **Shift + Right-Click can be switched off** with the new `partyToggleByShiftClick` option. It stays on by default, so nothing changes unless you want it to; turn it off and the gesture belongs to Carry On alone while the keybind keeps working
- **Party members glow**, so you can see which animals are coming with you. It uses the entity's glow flag rather than the Glowing status effect - an effect has a duration and would quietly lapse, leaving a member looking unselected, while the flag persists with the membership it represents. The outline is re-applied wherever the party is synced, so it survives a relog and follows pets home after a run

Killed For Real By An Evoker

- Fixed evoker fangs killing you outright and stranding the run. The evoker's attack spawned three **real** Evoker Fangs entities at your feet - the code called them a visual effect, but that entity ticks on its own and bites for genuine magic damage a moment after the turn resolved. The metered hit landed, your health was clamped to 1 to hold you for the death animation, and then a fang bit for real: vanilla death screen, mid-fight, no way back into the run
- The fangs are drawn with particles now. Nothing in an arena may deal damage on its own schedule - every point of damage in a fight goes through one path so it can be metered, resisted and survived
- **The off-turn damage guard had a hole the same shape.** It only refused damage from a *living* attacker, and Evoker Fangs is a plain entity, so it sailed straight past. Area effect clouds, arrows with no shooter and falling blocks all have that shape too. Any entity's damage is now refused during a fight; damage with no entity behind it, like falling or drowning, is still Craftics' own business and is left alone
- **A fight can no longer end in a vanilla death at all.** If anything does get damage past the metered path, the death is refused and handed to Craftics' own defeat flow instead - the animation, the totem, the party hand-off, the proper end of the run. It is a backstop rather than a fix, so it names the cause in the log: anything it catches is a bug of its own

Two Things That Looked Wrong

- The ten Simply Swords weapons added in 1.70 now describe their Craftics ability on their tooltip. They fought correctly from the start; they just never said what they did, because the ability table and the tooltip table are two lists in two files and only one of them got the new entries
- The Level Select block renders as a proper isometric block in recipes and inventories instead of a flat side-on sprite. Its model came out of Blockbench with no parent, so it inherited none of the display transforms every other block item is drawn with - including the rotation that makes a block look like a block

EMI and JEI Support

- **EMI and JEI both work alongside Craftics now.** Either one's item grid draws in the space either side of the inventory, the same space the stat and damage-affinity panels use, so **only one of them is up at a time**
- **Your existing panel key (U) swaps them.** Turning the stat panels off hands the space to the recipe viewer; turning them back on takes it back. They share one screen region, so "off" for one is the same event as "on" for the other. With no viewer installed the key behaves exactly as it always did and simply hides the panels
- The viewer starts hidden, so a new player still meets the Craftics panels first, and it is held there rather than set once. EMI reads its own config back off disk during startup and again whenever its settings screen is opened, so a single "hide it" at launch was a race against load order - which is why it was coming up visible
- Craftics never writes to either mod's config - the switch is thrown in memory for the session only, so playing with this mod cannot leave your recipe viewer turned off after you stop
- Having EMI and JEI installed at the same time is handled rather than left to luck: the panels stand down for either, and hiding puts both away instead of leaving a second grid behind the first
- Because Craftics holds the viewer's state, the viewer's own visibility keybind no longer sticks. One owner of that switch is what keeps "never both on screen" true against a mod that resets it from disk behind us

One Effect, One Meaning, Whoever Has It

Poison, Wither, Burning and Bleeding used to mean different things depending on whether they landed on you or on a mob. The tick formulas were already shared; what was not shared is now.

- **Damage over time scales with the pool it is eating.** Every DoT tick carries a share of the victim's own maximum health, which mobs have always paid and players never did. This is what stops one rule needing two balance tables: a twentieth of the pool is +1 on a 20 HP player and +20 on a 400 HP boss, so the scaling falls out of whose health bar it is rather than being hand-tuned per side
- **Bleeding is a stack count on both sides now.** It always was on mobs - hit something with a Sharpness V sword and it gains five stacks, which decay one per turn and fade as they go. On a player it was a duration that *replaced itself* on every application, so a mob hitting you five times left bleed exactly where it started while the same five hits on a mob built to five stacks. Player bleed now accumulates and decays identically
- **Vulnerable** is new, and it is Resistance with the sign flipped: +2 damage taken per level. Mobs could always have their guard stripped and players could not, so any effect that wanted to do it to a player had nothing to apply. It counts as a debuff, so cleanses remove it
- Bleed on the two content sources that expressed it as a duration (an instrument song, modded mob weapons) was rewritten as stacks at the same strength, rather than bending the rule to fit them
- **Damage over time is gentler on players than on mobs**, by one factor applied at the very end. A mob only has to survive this fight; your health bar has to last the whole run, so the same number is not the same threat. Poison I now reads 4, 3, 2 a turn where it read 5, 4, 3; burning and wither come down by roughly the same. The formulas themselves are untouched and still shared - the difference between the two sides is a single readable number, not a second ruleset
- **Bleed is capped at five stacks on a player** and never hits harder than the flat version it replaced, at any strength. Sharpness V used to bleed you for 7 a turn for its whole duration; it now peaks at 5 and decays from there. Accumulation still matters - five stacks hurt far more than one - it simply cannot run away on a health bar that small

The guard is a test that gives a mob and a player the same maximum health, applies the same effect, and demands the identical number out of both - no allowance subtracted. That, plus a bleed-curve comparison turn by turn, is what stops the two drifting apart again.

Your Status Effects Are Yours Again in Multiplayer

Four separate places were telling every player about one player. Together they made a single member's debuff look like the whole party had been hit.

- **Chat.** Fourteen second-person lines - "Magma burns you", "Regeneration healed 2 HP", "You breathe in the poison cloud" - went to the entire party. Nine of them live in the per-turn hazard pass, which runs once per member, so a three-player party got one person's poison tick printed three times to everyone. Chat is where players read what happened to them, so this alone was most of the problem. They now go to the player they are about
- **The effects strip.** The combat sync packet carries one effects string and every client stores it as "my effects". Each client is now sent its own, including its own stealth state
- **Screen overlays.** Blindness, Darkness, Poison, Burning and Warped vignettes all read that same shared string, so the turn holder's blindness dimmed everybody's screen. Darkness also hides distant enemies, and it was hiding them from the whole party - the code even documents that as per-player, which it had quietly stopped being
- **The low-health warning.** The red pulsing edge read a shared health value, so the party saw it whenever the turn holder was hurt, while a player actually near death saw nothing. Each client is now sent its own health

Worth saying plainly: the effects themselves were never shared. Regeneration really was healing only the player who had it, and poison really was only damaging the player who ate the thing. The server had it right and everything the player could see had it wrong.


Tamed Animals Actually Reach Your Island Now

- Fixed tamed animals never arriving home. The game said they had been sent, and they had not been
- An animal tamed in a fight is copied home from a snapshot taken of the mob standing in the arena, so the copy carried that mob's entity id. The original was still in the arena at that moment, and a world refuses a second entity holding an id it already has, so the homecoming copy was dropped with nothing but a log line. A pet brought FROM your island never hit this, because its original is removed the moment it is collected for a fight - which is exactly why hub pets came back and tamed ones did not. The arena mob is now retired before its copy is sent
- Fixed a guest's tamed animal landing nowhere in multiplayer. Each animal is routed to its owner's island, but that lookup was skipped whenever the animal already belonged to the player the restore was running for, on the assumption they were standing on their own island. A guest fights inside the HOST's island, so a guest's own animal was spawned into the host's world at the guest's coordinates. Every animal now resolves its owner's island explicitly
- The "sent home to your island" message reports what actually arrived, and says so plainly when something did not. Announcing success regardless is what let this look fine for so long

0.4.3

Everyone Shown the Host's Emeralds

- Fixed the emerald counter in a party showing the host's balance instead of your own. It corrected itself only when you next bought something
- The victory screen, the waiting screen, the event prompt and the defeat screen's remaining-emeralds line now each send the player their own total

A Teammate's Open Container No Longer Stalls the Party

- Fixed an open screen swallowing the end of a fight. One player standing in a chest, a shulker or a backpack when somebody else landed the killing blow never saw the victory screen, the level transition or the next arena - the packets arrived behind whatever they had open, and from their seat the run simply stopped
- Nothing anywhere closed a player's screen at a combat boundary; the only hint the problem was known is that the loot overflow chest refuses to open when another screen already is
- Craftics' own screens are spared, because some of them are the thing being waited on: the post-battle loot screen is what advances the victory flow, so closing it from here would either skip the loot or deadlock the sequence this exists to unblock. Same for a trader mid-event

Backpacked Compatibility

- Battle loot now fills a **worn backpack** instead of stopping at the "Inventory Full" screen. Backpacks sit after the normal inventory and before the overflow chest, so they act as the extra capacity they are rather than filing away the potion you wanted on your hotbar
- Unstackable rewards go in too - a sword that will not fit is exactly what a backpack is for
- Reached entirely by reflection with no compile-time dependency, so Craftics builds and runs with Backpacked absent, and every failure path degrades to "no backpack" rather than breaking loot delivery

Simply Swords 1.70 Uniques

Ten unique weapons added in Simply Swords 1.70 now have Craftics abilities. They were falling through to generic gear inference before this, which can guess a damage number off an item but cannot give a weapon a signature move, so every one of them fought like a plain sword.

- **Bloodwake** - every hit deepens the bleed, and the hit that tops it out bursts the wound, spattering the tiles around it and drinking back a quarter of the blow
- **Wraithmaw** - spectral cutlasses collect above you as you fight, and each swing sends the ones you have hunting for *other* enemies on the field
- **Soulstalker** - abyssal tendrils come out of your back, so they strike what is beside you rather than beside your target. Standing in a crowd is the point
- **Dreadwhisper** - the first hit opens a Corrupted Wound, and striking the same wound again ruptures it for a second full hit and drains the target
- **Gloampiercer** - throws like a trident, and a shadow clone spears a second enemy near the one you hit, leaving Gloam that slows them
- **Riftmane** - spectral chargers gore the whole rank behind your target and scatter it
- **Stormscale** - plants a spectral glaive on the first swing. Every swing after pulses lightning at the planted tile, growing with each arrival
- **Ionbound Stormscale** - the same rod, plus ion cubes that bank up and discharge as a corridor that drags everything inward and paralyses it
- **Dawnquiver** - attacks at 4 tiles and conjures its own arrows of light, so it needs none from your quiver and takes no bow enchantments. Ordinary shots bank Dawn Chorus, and a full quiver of it looses three converging bows that pick their own targets
- **The Devourer** - an abyssal maw opens under your target, drags what is around them into its middle, and grows fatter every time it feeds

Legendary Weapons Missing From Their Own Loot Pool

- Fixed 22 unique weapons never appearing in the legendary section of a weapon lootbox, and being eligible to turn up as ordinary common rolls instead. The legendary pool was a hand-written list that had to be kept in step with the weapons Craftics registers, and it had fallen behind: the list is alphabetical and stops at "s", so every unique added after that point was missing. Twelve older weapons were affected before the ten that arrived with Simply Swords 1.70
- The list does two jobs and a missing weapon failed both, which is why it was invisible: absent from the legendary pool, and absent from the exclusion list that keeps boss-drop weapons out of the ordinary tiered pools
- The Simply Swords half is now derived from the uniques that actually registered, so the two can no longer disagree

Each weapon keeps the shape of its real behaviour rather than a literal copy: a charge-and-spend weapon stays charge-and-spend, and a weapon that wants you standing still still does. All ten join the unique boss-drop pool automatically.

The Enchanter Tells You What Might Happen

- Hovering a weapon or armor piece at the enchanter now lists a few things the enhancement could turn out to be. One of them is what you will actually get
- The enchanter was a blind roll, so getting Dull or Hilt back on a good weapon felt like the game had cheated rather than like a gamble that went badly. Nothing had ever told you a bad outcome was on the table. Seeing Dull sitting among the possibilities before you commit makes the same result read as a bet you took
- The decoys are drawn from the same pool the real result came from, so every option shown is something that genuinely could have happened. Padding the list with impossible entries would let you find the real one by elimination
- The enchanter only offers enchantments that would actually change the item. One it already carries stays on the table only while there is headroom above it, and when it comes up the level is forced past what is already there, so Sharpness III can become Sharpness IV but never Sharpness III again or, worse, a Sharpness II that reads as a downgrade
- A weapon it has nothing left to add to is no longer listed at all, rather than spending your one pick on an item that cannot change. Fully enchanted armor is offered a trim instead
- The roll is decided when the offer is made rather than when you accept, so the list can promise the truth is on it instead of guessing at a roll that has not happened yet. Where the position of the real entry is concerned, it lands anywhere in the list, since always-first or always-last would spoil the roll rather than warn about it

Infinite Arrows on Aimed Shots

- Fixed a bow never running out of ammunition for a player carrying only tipped or spectral arrows. Every "you need arrows" check in the mod counts all three arrow types, but the routine that actually spends one could only find plain arrows, so on any shot that does not resolve tipped and spectral arrows itself the check passed and nothing was paid
- Plain arrows are still spent first, so a tipped arrow is never quietly burned as ordinary ammunition while plain ones are sitting in the bag

Backpacked Augments

Most of Backpacked's augments hook events a turn-based arena never fires, so in Craftics they were dead weight the player had paid for. Seven now do something.

- **Quiverlink** - a bow finds arrows stored in the backpack. Before this, a player carrying a full quiver of them was silently dropped to melee range. Which store empties first follows the augment's own Priority setting, which defaults to the backpack
- **Funnelling** - battle loot goes into the pack *before* your inventory rather than after it, which is what the augment is for. Its item filter is honoured, so only the loot you asked for is diverted
- **Lootbound** - combat rewards are mob drops that never got to be entities, so they are pulled in the same way, following Funnelling's filter. Turning its "mobs" toggle off keeps battle loot out of the pack entirely
- **Immortal** - a Totem of Undying stored in the backpack now saves you in a fight. Hand and offhand totems are still spent first, matching the augment's own rule
- **Reforge** - Mending items inside the backpack repair from combat XP. Craftics awards XP directly rather than dropping orbs, which is the same reason ordinary Mending needed its own implementation here, so the backpack's items simply join that pool
- **Recall** - your backpack's contents survive the defeat coin-flip, provided the augment is linked to a Backpack Shelf. Without a shelf linked it protects nothing, exactly as the augment behaves on a normal death
- **Giant** - the extra space costs one tile of movement per turn. The penalty is dropped rather than leaving a player unable to move at all, so a low Speed stat cannot strand you

Backpacks Are No Longer a Death-Proof Vault

- Backpack contents now take the same defeat coin-flip as your main inventory, at the same rate. They sat outside it entirely before, so a worn backpack was a safe deposit box that a run's defeat could not touch, and battle loot filling one automatically would have made stuffing a backpack the obviously correct way to play
- Recall is the way to buy them back out of it

Standing in the Wrong Square

- Fixed the player's body drifting away from the tile the game is actually playing them on - the move highlights, your reach and enemy pathing all read one square while your character was drawn on another
- The grid is what the rules use and the body is only the picture. They are allowed to disagree for a moment - a dash commits the grid immediately and slides the body over several ticks, which is the point of the animation - but once nothing is animating, a disagreement is drift
- Nothing was correcting it in a solo fight. The turn switch re-derives the grid from the body, which would have papered over it, but that returns early when there is only one player in the queue, so a single player could drift and stay drifted for the whole level
- The body is snapped back to the grid, not the other way round. Moving the grid to meet a drifted body would silently relocate you in the fight; moving the body is only a cosmetic correction
- Skipped entirely while a dash, a scripted walk or a mount is moving you, so nothing fights an animation that is doing its job

Hay Bales Building Walls Instead of Feeding Animals

- Fixed the hay bale being treated as a building block rather than an item with a use. Clicking your own pet with one answered "Something's standing there" and clicking bare ground built a hay wall - so it could neither heal an ally nor tame a llama, both of which it is sold for
- Any block-shaped item that has a real combat use is now that thing rather than a wall. The exclusion list this used to rely on was hand-written, and the hay bale was simply missing from it; asking the item handler instead means an item gains a use and stops being a wall in one edit
- Blue ice and the respawn anchor were quietly in the same position and are fixed by the same change

Feeding Your Animals

- Fixed being unable to heal an animal ally with the food it eats. Raw or cooked meat and rotten flesh now heal a wolf, raw fish a cat, seeds a parrot, wheat and apples a horse, and so on down the list
- Only constructed allies could be healed before - an iron ingot on an iron golem, a snowball on a snow golem - because the registration binds exactly one item to one ally. That shape does not fit an animal, which eats any of a dozen things, so animals had no heal item at all and clicking one answered "you can't attack it"
- Feeding beats eating: raw beef held over your wolf is dinner for the wolf, and the same beef anywhere else is still dinner for you

Tamed Animals Go Home

- Animals tamed **during** a fight now return to your island at the end of that level instead of being carried through the rest of the run. Taming a wolf on level 3 is acquiring a wolf, not drafting one
- Pets you deliberately brought from the hub are unaffected and continue with you as before
- In a party each animal goes to its own tamer's island, not the run host's

Trial Chamber Enemies Hitting Far Too Hard

- **Halved the damage scaling in trial chambers.** Deep into a run a Breeze was swinging for roughly eight hearts
- Health and damage were scaled by the same multiplier, and they are not the same problem: triple health makes a fight longer, triple damage makes it shorter and the player is the one it ends. Health keeps the full surcharge; attack now gets half of it
- Trial spawns also skip the per-biome damage cap that restrains every campaign enemy. That is deliberate, so a trial can be a genuine step up, but it removed the one thing that would otherwise have caught the number growing out of range
- Floored at each mob's own base damage, so early trials are not softened into being easier than an ordinary level, and the Warden still out-hits everything around it
- The formula moved into its own class with no Minecraft types in it, so it can be executed in a test. It previously could not be: touching the trial chamber class at all needs a running game, which is how a number this far out survived

0.4.2.1

Auction Sellers Not Getting Paid

- Fixed a sale never appearing to pay out. The seller was credited server-side, but nothing told either client the number had changed, so both sides watched their balance sit still - the seller saw the item go and no emeralds arrive. The balance was really there, and showed up the next time something else refreshed it
- Fixed the money genuinely vanishing when the seller was inside an Infinite Mode run. A run spends its own wallet and the real balance is parked until it ends, so paying into the run wallet handed them emeralds that leaving the run overwrote. Sales now land in the parked balance, and say so

Party Members Falling Out of Arenas

- Fixed a party member who ended up below the arena floor being killed outright with a totem still in their hand. Only the turn holder was ever offered theirs, and that path skips whoever is acting - so it could only ever kill a non-leader
- Fixed its rescue threshold missing a dug pit entirely: a member who fell in was neither rescued nor killed, and stood in the hole while enemies attacked the tile above
- Spawn placement no longer puts anyone on a tile with nothing under it. It preferred a free hole over an occupied solid tile, and counted the block you stand IN as floor, so a torch over a shaft read as ground
- With no safe tile anywhere it now refuses and logs it, instead of quietly picking the least-bad hole

Two Version Crashes

- Fixed the server dying whenever a non-creative player left-clicked a block, on 1.21.3, 1.21.4 and 1.21.5. A bundled library calls a Minecraft method that gained a parameter in 1.21.3
- Fixed the client dying on world join on 1.21.3, from a mixin guard that said 1.21.2 where it meant 1.21.4
- Neither could fail at build time, so the search is now mechanical: every mixin selector, and every Minecraft method the bundled libraries reference, is checked against each version's real mappings

0.4.2

Noteworthy:
- Ping spots with V
- Center the camera on your character with X
- Biome Atlas in guide book with drop rates
- Guests can start runs

Biome Pages Explain Their Level 4

- Every biome page in the Biome Atlas now describes its **level-4 encounter**: the graveyard whose graves keep raising zombies, the river's rising flood, the void rift eating the arena a ring at a time, and thirteen more
- Each encounter writes its own description, next to the code that runs it, so the page describes the fight that exists rather than the one it was designed from. Several of these deliberately dropped flavour the engine could not support, and a page written from the design brief would document a fight nobody is playing
- Read straight from the mechanic registry on the client rather than synced, since the mechanics register in the mod's main entrypoint and the client already has them. A synced copy would be a second version of the same sentence waiting to disagree
- The page shows the line under exactly the condition the game uses to run the encounter - a registered mechanic on a biome long enough to have a level 4 - so a biome can never advertise a set piece it does not have
- An addon's own mechanic simply gets no line unless it writes one
- **The biome's weather is explained too**, not just named. "Sculk Sensors from level 1" named the thing about to blind your party and said nothing about the Swift Sneak boots that prevent it or the pickaxe that removes it. All eight weather layers now describe themselves the same way the encounters do
- That also gives the Deep Dark a special condition to show. It is the one biome with no level-4 encounter, so its sensors are the whole of what makes it different

Co-op Pings

- A new **ping** keybind, `Z` by default. Hold it and a wheel opens where the cursor is; flick toward an option and let go. Tap it without moving and it sends a plain "look here", so the common case costs one key press and never makes you read a menu
- Not on the middle mouse button, where a ping wheel would normally live: that already drags the tactical camera, and the two cannot share it, since opening a radial menu and panning are the same gesture
- Six options: Look Here, Enemy, Loot, On It, You Take It, Careful. Fixed positions clockwise from straight up, so the gesture becomes muscle memory
- A ping stands as a **2 second beacon pillar** on its tile, in its own colour, with a ring marking the tile itself. A flat ground marker was the obvious choice and the wrong one: the tactical camera looks down at a shallow angle, so a ground highlight is hidden behind the first obstacle or mob between it and you, which is exactly what you would be pinging about
- The tile is captured when the key goes **down**, not when it comes up. Choosing an option means moving the mouse, and a wheel that read the tile on release would mark wherever the flick left the cursor rather than the thing you were pointing at
- Pings go to your party, and back to you, so the marker you see is the one everyone else sees. Incoming pings also print a line in the combat log, since a ping off the edge of the screen would otherwise be silent
- Rate limited server-side, and one live ping per player: pinging again moves your marker rather than adding a second one, so nobody can wallpaper the arena
- Clicking is suppressed while the wheel is open, so a flick can never also spend AP on whatever the cursor passed over
- Fixed a crash (`BufferBuilder was empty`) on the frame a ping expired. The renderer opened a vertex buffer and then found nothing to put in it, which is fatal rather than a no-op; geometry is now collected before any buffer is opened, so the window cannot be hit rather than merely being narrow

Center Camera on Your Character

- A new keybind, `X` by default, that recenters the camera on your character and pulls the zoom in. Works in merchant scenes as well as fights
- Moves the saved view rather than taking a temporary focus, so the camera stays where it was put instead of drifting back a couple of seconds later

Biome Atlas

- The guide book has a new **Biome Atlas** category, sitting directly after the Enemy Bestiary: one page per biome, filling in as your island explores. A bestiary, but for places
- The first page shows the biome's whole roster as a **grid of mob heads**, the same shape the bestiary uses. Click any creature to jump straight to its bestiary entry
- The biome's **boss is named on its own line** above the grid, with its head beside it, and links to its bestiary entry once you have met it. Which creature ends a biome is the most useful thing on the page, and a face in a row of faces does not say it
- **Drops now live on the creature, in the bestiary**, listed with each item's share. The drop table is keyed on entity type, so a zombie drops the same things wherever it is met; putting that list on every biome page containing a zombie would be several copies of one fact, and the first balance change would leave them disagreeing
- **Both loot sources are now covered.** A biome's own pool is rolled once when you clear a level; every enemy also has its own table rolled per kill, and that second source is where most of what you carry out actually comes from. The guide showed only the first, which is why items kept dropping that nothing mentioned. They stay separate, since a share of a biome pool and a share of a zombie's drops are not comparable numbers and merging them would invent a statistic
- Each biome page also carries its level count, day or night, environmental effect and when it starts, and the boss
- Undiscovered biomes are listed but empty. The contents are not merely hidden on the client, they are never sent: a client holding the answer is a client that can show it
- Pages are derived from the live biome definitions and the live drop tables at send time, never authored. Retuning a loot weight, changing what a mob drops, swapping a mob into a pool or adding a whole biome changes the book with it, and a `/reload` re-pushes it to everyone already online
- Long item lists split across as many pages as they need instead of being clipped at the bottom of the page. The creature roster never splits at all - it is a grid
- Discovery is recorded against the island, so party members share one atlas

Clicking the Invisible Wall in a Village Threw You Off the Map

- Fixed clicking near the edge of a merchant scene walking the player through the wall and dropping them out of the world
- The "invisible block" is the barrier that walls a village in so nobody falls off it. The click-to-walk check only asked whether the clicked tile was inside the scene's footprint **rectangle**, and a village is not a rectangle - so a click that landed on a barrier passed the check with nothing behind it to stand on. The walk is a straight-line lerp, so it went through the wall, and the terrain follower fell back to "keep the height you had", leaving the player hovering over the void
- A walk destination is now checked for somewhere to actually stand before the walk starts. Clicking a wall does nothing, which is what clicking a wall should do

Being Moved to a Level Nobody Chose

- Fixed a queued event transition surviving the run that queued it. When a between-level event finishes it starts a 2 second countdown that ends by pulling the whole party into the next level, and **nothing ever cancelled that countdown** - not the end of combat, not a boss win, not going home. It kept ticking, and if anything refilled the pending-level slot before it expired, the party got moved into a level nobody picked
- That matches the report of being sent to another biome after a boss with no Continue pressed. It also explains why the level granted no progress: a transition fired outside the victory path never advances the biome cursor, so clearing that level counted for nothing
- The countdown is now cancelled with the rest of the interlude state, and refuses to fire at all for a player whose run has already ended
- Fixed both abort paths leaving the party behind a permanent "Loading Battle..." curtain. Only a successful transition ever took it down, so being spared an unwanted transition meant being stuck on a loading screen instead

Homing Projectiles Stalling Behind Blocks

- Fixed Grave Skulls, shulker bullets and scarabs **stopping dead** when a block came between them and the player, and never moving again. A seeker re-aims from scratch every turn, so an obstacle that does not move produced a projectile that does not move either
- One case guaranteed it: a seeker lined up on the player's own row or column has a zero-length sidestep, so a single block left it with exactly one candidate and no legal move
- They now follow the shortest clear route instead of stepping greedily toward the player. A greedy step cannot be patched into working here - after one sideways step the toward-the-player move points straight back where it came from, so it rocks between two squares - and a search cannot oscillate, because it follows one path instead of re-deciding every step
- Walled in on every side, a seeker still holds position. That was always the right answer for that case; it was just also being given for cases that had a way through

Arena Floors Being Permanently Edited

- Fixed the shovel (and axe, and hoe) reshaping the arena floor mid-fight. These are vanilla right-click interactions that never went near Craftics' item handling, so nothing refused them - and the arena restore only puts back blocks **Craftics** placed, so a player-made block outlived the fight and was still there on every future run in that arena slot
- Breaking arena blocks is refused for the same reason. Outside a fight your island is still yours to dig up; this applies only inside the arena you are currently fighting in
- Matched on the tool rather than blanket-refusing right-clicks, so every Craftics combat item that places something still works

Locked Out of Raids With No Way Back

- Fixed a player being permanently refused from raids with "finish your run" and no way to finish it. The raid lobby treated the persisted mid-run flag as busy, and that flag stays set for a run merely **paused** in the hub between levels. `/home` deliberately leaves it set so runs stay resumable, so a run left in a bad state locked the player out of every raid until an operator ran `/craftics reset_combat` on them
- Being parked in your own hub is not being busy, and a raid teleports you out and back without touching a paused run. This is the same call `RunInviteManager` already made for starting runs, so the two now agree instead of contradicting each other
- Still refused mid-fight, and still refused at the between-level gates - trader, shrine, intro, dig site, loot - which is what "busy" actually means

/home Yields to Other Mods Instead of Fighting Them

- **`/home` still works.** Craftics keeps it whenever it can, and gives it up automatically the moment another mod wants it - falling back to **`/island`** rather than overwriting a command the player is already using. Both names are configurable (`homeCommandAlias`, `homeCommandFallback`), and `/craftics home` is unchanged and always works
- `/home` is one of the most contested command names there is: FTB Essentials, EssentialsX ports and most teleport mods all claim it. Brigadier does not arbitrate - it merges same-named commands and lets the one registered **last** win - so ownership of `/home` came down to mod load order, which a server owner can neither see nor change
- Detecting that needs two checks, because the conflict can land on either side of us. Before registering, Craftics skips a name something already holds. After every mod has registered, it checks whether the command behind its own name is still the one it handed over - which is the only way to catch a mod that loaded later and merged over the top
- Losing the race costs the familiar name and nothing else: the shortcut moves to the fallback, players already online get the updated command list, and the log says which name went where. Rechecked after `/reload`, which rebuilds the command tree and re-runs the whole race
- Every message that told you to type `/home` now asks what the command is actually called, including the rest-room sign, so nothing instructs players to type a command that does not exist

Phantoms Landing On Top Of You

- Fixed a phantom finishing its swoop **on the player's own tile**. The grid has no way to represent two combatants on one square
- The cause was a snap, not the flight. Before a swoop starts, the mob is moved onto the first tile of its path so the animation begins in the right place - and a phantom standing *next to* its target builds a path whose first tile is the target's tile. It was put there outright, before the dive, before any landing check. Every later guard then read it as already standing there and left it alone, because a mob's own square is the one place it is always allowed to be: the illegal position was created and then protected
- The snap now refuses an occupied tile. Nothing is lost by skipping it - it exists so a boss diving in from off-stage starts at the arena edge rather than sliding diagonally out of its old tile, and such a path begins nowhere near a player
- A move may also no longer **end** on an occupied tile, checked once where every move is started rather than per action. There are three swoop dispatch sites alone, and a rule enforced per action is a rule that holds until somebody adds the next one
- Passing *through* someone is still allowed, since that is what a dive is. Only the landing is pulled back, and damage is unchanged: it is worked out from the full flight path
- Party members count, not just whoever's turn it is - the previous check consulted only the current player, so a phantom could land on everyone else in the party

Golden Carrots Refused At Full AP

- Fixed a golden carrot being refused outright whenever AP was full. It is **both** a heal and an AP restore, so a capped AP bar was denying the larger half of the item: a wounded player at full AP could not eat one at all
- It now heals, and simply says AP was already full. Only a player at full HP **and** full AP gets the carrot handed back, which is the one case where eating it really would do nothing

Lootbox Emeralds Not Leaving Your Balance

- Fixed a lootbox purchase not updating the emerald balance on screen. The emeralds were genuinely spent server-side, but nothing told the client the number had changed, so the player watched their balance sit still - which reads as the purchase never having gone through

Lootboxes Blocked By Lobby Protection

- Fixed lobby spawn protection denying right-clicks on lootbox kiosks. Protection now exempts a registered kiosk where the denial is decided, rather than relying on the lootbox handler being registered first and consuming the event
- The old arrangement worked, but only for as long as nobody reordered two `init()` calls in a file neither class lives in, and it was invisible from the protection code. Everything else in the lobby stays protected exactly as before

Material Crates Giving Enchanted Materials

- Fixed the Material Crate rolling the enchant pass. The polish roll is published in the odds screen for **weapons and armor only**, but it ran on every box type - so a material crate produced enchanted items its own stated odds said nothing about
- Undisclosed odds are the one thing in a loot box worse than a bad drop table, so the rule is now pinned by a test that fails if the roll and the odds display ever disagree again

Pets Deleted Instead of Returned

- Fixed hub pets being **permanently destroyed** when they could not be placed on the arena. A pet is removed from the hub world the moment it is collected for a fight, and the end-of-fight restore is built from the creatures on the field - so one that never got a tile existed nowhere and was gone for good. The game even said it "stayed behind", which was the opposite of what happened
- They are now carried instead: offered a tile again on the next level, and returned to the hub when the run ends
- Fixed the same loss for a pet **benched mid-fight**. Withdrawing an ally takes it off the grid and discards its mob, and the bench was cleared wholesale at each level boundary - correct for the temporary allies an addon fields, fatal for a real animal. Swapping your wolf out for one turn cost you the wolf

One Broken Compat No Longer Silences the Rest

- Compat registrations now run independently. They resolve items out of other mods' registries, so a renamed id turns a lookup into a throw, and one throw took out every compat listed after it - unrelated mods' weapons lost their Craftics stats with no clue why. Because this also runs from the tooltip render, it repeated on every frame
- A failure is logged once, names the compat, and leaves the others working

Guests Could Not Start a Run

- Fixed a party member being unable to start a biome run that the level select block showed as unlocked. Pressing Enter did nothing at all: no message, no error, just a screen that closed
- The lobby has always let any party member host. The block was that it judged the pick against the **starter's own** progression while the level select screen shows the **island's**, so a guest who was personally behind was refused every biome they could see. Both now read the same record
- The refusal was also silent. Being turned down for a locked biome, or for one a datapack removed, now says so
- Biome discovery is recorded on the island as well as the runner, so a guest-hosted run no longer leaves the island (and therefore everyone's level select and guide book) thinking the biome was never visited
Hives and Graves Over Pits

- Fixed hives, graves, sculk sensors and the Deeper and Darker props being placed on VOID tiles - a solid block hanging in a hole, which reads as ground, and which turns a death pit into walkable floor the moment someone mines it. Mob spawns were never affected

Command Permissions

- `/craftics force_event` is now gated behind a permission node. It had no check of any kind, so any player could line up a vault or a trader before every level
- `/lobby`, `/spawn` and `/craftics world lobby` no longer teleport you out of a live fight, which skipped every death penalty. `/home` always blocked it; the other three each had their own copy without the check
- `/craftics difficulty` is owner-only. It writes to the island, so a guest could retune it for everyone

Boards and Lootboxes

- The season, career and infinite boards turn on their own centre instead of their bottom edge, and the season board is placed at your feet. Boards already placed render half their height lower until re-placed
- Punching a lootbox opens it, the same as right-clicking. The `(Punch: Odds)` hint is gone from the label; the odds are a button inside

0.4.1

Addon API: Charging for an Attack

- A new `onAttackApCost` hook, handed the AP an attack costs after the engine's own discounts. Return less to make the swing cheaper, more to make it dearer
- Clamped to `[1, cost]`: a handler can never hand out a free attack, and never charge more than the weapon asked for. Attacks that were already free skip the hook entirely
- This is the shape an AP discount has to take. Adding AP back to the pool after the fact only works for the player the engine happens to be resolving, which is why the Feral Claws bug below existed

Feral Claws Doing Nothing in a Party

- Fixed Feral Claws having no effect for anybody but the party's combat leader. It refunded AP by fetching a combat manager for the wearer, and that lookup returns a fresh idle manager for a player who is not the leader - so the AP was added to an instance nobody was playing and quietly discarded
- Reworked while fixing it: a **50% chance an attack costs 1 AP less**, taken off the price rather than refunded afterwards, instead of a refund on kill. Never below 1 AP total, so a sword or bow is not discounted and the claws pay off on 2 AP weapons - axes and the mace
- Refunding on kill also meant the artifact did nothing at all in the fights where AP is tightest, since a swing that fails to kill is exactly when the AP was needed

Silent Event Arenas

- Fixed raid boss, pillager camp and bastille arenas playing **no music at all**. The soundtrack is chosen by arena biome id, and these three are event arenas whose ids are declared by the level rather than by a biome anyone travels to, so none of them had an entry - and no entry means silence rather than a fallback
- Arena biome resolution now lives in one place the soundtrack can ask, instead of being re-derived. A level whose arena and music disagree was the failure mode

Bone and Wither Armor Affinity

- The Immersive Armors Bone and Wither sets now carry the **Ranged** affinity rather than Blunt and Special. Both sets exist to keep arrows flying - their whole bonus is firing without spending one - so an archer wearing them was levelling the wrong stat to improve what the armor does

Accessories Lost to Infinite Mode

- Fixed Accessories-mod trinkets not coming back after an infinite run. A run stashes your whole island loadout and returns it at the end, and accessories were the one part it never touched: they walked into the run and the loadout came back without them
- Stored as NBT so they survive a server restart mid-run, and a no-op when the Accessories mod is absent

Tab List Power Scores

- Removed the power score drawn beside names in the tab list. The standings boards are where a score belongs

Smaller Things

- The Pet affinity's description now mentions the **+1% spawn egg drop chance** it has always granted. The bonus was real and applied; only the tooltip omitted it
- Two more playtesters credited

LuckPerms and Craftics Commands

- Every Craftics command is now behind a **named permission node** instead of a bare operator check. `hasPermissionLevel(2)` is answered by the vanilla op list before any permission mod is consulted, so LuckPerms could not grant, deny or track a single Craftics command no matter how it was configured - the only way to let someone run one was to make them a full server operator
- Nodes read `craftics.command.<name>`. Grant `craftics.command.*` for the old all-or-nothing shape, or hand out individual nodes so a build-team member can move the lobby spawn without also being able to wipe an island
- `craftics.lobby.bypass` covers editing inside protected lobby areas
- Operator status still works and nothing needs configuring: each check falls back to level 2 when no permission mod is installed, so a server without LuckPerms behaves exactly as it did

Turning the Auction and Lootboxes Off

- `/craftics auction enable|disable` and `/craftics lootbox enable|disable` take either system offline without stopping the server. For an economy exploit found mid-event, the choice used to be leaving it open or kicking everybody
- Stored as **disabled** rather than enabled, so an existing world that has never seen the setting reads as on. A flag that defaulted to off would silently close the auction house on every server that updated
- A closed lootbox reports that it is closed and consumes the click, rather than passing it along to whatever else was listening on that block

Lobby Lootboxes That Could Not Be Clicked

- Fixed lobby lootboxes, and every other right-click interaction, dying silently for players who had been in a cutscene. The client suppresses use and attack while a scene is playing, and five commands that teleport you out of one - `/lobby`, `/spawn`, `/craftics world lobby`, `/craftics world hub` and `/craftics lobby` - moved the player without ever telling their client the scene had ended. The flag stayed set for the rest of the session
- `/craftics reset_combat`, the documented fix for being stuck, cleared combat state only and left the scene flag exactly where it was
- The flag is now cleared on join and on every path that leaves a scene. It reads as a permissions problem from the player's side, which is why it went unreported for so long: nothing is denied, nothing is logged, the click simply does not happen

Black Screen After a Transition

- Fixed the fade-to-black transition holding forever when the packet that should end it never arrived, leaving a player staring at a black screen with no HUD. The hold had no timeout and its reset was never called from anywhere, so the state survived quitting to the title screen and rejoining
- It now fades out after 30 seconds and logs why. A transition that finishes late is a stutter; one that never finishes ends the session

Season Standings

- A new **season board** showing one score per player: chapter placements plus how far they have taken their own island. `/craftics seasonboard spawn` places one, `/craftics seasonboard remove` clears nearby ones
- The score is **derived on every refresh**, never stored. There is no season total in a save file to drift out of step with the values it came from, and no migration to get wrong
- Every part of it only goes up. Emeralds and stat points are deliberately excluded - spending or respeccing would drop a player's rank without them doing anything, which reads as a bug
- Built on the lifetime infinite score rather than the chapter one, because chapter rotation zeroes the chapter score for everyone. A season board that reset itself on every rotation would be measuring the wrong thing
- Offline players are included, which is the point of a season board and the reason it cannot be the tab list
- Each row shows the split as well as the total, so a player can see whether the person above them got there through chapters or through their island
- All seven weights are configurable, so a server can retune what a season rewards without a rebuild

Tied Chapter Placements

- Fixed tied chapter scores banking **different permanent point totals** depending on the order the standings happened to be built in. Placement points never reset, so an arbitrary ordering handed one player a lasting reward over an equal player for a reason nobody could see, and two reads of an unchanged save could disagree about who won
- Ties now share the place and the points, and the next distinct score skips the places they used up: 1st, 1st, 3rd. Everyone at a banked place banks, even where a tie pushes the count past ten

Names on Boards

- Boards show names instead of UUID fragments. A player's name is now recorded on **every join** rather than only by a few infinite-mode paths, so someone who had never taken a run had no name recorded anywhere and appeared on the career board as eight hex characters
- Where a name still cannot be found, the board skips the row rather than printing a UUID - there is no UUID-to-name lookup in the mod, and a UUID names nobody a reader could recognise. A chapter podium line keeps a neutral placeholder instead, since dropping one would misreport who placed

Enemies Arriving Without an Identity

- Fixed raid reinforcements spawning **blank**: a summoned wave arrived with no AI key, no name and no spawn NBT, so a modded creature turned up as an empty instance of its entity type while the creature beside it, spawned by the normal path, was correct
- A saved fight now stores each enemy's AI key and name alongside its stats. Without them, resuming a fight rebuilt every enemy from its entity type, which is fine for a zombie and wrong for any mod that ships one entity type for hundreds of creatures - all of them came back identical
- Resuming now matches saved enemies by **identity first** and position second. Matching on type alone meant a party of six creatures sharing an entity type could each take any other's saved state
- A combatant's display name now reaches the client, so hover text, name plates and capture prompts show what the creature is rather than what its entity type is called

Addon API: A Bench, and Switching Off It

- A field ally provider can now declare **reserves** alongside the allies it fields: creatures carried into the fight with no tile and no mob in the world, fielded only when the player swaps one in. A party larger than the field is the point of having a party - six creatures where three fight is a different game from six all swinging at once, and choosing which three is the interesting part
- A switch costs **1 AP** and puts the incoming creature on the tile the outgoing one vacated. It is refused, with no AP spent, when it is not your turn, when the ally is not yours, when someone is riding it, or when the incoming creature is too large for the tile being freed
- A benched creature keeps everything it was carrying. Bench a wounded, poisoned ally and it comes back wounded and poisoned with its summon timer still running - the combatant itself goes to the bench rather than being rebuilt from its definition, so every scrap of per-fight state rides along, including state added later that nobody remembers to copy. A bench that healed would be the cheapest heal in the game
- **No menu.** Craftics owns what a switch means and the addon owns the screen the player picked from, the same split the combat tools use: read the bench, draw it however you like, ask for the swap. A party-selection UI is your design
- Reserves are addressed by index rather than entity id, because a benched ally has no entity - no mob, no tile, nothing to be found by. That absence is what being benched is
- **Only provider allies get a bench**, and Craftics' own hub pets deliberately do not. A hub pet is a real animal that was standing in your yard and is owed back to it; one that is neither in the yard nor in the fight is an animal in no place at all. Every end-of-fight path filters on exactly the flag that excludes it, and each would have to learn about a bench before one could be safe - where a miss duplicates the animal and a false positive destroys it, since the hub copy was discarded when the party was collected

Accuracy

- Attacks can now **miss**. Accuracy is a per-action multiplier living beside the attack type, on the same slot with the same lifecycle: set for the one action an AI is about to name, cleared before the next decision. A creature whose movepool holds a wild haymaker and a reliable jab needs the haymaker to land less often, and nothing about the defender expresses that
- Distinct from the armor-class dodge, which is the defender's property and answers "did I get out of the way". Accuracy is the attack's own
- **Inert until something asks for it.** Every attack defaults to always landing, and a certain hit deliberately draws no randomness at all - so adding the roll to the damage paths does not shift any other roll in the fight. Had it drawn and discarded, every dodge and crit downstream would have started landing differently on the day this shipped, which reads as a balance change nobody made
- A miss skips the hit outright rather than dealing zero. Zero already means three other things, and a hit that "lands for 0" still fires every on-hit rider behind it - knockback, thorns, weapon debuffs, counters
- The player's existing ranged miss now runs through the same roll, and no longer cancels an empty-tile cone or sweep: that swing was aimed at a tile, the anchor enemy is only there to orient the shape, and there was never anything to miss
- `onMiss` fires. The addon hook has been declared since the effect API existed and was invoked from nowhere

Blinded Pets

- Fixed blindness doing nothing whatsoever to an ally. Enemies could apply it, the HUD showed it ticking down, and no ally code path ever read it - the debuff was applied to a wolf and then simply ignored
- A blinded ally now swings at half accuracy for the turn instead of losing it. Enemies fumble their whole turn to blindness at a check allies never reach, and copying that would have a pet silently forfeit turns, which reads as the game being broken; one that visibly swings and misses reads as blinded

Stale Per-Action Overrides

- Fixed an attack-type override outliving the action it was set for. The clear sat inside the branch that consults the AI, while the two branches that return an action without consulting it - a burning mob running for water, one that has lost sight of its target - skipped it, leaving the override to silently retype every later attack that never asked for one

Addon API: Custom Actions in a Player's Hands

- Fixed an ally handed a custom action doing nothing at all with it. The ally turn resolved only move, attack, flee and idle, and dispatched custom actions from the enemy path alone - so an ordered custom action fell through to idle and quietly burned the turn. An addon's move worked completely in an enemy's hands and landed as a single plain hit in yours, which made the same move strictly better on the side you were fighting
- A handler's damage now routes by **whose action it is** rather than assuming an enemy threw it. An ally's action hits creatures and never falls through to the player; previously anything an ally targeted that was not itself was read as "the player", so a pet's own move hit its owner
- Handlers can name the player outright with `damagePlayer`. Before this the player was whatever was left after ruling out allies and the actor, so an action that meant the player had to pass a throwaway combatant as a token, and an ally's action had no way to say it at all
- Attack-type effectiveness is applied on the handler's behalf for creature targets, so an addon move deals the same damage from either side. It changes nothing for an action that never declared a typing

Commanded Moves Losing Their Typing

- Fixed the ally turn clearing the pending attack type and accuracy **before** reading the standing order. An addon sets both when it issues a command and the order is obeyed a turn later, so every commanded move arrived untyped and at default accuracy - leaving the ally AI as the only way to give an ally a typed move, which is the wrong seam for a player-issued order
- Blindness now **scales** an accuracy the attack already brought instead of replacing it. Overwriting meant a wild haymaker was more likely to land while blinded than while sighted, so the debuff read as a buff on exactly the attacks it should punish hardest. Two sources of blindness now compound, and no stack of them can grind a combatant down to unable-to-act

Modded Weapons and Armor Now Work on Install

- Modded weapons that nothing registered used to hit for a **bare fist**, and modded armor was worth **no Armor Class at all**. Both failed silently: a whole weapon pack equipped normally, showed a tooltip, and left every blade in it identical and useless with nothing on screen to say why. Craftics now works stats out from the item itself, the same way it has always read a modded food's heal value off its own nutrition
- Damage is not a formula. Craftics' own numbers are hand-tuned and do not track vanilla - a netherite axe hits for 27 where vanilla gives it 10 - so a modded weapon is placed on **Craftics' existing ladder** by interpolation instead. A sword sitting between iron and diamond in vanilla terms gets a Craftics number between iron and diamond
- The ladder is read from the live vanilla items at runtime rather than hardcoded, so it follows the Minecraft version and the server's own damage config instead of drifting from them. A weapon far above netherite is clamped rather than extrapolated: a joke sword with 400 attack damage does not get 400 damage here
- Weapon **shape** is read from the item's name and decides damage type, AP cost, reach and signature trick, using the families Craftics already supports: daggers and sai, chakrams and other thrown blades, warglaives, spears, halberds and glaives, scythes, greatswords, greataxes, warhammers, bows. A modded halberd reaches a tile the way a Craftics halberd does
- Compound names beat the words inside them, so a greataxe is not an axe and a warglaive is not a glaive. Tools are excluded outright - a modded pickaxe contains "axe" and does not become a battleaxe - and anything that reads as no weapon at all is left alone rather than guessed at
- **Armor gets an affinity too**, chosen from its material's name, because every Craftics set grants one and a set granting none reads as broken next to the rest. A modded variant of a vanilla material lands where the player already expects: "reinforced iron" boosts Cleaving exactly like iron. Armor Class comes from the piece's own rating and toughness, measured against the ladder for its own slot, since a chestplate carries three times a helmet's points and one shared ladder would call every helmet leather
- Toughness counts on its own, because diamond and netherite carry identical armor points and differ only in it - a score built on points alone would hand every modded end-tier armor diamond's number
- **An explicit registration always wins.** Inference only ever fills a gap, so a compat module or a datapack correcting a guess is never fighting it. Off entirely with `autoIntegrateModdedGear` in the config

Addon API: Clicking an Ally

- An addon can now handle a player clicking one of their own allies. Craftics did exactly one thing with that click - heal, if the player held the ally's registered heal item - and refused everything else, which is a closed set and the wrong shape for an addon whose allies are the point of the mod
- There was no way to reach it from outside: grid clicks arrive on Craftics' own packet and go straight into the attack path, so no Fabric event ever sees them. This is the general form of the heal-item hook that was already sitting there
- Handlers get first refusal, before the heal-item check, and decline by returning false so anything they do not recognise still heals as it always did. Craftics charges no AP for an ally click, so a handler that should cost something spends it itself

Diagnostics: Unclaimed Spawn Keys

- Craftics now says once, per key, when a combatant was given an AI key that no spawn customizer is registered for, and lists the keys that are registered. An undressed combatant looks exactly like a generic one, so the only way to tell "my customizer ran and did nothing" from "my customizer was never called" was to add logging on the addon side and guess between them
- Only for a combatant whose AI key differs from its entity type, since that difference means somebody set the key deliberately. An ordinary mob nobody intended to customise stays silent, so a vanilla install logs nothing

Addon API: Combat Portraits

- An addon can now draw the combatant icons in the combat HUD itself. Craftics picks a head texture by entity type, which fails completely for a mod whose single entity type stands in for hundreds of creatures: no icon registered for that type could be right for more than one of them, so every combatant fell through to a coloured square with a letter in it
- The renderer is handed the **entity id**, not just the type, because the roster is already keyed by it and the creature is standing in the client world - so an addon can reach the live entity and draw whatever its own screens draw, rather than being limited to a flat texture it would have had to invent
- One registration covers all four places a combatant icon appears: both rosters, the turn-order strip and the hover inspect panel. A portrait in one panel and a blank square in the next reads worse than blank squares everywhere
- Renderers get first refusal and do not replace the fallback, so anything unclaimed still gets its head texture or coloured square. The damage tint is passed through, so a portrait can redden by health the way the enemy column already does
- A renderer that throws is logged once and then never asked again for the session. This runs once per combatant per frame, and something that fails once fails sixty times a second

Addon API: Hiding HUD Panels

- A combat HUD panel can now be turned off by an addon that draws its own version of the same thing. A compat mod whose party screen already lists your creatures had no way to stop Craftics stacking an ally roster underneath it, which is two lists of the same information competing for one corner of the screen
- Four panels are addressable rather than one switch for the ally list, because the redundancy argument is never about one panel for long: a mod that replaces the party list usually replaces the opposing side's list too
- Hiding the enemy roster keeps hover inspection. Pointing at an enemy, an ally or a party member still opens its stat panel - that is a different feature, and replacing a list is not a reason to lose it
- Suppression is visual only. Craftics keeps tracking and syncing everything the panel would have shown, so a panel turned back on mid-fight shows the truth immediately instead of catching up
- Players get their own toggles for the ally and enemy rosters in the config's Visual section. Neither side overrules the other: an addon cannot force back a panel the player turned off, and a player who never touched the setting does not undo an addon that replaced it

Title Screen Crash on Addon Biomes

- Fixed the menu crashing every frame once any addon campaign was installed. A biome id was concatenated straight into a texture path, and a resource location rejects the `:` in a namespaced id, so building the path threw instead of returning something that merely fails to resolve. The "no card art, draw a flat backdrop" fallback was on the very next line and could never be reached
- Craftics' own biomes are all bare - plains, cave, deep_dark - so nothing in the base game ever produced an id that could trip it. The documented convention for addon biomes is `namespace:path`, which the addon template's own example uses, so this hit every code-registered addon campaign immediately and no vanilla install ever
- The same concatenation existed at five places, not one: the title screen backdrop and its save-card thumbnail, two in level select, and the world-icon writer. All five now go through one resolver that cannot throw whatever the id looks like
- Addon card art is now looked up in **the addon's own namespace**: `mymod:cavern` reads `assets/mymod/textures/gui/biomes/cavern.png`. Two addons naming a biome the same thing no longer overwrite each other's cards

Addon API: An Event Can Become a Fight

- Fixed a forced event being unable to turn into a level. The non-choice path built its arena from the level it had queued **before** the handler ran, and cleared the pending level first, so a handler that asked to run its own fight was silently overruled and the event could only hand out rewards. That rules out the entire shape a trainer battle, a gym or a scripted ambush needs, and left choice events as the only way to reach it
- `EventManager.setPendingNextLevel` is now read back. It was public, it was on the object every handler is handed, and nothing anywhere called it - so the setter an author reaches for first did nothing at all on either path
- Both paths now resolve the same way: whatever the handler set on its EventManager, else whatever it set on the combat manager, else the level already queued. A handler that starts its own fight instead of queuing one is left alone rather than having a second arena built on top of it

Raid Softlock After the First Round

- Fixed a raid locking up the moment the opening round ended, with the last player shown as still taking their turn and nobody able to act or pass. The raid's prep turn skips the first enemy phase so the party can reposition before any pillager fires, and that shortcut jumped straight back to the player phase without handing the round to the first player. The turn counter had already wound back, so every client was told it was the first player's turn while the server still believed it belonged to whoever ended the round: the first player's input was rejected as out of turn, and the last player's client never offered the button
- The AFK timer could not rescue it either, because it only counts down during a turn it believes someone is holding. That is why the fight sat there indefinitely instead of timing out
- The Phantom armor bonus skips the enemy phase the same way and had the same latent bug in any party fight, not just raids. Both now share one hand-off, so a third skip cannot reintroduce it

Compat Item Stats Missing for Everyone but the Host

- Fixed instrument, paladin and simply-swords items showing no Craftics stats for anyone except the world's host. Compat gear is registered late, after every other mod's items exist, and the three places that trigger it each kept their own hand-written list which had drifted apart. Instruments and paladins were only on the list the server runs, and a multiplayer client never reaches that one
- The host was the one player who could not see the problem, since their client shares a process with the server and picks the registration up for free. From every other seat it looked like a permissions bug
- All three now call one shared list, so a newly added compat cannot be registered in some sessions and not others

Pets Not Coming Home After a Fight

- Fixed a party member's pet failing to return to their island. The restore worked out which island the animal belonged to and then spawned it into whichever world the restore was running for, so a guest's wolf was placed at its own island's coordinates inside the host's dimension. From the owner's side the animal simply never came back
- Solo play was never affected, which is why it survived this long: with one player the two worlds are the same world
- A pet restored without stored NBT is also re-tamed to its actual owner rather than to whoever the fight was resolved for, and everyone who got an animal back now has their party list resynced instead of only the player the restore was called for

Allies With Nowhere to Stand

- A fielded ally that cannot be placed now says so in chat instead of only in the server log. With a handful of tamed wolves a full arena was near-impossible to hit, so a log line was enough; an addon fielding a party of six on a cramped floor reaches it easily, and a creature that silently never turns up reads as the game having lost it
- Reported once with a count rather than once per ally, so a tight arena reads as a tight arena instead of a stack of errors

Addon API: Sending Out From an Empty Field

- A trainer can now field a benched creature onto an **empty tile with nothing withdrawn**. Switching captures the outgoing creature's live state onto the bench, so it needs one on the field to capture; when a trainer's last creature was knocked out there was nothing to withdraw, the bench was stranded, and the trainer conceded with reserves left - the opposite of how a gym battle ends
- The reserve is removed from the bench rather than swapped, since nothing is coming back to take its place. Swapping would leave the same creature sittable twice
- It is fielded through the same path a switch uses, so its NBT, spawn customizer, AI key and typing land exactly as if it had started the fight on the grid. Refused, with the bench untouched, when the tile is out of bounds, occupied, not standable, or too small for the creature's footprint

Infinite Mode Bleeding Into Normal Play

- Fixed a parked Infinite Mode run being treated as the run you are currently playing. Parking a run deliberately keeps it marked active - that is how it survives to be resumed - and only raises a separate "suspended" flag, but three of the checks that ask "is this fight an infinite one?" read the active mark alone. With a run parked, every normal campaign level answered yes
- The visible symptom was the smallest part of it: the victory screen's Go Home button still read **Pause Infinite Mode Run and Go Home** after you had gone back to normal mode
- Underneath, the same check meant a **party wipe in an ordinary campaign level ended the parked run** - zeroing its score and clearing its saved inventory, accessories and stats, the entire save point - and then returned early, skipping every normal death penalty on the way out
- Campaign boss kills were routed into the infinite-mode boss flow, which counts a biome cleared and overwrites the shared biome cursor with a freshly rolled infinite biome. Campaign clears banked points into the infinite score, the all-time score and the best-ever score
- Going home from a campaign level also **skipped the biome reset the button promises**, because that one reads the flag directly rather than through the shared check. Left alone, the fix would have corrected the label while leaving the behaviour wrong, so both landed together
- There is now a single named check for "a run that is being played right now", and the three predicates route through it, including the one that follows a player's stored host pointer - parking re-stamps that pointer at the host itself, so without it a parked run walked straight back in through the side door
- The places that legitimately mean **any run, parked included** are unchanged and now say so: stopping a run, resuming one, and the hardcore island wipe, where a parked run must die with the island it was saved on
- Parking a run no longer drops members who were mid-fight or offline from its roster. They were left holding run loot in place of their real inventory until they happened to relog, because the only path that could give it back could no longer see them
- Note for existing worlds: this stops the damage, it does not undo it. A run already clobbered by the old behaviour stays clobbered

Feral Claws

- Fixed Feral Claws doing nothing at all. It was also the wrong shape - free attacks scale badly - so it is now a **50% chance for an attack to cost 1 less AP**, with attacks never dropping below 1 AP
- The floor means 1 AP attacks, which includes swords and bows, are never discounted. The artifact is worth carrying on heavier weapons and honest about it

Silent Trial Chambers and Raids

- Fixed the music cutting out entirely on entering an event: trial chambers and their ominous variant, raids, raid bosses, ambushes, pillager camps and bastilles. Event arenas are not biomes anyone travels to, and the soundtrack is chosen by biome, so an arena whose id had no entry played nothing rather than falling back
- The arena and the soundtrack now resolve the biome the same way, so a built arena and its music cannot disagree

Bone Armor Fighting the Wrong Way

- Skeleton bone and wither bone armor now grant **Ranged** affinity instead of Blunt. Both sets exist to keep a quiver full, and typing them off the material they are cut from put them at odds with the only reason to wear them

Accessories Following You Into Infinite Mode

- Accessory slot items now stay with your island loadout instead of being carried into an Infinite Mode run. Every other slot was already stashed on entry and handed back on exit; trinkets were not, so a run inherited them and the island lost them for its duration
- They are stashed and restored across all four transitions - starting a run, parking one, resuming it, and ending it - so a trinket cannot be stranded on the wrong side of any of them
- A run that was already in progress before this update is left alone. Restoring an empty stash for one would have wiped the trinkets the player is actually wearing, so those runs finish on the old behaviour rather than being migrated

Stars in the Tab List

- The score numbers Craftics drew beside player names in the tab list are gone

The Infinite Mode Wolf That Did Not Last

- Fixed the wolf handed to a Pet class run behaving like a wild spawn rather than a tamed pet, so it did not carry past the first level. It is now adopted as a proper party pet, owned and tamed, with its state captured the way every other pet's is. Dying is the only thing that takes it off your party now

Pet Affinity and Spawn Eggs

- Each point of Pet affinity now adds **+1% to the chance a fight drops a spawn egg**, on top of the base chance and any luck items. In a party the highest Pet affinity among everyone being rewarded is the one that counts, so bringing a pet specialist helps the whole party rather than only themselves

Enemies Spawning On Top of Party Members

- Fixed party members being placed onto tiles that were already occupied, which read as enemies spawning inside them. The placement search checked that a tile could be stood on but not whether something was already standing there, and the party's own positions were not refreshed before the search ran
- Only the island owner was reliably safe, since they were placed first. An occupied tile is still used as a last resort for an arena with genuinely nowhere else to put someone, rather than leaving a fighter out of the fight

0.4.0

Addon API: Spawning What You Are, Not What Your Entity Type Says

- Enemy and ally entries can now carry **spawn NBT**, merged onto the mob the moment it appears. A datapack can author it as an SNBT string, the same syntax /summon takes, so variant mobs need no Java at all
- For what NBT cannot express - an entity that has to be initialised through its own mod's API - there is a **spawn customizer** hook that runs on the live mob before its first turn
- Both are looked up by the combatant's AI key first and its entity type second. That ordering is the point: a mod that ships ONE entity type for hundreds of creatures can give each its own initialisation while they all share a type. Without it, every one of them spawns blank and identical
- The arena's own flags are re-applied after any NBT merge. Loading NBT onto a live entity restores its whole serialized state, so an authored tag would happily switch AI and gravity back on and let a mob wander off its tile. Your NBT decides what the mob IS; the arena decides how it is held

Addon API: Attack Types

- A new third idea alongside damage types and affinities. An **attack type** is a trait of the attack itself - nobody levels it - and it exists only to be compared against what the defender is, producing a multiplier
- All three are orthogonal. A weapon can be Slashing damage, so it scales from the Slashing affinity, while being typed Fire, so it lands hard on a grass defender and poorly on a water one. Retyping it changes nothing about what the player levels to improve it
- Effectiveness is authored as a **chart per attacking type** - what it is strong and weak against - and defenders simply declare what they ARE. That is the only shape that scales: a thousand creatures across eighteen types needs eighteen chart entries and one line per creature, where per-creature resistance tables would need eighteen thousand cells
- Dual types multiply through, so strong against one and weak against the other cancels out. Immunity is absorbing - nothing later brings it back above zero
- Typing applies in **every direction**: player on enemy, enemy on ally, ally on enemy, and enemy on player. A mob types its own attacks once, or an AI overrides it per action for a creature with a movepool. The player's defending types come from a provider, since a player's typing usually derives from something that changes mid-run
- A mount that intercepts a hit aimed at its rider is judged as the mount, because the animal is the thing being hit
- Players are told what happened - "It's super effective!", "It's not very effective...", "It has no effect..." - so matchups are legible without a wiki
- Inert until something opts in. An untyped weapon, an unregistered type or an untyped defender all return a plain 1x, so nothing about existing combat changes

Addon API: Custom Enemy Actions

- An addon can define an enemy action whose resolution is genuinely new, rather than composing the forty shapes Craftics already has
- Deliberately one extra member of a sealed set rather than an open interface. The turn machine dispatches with pattern-matching switches the compiler checks for exhaustiveness; unsealing would surrender that across dozens of sites, and an unhandled action would look like an enemy that just stands there
- Handlers are given a context whose damage and movement methods route through Craftics' own pipeline, so an addon action cannot accidentally skip resistances, typings, shields, death handling or the pit-fall check
- Wrap one in a boss ability and it inherits the whole telegraph system: warning tiles, the wind-up VFX and the one-turn delay, with the handler firing when it resolves
- An unregistered action id costs that enemy its turn and logs once. An addon can be uninstalled while a save still holds an AI that names its actions, and a missing handler should not wedge the fight waiting for something that will never resolve

Addon API: Allies From Outside the Hub

- Craftics' battle party is built from real mobs standing in the hub: you tag a wolf, it is snapshotted and put back afterwards. A mod whose party is DATA ON THE PLAYER has no wolf to tag, so there is now a **field ally provider** hook for it
- Provider allies may each declare their own AI key, spawn NBT and display name, which is what lets a single entity type field a whole party of visibly different creatures. Without the name, six creatures sharing an entity type all read as six copies of the same thing
- They are fielded as temporary: they fight the battle and are gone, never carried between levels and never materialised into the hub. An ally that was never a hub entity must not be "returned" to one, or the player ends up with a second copy of a creature the owning mod is still tracking
- The party cap is passed through as advisory and the result is deliberately not truncated to it. The cap is written for tamed wolves; a mod with a six-creature party owns its own rules, and silently cutting that to one would look like a Craftics bug

Addon API: Affinity Reskins

- The eight affinities can be renamed and re-iconed. Their number stays fixed because they are the axes level-up points are spent on and the screens are laid out for exactly eight; what a total-conversion mod needs is not more axes but different ones
- One call renames the affinity everywhere it is shown: the level-up screen, the respec screen, the Infinite Mode class picker, the damage-type panel, weapon tooltips including the Simply Swords and Simply Bows compat ones, the combat damage feedback line with its Resisted and Weak notes, and both chat messages
- The damage type that scales from the affinity is renamed with it. A player who saw "Fire affinity" next to "Slashing damage" would read it as a bug rather than a theme
- Nothing mechanical changes, and points are saved by internal name, so a reskin can be added to or removed from a live world without touching player progress

Allies Leaking Into the Hub

- Fixed temporary allies - spawn-egg summons, and now provider-supplied party creatures - being materialised into the hub world as real mobs when combat ended abnormally
- The normal end-of-fight path already excluded them correctly. The abnormal-exit rescue path, which recovers allies when a fight is torn down unexpectedly, rescued every living ally without checking, so a summon that should have evaporated turned into a permanent animal standing in your hub


0.3.9

Battle Intros

- Showcase fights - the first level of a biome, biome bosses, and raid bosses - now open with a fighting-game style intro: the camera zooms in tight on each fighter in turn, they strike a flourish chosen by their strongest affinity (a sword-dance for Slashing, a heavy overhead chop for Cleaving, a ground-pound for Blunt, a slow bow-draw sweep for Ranged, water-bending arm work for Water, an arcane weave for Special, a kneel-and-whistle for Pet, shadow-boxing for Physical), themed particles burst around them, and after the last fighter the camera pulls back out and the fight begins
- Fighters are handed their matching weapon for the pose: a Blunt main walks in holding their mace, a Ranged main their bow - whichever qualifying weapon is already in their inventory. Physical and Pet mains pose bare-handed on purpose
- About 2.2 seconds per fighter, camera controls and combat input locked for the duration, and it only plays on fights worth making an entrance for - mid-biome levels start immediately as before
- Every failsafe the rest of the mod has learned the hard way is wired in: the sequence aborts cleanly if the fight ends mid-intro, waits for the loading swipe to clear before starting, and can never strand the camera or the input lock

Bestiary Crediting the Wrong Player

- Fixed the bestiary only filling in for the island's owner. Party members fought the same mobs and unlocked nothing for it, while entries kept appearing for whoever owned the island whether they had been in the fight or not
- The unlock ran while the fight was still being set up, which is before anyone except the leader has been attached to it, so the only player it could ever credit was the one the fight was built around. Credit is now handed out per player as each one joins, which also covers somebody dropping into a run already in progress

Universal Attractor

- The Universal Attractor no longer drags enemies inside walls. It checked whether another creature was standing on a tile but never whether the tile held a block, so obstacles and fallen rubble read as open floor and mobs were pulled straight into them
- Enemies pulled over a pit now fall in and die instead of hovering above the hole. It is the same fall a Concussive Blast shove already caused, and flying mobs stop at the rim exactly as they do for every other pull or knockback
- Pulled mobs now actually move. The pull updated the battle grid but never the creature itself, so its real position was left behind to catch up on its own

Returning Home from a Trading Hall

- /home now behaves properly inside a trading hall or bartering station. It used to send you home while leaving the fixed booth camera in place, so you arrived still looking through the market with nothing but the Leave button to break out of the view
- It now does exactly what that Leave button does before sending you home

Auction Board Listings

- Fixed artifacts on the auction board showing no price, no seller and no sign they could be bought, just a bare item name. Artifacts have their tooltips rewritten to describe what they do inside Craftics rather than in their own mod, and that rewrite was throwing away the board's own lines along with the mod's
- The same wipe applied to every item with a rewritten tooltip, so totems, Simply Swords and the rest were being listed just as blankly

It Takes a Pillage Event Intros

- The Pillager Camp and Bastille intros no longer show a pillager portrait. Both are narration - the party catching smoke through the trees, a map running out at a wall - so nobody is actually speaking, and a pillager inviting the party to come sack its own camp made little sense
- The portrait also chose the voice, so every narrated line was being grunted out in pillager. Both now use the plain narrator with no portrait, matching the dig site and the other event intros

0.3.8.5

Odd-Shaped Arena Floors

- Fixed non-square arenas losing their floor pattern across part of the grid. A level definition bakes its checkerboard at its own fixed size, and any arena whose real shape extends past that - polygon arenas especially - had the excess filled with a single plain block, erasing the pattern that makes the grid readable
- The pattern is now a formula the builder can evaluate at any coordinate, phase-aligned with the baked tiles, so it extends seamlessly to whatever shape the arena actually is. Covers generated biome levels, raid boss arenas and the fixed early levels

Menu Icon Duping

- Fixed inventory-sort mods (and plain double-clicking) being able to pull the icon items out of read-only menus - the lootbox odds preview, the lootbox confirm screen, auction browsing and trade menus - into your inventory. The menus blocked clicks landing ON their own slots, but a double-click collect started on one of YOUR inventory slots sweeps matching items out of every slot in the open screen, and that path was never told the icon region is off limits
- Sort mods' "loot all" button no longer works on those menus either. The server was already refusing to move anything, but the client had predicted the transfer and was never corrected, so the icons appeared in your inventory as convincing ghost items (they were never really there - a relog clears any you picked up before this fix). Rejected clicks now force an immediate resync
- Shift-clicking and number-key-swapping a menu icon no longer counts as pressing it - only a plain click does. "Loot all" fires a shift-click at every slot in one batch, and on the lootbox confirm screen that batch would have walked straight onto the "Open it" button and spent your emeralds
- Menu icons are now also poisoned outright: every icon carries an invisible marker, and the server destroys marked items found in any player's inventory - the instant the menu closes, and on a regular sweep as backstop. Inventory mods with their own server-side transfer path (ClientSort's server acceleration) write straight into player inventories without ever consulting the menu, so rather than trying to enumerate every exit, anything that escapes simply evaporates. No server-side mod configuration required

0.3.8.4

Victory Rewards

- Fixed the victory screen sometimes showing an empty reward grid even though chat had just announced the loot. The reward tally was shared globally across every fight on the server, and any OTHER party starting or finishing a fight wiped it - so whether your grid survived to the victory screen depended on what everyone else on the server happened to be doing. Each fight now keeps its own tally
- Victory loot chat lines now consolidate: killing three skeletons reads "+ 3x Bone" on one line instead of "+ 1x Bone" three times. Items only merge when they are genuinely identical - a cooked and a raw drop still get their own lines

Battle Animations Dying Until Restart

- Fixed the bug where your character (and party members) would abruptly stop playing all battle animations - no walk, no attacks, no idle - until the client was fully restarted. It was triggered by chaining teleports between worlds (losing an infinite run, going to the lobby, then home, then into a run), and struck seemingly at random because a garbage-collection pass at the right moment could silently repair it
- Under the hood: the animation layer lookup was keyed on the player entity, but Minecraft swaps in a brand-new player entity on every world change while giving it the same entity ID as the old one. The lookup would then hand back the animation layer of a previous, no-longer-rendered body, and every animation played into it went nowhere - no error, no log, nothing visibly wrong except a statue where your character should be
- The layer now lives on the player entity itself (via PlayerAnimator's per-player storage), so a fresh body can never inherit a dead one's animation layer

Discord Link

- The periodic Discord reminder in chat is clickable again. The link was being sent as plain text on the assumption the client would find and linkify it - it doesn't for messages the server hands it, so the address sat there as dead text you had to retype by hand
- It now carries a real open-link action, underlined so it reads as a link, with the destination on hover. Clicking still goes through the game's own "open this link?" confirmation, the same as any link in vanilla chat

Items Not Stacking

- Fixed identical items refusing to stack. The season stamp added in 0.3.8.3 writes the moment an item was acquired onto the item itself, and two stacks only merge when everything about them matches - so arrows, potions, golden apples and ender pearls picked up at different moments each became their own pile that could never recombine
- Stackable items are no longer stamped at all. The stamp could not have meant anything on them anyway: a stack of 64 arrives in pieces from different places at different times, so there is no single moment it was acquired, and recording the first one would just be claiming it for the other 63. What a season boundary actually cares about - the sword, the armour, the trident somebody brings to a fight - occupies a slot of its own regardless
- Stacks already broken by this repair themselves. The same sweep that caused it now takes the stamp back off any stackable carrying one, so existing piles recombine within a couple of seconds of being held. Items sitting in chests are not swept, so those recover once they pass back through an inventory

0.3.8.3

Sudden Death

- A non-boss fight that runs past round 20 enters SUDDEN DEATH: full-screen title, a horn and a wither shriek, and every enemy gains +3 speed and +2 attack. It is aimed at the fight that should have ended ten rounds ago - somebody parked in a nearly-cleared arena farming it, or whittling a room down from maximum range with nothing able to reach them
- Reinforcements arriving after it triggers are angry too. A summon that turned up calmer than the mob beside it would make stalling for reinforcements a strategy, which is the exact behaviour this exists to discourage
- Boss fights and raids are exempt. A boss is SUPPOSED to be long - several have phase transitions that only arrive after a while, and the Hollow King's whole loop is built on spending turns mining his pillars rather than hitting him. A clock on that would punish playing the fight the way it was designed
- New achievement, **Overtime**, for winning a fight after Sudden Death has set in

Seasons (groundwork, invisible)

- Every weapon, piece of armour and battle-usable item is now quietly stamped with when it came into a player's hands. Nothing displays it and nothing reads it yet
- It exists now because it is the one piece that cannot be added later: an item that already exists has no record of when it arrived, so stamping has to start before the first season boundary or everything already in circulation is unattributable
- Stamped by a periodic inventory sweep rather than at the point items are granted. Items arrive from loot, crafting, trading, the auction house, dropped stacks, admin commands and other mods, and a stamp applied at only some of those would be worse than none - the gaps would be indistinguishable from legitimately unstamped items

Teleport Logging

- Every dimension change is logged now, both halves of it. `[teleport] X attempting <from> -> <to>` goes in before the move, and `[teleport] X arrived <from> -> <to>` comes from the game's own world-change hook after it
- The pairing is the diagnostic. A cross-dimension teleport that never completes IS a ghost lobby, and it leaves an "attempting" line with no "arrived" to match it. That failure has now been three separate bugs wearing the same symptoms, and each one took a report and a guess to find; this makes it something you can see in a log instead
- The attempting line names the system that asked for the move - a victory, a raid ending, a disconnect cleanup - rather than the shared helper they all funnel through, which the log would already know
- The arrival line comes from the game rather than from this mod, so it also catches moves Craftics never asked for: a vanilla portal, an operator, another mod, a respawn
- Islands log when they unload and when they are deleted, with a warning if a delete is refused because somebody is still inside. An island unloading in the same breath as somebody leaving it is the shape of half these reports, and the two lines sitting next to each other is what makes that visible rather than theoretical
- `/debug` prints the tail of this log in game, so a player can read it back without finding the file

Dying to Something That Isn't the Fight

- Fixed the void arena. Being killed by anything outside the fight - `/kill`, an operator, a plugin, another mod - left the run still running with nobody in it. Craftics never lets its own damage kill you outright (its death path clamps your health and runs the game-over sequence itself), so a real vanilla death mid-fight always means something external did it, and nothing was telling the fight about it
- What that looked like: a long "loading terrain", then an arena of pure void tiles with the PREVIOUS fight's enemies still listed in the sidebar - you had respawned into an arena whose dimension had already been torn down. `/home` walked you straight back into it, because the mod correctly refuses to send you home mid-fight and the fight still believed it was running
- An outside death now ends the run the same way a disconnect does and puts you in the lobby. It also recovers anyone already stuck: dying or relogging in a dead arena gets you out instead of back in

Ghost Lobby (attempt #443634564)

- Fixed the ghost lobby for players who leave mid-fight. That detail was the whole clue: leaving mid-fight means logging out INSIDE your island dimension, so rejoining is a cross-dimension move - and the join handler was performing that teleport while the login handshake was still settling. It is the same race as tearing an island down under a teleport in flight: the server puts you at the destination and starts sending you sound and player events, while the dimension change that would have sent you chunks and entity tracking never completes. A void you can hear people walking around in, invisible to everyone, and no command fixes it. Anyone who logged out in the lobby took the same-dimension path and was always fine, which is why it looked random
- The teleport now waits for login to finish, and re-checks the player first, since they can drop again inside that window
- The earlier spectator and island-unload fixes were both real and both stay. This was a third cause wearing the same symptoms

Debug Command

- `/debug` is now a diagnostic dump rather than a second door to the report form: the last 20 lines of the log, colour-coded so errors and warnings stand out, then the dimension you are in and your position. Purely local, nothing uploaded, nothing shown to anyone else
- Position and dimension print LAST on purpose, because chat scrolls and the last thing printed is the thing still on screen. "Which dimension am I actually in" is the single most useful line for the kind of bug this mod produces - runtime dimensions that look identical to the lobby from the inside
- Filing a report is `/bugreport` (or `/bug`), which still opens the form. The two are kept apart so neither has a second, surprising behaviour hiding behind an argument

Brushes

- The brush only digs sand and gravel now. It always claimed to - the note above it read "excavate random item from sand/gravel tile" - but nothing ever checked the block, so any tile you stood next to would do, including the one under your own feet. That is most of why it felt like free money: a 1 AP action with no situation attached to it, usable every turn of every fight regardless of where you were standing
- Sand, red sand, gravel and both suspicious variants all count. A refused brush costs you nothing, because the ground check runs before any durability is spent, the same way the reach check above it already did
- Diamond drops from 8.33% to 3%. The old table was a flat twelve-way split, which made the rarest thing in it exactly as likely as brick
- Pottery sherds join the pool at 3% for all of them put together, not 3% each. There are 23 of them and they are spell scrolls, so a flat 3% apiece would have been 69% of every brush and would have turned the thing into a scroll dispenser instead of nerfing it. One shared slice also means the table stops tilting further every time vanilla ships another sherd
- The eleven ordinary finds split the remaining 94%, at 8.545% apiece. The table is written as weights rather than percentages now, so adding a find renormalises the whole thing instead of quietly taking the difference out of diamond and the sherds

Infinite Mode Chapters

- Infinite mode now runs on a server-wide **chapter seed**. Every player on the server meets the same biomes, arenas, enemy rosters, loot, bosses, events and trader stock at the same run depth, so a score on the board reflects how you played rather than what you happened to roll
- Combat rolls stay random per player on purpose. Crits and procs are consumed in an order set by what each player does, so two people sharing a seed desync on their first differing move regardless - seeding them would cost the whole combat codebase and buy nothing
- Chapters rotate on a recurring schedule: `/craftics chapter schedule daily 04:00`, `weekly MONDAY 04:00`, or `monthly 1 04:00`, with `/craftics chapter timezone <zone>` to anchor them. `/craftics chapter rotate` forces one immediately, and `/craftics chapter info` shows the seed, the schedule and the countdown
- The next rotation is stored as an absolute timestamp, so one that falls while the server is down fires on the next boot instead of being silently skipped
- Rotation clears the infinite leaderboard and banks its final top ten onto a new permanent **Top Players** board, using championship points (25/18/15/12/10/8/6/4/2/1). Placing well across many chapters is what builds a career standing, rather than one lucky run
- The infinite board now shows which chapter is running and how long is left in it
- Campaign levels are untouched and still reroll their layout and mobs on every visit


0.3.8.2
Mobs Acting Out of Turn

- Nothing can hurt you in a fight except the fight.
- Your allies get the same protection, so nothing chews through your wolf while it waits for its turn
- Removed the stalker as a miniboss
- Removed herobrine
- Projectiles are visually distinct when attacking
- Fissure fills with deepslate now, and visually falls from the sky.

Islands and Admin Tools

- `/craftics rebuild_arenas all` rebuilds every arena on every island the save knows about, not just the ones whose owners happen to be online. It refuses while anyone is mid-fight, since regenerating an arena re-lays its blocks and doing that under a live battle pulls the floor out from under it. Op-only and deliberately loud about the fact that it will hang the server while it runs
- `/craftics deleteisland <player> confirm` deletes an island for someone who wants to start over, or who would rather just live on a friend's. The island, everything built on it, and their campaign progress all go - leaving the progress behind would give them a save that has forgotten where it lives but not how far it got, with the level select still showing biome twelve unlocked over a world that no longer exists. It does not stop them having an island again: going home builds a fresh one
- Everyone on the island is sent to the lobby BEFORE the dimension is deleted, visitors included. Deleting a dimension out from under someone standing in it is undefined behaviour, not a clean error. The `confirm` is mandatory rather than a flag, and the whole thing is op-gated

Difficulty

- Difficulty is a thing you can choose now. `/craftics difficulty easy|medium|hard`, with no argument to see where you stand. **Easy** is exactly what the game was before this existed, **Medium** gives enemies 1.5x health and +1 damage on every attack, **Hard** gives them 2x health and +2
- It is **per island, not per server**. On a shared server one player wanting a harder campaign should not drag everyone else's runs up with them, and in a party fight the island being played is the leader's, so the leader's setting is the one that counts
- Easy is the default everywhere, and stays that way until somebody chooses otherwise. It deliberately does not read the world's own difficulty: a server that happens to run on Hard would otherwise silently double every islander's enemy health, and existing saves would get harder just for updating
- Only two levers, on purpose. A difficulty that quietly changes ten things cannot be reasoned about by the person picking it. The damage is a flat addition rather than a multiplier because a multiplier makes the big hits enormous and leaves chip damage free - this raises the floor instead, and the boss single-hit ceiling rises with it so the setting is still felt in the fights it is meant to be
- It cannot be changed mid-fight. Enemy health is decided as each mob spawns, so switching during a battle would leave what is already on the field at its old health while everything after it arrived at the new value - one fight running at two difficulties
- **Peaceful is no longer allowed.** It is not a difficulty this mod has a degraded mode for, it is one it cannot run at: vanilla despawns every hostile mob on the spot, so an arena would build itself and stand empty. Setting it puts the world back to Easy and says why. If you want the fights easier, that is what the Craftics difficulty is for

Main Menu

- A big PLAY ONLINE card on the right of the title screen drops you straight onto `play.crackedgames.co`. It is built as a peer of the CONTINUE card rather than another row on the menu list - same bevel, same accent bar, same hover - because it is a destination rather than a client setting, and at the bottom of that column it would have read as one
- It connects through the same path the multiplayer list uses, with a throwaway server entry rather than one written into your servers.dat. Adding a permanent entry to your own server list on a button press is editing your data to save you a click
- Greys out with a reason when multiplayer is disabled, and drops off entirely on a window too narrow to hold both columns rather than stacking on top of the menu

Trading

- `/trade <player>` asks someone to trade. They get a clickable line in chat rather than a screen thrown in their face, because in this mod that could land mid-fight, and the offer goes stale by itself after a minute so a forgotten invite cannot be accepted an hour later. `/trade accept <name>` opens the window for both of you, `/trade cancel` walks away
- The window shows both sides at once: what you are giving, what they are giving, and your own hotbar along the bottom to offer from. Click a hotbar slot to put that stack up, click one of your own offered stacks to take it back. Any change to either side clears both accepts, so nobody can agree to a stack of diamonds and receive a stack of dirt
- **Nothing is ever held in escrow, and that is the whole point.** The usual way to build this takes both sides' items into the window and hands them out when everyone agrees, which means that between those two moments the items are somewhere that is neither inventory - and a crash, a disconnect or a restart in that gap has to guess who owns them. Every wrong guess either destroys an item or creates one. That gap is where duping comes from, so there is no gap here: an offer is only a description of items you still physically hold, and they do not move until one uninterrupted tick at the end
- Both sides are re-checked in full before a single item is taken. An offer is a promise about an inventory you were free to keep using the whole time, so if either of you no longer has everything you offered the trade is refused with nothing moved. Matching is on the item and its components, so an offered enchanted sword can never be settled with a plain one. Anything that will not fit lands at your feet rather than vanishing
- Disconnecting ends the trade for both of you and says so. There is nothing to hand back, but the session has to go, or you would come back to a server that still believed you were mid-trade

Thrown Weapons

- A chakram is thrown before it hits anything. It used to be launched by its own ability handler, which runs after the swing has been resolved, so the damage and the particles had already played by the time the disc left your hand and it spent the whole flight chasing an outcome you had watched happen. The throw now goes out first and the hit is timed to the disc arriving, using the same delay a bow shot has always used for its arrow
- A ricochet carries the weapon's effects. Every bounce used to be a bare point of damage applied around the outside of the entire on-hit pipeline, which is why a Punch chakram knocked back only the enemy you aimed at, and a Chomp'olotl rolled its axolotl only on the first target it touched. A bounce now lands through the same pass a normal strike does: Sharpness, Smite, Bane, Knockback, Serrated, Fire Aspect, and the weapon's own unique proc all apply to every enemy in the throw
- Ricochets actually chain now. Every bounce measured its range from the enemy you originally threw at, so the reachable set never moved and a chain across a spread-out group was impossible no matter how they were lined up. Each bounce now looks for its next target from where the disc just LANDED, up to three of them, and the reach is a tile longer. A thrown disc travels; its range travels with it
- That includes the multi-target effects. A hop runs the weapon's own ability first and then the enchantment pass, in that order, which is what lets Sweeping Edge and the abilities that carry their own arc (claymore, glaive, scythe, coral fan, Soulrender) fire from a ricochet or a pierce instead of only from the swing that started it. The two share one geometry, so running them in this order is also what keeps a neighbour caught by both from being hit twice for one landing
- Crossbow bolts go through the whole line. Pierce hit exactly one enemy - the single tile directly behind the target - and the arrow was drawn stopping at the first body, so a shot into a queue of skeletons killed two of them and looked like an ordinary shot. The line now runs to the edge of the arena, stopping at a wall, every enemy on it takes the bolt, and the arrow is seen crossing all of them. Flame lights each one and Punch shoves each one, rather than only whoever was in front
- The disc's speed comes off the distance it is covering rather than a fixed count per bounce, so a throw at the tile next door snaps across it and one thrown to the edge of its range takes the time it should
- Thrown items land before they do anything. A snowball's damage, a fire charge's burst and a splash potion's cloud all used to resolve the instant the item was used, and the item was then shown crossing the arena afterwards, chasing a result you had already watched happen. The throw goes first now and the effect waits for it to arrive
- Which also means a thrown item can travel at a speed that looks like a throw. Its flight was pinned to four ticks regardless of distance, deliberately, because every tick it spent in the air was a tick its explosion had already happened without it. It is timed by distance now, like the arrows and the chakram

The Hollow King

- He is a miner again. Five of his seven abilities used to ask the same question - step off the highlighted tiles - all centred on wherever you happened to be standing, and none of them had anything to do with the mine. He built walls and then ignored them
- **Shore Up.** He drives in three support pillars, five once enraged, three blocks tall and visibly timber rather than stone, and takes +1 armour from every one still standing. While they hold he is genuinely hard to hurt, and the room is held together by specific tiles you can point at
- **Total Collapse.** He knocks them out, and the ceiling comes in down the entire row and column of every pillar at once. A support does not hold up the square it stands on, it holds up the span, so the span is what falls. It is a lot of floor, and all of it is painted a turn ahead
- **Take the pillars out yourself.** A pickaxe on an adjacent pillar knocks the whole thing down: one less point of his armour, and two fewer lanes in the collapse he is loading. But a pickaxe only reaches one tile, so getting to a pillar means standing in the lanes it loads, and every turn spent mining is a turn not spent hitting him. That is the fight now - not "am I on the orange tile" but how much of the room you are willing to dismantle, and when
- **Shrapnel** replaces Rubble Toss, which cleared an obstacle from anywhere on the map for four flat damage and fought his own cave-ins for terrain. He shatters one of his own supports and fires it down the lane, hitting everything between it and you. He spends armour to threaten you, and you get to watch him choose
- Miner's Fury ploughs through obstacles as it always did, his own pillars included
- All of it is seen. A collapse rains rubble on every single tile it covers, and the rubble LANDS - real blocks piled on the floor for a few turns before they are cleared away. It only ever falls on open ground, because a rock dropped over a pillar or a wall touches down three blocks up where nothing owns it, which is where the stray stone perched on top of the beams was coming from
- A pillar knocked out topples outward in a scatter of timber, thrown up and gone before gravity brings it back down onto something. Pillars themselves now go up in one piece with dust up their length rather than being built by falling blocks - a log dropped down a column that already has a pillar in it lands on the beam and becomes a stump that outlives it, which is why the beams appeared never to go away
- He no longer puts the pillars in the same places every fight. The site search walked the arena from one corner and took the first tiles that fitted, so the same room produced the same three pillars in the same spot every cast - memorise it once and the mechanic is over. Same rules, shuffled order
- He no longer just alternates shore-up and collapse. The pillars have to stand a couple of turns before he will bring them down, which is the window you need to go and mine them anyway, and it leaves room for the rest of his kit to fire between
- Where you stand now decides what he does. Inside 3 tiles the spells go away entirely and he comes at you with the pickaxe. From 4 to 5 is his working distance: he plants his feet and casts, and the telegraph turn is a turn he really is standing still. At 6 or more he closes AND casts in the same turn, at speed 6 - backing off no longer buys a free turn, it buys a charge
- And when he cannot reach you at all, he digs. A boss that cannot find a path normally wanders, which against a player who has walled themselves in is the same as switching the fight off - and this one can end up walled in by his own cave-ins. He takes the stone out and keeps coming. He will not cut through his own pillars (that is his armour) or the arena boundary

- A boss can no longer build on top of you. Any solid block written into a tile someone is standing on gets that person ejected by vanilla's own push-out, so a cave-in did not block your path, it shoved you off the tile - into a fissure, off a ledge, into a void pit. Nothing in the telegraph told you which way you were going to be pushed, which made it a death with no counterplay. The stone lands on whoever is standing there and hurts them instead, which is what a cave-in should have been doing all along
- The same rule covers his TNT. A charge is primed at standing height, so priming one under somebody used to materialise a block of TNT inside them. It detonates on the spot now, which is also the honest reading of a demolition charge planted at your feet
- Cave-in stone stands in the obstacle layer instead of replacing the floor. Painted at floor level it came out looking like a slightly different shade of ground that the grid then refused to let anything cross - a wall you could neither see nor walk through
- The TNT warning says what you actually get. It read "2-round fuse", which was counting the fuse's own ticks rather than the window you have: the charge is primed during the boss's turn and blows at the end of your next one. It now says so

Arenas

- The Bastion Brute has his own arena back. Every biome's boss level looks for `boss.schem` in that biome's folder, and the crimson forest's was named `bastion_boss.schem` - so the lookup found nothing, fell through to the numbered list, and fought the boss in the ordinary level-1 arena. The purpose-built one had never loaded once. It is the only biome that was named off-convention. On a save that already has that level cached, `/craftics rebuild_arenas` picks it up
- Fixed cave levels being fought in a nether arena. Arenas are cached per level number and validated against the biome they were built for, and a cache entry from before that stamp existed was trusted rather than rebuilt. That was a safe assumption until biomes went from five levels to seven, at which point every level number resolved to a different biome than it had when its arena was built - Underground Caverns II is global level 51, which used to be Soul Sand Valley I. It could never fix itself either, because the corruption check only looks for missing floor and a netherrack floor is perfectly solid. An unstamped arena is now rebuilt on the spot the next time you enter that level, once, and stamped so it never happens again

0.3.8
Softlocks

- A fight can no longer end up with nothing in it and no way out. A room can empty DURING the enemy phase - a mob burns to death on its own turn, walks into lava, or is killed by another mob - and every victory check in the game was attached to a player action, so none of them ever saw it happen. What was left was a fight containing nothing, which the player could only end by taking another turn. The enemy phase now checks the room the same way every player action does
- Added a watchdog over the enemy phase. It is a state machine spread across about sixty places that arm the next step, and any one of them that returns without arming anything leaves the phase spinning on the same tick forever. The player is locked out completely while that happens, because every input that is not their turn is dropped - which is why it showed up as "end turn does nothing" rather than as a freeze. Ten seconds of no progress now forces the rotation onward and logs which entity and which step it was stuck on, so the next one names itself

Multiplayer

- Party pets belong to whoever brought them, not to whoever pressed start. Pets were collected for the run leader alone, so a member could add animals, see them in their party list, and then fight without them - the only party that ever turned up was the host's
- That fix came with two traps worth naming. The collector deletes party entries whose animal it cannot find, and a member's pets live on THEIR island rather than the leader's, so the obvious version of this would have quietly wiped every member's party list on the first run. It now searches the owner's island too, and treats "could not look there" as a lookup that failed rather than as proof the animal is gone. Restoring afterwards had the mirror of the same bug: pets carried no owner, so a member's wolf would have been rehomed onto the leader's island
- Fixed the ghost lobby: leaving an island no longer strands you in an empty void where you can hear the people around you but see nothing, and nobody can see you. An island is unloaded once the last person leaves it, and that was happening in the same tick as the teleport - pulling the world out from under a dimension change still in flight. The server then believes you arrived, which is why the sound is right, while the handover that would have sent you any terrain never finishes. No command fixes it, which is why operators were as stuck as everyone else. The unload now waits a tick, the way the logout path always has. That is the difference between logging out of an island and walking out of one
- The same race was in three places, and the worst of them was a host logging out while guests were visiting: everyone was teleported out and the island was torn down immediately, so it could strand several people at once
- Arriving at a hub or the lobby also takes you out of spectator now. A downed party member is put into spectator while the rest of the party fights on, and the exits that forgot to put them back left them stuck that way. Only spectator is touched, so an operator in creative stays in creative

Auction House

- `/auction`, `/shop` and `/store` open a chest screen of everything players have listed, cheapest first, 45 to a page. Clicking an item opens a confirm step showing the price, your balance and what you get, so nothing is ever bought on a single click. The bottom row pages through the board, switches to your own listings, and collects anything waiting for you
- Sell with `/auction sell <price>` for emeralds, or `/auction sell barter <item> <count>` to ask for items instead. Barter only ever accepts plain stacks as payment: nothing enchanted, renamed or damaged is taken, so clicking Buy on a listing that wants a diamond sword can never quietly spend the enchanted one you were saving
- The board holds 500 listings, 10 per player, and a listing lasts 14 days before it goes back to its seller. Nothing here ever destroys an item: an expired listing, a cancelled one, or a purchase that will not fit in your inventory all go to a mailbox you collect from
- The whole thing is built so an item exists in exactly one place at every moment - in an inventory or on the board, never both, and it moves between them in a single tick. Buying claims the listing before a single emerald is spent, so two people clicking the same item in the same tick can only ever have one of them succeed, and the one who loses pays nothing. There is no pending or reserved state anywhere, because a pending state is one a crash can strand

Fire

- Fire has a reach. Every flame now carries an intensity: how much further it can travel. A light struck by hand starts with three tiles of it and each tile the fire spreads to is born one weaker, so flint and steel is a tool with a radius rather than something that slowly eats the whole arena. A fire that has run out still burns where it stands, it just stops passing itself on
- Burnt wood leaves charcoal, not soil. Every burnt-out tile turned into dirt, which reads correctly under grass and leaves and completely wrong under anything built: setting fire to a plank floor or a fence line left a field of earth hanging where the boards had been, as though the fire had grown a garden. Worked wood - planks, logs, fences, doors, stairs, slabs - now burns down to a block of coal. Ground still becomes soil, because under burnt grass that is what is left
- Soul fire never runs out of reach, and anything it lights inherits that. It burns without fuel and it does not tire
- Soul fire has its own burn. Soul Burning holds longer than ordinary Burning and bites harder, and fire resistance no longer switches it off - the potion and the armour take a point off each tick and nothing more. Fire resistance is what makes ordinary fire a non-event, and soul fire is supposed to still be a reason to move even for someone who has solved fire. It shows the flame icon in soul-fire blue
- Mobs take soul burn too, on their own timer, so one can carry an ordinary burn and a soul burn at once after walking out of one flame into the other. Fire immunity is not a way out of this one either: a blaze standing in soul fire still burns, it just takes a slightly smaller bite. Water still puts both out, which is a rule older than fire immunity
- Sculk jaws are no longer generated, and one baked into a schematic is left as scenery. The tile was backed by a real Deeper and Darker block that kept running its own logic inside the arena, biting whoever stood on it over and over in real time, on nobody's turn, on top of the single once-per-step bite the grid rule applies. A hazard the turn system cannot see or bound is not a hazard, it is a death with no counterplay
- Two fires meeting resolve to the fiercer one. Ground that was already alight used to simply refuse a second flame; now the stronger one takes it over, so soul fire washing across an ordinary burn turns it blue and endless instead of stopping at its edge, and a fresh strike re-arms ground whose fire had nearly gone out. The weaker flame never drags the stronger one down

Interface

- The Continue button on the victory screen is now the bigger of the two. It is the one that gets pressed, over and over, and an infinite run is nothing but that button between fights, so it is sized as the primary action instead of matching Go Home

0.3.7
Softlocks and Lost Inventories

- The victory screen could be lost with its choice still unanswered, which left you standing in the arena unable to move or use anything. The server holds the whole party still until that button is pressed, and closing the screen is not something the screen itself can refuse - anything that opens or clears a screen replaces it silently, and there was then nothing left that would ever ask again. The screen now comes back on its own whenever it goes missing with a choice outstanding, keeping the reward reveal where it left off
- `/home` works again once a fight is decided. It is blocked mid-combat so it cannot be used to escape a losing fight, but that guard also covered the post-victory and game-over windows - the exact moments where a lost screen leaves you with nothing else to try. Winning is not escaping, so those two phases now let it through
- Breaking the bed you slept in no longer respawns you into the void. Every world here is void-generated, so vanilla's "your bed is gone, use the world spawn" fallback hands back a coordinate in open air: you fall out of the world and die a second time, and that death is an ordinary one that drops your entire inventory
- Getting back to your island no longer depends on your island keeping the shape it was generated with. Every safety net in the game used to check a single column of blocks - the coordinate it remembered, straight down. Dig out that spot, terraform the yard, move your base across the island, and the check meant to catch a void landing would confirm one instead, dropping you into open air above ground you built yourself. The game now searches outward from where you were headed until it finds somewhere you can actually stand, so it always lands you at the nearest solid ground rather than giving up on the one column it knew about
- That search covers everything that puts you back on your island: respawning, `/home`, leaving a fight, finishing an infinite run, walking out of the trading hall, and returning from a raid boss. Coming back from a raid used to send you to the lobby if your exact departure spot had since lost its floor, which was a long way to walk home from
- As a last resort, if there is genuinely nothing left to stand on anywhere near your hub, a single block is placed at your spawn point and you land on that. One block, not a rebuilt starter room: clearing the site is a decision as often as it is an accident, and coming home to the old hub stamped back over your plot would undo the work. You get a foothold on your own island and nothing more
- Landing spots need two blocks of headroom now, not one. A single gap of air is a place you can technically stand and then suffocate in, which on a rescue is just a slower version of the problem
- Keep inventory is on everywhere: the lobby, your island, and raid arenas. Every world here is void generated, so an ordinary misstep drops your gear into a place there is no walking back to, and dying to the world was never meant to be the difficulty - the fights are. Losing a fight still costs what the death-penalty config says it costs, which is a separate system and is unchanged

Combat Fixes

- Enemies standing in fire are attacked instead of the fire being put out. Attacking a burning tile stamps the flames out, and that check claimed the click before it ever looked for a target - so hitting a mob that had walked into a fire spent your AP extinguishing the burn that was damaging it
- Projectiles no longer count as kills. A boss fireball, wither skull or seeker is a real entity on the grid and is retired the instant it lands on anything, which ran the full kill pipeline: every hit the party took raised the kill streak, paid streak emeralds, healed Symbiote, refunded Rampage AP, and inflated the enemies-killed total
- Special attacks always telegraph now, in every biome, for every boss. Warnings used to be dropped from the fourth biome onward, and that rule had been carved back four separate times - Void Walker's rifts, then infinite bosses, then raid bosses, then the Warden's fissure - each time because the un-telegraphed version turned out to be indefensible. The warning turn is the counterplay: it is the only thing separating a boss ability from unavoidable damage, and an attack you cannot see coming is not harder, it is a die roll. Late-game pressure still escalates, just not by hiding information - a deep-biome boss moves or strikes during its telegraph turn instead of standing still, and infinite and raid bosses stack actions per turn on top of that

The Warden

- The fissure never opens under the Warden itself. The code that picks where the arena tears has always been documented as skipping the boss's own footprint - "a boss that drops itself into the void is a comedy, not a threat" - and it never actually did it, so a crack that happened to line up took the ground out from under the Warden along with everything else
- The Warden will not walk into a crack it has already telegraphed. Its movement stops at the edge of ground it is about to delete, which matters the moment anything lets it move with a fissure outstanding
- The hole fills itself back in. From the round after a fissure opens, the ceiling of the deep dark starts collapsing into it: 1 to 5 loads of cobble come down per round on random tiles of the crack, never more than two on the same tile. The first load piles up rubble you can stand on a block down, the second brings the tile level with the floor again. A load that lands on a player or a mob deals 10 damage, which makes a half-filled crack a gamble rather than a free shortcut, and it means the arena repairs itself over several rounds instead of being permanently one band of floor poorer
- Sonic Boom is a lane, not a poke. It draws a straight line from the Warden to every player on the field, widens each line by a tile on both sides, and telegraphs the whole shape a turn ahead. Range and line of sight are irrelevant - it is sound, it goes through walls and it reaches the far corner - so the counterplay is leaving the lane, not standing outside some radius. It fires whether or not the Warden is currently hunting anyone, on a 3 round cooldown
- Anyone the boom catches is Marked, Blinded and left in Darkness for 4 turns, and while a mark is live the Warden hunts that player specifically and moves 2 faster. A thrown projectile will not pull it off a marked target: a mark is a lead it can actually follow, so the distraction trick stops working until the mark lapses, and then the Warden goes back to hunting by sound
- Marked now exists on the player side and means exactly what it means on an enemy: everything that hits you hits twice as hard. It is applied after all mitigation, the same place the enemy version is applied, so no armor or resistance is skipped by it
- Tripping a sculk sensor Marks the party for 2 turns on top of the darkness it already caused. Setting one off is announcing where you are, and in the deep dark the thing listening is the boss

Boss Movement

- Bosses clear a 4 tile gap, up from 2, and a leap costs 1 movement instead of scaling with the width. A pit dug across the approach or an arena split in half should cost the party position and time, not switch the boss off for the rest of the fight - and pricing a vault by its width made the wide gaps a boss most needs to clear the ones it could never afford. Ordinary mobs still cannot jump at all, so a pickaxe trench keeps its whole purpose against everything that is not a boss
- A vault is visibly a jump. The boss arcs over the gap with the arc scaling to how far it is going, takes longer in the air than a walked tile, and roars on takeoff and thuds on landing. Before this it slid flat across the hole, which read as a teleport or a bug

Enemy Scaling

- Trial chamber and ominous trial enemies keep the difficulty they were built with. Their stats are authored as the biome baseline times a trial multiplier, then the ordinary per-biome damage cap clamped them straight back down to campaign numbers, so from the first ominous biome onward the multiplier did nothing and "the foes within are stronger" was not true
- The ominous trial's Warden scales with the encounter instead of sitting at a flat 10 attack for the whole overworld, which left the scariest thing in the game barely above the damage cap it was already allowed to ignore
- No enemy can spawn above 18 attack. Several spawn kinds legitimately skip the per-biome cap and none of them had a ceiling of their own, so a deep-biome NG+ run could stack them into a one-shot. Bosses are unaffected - they keep their own, lower ceiling

Arena Cleanup

- Items dropped during a fight are removed with it. Arena slots are reused per level and a dropped item outlived the fight that made it, so returning to a level opened it littered with old drops and every arena the world had ever built kept leaking entities
- The sponge actually removes the water it soaks up. It was draining the tiles in the grid model only, so the water block stayed standing in the world: the arena showed a pool on ground the rules had already decided was dry, and you could still see it and fish it. Draining now runs through the same tile reset the empty bucket uses, world blocks included, and the sponge lands on dry floor rather than floating over the pool it was meant to absorb

Nether Consistency

- Water can no longer be poured in the Nether. A water bucket handed you a permanent water tile there - a Soaked source, a doubling of every lightning hit that lands on it, and a fishable pool - out of a bucket vanilla flashes to steam on contact. It reads the campaign region the same way the bed already does, because an arena physically runs inside an island dimension and is never literally the Nether. The End is left alone: water works there in vanilla, so it works here

Item Targeting

- Cobweb no longer reaches across the whole arena. It had no range check at all, so the strongest single-target control in the game - a full turn skipped - could be thrown at a boss on the far side of the map, through a wall. It now uses the same 4-tile line-of-sight rule as the snowball, egg, brick and ink sac
- Cobweb no longer webs your own allies. There was no ally check behind the "No enemy at target!" message, so clicking a tamed wolf spent the web and made the wolf skip its turn while the game insisted nothing was there
- Splash potions no longer hit your own allies. The blast list fed the enemy-side effect path, which only ever applies the harmful half of a potion, so a pet caught in the radius took the harming, poison or wither branch with no healing branch to answer it - a splash Instant Health did nothing to a wounded wolf and a splash Harming killed it. Every other area effect in the mod already filtered allies out
- Buckets are poured at arm's length. Water, lava and the empty bucket had no range check of any kind, so lava could be poured onto a tile on the far side of the arena and the empty bucket could delete someone's lava moat from across the map. They now need an adjacent tile, which is the rule the sponge and the jukebox sitting beside them in the same file already demanded for the same physical action
- The bell and the spore blossom are set down next to you as well. Both place a block and fire an area effect the instant they land, so with no reach limit the bell was a mass stun deliverable to a boss standing well outside its reach
- Fire charges answer to the throwable rule. The charge had no range limit and no line of sight check on either of its branches, so it could light the ground under a boss through a wall on the other side of the arena. It now uses the same 4-tile line-of-sight rule as the snowball, egg, brick, ink sac and cobweb
- Fire charges no longer burn your own pet. An ally is alive, so a charge aimed at one fell straight through to the damage branch - the same hole the cobweb and the splash potion each had
- Anvils no longer drop on your own allies. The target test checked that something was alive but never that it was an enemy, so clicking a tamed wolf dropped an anvil on it for half its maximum HP

Combat Resolution

- A sword sweep no longer hits a big enemy once per tile it stands on. A 2x2 spider occupies several of the squares around your target and was returned once for each, so one swing landed on it two or three times - and a two-target sweep could be spent entirely on that one mob while the genuine second neighbour beside it was never touched
- The Enderman lands next to you when it blinks in. Its four approach tiles are offsets from your position, and when it started off-axis every one of them came out diagonal - two tiles away, not adjacent - while the teleport-and-strike action still resolved into a melee hit regardless. It now collapses onto the dominant axis first, the way the spider and cave spider ambushes already do
- Area items count a big enemy once. The same "listed once per tile it stands on" mistake the sword sweep had was also in the bell and the spore blossom, so one blossom took 4 speed off a 2x2 spider while the zombie beside it lost 1, and the bell reported three enemies stunned when it had stunned one. The jukebox was doing the same thing in the party's favour, handing a multi-tile ally its speed buff once per tile

Hub Effects

- A potion drunk in the hub keeps the duration it was drunk with. Frozen effects each record their own length and there is a helper that honours it, but the unfreeze that runs when combat starts overwrote every one of them with a single blanket value, so a 3-turn buff and an 8-turn buff both opened the fight at 5

Achievements

- Five boss achievements named the wrong boss. The descriptions were shifted one biome late, so killing the Ender Dragon read "Dragon Slayer - Defeat the Void Herald", and "Defeat the Hexweaver" and "Defeat the Void Walker" each appeared on two different achievements. Soul Sand Valley, Warped Forest, Basalt Deltas, Outer End Islands and Dragon's Nest now name the boss you actually fought, in both the toast and the advancement entry
- The weapon-skills section header still counted the feat that was removed for having no spears in 1.21.1

Raid Bosses

- Deleting a raid boss actually removes it. Every definition registers a boss AI under its own key, and nothing ever unregistered one: `/raidboss edit delete` and a reload after removing a JSON both dropped the definition while leaving a live AI factory behind, still holding the boss that no longer existed. Registering something now has a matching way to retire it

Config Screen

- Four config sections showed their raw translation key as a heading. Infinite Scaling, Death Penalty, Bug Reports and Daily Raid Bosses were declared in the config but never given a name, so Mod Menu printed strings like `text.config.craftics-config.section.raidBosses` where the heading should be

Documentation

- The jukebox tooltip and its trader label both advertised +1 ally Speed. It grants +3, which is what the item's own message and the guide book have always said
- The Raiser set bonus promised tamed allies "+2 Speed and +1 Attack". The speed half is implemented nowhere - Rally has only ever added attack - and it was quoted in both description tables at once
- The Armor Class summary quoted a 40% dodge cap. The cap is 60%. It also still listed Resistance as an Armor Class source, which the body of the same method has a note explaining it is not: Resistance reduces incoming damage directly
- The enemy-defense table's full-set totals were computed as though the compression ran once over the set. It runs per piece, so leather is 4 not 2, iron and copper 6 not 4, and netherite 10 not 8 - up to 2 points, a tenth of an enemy's whole mitigation budget
- The README claimed version 0.2.10 in its fact box and 0.1.0 in its status section, and headed a nine-biome Overworld table "8 Biomes" three lines above text saying you visit all nine
- The README has been rewritten from 814 lines to about 130. Most of what it said was wrong and outdated.
- The guide book described a Sonic Boom that did not exist: "a straight-line blast that IGNORES defense", when it was neither a line nor unblockable. Its entry, the Warden's page and the fissure are now written the way they actually behave
- The guide book now states the reach on the items that just gained one, so the bell, spore blossom and the buckets say they are placed next to you and the fire charge gives its throw range

0.3.6.2
Combat, Events and Arena Fixes

- Sandstorm's blindness now lasts 2 turns. It was configured for one turn despite the effect needing to cover the following enemy turn as well.
- Event traders now open the same banked-emerald Trading Hall screen as the lobby instead of converting currency into physical emeralds and opening a vanilla trade UI. Offers refresh after each purchase, stock is shared within the event, purchases work even when your inventory is full, and party members take turns through the shared merchant without blocking one another.
- Any combat reward that cannot fit in your inventory now opens the managed loot chest rather than dropping on the ground. Lootboxes use the same delivery path, so their rewards follow the same rule.

Arena Terrain

- Void Rift now makes a real visible shaft through the arena floor instead of only lowering the surface into a shallow pit.
- Outer End Islands uses its packaged arena schematics until the editable disk copies are regenerated. The local overrides for that biome had been replaced by Soul Sand Valley layouts, causing arenas 2 and 4 to load the wrong map.

Client Stability

- Fixed the `Pose stack not empty` client crash when sculk darkness hid an entity during a bounce animation. The entity renderer now restores its transform with `try/finally` on every supported Minecraft version, including when another render mixin cancels the draw.
- Abandoned combat cleanup now removes every transient arena-tagged entity. A client crash or disconnect can no longer leave an untracked, non-interactive Warden behind to overlap the next boss attempt.

0.3.6.1
Bleed

- Bleed was ending fights by itself and has been halved. It charged the full triangle of your stack count - 1, 3, 6, 10, 15 and up - which compounded far faster than stacks come off, since they decay one a turn while a Sharpness V sword alone adds five per hit, before Serrated, Piercing or Impaling add theirs. One swing was 15 damage a turn, two was 45, three was 91, past most things' entire HP pool. It is half that triangle now - 1, 1, 3, 5, 7, 10, 14 - so the same three swings read 7, 22 and 45
- Bleed also has its own ceiling of 50 a turn, rather than sharing the general 100 damage-over-time cap with burn. That cap was never a real limit on bleed, which reached it in three hits; 50 now needs fourteen live stacks, and a big enemy's bonus damage counts against it instead of being added on top of it

Raid Bosses

- A finished raid no longer drops you into the void. Sending a raider back to where they joined from was the one teleport in the mod that did not check for ground under the landing spot, and every hub world here is a void world, so an origin whose floor had gone - or one recorded mid-jump or mid-fall - put the player in open air over nothing. That is an ordinary death outside combat, so it took the whole inventory with it, gear you brought from outside the raid included. The landing is now clamped onto the highest solid block in that column, and an origin with no ground left anywhere in it sends you to the lobby instead
- Raid arenas no longer spawn anyone over a hole. A raid is built inside an empty void dimension, so a tile the arena marks walkable but never floored is not a pit, it is the sky. The leader's start tile and every fanned-out member's tile now require a real floor, the same check ordinary party fights have used for schematic arenas
- A raid dimension is never deleted with someone still inside it. That case used to be logged and then deleted anyway, which is the worst outcome for whoever was left: standing on nothing in a world that is disappearing, with their items dropping into it. Anyone still in the arena at teardown is now moved to the lobby first. Covers a win, a wipe, an admin cancel and a server stop

Taming

- Taming an animal with food now actually works. Every taming item that is also something you can eat - cod and salmon for cats and ocelots, carrot, potato and beetroot for pigs, golden apple and golden carrot for horses and donkeys, sweet and glow berries for foxes - was being caught by the "eat this" branch first and swallowed for a couple of HP. Those mobs could not be tamed at all. Taming is now checked ahead of eating, and only claims the click when the tile in front of you actually holds a live untamed animal that item breeds, so the same item still eats normally everywhere else

Kill Streak

- Your kill streak no longer carries into the next fight. It was only ever cleared on a turn that ended without a kill, so a fight that ended on a killing blow - which is most of them - left the streak standing. The next fight then opened with a free damage multiplier before anything in it had died, up to +90% in leather and compounding on a Feral trim. It now resets with the rest of the per-fight state when a new fight starts

Achievements

- Whirlwind, Shockwave and Coral Reef each needed one more enemy than they advertised. The count they were given excludes the enemy the swing already hit, so "hit 5 enemies with a shockwave" was really six, and sweep and splash were off the same way. The Crossbow's Pierce feat had this right and the other three were copied without it
- Reef Dweller now checks the Coast trim it has always asked for. Without that check it was Coral Crusader with turtle armor added, so a single boss kill handed over both

Potions

- Splash and lingering potions count as Special casts again. The check behind that only recognised the drinkable potion, so the two hoe enchantments that key off Special - Performative's free double-cast and Reserving's AP refund - silently never fired for a thrown potion

Documentation

- The campfire's own comment said it healed 1 HP to anyone adjacent. It heals 2, to anyone standing in its 5x5, which is what the item's message and the combat code have both always said
- The echo shard's comment said it returned you at the start of your turn. It returns you at the end of it
- The Wither's summary still described 3HP skulls dealing 5 damage inside a 2 tile decay aura. The skulls have been 6HP and 7 damage for a while, and the aura is 3 tiles, 4 in phase two

0.3.6
Combat and Lootbox Updates

- Raid bosses now load from the bundled boss JSON files at runtime, so new bosses appear without manual config copying.
- The Special lootbox cache now includes Artifacts and More Totems as ultra rare rewards.
- Combat enchantment tooltips now show effect descriptions for compat items and enchanted gear in inventory and battle views.

Raid Bosses

- Raid boss attacks now show telegraphs again.
- Raid announcements now appear as toasts instead of chat lines.
- Players now get warnings 30 and 10 minutes before a raid, not just 1 hour before.
- Large bosses are now solid across their full body.

Lootboxes

- Players can read lootbox odds by punching a lootbox.
- Right clicking opens a confirm screen with the price and emerald balance.
- Right clicking while holding a key opens the lootbox directly.
- The kiosk hologram is easier to read and no longer uses dark gray text.
- Weapon, armor, trim, and enchantment pools now come from the game's registries.
- Netherite weapons are locked to the legendary tier and diamonds to rare.
- Every coral type appears in the weapon cache at uncommon.
- The Material Crate is now a real crate with useful rewards and better quantity ranges.
- Simply Swords relics are no longer handed out as weapons.

Enchantments

- Sharpness, Smite, Bane of Arthropods, Knockback, Serrated, and Sweeping Edge now work on all melee weapons.
- Lootbox enchantment levels are now capped to what each enchantment supports.

Movement

- Blocks can be placed in water and lava to create bridges.
- Walking through lava now burns you.
- The pathfinder now jumps lava channels instead of wading them.

0.3.5
Daily Raid Bosses

- Once a day the server calls out a raid boss an hour before it arrives, then opens a five minute window where anyone can type /raidboss to join. Up to eight raiders share an arena, and the ninth joiner opens a second arena rather than being turned away, so a full server can field as many simultaneous raids as the admin allows
- Raid bosses are authored, not generated. Each one is a JSON file in config/craftics/raidbosses naming its mob, its health, a movepool of six to eight attacks drawn from every boss in the mod, its emerald bounty and its own loot table. Admins can write them by hand or build them in game with /raidboss edit, and both write the same file
- Arenas can carry their own hazards too. A boss can scatter lava, water, ice, powder snow, mud or cover through its own fight, authored per boss and rolled fresh every time it runs, so the same raid boss never opens onto an identical floor twice.
- Every raid boss starts the fight already ahead: it either acts twice per turn, or carries a permanent buff it can never lose. Strength, Resistance, Speed, Regeneration, Absorption and Fire Resistance are all available, at any level
- Health is fixed, never scaled to how many showed up. A raid can technically begin with one player, and one player will almost certainly lose
- Win and everyone who stayed collects the bounty and two drops from the boss's table, including anyone who was downed along the way. Wipe and you get the usual defeat screen with none of the usual penalties, since raiders bring their own gear rather than a run's
- Leave and you get nothing. A raider who goes quiet has their turn ended after 45 seconds, and a second timeout removes them from the raid entirely, because seven people should not wait on one
- The day's boss is picked at random by weight and will not repeat for a configurable number of days, a week by default
- Raids run in their own temporary dimension and put every participant back exactly where they were standing when they joined

0.3.4
Soul Fire Off Soul Ground

- Soul fire is a real block anywhere now. Outside a soul sand valley it was only ever a puff of blue particles, because the game itself deletes a soul flame the moment it notices there is no soul sand or soul soil beneath it: the flame was placed, the floor was wrong, and the next thing to disturb that tile wiped it out. It now scorches the ground it lands on into soul soil first, so it has footing it is allowed to stand on and it stays lit
- The scorched ground lasts only as long as the flame does and reverts on its own as the tile burns down to magma and then back to whatever it started as. None of this showed before because soul fire could previously begin nowhere except on soul ground, the one floor that was already correct, and everything new that lights soul fire elsewhere depends on it

The Ender Dragon

- Every one of the dragon's flame attacks is soul fire now.
- The breath no longer paints a patch of fire that sits for a few turns and times out. It lights the ground and hands it to the arena, so the fire spreads outward a ring per turn, collapses to magma behind the front and burns itself out. The shape you dodge is only where the fire starts
- Breath Wave was a wall of flame that marched three tiles a turn under its own power. It now breathes a line of soul fire along the edge nearest you and lets the fire do the walking, so it arrives more slowly and does not stop coming. It is telegraphed now too, having been the one dragon attack with no warning at all: fair enough as a visible wall crossing the arena, not fair at all as a line that lands quietly and then creeps
- Every dragon attack now warns, damages and burns exactly the same tiles, where the tiles it set alight used to be only a part of what it had warned you about. The Swoop also hits the whole corridor it warns rather than only the single tile you were standing on, so anyone else caught in the lane no longer watches the dragon pass straight over them and take nothing
- The breath has its own colours on impact instead of a generic burst: dragon's breath, portal motes and soul flame

The Wailing Revenant

- Magma Rows is now Soul Ember. Instead of turning whole rows of the arena into fire that expired after two turns, it drops a single burning ember, three once it is enraged, and leaves the rest to the fire. The ember falls out of the sky trailing soul flame and ash and flares as it lands, with several falling staggered rather than in step
- That also fixes something you could watch happen: the rows it painted came up orange while every tile the fire spread to from them came up blue, because soul sand catches as soul fire on its own. The seed was the only tile in the whole burn that was the wrong colour
- Soul sand valley is the one arena where this is a fair thing to drop, since its floor rebuilds itself after burning instead of scarring to ash, so the fire sweeps the room and the room grows back
- The ghast stays off the stage now. From its second turn onward it had been walking into the corner of the arena and fighting you from there, which quietly dismantled the entire fight: a boss built to hover out of reach and be answered at range was standing next to you, hittable with a sword, while the reflected fireball that is supposed to be the whole point of the encounter became a formality. Nothing can relocate a boss that fights from off the stage any more, whatever tries
- You cannot melee it, and you can now click it. The tiles it owns along the front row are an aiming surface rather than a body, but reach was being measured against them, so standing on the front row let you punch a ghast hovering ten blocks past the edge, and melee weapons no longer light those tiles up as attackable either. Clicking the ghast's own body now shoots it, using whichever of its tiles is nearest you, since the game had only ever looked for click targets on the arena floor and one tile past it
- Reflected fireballs hit it for a flat 50. They used to deal a twentieth of its maximum health, which meant the harder the fight scaled the weaker its one real counterplay became

Armor Defense

- Armor now gives a small flat damage reduction on top of Armor Class, so failing a dodge in full diamond is no longer identical to failing one in nothing. Full sets: Leather 0, Gold 0, Chainmail 1, Iron and Copper 2, Diamond 4, Netherite 5. Gold gives none by design, since it is the Gambler set and is priced for crits rather than protection
- A hit always deals at least 1, and only enemy attacks are reduced, so lava, sculk jaws and your own bed still bite for full. Mixed sets average smoothly because each piece contributes a quarter and the total is divided once at the end, so two diamond plus two iron gives 3. The values derive from each material's existing AC base rather than a second table, so modded armor is covered automatically
- Armor tooltips now show the set's defense, and the guidebook and combat docs cover it

New Item Uses

- Bed (2 AP): in the Overworld it is a respawn anchor. It does not grant a revive, but if a Totem of Undying triggers you come back beside the bed instead of where you fell, which turns a totem from a one turn reprieve into a real escape. The game picks the side furthest from enemies, and if the bed is hemmed in you simply revive where you fell. Any color works, including modded ones
- Bed in the Nether and End (2 AP): explodes instantly instead, reaching a tile further than TNT and hitting far harder, with most of its damage scaled to the target's max health. It has to be placed next to you, so you are always standing in your own blast. TNT can be lobbed anywhere and only costs a round of fuse, so without that the bed would simply be a better bomb
- Respawn Anchor (2 AP): the same item from the other side, anchoring in the Nether and exploding in the Overworld and End. Both now share one rule: each works only in its home region and detonates everywhere else
- End Crystal (2 AP): sits inert until something destroys it, then detonates across 3 tiles scaled to enemy max health. It is the only bomb where you pick the moment, so the play is to place it in a choke, let them gather, and pop it from range. Anything can set it off, including your own splash damage, and the blast catches you like everything else
- Dragon's Breath (1 AP): leaves soul fire on the target tile with soul soil scorched underneath. It goes in as dragon fire rather than a struck light, so it needs no fuel and takes hold on ground a flint and steel would refuse, then burns and spreads like any other soul fire
- Bone Meal (1 AP): grows tall grass on bare ground. Cover was the one part of the stealth loop that could only ever run down, since grass hid you, shears harvested it and enemies tore it up, but nothing ever made more of it
- Tall Grass and Large Fern (1 AP): can now actually be placed. Shears have been handing them to you since they were added, but combat had no branch for putting them back down, so harvested cover was a one way trip
- Ink Sac (1 AP): blinds one enemy for 2 turns, and a blinded enemy fumbles its turn and deals no damage. A cheap single target skip button for whatever is about to hit hardest, matched to the Tentacled Totem's duration
- Chorus Fruit (1 AP): heals, then throws you to a random safe tile. The destination is random rather than aimed on purpose, which keeps it a panic button instead of a better ender pearl. It was previously treated as ordinary food and did nothing else
- Ice, Packed Ice and Blue Ice: all place a sliding tile, and stopping on ice does not stop you. You carry on to the end of the ice and one tile past it. Ice melts to water after 3 rounds, packed ice is permanent, and blue ice (2 AP) lays a 3 tile runway. Enemies shoved onto ice skid too, and slides resolve like any other shove, so one ending in lava or off a ledge does exactly that
- Wolf Armor (1 AP): +3 defense on one wolf ally for the rest of the fight. Wolves only, one set each

Fixes

- Powder snow can no longer be lit. It is not fuel, but the check for what can hold a flame only refused water, lava and open void, so powder snow fell through and accepted a light. The flame could not survive standing on it so nothing was ever visible, but the tile still joined the burn cycle and spread fire to its neighbours from a fire you could not see. It now refuses every source, including soul fire and dragon's breath
- Sand, gravel and other falling blocks now actually stay where you put them when filling a void. They were being placed with a full block update, so the block fell straight through the hole it was meant to bridge. The tile still read as walkable ground while the block that made it walkable was gone, which is why filling a void produced a sunken pit instead of level floor
- That also fixes the follow-up: because the first block had vanished, placing a second one was treated as building a wall rather than finishing the fill, so a tile that looked like plain ground was silently marked an obstacle and blocked movement and line of sight
- Digging into an arena that is a thin platform over open void no longer manufactures scenery that is not there. It was sealing the sides with cobblestone, which left blocks visibly stuck to the underside of neighbouring floor tiles, and capping the shaft with the black concrete void marker two blocks down, which is a false bottom hanging in the drop. Arenas built on solid ground are unaffected and still get both
- The anvil's "Special: 10% per point to avoid wear" line only appeared on the damaged anvil, though the save has always been rolled for every condition. It now shows on all three, including the two stages where sparing the wear is worth the most
- Corrected two errors in the combat docs: the anvil was listed as a flat 5 damage rather than half the target's max health, and gold armor was listed at base 3 alongside chainmail when it has been base 2 since it became the Gambler set

Lootbox Kiosks

- Lootbox chests announce themselves. Each one carries a floating label with its name, its price and the command to read its odds, pulsing between two shades of its own colour: gold for weapons, cyan for armor, green for materials, magenta for special, purple for tomes. A kiosk used to be indistinguishable from every other chest in the room
- Type-coloured motes orbit each kiosk, and opening one bursts into fireworks, totem sparks and a flash rather than the small puff it had before
- The labels rebuild themselves from the chest registration, so they survive a restart, and nothing animates unless a player is nearby

Lootbox Loot

- The Special Cache draws from EVERY special item in the mod instead of the eleven that were hand-listed. Throwables, banners, anvils, scaffolding, campfires, jukeboxes, totems, cobwebs, flint and steel, shears, beehives, armor stands, ominous bottles, respawn anchors, end crystals, dragon breath, wolf armor, goat horns, every pottery sherd and every ice item are in the pool. It reads the same list the combat code uses, so anything that gains a use later turns up here on its own
- It pays out more too: three guaranteed picks, a 40% bonus pick, and an 8% jackpot of two more
- Legendary weapons are the full unique rosters. All thirty-five Simply Swords uniques and all seven Simply Bows uniques can drop from the Weapon Cache when those mods are installed, where it used to be six runic weapons. Without them the table still falls back to netherite
- Weapons arrive already enchanted 35% of the time and armor 30%, with a further 25% chance of a second enchantment on top, at levels one to three. Enchantments are only ever drawn from those valid for that exact item, so a crossbow never rolls a bow enchant
- Armor separately has a 20% chance to arrive trimmed, random pattern and material. The trim roll, the enchant roll and the item roll are all independent, so a piece can come with both, one or neither
- Every number above is listed in /craftics lootbox odds, with the enchant and trim chances shown as their own block so it is clear they never change WHAT you get, only how good it is

Lobby

- The lobby is protected. Blocks cannot be broken or placed, containers cannot be opened and entities cannot be interacted with within sixty-four blocks of the lobby spawn. Operators and creative mode are exempt so the room can still be built in place, and lootbox kiosks still open normally
- /spawn now works as another way back to the lobby, alongside /lobby

Island Moderation

- Islands now keep a creation record: when the island was made, the name its owner was going by at the time, the dimension it was created in and its origin. Written once, when the island first comes into existence, and never rewritten afterwards
- The dimension and origin are recorded rather than worked out on demand even though they can be derived from a player's UUID today. That derivation has already changed once, when islands moved out of the old overworld lanes, and a record that quietly recalculates itself is worth nothing in exactly the argument it exists to settle
- `/craftics island info <player>` reports all of it, along with where the hub sits now and whether the dimension is currently loaded. If the recorded dimension ever stops matching the live one, both are shown, because that difference is the whole answer
- `/craftics island info` and `/craftics island tp` now accept a plain name or a UUID instead of only online players. A moderator looking into an island is usually doing it because the owner is not connected, and the old form could not name them. `tp` opens the island if it had been unloaded, which is the normal state for an offline player's world
- Islands created before this release report their creation details as unknown rather than guessing. An audit record that invents a plausible answer is worse than one that admits it does not know
Fire

- Fire is a real thing on the battlefield now. It used to be a magma block painted onto the floor; it is now an actual flame standing on the ground, lit with flint and steel on an adjacent tile or a fire charge thrown at range
- A lit tile burns for a turn, spreads to every flammable tile beside it as it collapses into a magma block, and that magma finishes burning a turn later. Ground that has burned is spent for the rest of the fight, so a fire eats its way outward and then dies rather than circling back over the same ash
- Wood, leaves, plants and living ground carry a fire: grass blocks, mycelium and moss. Bare dirt, sand, gravel and stone floors have nothing to give, so a fire crossing a stone courtyard stops there
- Netherrack, soul sand and soul soil burn and then rebuild themselves, fireproof for a turn afterwards, so the nether floor is never permanently scarred
- Standing in flames sets you alight: Burning II for two turns, and every further turn spent in the fire adds two more rather than resetting the timer. It burns enemies on exactly the same terms
- You can strike a light on any open ground, fuel or not. A fire on bare stone simply burns where it stands and goes nowhere, which makes it a firebreak instead of a mistake. Water, lava, open void and standing walls still refuse a light
- Attack a burning tile to beat the flames out for 1 AP, the same way you clear tall grass. The ground is left scorched either way, so what you are buying is the tiles behind it

Soul Fire

- A fire lit on soul sand or soul soil comes up blue, and soul fire plays by different rules
- It needs no fuel at all. It takes stone, sand, gravel, anything it touches, and carries itself onward. Only water, lava, open void, permanent walls and ground still cooling from an earlier burn turn it away
- It also holds its flames a turn longer than ordinary fire, so the ground behind the front is still alight while the front keeps moving, and it inflicts Burning III instead of Burning II
- An ordinary fire that reaches soul ground turns blue on its own

Burning Kills

- An animal killed while on fire drops its meat cooked. Whether it burned to death or simply died alight, the fire did the cooking

Item Fixes

- Echo shards do something. Using one now returns you to the tile you started your turn on when the turn ends, which is what its tooltip has always promised. Previously the shard was consumed and the only thing that happened was a chat message, printed twice
- That double message was not unique to echo shards. Every item whose result carried an internal payload, including lava and water buckets, campfires, banners, fishing rods, anvils and goat horns, dumped its raw payload into chat and then printed the real message underneath it
- Flint and steel actually sets things on fire. It only ever applied the vanilla fire visual, which combat strips every tick, so the target visibly caught alight and then took no burn damage whatsoever. It now applies Burning for three turns, and striking an already burning target fans the fire for one more turn each time
- Nothing applies a damage-over-time effect for a single turn any more. A one turn burn or poison is a status icon that flickers and vanishes, so burns, poison, wither and bleed now last at least two turns from every source. Stuns are untouched, since skipping a turn happens whether or not the timer outlives it

It Takes a Pillage Compatibility

- The It Takes a Pillage illagers now fight in combat when the mod is installed. Nothing here touches your game without it
- Archers fire and kite exactly like pillagers: crossbow lanes, repositioning, backing off when you close in
- Skirmishers keep the vindicator's rook charge - open a straight lane to them and they come down it, hitting harder the further they travel - but unlike a vindicator they also just walk, fast, when no lane exists. Denying the lane no longer parks them; it turns them into a runner
- Legioners are a shield wall: 15 HP, slow, and carrying a flat damage reduction that grows with biome depth and CAN zero a hit outright - chip damage bounces off entirely rather than trickling through. One ranged shot in four glances off the tower shield without landing at all. Crush them with blunt weapons; blades and arrows are what the shield is for
- The clay golem serves both sides: it can turn up as an enemy, and you can build one of your own as an ally, healed with clay balls
- A new event, the Pillager Camp, can appear between levels: a mixed illager patrol - crossbows, an axe, the mod's three new soldiers, and a clay golem standing watch. Routing it pays emeralds and a Bastille Map
- The Bastille Map works like a trial key: use it during any later fight and the next stop is the Bastille - the illagers' stronghold, three garrisons back to back in one arena, each marching out the moment the last falls. The keep's garrison brings the shield wall and an evoker, with a ravager on deep runs
- The Bastille never appears on its own; the map is the only way in. Breaking it pays each of you an ominous-tier haul: a heavily enchanted weapon or armor piece, an artifact, a bundle of materials with the illagers' ravager horn among them, and a serious emerald purse

Offhand Auras

- Banners, torches and lanterns now work from your offhand as a walking version of their placed effect. A torch in the offhand is a moving radius-2 light, a lantern radius-3 - both negate darkness around you as you move. An offhand banner carries its defense aura with you: +2 Armor Class for you and any ally within 2 tiles of wherever you stand
- The cost is built in: that hand isn't holding a shield. The carried banner grants the flat base bonus - the Special-scaled bonus stays exclusive to a planted one - and multiple carriers don't stack, though a carried banner does stack with a planted one, the same way the Beacon helmet's aura does

Infinite Mode Bosses

- Infinite bosses no longer roll attacks their donor boss does not have. Six abilities in the pool had no counterpart on any boss, left behind by renames and reworks, and have been removed
- A rolled movepool now sets up its own combos. If a boss draws an attack that only pays off against a player in a particular state, the roll guarantees it also draws something that can put them in that state, so a lightning chain that wants you Soaked always arrives with a way to soak you. It will not drop the payoff to do this; it trades out a move nothing else depends on

Modded Tools

- Modded pickaxes work like vanilla ones. Breaking obstacles, digging pits and mining now recognise any pickaxe rather than the vanilla six, so a modded pickaxe no longer half worked: it could mine a rubble pile but not break a wall with the same swing

Visuals

- Crimson Forest and Warped Forest arenas have their own skies again. Both were wearing the pale overworld woodland haze, because the colour table matched the word forest before it ever reached the nether entries
- The sculk sensor range ring is much calmer. It filled every tile in the field with a pulsing cyan slab that faded fully out and back every two and a half seconds; it now traces only the boundary of the field, dimmer and slower, and never fades out entirely

Deeper and Darker Compatibility

- The Deep Dark is fully overhauled when the Deeper and Darker mod is installed. Its whole enemy roster is replaced with the mod's sculk creatures. Nothing here touches your game if you don't have the mod
- Shattered are the common melee threat, Sculk Centipedes dart in and back out again while poisoning you, Sculk Leeches drain your life to heal themselves, and Sculk Snappers are slow but lock you in place when they bite
- Angler Fish can only travel through water, where they are fast and lethal. On dry ground they are harmless, so the danger is stepping into the water with one
- Shriek Worms are a hidden ambush. They are completely invisible and cannot be targeted until someone walks into their melee range, then they rear up and lash out across three tiles, rooting whoever they hit. They never move
- The Stalker replaces the Deep Dark's level 4 miniboss. It alternates every other turn between hunting you in the open and vanishing entirely, slipping to a new spot unseen before reappearing from a fresh angle. Sculk leeches trickle in while it stalks
- New Blooming Caverns hazards: the gloomy cactus deals contact damage and sets you burning, and gloomy geysers erupt when stepped on for Burning II plus a launch of up to three tiles in a random direction
- Sludges now actually spawn. They were fully built, with slime behaviour and a Soaked hit, but had never been added to any spawn pool, so no player could ever have met one

Deeper and Darker Gear

- Warden and Resonarium gear now has real combat stats. Every piece of it was previously unregistered, which meant a Warden sword swung for bare fist damage. Tiers follow the mod's own smithing recipes: Resonarium upgrades from iron and lands between iron and diamond, Warden upgrades from netherite and sits above it
- Warden weapons cannot inherit the Netherite Sword's execute, so they get their own edge instead: the sword sweeps into a second enemy far more often, and the axe shatters armor permanently and scales harder with Cleaving
- The Warden armor set is Echo, and it cuts both ways. Wearing all four pieces keeps you permanently in Darkness, so you fight half blind with everything past two tiles invisible to you and missing from your threat overlay. In exchange it grants +2 affinity to whichever damage type you are carrying the most weapons of, re-read on every single hit, so swapping to axes mid fight moves the bonus with no re-equip. Its own affinity is Physical, deliberately the most generic lane, so it never fights the build the set bonus picks, and the tooltip names whichever affinity it is boosting right now
- Warden armor is the new armor class ceiling at 8, one above netherite. Resonarium sits at 5, between iron and diamond, and carries Special affinity

Deep Dark Props

- Infested Sculk now grows in Deep Dark arenas. Break it and Sculk Leeches and a Shriek Worm boil out of it and throw you backwards. Break it with Silk Touch and it comes away clean with nothing waking up, which finally gives a Silk Touch tool a reason to be in your bag down there
- Ancient Vases are a straight gamble. Most break open into treasure, including diamonds, emeralds, enchanted golden apples and Warden Carapace, but roughly a third of the time a Stalker unfolds out of the pot instead. Silk Touch takes the vase out whole and unopened, trading the roll for something you can carry home
- Sculk Jaws lie in the floor and bite anything that walks over them. Every jaw you cross bites, not just the first, so a row of them will end a careless move. The bite also swallows an XP level, and since XP is what pays for enchanting that is a real cost, but the jaw only holds it: break the jaw and it coughs back up everything it took, so you can eat the loss or spend a turn getting it back
- Sculk creatures are immune to jaws, exactly as in the source mod. In a Deep Dark run every enemy is a sculk creature, so the traps only ever work against you

Deep Dark Loot

- Deeper and Darker materials now drop from the biome. Sculk Bone, Soul Dust, Grime Balls, Soul Crystals, Resonarium and Resonarium Plate come from level rewards, with Warden Carapace, Reinforced Echo Shards and the Warden smithing template as the rare tail
- Every sculk creature now drops something when killed. They previously dropped nothing at all, so the whole roster was a dead end. The Stalker is the reliable source of the Reinforced Echo Shard that gates the Warden tier
- Modded loot is added at runtime rather than written into the biome file, because an unknown item id in a biome file logs a warning for every player. Nobody without the mod sees anything

Deeper and Darker Artifacts

- Sonorous Staff (2 AP): a ranged Special attack that fires a line through your target and everything behind it. Damage falls off the further down the line an enemy stands, so it rewards lining up a crowd rather than replacing a bow for single targets. The first target is hurled back two tiles
- Soul Elytra (2 AP): launch and glide up to five tiles, straight over obstacles, hazards and pits.
- Heart of the Deep (1 AP): pulse, dragging every hidden enemy into view and lifting Darkness from you. Shriek Worms stay revealed for good, but a Stalker can vanish again on its next turn.

Food

- Food healing is no longer a fixed list of items. Every edible thing, modded ones included, now heals based on its own hunger and saturation, so a modded steak is worth roughly what it looks like instead of a flat 1 HP with no tooltip
- Vanilla values are almost entirely unchanged: cooked beef still heals 5, rabbit stew still 6, an apple still 2, a cookie still 1
- Food now costs AP based on how much it heals. 1 AP normally, 2 AP once it heals 7 or more, 3 AP at 12 or more. Every vanilla food is still a 1 AP snack; the golden apples move to 2
- Food tooltips are generated from those same numbers, so modded food finally describes itself, and the Raw and Risky warnings come from the same place the eating code does

New Item Uses

- Shears (1 AP): cut tall grass, ferns or cobwebs out of the arena, and you keep what you cut. Cover becomes portable, so you can pull a bush out of a bad spot and drop it somewhere useful
- Armor Stand (1 AP): plant a decoy. Enemies go for the stand instead of you until it breaks
- Ominous Bottle (1 AP): drink it to guarantee the next between-level event is a Trial Chamber
- Beehive (2 AP): set down a hive that releases an allied bee to fight for you every round until it is destroyed

Bee Hives in the Forest

- Wild bee hives now hang in Dark Forest arenas about half the time. While one stands it releases a hostile bee every round, so ignoring it means a slowly growing swarm and the fight pushes you into spending attacks on the hive
- Break a wild hive with a Silk Touch tool and you keep the hive intact, which you can then place on your own side to spawn bees that fight for you. A forest hazard becomes a reusable summon if you bring the right tool

Enemies

- Enemies no longer stand around doing nothing when they lose track of you. Duck into tall grass and they hunt: they walk to the nearest patch of cover and thrash it open, destroying it in the process. Hiding now buys you time instead of making you untouchable, and the grass you spent is gone for good

Combat Fixes

- The Netherite Sword's execute now actually deals its triple damage against targets below 30% HP. The multiplier was being calculated and then discarded, so the hit only ever landed for its base damage. The Diamond Sword's own critical hit had exactly the same problem and now doubles properly
- The execute finisher no longer drops a crying obsidian block onto the battlefield. Those could stack on top of each other, left only the bottom block mineable, and forced you into a crawl. It now ends on a soul fire flourish and a deep toll instead
- Enemies knocked or pulled into powder snow now take freezing damage and are slowed, and enemies shoved into a sunken pit are staggered. Both tiles were being landed on with no effect at all
- Regular enemies can now be knocked clean off the edge of the arena into the void. Bosses still brace at the edge, so they cannot be shoved off the map

Quality of Life

- Torches and lanterns placed during a fight now actually appear. The light zone was being registered and the item and AP were spent, but the block itself was never placed
- Items can be dropped on the ground during combat again. Only the Move item stays locked in place
- Sculk sensors are now ordinary pickaxe-breakable obstacles instead of blocks carrying a health bar. They still paint their range ring and still trigger the darkness and silverfish ambush when you stray too close
- Home islands sit in the Plains biome instead of the void, so animals and mobs spawn on them normally and you have something to tame and farm. Islands previously used the void biome, which has no spawns at all, so nothing could ever appear. This applies to newly created islands: an existing island keeps whatever biome its chunks were already generated with

Arenas No Longer Float in Nothing

- Arenas, event scenes and trade halls are now ringed by a bank of cloud instead of ending at a hard edge with empty void past it. The cloud starts just under the floor at the build's edge and climbs the further out it goes, so you are standing in a bowl with walls above eye level rather than beside a lake
- The cloud is built from chunky blocks with real height and shading rather than flat sheets, so it has silhouette and depth, and it drifts slowly enough to read as weather rather than a conveyor belt
- A clear window is kept open along the line between the camera and the board, so the wall never stands between you and your own tiles no matter where you orbit to
- Cloud colour follows the arena's biome: dusty yellow in the desert, ochre in badlands, rain blue in the jungle, blue white on snow, red in the crimson forest, teal in the warped, violet in the End. Rain shifts the whole bank toward storm grey
- Dark Forest and Deep Dark fog is near black instead, and denser, so those arenas close in around you the way they should
- The cloud rests on the ground it meets. It pools in hollows and laps up hillsides rather than growing through the terrain
- A distance haze sits beyond the cloud to swallow far hills and treetops. It only ever tightens the view, so underwater, lava, Blindness and Darkness fog all still look exactly as they did, and it never touches the sky's own colour

The Arena Reacts To The Fight

- Boss phase two floods the surrounding cloud red and pulls the walls in closer for the length of the entrance. Killing the boss washes them gold white and opens them back up
- Darkness or Blindness drives the whole bank black for as long as the effect holds
- The camera leans in and a soft vignette closes around the screen while the enemies take their turn, then releases when control returns to you
- Every combatant now casts a contact shadow on the tile beneath them, so models sit on the board instead of hovering over it. A knocked back mob's shadow stays on the ground it is going to land on
- Landing a blow leaves an expanding ring on the struck tile, wider and gold on a heavy hit, and the camera is kicked in the direction the blow came from rather than just rattling in place. Heavy hits also flash and hold the scene still for a couple of frames
- Moving kicks up dust where a combatant lands, for enemies and allies as well as you

Weather and Hazards You Can See

- Arenas carry their biome's weather: snow on cold boards, blown sand in the desert, cherry petals, crimson and warped spores, nether ash, drifting spores in the dark places, rain in the jungle and swamp. Real rain overrides a dry biome
- Lava tiles give off heat, void pits breathe pale wisps out of the hole, and sculk fields pulse slowly, so the tiles that can kill you announce themselves without a tooltip
- Frost left by a blizzard finally does something. Stepping on rime slows you and chills you, and leather boots turn it aside exactly as they do powder snow. It has always been described as a hazard and has never once been one

Bridging and Building

- Placing a block on a void or sunken tile now drops it to ground level and fills the hole into safe footing instead of stacking a wall in mid air. The fill lasts the rest of the fight, and a second block on the same tile raises a normal wall on top of it

Boss Threats

- Bosses leap gaps up to two tiles wide to keep after you. A pit dug in front of a boss no longer takes it out of the fight for good, and a split arena no longer strands it on the far side. Ordinary enemies are still stopped cold by a pit, so digging one is still worth the AP
- The Warden tears the arena in half. From the fourth turn it opens a permanent fissure two tiles wide clean across the board, three in phase two, telegraphed a turn ahead. The ground is gone for the rest of the fight, cover on the wrong side with it, and a party can be cut apart by it
- The Wither rots the ground it stands on. Every tile its body covers is permanently withered, and each charge leaves a scar behind the fire trail. Withered ground saps you and applies Wither every time you step onto it, so the safe half of the board shrinks the longer the fight runs and standing still stops being free

Arena and Combat Fixes

- Rails, pressure plates, thin snow and other flat decorations lying in a pit no longer count as its floor. The game read them as solid ground, called a two block hole a shallow dip, and let you walk in and fall straight through
- Falling into a pickaxe dug void is fatal again. Its floor sat one block above the depth the fall check was watching for, so you stood at the bottom of a lethal hole perfectly alive while the fight kept swinging at where the board thought you were
- You can no longer walk onto a tile with nothing under it. The board is checked against the world before a move is accepted, and a tile that claims to be walkable over a hole is corrected on the spot
- A killing blow can no longer land during your own death animation. Anything that hit in that window killed you outright, opening the vanilla death screen on top of a fight still playing out its ending
- Water throwables deal real Water damage. Turtle eggs, pufferfish, nautilus shells and hearts of the sea ignored every water resistance and weakness in the game, and none of them counted as water damage for the achievements built around it, so a water only run disqualified itself. They also splash and sound like they hit something now
- Six collection achievements could never be earned by anyone. Trim patterns, trim materials, a full matching trim set, armor sets owned, pet species tamed and goat horn variants are all tracked and granted now, and they carry across runs
- Arenas built with blocks from a mod you do not have are skipped rather than loaded full of holes. Deep Dark arenas built on Deeper and Darker blocks were loading for everyone; without the mod every one of those blocks became air, which reads as a floor full of invisible death pits. The bundled vanilla arenas are used instead, and installing the mod brings the custom ones straight back
- Leaving a world mid fight no longer risks a crash on the way out

0.3.2
Biome Minibosses

- Every biome now has a unique miniboss on level 4 (biomes are 7 levels each now, except the Dragon's Nest which stays 3). Each is either an event with its own hazard or a literal miniboss enemy
- Overworld: Plains Graveyard (graves raise zombies), Desert Sandstorm (husks + reinforcement waves), Jungle Broodmother (elite spider + spawns), Forest Pale Garden (the Creaking, now on level 4), River Flash Flood (rising water), Snowy Blizzard (snowy creeper waves), Mountain Rockbreaker (elite golem + falling rock), Cave-In (collapsing ceiling), Deep Dark Swarm (silverfish and skeleton waves)
- Nether: Fire Rain (raining embers), Bone Colossus (elite wither skeleton), Fungal Bloom (spreading spores), Warped Enderman (elite + endermite swarm), Magma Surge (erupting lava vents)
- End: Void Rift (crumbling platform), Shulker Sentinel (elite shulker), Chorus Bloom (spreading chorus)
- Miniboss levels pay bonus emeralds and a richer loot roll

Biome Weather

- Biomes now have persistent weather that kicks in partway through and lasts the rest of the biome
- Snowy (from level 4): blizzard winds telegraph with floor arrows, then drag every fighter one way
- Jungle (from level 2): rain churns grass and dirt into mud - crossing a mud tile has a 50% chance to stop you (or an enemy) in place
- Desert (from level 4): a sandstorm blinds everyone for a turn every few rounds
- River (from level 1): a current runs through the water. Every two rounds it warns with flow arrows on every water tile, then sweeps anyone still standing in the water toward the nearest bank. Where the water sits on one side it all flows that way; on water-ringed arenas it pushes outward in every direction at once
- Every weather effect now has its own ambience: falling rain, drifting snow with a wind howl, blowing sand, plus one-shot sound cues on the special-level events
- Fixed biome weather not activating at all in game (the effect settings were being dropped when biome levels were renumbered)
- Fixed the warning arrows for weather pushes (blizzard gust, river current) never showing - they now use the same persistent arrow telegraph as the Frostbound Hunter's gust

Biome Hazards

- Crimson Forest levels now scatter crimson fungus across the floor - walk through a patch and it makes you bleed
- Warped Forest levels scatter warped fungus that warps your movement for two turns when crossed, on top of the biome's periodic warp
- Crimson and warped fungus can be cleared for 1 AP by attacking it, like breaking tall grass
- Crimson and warped fungus, mud, and the sculk sensor range ring now show a hover tooltip explaining what they do
- Snowy biomes can now grow large ferns you can hide in (and break) like the plains grass
- Warped Forest now has a persistent warp effect: starting turn two and every other turn, your movement is mirrored (attacks are unaffected)
- Fixed Warped movement being unusable when the mirror tile was blocked: the highlighted tiles are now the mirror of where you can actually reach, so every green tile is a legal move
- Jungle levels can open with a standing mud bog (an irregular patch), separate from the rain's ongoing mud
- Jungle arenas now use grass and moss floors instead of mud, so the rain has fresh ground to churn
- Fixed pre-placed stage cobwebs doing nothing when walked through

Sculk Sensors

- The Deep Dark now has sculk sensors on every level (1 to 3 each), ringed by sculk blocks marking their range
- Step within 2 tiles of one and it shrieks: darkness on the whole party for a turn and a wave of silverfish spawns next round, warned in red where they'll appear
- Sensors re-arm every other round. Wearing Swift Sneak lets you walk past without setting them off
- Destroy a sensor by attacking it, but only from outside its range - it senses you if you're too close

Infinite Mode

- Infinite runs now use their own emerald wallet, starting at 10. Your real balance is stashed with your items and returns when the run ends; run emeralds evaporate with the run
- Fixed a co-op party wipe not ending the run when the host wasn't the party leader (players got hit with death penalties and stuck stashes instead)
- Fixed the Pale Garden level spawning at campaign-forest difficulty instead of scaling with run depth
- Fixed emerald rewards scaling with the rolled biome's campaign position instead of run depth
- Fixed NG+ campaign progress making infinite enemies harder. infinite difficulty now depends only on how deep the run is
- Fixed enemies in late-campaign biomes (like Soul Sand Valley) hitting far too hard when rolled early in a run

Combat

- Dual-wield second strikes (daggers and sais) now deal 75% of the offhand weapon's own damage instead of the main hand's - no more free damage from offhanding a cheap dagger
- Fixed the enemy/ally/player inspect panel not appearing on hover in battle
- Scaled down multiplayer mob and boss health scaling to 0.7x instead of 0.95
- Five enchants now do something in combat. Efficiency: 10% per level to reduce an action's AP cost by 1 (mining and attacks). Fortune: chance at bonus loot when mining or on a killing blow. Silk Touch: mined obstacles drop their block, and killing blows can drop the enemy's head. Respiration: swim deep water for a few turns before drowning. Swift Sneak: extra speed on your first turn
- Efficiency, Fortune, and Silk Touch tooltips now describe what they actually do on the item they're on (pickaxe vs weapon)
- Night Vision Goggles now grant immunity to Blindness and Darkness (previously +1 attack range)
- Blindness now cuts attack range by 2 per level (Darkness cuts 1, and they stack)
- Bleeding and Burning ticks are now capped at 100 damage so a runaway stack can't one-shot a full-health target
- Fixed weather debuffs (blindness, darkness, slowness) wearing off before your next turn instead of lasting it
- Fixed the Move tool being throwable onto the ground, and fixed items vanishing instead of dropping when you press Q

Event Effects

- Every biome miniboss now throws particles: lava and flame on erupting vents, splashes on the flood, dust on falling rubble, portal swirls on void collapse and enderman spawns, spores on the fungal blooms, and spawn puffs on every reinforcement wave
- The Void Rift now warns which ring of the arena will crumble a full turn before it drops, instead of vanishing under you with no notice
- The Magma Surge telegraph now marks the doomed vent tiles in red with a hiss, so you can see and dodge the eruption
- The Chorus Bloom now tells you (with a sound and message) when it confuses an enemy standing in the grove
- Crimson and Warped Forest now have their own drifting-spore ambience and biome sound loop, and the sculk sensors give off a faint sculk haze so you can spot them

Visual

- New skull icons for the Poison and Wither status effects

Boss Attacks

- Boss attacks were reworked to claim space and feel threatening instead of poking single tiles. Attacks now sweep full lanes, rings and multi-zone barrages, and every telegraph paints exactly the tiles that will be hit so you always know where to stand
- The Sandstorm Pharaoh's Curse of the Sands finally does something: it is a telegraphed hex that, once it lands, sprouts a live sand mine on every tile you step off for the next few turns. Dodge the initial cast and the curse never sticks. Its Sandstorm also grew from a 3x3 to a 5x5 storm that saps your strength
- The Molten King's Lava Cage now walls off the full ring around you with a single escape gap on the side away from the boss, instead of a few loose embers. The Ashen Warlord's phase 2 Fire Pillar erupts as an eight-lane star of flame. The Tidecaller's Trident Storm lands as three overlapping splash zones so a lazy sidestep walks you into the next one. The Chorus Mind's phase 2 Chorus Bomb detonates a full 5x5 and drags you toward the boss
- Infinite Mode bosses share the same upgraded movepool, so every random boss reads clearly and leaves real counterplay

Combat Effects

- Darkness is now a fog of war instead of a minor range cut. While you are in Darkness, any enemy more than 2 tiles away vanishes from your screen - model, threat tiles, roster and hover - until it closes in or the effect ends. Only you are blinded; teammates see the battlefield normally

Multiplayer

- Dialed back enemy HP scaling in multiplayer. It was ramping too hard, so the per-extra-player bonus was lowered (2-player fights are now 1.75x enemy HP instead of the previous heavier curve)

Crash Fixes

- Fixed a startup crash on 1.21.3, 1.21.4 and 1.21.5. The inventory stat-panel click handler targeted a method that only exists on 1.21.1, so the mod failed to load at launch on the newer versions. It now targets a method present on every version
- Fixed a crash for players wearing the Copper Helmet (Copper Age Backport). A duplicate armor-material registration left holes in the client's registry that crashed the wearer mid-render; those holes are now splinted on join so the game stays stable (and the offending mod is named in the log)
- Fixed a multiplayer meltdown when someone timed out mid-combat: the cleanup ran off-thread and desynced positions, fall-killed the remaining party, and left duplicate player entities behind. All disconnect cleanup now runs on the server thread

Raids & Bosses

- Raids now give you a turn to get into position before the pillagers open fire, instead of being shot the instant you arrive
- Pillagers only fire in straight lines now (down a row or column, with a clear line of sight) - break the line and you break their shot
- The raid finale ravager no longer spawns half off the arena; large mobs validate their full footprint and land on solid ground
- The Frostbound Huntsman actually moves now - it kites, repositioning to hold its range and shoot on the move instead of standing still
- Bosses no longer spawn on top of void tiles (the 2x2 boss footprint is fully checked against pits)
- Infinite Mode bosses no longer nuke you from across the arena with no warning: their attacks are range-limited and always telegraphed, and a boss's held weapon now matches how it actually attacks

World Generation

- Fixed arena floors being hollow underneath, so a raised wall on the edge no longer lets you see into the void below the ground
- Fixed border fences and walls floating above snow layers and slabs; they now rest flush on the surface
- Mountain-biome void tiles now scatter in clumps across the board instead of always sitting dead center

Mid-Biome Events

- Deserts now brew sandstorms from level 4 on: every few turns the storm breaks and blinds the whole party for a couple of turns - a warning turn lets you close the distance first

Combat

- The Blaze Rod's burn now lasts its full duration instead of fizzling before your next turn
- Knockback can now shove enemies into pits again
- Rotten flesh now correctly applies Weakness to party members (not just the host)
- The Crossbow no longer reads "0 range" (and stays usable) when wearing a Skeleton Skull
- Thrown tridents are all recovered at the end of the level - throwing several no longer loses all but one
- Timberfall now also triggers when you mine an obstacle with a pickaxe while holding a Timberfall axe in your offhand
- Shield tooltip/help now correctly says it grants +1 AC (not DEF)
- Extended (long) potions now show their real longer duration on hover instead of a flat 3 turns

Bug Fixes

- Fixed a major progression bug where completing a mid-run event could send you to the wrong biome and then bounce you back and forth between it and the correct one
- Fixed the Infinite Mode class-select screen rendering blurry
- Fixed the level-up point menu vanishing instantly after beating an Infinite boss, so you can actually spend the points now
- Artifact-granted regen is now labeled by the artifact (e.g. Onion Ring) in chat instead of "Trim regen"
- Restyled the hover panel over players and enemies to match the rest of the mod's UI

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
- /craftics lobby rebuild (op): re-paste the central hub from the bundled schematic in place, no world reset needed. Re-place lootbox chests and scoreboards inside its footprint afterwards
- Fresh dedicated servers now default to the Craftics world type on their own: if no world exists yet and level-type was left at vanilla default, server.properties is set to the Craftics preset before the world generates. Existing worlds and deliberate level-type choices are never touched; level-type=default keeps vanilla terrain

Placement Rules

- Special blocks (campfire, banner, torch, lantern, jukebox, scaffolding, honey/slime block, cactus, cake, spore blossom, lightning rod, powder snow) now require flat, solid ground: no more planting a campfire over the void or a banner in lava. Void, sunken pits, water, deep water, lava, fire, powder snow, obstacles and stairs all refuse placement with a clear message

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


