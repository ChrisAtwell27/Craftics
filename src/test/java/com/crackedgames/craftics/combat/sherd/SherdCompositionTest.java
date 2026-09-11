package com.crackedgames.craftics.combat.sherd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The composition rules that let one sherd be turned into a different sherd.
 *
 * <p>These are the guarantees the Scribe leans on. An inscription is applied blind - it does
 * not know which sherd it landed on - so the transformations have to behave sanely against
 * spells they were never written for: a range bonus on a self-cast, a chain bonus on something
 * that never chained, a cost reduction applied three times.
 *
 * <p>Scope note: the twenty-three definitions in {@link SherdRegistry} are keyed by
 * {@code Items} constants, which need a Minecraft bootstrap this suite does not have, so their
 * numbers cannot be pinned here. They are verified against the pre-refactor values by hand -
 * see the in-game checklist in the pull request - and everything testable without a registry
 * is pinned below.
 */
class SherdCompositionTest {

    /** A stand-in spell. The item is only ever stored, never dereferenced. */
    private static SherdSpell.Builder targeted() {
        return SherdSpell.of(null, "Test Spell").ap(4).range(3)
            .step(SpellStep.of(Selector.enemy()).effect(Effects.damage(10)));
    }

    private static SherdSpell.Builder selfCast() {
        return SherdSpell.of(null, "Test Buff").ap(4).selfCast()
            .step(SpellStep.of(Selector.self()).effect(Effects.healCaster(5)));
    }

    // ── Cost ────────────────────────────────────────────────────────────

    @Test
    void costReductionsStackButNeverReachZero() {
        SherdSpell spell = targeted().adjustAp(-2).adjustAp(-2).adjustAp(-2).build();
        assertEquals(1, spell.apCost(),
            "a free cast would make an inscribed sherd strictly better than not acting");
    }

    @Test
    void costIncreasesApply() {
        assertEquals(6, targeted().adjustAp(2).build().apCost());
    }

    // ── Range ───────────────────────────────────────────────────────────

    @Test
    void rangeBonusExtendsATargetedSpell() {
        assertEquals(5, targeted().adjustRange(2).build().range());
    }

    @Test
    void rangeBonusLeavesASelfCastAlone() {
        SherdSpell spell = selfCast().adjustRange(3).build();
        assertEquals(0, spell.range(), "a self-cast has no target tile to reach further toward");
        assertTrue(spell.isSelfCast(), "a range bonus must not turn a self-cast into a targeted spell");
    }

    // ── Selector transformations ────────────────────────────────────────

    @Test
    void extraRadiusWidensAnArea() {
        Selector base = Selector.enemiesAround(1).build();
        assertEquals(3, base.withExtraRadius(2).radius());
    }

    @Test
    void extraRadiusRefusesToWidenATileTarget() {
        // Widening "the tile you aimed at" is meaningless, and silently turning a trap
        // placement into an area would be a surprise rather than an upgrade.
        Selector tile = Selector.tile().build();
        assertSame(tile, tile.withExtraRadius(2));
    }

    @Test
    void extraChainStartsAChainOnSomethingThatNeverChained() {
        Selector base = Selector.enemy().build();
        assertNull(base.chain(), "precondition: a plain single-target selector does not chain");
        Selector arced = base.withExtraChain(2, 2);
        assertNotNull(arced.chain());
        assertEquals(2, arced.chain().maxHops());
        assertEquals(2, arced.chain().hopRange());
    }

    @Test
    void extraChainLengthensAnExistingChainWithoutRetuningItsReach() {
        Selector base = Selector.enemy().chain(3, 2, 1).build();
        Selector arced = base.withExtraChain(2, 9);
        assertEquals(5, arced.chain().maxHops(), "hops add");
        assertEquals(2, arced.chain().hopRange(),
            "an inscription that adds links must not silently re-tune how far each link reaches");
        assertEquals(1, arced.chain().decayPerHop(), "falloff is the spell's own, not the inscription's");
    }

    @Test
    void unlimitedChainsStayUnlimited() {
        Selector base = Selector.enemy().chainUnlimited(2, 1).build();
        assertEquals(Integer.MAX_VALUE, base.withExtraChain(3, 2).chain().maxHops(),
            "adding to an unbounded chain must not overflow it into a small number");
    }

