package com.crackedgames.craftics.api;

import com.crackedgames.craftics.api.registry.*;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.level.BiomeRegistry;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.campaign.Campaign;
import com.crackedgames.craftics.level.campaign.CampaignManager;
import net.minecraft.item.Item;

/**
 * Public API for Craftics modding.
 *
 * Other Fabric mods can use this class to:
 * - Register custom biomes (programmatically or via JSON datapacks)
 * - Register custom AI strategies for their mob types
 * - Register custom weapons with damage types, abilities, and stats
 * - Register equipment scanners for addon inventory slots (trinkets, baubles, etc.)
 * - Register custom armor set bonuses
 * - Register custom trim pattern and material effects
 * - Register custom between-level events
 * - Register enchantment stat bonuses
 *
 * <h2>Datapack Modding (no code required)</h2>
 * Place JSON files in: {@code data/<your_mod_id>/craftics/biomes/your_biome.json}
 * <p>
 * See the built-in biomes in {@code data/craftics/craftics/biomes/} for examples.
 *
 * <h2>Code Modding (Fabric mod)</h2>
 * <pre>{@code
 * // In your mod initializer:
 * CrafticsAPI.registerAI("mymod:custom_mob", new MyCustomAI());
 * CrafticsAPI.registerBiome(myBiomeTemplate);
 * CrafticsAPI.registerWeapon(myItem, WeaponEntry.builder(myItem)
 *     .damageType(DamageType.SLASHING).attackPower(8).apCost(1).range(1)
 *     .ability(Abilities.sweepAdjacent(0.1, 0.05).and(Abilities.stun(0.05, 0.03)))
 *     .build());
 * }</pre>
 *
 * @since 0.2.0
 */
public final class CrafticsAPI {

    private CrafticsAPI() {} // no instances

    // === Existing: AI ===

    /**
     * Register a custom AI strategy for a mob type.
     * Call this during mod initialization.
     *
     * @param entityTypeId Full entity type ID (e.g., "mymod:custom_zombie")
     * @param ai           The AI strategy to use for this mob type
     */
    public static void registerAI(String entityTypeId, EnemyAI ai) {
        AIRegistry.register(entityTypeId, ai);
    }

    /**
     * Register the attack animation style for a mob type, so a custom mob
     * lunges, pounces, slams, dashes, bounces, blinks, rams, jabs or channels
     * when it attacks instead of using the generic lunge. Call during mod
     * initialization; last registration for a type wins. Ranged attacks always
     * play the draw-and-release lean unless the style is
     * {@link com.crackedgames.craftics.combat.animation.MobAttackAnimations.Style#CAST}.
     *
     * @param entityTypeId Full entity type ID (e.g., "mymod:lava_crab")
     * @param style        The attack-animation archetype
     * @since 0.2.5
     */
    public static void registerAttackAnimation(
            String entityTypeId,
            com.crackedgames.craftics.combat.animation.MobAttackAnimations.Style style) {
        com.crackedgames.craftics.combat.animation.MobAttackAnimations.register(entityTypeId, style);
    }

    // === Existing: Biomes ===

    /**
     * Register a custom biome template programmatically.
     * The biome will be inserted into the progression based on its order value.
     *
     * @param template The biome template to register
     */
    public static void registerBiome(BiomeTemplate template) {
        BiomeRegistry.register(template);
    }

    // === New: Weapons ===

    /**
     * Register a weapon with its Craftics combat stats and optional ability.
     * Use {@link WeaponEntry#builder(Item)} for a fluent API.
     *
     * @param item  The weapon item
     * @param entry The weapon's combat data
     */
    public static void registerWeapon(Item item, WeaponEntry entry) {
        WeaponRegistry.register(item, entry);
    }

    // === Usable Items ===

    /**
     * Register an item the player can use during a Craftics turn - a consumable,
     * throwable, or special-effect item. Registered items are checked before
     * Craftics' built-in item handling, so an addon can add new usable items (or
     * override a vanilla one) without touching combat code.
     * Use {@link UsableItemEntry#builder(Item)} for a fluent API.
     *
     * @param item  the usable item
     * @param entry the item's combat data and effect handler
     */
    public static void registerUsableItem(Item item, UsableItemEntry entry) {
        UsableItemRegistry.register(item, entry);
    }

