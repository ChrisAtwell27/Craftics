package com.crackedgames.craftics.combat.dialogue;

import com.crackedgames.craftics.api.RegistrationSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Registry of {@link DialogueDefinition}s keyed by id, mirroring {@code AllyRegistry}. */
public final class DialogueRegistry {

    private static final Map<String, DialogueDefinition> REGISTRY = new ConcurrentHashMap<>();
    private static final Set<String> DATAPACK_KEYS = ConcurrentHashMap.newKeySet();

    private DialogueRegistry() {}

    /** Register a dialogue from code (survives /reload). */
    public static void register(DialogueDefinition d) { register(d, RegistrationSource.CODE); }

    /** Register a dialogue, tagging whether it came from code or a datapack. */
    public static void register(DialogueDefinition d, RegistrationSource source) {
        REGISTRY.put(d.id(), d);
        if (source == RegistrationSource.DATAPACK) DATAPACK_KEYS.add(d.id());
        else DATAPACK_KEYS.remove(d.id());
    }

    public static void clearDatapackEntries() {
        for (String id : DATAPACK_KEYS) REGISTRY.remove(id);
        DATAPACK_KEYS.clear();
    }

    /** The dialogue with this id, or {@code null} if none is registered. */
    @Nullable
    public static DialogueDefinition get(String id) { return REGISTRY.get(id); }

    /** Return a random definition whose group equals {@code group}, or null if none. */
    @Nullable
    public static DialogueDefinition pickFromGroup(String group, Random random) {
        if (group == null || group.isEmpty()) return null;
        List<DialogueDefinition> matches = new ArrayList<>();
        for (DialogueDefinition d : REGISTRY.values()) {
            if (group.equals(d.group())) matches.add(d);
        }
        if (matches.isEmpty()) return null;
        return matches.get(random.nextInt(matches.size()));
    }

    /** Test-only: wipe everything (code + datapack). */
    public static void clearAllForTest() { REGISTRY.clear(); DATAPACK_KEYS.clear(); }
}
