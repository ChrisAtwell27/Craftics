package com.crackedgames.craftics.component;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.ladysnake.cca.api.v3.component.Component;

/**
 * CCA component attached to the overworld. Stores global game data.
 * Mirrors all fields from CrafticsSavedData for future migration.
 */
public class WorldDataComponent implements Component {

    public boolean hubBuilt = false;
    public int hubVersion = 0;
    public int highestBiomeUnlocked = 1;
    public int emeralds = 0;
    public String activeBiomeId = "";
    public int activeBiomeLevelIndex = 0;
    public int branchChoice = -1;
    public String discoveredBiomes = "";
    public int ngPlusLevel = 0;

    // --- Convenience methods (same API as CrafticsSavedData) ---

    public boolean isInBiomeRun() {
        return activeBiomeId != null && !activeBiomeId.isEmpty();
    }

    public void startBiomeRun(String biomeId) {
        this.activeBiomeId = biomeId;
        this.activeBiomeLevelIndex = 0;
    }

    public void advanceBiomeRun() {
        this.activeBiomeLevelIndex++;
    }

    public void endBiomeRun() {
        this.activeBiomeId = "";
        this.activeBiomeLevelIndex = 0;
    }

    public void addEmeralds(int amount) {
        this.emeralds += amount;
    }

    public boolean spendEmeralds(int amount) {
        if (this.emeralds >= amount) {
            this.emeralds -= amount;
            return true;
        }
        return false;
    }

    public boolean isBiomeDiscovered(String biomeId) {
        if (discoveredBiomes.isEmpty()) return false;
        for (String discovered : discoveredBiomes.split(",")) {
            if (discovered.equals(biomeId)) return true;
        }
        return false;
    }

    public void discoverBiome(String biomeId) {
        if (!isBiomeDiscovered(biomeId)) {
            discoveredBiomes = discoveredBiomes.isEmpty() ? biomeId : discoveredBiomes + "," + biomeId;
        }
    }

    public void initBranchIfNeeded() {
        if (branchChoice < 0) {
            branchChoice = new java.util.Random().nextInt(2);
        }
    }

    public java.util.List<String> getPath() {
        initBranchIfNeeded();
        return com.crackedgames.craftics.level.BiomePath.getPath(branchChoice);
    }

    public void startNewGamePlus() {
        ngPlusLevel++;
        highestBiomeUnlocked = 1;
        discoveredBiomes = "";
        activeBiomeId = "";
        activeBiomeLevelIndex = 0;
        branchChoice = new java.util.Random().nextInt(2);
    }

    public float getNgPlusMultiplier() {
        return 1.0f + ngPlusLevel * 0.25f;
    }

    // --- CCA serialization ---

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        hubBuilt = tag.getBoolean("hubBuilt");
        hubVersion = tag.getInt("hubVersion");
        highestBiomeUnlocked = tag.getInt("highestBiomeUnlocked");
        if (highestBiomeUnlocked < 1) highestBiomeUnlocked = 1;
        emeralds = tag.getInt("emeralds");
        activeBiomeId = tag.getString("activeBiomeId");
        if (activeBiomeId == null) activeBiomeId = "";
        activeBiomeLevelIndex = tag.getInt("activeBiomeLevelIndex");
        branchChoice = tag.getInt("branchChoice");
        if (branchChoice == 0 && !tag.contains("branchChoice")) branchChoice = -1;
        discoveredBiomes = tag.getString("discoveredBiomes");
        if (discoveredBiomes == null) discoveredBiomes = "";
        ngPlusLevel = tag.getInt("ngPlusLevel");
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean("hubBuilt", hubBuilt);
        tag.putInt("hubVersion", hubVersion);
        tag.putInt("highestBiomeUnlocked", highestBiomeUnlocked);
        tag.putInt("emeralds", emeralds);
        tag.putString("activeBiomeId", activeBiomeId);
        tag.putInt("activeBiomeLevelIndex", activeBiomeLevelIndex);
        tag.putInt("branchChoice", branchChoice);
        tag.putString("discoveredBiomes", discoveredBiomes);
        tag.putInt("ngPlusLevel", ngPlusLevel);
    }
}