    // === Enemies ===

    /**
     * Register a reusable enemy template. Biome JSON can then reference it by id
     * with {@code "enemy": "<id>"} instead of redefining the enemy's stats inline.
     * Use {@link EnemyEntry#builder(String, String)} for a fluent API.
     *
     * @param entry the enemy template (stats, appearance, and AI)
     */
    public static void registerEnemy(EnemyEntry entry) {
        EnemyRegistry.register(entry);
    }

    // === Allies ===

    /**
     * Register a hook that runs on a freshly spawned arena mob, enemy or ally.
     *
     * <p>Craftics creates every combatant by looking its entity type up and creating it
     * bare. That is enough when the entity type IS the identity, and not enough for a mod
     * that ships one entity type for many creatures and stores which one elsewhere - every
     * one of them would spawn blank and identical.
     *
     * <p>{@code key} is matched against the combatant's {@code aiKey} first and its entity
     * type id second. That order is what makes the one-type-many-creatures case work: give
     * each creature its own {@code aiKey} and each gets its own initialisation while they
     * all share a type.
     *
     * <p>For anything expressible as entity NBT, prefer {@code spawnNbt} on the enemy or
     * ally entry instead - a datapack can author that with no Java at all. Use this for
     * what NBT cannot say. Both run when both are present, NBT first.
     *
     * @see com.crackedgames.craftics.api.SpawnCustomizer
     * @since 0.3.9
     */
    public static void registerSpawnCustomizer(String key, SpawnCustomizer customizer) {
        com.crackedgames.craftics.api.registry.SpawnCustomizerRegistry.register(key, customizer);
    }

    /**
     * Register a combat ally - a mob recruited from the player's hub that fights
     * alongside them. Use {@link AllyEntry#builder(String)} for a fluent API.
     *
     * @param entry the ally definition (stats, recruitment mode, and AI)
     */
    public static void registerAlly(AllyEntry entry) {
        AllyRegistry.register(entry);
    }

    /**
     * Give an enemy a bench: creatures it carries into the fight on no tile, fielded only when
     * it switches one in.
     *
     * <p>A trainer with a team is not a boss mechanic. A route trainer with three creatures is
     * the ordinary case and a gym leader is the same thing with better ones, so this is
     * available to any enemy - set it from a spawn customizer, from a boss AI's setup, or from
     * whatever built the fight.
     *
     * <p>The bench lives on the trainer and dies with it. A defeated trainer's remaining team
     * leaves with it, which is both the rule a real battle uses and the one that needs no
     * cleanup pass to enforce.
     *
     * @see com.crackedgames.craftics.api.EnemyBench
     * @since 0.4.0
     */
    public static void setEnemyBench(com.crackedgames.craftics.combat.CombatEntity trainer,
                                     java.util.List<com.crackedgames.craftics.api.EnemyBench> bench) {
        if (trainer == null) return;
        trainer.getBench().clear();
        if (bench != null) {
            for (var b : bench) {
                if (b != null) trainer.getBench().add(b);
            }
        }
    }

    /** Read an enemy's bench. Empty when it has none. */
    public static java.util.List<com.crackedgames.craftics.api.EnemyBench> enemyBench(
            com.crackedgames.craftics.combat.CombatEntity trainer) {
        return trainer == null ? java.util.List.of()
            : java.util.List.copyOf(trainer.getBench());
    }

    /**
     * Switch a trainer's benched creature in for one of its creatures on the field.
     *
     * <p>Typically called from the trainer's own AI, or from a {@code CustomAction} handler so
     * the switch costs the trainer its turn the way a real one does.
     *
     * <p>The outgoing creature returns to the bench <b>carrying its damage and status</b>: it
     * is captured from its live state, not rebuilt from its definition. A switch that healed
     * would make rotating a team strictly better than fighting with it.
     *
     * <p>Refused, with nothing changed, when the outgoing creature is not on the field, is not
     * an enemy, is the trainer itself, or when the reserve will not fit the tile being freed.
     *
     * @return true if the switch happened
     * @since 0.4.0
     */
    public static boolean switchEnemy(net.minecraft.server.network.ServerPlayerEntity anyParticipant,
                                      com.crackedgames.craftics.combat.CombatEntity trainer,
                                      com.crackedgames.craftics.combat.CombatEntity outgoing,
                                      int reserveIndex) {
        if (anyParticipant == null) return false;
        var cm = com.crackedgames.craftics.combat.CombatManager
            .getActiveCombat(anyParticipant.getUuid());
        if (cm == null || !cm.isActive()) return false;
        return cm.switchEnemy(trainer, outgoing, reserveIndex);
    }

