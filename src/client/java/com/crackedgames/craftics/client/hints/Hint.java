package com.crackedgames.craftics.client.hints;

import java.util.function.Predicate;

public record Hint(
        String id,
        Predicate<HintContext> sensor,
        int idleSeconds,
        HintPresenter presenter,
        Priority priority,
        Mode mode
) {
    public enum Priority { LOW, MEDIUM, HIGH }
    public enum Mode { RECURRING, ONE_SHOT_PERSISTENT }
}
