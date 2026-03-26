# Pottery Sherd Spells — Design Document

Ancient magic sealed within pottery sherds. Single-use spell scrolls found through archaeology — rare, powerful, and worth building your turn around.

All spells deal **MAGIC** damage type (benefits from Gold armor +2, Netherite +1, Fire Resistance +1) unless noted otherwise. All are **consumed on use**.

---

## AP Cost Tiers

| AP | Philosophy | Comparable |
|----|-----------|------------|
| **2** | Quick utility — repositioning, simple heals | Ender pearl (1 AP), food |
| **3** | Solid single-target — debuffs, conditional damage, setup | Bell stun (2 AP), trident (2 AP) |
| **4** | Strong — high damage, AoE, life steal, big buffs | TNT (1 AP but weaker), splash potion |
| **5** | Powerful — mass disable, artillery, turn-defining | Nothing comparable — these are premium |
| **6** | Ultimate — game-changing, boss-killer | Nothing. This IS your turn. |

---

## 2 AP — Quick Casts

### Explorer Sherd — "Phase Step"
- **AP:** 2 | **Range:** 4 tiles | **Target:** Any walkable tile
- Teleport to target tile, **ignoring obstacles and enemies in path**. No HP cost (unlike ender pearl). Also **reveals all enemy stats** (like spyglass).
- **Sound:** Enderman teleport
- **Animation:**
  1. **Cast:** Swirl of `END_ROD` particles (10) spiraling upward around player position (spread 0.3, 0.8, 0.3)
  2. **Travel:** `PORTAL` particles (8) along direct line from origin to destination (not arced — instant displacement)
  3. **Arrival:** Burst of `END_ROD` particles (12) expanding outward at destination + `REVERSE_PORTAL` particles (6) settling downward
- *The explorer mapped a path no one else could walk — through the walls themselves.*

### Friend Sherd — "Guardian Spirit"
- **AP:** 2 | **Range:** self | **Target:** Self-cast
- Heal **5 HP**. If the player has a **tamed pet/ally** in combat: also heal the pet for **5 HP** and give it **+3 attack** for 2 turns.
- **Sound:** Cat purr + experience pickup
- **Animation:**
  1. **Cast:** `HEART` particles (6) float upward from player (spread 0.4, 0.5, 0.4)
  2. **Pet link (if ally present):** `HAPPY_VILLAGER` trail particles (8) along line from player to pet + `HEART` particles (4) at pet position
  3. **Glow:** `ENCHANTED_HIT` particles (5) shimmer on pet briefly
- *Friendship is a force multiplier.*

---

## 3 AP — Standard Spells

### Heart Sherd — "Mending Light"
- **AP:** 3 | **Range:** self | **Target:** Self-cast
- Heal **10 HP** + **Regeneration II** for 3 turns.
- **Sound:** Experience level up
- **Animation:**
  1. **Cast:** Golden `ENCHANTED_HIT` particles (12) spiral upward around player in a helix pattern (2 loops, radius 0.5)
  2. **Pulse:** `HEART` particles (8) burst outward at chest height (spread 0.5, 0.2, 0.5)
  3. **Linger:** `EFFECT` particles (6) drift upward slowly for regen visual (speed 0.01)
- *The heart on the sherd still beats — and yours beats stronger.*

### Scrape Sherd — "Corrode"
- **AP:** 3 | **Range:** 3 | **Target:** Enemy
- **3 damage** + **reduce target defense by 5** for 3 turns. Stacks with other defense reductions. Turns tanks into paper.
- **Sound:** Grindstone use
- **Animation:**
  1. **Cast:** `ITEM_SLIME` particles (4) coalesce at player's hand position (tight spread 0.1)
  2. **Projectile:** Green-tinted trail using `ITEM_SLIME` (8) + `DRIPPING_OBSIDIAN_TEAR` (4) along arc from player to target (standard trailParticles arc)
  3. **Impact:** `ITEM_SLIME` burst (15) engulfing target (spread 0.4, 0.6, 0.4) + `FALLING_OBSIDIAN_TEAR` (6) dripping down from target's top — armor melting visual
- *Oxidation accelerated to seconds — armor crumbles like ancient copper.*

