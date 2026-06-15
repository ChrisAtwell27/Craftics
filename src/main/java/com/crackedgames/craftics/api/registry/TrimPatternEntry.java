package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.TrimEffects;

/**
 * Immutable definition of an armor trim pattern's Craftics combat bonuses.
 *
 * <p>Each pattern grants a per-piece stat bonus (applied once for each armor piece
 * carrying that trim, up to four pieces) and a four-piece set bonus that activates when
 * all four worn pieces share the same pattern.
 *
 * <p>Built-in patterns (sentry, dune, coast, wild, ward, eye, ...) are registered by
 * {@code VanillaContent}; addons register their own through
 * {@code CrafticsAPI.registerTrimPattern}:
 *
 * <pre>{@code
 * CrafticsAPI.registerTrimPattern(new TrimPatternEntry(
 *     "mymod:rune",
 *     TrimEffects.Bonus.ARMOR_PEN, "+1 Armor Penetration per piece",
 *     TrimEffects.SetBonus.ETHEREAL, "Ethereal", "20% chance to dodge incoming attacks"
 * ));
 * }</pre>
 *
 * @param patternId          the trim pattern id as it appears in item NBT
 * @param perPieceStat       the stat incremented once per armor piece carrying this trim
 * @param perPieceDescription short text shown to players describing the per-piece bonus
 * @param setBonus           the four-piece set bonus mechanic
 * @param setBonusName       display name for the set bonus
 * @param setBonusDescription one-line mechanic description for the set bonus
 * @since 0.2.0
 */
public record TrimPatternEntry(
    String patternId,
    TrimEffects.Bonus perPieceStat,
    String perPieceDescription,
    TrimEffects.SetBonus setBonus,
    String setBonusName,
    String setBonusDescription
) {}
