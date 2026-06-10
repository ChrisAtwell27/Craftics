# UI Improvements: Combat HUD, Level Select, Grid Rendering

Branch: `feat/ui-improvements` (off `main`). Nothing is committed; everything sits in the working tree for review.

This pass covered the three UI systems that carry most of the moment-to-moment experience: the in-combat HUD (`CombatHudOverlay`), the level select screen (`LevelSelectScreen`), and the tile grid rendering/selection pipeline (`TileOverlayRenderer`, `TileRaycast`, `ClientGridHelper`, `CombatInputHandler`). Below: what was found, what was fixed, and what I'd suggest doing next.

All four Stonecutter shards compile after the changes (`1.21.1`, `1.21.3`, `1.21.4`, `1.21.5` via `compileClientJava`).

---

## 1. Combat HUD (`src/client/.../client/CombatHudOverlay.java`)

### Findings

| # | Finding | Severity |
|---|---------|----------|
| 1.1 | There was no visible way to end a turn. The R keybind is the only path, and nothing on screen tells you it exists. For a tactical RPG this is the single most-pressed action in the game and it was invisible to new players. | High (UX gap) |
| 1.2 | The turn banner's fade logic was dead code: `alpha` was computed and then unconditionally set back to 255, so the banner snapped from "YOUR TURN - Turn N" to "Turn N" with no animation. The age counter also incremented per *frame*, not per tick, so the collapse timing ran 2 to 4 times faster on high-refresh displays. | Medium (bug) |
| 1.3 | All HP bars (player, party, allies, enemies, boss, inspect panels) snapped instantly to new values. Damage was easy to miss, especially on the enemy roster during AoE turns. | Medium (UX) |
| 1.4 | The AP/SPD resource bar showed a fixed 3 pips per section (overflow shared between sections). With `maxAp` of 5, spending 5 -> 4 -> 3 changed *nothing on screen*; pips only started draining below 3. The pips actively lied about your resources. | Medium (bug) |
| 1.5 | The collapsed enemy roster ("+N more") double-counted the boss: `remaining` was computed from the total enemy count while the boss renders separately, and the mini-head skip loop iterated the raw map where the boss could be among the first entries. Result: wrong "+N" count and a shown enemy duplicated in the mini list. | Low (bug) |
| 1.6 | Attack AP cost was not surfaced anywhere in combat; players had to learn the cost of an attack by trial and error. | Low (UX) |
| 1.7 | No feedback on how many movement points a move will cost before clicking. | Medium (UX) |

### Fixes implemented

- **Clickable End Turn button** (bottom center, above the mode pill). Shows the live keybind label (`[R]`, read from the actual binding so rebinds are reflected), highlights on hover, and pulses its border once both AP and movement are fully spent as a "you're done here" nudge. Click routing goes through `CombatHudOverlay.tryClickHudButtons()`, called from `CombatInputHandler` *before* tile targeting so the click never falls through to the grid. Only rendered on the local player's turn.
- **Turn banner rebuilt** ([CombatHudOverlay.java](../src/client/java/com/crackedgames/craftics/client/CombatHudOverlay.java)): wall-clock timing (frame-rate independent), a real 600 ms crossfade from the full banner to the compact "Turn N" pill at the 1.5 s mark, and a short entrance "pop" scale whenever the phase flips so turn changes register peripherally. The `fadeTurnBanner` config still disables the collapse.
- **Smooth HP bars with damage ghosts** via a shared `drawHpBar()` helper. The colored fill snaps to the real value (the truth is never delayed) while a bright ghost segment marks the chunk just lost and drains toward it; heals sweep a light segment upward. Applied to: solo player bar, party list, ally roster, enemy roster rows, the boss bar, and both inspect panels. Animation state is keyed per on-screen bar and cleared when combat ends, so reused entity ids across levels can't bleed animations.
- **Resource pips now show one pip per point**, shrinking pip size as totals grow (10 px up to 8 total pips, down to 5 px at extremes) so the row always fits and every spend visibly drains a pip. Numeric labels stay.
- **Enemy roster boss counting fixed**: the boss is identified up front, all row/remainder math runs on a non-boss list, and the mini-head remainder is taken from that same list. "+N more" is now exact.
- **Mode pill shows attack cost** ("ATTACK · 2 AP", reading `attackApCost` from config) on melee/ranged modes.
- **Move cost at the cursor**: hovering a reachable tile in MOVE mode shows a small "N SPD" tag beside the cursor (gold when it spends your last point, blue otherwise). Fed by the same path the grid renderer previews, so the number always matches the drawn route.

