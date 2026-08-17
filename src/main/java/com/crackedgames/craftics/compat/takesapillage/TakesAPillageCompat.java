package com.crackedgames.craftics.compat.takesapillage;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.AllyEntry;
import com.crackedgames.craftics.api.registry.AllyRegistry;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.PillagerAI;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Items;

/**
 * Compatibility module for the It Takes a Pillage mod.
 *
 * <p>Brings the mod's three new illagers into combat, each with its own tactical identity
 * rather than a reskin of an existing raider:
 * <ul>
 *   <li><b>Archer</b>: fires and kites at range - {@link PillagerAI} verbatim (crossbow
 *       lanes, reposition, retreat-and-fire), pillager stats.</li>
 *   <li><b>Skirmisher</b>: keeps the vindicator's rook charge but walks normally at 3 SPD
 *       between charges ({@link SkirmisherAI}). Trades attack and defense away for it.</li>
 *   <li><b>Legioner</b>: 15 HP shield-wall anchor, slow (1 SPD) but hard to move through.
 *       Carries a flat damage reduction that scales with biome depth and a 25% chance to
 *       turn a ranged hit away entirely - see {@link CombatEntity#getFlatDamageReduction}
 *       and {@link CombatEntity#getRangedBlockChance}, applied at spawn below. Weak to
 *       blunt, resists slashing and ranged (registered in {@code MobResistances}).</li>
 * </ul>
 *
 * <p>The clay golem joins both sides of the line: a buildable ally in the golemoverhaul
 * style (built, clay-ball heal), and an enemy the pillager events can field.
 *
 * <p>AI entries and ally registration go in whether the mod is loaded or not, matching the
 * other compat modules - an entry for an entity that never spawns is free, and datapacks
 * referencing the ids by name get a reliable fallback.
 */
public final class TakesAPillageCompat {

    public static final String MOD_ID = "takesapillage";

    private static boolean loaded = false;
    private static boolean registered = false;

    private TakesAPillageCompat() {}

    // Canonical entity id paths pulled from the mod's lang file.
    public static final String ARCHER     = MOD_ID + ":archer";
    public static final String SKIRMISHER = MOD_ID + ":skirmisher";
    public static final String LEGIONER   = MOD_ID + ":legioner";
    public static final String CLAY_GOLEM = MOD_ID + ":clay_golem";

    // ── Baseline stats ──
    // Archer mirrors the vanilla pillager entry (hp 8, atk 3, range 4).
    public static final int ARCHER_HP = 8, ARCHER_ATK = 3, ARCHER_DEF = 0, ARCHER_RANGE = 4;
    // Skirmisher: vindicator minus a point of attack and defense, plus real mobility.
    // (Vanilla vindicator registers at hp 12, atk 4, def 1.)
    public static final int SKIRMISHER_HP = 12, SKIRMISHER_ATK = 3, SKIRMISHER_DEF = 0, SKIRMISHER_SPEED = 3;
    // Legioner: the numbers the user asked for, unscaled HP by design - the wall is meant
    // to be exactly as tall in biome one as in biome ten; its DR is what grows.
    public static final int LEGIONER_HP = 15, LEGIONER_ATK = 3, LEGIONER_DEF = 3, LEGIONER_SPEED = 1;
    /** Flat damage shaved off every hit the legioner takes, before defense. Scales: see
     *  {@link #applySpawnTraits}. */
    public static final int LEGIONER_BASE_DR = 3;
    /** Chance a ranged hit glances off the legioner's tower shield entirely. */
    public static final double LEGIONER_RANGED_BLOCK = 0.25;

