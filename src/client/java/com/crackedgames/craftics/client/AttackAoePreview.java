package com.crackedgames.craftics.client;

import com.crackedgames.craftics.combat.AoeShapes;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side preview of a player attack's area of effect, shown while hovering
 * a tile during the player's turn. Mirrors the server damage geometry by
 * reusing {@link AoeShapes} directly, so the preview matches what actually
 * happens. Reading enchant levels client-side works because the held
 * ItemStack's component data is synced to the client.
 *
 * <p>Returns two tile sets: {@link Preview#damageTiles} (tiles that take
 * damage, drawn amber) and {@link Preview#effectTiles} (tiles only pulled /
 * knocked back / debuffed, drawn blue). Each shape method here has a paired
 * use in {@code VanillaWeapons} / {@code CombatManager}; keep them in sync.
 */
public final class AttackAoePreview {

    private AttackAoePreview() {}

    /** The two tile layers to highlight. */
    public record Preview(Set<GridPos> damageTiles, Set<GridPos> effectTiles) {
        static Preview empty() {
            return new Preview(Set.of(), Set.of());
        }
    }

    /**
     * Compute the AoE preview for the player's held weapon aimed at
     * {@code hover}. Returns {@link Preview#empty()} when there's nothing
     * meaningful to draw (single-target attacks, no hover, off-turn).
     */
    public static Preview compute(MinecraftClient client, GridPos hover) {
        if (client == null || client.player == null || hover == null) return Preview.empty();
        GridPos player = ClientGridHelper.getPlayerGridPos(client);
        if (player == null) return Preview.empty();

        ItemStack held = client.player.getMainHandStack();
        ItemStack offhand = client.player.getOffHandStack();
        if (held == null || held.isEmpty()) return Preview.empty();

        Set<GridPos> damage = new LinkedHashSet<>();
        Set<GridPos> effect = new LinkedHashSet<>();

        var item = held.getItem();

        // Instruments: preview the performance shape, faced toward the hovered
        // tile, using the SAME geometry the server resolves and the VFX draws.
        // Attack instruments preview as damage (amber), support as effect (cyan).
        // SCATTER is server-rolled randomly per cast, so it has no meaningful
        // pre-cast preview and is skipped.
        com.crackedgames.craftics.compat.instruments.InstrumentDef instr =
            com.crackedgames.craftics.compat.instruments.InstrumentsCompat.defFor(item);
        if (instr != null) {
            if (instr.shape() == com.crackedgames.craftics.compat.instruments.InstrumentDef.Shape.SCATTER) {
                return Preview.empty();
            }
            List<GridPos> shape =
                (instr.shape() == com.crackedgames.craftics.compat.instruments.InstrumentDef.Shape.FULL_ARENA)
                    ? allArenaTiles()
                    : com.crackedgames.craftics.compat.instruments.InstrumentPerformance.shapeTiles(instr, player, hover, 0L);
            Set<GridPos> dest = instr.role() == com.crackedgames.craftics.compat.instruments.InstrumentDef.Role.ATTACK
                ? damage : effect;
            dest.addAll(shape);
            return finish(damage, effect);
        }

        // Mace: 3x3 slam (damage) + Density pull radius + Wind Burst ring
        // (both effect-only). All combine.
        if (item == Items.MACE) {
            damage.addAll(AoeShapes.slam3x3(hover));
            int density = PlayerCombatStats.getEnchantLevel(held, "minecraft:density");
            if (density > 0) {
                int pullRadius = density <= 1 ? 2 : 3;
                for (int dx = -pullRadius; dx <= pullRadius; dx++) {
                    for (int dz = -pullRadius; dz <= pullRadius; dz++) {
                        effect.add(new GridPos(hover.x() + dx, hover.z() + dz));
                    }
                }
            }
            // Wind Burst knocks back the 3x3 ring (effect, overlaps the slam).
            if (PlayerCombatStats.getEnchantLevel(held, "minecraft:wind_burst") > 0) {
                effect.addAll(AoeShapes.slam3x3(hover));
            }
            return finish(damage, effect);
        }

        // Crossbow: rocket blast, or pierce line + multishot diagonals
        if (item == Items.CROSSBOW) {
            if (offhand != null && offhand.isOf(Items.FIREWORK_ROCKET)) {
                // Rocket: 3x3 blast around the hovered tile.
                damage.addAll(AoeShapes.slam3x3(hover));
            } else {
                // Pierce line through the hovered tile to the arena edge.
                damage.add(hover);
                int pierce = PlayerCombatStats.getEnchantLevel(held, "minecraft:piercing");
                int lineLen = arenaSpan() ; // far enough to reach the edge
                damage.addAll(AoeShapes.lineBehind(player, hover, lineLen));
                // (pierce only changes how many it actually damages, not the line shape)
            }
            if (PlayerCombatStats.getEnchantLevel(held, "minecraft:multishot") > 0) {
                damage.addAll(multishotDiagonals(player, hover));
            }
            return finish(damage, effect);
        }

        // Swords: every geometry enchant COMBINES (server applies them all
        // independently on the same swing). Sweeping Edge sweep + Fire Aspect
        // cone + Knockback line all union together. The direct hit is always
        // the hovered tile.
        if (isSword(item)) {
            damage.add(hover); // direct hit

            int sweep = PlayerCombatStats.getEnchantLevel(held, "minecraft:sweeping_edge");
            if (sweep > 0) {
                damage.addAll(AoeShapes.sweepingEdge(player, hover, sweep));
            }

            // Fire Aspect burn footprint scales with Sweeping Edge level
            // (cone -> wide cone -> ring + outer fire ring). Effect layer
            // since the cone burns but deals no direct damage.
            int fireAspect = PlayerCombatStats.getEnchantLevel(held, "minecraft:fire_aspect");
            if (fireAspect > 0) {
                effect.addAll(AoeShapes.fireAspectShape(player, hover, fireAspect, sweep));
            }

            // Knockback shockwave: line behind the target. Pushes (effect only).
            int knockback = PlayerCombatStats.getEnchantLevel(held, "minecraft:knockback");
            if (knockback > 0) {
                effect.addAll(AoeShapes.lineBehind(player, hover, knockback + 1));
            }

            // Smite is undead-only radiant burst; show its reach as effect-layer
            // (we can't know target type client-side, so always hint it).
            int smite = PlayerCombatStats.getEnchantLevel(held, "minecraft:smite");
            if (smite > 0) {
                int radius = smite <= 2 ? 2 : (smite <= 4 ? 3 : 4);
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) <= radius) {
                            effect.add(new GridPos(hover.x() + dx, hover.z() + dz));
                        }
                    }
                }
            }
            return finish(damage, effect);
        }

        // Bow: Flame (3x3 burn) + Punch (8-ring knockback). Both are
        // effect-only (no direct damage); the direct hit is the hovered tile.
        if (item == Items.BOW) {
            damage.add(hover);
            if (PlayerCombatStats.getEnchantLevel(held, "minecraft:flame") > 0) {
                effect.addAll(AoeShapes.slam3x3(hover));
            }
            if (PlayerCombatStats.getEnchantLevel(held, "minecraft:punch") > 0) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        effect.add(new GridPos(hover.x() + dx, hover.z() + dz));
                    }
                }
            }
            return finish(damage, effect);
        }

        // Live coral fans: per-type shape
        if (item == Items.TUBE_CORAL_FAN) {
            damage.addAll(AoeShapes.plus(hover));
            return finish(damage, effect);
        }
        if (item == Items.BRAIN_CORAL_FAN) {
            damage.addAll(AoeShapes.slam3x3(hover));
            return finish(damage, effect);
        }
        if (item == Items.BUBBLE_CORAL_FAN) {
            damage.addAll(AoeShapes.lineOutward(player, hover, 3));
            return finish(damage, effect);
        }
        if (item == Items.FIRE_CORAL_FAN) {
            damage.addAll(AoeShapes.cone(player, hover, 2));
            return finish(damage, effect);
        }
        if (item == Items.HORN_CORAL_FAN) {
            damage.addAll(AoeShapes.pierceBehind(player, hover));
            return finish(damage, effect);
        }

        // Everything else is single-target, nothing extra to preview beyond
        // the normal hover tile the renderer already draws.
        return Preview.empty();
    }

    /** Two diagonal multishot fan lines from the player past the hovered tile. */
    private static Set<GridPos> multishotDiagonals(GridPos player, GridPos target) {
        Set<GridPos> out = new LinkedHashSet<>();
        int[] dir = AoeShapes.cardinalDir(player, target);
        int dx = dir[0];
        int dz = dir[1];
        int[][] diagonals;
        if (dz == 0) {
            diagonals = new int[][]{{dx, -1}, {dx, 1}};
        } else if (dx == 0) {
            diagonals = new int[][]{{-1, dz}, {1, dz}};
        } else {
            return out;
        }
        int len = arenaSpan();
        for (int[] diag : diagonals) {
            int cx = player.x();
            int cz = player.z();
            for (int i = 0; i < len; i++) {
                cx += diag[0];
                cz += diag[1];
                out.add(new GridPos(cx, cz));
            }
        }
        return out;
    }

    private static int arenaSpan() {
        return Math.max(CombatState.getArenaWidth(), CombatState.getArenaHeight());
    }

    /** Every tile in the arena bounds (FULL_ARENA instrument preview, e.g. Violin). */
    private static List<GridPos> allArenaTiles() {
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        List<GridPos> out = new java.util.ArrayList<>(w * h);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < h; z++)
                if (CombatState.isInPolygon(x, z)) out.add(new GridPos(x, z));
        return out;
    }

    private static boolean isSword(net.minecraft.item.Item item) {
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD
            || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD
            || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
    }

    /** Clip both sets to the arena and remove damage tiles from the effect set
     *  (a tile that takes damage shouldn't also draw the dimmer effect color). */
    private static Preview finish(Set<GridPos> damage, Set<GridPos> effect) {
        Set<GridPos> dmg = clip(damage);
        Set<GridPos> eff = clip(effect);
        eff.removeAll(dmg);
        return new Preview(dmg, eff);
    }

    private static Set<GridPos> clip(Set<GridPos> tiles) {
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        Set<GridPos> out = new LinkedHashSet<>();
        for (GridPos t : tiles) {
            if (t.x() >= 0 && t.x() < w && t.z() >= 0 && t.z() < h
                    && CombatState.isInPolygon(t.x(), t.z())) out.add(t);
        }
        return out;
    }
}