### Angler Sherd — "Riptide Hook"
- **AP:** 3 | **Range:** 3 | **Target:** Enemy
- **Pull** target **2 tiles toward** player (reverse knockback) + **4 damage**. If target ends adjacent to player, deal **+3 bonus damage** (reeled in).
- **Damage type:** WATER
- **Sound:** Fishing rod cast + bobber splash
- **Animation:**
  1. **Cast:** `FISHING` particles (3) at player hand
  2. **Hook:** `CRIT` trail particles (6) from player TO target (low arc, fast) — the line going out
  3. **Snag:** `SPLASH` burst (8) at target's original position
  4. **Reel:** `BUBBLE` trail particles (6) from target's old position TOWARD player — reversed trail following the pull path. Spawn one set per tile of movement.
  5. **Bonus impact (if adjacent):** `DAMAGE_INDICATOR` (5) + `SPLASH` (8) at target's final position
- *The angler's patience pays off — in violence.*

### Heartbreak Sherd — "Shatter Will"
- **AP:** 3 | **Range:** 3 | **Target:** Enemy
- **3 damage** + target gets **-4 attack** penalty AND **-3 speed** for 2 turns. The crippling debuff.
- **Sound:** Glass break + anvil destroy
- **Animation:**
  1. **Cast:** `WITCH` particles (4) at player's hand
  2. **Projectile:** `ENCHANTED_HIT` trail (6) from player to target with a slow, heavy arc (arc height 1.0)
  3. **Impact:** `ITEM` break particles using amethyst shard texture (10) shattering outward (spread 0.5, 0.5, 0.5, speed 0.15) + `ANGRY_VILLAGER` (3) above target head + `LARGE_SMOKE` (4) rising
- *A broken heart breaks everything else with it.*

### Sheaf Sherd — "Entangle"
- **AP:** 3 | **Range:** 3 | **Target:** Enemy
- Target is **stunned** for 1 turn. All enemies within 1 tile of target get **-3 speed** for 2 turns.
- **Sound:** Grass break + vine place
- **Animation:**
  1. **Cast:** `COMPOSTER` particles (4) swirl at player
  2. **Projectile:** `COMPOSTER` + `HAPPY_VILLAGER` trail (8) from player to target (medium arc)
  3. **Impact (primary):** `COMPOSTER` particles (15) erupting upward from ground at target (spread 0.2, 0.8, 0.2, speed 0.1) — vines growing up around legs
  4. **Splash (AoE):** For each slowed enemy within 1 tile: `COMPOSTER` particles (6) at their feet + `FALLING_SPORE_BLOSSOM` (4) drifting down
- *Wheat stalks twist into strangling vines.*

### Miner Sherd — "Earthen Spike"
- **AP:** 3 | **Range:** 2 | **Target:** Enemy
- **7 damage**. If the target is adjacent to any obstacle tile: **+4 bonus damage** (the earth has more to work with). Max 11.
- **Damage type:** BLUNT
- **Sound:** Stone break + anvil land
- **Animation:**
  1. **Cast:** `DUST_PLUME` particles (4) at player's feet — ground rumble
  2. **Travel:** `DUST_PLUME` trail (6) running along the GROUND from player to target (no arc — y stays at floor level). Particles spawn at foot height.
  3. **Impact:** `BLOCK` break particles using stone texture (20) erupting UPWARD from beneath target (spread 0.3, 0.0, 0.3, speed 0.3 on Y axis) — spike bursting up. `EXPLOSION` (1) at target. `CLOUD` (4) dust settling.
  4. **Bonus (if near obstacle):** Double the stone break particles (40) + additional `BLOCK` particles from the obstacle tile's direction
- *The pickaxe strikes below — and the ground strikes above.*

### Danger Sherd — "Hex Trap"
- **AP:** 3 | **Range:** 3 | **Target:** Empty tile
- Place an invisible arcane trap. When an enemy steps on it: **8 damage** + **stunned**. Lasts 5 turns or until triggered. Only one trap active at a time.
- **Tile effect type:** `hex_trap`
- **Sound:** Enchant table ambient on place, elder guardian curse on trigger
- **Animation:**
  1. **Placement cast:** `WITCH` particles (4) swirl at player's hand
  2. **Placement travel:** `WITCH` trail (4) from player to tile (low, subtle arc)
  3. **Placement settle:** `ENCHANT` particles (6) spiral downward INTO the tile (spread 0.3, funnel from y+1.0 down to y+0.1) — magic sinking into the ground. Then nothing — the trap is invisible.
  4. **Trigger (when enemy steps on):** `EXPLOSION` (1) + `ENCHANTED_HIT` burst (20, spread 0.5, 0.8, 0.5) erupting from ground + `WITCH` (10) + `SOUL_FIRE_FLAME` (8) spiraling upward — the curse detonates
