package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.TurnOrderProvider;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for turn-order dispatch.
 *
 * <p>The list a provider returns is what the round iterates, so the rules that matter are the
 * ones that keep it a round: every creature acts, none acts twice, and nothing an addon says
 * can add a combatant to the fight or take one out of it. Those are what these pin down.
 *
 * <p>The player argument is null throughout - ordering never dereferences it, it is passed
 * through to the provider - so these run for real rather than around a Minecraft type.
 */
class TurnOrderRegistryTest {

    private static CombatEntity creature(int id, int speed) {
        return new CombatEntity(id, "minecraft:wolf", new GridPos(id, 0), 10, 2, 0, 1, -1, speed);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        TurnOrderRegistry.clear();
    }

    @Test
    void noProviders_leavesTheRoundAlone() {
        // Craftics with no addon must act in spawn order, exactly as it always has.
        assertTrue(TurnOrderRegistry.isEmpty());
        assertNull(TurnOrderRegistry.order(null, List.of(creature(1, 3)), 1));
    }

    @Test
    void aProviderDecidesTheOrder() {
        CombatEntity slow = creature(1, 2);
        CombatEntity fast = creature(2, 6);
        TurnOrderRegistry.register(TurnOrderProvider.byMoveSpeed());

        assertEquals(List.of(fast, slow), TurnOrderRegistry.order(null, List.of(slow, fast), 1));
    }

    @Test
    void byMoveSpeed_keepsTiesInTheOrderTheyHad() {
        CombatEntity first = creature(1, 4);
        CombatEntity second = creature(2, 4);
        TurnOrderRegistry.register(TurnOrderProvider.byMoveSpeed());

        // A coin flip between two equally fast creatures is a design decision, and one a
        // provider can make for itself. The default must not make it for them.
        assertEquals(List.of(first, second), TurnOrderRegistry.order(null, List.of(first, second), 1));
    }

    @Test
    void aCreatureLeftOutStillActs() {
        CombatEntity named = creature(1, 1);
        CombatEntity omitted = creature(2, 9);
        TurnOrderRegistry.register((p, actors, round) -> List.of(named));

        // Dropping it would have the creature silently skip the round, which reads as the
        // fight being stuck rather than as an ordering choice.
        assertEquals(List.of(named, omitted), TurnOrderRegistry.order(null, List.of(named, omitted), 1));
    }

    @Test
    void aCreatureNamedTwiceActsOnce() {
        CombatEntity a = creature(1, 1);
        CombatEntity b = creature(2, 1);
        TurnOrderRegistry.register((p, actors, round) -> List.of(a, a, b, a));

        assertEquals(List.of(a, b), TurnOrderRegistry.order(null, List.of(a, b), 1));
    }

    @Test
    void aStrangerCannotJoinTheRound() {
        CombatEntity inTheFight = creature(1, 1);
        CombatEntity stranger = creature(99, 9);
        TurnOrderRegistry.register((p, actors, round) -> List.of(stranger, inTheFight));

        // Ordering is not a spawn hook. Something that is not acting this round cannot be
        // given a turn by naming it.
        assertEquals(List.of(inTheFight), TurnOrderRegistry.order(null, List.of(inTheFight), 1));
    }

    @Test
    void nullsInTheAnswerAreIgnored() {
        CombatEntity a = creature(1, 1);
        CombatEntity b = creature(2, 1);
        List<CombatEntity> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(b);
        TurnOrderRegistry.register((p, actors, round) -> withNull);

        assertEquals(List.of(b, a), TurnOrderRegistry.order(null, List.of(a, b), 1));
    }

    @Test
    void decliningProvidersFallThroughToTheNext() {
        CombatEntity slow = creature(1, 1);
        CombatEntity fast = creature(2, 8);
        TurnOrderRegistry.register((p, actors, round) -> null);
        TurnOrderRegistry.register((p, actors, round) -> List.of());
        TurnOrderRegistry.register(TurnOrderProvider.byMoveSpeed());

        assertEquals(List.of(fast, slow), TurnOrderRegistry.order(null, List.of(slow, fast), 1));
    }

    @Test
    void aThrowingProviderLosesTheRoundRatherThanBreakingIt() {
        CombatEntity slow = creature(1, 1);
        CombatEntity fast = creature(2, 8);
        TurnOrderRegistry.register((p, actors, round) -> { throw new IllegalStateException("boom"); });
        TurnOrderRegistry.register(TurnOrderProvider.byMoveSpeed());

        assertEquals(List.of(fast, slow), TurnOrderRegistry.order(null, List.of(slow, fast), 1));
    }

    @Test
    void theProviderCannotMutateTheRoundsOwnList() {
        List<CombatEntity> live = new ArrayList<>(List.of(creature(1, 1), creature(2, 2)));
        TurnOrderRegistry.register((p, actors, round) -> {
            assertThrows(UnsupportedOperationException.class, () -> actors.add(creature(3, 3)));
            return null;
        });

        assertNull(TurnOrderRegistry.order(null, live, 1));
        assertEquals(2, live.size());
    }

    @Test
    void register_ignoresNullAndDuplicates() {
        TurnOrderRegistry.register(null);
        assertTrue(TurnOrderRegistry.isEmpty());

        TurnOrderProvider provider = (p, actors, round) -> null;
        TurnOrderRegistry.register(provider);
        TurnOrderRegistry.register(provider);
        TurnOrderRegistry.clear();
        assertTrue(TurnOrderRegistry.isEmpty());
    }
}
