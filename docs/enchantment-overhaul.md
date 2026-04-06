# Enchantment Overhaul Design Document

Every enchantment should feel like it fundamentally changes how you play.
Weapon enchants reshape your combat identity. Armor enchants create reactive
playstyles. Utility enchants provide tactical options that reward smart play.

When implementing these changes, they do NOT need the extra name like "Inferno Sweep" or anything like that. Just the description of what it does in the Guide Book is enough. No flavor text is needed.
---

## WEAPON ENCHANTMENTS

### Fire Aspect (Sword)  — INFERNO SWEEP
Swing sends a **cone of fire** (2 tiles deep, widening outward) in the attack
direction. All enemies in the cone catch fire. Primary target still takes the
full melee hit.

| Level | Cone Size | Burn Damage/Turn | Burn Duration |
|-------|-----------|-----------------|---------------|
| 1     | 2 deep, 3 wide | 1 | 2 turns |
| 2     | 3 deep, 5 wide | 2 | 3 turns |


---

### Sharpness (Sword) — BLEEDING WOUNDS
Attacks apply stacking **Bleed** debuffs instead of flat bonus damage. Each
stack of bleed grants 1 extra damage when attacked again.

Sharpness should also have a flat +1 extra damage per level

| Level | Stacks Per Hit |
|-------|---------------|
| 1     | 1 |
| 2     | 2 |
| 3     | 3 |


---

### Smite (Sword) — HOLY RADIANCE
Hitting an undead enemy releases a burst of radiant light. All undead in the
radius take 2 damage and lose 1 movement next turn (holy energy saps
them). Non-undead enemies in radius are revealed if stealthed.

| Level | Damage | Radius |
|-------|-------------|--------|
| 1     | 2 | 2 tile |
| 2     | 4 | 2 tiles |
| 3     | 6 | 3 tiles |
| 4     | 8 | 3 tiles |
| 5     | 10 | 4 tiles |

---

### Bane of Arthropods (Sword) — VENOM INJECTION
Hitting an arthropod injects spreading venom: Poison + Slowness. If a
poisoned arthropod dies, venom **chains** to all adjacent arthropods.

| Level | Poison/Turn | Slowness | Chain Range |
|-------|------------|----------|-------------|
| 1     | 1 | -1 speed, 2 turns | Adjacent |
| 2     | 2 | -1 speed, 3 turns | Adjacent + 1 tile |
| 3     | 3 | -2 speed, 3 turns | Adjacent + 2 tiles |
| 4     | 4 | -2 speed, 3 turns | Adjacent + 3 tiles |
| 5     | 5 | -3 speed, 3 turns | Adjacent + 4 tiles |


---

### Knockback (Sword) — SHOCKWAVE SLASH
Creates a **directional shockwave** through the target. All enemies in a line
behind the target get pushed back too. Enemies slammed into walls or other
enemies take **collision damage**.

| Level | Push Distance | Collision Damage |
|-------|--------------|-----------------|
| 1     | 2 tiles | 2 |
| 2     | 3 tiles | 4 |

---

### Sweeping Edge (Sword) — WHIRLWIND
Full 360-degree spin attack that hits **all** adjacent enemies with scaling
damage. At max level, also pushes hit enemies 1 tile outward.

| Level | AoE Damage % | Knockback |
|-------|-------------|-----------|
| 1     | 60% | None |
| 2     | 75% | None |
| 3     | 90% | 1 tile |

*Fantasy: Surrounded? Good. Spin to win.*

---

### Looting (Sword) — PLUNDER
Kills have a chance to give extra loot drops from the enemy killed.

| Level | Extra Drop Chance |
|-------|------------------|
| 1     | +50% |
| 2     | +100% |
| 3     | +150% |

---

## BOW ENCHANTMENTS

### Power (Bow)
Increases bow damage and attack range.

| Level | Bonus Damage | Bonus Range |
|-------|-------------|-------------|
| 1     | +1 | +1 |
| 2     | +3 | +1 |
| 3     | +5 | +2 |
| 4     | +8 | +2 |
| 5     | +11 | +3 |

