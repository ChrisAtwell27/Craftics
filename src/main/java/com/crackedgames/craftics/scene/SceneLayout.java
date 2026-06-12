package com.crackedgames.craftics.scene;

import java.util.List;

/**
 * The parsed result of scanning a scene schematic: the player spawn/vantage
 * point and the booths. Built by {@link SceneLayoutResolver}; consumed by the
 * scene runtime (Unit 3).
 */
public record SceneLayout(int spawnX, int spawnY, int spawnZ, float spawnYaw, List<StandSlot> stands) {

    /** True when the schematic had no spawn marker (an authoring error). */
    public boolean hasSpawn() {
        return !Float.isNaN(spawnYaw);
    }
}