    /**
     * Send a trainer's benched creature out onto an empty tile, withdrawing nothing.
     *
     * <p>What opens a trainer fight and what answers a knockout;
     * {@link #switchEnemy} answers a bad matchup. A switch captures the outgoing creature's
     * live state onto the bench, so it needs one on the field to capture - when a trainer's
     * last creature faints there is nothing to withdraw, and the bench would otherwise be
     * stranded while the trainer conceded with reserves left.
     *
     * <p>The reserve is removed from the bench rather than swapped, since nothing is coming
     * back to replace it. It is fielded through the same path a switch uses, so its NBT,
     * spawn customizer, AI key and typing all land as if it had started on the grid.
     *
     * <pre>{@code
     * // The leader's next Pokemon, after the last one fainted.
     * if (!trainer.getBench().isEmpty()) {
     *     CrafticsAPI.sendOutEnemy(player, trainer, 0, new GridPos(4, 2));
     * }
     * }</pre>
     *
     * @param anyParticipant any player in the fight, used to find it
     * @param trainer        the enemy whose bench is being drawn from
     * @param reserveIndex   index into {@code trainer.getBench()}
     * @param tile           where to field it. Refused if out of bounds, occupied, not
     *                       standable, or too small for the creature's footprint
     * @return true if the creature was fielded
     * @since 0.4.0
     */
    public static boolean sendOutEnemy(net.minecraft.server.network.ServerPlayerEntity anyParticipant,
                                       com.crackedgames.craftics.combat.CombatEntity trainer,
                                       int reserveIndex,
                                       com.crackedgames.craftics.core.GridPos tile) {
        if (anyParticipant == null) return false;
        var cm = com.crackedgames.craftics.combat.CombatManager
            .getActiveCombat(anyParticipant.getUuid());
        if (cm == null || !cm.isActive()) return false;
        return cm.sendOutEnemy(trainer, reserveIndex, tile);
    }

    /**
     * Order one of the player's allies to take a specific action on its next turn, instead of
     * letting its AI decide.
     *
     * <p>The other half of the {@link CombatTool} loop: the tool opens your menu, the player
     * picks, and this is where the answer goes. Craftics owns the turn structure and how the
     * action resolves; the addon owns the screen and the choice.
     *
     * <p>The order is <b>consumed as it is obeyed</b>, so the ally follows it once and then
     * goes back to thinking for itself. A standing order would have the creature repeat last
     * turn's move forever on any turn the player forgot to issue a new one, which reads as the
     * ally being stuck.
     *
     * <p>Everything downstream is the ordinary ally path: a commanded attack goes through the
     * same damage, resistance, attack-typing and accuracy handling an AI-chosen one does. The
     * order decides WHAT the ally does, never how it resolves. Pair it with
     * {@code EnemyAction.CustomAction} to order a move the addon defines.
     *
     * @param ally   the ally to order. Must belong to this player
     * @param action what to do, or null to cancel a pending order
     * @return true if the order was accepted
     * @since 0.4.0
     */
    public static boolean commandAlly(net.minecraft.server.network.ServerPlayerEntity player,
                                      com.crackedgames.craftics.combat.CombatEntity ally,
                                      com.crackedgames.craftics.combat.ai.EnemyAction action) {
        if (player == null || ally == null || !ally.isAlly()) return false;
        if (ally.getOwnerUuid() != null && !ally.getOwnerUuid().equals(player.getUuid())) return false;
        ally.setCommandedAction(action);
        return true;
    }

