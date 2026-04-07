package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.EventHandler;

public record EventEntry(
    String id,
    String displayName,
    float probability,
    int minBiomeOrdinal,
    boolean isChoiceEvent,
    EventHandler handler
) {}
