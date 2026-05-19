package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.TrimEffects;

/**
 * Immutable definition of an armor trim material's Craftics combat bonus.
 *
 * <p>A trim material adds a flat stat bonus per armor piece that carries any trim
 * made from that material. The bonus stacks with the trim pattern's per-piece stat
 * independently, so a piece with both a pattern and a material contributes both bonuses.
 *
 * <p>Built-in materials (iron, copper, gold, lapis, emerald, diamond, netherite,
 * redstone, amethyst, quartz, resin) are registered by {@code VanillaContent}; addons
 * register their own through {@code CrafticsAPI.registerTrimMaterial}:
 *
 * <pre>{@code
 * CrafticsAPI.registerTrimMaterial(new TrimMaterialEntry(
 *     "mymod:starstone", TrimEffects.Bonus.LUCK, 2, "+2 Luck per piece"
 * ));
 * }</pre>
 *
 * @param materialId     the trim material id as it appears in item NBT
 * @param stat           the stat incremented per armor piece carrying a trim of this material
 * @param valuePerPiece  how much {@code stat} is increased per piece
 * @param description    short text shown to players describing the bonus
 * @since 0.2.0
 */
public record TrimMaterialEntry(
    String materialId,
    TrimEffects.Bonus stat,
    int valuePerPiece,
    String description
) {}