    /**
     * Register a control item pinned to the hotbar for the duration of a fight, beside the
     * Move item.
     *
     * <p>Craftics already treats one item this way. Move is created when combat starts, locked
     * to a slot, restocked if it goes missing, undroppable, and destroyed afterwards - it is a
     * button that happens to live in the hotbar. A mod whose fights are commanded rather than
     * swung needs more of those buttons, and a control the player has to remember to carry is
     * a control they will lose.
     *
     * <p>The tool's {@code onUse} handler fires server-side on a mid-fight right-click and
     * Craftics does nothing else with the click, so the addon can open whatever UI it likes -
     * a vanilla screen handler, or its own payload to its own screen. Craftics deliberately
     * ships no menu framework here: a move-selection screen is the addon's design, and
     * anything Craftics invented would fit it worse.
     *
     * @see com.crackedgames.craftics.api.CombatTool
     * @since 0.4.0
     */
    public static void registerCombatTool(CombatTool tool) {
        com.crackedgames.craftics.api.registry.CombatToolRegistry.register(tool);
    }

    /**
     * Register a provider of allies that take the field with a player at the start of a fight.
     *
     * <p>Craftics' own battle party is built from real mobs standing in the hub: you tag a
     * wolf, it is snapshotted and discarded when combat starts, and put back afterwards. A mod
     * whose party is DATA ON THE PLAYER rather than entities in a yard cannot use that model -
     * there is no wolf to tag. This is the hook for that case.
     *
     * <p>Provider allies are fielded as temporary: they fight the battle and are gone, never
     * carried between levels and never materialised into the hub. An ally that was never a hub
     * entity must not be "returned" to one, or the player ends up with a second copy of a
     * creature the owning mod is still tracking in its own party.
     *
     * <p>Each ally may declare its own {@code aiKey}, spawn NBT and display name, which is
     * what lets a single entity type field a whole party of visibly different creatures.
     *
     * @see com.crackedgames.craftics.api.FieldAllyProvider
     * @since 0.3.9
     */
    public static void registerFieldAllyProvider(String key, FieldAllyProvider provider) {
        com.crackedgames.craftics.api.registry.FieldAllyProviderRegistry.register(key, provider);
    }

    /**
     * The allies this player has benched in the fight they are currently in.
     *
     * <p>What a switch menu is drawn from. Returns empty when the player is not fighting or
     * brought no reserves, so it is safe to call unconditionally.
     *
     * <p>The bench comes from {@link FieldAllyProvider#reserves}. Craftics' own hub pets never
     * appear on it: a hub pet is a real animal owed back to the yard afterwards, and one that
     * is neither in the yard nor in the fight is an animal in no place at all.
     *
     * @see com.crackedgames.craftics.api.BenchedAlly
     * @since 0.4.0
     */
    public static java.util.List<BenchedAlly> benchedAllies(net.minecraft.server.network.ServerPlayerEntity player) {
        if (player == null) return java.util.List.of();
        var combat = com.crackedgames.craftics.combat.CombatManager.getActiveCombat(player.getUuid());
        if (combat == null || !combat.isActive()) return java.util.List.of();
        return combat.benchedAllies(player.getUuid());
    }

    /**
     * Swap a benched ally onto the grid in place of one that is fighting, for 1 AP.
     *
     * <p>The counterpart to {@link #registerCombatTool}: the tool opens the addon's menu, the
     * menu asks for a swap, and this is the ask. Craftics owns what a switch MEANS - what it
     * costs, what it refuses, where the incoming creature stands - and the addon owns the
     * screen the player picked from, on the same split the combat tools use.
     *
     * <p>A creature that leaves the field keeps everything it was carrying. Bench a wounded,
     * poisoned ally and it comes back wounded and poisoned; a bench that healed would make
     * swapping the cheapest heal in the game.
     *
     * <p>Refused, with a message to the player and no AP spent, when it is not their turn,
     * when the ally is not theirs, when it is one of Craftics' own hub pets, when someone is
     * riding it, or when the incoming creature is too large for the tile being vacated.
     *
     * @param player               the player whose ally is being swapped
     * @param outgoingAllyEntityId entity id of the ally to withdraw
     * @param reserveIndex         {@link BenchedAlly#index()} of the ally to field
     * @return true if the swap happened
     * @since 0.4.0
     */
    public static boolean switchFieldAlly(net.minecraft.server.network.ServerPlayerEntity player,
                                          int outgoingAllyEntityId, int reserveIndex) {
        if (player == null) return false;
        var combat = com.crackedgames.craftics.combat.CombatManager.getActiveCombat(player.getUuid());
        if (combat == null || !combat.isActive()) return false;
        return combat.handleSwitchAlly(player, outgoingAllyEntityId, reserveIndex);
    }

