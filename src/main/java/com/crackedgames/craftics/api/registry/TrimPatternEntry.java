package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.TrimEffects;

public record TrimPatternEntry(
    String patternId,
    TrimEffects.Bonus perPieceStat,
    String perPieceDescription,
    TrimEffects.SetBonus setBonus,
    String setBonusName,
    String setBonusDescription
) {}