    // ── Step augmentation ───────────────────────────────────────────────

    @Test
    void augmentingAddsToEveryExistingStepAndKeepsWhatWasThere() {
        SherdSpell spell = SherdSpell.of(null, "Two Step").ap(3).range(2)
            .step(SpellStep.of(Selector.enemy()).effect(Effects.damage(10)))
            .step(SpellStep.of(Selector.enemiesNear(1)).effect(Effects.damage(5)))
            .augmentAllSteps(Effects.stun())
            .build();

        assertEquals(2, spell.steps().size(), "augmenting must not add or drop steps");
        for (SpellStep step : spell.steps()) {
            assertEquals(2, step.effects().size(),
                "each step keeps its own effect and gains the new one");
        }
    }

    @Test
    void augmentingPreservesSelectorConfiguration() {
        SherdSpell spell = SherdSpell.of(null, "Chained").ap(4).range(3)
            .step(SpellStep.of(Selector.enemy().chain(2, 2, 1)).effect(Effects.damage(8)))
            .augmentAllSteps(Effects.stun())
            .build();
        Selector.Chain chain = spell.steps().get(0).selector().chain();
        assertNotNull(chain, "rebuilding a step must carry its selector forward intact");
        assertEquals(2, chain.maxHops());
        assertEquals(1, chain.decayPerHop());
    }

    // ── Derivation ──────────────────────────────────────────────────────

    @Test
    void toBuilderProducesAnIndependentCopy() {
        SherdSpell base = targeted().build();
        SherdSpell derived = base.toBuilder().adjustAp(-1).adjustRange(1).build();

        assertEquals(4, base.apCost(), "deriving must not mutate the shared base definition");
        assertEquals(3, base.range());
        assertEquals(3, derived.apCost());
        assertEquals(4, derived.range());
    }

    // ── Inscriptions ────────────────────────────────────────────────────

    @Test
    void everyInscriptionAppliesWithoutTouchingAnUnrelatedSpell() {
        // An inscription is applied blind, so each must survive a spell it was not written for.
        for (SherdInscription inscription : SherdInscription.values()) {
            SherdSpell.Builder builder = targeted();
            assertDoesNotThrow(() -> inscription.applyTo(builder, inscription.defaultMagnitude()),
                inscription + " must apply to an arbitrary spell");
            assertNotNull(builder.build(), inscription + " must leave a buildable spell");
        }
    }

    @Test
    void everyInscriptionDescribesItselfWithItsMagnitude() {
        for (SherdInscription inscription : SherdInscription.values()) {
            String text = inscription.describe(inscription.defaultMagnitude());
            assertNotNull(text);
            assertFalse(text.isBlank(), inscription + " needs a tooltip line");
            assertTrue(text.contains(inscription.displayName()),
                inscription + " should name itself in its description");
        }
    }

    @Test
    void enduringMakesASherdGenuinelyUnbreakable() {
        SherdSpell.Builder builder = targeted();
        SherdInscription.ENDURING.applyTo(builder, 1);
        assertEquals(0, builder.build().breakPercent(),
            "0 is what the break roll short-circuits on; anything else is merely unlikely");
    }

    @Test
    void reapingExcludesBosses() {
        // Death Mark's own execute does not exclude bosses, but an inscription can land on any
        // sherd and stack with anything, so it is far easier to arrive at by accident.
        SherdSpell.Builder builder = targeted();
        SherdInscription.REAPING.applyTo(builder, 25);
        SherdSpell spell = builder.build();
        assertEquals(2, spell.steps().size(), "reaping adds its own step rather than editing one");
    }

    @Test
    void anUnknownInscriptionNameResolvesToNullRatherThanThrowing() {
        // Saved sherds outlive the enum. A removed inscription should cost that one line, not
        // make the whole sherd unresolvable.
        assertNull(SherdInscription.byName("NO_SUCH_INSCRIPTION"));
        assertNull(SherdInscription.byName(null));
    }

    @Test
    void inscriptionNamesRoundTripCaseInsensitively() {
        for (SherdInscription inscription : SherdInscription.values()) {
            assertSame(inscription, SherdInscription.byName(inscription.name()));
            assertSame(inscription, SherdInscription.byName(inscription.name().toLowerCase()));
        }
    }
}