    /**
     * Register AIs and the clay golem ally. Called once from
     * {@code CrafticsMod.onInitialize()} alongside the other compat inits.
     */
    public static void init() {
        if (registered) return;
        registered = true;

        // Archer IS the pillager kit at heart: crossbow lanes, kiting, retreat-and-fire.
        AIRegistry.register(ARCHER, new PillagerAI());
        // Skirmisher: rook charge + normal walking. Own AI class.
        AIRegistry.register(SKIRMISHER, new SkirmisherAI());
        // Legioner: slow melee advance - zombie behaviour reads right for a phalanx
        // (walk at the player, hit what's adjacent). Its identity is its defenses.
        AIRegistry.register(LEGIONER, new com.crackedgames.craftics.combat.ai.ZombieAI());
        // Clay golem the ENEMY: a golem walking down the player.
        AIRegistry.register(CLAY_GOLEM, new com.crackedgames.craftics.combat.ai.ZombieAI());

        // Enemy templates, so events and biome JSON can field these by id with their
        // authored stat lines rather than restating them at every use site.
        com.crackedgames.craftics.api.registry.EnemyRegistry.register(
            com.crackedgames.craftics.api.registry.EnemyEntry.builder(ARCHER, ARCHER)
                .hp(ARCHER_HP).attack(ARCHER_ATK).defense(ARCHER_DEF).range(ARCHER_RANGE)
                .build());
        com.crackedgames.craftics.api.registry.EnemyRegistry.register(
            com.crackedgames.craftics.api.registry.EnemyEntry.builder(SKIRMISHER, SKIRMISHER)
                .hp(SKIRMISHER_HP).attack(SKIRMISHER_ATK).defense(SKIRMISHER_DEF)
                .range(1).speed(SKIRMISHER_SPEED)
                .build());
        com.crackedgames.craftics.api.registry.EnemyRegistry.register(
            com.crackedgames.craftics.api.registry.EnemyEntry.builder(LEGIONER, LEGIONER)
                .hp(LEGIONER_HP).attack(LEGIONER_ATK).defense(LEGIONER_DEF)
                .range(1).speed(LEGIONER_SPEED)
                .build());
        com.crackedgames.craftics.api.registry.EnemyRegistry.register(
            com.crackedgames.craftics.api.registry.EnemyEntry.builder(CLAY_GOLEM, CLAY_GOLEM)
                .hp(16).attack(3).defense(2).range(1).speed(2)
                .build());

        // Clay golem the ALLY: buildable like the golemoverhaul set, healed with clay.
        // Stat block sits between the coal golem (8/3/0) and terracotta tank (18/3/4).
        AllyRegistry.register(AllyEntry.builder(CLAY_GOLEM)
            .hp(14).attack(3).defense(2).speed(2).range(1)
            .recruitMode(AllyEntry.RecruitMode.BUILT)
            .scalesWithOwnerGear(true)
            .healItem(Items.CLAY_BALL, 4)
            .build());

        // Event intro dialogues, on the raid's accept/decline vote rails (the choice
        // actions are the raid's own - one vote machine, see offerAcceptDeclineFight).
        com.crackedgames.craftics.combat.dialogue.DialogueRegistry.register(
            new com.crackedgames.craftics.combat.dialogue.DialogueDefinition(
                "craftics:pillager_camp_intro", "minecraft:pillager", "pillager_camp_intro",
                java.util.List.of(
                    "Smoke through the trees. Banners on sharpened stakes.",
                    "A pillager camp - and among the crossbows, soldiers you haven't fought before.",
                    "Something in that camp marks the road to their stronghold."),
                java.util.List.of(
                    new com.crackedgames.craftics.combat.dialogue.DialogueChoice(
                        "Raid the camp", "raid:accept"),
                    new com.crackedgames.craftics.combat.dialogue.DialogueChoice(
                        "Slip past", "raid:decline"))));
        com.crackedgames.craftics.combat.dialogue.DialogueRegistry.register(
            new com.crackedgames.craftics.combat.dialogue.DialogueDefinition(
                "craftics:bastille_intro", "minecraft:pillager", "bastille_intro",
                java.util.List.of(
                    "The map ends at a stone bastille, gates barred, walls manned.",
                    "Three garrisons stand between you and its vault.",
                    "Once the first horn sounds, there is no walking away."),
                java.util.List.of(
                    new com.crackedgames.craftics.combat.dialogue.DialogueChoice(
                        "Storm the Bastille", "raid:accept"),
                    new com.crackedgames.craftics.combat.dialogue.DialogueChoice(
                        "Turn back", "raid:decline"))));

        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            CrafticsMod.LOGGER.debug(
                "[Craftics × It Takes a Pillage] mod not loaded - entries registered for future use");
            return;
        }
        loaded = true;
        CrafticsMod.LOGGER.info(
            "[Craftics × It Takes a Pillage] enabled - archer, skirmisher, legioner, clay golem");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Per-spawn traits that live outside the stat block. Called from CombatManager's spawn
     * loop right after the {@link CombatEntity} is built, for every enemy, any biome - it
     * no-ops for everything that isn't ours.
     *
     * <p>The legioner's flat damage reduction scales with biome depth on the same curve
     * enemy defense does ({@code defPerBiome}), so the shield keeps mattering as player
     * damage grows while its unscaled 15 HP stays a fixed-size wall.
     */
    public static void applySpawnTraits(CombatEntity ce, int biomeOrdinal) {
        if (ce == null || !LEGIONER.equals(ce.getEntityTypeId())) return;
        int drScale = Math.max(0, biomeOrdinal)
            / Math.max(1, CrafticsMod.CONFIG.defPerBiome());
        ce.setFlatDamageReduction(LEGIONER_BASE_DR + drScale);
        ce.setRangedBlockChance(LEGIONER_RANGED_BLOCK);
    }
}
