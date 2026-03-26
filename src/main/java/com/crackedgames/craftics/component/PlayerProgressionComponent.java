package com.crackedgames.craftics.component;

import com.crackedgames.craftics.combat.PlayerProgression;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.ladysnake.cca.api.v3.entity.RespawnableComponent;

import java.util.EnumMap;

/**
 * CCA component attached to each player. Stores progression data (level, stats).
 * Auto-saves to NBT, auto-copies on respawn.
 */
public class PlayerProgressionComponent implements RespawnableComponent<PlayerProgressionComponent> {

    public int level = 1;
    public int unspentPoints = 0;
    private final EnumMap<PlayerProgression.Stat, Integer> statPoints = new EnumMap<>(PlayerProgression.Stat.class);

    public PlayerProgressionComponent() {
        for (PlayerProgression.Stat s : PlayerProgression.Stat.values()) {
            statPoints.put(s, 0);
        }
    }

    public int getPoints(PlayerProgression.Stat stat) {
        return statPoints.getOrDefault(stat, 0);
    }

    public int getEffective(PlayerProgression.Stat stat) {
        return stat.baseValue + getPoints(stat);
    }

    public boolean allocatePoint(PlayerProgression.Stat stat) {
        if (unspentPoints <= 0) return false;
        statPoints.put(stat, getPoints(stat) + 1);
        unspentPoints--;
        return true;
    }

    public void grantLevelUp() {
        level++;
        unspentPoints++;
    }

    public String serializeStats() {
        StringBuilder sb = new StringBuilder();
        sb.append(level).append(':').append(unspentPoints);
        for (PlayerProgression.Stat s : PlayerProgression.Stat.values()) {
            sb.append(':').append(getPoints(s));
        }
        return sb.toString();
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        level = tag.getInt("level");
        if (level < 1) level = 1;
        unspentPoints = tag.getInt("unspentPoints");
        for (PlayerProgression.Stat s : PlayerProgression.Stat.values()) {
            statPoints.put(s, tag.getInt("stat_" + s.name()));
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt("level", level);
        tag.putInt("unspentPoints", unspentPoints);
        for (PlayerProgression.Stat s : PlayerProgression.Stat.values()) {
            tag.putInt("stat_" + s.name(), getPoints(s));
        }
    }
}
