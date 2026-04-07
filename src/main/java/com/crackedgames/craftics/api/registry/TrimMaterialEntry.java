package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.TrimEffects;

public record TrimMaterialEntry(
    String materialId,
    TrimEffects.Bonus stat,
    int valuePerPiece,
    String description
) {}