### Suggested next steps

- **Turn-order strip with mob portraits.** `renderTurnOrder` only lists party players. Showing enemies in initiative order (mini mob heads via `MobHeadTextures`) would let players plan around who acts next; the data would need a small extension of the turn-order sync payload. *(→ implemented in round 2, §6.2 — no payload extension needed: sync order already mirrors act order.)*
- **Enemy HP text on hover only.** The roster draws `hp/max` for every enemy; with 7 enemies it gets dense. Consider numbers on hover or for damaged enemies only. *(→ implemented in round 2, §6.2 — damaged-only variant.)*
- **Damage-type icons in the inspect panel.** The panel shows ATK/DEF/SPD/RNG; surfacing elemental affinity (the mod has `MobThemeTags`) would support weapon-choice decisions. *(→ implemented in round 2, §6.2.)*
- **Low-HP edge vignette** tied to the smooth bar (e.g. below 25%), reusing `CombatVisualEffects` so it respects `disableCameraShake`/accessibility settings. *(→ implemented in round 2, §6.2.)*

---

## 2. Level Select (`src/client/.../client/LevelSelectScreen.java`)

### Findings

| # | Finding | Severity |
|---|---------|----------|
| 2.1 | Cards were not clickable. Visible side cards could only be reached via arrows/scroll/keyboard; clicking a card did nothing, which fails the most basic pointing-device expectation for a carousel. | High (UX gap) |
| 2.2 | No progress overview. Nothing tells you how far through a region you are, or how many biomes a page holds, without scrolling through every card. | Medium (UX) |
| 2.3 | Enter key did nothing; the only confirm was the button. | Low (UX) |
| 2.4 | Locked tabs show a padlock but never explain how to unlock; locked focused cards showed no name at all (not even a "???"). | Low (UX) |
| 2.5 | Footer hint didn't mention mouse interaction. | Trivial |

### Fixes implemented

- **Cards are clickable**: clicking a side card selects it (smooth-scrolls to it), clicking the already-focused card enters the biome (locked cards just select). Hit boxes are captured during render so they always match the drawn carousel, including mid-animation.
- **Hover feedback**: non-focused cards lighten their dim overlay and get a thin outline under the cursor, so clickability is communicated before the first click.
- **Progress dots** under the carousel, one per biome on the page: green = cleared, gold = the next biome to tackle, dark = locked, white ring = current selection. Dots are clickable to jump. They auto-shrink and disappear gracefully if an addon page has an absurd biome count.
- **Enter / numpad-Enter** activates the focused biome (same path as the button and focused-card click; the action was extracted into `activateFocusedBiome()` so all three stay in sync).
- **Tab tooltips**: hovering an unlocked tab shows "N / M biomes cleared"; hovering a locked tab explains "Clear earlier biomes to unlock this region."
- **"???" name** on focused locked-and-undiscovered cards, in a muted color, instead of total silence.
- **Footer hint** updated: `Q / E: dimension · <- ->: browse · click card: select · Enter: play`.

Note on click routing: tabs, dots and cards are hit-tested *before* `super.mouseClicked` because `HandledScreen` consumes every click (cursor-stack handling), so anything tested after it would never fire. The vanilla widgets (arrows, Enter button) don't overlap those regions at any realistic window size.

### Suggested next steps

- **Animated card focus**: scale the focused card up slightly (1.05x) rather than only dimming neighbors; one `matrices.scale` around the card center. *(→ implemented in round 2, §6.3.)*
- **Tab overflow**: many addon pages could overflow the tab bar width; switch to a scrolling tab strip past ~6 tabs. *(→ implemented in round 2, §6.3.)*
- **Gamepad/arrow-only flow**: Tab/Shift-Tab already cycle vanilla widgets, but Up/Down could move between tab row, carousel and button for full keyboard play. *(→ partially in round 2, §6.3: Up/Down cycle dimension rows; full widget focus traversal not done.)*

---

## 3. Grid Rendering & Selection (`TileOverlayRenderer`, `TileRaycast`, `ClientGridHelper`)

### Findings