    /**
     * Rename and re-icon one of the eight affinities.
     *
     * <p>The eight are fixed in NUMBER on purpose - they are the axes a player spends
     * level-up points on, and the levelling and respec screens are laid out for exactly
     * eight. What a total-conversion mod needs is not more axes but different ones, so the
     * eight slots are reskinnable instead.
     *
     * <p>One call renames the affinity <b>everywhere it is shown</b>: the level-up screen,
     * the respec screen, the infinite-mode class picker, the damage-type panel, weapon
     * tooltips, the combat damage feedback line, and the chat message for gaining a point.
     * The damage type that scales from the affinity is renamed with it, since a player who
     * saw "Fire affinity" next to "Slashing damage" would read it as a bug.
     *
     * <p>Nothing mechanical changes. A reskinned Slashing affinity still boosts Slashing
     * weapons and still grants the sweep chance.
     *
     * <p>Call this from a {@link CrafticsAddon}: it runs in common initialization, so the
     * server and the client both learn the skin. A server-only registration would rename
     * the chat lines and leave every screen showing the old name.
     *
     * @see com.crackedgames.craftics.api.AffinitySkin
     * @since 0.3.9
     */
    public static void reskinAffinity(
            com.crackedgames.craftics.combat.PlayerProgression.Affinity affinity,
            AffinitySkin skin) {
        com.crackedgames.craftics.api.registry.AffinitySkinRegistry.reskin(affinity, skin);
    }

    /**
     * Register a handler for an addon-defined enemy action.
     *
     * <p>Have an AI return {@code new EnemyAction.CustomAction("mymod:flamethrower", tiles, 6)}
     * and this handler resolves it. Wrap that in a {@code BossAbility} to get a telegraphed
     * charge-up - warning tiles, windup VFX and the one-turn delay - with the handler
     * firing when it resolves.
     *
     * <p>The handler is given a context wired into the real combat pipeline, so damage it
     * deals goes through resistances, attack typings, shields and death handling exactly
     * as a built-in action would.
     *
     * @see com.crackedgames.craftics.api.CustomActionHandler
     * @since 0.3.9
     */
    public static void registerCustomAction(String actionId, CustomActionHandler handler) {
        com.crackedgames.craftics.api.registry.CustomActionRegistry.register(actionId, handler);
    }

    /**
     * Register an attack type and its effectiveness chart.
     *
     * <p>An attack type is a trait of an ATTACK, not of a player. Nobody levels it. It
     * exists only to be compared against what a defender is, producing a damage
     * multiplier, and it is deliberately separate from both {@code DamageType} (which
     * decides the affinity a weapon scales from) and {@code Affinity} (which is what a
     * player levels). A weapon can be Slashing and Fire at once.
     *
     * <p>Charts are authored per attacking type - what it is strong and weak against -
     * because that is the shape that scales. Defenders then declare only what they ARE,
     * via {@link #setDefendingTypes}.
     *
     * @see com.crackedgames.craftics.api.registry.AttackTypeEntry
     * @since 0.3.9
     */
    public static void registerAttackType(
            com.crackedgames.craftics.api.registry.AttackTypeEntry entry) {
        com.crackedgames.craftics.api.registry.AttackTypeRegistry.register(entry);
    }

    /**
     * The attack type a mob's ordinary attacks carry.
     *
     * <p>Without this only the player's weapons could be typed, so every enemy would swing
     * untyped and a chart would do half of nothing. Set it once per creature and its melee,
     * ranged and ability damage are all typed.
     *
     * <p>A creature with a movepool overrides it per action from inside its AI with
     * {@code self.setPendingAttackType(id)} just before returning the action. That override
     * is cleared before every decision, so it only ever applies to the action that asked
     * for it.
     *
     * @since 0.3.9
     */
    public static void setDefaultAttackType(String mobKey, String typeId) {
        com.crackedgames.craftics.api.registry.AttackTypeRegistry
            .setDefaultAttackType(mobKey, typeId);
    }