- *The warning sign becomes a promise.*

---

## 4 AP — Strong Spells

### Blade Sherd — "Phantom Slash"
- **AP:** 4 | **Range:** 1 (adjacent) | **Target:** Enemy
- A spectral crescent blade. **8 damage** to target + **5 damage** to one random adjacent enemy (magic cleave). Melee range but devastating burst.
- **Sound:** Sword sweep
- **Animation:**
  1. **Cast:** `ENCHANTED_HIT` particles (6) coalesce along player's arm — blade forming
  2. **Slash:** `SWEEP_ATTACK` (2) at target position + `ENCHANTED_HIT` trail (10) in a wide horizontal ARC through the target (semicircle, radius 1 block centered on the attack direction). Not a line — a crescent sweep.
  3. **Primary impact:** `CRIT` (8) + `ENCHANTED_HIT` (6) burst on main target
  4. **Cleave (if secondary hit):** `SWEEP_ATTACK` (1) at secondary target + `ENCHANTED_HIT` (4) + `DAMAGE_INDICATOR` (3). Slightly delayed from primary.
- *The blade etched in clay becomes real for one terrible instant.*

### Burn Sherd — "Immolation"
- **AP:** 4 | **Range:** 3 | **Target:** Enemy
- Target erupts in flame. **6 fire damage** + **Burning** effect (2 dmg/turn) for 3 turns. All enemies within 1 tile of target take **3 fire damage** + Burning for 1 turn.
- **Sound:** Blaze shoot + fire extinguish
- **Animation:**
  1. **Cast:** `FLAME` particles (6) spiral from player's hand upward
  2. **Projectile:** `FLAME` (10) + `LARGE_SMOKE` (5) trail from player to target — fireball arc (arc height 1.5, like potion throw but with fire)
  3. **Detonation:** `LAVA` particles (8) burst at target + `FLAME` (20) mushroom cloud expanding outward (spread 0.6, 1.0, 0.6) + `LARGE_SMOKE` (10) rising
  4. **Splash:** For each enemy in AoE: `FLAME` (6) + `SMALL_FLAME` (4) at their position. Set mob fire ticks for visual flames on entity.
  5. **Linger:** `SMALL_FLAME` (3) continue at target position for a moment — ground burning
- *The campfire on the sherd roars to life — hungry and indiscriminate.*

### Snort Sherd — "Tectonic Charge"
- **AP:** 4 | **Range:** 2 | **Target:** Enemy
- Knockback target **3 tiles** away from player. **3 damage per tile pushed**. If they collide with a wall/obstacle/arena edge: **+6 bonus damage** + **stunned**. Max 15 damage.
- **Damage type:** BLUNT
- **Sound:** Ravager roar + anvil land on collision
- **Animation:**
  1. **Cast:** `DUST_PLUME` (6) + `CLOUD` (4) burst at player — shockwave origin
  2. **Shockwave:** `CLOUD` particles (3) spawn at each tile between player and target's starting position — the force traveling outward
  3. **Push (per tile of knockback):** `DUST_PLUME` (4) at each tile the target passes through — dust trail following the pushed entity
  4. **Landing (no collision):** `DUST_PLUME` (8) + `CLOUD` (4) at final position — skidding stop
  5. **Wall collision (if applicable):** `EXPLOSION` (1) at collision point + `BLOCK` stone break (15) + `DAMAGE_INDICATOR` (5) — devastating slam. Screen-shake worthy.
- *The sniffer's snort becomes a seismic shove.*

