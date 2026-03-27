package com.crackedgames.craftics.combat;

/**
 * Between-level event types with their base probabilities and constraints.
 */
public enum EventType {
    OMINOUS_TRIAL(0.05f, 10, true),
    TRIAL_CHAMBER(0.10f, 0, true),
    AMBUSH(0.10f, 0, false),
    SHRINE(0.07f, 0, false),
    TRAVELER(0.06f, 0, false),
    TREASURE_VAULT(0.04f, 0, true),
    DIG_SITE(0.06f, 0, true),
    TRADER(0.0f, 0, true),  // probability set from config at runtime
    NONE(0.0f, 0, false);   // remainder after other events

    public final float baseProbability;
    public final int minBiomeOrdinal;
    /** True if players must vote accept/decline before the event starts. */
    public final boolean isChoiceEvent;

    EventType(float baseProbability, int minBiomeOrdinal, boolean isChoiceEvent) {
        this.baseProbability = baseProbability;
        this.minBiomeOrdinal = minBiomeOrdinal;
        this.isChoiceEvent = isChoiceEvent;
    }
}