    /**
     * Declare how to work out which types the PLAYER defends as, so incoming attacks can be
     * resisted or land hard.
     *
     * <p>A function rather than a fixed list because a player's typing usually derives from
     * something that changes during a run. Called on every hit the player takes, so keep it
     * cheap; returning null or empty means untyped, which is the default.
     *
     * @since 0.3.9
     */
    public static void setPlayerDefendingTypesProvider(
            java.util.function.Function<net.minecraft.server.network.ServerPlayerEntity,
                                        java.util.List<String>> provider) {
        com.crackedgames.craftics.api.registry.AttackTypeRegistry
            .setPlayerDefendingTypesProvider(provider);
    }

    /**
     * Declare which attack types a mob DEFENDS as.
     *
     * <p>{@code mobKey} is matched against the combatant's {@code aiKey} first and its
     * entity type id second, the same rule spawn customizers use - so one entity type can
     * still carry a different typing per creature.
     *
     * <p>A defender with several types multiplies through all of them, so strong against
     * one and weak against another cancels out. Passing no types clears the entry.
     *
     * @since 0.3.9
     */
    public static void setDefendingTypes(String mobKey, String... typeIds) {
        com.crackedgames.craftics.api.registry.AttackTypeRegistry
            .setDefendingTypes(mobKey, typeIds);
    }

    // === Custom Status Effects ===

    /**
     * Register a custom combat status effect. Addon content can then apply it to
     * combatants (via a {@code UsableItemHandler} or {@code CombatEntity}), and it
     * ticks each round alongside Craftics' built-in damage-over-time effects.
     * Use {@link CustomEffectDef#builder(String)} for a fluent API.
     *
     * @param def the effect definition
     */
    public static void registerEffect(CustomEffectDef def) {
        CombatEffectRegistry.register(def);
    }

    // === Environments ===

    /**
     * Register a custom arena environment theme - floor, post, and light blocks plus a
     * flavor-obstacle style. Biomes select an environment by id with their
     * {@code "environment"} field. Use {@link EnvironmentDef#builder(String)}.
     *
     * @param def the environment definition
     */
    public static void registerEnvironment(EnvironmentDef def) {
        EnvironmentRegistry.register(def);
    }

    // === Campaigns ===

    /**
     * Register a custom campaign - an authored, ordered playthrough of biomes that drives
     * difficulty scaling, dimension labeling, and completion detection. Use
     * {@link Campaign#builder(String)} for a fluent API.
     *
     * <p>The datapack route is {@code data/<your_mod_id>/craftics/campaigns/*.json}, read on
     * server start and re-read on {@code /reload}.
     *
     * <p><strong>Full-replace semantics:</strong> a registered campaign REPLACES the built-in
     * campaign path entirely. Exactly one campaign is active per world - the
     * most-recently-registered non-vanilla campaign wins over Craftics' built-in
     * {@code craftics:vanilla} campaign.
     *
     * @param campaign the campaign definition
     */
    public static void registerCampaign(Campaign campaign) {
        CampaignManager.register(campaign, RegistrationSource.CODE);
    }

    // === New: Equipment Scanners ===

    /**
     * Register an equipment scanner that contributes stat bonuses from
     * non-standard inventory slots (trinkets, baubles, curios, etc.).
     * The scanner is called during trim/equipment scanning and its results
     * are merged into the player's combat stats.
     *
     * @param id      Unique scanner ID (e.g., "artifacts")
     * @param scanner Function that scans a player and returns stat modifiers
     */
    public static void registerEquipmentScanner(String id, EquipmentScanner scanner) {
        EquipmentScannerRegistry.register(id, scanner);
    }

    // === New: Armor Sets ===

    /**
     * Register an armor set with damage type bonuses and stat bonuses.
     * The armor set ID should match what {@code PlayerCombatStats.getArmorSet()}
     * returns for your armor material.
     *
     * @param entry The armor set bonus data
     */
    public static void registerArmorSet(ArmorSetEntry entry) {
        ArmorSetRegistry.register(entry);
    }

    // === New: Hybrid Armor Sets ===

    /**
     * Register a hybrid armor set - the subclass bonus a player gets from wearing
     * exactly two distinct armor materials. Keyed by the unordered material pair.
     *
     * @param entry The hybrid set data
     */
    public static void registerHybridSet(HybridSetEntry entry) {
        HybridSetRegistry.register(entry);
    }