### Shelter Sherd — "Stone Aegis"
- **AP:** 4 | **Range:** self | **Target:** Self-cast
- **Resistance II** for 4 turns (+4 defense) + **Absorption II** for 3 turns. Become nearly unkillable for several rounds.
- **Sound:** Shield block + iron golem repair
- **Animation:**
  1. **Cast:** `BLOCK` stone particles (15) rise from ground in a ring around player (radius 1.0, evenly spaced in circle at foot height, moving upward)
  2. **Shield form:** `ENCHANTED_HIT` (8) trace a dome outline above player (hemisphere pattern, radius 0.8) — the aegis taking shape
  3. **Solidify:** `DUST_PLUME` (10) compress inward toward player (from spread 1.0 to 0.2) — the shield tightening. `ENCHANT` (4) orbiting slowly.
  4. **Resolve:** Brief `FLASH` — single `FIREWORK` (1) particle directly on player, white. The ward is set.
- *The house on the sherd builds itself around you — invisible, unyielding.*

### Flow Sherd — "Tidal Surge"
- **AP:** 4 | **Range:** self AoE (2 tiles) | **Target:** Self-cast
- All enemies within 2 tiles: **5 damage** + pushed **2 tiles away** from player (knockback). Creates breathing room.
- **Damage type:** WATER
- **Sound:** Splash + water ambient
- **Animation:**
  1. **Cast:** `BUBBLE` particles (8) swirl at player's feet — water rising
  2. **Wave:** `SPLASH` (6) + `BUBBLE` (4) + `DRIPPING_WATER` (3) spawn in an expanding ring outward from player. First ring at distance 1 (8 particles), then ring at distance 2 (16 particles). Timed ~100ms apart for wave propagation feel.
  3. **Hit (per enemy):** `SPLASH` burst (10) + `BUBBLE_COLUMN_UP` (6) at each enemy struck — wave crashing into them
  4. **Knockback trail:** `DRIPPING_WATER` (3) at each tile enemies are pushed through
  5. **Settle:** `FALLING_WATER` (4) at player's feet — water receding
- *The breeze pattern swirls — and the current follows.*

### Mourner Sherd — "Soul Drain"
- **AP:** 4 | **Range:** 3 | **Target:** Enemy
- Deal **7 damage**. **Heal player** for the amount of damage actually dealt (after defense). Pure life steal.
- **Sound:** Soul escape + experience pickup
- **Animation:**
  1. **Cast:** `SOUL_FIRE_FLAME` (4) + `SOUL` (3) coalesce at player's outstretched hand
  2. **Projectile:** `SOUL_FIRE_FLAME` (8) + `SOUL` (4) trail from player to target — slow, heavy arc (arc height 0.8). Dark purple/teal aesthetic.
  3. **Drain impact:** `SOUL` (12) burst at target (spread 0.4, 0.6, 0.4) + `DAMAGE_INDICATOR` (5)
  4. **Life stream:** `SOUL_FIRE_FLAME` trail (10) from target BACK to player — the stolen life flowing home. Reversed trail with slight upward drift (y speed 0.02).
  5. **Heal:** `HEART` (6) + `HAPPY_VILLAGER` (4) at player position as health returns
- *The weeping figure on the sherd drinks deep — and so do you.*

### Brewer Sherd — "Alchemist's Surge"
- **AP:** 4 | **Range:** self | **Target:** Self-cast
- Apply **4 random positive effects** (each for 3 turns) chosen from: Speed I, Strength I, Resistance I, Regeneration I, Fire Resistance, Haste I. No duplicates. High roll = god mode.
- **Sound:** Brewing stand brew + witch drink
- **Animation:**
  1. **Cast:** `WITCH` particles (6) spiral upward from player — brewing
  2. **Bubble:** `EFFECT` particles (4) of different colors pop up in sequence — each representing a rolled effect. Spawn over ~400ms with slight delays between each.
  3. **Infusion:** `SPLASH` (12) burst at player position (spread 0.5, 0.3, 0.5) — the potion cocktail breaking. Multiple colors via `ENTITY_EFFECT` with randomized RGB.
  4. **Afterglow:** `WITCH` (4) + `EFFECT` (4) particles orbit player briefly — the magic settling in
- *Every potion at once — the brewer's masterpiece.*

