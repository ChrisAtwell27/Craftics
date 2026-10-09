package com.crackedgames.craftics.compat.aether.boss;

import com.crackedgames.craftics.api.registry.BossGateRegistry;
import com.crackedgames.craftics.combat.dialogue.BossGateDialogue;
import com.crackedgames.craftics.combat.dialogue.DialogueDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Slider's gate, as far as it can be asked without a server.
 *
 * <p>Whether a real player is carrying a pickaxe needs an inventory, an item registry and the
 * pickaxe tag, none of which exist here: that half is the in-game checklist's. What is pinned
 * is the rule built on top of it, that one pickaxe anywhere in the party is enough and none is
 * a refusal, and the words a refused party reads. Party members are plain strings standing in
 * for players.
 */
class SliderGateTest {

    @AfterEach
    void reset() {
        BossGateRegistry.clear();
    }

    // ── The rule ──

    @Test
    @DisplayName("one pickaxe anywhere in the party lets everyone in")
    void onePickaxeIsEnough() {
        assertTrue(SliderGate.verdict(List.of("alex"), "alex"::equals).isEmpty());
        assertTrue(SliderGate.verdict(List.of("alex", "steve", "kai"), "kai"::equals).isEmpty());
        assertTrue(SliderGate.verdict(List.of("alex", "steve"), member -> true).isEmpty());
    }

    @Test
    @DisplayName("a party with no pickaxe is refused")
    void noPickaxeIsARefusal() {
        assertEquals(SliderGate.lines(1), SliderGate.verdict(List.of("alex"), member -> false));
        assertEquals(SliderGate.lines(3),
            SliderGate.verdict(List.of("alex", "steve", "kai"), member -> false));
    }

    @Test
    @DisplayName("a missing member is skipped, not asked")
    void aMissingMemberIsSkipped() {
        assertEquals(SliderGate.lines(2), SliderGate.verdict(Arrays.asList(null, "alex"), member -> {
            assertNotNull(member);
            return false;
        }));
    }

    // ── The words ──

    @Test
    @DisplayName("it says nobody has a pickaxe, and that nothing else hurts the Slider")
    void itSaysWhatIsWrong() {
        List<String> party = SliderGate.lines(2);
        assertEquals("Nobody in your party is carrying a pickaxe.", party.get(0));
        assertEquals("The Slider cannot be hurt by anything else.", party.get(1));

        // Alone, "your party" is nobody the player recognises.
        List<String> solo = SliderGate.lines(1);
        assertEquals("You are not carrying a pickaxe.", solo.get(0));
        assertEquals(party.subList(1, party.size()), solo.subList(1, solo.size()));
    }

    @Test
    @DisplayName("the whole box is short narrator lines with no dashes")
    void theWholeBoxReadsCleanly() {
        // What the leader actually clicks through: the gate's lines, then the engine's.
        DialogueDefinition box = BossGateDialogue.forDecider(SliderGate.lines(2));
        assertEquals("", box.speaker());
        assertEquals(SliderGate.lines(2).size() + BossGateDialogue.CHOICE_LINES.size(), box.lines().size());
        for (String line : box.lines()) {
            // The box draws one unwrapped line. The dungeon's own boss intro runs to 66.
            assertTrue(line.length() <= 64, line);
            // 0x2014 and 0x2013 are the em and en dash.
            assertTrue(line.chars().noneMatch(c -> c == 0x2014 || c == 0x2013), line);
        }
        assertEquals(List.of("Go home", "Start again from level 1"),
            box.choices().stream().map(choice -> choice.label()).toList());
    }

    // ── The wiring ──

    @Test
    @DisplayName("it is registered against the Bronze Dungeon")
    void itGuardsTheBronzeDungeon() {
        BossGateRegistry.clear();
        SliderGate.register();

        assertTrue(BossGateRegistry.has(SliderAI.BIOME_ID));
        assertTrue(BossGateRegistry.has("aether_bronze_dungeon"));
        assertFalse(BossGateRegistry.has("aether_silver_dungeon"));
    }
}
