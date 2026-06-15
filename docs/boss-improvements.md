# Boss improvements

A pass over the boss system: one systemic state bug fixed for all 18+ bosses,
plus new spectacle around the three beats of every boss fight (intro, phase
two, the kill) and clearer telegraph rendering.

All four Stonecutter shards compile and test clean (`1.21.1`-`1.21.5`,
non-active shards verified with `--rerun-tasks`).

---

## 1. The shared-instance state leak - fixed for every boss

`AIRegistry` hands back **one shared AI instance per key**, and `BossAI`
carries a lot of per-fight state on that instance: `phaseTwo`, `turnCounter`,
the ability-cooldown map, the pending telegraph, and the minion/projectile
ID lists. Only three bosses (Molten King, Sandstorm Pharaoh, Tidecaller) had
been given fresh per-fight instances; the other fifteen shared theirs across
every fight in the server's lifetime. Symptoms:

- Any boss killed while in phase two left the **next** boss of its kind
  starting in phase two - no transition, wrong cooldowns, stale turn counter.
- The **Broodmother**'s entire nest state machine (`HUNTING`/`NESTING`, the
  egg-sac list, pending web rain) leaked between fights.
- The **Hollow King**'s `lightsOutPermanent` flag persisted, so a rematch
  started in permanent darkness.
- Stale minion ID lists could alias onto new entity IDs in later fights.

Fix ([AIRegistry.java](../src/main/java/com/crackedgames/craftics/combat/ai/AIRegistry.java)):
boss AIs are now registered through `registerBoss(key, factory)` - the shared
instance remains for stateless queries (`getGridSize`), and
`AIRegistry.createFresh(key)` mints a per-fight copy that
[CombatManager](../src/main/java/com/crackedgames/craftics/combat/CombatManager.java)
pins on the boss entity via `setAiInstance` at spawn, for **every** boss.
Addon bosses registered through plain `register()` are covered by a reflective
no-arg-constructor fallback. The Molten King's split copies keep their explicit
`MoltenKingAI(1)` instances.

## 2. Boss fight beats

| Beat | Before | Now |
|------|--------|-----|
| **Intro** | "BOSS FIGHT" title with the *level* name as subtitle; generic combat-start growl | Subtitle names the boss itself ("The Hollow King"), with a wither-spawn sting layered over the growl |
| **Phase two** | A silent flag flip - only addons were notified; players inferred it from behavior | Combat-log line, a " <boss> - PHASE 2 " subtitle for every party member, ravager roar + smoke/flash burst on the boss, camera shake and a dark-red screen flash on all clients |
| **Defeat** | Generic "X defeated!" line, small poof, orb-pickup ding | " <boss> DEFEATED! " gold line, explosion bloom + golden totem rain, wither-death knell, camera shake + golden screen flash. Skipped for a gen-0 Molten King splitting into fragments - that's not the kill |

Client reactions ride a new `EVENT_BOSS_MOMENT` combat event
([CombatEventPayload.java](../src/main/java/com/crackedgames/craftics/network/CombatEventPayload.java),
handled in [CrafticsClient.java](../src/client/java/com/crackedgames/craftics/CrafticsClient.java));
titles/sounds/particles are server-driven so all co-op clients see the same moment.

## 3. Phase visibility on the HUD

The sync payload now carries a `;phase=2` token for bosses in phase two
(`BossAI.isInPhaseTwo()`); the boss HP bar in
[CombatHudOverlay.java](../src/client/java/com/crackedgames/craftics/client/CombatHudOverlay.java)
shows it permanently - the bar frame turns molten gold and a **II** badge sits
at the bar's right edge. The screen flash tells you the moment happened; the
badge keeps telling you afterwards.

## 4. Telegraph rendering

Boss warning tiles ([TileOverlayRenderer.java](../src/client/java/com/crackedgames/craftics/client/TileOverlayRenderer.java))
were a flat pulsing red fill. Now:

- A **crisp pulsing perimeter outline** around the warned region - reads as
  "the strike lands exactly here", distinct from the softer danger/threat washes.
- A **through-wall ghost pass** (same GREATER-depth xray as the move/attack
  highlights), so a telegraph behind a boulder at a low camera angle is still
  visible - a telegraph you can't see is a hit you can't dodge.

## 5. Summons stop spawning in lava