### Plenty Sherd — "Bountiful Harvest"
- **AP:** 4 | **Range:** self | **Target:** Self-cast
- Immediately restore **+5 AP** + heal **10 HP**. Net cost: 1 AP + a consumable for 5 HP. The only spell that pays for itself.
- **Special:** Returns a `RESTORE_AP:5` prefixed message for CombatManager to add AP back.
- **Sound:** Experience pickup + villager yes
- **Animation:**
  1. **Cast:** `HAPPY_VILLAGER` (6) burst at player — abundance
  2. **AP visual:** `ENCHANT` (8) particles spiral upward in a tight column above player (radius 0.2, height 2.0) — power returning. Three distinct pulses for the 3 AP restored, timed ~150ms apart.
  3. **Heal:** `HEART` (4) mixed into the column
  4. **Flourish:** `COMPOSTER` (6) + `HAPPY_VILLAGER` (4) settling outward at ground level — harvest complete
- *The cornucopia overflows — time itself bends to your abundance.*

---

## 5 AP — Powerful Spells

### Archer Sherd — "Spectral Volley"
- **AP:** 5 | **Range:** 4 | **Target:** Enemy
- Rain spectral arrows on the target zone. **7 damage** to target + **4 damage** to all other enemies within 1 tile of target. Long-range artillery.
- **Damage type:** RANGED
- **Sound:** Skeleton shoot + arrow impact
- **Animation:**
  1. **Cast:** `ENCHANTED_HIT` (4) at player's hand — nocking a spectral arrow
  2. **Volley (3 arrows):** Three `CRIT` trails (6 particles each) descending from HIGH above (y+6) DOWN onto target zone. Each arrow starts from a slightly different offset (spread 1.0 in X/Z at the top) and converges on target. Slight delay between each (~100ms). Arc is INVERTED — starts high, falls down.
  3. **Primary impact:** `CRIT` (10) + `ENCHANTED_HIT` (8) burst at main target — heavy hit
  4. **Splash impacts:** `CRIT` (5) at each AoE-hit enemy + `DAMAGE_INDICATOR` (3)
  5. **Ground scatter:** `ENCHANTED_HIT` (4) at random positions in the 3x3 zone — arrow fragments
- *The archer on the sherd draws back a ghostly bowstring — and the sky answers.*

### Howl Sherd — "Dread Howl"
- **AP:** 5 | **Range:** self AoE (3 tiles) | **Target:** Self-cast
- All enemies within 3 tiles of player: **4 damage** + **stunned** (skip next turn). The panic button.
- **Sound:** Wolf howl
- **Animation:**
  1. **Cast:** Player-centered `SOUL` (6) particles spiral tightly, then PAUSE — the intake of breath
  2. **Howl:** Three expanding rings of `CLOUD` particles (ring 1: radius 1, 8 particles; ring 2: radius 2, 12 particles; ring 3: radius 3, 16 particles). Each ring spawns ~150ms after the previous — visible shockwave propagation.
  3. **Stun impacts:** At each affected enemy: `CLOUD` (8) + `SOUL` (4) burst (spread 0.3, 0.5, 0.3) — fear taking hold. `CRIT` (3) for the damage component.
  4. **Echo:** `SOUL` (4) particles drift slowly outward from player after the main blast — the howl echoing. Low speed (0.02).
- *The wolves carved into clay lift their heads — and the living freeze in terror.*

### Arms Up Sherd — "War Cry"
- **AP:** 5 | **Range:** self | **Target:** Self-cast
- **Strength III** for 3 turns (+9 attack) + **Speed II** for 3 turns (+4 speed). Turns you into a wrecking ball for 3 full rounds.
- **Sound:** Ender dragon growl + raid horn
- **Animation:**
  1. **Buildup:** `FLAME` (4) + `SOUL_FIRE_FLAME` (4) particles rising slowly from ground around player — power gathering. Tight ring, radius 0.6.
  2. **Eruption:** `FLAME` (15) BURST upward from player (spread 0.3, 0.0, 0.3, Y speed 0.4) — pillar of fire shooting up. `LAVA` (6) scattered outward.
  3. **Aura:** `ENCHANTED_HIT` (10) form a brief spinning ring at shoulder height (radius 0.7, placed in circle pattern) — the power crystallizing
  4. **Resolve:** `CRIT` (8) fall back down onto player + `FLAME` (4) at feet. The energy absorbed.
- *Arms raised in triumph — the battle has already been won.*

