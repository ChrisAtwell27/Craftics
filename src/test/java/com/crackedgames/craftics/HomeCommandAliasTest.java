package com.crackedgames.craftics;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for which name the go-home shortcut claims.
 *
 * <p>{@code /home} is contested - FTB Essentials, EssentialsX ports and most teleport mods want
 * it - and Brigadier merges same-named literals with the later registration winning, so the
 * rule Craftics needs is "take /home unless somebody else is using it". That rule has more
 * branches than it looks like it does, and every one of them is a case where the wrong answer
 * either steals another mod's command or leaves the player with no way home.
 */
class HomeCommandAliasTest {

    /** Nothing is registered: the field is wide open. */
    private static final Predicate<String> NOTHING_TAKEN = name -> false;

    private static Predicate<String> taken(String... names) {
        Set<String> set = Set.of(names);
        return set::contains;
    }

    // ── The ordinary cases ────────────────────────────────────────────────

    @Test
    void takesThePreferredNameWhenItIsFree() {
        assertEquals("home", CrafticsMod.resolveHomeAlias("home", "island", NOTHING_TAKEN));
    }

    @Test
    void fallsBackWhenAnotherModOwnsThePreferredName() {
        // The whole point: give up /home rather than overwrite the command a player is
        // already using, but still leave them a way home.
        assertEquals("island", CrafticsMod.resolveHomeAlias("home", "island", taken("home")));
    }

    @Test
    void claimsNothingWhenBothNamesAreTaken() {
        // "" is not a failure - /craftics home is namespaced and always works. Claiming a
        // third name nobody configured would be worse than claiming none.
        assertEquals("", CrafticsMod.resolveHomeAlias("home", "island", taken("home", "island")));
    }

    @Test
    void aBlankPreferredNameGoesStraightToTheFallback() {
        assertEquals("island", CrafticsMod.resolveHomeAlias("", "island", NOTHING_TAKEN));
    }

    @Test
    void bothBlankMeansNoShortcutAtAll() {
        // Someone who wants no top-level command must be able to have that.
        assertEquals("", CrafticsMod.resolveHomeAlias("", "", NOTHING_TAKEN));
        assertEquals("", CrafticsMod.resolveHomeAlias(null, null, NOTHING_TAKEN));
    }

    // ── The awkward ones ──────────────────────────────────────────────────

    @Test
    void aFallbackEqualToThePreferredNameIsNotRetried() {
        // Configuring both to the same word is a plausible mistake. Without the guard the
        // taken name is tested twice and refused twice, which reads as the fallback being
        // broken rather than as the config being odd.
        assertEquals("", CrafticsMod.resolveHomeAlias("home", "home", taken("home")));
        // ...and when it is free, it is simply claimed once.
        assertEquals("home", CrafticsMod.resolveHomeAlias("home", "home", NOTHING_TAKEN));
    }

    @Test
    void anUnusableNameIsSkippedRatherThanRegistered() {
        // Brigadier matches literals verbatim, so a name with a space or a slash registers a
        // command nobody can ever type. Falling through to the fallback beats that.
        assertEquals("island", CrafticsMod.resolveHomeAlias("go home", "island", NOTHING_TAKEN));
        assertEquals("island", CrafticsMod.resolveHomeAlias("/home", "island", NOTHING_TAKEN));
        assertEquals("", CrafticsMod.resolveHomeAlias("go home", "also bad", NOTHING_TAKEN));
    }

    @Test
    void namesAreMatchedInLowercase() {
        // Commands are typed lowercase, so "Home" must resolve to the same name - and must
        // collide with a mod that registered "home".
        assertEquals("home", CrafticsMod.resolveHomeAlias("HOME", "island", NOTHING_TAKEN));
        assertEquals("island", CrafticsMod.resolveHomeAlias("Home", "island", taken("home")));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertEquals("home", CrafticsMod.resolveHomeAlias("  home  ", "island", NOTHING_TAKEN));
        assertEquals("", CrafticsMod.resolveHomeAlias("   ", "   ", NOTHING_TAKEN));
    }

    @Test
    void anUnrelatedTakenNameChangesNothing() {
        // Only a collision on a name we actually want matters.
        assertEquals("home", CrafticsMod.resolveHomeAlias("home", "island", taken("spawn", "tpa")));
    }

    // ── Name sanitising ───────────────────────────────────────────────────

    @Test
    void sanitizeAcceptsTheCharactersACommandCanHave() {
        assertEquals("island", CrafticsMod.sanitizeAlias("island"));
        assertEquals("my_home", CrafticsMod.sanitizeAlias("my_home"));
        assertEquals("go-home", CrafticsMod.sanitizeAlias("go-home"));
        assertEquals("home2", CrafticsMod.sanitizeAlias("home2"));
    }

    @Test
    void sanitizeRejectsAnythingUntypeable() {
        assertEquals("", CrafticsMod.sanitizeAlias(null));
        assertEquals("", CrafticsMod.sanitizeAlias(""));
        assertEquals("", CrafticsMod.sanitizeAlias("two words"));
        assertEquals("", CrafticsMod.sanitizeAlias("/slash"));
        assertEquals("", CrafticsMod.sanitizeAlias("bang!"));
    }
}
