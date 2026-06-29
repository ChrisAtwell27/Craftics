package com.crackedgames.craftics.world;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossKillsNbtTest {
    @Test
    void incrementAndReadBack() {
        CrafticsSavedData.PlayerData pd = new CrafticsSavedData.PlayerData();
        assertEquals(0, pd.getBossKills("plains"));
        pd.incrementBossKills("plains");
        pd.incrementBossKills("plains");
        pd.incrementBossKills("nether_wastes");
        assertEquals(2, pd.getBossKills("plains"));
        assertEquals(1, pd.getBossKills("nether_wastes"));

        NbtCompound nbt = pd.toNbt();
        CrafticsSavedData.PlayerData restored = CrafticsSavedData.PlayerData.fromNbt(nbt);
        assertEquals(2, restored.getBossKills("plains"));
        assertEquals(1, restored.getBossKills("nether_wastes"));
        assertEquals(0, restored.getBossKills("never_killed"));
    }
}