### Prize Sherd — "Fortune's Favor"
- **AP:** 5 | **Range:** self | **Target:** Self-cast
- Your **next attack this turn deals triple damage**. Also gain **Luck II** for 3 turns. The setup spell — combine with Plenty for a devastating same-turn combo.
- **Special:** Returns a `TRIPLE_NEXT:1` prefixed message. CombatManager sets a `tripleDamageNextAttack` flag consumed on the player's next melee/ranged attack.
- **Sound:** Note block bell + enchanting
- **Animation:**
  1. **Cast:** `ENCHANT` (8) glyphs swirl around player at waist height (orbiting circle, radius 0.8) — fortune being summoned
  2. **Converge:** `ENCHANT` (6) + `WAX_ON` (4) particles collapse inward toward player's weapon hand (from spread 1.0 to 0.1 over time)
  3. **Charge:** Player's held weapon position gets `ENCHANTED_HIT` (10) shimmer + `FIREWORK` (1, gold tint) — the weapon is LOADED. Lingering `ENCHANT` (2) orbit the weapon.
  4. **Luck sparkle:** `WAX_ON` (6) brief golden rain around player
- *The trophy on the sherd glows — and everything you touch turns golden.*

---

## 6 AP — Ultimate Spell

### Skull Sherd — "Death Mark"
- **AP:** 6 | **Range:** 3 | **Target:** Enemy
- If target is below **40% HP**: **instant kill** (deal 9999 damage). If above 40%: **5 damage** + **Wither III** for 3 turns (6 dmg/turn). Either way, something dies.
- **Sound:** Wither shoot (execute) or wither ambient (normal)
- **Animation (execute path — target below 40%):**
  1. **Cast:** `SOUL_FIRE_FLAME` (8) + `SOUL` (6) coalesce ominously at player — dark energy gathering
  2. **Projectile:** `SOUL_FIRE_FLAME` (10) + `SOUL` (6) heavy trail to target. SLOW arc — deliberately menacing (arc height 2.0, more trail particles than usual, 12 steps instead of 8).
  3. **Mark:** `SCULK_CHARGE_POP` (6) appear above target's head — the death mark manifests
  4. **Execute:** Brief pause (200ms), then: `EXPLOSION_EMITTER` (1) + `SOUL_FIRE_FLAME` (20, spread 0.8, 1.2, 0.8) + `SOUL` (15) erupting upward from target in a massive column. `LARGE_SMOKE` (10) billowing out. The entity is obliterated.
- **Animation (wither path — target above 40%):**
  1. **Cast:** Same as execute
  2. **Projectile:** Same as execute
  3. **Impact:** `SOUL` (10) burst at target (spread 0.4, 0.6, 0.4) + `DAMAGE_INDICATOR` (4)
  4. **Wither curse:** `SOUL_FIRE_FLAME` (6) swirl around target in decaying orbit + `SCULK_CHARGE_POP` (4) — the wither taking hold. Mob gets wither visual darkening.
- *The skull grins. It knows which prey is weakest.*

---

## Balance Notes

| Tier | AP | Sherds | Role |
|------|----|--------|------|
| **S — Boss-killer** | 6 | Skull | Execute or devastating DoT. Save for the big fight. |
| **A — Turn-defining** | 5 | Howl, Arms Up, Archer, Prize | Commit your whole turn. Change the battle. |
| **B — Strong plays** | 4 | Blade, Burn, Snort, Shelter, Flow, Mourner, Brewer, Plenty | Heavy investment, heavy payoff. |
| **C — Efficient** | 3 | Heart, Scrape, Angler, Heartbreak, Sheaf, Miner, Danger | Leave AP for a follow-up attack or move. |
| **D — Quick** | 2 | Explorer, Friend | Cheap utility. Still worth the sherd. |

### Design Rationale
- Sherds are **rare archaeology drops** — high AP cost makes each cast a deliberate, meaningful decision
- A typical player has **3-6 AP per turn** (depending on build, trim bonuses, Plenty spell)
- **2-3 AP** spells leave room for attacks/movement afterward
- **4 AP** spells are your main action — maybe one move left
- **5 AP** spells ARE your turn (unless you have AP-boosting gear)
- **6 AP** spells require investment to even cast (AP upgrades, Wild trim, or Plenty combo)
- Higher costs = higher numbers. A 5 AP spell should feel **3-4x stronger** than a 1 AP fire charge
- MAGIC damage type makes **Gold armor set** the "spellcaster" build path
- **Plenty + Prize** is an intentional combo: 4 AP to get +3 back, then 5 AP triple-damage attack = 6 AP total for a massive hit