| # | Finding | Severity |
|---|---------|----------|
| 3.1 | Cursor picking ignored entities. The ray went straight to blocks/the floor plane, so at the combat camera's oblique angle, clicking a tall mob's visible body selected the tile *behind* it. Players were missing attacks they had aimed correctly. | High (bug/UX) |
| 3.2 | Move/attack regions were flat translucent fills with no borders. Region edges (the exact thing a tactics player scans for: "can I reach that tile?") were mushy, especially over busy terrain. | Medium (UX) |
| 3.3 | No movement path preview. The mod showed *where* you can go but not *how* you'll get there, which matters with hazards, webs and AoE-threatened tiles on the route. | Medium (UX) |
| 3.4 | All pulse animations (`warning tiles`, hover) advanced a counter per frame, so the pulse speed depended on FPS (a 144 Hz player saw 2.4x faster flashing than a 60 Hz player). | Low (bug) |
| 3.5 | `ClientGridHelper.isTileWalkable` treated any non-air block at body height as an obstacle, but the server (`ArenaBuilder`/`TileType`) classifies tall grass, large ferns, cobwebs and stair ramps as walkable. Client-side previews (enemy movement ranges on hover) showed enemies as unable to path through tiles they absolutely can. | Medium (bug) |
| 3.6 | The renderer body was duplicated ~170 lines per Stonecutter branch (`<=1.21.4` and `1.21.5+`), so every visual tweak had to be written twice and they had already started drifting. | Medium (maintainability) |
| 3.7 | The legacy (`<=1.21.4`) path issued one draw call per quad (begin/end per tile). | Low (perf) |

### Fixes implemented

- **Entity-aware tile picking** ([TileRaycast.java](../src/client/java/com/crackedgames/craftics/client/TileRaycast.java)): the cursor ray now tests mob and player bounding boxes inside the arena first; the nearest entity hit wins and resolves to the tile that entity stands on. A block hit closer than the entity still wins, so mobs can't be picked through walls, and invisible (stealthed) mobs are skipped so sweeping the cursor can't reveal a hidden enemy's position.
- **Region edge outlines**: move and attack sets now draw a crisp, brighter perimeter (edge strips only where the neighbor tile is outside the set), and AoE damage previews get an amber outline marking exactly which tiles are clickable for empty-tile AoE shots. Corner strips are shortened so translucent overlap doesn't create bright dots.
- **Hover cursor ring**: the hovered tile keeps its pulsing fill and gains a bright 4-edge outline ring on top of everything, reading as a proper cursor.
- **Movement path preview**: hovering a reachable tile in MOVE mode draws breadcrumb squares along the BFS shortest path from your tile (cached, recomputed only when either endpoint changes). The HUD's "N SPD" cursor tag reads the same cached path, so visuals and numbers can't disagree. Degrades silently (no breadcrumbs) where the client can't reproduce a server-legal path.
- **Wall-clock pulses**: warning-tile and hover pulses are now `System.currentTimeMillis()`-based and identical at any frame rate.
- **Walkability parity** ([ClientGridHelper.java](../src/client/java/com/crackedgames/craftics/client/ClientGridHelper.java)): tall grass, large ferns, cobwebs, stairs and bottom slabs are now passable at body height in the client helper, mirroring `ArenaBuilder`'s tile scan, plus a polygon-mask check in the new path BFS so previews respect non-rectangular arenas.
- **Renderer de-duplicated** ([TileOverlayRenderer.java](../src/client/java/com/crackedgames/craftics/client/TileOverlayRenderer.java)): all highlight logic now lives in one version-independent `buildQuads()` that emits a `Quad` list; the per-version branches are thin GPU flushes. The legacy branch also batches everything into a single buffer upload instead of one draw per tile. Any future visual change is written once and both branches get it.

### Suggested next steps

- **Threat overlay toggle.** The config already has `showEnemyRangeHints`/`showEnemyIntentions`; a "show all enemy reach" hotkey overlaying the union of enemy movement+attack ranges (classic Fire Emblem "danger zone") would be the single biggest tactical-readability win. The per-enemy preview machinery in `ClientGridHelper.getMovePatternTiles` already does the math. *(→ implemented in round 2, §6.1.)*
- **Path-cost-aware preview.** The client BFS counts steps, not server move *cost* (`GridTile.getMoveCost` charges hazards 50). Syncing a compact per-tile cost grid in `TileSetPayload` would let the breadcrumbs route around lava exactly like the server will. *(→ investigated in round 2, §6.1 — the premise was wrong for players; a deep-water walkability gap was the real bug and was fixed instead.)*
- **Through-wall highlight pass.** Highlights are depth-tested, so tiles behind obstacles vanish at low camera angles. A second, very faint depth-ignoring pass (or `RenderLayer` with `ALWAYS` depth) would keep the move region readable without rotating the camera. *(→ implemented in round 2, §6.1 — with a GREATER depth test rather than ALWAYS, so the ghost shows only where occluded.)*
- **Ally pass-through parity.** Server pathfinding lets you move *through* allies but not stop on them; the client preview treats every mob tile as a hard blocker, so a path threading an ally shows no breadcrumbs (move still works). Distinguishing ally tiles in the BFS would close the last preview gap. *(→ implemented in round 2, §6.1.)*
- **Hover snap hysteresis.** The raycast biases away from tile edges, but a tiny "keep previous tile unless the new one is >0.1 into its area" hysteresis would remove residual flicker when the cursor rides a boundary. *(→ implemented in round 2, §6.1.)*