`BossAI.findSummonPositions` / `findSummonPositionsNear` treated lava and fire
tiles as valid spawn tiles (they're "walkable"). Minion summons now prefer
tiles with no step damage and only fall back to hazards when the arena offers
nothing else (e.g. a late-fight Molten King floor).

## 6. Nether & End boss pass (round 2)

A per-boss audit of the Nether and End bosses plus the remaining End mobs,
fixing concrete logic bugs:

### 6.1 Telegraphs and abilities that didn't do what they claimed

- **Chorus Mind Resonance Cascade hit nothing.** The warning marked every
  plant-adjacent tile, but the resolve was a radius-0 strike on the *boss's
  own tile*. The resolve now pulses exactly the warned tiles. Also: phase-2
  auto-spread plants only ever grew the AI's private list - invisible,
  unhittable bookkeeping - and now grow as real chorus obstacle tiles; the
  plant ledger re-validates against the arena each turn; the free teleport
  lands on a footprint-valid tile *beside* a plant (not on the obstacle), and
  abilities are now ranged from the post-teleport position (they used the
  stale pre-teleport tile).
- **Shulker Architect's Bullet Storm bullet count was flavor text.** It built
  the 4 (P2: 6) target list, threw it away, and fired one instant
  untelegraphed AoE. Now a telegraphed volley: marked tiles resolve next turn
  as one bullet each, with a half-power plink while it charges. Teleport Link
  also teleported the 2×2 boss squarely onto its own still-living turret -
  it now lands footprint-validated beside the farthest turret.
- **Void Herald's phase-2 auto-collapse silently never happened** whenever
  another telegraph fired the same turn: it set its warning and fell through,
  and the later ability overwrote `pendingWarning`. It now returns (advancing
  while the cracks spread). Blink Assault re-rolled its landing between
  checking and using it, and ignored the boss's 2×2 footprint - both fixed.
- **Molten King's eruption could teleport onto the player.** When no landing
  tile validated (anchor-only checks that ignored the 4×4/2×2 footprint), the
  fallback was the player's own tile. Landings are now footprint-validated,
  exclude the player, and a failed leap no longer burns the cooldown.
- **Wither's decay-aura cooldown was dead code** (set every turn, never
  checked - the aura is genuinely passive and now documented as such). Its
  pulse-and-approach composite also only works now that composite Move
  sub-actions execute (round-1 dispatcher fix).

### 6.2 Cooldowns charged for nothing

Bastion Brute (summon, charge), Wailing Revenant (all four ability slots),
Void Walker (mirror image, phase strike, rift), Void Herald (endermites,
collapse), Shulker Architect (turret, link) and the Wither (skulls, summon,
charge) all paid their cooldown *before* checking whether the ability could
actually fire - a full-arena or blocked-lane failure locked the ability out
for its whole cooldown anyway. All of them now pay on success only.

### 6.3 Other fixes

- **Bastion Brute's gore charge** stopped only at OBSTACLE/VOID, so it could
  plow into deep water and end on a tile it can't stand on; it now stops at
  anything unwalkable (fire/lava remain charge-through).
- **Wailing Revenant never idles**: when every slot is cooling or capped, the
  stationary artillery boss spits a plain half-power fireball instead of
  wailing at nothing.
- **Phantom** (End roster): its stacking miss-streak lived on the shared AI
  instance - every phantom on the server pooled one streak across fights.
  Per-entity now; its circling reposition also no longer lands on the
  player's or an ally's tile.
- **DragonAI** audited clean - one misleading field renamed
  (`lastSwoopHorizontal` → `lastWaveHorizontal`; it alternates breath-wave
  orientation, unrelated to the swoop axis). Shulker turret and End Crystal
  AIs are stateless and were already correct.

### 6.4 Manual test notes (round 2)

1. Chorus Mind with 3+ plants: the cascade warning tiles take damage on
   resolve; in phase two, new chorus obstacles visibly grow each turn and the
   boss blinks beside them, never onto them.
2. Shulker Architect: bullet storm marks 4 (P2: 6) tiles that each get struck
   next turn; melee the boss next to a turret network - it blinks beside its
   farthest turret, never on top of one.
3. Void Herald phase two: platforms keep collapsing every other turn even
   while gales/lightning telegraphs are active.
4. Molten King at any size: eruptions never land it overlapping a wall, a mob,
   or you.
5. Box a Wailing Revenant's abilities (kill its skeletons, eat the rain):
   it still throws a weak fireball every turn rather than idling.
6. Two phantoms circling: each builds its own speed streak.

## 7. Manual test notes

1. Fight the same boss twice (die or win, then rematch): the second fight
   starts in phase one with fresh cooldowns; the Broodmother lays fresh egg
   sacs and hunts; the Hollow King's arena is lit again.
2. Drop a boss to half HP: subtitle + roar + shake + red flash fire once; the
   boss bar frame turns gold with a "II" badge that persists.
3. Kill a boss: gold defeat line, explosion + golden rain, knell, shake.
4. Molten King: splitting at half HP shows the "splits into fragments" line
   with no defeat fanfare; killing the last fragment gets the full payoff.
5. Stand so a wall hides part of a telegraphed area: the warned tiles ghost
   through the wall, with a pulsing outline around the region.
6. Revenant's Raise the Dead in a Gravefire-Grid arena: zombies appear on
   safe tiles, not in the fire (unless nothing safe remains).