### Animation Philosophy
- Every spell has a **cast → travel → impact** rhythm (even self-buffs: gather → infuse → resolve)
- Higher AP spells get **more particles, more stages, and deliberate pauses** — the casting should FEEL expensive
- AoE spells use **expanding rings** timed with delays to show propagation
- Projectile spells use `trailParticles()` arcs (reuse existing infra) with spell-specific particle types
- Self-buffs use **inward-converging** particles (power flowing INTO the player)
- Damage spells use **outward-expanding** particles (power exploding FROM impact)
- The Skull execute path has a **deliberate pause** before detonation — build tension

---

## Implementation Summary

### New File
- `PotterySherdSpells.java` in `com.crackedgames.craftics.combat`
  - Static method per sherd: `useArcherSherd(player, arena, target, enemies, combatEffects)` etc.
  - `isPotterySherd(Item)` helper
  - `getSherdApCost(Item)` helper returning 2-6 based on the sherd
  - `POTTERY_SHERDS` set of all 22 shard Items

### Modified Files
| File | Changes |
|------|---------|
| `ItemUseHandler.java` | Add sherds to `EXTRA_USABLE`, add `isPotterySherd()` check in `isUsableItem()`, route `getApCost()` through `PotterySherdSpells.getSherdApCost()`, add dispatch in `useItem()` if-else chain |
| `CombatManager.java` | Handle `RESTORE_AP` prefix (Plenty), `TRIPLE_NEXT` prefix (Prize), `hex_trap` tile effect in tick loop + movement trigger, `tripleDamageNextAttack` flag in attack handling |
| `CombatTooltips.java` | Add tooltip strings for all 22 sherds with AP cost, range, and description |
| `ProjectileSpawner.java` | Add spell-specific methods: `spawnSpellTrail(type)`, `spawnSpellImpact(type)`, `spawnExpandingRing()`, `spawnConvergingParticles()`, `spawnHelixParticles()` |
| `CombatEntity.java` | Add `defensePenalty` + `defensePenaltyTurns` fields for Scrape's temporary defense shred (decremented each turn) |

### New ProjectileSpawner Methods
```
spawnSpellTrail(world, from, to, particle, secondaryParticle, count, arcHeight)
spawnExpandingRing(world, center, radius, particle, count)  // for AoE propagation
spawnConverging(world, center, radius, particle, count)     // for self-buff gather
spawnDescendingVolley(world, target, count, spread)          // for Archer's sky arrows
spawnReversedTrail(world, from, to, particle, count)         // for Mourner/Angler return
```

### New CombatManager Mechanics
1. **`hex_trap` tile effect** — checked when enemies move (in movement processing). On trigger: 8 damage + stun + remove tile effect + particles/sound.
2. **`RESTORE_AP:N` prefix** — parsed in `handleUseItem()`, adds N to `apRemaining` after normal AP deduction.
3. **`TRIPLE_NEXT:1` prefix** — sets `tripleDamageNextAttack = true` flag. Consumed on next melee/ranged attack (multiply final damage by 3).
4. **`defensePenalty` on CombatEntity** — temporary defense reduction with turn counter. Applied by Scrape, factored into `takeDamage()` defense calculation.
5. **Pull mechanic (Angler)** — reverse of existing knockback: move target tile-by-tile toward player, stopping if path is blocked.

### Verification
1. `./gradlew build` — compiles
2. In-game: give each sherd via `/give`, enter combat, verify:
   - AP deducted correctly (2-6 depending on sherd)
   - Sherd consumed after use
   - Can't cast if insufficient AP (red error message)
   - Damage/effects apply to correct targets and scale properly
   - AoE spells (Howl, Flow, Burn splash) hit the right radius
   - Hex Trap triggers on enemy movement, not just tile tick
   - Plenty restores AP (check AP counter updates)
   - Prize triples next attack damage specifically
   - Skull executes at 40% threshold correctly
   - Angler pulls toward player (reverse knockback direction)
   - All animations play with correct particle types and timing
   - Tooltips display correct AP cost on hover
   - Sounds fire for each spell
