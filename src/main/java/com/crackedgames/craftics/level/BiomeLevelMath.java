package com.crackedgames.craftics.level;

/**
 * The pure arithmetic of "where am I in a biome" - extracted from {@link BiomeTemplate} so it
 * can be unit-tested without a Minecraft bootstrap (BiomeTemplate itself holds Block[]/Item[]
 * fields and cannot be constructed in a test).
 *
 * <p>This exists because of a real bug. Players reported skipping levels: one went Deep Dark
 * level 3 -> 6 and landed in "Nether Wastes I"; another skipped to Stony Peaks "right before
 * the boss level". Deep Dark starts at global level 41 with 5 levels, so its boss is index 4
 * (global 45) and Nether Wastes starts at 46 - an index of 6 gives 41+6 = 47, inside the next
 * biome, with the boss jumped clean over.
 *
 * <p>The level counter could be advanced more than once for a single victory, and nothing
 * bounded the result: the boss test was exact equality, so an index that overshot the boss
 * reported "not a boss" and kept indexing off the OLD biome's startLevel into the next
 * biome's range - silently skipping the boss fight, the biome unlock and the NG+ check.
 * Clamping here means an overshoot degrades to "you fight the boss", never "you teleport into
 * a later biome".
 */
public final class BiomeLevelMath {

    private BiomeLevelMath() {}

    /**
     * The index of {@code globalLevel} within a biome, clamped to the biome's own range.
     *
     * <p>Clamping rather than returning raw arithmetic is the fix: a caller that overshoots
     * gets the last level of THIS biome, not a phantom index that resolves into the next one.
     */
    public static int biomeLevelIndex(int globalLevel, int startLevel, int levelCount) {
        int raw = globalLevel - startLevel;
        if (raw < 0) return 0;
        int last = Math.max(0, levelCount - 1);
        return Math.min(raw, last);
    }

    /**
     * Whether {@code globalLevel} is this biome's boss level - true at the boss AND anywhere
     * past it, so a run that overshoots still has to clear the boss before it can leave.
     */
    public static boolean isBossLevel(int globalLevel, int startLevel, int levelCount) {
        return biomeLevelIndex(globalLevel, startLevel, levelCount) >= Math.max(0, levelCount - 1);
    }
}
