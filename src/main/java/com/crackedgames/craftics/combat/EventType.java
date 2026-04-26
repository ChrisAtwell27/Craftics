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
    CRAFTING_STATION(0.05f, 0, false),
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

    public static EventType fromId(String id) {
        return switch (id) {
            case "craftics:ominous_trial" -> OMINOUS_TRIAL;
            case "craftics:trial_chamber" -> TRIAL_CHAMBER;
            case "craftics:ambush" -> AMBUSH;
            case "craftics:shrine" -> SHRINE;
            case "craftics:traveler" -> TRAVELER;
            case "craftics:treasure_vault" -> TREASURE_VAULT;
            case "craftics:dig_site" -> DIG_SITE;
            case "craftics:trader" -> TRADER;
            case "craftics:crafting_station" -> CRAFTING_STATION;
            default -> NONE;
        };
    }

    public String toId() {
        return switch (this) {
            case OMINOUS_TRIAL -> "craftics:ominous_trial";
            case TRIAL_CHAMBER -> "craftics:trial_chamber";
            case AMBUSH -> "craftics:ambush";
            case SHRINE -> "craftics:shrine";
            case TRAVELER -> "craftics:traveler";
            case TREASURE_VAULT -> "craftics:treasure_vault";
            case DIG_SITE -> "craftics:dig_site";
            case TRADER -> "craftics:trader";
            case CRAFTING_STATION -> "craftics:crafting_station";
            case NONE -> "none";
        };
    }
}