---

### Flame (Bow)
Arrow hits burn the target and all enemies adjacent to the target.

| Level | Burn Damage/Turn | Burn Duration |
|-------|-----------------|---------------|
| 1     | 1 | 2 turns |
| 2     | 3 | 4 turns |

---

### Infinity (Bow)
Arrows are never consumed. Only requires 1 arrow in inventory to fire unlimited shots.

---

### Punch (Bow) — IMPACT SHOT
Arrows explode on impact, creating a **radial knockback burst**. Target and
adjacent enemies are pushed outward. Enemies knocked into walls or each
other take collision damage.

| Level | Knockback | Collision Damage | Radius |
|-------|----------|-----------------|--------|
| 1     | 1 tile | 1 | Adjacent to target |
| 2     | 2 tiles | 2 | 1 tile from target |

*Fantasy: Concussive rounds. Each arrow is a small bomb.*

---

## CROSSBOW ENCHANTMENTS

### Quick Charge (Crossbow)
Reduces the AP cost of firing the crossbow.

| Level | AP Reduction |
|-------|-------------|
| 1     | -1 (min 1) |
| 2     | -2 (min 1) |
| 3     | -3 (min 1) |

---

### Multishot (Crossbow)
Fires the main bolt plus 2 extra bolts at 45-degree angles in both directions.
Each side bolt hits the first enemy in its path for 50% damage.

---

### Piercing (Crossbow)
Bolts pierce through enemies in a line, hitting additional targets behind the
first. Also inflicts Bleed (like Sharpness — 1 extra damage when attacked again
per stack) and increases bolt damage.

| Level | Extra Targets | Bonus Damage | Bleed Stacks |
|-------|--------------|-------------|-------------|
| 1     | 1 | +1 | 1 |
| 2     | 2 | +2 | 1 |
| 3     | 3 | +3 | 2 |
| 4     | 4 | +4 | 3 |

---

## MACE ENCHANTMENTS

### Density (Mace) — GRAVITY WELL
Strike creates a **gravitational pull** at the impact point. Nearby enemies
are dragged toward the target tile. Enemies already adjacent take bonus
crushing damage from compression.

| Level | Pull Radius | Pull Distance | Crush Bonus |
|-------|------------|--------------|-------------|
| 1     | 2 tiles | 1 tile | +1 |
| 2     | 3 tiles | 1 tile | +2 |
| 3     | 3 tiles | 2 tiles | +3 |

Combos with Wind Burst (pull them in, then blast them away next hit) and
Sweeping Edge allies.

---

### Breach (Mace) — ARMOR SHATTER
Hits **permanently reduce** the target's defense for the rest of combat.
Once defense reaches 0, further shatters apply **Vulnerable** (target takes
25% bonus damage from all sources).

| Level | Defense Destroyed Per Hit |
|-------|-------------------------|
| 1     | 1 |
| 2     | 2 |
| 3     | 3 |

*Fantasy: First hit cracks the armor. Second hit breaks it. Third hit? There
is no armor.*

---

### Wind Burst (Mace)
Hit releases a shockwave that knocks back all adjacent enemies. Each hit with
Wind Burst also buffs your next Mace attack's damage, stacking per consecutive
Mace hit.

| Level | Knockback | Next Mace Hit Bonus |
|-------|----------|-------------------|
| 1     | 1 tile | +2 |
| 2     | 2 tiles | +3 |
| 3     | 3 tiles | +4 |

---

## TRIDENT ENCHANTMENTS

### Riptide (Trident) — 
Do not change its current effect.



---

### Channeling (Trident) — STORM CALLER
On hit, call a **chain lightning bolt** that jumps between enemies.
Prioritizes Soaked enemies and deals double damage to them.

| Level | Chain Count | Chain Damage 
|-------|-----------|-------------|
| 1     | 1 | 3 (6 if Soaked) 
| 2     | 3 | 6 (8 if Soaked)
| 3     | 5 | 10 (10 if Soaked) 


---

### Loyalty (Trident)
After hitting the primary target, the trident ricochets to nearby enemies
before returning to your inventory.

