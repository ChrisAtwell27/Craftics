package com.crackedgames.craftics.world;

/**
 * The NG+ difficulty multiplier, in one place.
 *
 * <p>This formula used to be written out twice - byte-identically - in
 * {@code CrafticsSavedData.PlayerData} and {@code WorldDataComponent}, and neither copy read
 * {@code ngPlusScalingPerCycle}. So the config option existed, advertised a 0.0-1.0 range in
 * the settings UI, and did nothing: the 0.25 default had been copy-pasted into both consumers
 * and the knob left orphaned. Two statements of one fact, and a third that lied.
 *
 * <p>Kept config-null-safe so it stays usable before mod init and in unit tests, matching the
 * guard {@code CombatEffects.maxEffectDuration()} uses for the same reason.
 */
public final class NgPlusScaling {

    private NgPlusScaling() {}

    /** Fallback when the config hasn't loaded yet. Matches the config field's own default. */
    private static final float DEFAULT_PER_CYCLE = 0.25f;

    /** The configured scaling added per NG+ cycle. */
    private static float perCycle() {
        var config = com.crackedgames.craftics.CrafticsMod.CONFIG;
        return config != null ? config.ngPlusScalingPerCycle() : DEFAULT_PER_CYCLE;
    }

    /**
     * Difficulty multiplier for an NG+ level: {@code 1.0 + level * perCycle}. NG+0 is always
     * 1.0, so a fresh run is never scaled regardless of the setting.
     */
    public static float multiplier(int ngPlusLevel) {
        if (ngPlusLevel <= 0) return 1.0f;
        return 1.0f + ngPlusLevel * perCycle();
    }
}
