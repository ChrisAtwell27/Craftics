# Broodmother Boss Rework — Design Spec

**Date:** 2026-04-02
**Status:** Approved
**Scope:** Rework BroodmotherAI from static spawn-spam into a state-based predator with egg sac prioritization, ceiling mechanics, and web terrain.

---

## Problem

The current Broodmother is a rigid priority cascade (Spawn Brood > Web Spray > Pounce > Bite > walk). Every fight plays out identically as spider spawning spam. Phase 2 adds minor stat bumps but no new behavior. Egg sacs exist but are non-interactive — the player has no way to destroy them or reason to engage with them.

## Design Goals

1. **Target prioritization** — Player must decide between damaging the boss, destroying egg sacs, or clearing webs.
2. **Readable AI states** — Player can tell when the Broodmother is hunting vs nesting and plan accordingly.
3. **Phase escalation** — Phase 2 introduces genuinely new mechanics (Hunting Dive, web terrain, egg sac replacement), not just stat bumps.
4. **Minion control** — Spawns are tied to egg sac count, giving the player direct agency over spawn pressure.

---

## Stats

| Stat    | Phase 1 | Phase 2 (≤50% HP) |
|---------|---------|--------------------|
| HP      | 35      | —                  |
| ATK     | 6       | —                  |
| DEF     | 2       | —                  |
| Speed   | 3       | 5 (+2 bonus)       |
| Size    | 2×2     | 2×2                |

---

## Egg Sacs

Egg sacs are the central mechanic of the fight. They control spawn capacity and create physical terrain the player must navigate around.

- **3 placed at fight start** in arena corners/edges (same placement logic as current)
- **Visual:** Turtle Egg blocks on the grid
- **1 HP, 0 ATK, 0 DEF** — single hit to destroy
- **Solid for all entities except the Broodmother** — she can walk through them; all other entities (player, minions) treat them as obstacles
- **Max alive cave spiders = number of living egg sacs** — 0 sacs means no spawning is possible
- **Phase 2:** Broodmother can place up to 2 new egg sacs when in Nesting state (if fewer than 3 remain). New sacs are placed near existing sac positions or boss position.

---

## AI States

The Broodmother uses a state-based AI instead of a priority cascade. She is always in one of two states: **Hunting** or **Nesting**.

### Hunting (default start state)

The Broodmother is aggressive, chasing the player.

**Ability priority:**
1. **Ceiling Ambush** (P1) / **Hunting Dive** (P2) — if off cooldown
2. **Pounce** — if in range and off cooldown
3. **Venomous Bite** — if adjacent
4. Walk toward player

**Transitions to Nesting when:**
- An egg sac is destroyed (immediately protective response)
- No minions alive AND Spawn Brood is off cooldown (needs reinforcements)

### Nesting

The Broodmother retreats toward her egg sacs and rebuilds her brood.

**Ability priority:**
1. **Spawn Brood** — if off cooldown and egg sacs exist
2. **Web Spray** — if player in range and off cooldown
3. **Place New Egg Sacs** (Phase 2 only) — if fewer than 3 sacs remain
4. Move toward nearest egg sac cluster

**Transitions to Hunting when:**
- At least 1 minion is alive (has cover to hunt)
- Spawn Brood AND Web Spray are both on cooldown (nothing left to do in nest)

---

## Abilities

### Ceiling Ambush (Phase 1 only)

| Property     | Value                                      |
|--------------|--------------------------------------------|
| Cooldown     | 4 turns                                    |
| Behavior     | Ascend to ceiling (untargetable 1 turn), then slam down on telegraphed 3×3 area |
| Target       | Centered on player's position at time of ascent |
| Damage       | ATK + 4                                    |
| Warning      | TILE_HIGHLIGHT red (0xFFFF4444) on 3×3 zone, 1-turn telegraph |
| Action       | Uses `CeilingAscend` on ascend turn, `AreaAttack` on landing |

### Hunting Dive (Phase 2 upgrade — replaces Ceiling Ambush)

| Property     | Value                                      |
|--------------|--------------------------------------------|
| Cooldown     | 4 turns                                    |
| Behavior     | Ascend to ceiling (untargetable). Turn 1 on ceiling: rain cobwebs onto 4-5 random tiles. Turn 2: dive-bomb player's current position in 3×3 AoE. |
| Web Rain     | Places cobweb blocks at Y+1 on 4-5 random walkable tiles. Webs apply Slowness and last 2 turns. Breakable by player with sword/axe (costs attack action). |
| Dive Damage  | ATK + 4                                    |
| Warnings     | Turn 1: GATHERING_PARTICLES gray (0xFFCCCCCC) on web target tiles. Turn 2: TILE_HIGHLIGHT red (0xFFFF4444) on 3×3 dive zone. |
| Total time   | 3 turns (ascend → web rain → dive)         |
| Implementation | Uses a `ceilingTurnsRemaining` counter in the AI. Turn 1: returns `CeilingAscend`, sets counter to 2. Turn 2 (counter=2): places webs + sets warning for dive zone, decrements. Turn 3 (counter=1): warning resolves as `AreaAttack`. Boss is untargetable while counter > 0. |

### Pounce

| Property     | Value                                      |
|--------------|--------------------------------------------|
| Cooldown     | 2 turns                                    |
| Range        | P1: 2–3 tiles. P2: 2–4 tiles.             |
| Behavior     | Leap to tile adjacent to player, 2×2 AoE landing |
| Damage       | ATK + 2                                    |
| Warning      | TILE_HIGHLIGHT red (0xFFFF4444) on 2×2 landing tiles, 1-turn telegraph |