    // === New: Trim Patterns & Materials ===

    /**
     * Register a trim pattern with per-piece stat bonus and set bonus.
     *
     * @param entry The trim pattern data
     */
    public static void registerTrimPattern(TrimPatternEntry entry) {
        TrimPatternRegistry.register(entry);
    }

    /**
     * Register a trim material with its stat bonus.
     *
     * @param entry The trim material data
     */
    public static void registerTrimMaterial(TrimMaterialEntry entry) {
        TrimMaterialRegistry.register(entry);
    }

    // === New: Events ===

    /**
     * Register a custom between-level event that can occur during biome runs.
     * Events are rolled based on probability after each level.
     *
     * @param entry The event data including handler
     */
    public static void registerEvent(EventEntry entry) {
        EventRegistry.register(entry);
    }

    // === New: Piglin Bartering ===

    /**
     * Register a new piglin barter category (a piglin "type"). Categories are selected at random
     * when a Nether barter event fires; each drives its own reward pool. Addon ids should be
     * namespaced, e.g. {@code "mymod:warlord"}.
     *
     * @param category the category definition
     */
    public static void registerBarterCategory(com.crackedgames.craftics.combat.barter.BarterCategory category) {
        com.crackedgames.craftics.api.registry.BarterCategoryRegistry.register(category);
    }

    /**
     * Add a reward entry to a barter category's pool. The entry's {@code categoryId} may target a
     * built-in category (e.g. {@code "craftics:relic_trader"}) or an addon-registered one. Entries
     * targeting an unknown category are simply never rolled (no error).
     *
     * @param entry the reward entry
     */
    public static void registerBarterReward(com.crackedgames.craftics.combat.barter.BarterEntry entry) {
        com.crackedgames.craftics.api.registry.BarterRegistry.register(entry);
    }

    /**
     * Register a new villager trader (a trader "type") and the stock it sells. Traders are drawn at
     * random for the trader event, and each one that a player has MET gets a booth in the Trading
     * Hall - so registering a trader also adds a stall to the hall. Addon ids should be namespaced,
     * e.g. {@code "mymod:blacksmith"}.
     *
     * <p>Register during mod init. Order matters: the hall seats booths in registration order, so
     * traders registered later take later stalls.
     *
     * @param category the trader's identity (id, display name, icon, minimum biome tier)
     * @param stock    supplies the wares it can offer at a given tier
     */
    public static void registerTrader(com.crackedgames.craftics.combat.TraderCategory category,
                                      com.crackedgames.craftics.api.registry.TraderStockProvider stock) {
        com.crackedgames.craftics.api.registry.TraderCategoryRegistry.register(category, stock);
    }

    // === New: Enchantments ===

    /**
     * Register an enchantment that provides passive stat bonuses in Craftics combat.
     * The handler receives the enchantment level and a StatModifiers accumulator.
     *
     * @param enchantmentId Full enchantment ID (e.g., "mymod:holy_blessing")
     * @param handler       Function that applies stat bonuses based on enchantment level
     */
    public static void registerEnchantment(String enchantmentId, EnchantmentEffectHandler handler) {
        EnchantmentRegistry.register(enchantmentId, handler);
    }

    // === Runtime ===

    /**
     * Force a specific event to trigger at the next between-level transition
     * for the given player. Pass null to clear.
     *
     * @param player  The player whose next event to force
     * @param eventId Event ID (e.g., "mymod:enchanted_forge") or null to clear
     */
    public static void forceNextEvent(net.minecraft.server.network.ServerPlayerEntity player, String eventId) {
        com.crackedgames.craftics.combat.CombatManager cm = com.crackedgames.craftics.combat.CombatManager.get(player);
        if (cm != null) {
            cm.setForcedNextEvent(eventId);
        }
    }

    // === Queries ===

    /**
     * Get the total number of levels across all registered biomes.
     */
    public static int getTotalLevels() {
        return BiomeRegistry.getTotalLevelCount();
    }

    /**
     * Check if a custom environment style is registered.
     */
    public static boolean hasEnvironmentStyle(String styleName) {
        return EnvironmentRegistry.isRegistered(styleName);
    }
}