| Level | Extra Ricochet Targets | Ricochet Damage |
|-------|----------------------|----------------|
| 1     | 1 | 50% |
| 2     | 2 | 50% |
| 3     | 3 | 50% |

---

### Impaling (Trident)
Increases trident damage and inflicts Bleed (like Sharpness — 1 extra damage
when attacked again per stack).

| Level | Bonus Damage | Bleed Stacks |
|-------|-------------|-------------|
| 1     | +1 | 1 |
| 2     | +2 | 1 |
| 3     | +5 | 2 |
| 4     | +8 | 2 |
| 5     | +10 | 3 |

---

## ARMOR ENCHANTMENTS

### Protection — GUARDIAN THRESHOLD
Defense bonus (unchanged: 2 levels = +1 defense). New effect: when total
Protection across all armor reaches **8+**, gain **Last Stand** — the first
hit that would kill you instead leaves you at 1 HP (once per combat).

---

### Fire Protection
Reduces fire damage taken by a percentage. At max level, grants full immunity to Burning.

| Level | Fire Damage Reduction | Burn Immunity |
|-------|---------------------|---------------|
| 1     | 25% | No |
| 2     | 50% | No |
| 3     | 75% | No |
| 4     | 100% | Yes |

---

### Blast Protection — BLAST ABSORPTION
Reduced AoE/explosion damage. The absorbed blast energy is stored and added
as **bonus damage** to your next attack.

| Level | AoE Reduction | Stored as Bonus Damage |
|-------|--------------|----------------------|
| 1     | 30% | 30% of reduced amount |
| 2     | 40% | 50% of reduced amount |
| 3     | 50% | 75% of reduced amount |
| 4     | 60% | 100% of reduced amount |

Stored energy expires after 2 turns if not used. Only stores from one blast
at a time (latest overwrites).

---

### Projectile Protection — DEFLECTION FIELD
Chance to **deflect ranged attacks** back at the shooter, dealing a portion
of the original damage.

| Level | Deflect Chance | Reflected Damage |
|-------|---------------|-----------------|
| 1     | 15% | 50% |
| 2     | 25% | 50% |
| 3     | 30% | 75% |
| 4     | 40% | 100% |

---

### Thorns — RETRIBUTION AURA
**Guaranteed** damage reflection on every melee hit received (no more random
chance). Scales with damage taken — the harder they hit, the more it hurts
them back. At level 3+, also knocks the attacker back.

| Level | Damage Reflected | Knockback |
|-------|-----------------|-----------|
| 1     | 15% of damage taken | None |
| 2     | 25% of damage taken | None |
| 3     | 35% of damage taken | 1 tile |

---

### Feather Falling — WIND WALKER
**Immune to knockback.** When an enemy attempts to knock you back, they are
pushed 1 tile away instead (you stand firm, they recoil). At higher levels,
gain bonus movement after being attacked.

| Level | Knockback Immunity | Recoil | Bonus |
|-------|-------------------|--------|-------|
| 1     | Reduce by 2 tiles | No | — |
| 2     | Full immunity | 1 tile recoil | — |
| 3     | Full immunity | 1 tile recoil | +1 movement next turn |
| 4     | Full immunity | 2 tile recoil | +1 movement next turn |


---

## WATER-THEMED ARMOR ENCHANTMENTS

### Aqua Affinity
Boosts water-based attack damage.

| Level | Bonus Water Damage |
|-------|-------------------|
| 1     | +2 |
| 2     | +4 |
| 3     | +6 |


---

### Respiration — SECOND WIND
Passive regeneration that scales with **missing HP**. The lower your health,
the harder you breathe, the faster you recover. Clutch survival enchantment.

| Level | Above 50% HP | Below 50% HP | Below 25% HP |
|-------|-------------|-------------|-------------|
| 1     | — | +1 HP/turn | +2 HP/turn |
| 2     | +1 HP/turn | +2 HP/turn | +3 HP/turn |
| 3     | +1 HP/turn | +2 HP/turn | +4 HP/turn |


