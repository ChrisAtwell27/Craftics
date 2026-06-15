# Combat animation improvements

A pass over every custom animation in the mod: the mob attack-animation engine
was rebuilt around a per-mob-type style registry (addon-extensible), the four
dormant pose states are now wired into real combat moments, and a multiplayer
bug in the player weapon animations was fixed.

All four Stonecutter shards compile and test clean (`1.21.1`-`1.21.5`,
non-active shards with `--rerun-tasks`).

---

## 1. Per-mob attack styles - the registry

[MobAttackAnimations.java](../src/main/java/com/crackedgames/craftics/combat/animation/MobAttackAnimations.java)
replaces the old ~250-line hardcoded `isSpiderLike`/`isHeavyHitter` branch
chain inside `CombatManager.tickEnemyAnimating`. Every attack now resolves a
**Style** - a tick-indexed curve of (forward, rise) offsets with a strike frame
- from a registry keyed by entity type id.

| Style | Motion | Default mobs |
|-------|--------|--------------|
| `LUNGE` | ease forward, ease back | everything unregistered |
| `POUNCE` | crouch low, spring forward+up | spiders, cave spiders |
| `SLAM` | rise tall, fast forward slam | iron golem, ravager, warden, hoglin, zoglin, polar bear |
| `DASH` | fast low dash, held strike pose | wolf, fox, ocelot, cat, vex, phantom |
| `BOUNCE` | hop straight up, slam down | slime, magma cube |
| `BLINK` | sink-flicker, jump-cut beside target | enderman, endermite |
| `RAM` *(new)* | back up a step, head-down charge with extra reach | goat, camel |
| `JAB` *(new)* | two quick small pokes | silverfish, bee, chicken, parrot, rabbit, frog |
| `CAST` *(new)* | stand and channel, arms raised, release flick | witch, evoker, illusioner |
| `RANGED_DRAW` | lean away to aim, snap forward on release | any ranged attack |

Ranged attacks always play the draw unless the mob is a registered caster - a
witch lobbing a potion channels instead of nocking an invisible arrow.

**Addon mobs** get unique attack animations with one line at mod init:

```java
CrafticsAPI.registerAttackAnimation("mymod:lava_crab", MobAttackAnimations.Style.POUNCE);
```

Unregistered mobs keep the default lunge, so nothing breaks without a
registration. Last registration wins, so addons can also re-style vanilla mobs.

## 2. Pose states - all eight wired now

The `AnimState` pose system (server-set, CCA-synced, applied by the client
`BipedAnimMixin`) had eight states but only WINDUP and HIT ever fired:

| State | Now triggered by |
|-------|------------------|
| `WINDUP` | attack start (unchanged) - except casters, see CAST |
| `ATTACK` | **the strike frame of every melee style** - biped arm snap + sweep FX, in sync with the positional impact instead of a lone hand-swing at windup start |
| `RECOIL` | **animation end** - the arm eases back to neutral; previously WINDUP stayed latched (arm cocked) until the safety tick expired it ~11 ticks later |
| `HIT` | taking damage (unchanged) |
| `CAST` | **caster-style attacks** (witch/evoker channel for the whole lob) and **every boss telegraph turn** - a boss charging an ability now visibly channels, arms raised, while the warning tiles pulse |
| `ROAR` | **boss phase-two transition** - head back, arms wide, ring burst (the roar sound moved here from the announce code so it isn't played twice) |
| `STUNNED` | **the stunned-turn skip** - head down, slack arms, wobbling daze instead of an unexplained skipped turn |
| `IDLE` | reset (unchanged) |

The per-style strike frame also owns the hand swing now - the old flow swung
once at windup start (before the mob had even moved) and again mid-animation
for some types.

## 3. Player weapon animations (client)

[CombatAnimations.java](../src/client/java/com/crackedgames/craftics/client/CombatAnimations.java)
keeps its 11 weapon-specific animations untouched, but the state machine had a
real multiplayer bug: **one shared attack timer for all avatars.** Attack
animations play on every party member's avatar, so a teammate's swing
overwrote the local timer (cutting animations short), and expiry always faded
the *local* player's layer - remote avatars froze at the last frame of their
swing. Fixed with per-player countdowns (weak-keyed, GC-safe), and
`stopAll()` now clears every tracked avatar's layer at combat end, not just
the local one.

## 4. Manual test notes

1. Watch a goat attack: it backs up half a step, drops its head, and slams
   through with visibly more reach than a zombie's lunge.
2. A silverfish or bee attacking pokes twice in quick succession.
3. A witch attacking raises both arms and channels (enchant particles
   converging), then flicks forward as the potion lands - no archer lean.
4. A zombie's arm: cocks on windup, snaps forward exactly when the lunge
   connects (sweep flash), then eases back down - it no longer stays cocked
   for half a second after the hit.
5. Any boss on a telegraph turn stands channeling with raised arms while the
   red tiles pulse; at phase two it rears back in a roar pose with the ring
   burst.
6. Stun an enemy: on its skipped turn it visibly slumps and wobbles.
7. Co-op: two players attacking in the same window each play their own full
   swing; after combat no avatar is left frozen mid-swing.
8. An addon mob registered with `CrafticsAPI.registerAttackAnimation` plays
   its chosen style; an unregistered one still lunges.

