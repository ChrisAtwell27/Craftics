# Boss improvements

A pass over the boss system: one systemic state bug fixed for all 18+ bosses,
plus new spectacle around the three beats of every boss fight (intro, phase
two, the kill) and clearer telegraph rendering.

All four Stonecutter shards compile and test clean (`1.21.1`–`1.21.5`,
non-active shards verified with `--rerun-tasks`).

---

## 1. The shared-instance state leak — fixed for every boss

`AIRegistry` hands back **one shared AI instance per key**, and `BossAI`
carries a lot of per-fight state on that instance: `phaseTwo`, `turnCounter`,
the ability-cooldown map, the pending telegraph, and the minion/projectile
ID lists. Only three bosses (Molten King, Sandstorm Pharaoh, Tidecaller) had
been given fresh per-fight instances; the other fifteen shared theirs across
every fight in the server's lifetime. Symptoms:

- Any boss killed while in phase two left the **next** boss of its kind
  starting in phase two — no transition, wrong cooldowns, stale turn counter.
- The **Broodmother**'s entire nest state machine (`HUNTING`/`NESTING`, the
  egg-sac list, pending web rain) leaked between fights.
- The **Hollow King**'s `lightsOutPermanent` flag persisted, so a rematch
  started in permanent darkness.
- Stale minion ID lists could alias onto new entity IDs in later fights.

Fix ([AIRegistry.java](../src/main/java/com/crackedgames/craftics/combat/ai/AIRegistry.java)):
boss AIs are now registered through `registerBoss(key, factory)` — the shared
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
| **Phase two** | A silent flag flip — only addons were notified; players inferred it from behavior | Combat-log line, a "⚠ <boss> — PHASE 2 ⚠" subtitle for every party member, ravager roar + smoke/flash burst on the boss, camera shake and a dark-red screen flash on all clients |
| **Defeat** | Generic "X defeated!" line, small poof, orb-pickup ding | "☠ <boss> DEFEATED! ☠" gold line, explosion bloom + golden totem rain, wither-death knell, camera shake + golden screen flash. Skipped for a gen-0 Molten King splitting into fragments — that's not the kill |

Client reactions ride a new `EVENT_BOSS_MOMENT` combat event
([CombatEventPayload.java](../src/main/java/com/crackedgames/craftics/network/CombatEventPayload.java),
handled in [CrafticsClient.java](../src/client/java/com/crackedgames/craftics/CrafticsClient.java));
titles/sounds/particles are server-driven so all co-op clients see the same moment.

## 3. Phase visibility on the HUD

The sync payload now carries a `;phase=2` token for bosses in phase two
(`BossAI.isInPhaseTwo()`); the boss HP bar in
[CombatHudOverlay.java](../src/client/java/com/crackedgames/craftics/client/CombatHudOverlay.java)
shows it permanently — the bar frame turns molten gold and a **II** badge sits
at the bar's right edge. The screen flash tells you the moment happened; the
badge keeps telling you afterwards.

## 4. Telegraph rendering

Boss warning tiles ([TileOverlayRenderer.java](../src/client/java/com/crackedgames/craftics/client/TileOverlayRenderer.java))
were a flat pulsing red fill. Now:

- A **crisp pulsing perimeter outline** around the warned region — reads as
  "the strike lands exactly here", distinct from the softer danger/threat washes.
- A **through-wall ghost pass** (same GREATER-depth xray as the move/attack
  highlights), so a telegraph behind a boulder at a low camera angle is still
  visible — a telegraph you can't see is a hit you can't dodge.

## 5. Summons stop spawning in lava

`BossAI.findSummonPositions` / `findSummonPositionsNear` treated lava and fire
tiles as valid spawn tiles (they're "walkable"). Minion summons now prefer
tiles with no step damage and only fall back to hazards when the arena offers
nothing else (e.g. a late-fight Molten King floor).

## 6. Manual test notes

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