---

### Depth Strider — PRESSURE WAVE
Moving near enemies creates **crushing water pressure**. Enemies you walk
past (within 1 tile of your movement path) take damage and get soaked.
Turn movement itself into an attack.

| Level | Pass-By Damage | Slowness Applied |
|-------|---------------|-----------------|
| 1     | 1 | -1 speed, 1 turn |
| 2     | 2 | -1 speed, 2 turns |
| 3     | 2 | -2 speed, 2 turns + Soaked |

*Fantasy: You move through the battlefield like a current. Everything in
your wake is crushed.*

---

### Frost Walker
Allows you to walk on water tiles without a boat, turning them to ice.

*Fantasy: Winter follows your footsteps. The arena freezes behind you.*

---

## MOVEMENT / UTILITY ARMOR ENCHANTMENTS

### Soul Speed — SOUL HARVEST
Killing enemies grants stacking **speed boosts**. The more you kill, the
faster you become. Snowball enchantment for aggressive players.

| Level | Speed Per Kill | Max Stacks | Duration |
|-------|---------------|-----------|----------|
| 1     | +1 movement | 2 | 2 turns |
| 2     | +1 movement | 3 | 3 turns |
| 3     | +2 movement | 4 | 3 turns |

*Fantasy: You feed on their souls. Each kill makes you faster.*

---

### Swift Sneak — SHADOW STEP
End your turn without attacking → become **partially invisible**. While
invisible: enemies have reduced accuracy, and your first attack from
stealth deals bonus damage.

| Level | Enemy Miss Chance | Stealth Attack Bonus | AP Bonus |
|-------|------------------|---------------------|---------|
| 1     | 20% | +2 damage | — |
| 2     | 30% | +3 damage | — |
| 3     | 40% | +4 damage | +1 AP next turn |

Invisibility breaks when you attack or take damage.

*Fantasy: Patience is a weapon. Disappear, reposition, strike.*

---

## TOOL WEAPON ENCHANTMENTS (Axes, Shovels, Hoes)

### Efficiency
Reduces the AP cost of your equipped weapon.

| Level | AP Reduction |
|-------|-------------|
| 1     | -1 (min 1) |
| 2     | -1 (min 1) |
| 3     | -2 (min 1) |
| 4     | -2 (min 1) |
| 5     | -3 (min 1) |

---

### Fortune — SPOILS OF WAR
Kills have a chance to drop **consumable combat items** (instant health,
splash potion, golden apple effect, etc.) in addition to regular rewards.

| Level | Drop Chance | Item Pool |
|-------|------------|-----------|
| 1     | 15% | Health Potions |
| 2     | 25% | + Strength, Speed potions |
| 3     | 35% | + Golden Apple, Enchanted items |

*Fantasy: You always find something useful on the bodies.*

---

### Silk Touch
Increases the quantity of loot dropped by killed enemies.

| Level | Extra Loot |
|-------|-----------|
| 1     | +1 drop |

---

### Unbreaking
Reduces durability consumed per use. Base cost is 5 durability per use.

| Level | Durability Per Use |
|-------|--------------------|
| 1     | 4 |
| 2     | 3 |
| 3     | 2 |

---

### Mending
Chance to repair the item's durability on kill.

| Level | Repair Chance | Durability Restored |
|-------|--------------|-------------------|
| 1     | 25% | 5 |

---

## FISHING ROD ENCHANTMENTS

### Luck of the Sea
Increases the chance of catching rare loot when fishing. Works similarly to
vanilla — higher levels shift the loot table toward treasure and away from junk.

| Level | Rare Loot Chance Increase |
|-------|--------------------------|
| 1     | +1 tier |
| 2     | +2 tiers |
| 3     | +3 tiers |

---

### Lure
Reduces the AP cost of fishing. Higher levels have a chance to cost no AP.

| Level | AP Reduction | Free Cast Chance |
|-------|-------------|-----------------|
| 1     | -1 (min 1) | — |
| 2     | -1 (min 1) | 15% |
| 3     | -2 (min 1) | 30% |