---

## 4. Cross-cutting observations

- **HUD widgets + GLFW input.** Combat clicks are polled raw (GLFW) rather than through a `Screen`, so HUD buttons need manual rect bookkeeping (`endTurnBtnRect` published by the overlay, consumed by the input handler). If more HUD buttons are planned (action bar, ability buttons), it's worth extracting a tiny `HudButton` helper that owns rect, hover, click and uiScale mapping in one place.
- **`largerUI` scaling** is handled by multiplying the stored rect by `uiScale` and dividing the mouse position once; any future clickable HUD element must go through the same `mouseGuiPos` helper or hit boxes will drift at 1.5x.
- **Config reads in render code** are wrapped in `try/catch` per call (existing pattern). Fine functionally, but a once-per-frame snapshot of the config values at the top of `onHudRender` would tidy the hot path.
- **Manual test checklist** (rendering can't be covered by the JUnit setup):
  1. Enter combat: End Turn button appears bottom-center on your turn, disappears on the enemy turn; clicking it ends the turn; hover highlight works; label matches a rebound key; border pulses when AP and SPD are both 0.
  2. Click directly on a tall enemy (enderman/ravager) from a low camera angle: the attack targets that enemy, not the tile behind.
  3. Hover a far move tile: breadcrumbs draw along the route and the cursor tag shows the matching SPD count; both vanish in attack mode and on the enemy turn.
  4. Take a big hit: every visible bar (yours, party, boss on its side) shows the pale drain ghost; heals sweep up.
  5. Fight with a boss plus 5+ enemies and `compactEnemyList` on: "+N more" equals the actual hidden count and no mini-head duplicates a listed row.
  6. Turn banner: full banner pops in on phase change, melts into "Turn N" after ~1.5 s; with `fadeTurnBanner=false` it stays full.
  7. Level select: click side cards/dots to navigate, click focused card or press Enter to start, hover a locked tab for the unlock tooltip, check the "???" label on a locked focused card.
  8. Toggle `largerUI` and repeat 1 and 3 (hit boxes and cursor tags must still line up).
  9. Toggle `colorblindMode` and check move/attack/hover tints still differ.
  10. Stand next to tall grass concealing an enemy: hovering the grass from afar must not reveal it (entity picking skips invisible mobs).

---

## 5. Files touched (round 1)

| File | Change |
|------|--------|
| [CombatHudOverlay.java](../src/client/java/com/crackedgames/craftics/client/CombatHudOverlay.java) | End Turn button, banner rewrite, smooth HP bars, pip fix, roster count fix, AP cost on mode pill, SPD cursor tag, mouse/GUI mapping helpers |
| [CombatInputHandler.java](../src/client/java/com/crackedgames/craftics/client/CombatInputHandler.java) | HUD buttons get first claim on clicks before tile targeting |
| [CrafticsClient.java](../src/client/java/com/crackedgames/craftics/CrafticsClient.java) | `getEndTurnKey()` accessor for the button label |
| [TileOverlayRenderer.java](../src/client/java/com/crackedgames/craftics/client/TileOverlayRenderer.java) | De-duplicated core, region outlines, hover ring, path breadcrumbs, FPS-independent pulses, batched legacy draw |
| [TileRaycast.java](../src/client/java/com/crackedgames/craftics/client/TileRaycast.java) | Entity-aware picking with wall occlusion and stealth guard |
| [ClientGridHelper.java](../src/client/java/com/crackedgames/craftics/client/ClientGridHelper.java) | Walkability parity with server tiles, `getPathTo` BFS for previews |
| [LevelSelectScreen.java](../src/client/java/com/crackedgames/craftics/client/LevelSelectScreen.java) | Clickable cards + hover, progress dots, Enter key, tab tooltips, "???" labels, footer |

---

## 6. Round 2 — the suggested next steps, implemented

A follow-up pass worked through the "suggested next steps" from sections 1–3 (each bullet above is annotated with its outcome). All four shards compile after these changes too (`compileClientJava` for `1.21.1`–`1.21.5`, verified with `--rerun-tasks` so stale Stonecutter-generated sources can't mask errors).

### 6.1 Grid

- **Threat overlay ("danger zone")** on a new keybind (default **Y**, in the Craftics controls category, with a lang entry and a combat-log confirmation on toggle). It unions, over every enemy, the tiles that enemy could *stand on* this turn (the same per-movement-style BFS the hover preview uses, fed by the `spd=`/`mv=` tags already in the enemy sync) with the tiles it could *strike* from any of those positions — a Chebyshev ring for melee, a Manhattan diamond for ranged, using the synced `range=` tag. Drawn as a deep-red wash with a perimeter outline *underneath* your own move/attack highlights so your options stay readable on top. Hidden while Blinded (the overlay would otherwise leak exactly what blindness is meant to take away), cached for 400 ms (enemy positions only change on action events), and force-reset when combat ends.
- **Path-cost parity — investigated, premise corrected.** The round-1 suggestion assumed the client preview needed the server's per-tile move costs. Reading `findPathPlayer` showed the server charges *players* cost 1 per step everywhere — hazard tiles hurt you when you step on them (damage per step) instead of costing extra movement; the cost-50 hazard charge applies to mob pathfinding only. So a synced cost grid would have made the preview *wrong*, not better. The real client/server gap was **deep water**: a 2-deep water column is an instant-kill `DEEP_WATER` tile server-side, but `ClientGridHelper.isTileWalkable` treated it as walkable, so previews happily routed through it. Fixed in `isTileWalkable` (water-over-water is now unwalkable client-side).
- **Through-wall ghost pass.** Move/attack fills, the path breadcrumbs, and the hover cursor are re-rendered at low alpha with a **GREATER** depth test, so the faint copy appears *only* where terrain occludes the normal pass — no double-brightness where both passes are visible. On ≤1.21.4 this is plain `RenderSystem` depth-state toggling. On 1.21.5 the render-pipeline refactor made `RenderLayer.of` package-private, so the shard gets a custom GREATER-depth position-color pipeline (replicated from the now-private vanilla snippet and verified against the 1.21.5 jar via `javap`) plus two small invoker mixins — `RenderLayerInvoker` and `RenderPhaseBuilderInvoker` — which are empty interfaces on the older shards.
- **Ally pass-through parity.** The path-preview BFS now threads routes *through* ally-occupied tiles without allowing a stop on them, matching server pathfinding, so breadcrumbs no longer vanish when an ally stands mid-route.
- **Hover snap hysteresis.** Floor-plane picks keep the previous tile until the cursor travels ~0.08 tiles into a cardinal neighbor, killing boundary flicker; entity and block-face picks still snap instantly (they're already stable).

### 6.2 Combat HUD

- **Enemy act-order strip.** During the enemy phase, a centered strip of unit portraits below the turn banner (and below the party turn-order panel in multiplayer, via a shared bottom-Y handoff) shows the order units will act. No payload extension was needed: the enemy sync order already mirrors the server's `enemies` list, which *is* the act order — `CombatState` now records it (`getUnitActOrder`). The unit currently acting — learned from the attack-animation event, so best-effort with a 2.5 s freshness window (`noteActingEnemy`/`getActingEnemyId`) — gets a gold frame; a green/red underline under each portrait reads friend/foe at a glance. Caps at 10 portraits plus a "+N" tail.
- **HP text only when damaged.** Enemy roster rows draw the gray `hp/max` numbers only once the enemy has taken damage — a full bar already says "full", and the untouched roster reads much quieter with 6+ enemies.
- **Theme line in the inspect panel.** Hovering a themed enemy now shows a color-coded "◆ Hit: Soaked 2t" / "Poison 2t" / "Weakness 2t" line between the stat rows and the effects list. It reads the same `MobThemeTags` registry the server resolves on-hit effects from (vanilla tags are registered in its static init, compat-module tags at mod init), so the hint can't drift from the actual on-hit behavior. Skipped for allies.
- **Low-HP edge vignette.** At ≤30% HP a pulsing red edge vignette fades in, deepening and widening as HP falls further; rendered with the other status vignettes in `CombatVisualEffects.render`. It deliberately skips 0 HP — the death overlay owns that moment — and, like all the status vignettes, only ever reflects the local player's own HP.

### 6.3 Level select

- **Animated card focus scale.** Cards swell up to +10% as they approach the focus slot. The scale is driven by the *eased* `scrollOffset` (not the selected index), so the size change slides with the carousel instead of popping on selection change; click hit-boxes are captured at the scaled size so visuals and clicks can't disagree.
- **Tab overflow scrolling strip.** While every tab fits, the bar stays centered exactly as before. Once regions/addon pages overflow the width, the bar becomes a scissored viewport that keeps the active tab centered (eased scroll), with ◀ ▶ chevrons marking hidden tabs on either side. Hover and click regions clamp to the viewport so a clipped tab can't be hit through the chevrons.
- **Up/Down dimension navigation.** Up/Down arrows cycle dimension rows (same path as Q/E, skipping locked pages); the footer hint now reads `↑ ↓ / Q / E: dimension`.

### 6.4 Files touched (round 2)

| File | Change |
|------|--------|
| [CrafticsClient.java](../src/client/java/com/crackedgames/craftics/CrafticsClient.java) | Y threat-overlay keybind registration + toggle handling with combat-log confirmation |
| [CombatState.java](../src/client/java/com/crackedgames/craftics/client/CombatState.java) | Threat overlay state + 400 ms cache, unit act-order list from sync, acting-enemy tracking from attack-anim events |
| [ClientGridHelper.java](../src/client/java/com/crackedgames/craftics/client/ClientGridHelper.java) | Deep-water unwalkable, ally pass-through in the preview BFS, threat-union math |
| [TileOverlayRenderer.java](../src/client/java/com/crackedgames/craftics/client/TileOverlayRenderer.java) | Threat wash + outline pass, GREATER-depth ghost pass for fills/breadcrumbs/cursor |
| [TileRaycast.java](../src/client/java/com/crackedgames/craftics/client/TileRaycast.java) | Hover snap hysteresis on floor-plane picks |
| [RenderLayerInvoker.java](../src/client/java/com/crackedgames/craftics/mixin/client/RenderLayerInvoker.java), [RenderPhaseBuilderInvoker.java](../src/client/java/com/crackedgames/craftics/mixin/client/RenderPhaseBuilderInvoker.java) | 1.21.5-only invoker mixins for the custom GREATER-depth pipeline (empty interfaces on ≤1.21.4); registered in `craftics.client.mixins.json` |
| [CombatHudOverlay.java](../src/client/java/com/crackedgames/craftics/client/CombatHudOverlay.java) | Act-order strip, damaged-only roster HP text, theme line in the inspect panel |
| [CombatVisualEffects.java](../src/client/java/com/crackedgames/craftics/client/CombatVisualEffects.java) | Low-HP pulsing edge vignette |
| [LevelSelectScreen.java](../src/client/java/com/crackedgames/craftics/client/LevelSelectScreen.java) | Card focus scale, tab overflow scrolling strip, Up/Down dimension keys |
| `assets/craftics/lang/en_us.json` | `key.craftics.threat_overlay` entry |

### 6.5 Manual test additions (round 2)

1. Press Y in combat: red threat wash appears under your highlights and outlines the reachable+strikeable union; toggling logs a combat-log line; the overlay vanishes while Blinded and after combat ends.
2. Stand behind a boulder at a low camera angle: move fills, breadcrumbs and the hover cursor show a faint ghost *only* through the obstruction, with no brightening where they were already visible.
3. Preview a route across 2-deep water: no breadcrumbs through it; shallow water still routes.
4. Put an ally mid-corridor: breadcrumbs thread through the ally's tile but you cannot end the move on it.
5. Ride the cursor along a tile boundary: the hovered tile holds until the cursor is clearly inside the neighbor.
6. Enemy phase: the act-order strip lists portraits in the order units move; the acting unit lights up gold within a beat of its attack starting; allies in the queue show green underlines; 11+ units collapse into "+N".
7. Hover a drowned/stray/ocelot: the inspect panel shows the matching "Hit: …" theme line; a zombie shows none; an ally never shows one.
8. Drop below 30% HP: red edge pulse appears and deepens as HP falls; it disappears on heal above 30% and yields to the death overlay at 0.
9. Enemy roster: full-HP enemies show no numbers; any damaged enemy shows `hp/max`.
10. Level select: cards grow/shrink smoothly while scrolling; with enough addon pages the tab bar scrolls (chevrons appear) and the active tab stays centered; Up/Down switch dimensions.
