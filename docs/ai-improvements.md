# Overworld AI improvements

A pass over every overworld combat AI — hostile mobs, neutral/passive mobs, and
all six ally archetypes. Two systemic problems fixed across the board, plus
per-mob behavior upgrades.

All four Stonecutter shards compile and test clean after the changes
(`1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, verified with `--rerun-tasks`).

---

## 1. Systemic fixes

### 1.1 Shared-AI-instance state leak (bug class)

`AIRegistry` hands out **one shared AI instance per entity type**, but several
AIs kept per-fight state in instance fields. That state was silently shared by
every mob of the type, across every fight in the server's lifetime:

| AI | Leaked state | Visible symptom |
|----|--------------|-----------------|
| `EvokerAI` | `hasSummonedVex` | After the first evoker ever summoned, **no evoker ever summoned a vex again** |
| `EndermanAI` | `strikesRemaining`, `frenzy` | One bloodied enderman put every future enderman into permanent frenzy pacing |
| `DrownedAI` | `hasTrident` | The first drowned's 50/50 roll decided the loadout of every drowned, forever |
| `WitchAI` | `brewIndex` | Brew rotation skipped around as witches shared the counter |

Fix: a small per-entity scratch map on `CombatEntity`
([CombatEntity.java](../src/main/java/com/crackedgames/craftics/combat/CombatEntity.java),
`getAiMemory`/`setAiMemory`) — it lives and dies with the entity, exactly the
lifecycle the old fields pretended to have. The four AIs now keep their state
there. (`BlazeAI` had already discovered this bug and worked around it with
per-entity instances; the memory map is the general fix.)

### 1.2 Threat-aware kiting and path-validated movement

- **Kiters ignored your pets.** Skeleton, stray, pillager, witch and evoker
  measured danger only against the player's tile, so a wolf chewing on a
  skeleton's ankles never triggered its retreat. New
  `AIUtils.threatPositions()` collects every party player *and* every live
  ally; all kite/retreat triggers and retreat-tile scoring now run against the
  nearest *threat*, not the nearest *player*.
- **Straight-line flee cornered mobs.** `getFleeTarget` only tried the
  directly-away ray (plus perpendiculars), so a passive mob with an open
  diagonal escape would freeze instead. New `AIUtils.fleeReachable()` and
  `AIUtils.bestRetreatTile()` score every tile actually reachable this turn
  (BFS, path-validated, size-aware) and maximize threat distance.
- **Retreats no longer end on hazards.** All new tile scoring penalizes
  lava/fire/powder-snow destinations (`AIUtils.isHazardTile`).
- **Shared hit-and-run.** The wolf's strike-then-reposition combo moved to
  `AIUtils.hitAndRun()` and is now reused by fox, ocelot, agro cat and cave
  spider.

---

## 2. Hostile mobs

| Mob | Change |
|-----|--------|
| **Zombie / Zombie Villager / Husk** | Deduplicated into [UndeadHordeAI.java](../src/main/java/com/crackedgames/craftics/combat/ai/UndeadHordeAI.java) (zombie & vex = plain). Horde recount at the destination no longer counts the mover's own *old* tile as a packmate, and ally-side undead no longer feed the bonus. **Husk now joins the horde bonus** (it never did). **Zombie villager** gets a desperation twist: below 50% HP, +1 attack and +1 movement. |
| **Skeleton** | Kites from any threat (pets included); retreat/shot tiles chosen from path-validated reachable tiles; avoids hazard tiles; keeps the cornered-archer and no-clean-line fallback shots. |
| **Stray** | Same threat-aware kiting; still prefers maximum range (more cautious than skeleton). |
| **Pillager** | **Fired at hardcoded range 4 while its biome entry registers range 3** — now honors `getRange()`. Threat-aware retreat, hazard avoidance, prefers near-max-range firing positions. |
| **Creeper** | **Defuse-and-chase implemented** (was documented, never coded): a hot fuse now only detonates if a player or pet is inside the blast radius — otherwise the fuse resets (glow cleared) and the chase resumes. Exception: a creeper at ≤25% HP blows anyway rather than dying for nothing. Blast check covers party members and pets, matching the executor's Manhattan radius. |
| **Spider** | Breaks off to the ceiling to reset the ambush when ≤25% HP; skips the web shot when a web overlay already sits next to the player (pounces instead); never stacks a web on an existing overlay; `ThreadLocalRandom` instead of `Math.random()`. |
| **Cave spider** | Venomous hit-and-run: bites then scuttles out of reach with leftover speed (speed 3) — the poison does the work. |
| **Enderman** | Assault-cycle state per-entity (see §1.1); every teleport candidate (strike, dodge, stalk, escape) now refuses water tiles. |
| **Witch** | Brew rotation per-witch; **self-heal is now real** — below 40% HP she has a 25% chance to drink (ModifySelf heal for `atk/2`, min 3) instead of the old fake "reposition and call it healing"; the dead never-executed ally-buff block is gone; threat-aware retreat + hazard avoidance. |
| **Evoker** | Summon state per-entity (see §1.1) and a **second desperate vex** when first wounded below 50% HP; threat-aware retreat; reposition scan is now path-validated. |
| **Vindicator** | Rook-dash no longer charges through lava/fire — the dash lane stops at hazard tiles. |
| **Ravager** | **Ground stomp implemented** (was documented, never coded): when 2+ player-side combatants are in melee contact with its 2×2 body it slams an AoE (`AreaAttack`, radius-2 box) instead of a single tusk swipe. |
| **Drowned** | Trident roll per-drowned (see §1.1), stored on the entity flag CombatManager already reads. |
| **Silverfish** | **Swarm fury**: when any silverfish in the arena is hurt (lasting wound, not just last-turn flag), all of them enrage for +1 movement — vanilla wall-boil flavor. |

## 3. Neutral / passive mobs

| Mob | Change |
|-----|--------|
| **Wolf** | **Pack tactics**: +1 damage per other enraged wolf already in melee contact with the victim. Hit-and-run via the shared helper. **Prey-hunt bug fixed**: the old hit-and-run combo (`MoveAttackMove`) always resolves its bite against the player/aggro pet, so a wolf "hunting a sheep" could bite the player — prey is now attacked with `AttackMob` only. |
| **Fox** | Fights like a skirmisher: nips then darts back out (shared hit-and-run) when agro. |
| **Cat** | Agro cats claw-and-slink (hit-and-run); the flee-from-player is path-validated (no more freezing when the straight line is blocked). |
| **Ocelot** | **Reposition implemented** (was documented, never coded): strikes then springs away with leftover speed-4 movement; evasion flee is path-validated. |
| **Goat** | **Real ram**: when aligned on a row/column it thunders down the lane and hits with momentum — +1 damage per tile beyond 2 — plus the usual knockback. Unaligned approach unchanged. |
| **Llama** | Honors its registered `range` (was hardcoded 2 in three places); spit positions chosen from reachable tiles, preferring max range, never on hazards. |
| **Polar bear** | It's 2×2 — distance/pathfinding are now size-aware (the old anchor-only math let players stand inside its reach without triggering the territorial agro). The maul swats with 1 tile of knockback. |
| **Bee** | Swarm enrage now triggers on *lasting* wounds: a bee one-shot before its damage flag was read no longer leaves the swarm asleep. |
| **Rabbit / farm animals (PassiveAI)** | Path-validated flee — they bolt around corners instead of idling when the straight-line escape is blocked. |
| **Cod/Salmon** | Unchanged (water-constrained flee is its own correct logic). |

## 4. Ally archetypes

| Archetype | Change |
|-----------|--------|
| **Melee** (wolf, fox, golem-less default) | Target scoring adds +2 for enemies it can actually strike this turn and +4 for kills it can secure outright (HP ≤ its attack) — no more walking past a finishable enemy toward a marginally closer one. |
| **Ranged** (llama, snow golem) | Kite tile is scored over everything reachable this turn: maximize distance gained, +15 for tiles that keep the parting shot in range (skipped when fleeing for its life). Replaces the fixed two-tiles-straight-back hop. |
| **Tank** (iron golem, turtle, goat) | **Interposes**: when the biggest threat is too far to strike this turn, it plants itself on the player-threat line (within 2 tiles of the player) instead of sprinting across the arena and leaving the player open. Fights normally once the threat is in reach. |
| **Support** (axolotl, frog, villager) | Holds station on the player's *sheltered* side — the adjacent tile farthest from the nearest enemy — so the squishy support isn't the first thing a charge runs into. |
| **Flyer** (parrot, bee, allay) | Kill-secures: dives the weakest enemy it can both reach and finish this turn before defaulting to the globally weakest. |
| **Farm animal** | Benefits from the shared `fleeFrom` upgrade: when the straight-line bolt is blocked it takes the best reachable escape instead of freezing. |
| **All** | `AllyTargeting.fleeFrom` falls back to reachable-tile scoring when the direct retreat is blocked. |

---

## 5. Manual test notes

1. Fight two evokers back-to-back (or the same one twice): each summons a vex
   when you close in, and a second when dropped below half HP.
2. Damage a creeper's fuse target then walk 3+ tiles away: the hiss stops (glow
   clears) and it chases again; repeat at low creeper HP — it detonates anyway.
3. Put a wolf ally next to a skeleton: the skeleton backs off from the wolf
   even when you are far away.
4. Hit one bee in a swarm and kill it the same turn: the rest still enrage.
5. Hurt one silverfish: the whole group visibly speeds up next round.
6. Corner a rabbit against a wall with an open diagonal: it escapes around you.
7. Stand aligned with an enraged goat 4+ tiles away: the ram hits harder than
   its adjacent poke.
8. Surround a ravager with the player plus a pet: ground stomp hits both.
9. Iron golem with a distant enemy: it positions between you and the enemy
   rather than chasing; once the enemy closes, it charges.
10. Snow golem kites a zombie while still plinking it each turn.