### Venomous Bite

| Property     | Value                                      |
|--------------|--------------------------------------------|
| Cooldown     | None                                       |
| Range        | Melee (dist ≤ 1)                           |
| Damage       | ATK                                        |
| Effect       | Poison — 2 dmg/turn for 3 turns            |

### Web Spray

| Property     | Value                                      |
|--------------|--------------------------------------------|
| Cooldown     | 3 turns                                    |
| Range        | 4 tiles                                    |
| Area         | 3×3 centered on player                     |
| Damage       | 0                                          |
| Effect P1    | Stun 1 turn + Slowness 1 turn              |
| Effect P2    | Stun 1 turn + Slowness 1 turn + Poison (2 dmg/turn, 2 turns) |
| Warning      | GATHERING_PARTICLES gray (0xFFCCCCCC), 1-turn telegraph |

### Spawn Brood

| Property     | Value                                      |
|--------------|--------------------------------------------|
| Cooldown     | 3 turns                                    |
| Behavior     | Spawn 1 cave spider adjacent to each living egg sac |
| Constraint   | Total alive cave spiders cannot exceed number of living egg sacs |
| Minion stats | 3 HP / 2 ATK / 0 DEF                      |
| Warning      | GROUND_CRACK green (0xFF44AA44) on egg sac positions, 1-turn telegraph |

### Place Egg Sacs (Phase 2 only, Nesting state only)

| Property     | Value                                      |
|--------------|--------------------------------------------|
| Cooldown     | None (only fires when fewer than 3 sacs remain) |
| Behavior     | Place up to 2 new turtle egg blocks on empty walkable tiles near existing sacs or boss position |
| Constraint   | Only during Nesting state, only if fewer than 3 egg sacs remain |
| No warning   | Happens immediately as part of Nesting state behavior |

---

## Phase 2 — "Nest Awakening" (≤50% HP)

Triggered once when HP drops to or below 50%.

| Change                          | Detail                                    |
|---------------------------------|-------------------------------------------|
| Speed                           | 3 → 5 (+2 bonus)                         |
| Ceiling Ambush → Hunting Dive   | 3-turn ceiling sequence with web rain     |
| Web Spray                       | Gains Poison (2 dmg/turn, 2 turns)        |
| Pounce range                    | 3 → 4 tiles                              |
| Egg Sac replacement             | Can place up to 2 new sacs in Nesting state |
| Enraged flag                    | Set to true                               |

---

## Web Overlay System

Webs placed by Hunting Dive are **not** tile type changes. They are physical cobweb blocks placed above the arena floor.

- **Placement:** Cobweb block at arena Y + 1 (one block above the floor)
- **Grid effect:** Tile is marked as having a web overlay. Entities walking onto the tile receive Slowness.
- **Duration:** 2 turns, then the cobweb block is removed and the overlay cleared.
- **Player interaction:** Player can spend their **attack action** to break a web on their current tile or an adjacent tile, if wielding a sword or axe. The cobweb block is removed and the overlay cleared. We will need a clean solution for how the player and moving through cobwebs interacts, because cobwebs physically slow down movement when walking through, it could cause a desync.
- **Broodmother interaction:** The Broodmother is not affected by her own webs (no Slowness applied).

---

## Fight Flow Example

**Phase 1:**
1. Fight starts. 3 egg sacs in arena. Broodmother in Hunting state.
2. She pounces toward player, bites when adjacent.
3. After a few turns, she uses Ceiling Ambush — ascends, player sees red 3×3 warning, moves out of the way, she slams down.
4. Player decides to destroy an egg sac (costs attack action, but reduces future spawn pressure).
5. Egg sac destroyed → Broodmother switches to Nesting, retreats toward remaining sacs.
6. She spawns cave spiders (1 per remaining sac = 2), then uses Web Spray on the player.
7. Minions alive → she transitions back to Hunting.

**Phase 2:**
8. At 50% HP, Nest Awakening triggers. Speed increases, she becomes enraged.
9. She uses Hunting Dive — ascends, rains 4-5 webs across the arena, then dive-bombs.
10. Player must break webs or navigate around them while dodging the dive.
11. If egg sacs are destroyed, she enters Nesting and places 2 new ones.
12. The fight becomes a tug-of-war: player destroying sacs, boss replacing them, webs limiting movement.

---

## Files to Modify

| File | Changes |
|------|---------|
| `BroodmotherAI.java` | Full rewrite — state machine, new abilities, egg sac logic |
| `CombatManager.java` | Egg sac entity handling (solid for non-boss, 1HP destructible), web overlay placement/removal/breaking, Hunting Dive multi-turn ceiling sequence, turtle egg block visuals |
| `EnemyAction.java` | May need a `PlaceEggSacs` action type or reuse `CreateTerrain`/`SummonMinions` |
| `TileType.java` | No changes needed (webs are Y+1 blocks, not tile types) |
| `GridArena.java` | Web overlay tracking (set/get/clear per tile), slowness-on-step check |
| `CombatEffects.java` | Verify Slowness/Poison/Stun effects work as needed (likely no changes) |
| `jungle.json` | Verify boss stats match (HP 35, ATK 6, DEF 2) |
| `broodmother.html` | Update wiki documentation |
