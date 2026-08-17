package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.compat.takesapillage.TakesAPillageCompat;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The two It Takes a Pillage events: the Pillager Camp and the Bastille.
 *
 * <p>Both field the full illager line - vanilla raiders beside the mod's archer, skirmisher
 * and legioner - and neither exists unless the mod is installed
 * ({@link TakesAPillageCompat#isLoaded()} gates the event roll).
 *
 * <p><b>Pillager Camp</b>: a single ambush-sized fight. Victory pays ordinary loot plus a
 * {@linkplain #createBastilleMap Bastille Map} - a marked map that, used during a later
 * fight, forces the next between-level event to be the Bastille (the exact Trial Key
 * mechanic, see the UseItemCallback in CrafticsMod).
 *
 * <p><b>Bastille</b>: a three-battle siege in one arena. Clearing the field summons the
 * next garrison immediately - the victory gate in {@code CombatManager.handleVictory}
 * holds the win until wave 3 falls, mirroring the raid's wave gate. The last wave is the
 * garrison elite. Winning pays the "schweet" tier: heavily-enchanted gear, an artifact,
 * materials, and a large emerald purse, through the same reward helpers the ominous trial
 * uses.
 *
 * <p>Stats mirror the raid's scaling model: authored base + biome-ordinal bonuses + NG+
 * multiplier, so the events land a notch above the regular level at the same depth.
 */
public final class PillageEvents {

    private PillageEvents() {}

    /** Forced-event keys, matching the {@code forced.equals(...)} branches in the roll chain. */
    public static final String CAMP_KEY = "pillager_camp";
    public static final String BASTILLE_KEY = "bastille";

    /** CUSTOM_DATA marker on the Bastille Map. */
    private static final String MAP_NBT_KEY = "craftics_bastille_map";

    /** Chance per eligible level transition that a camp appears (mod installed only). */
    public static final float CAMP_CHANCE = 0.07f;

    /** Battles in the bastille. */
    public static final int BASTILLE_WAVES = 3;

    // ─── Level definitions ───────────────────────────────────────────────────

    /** Camp: one fight on an 8x8 dirt yard. */
    public static class CampLevelDef extends TrialChamberEvent.TrialChamberLevelDef {
        CampLevelDef(int w, int h, List<EnemySpawn> spawns, Random rng) {
            super(w, h, spawns, rng);
        }
        @Override public String getName() { return "Pillager Camp"; }
        @Override public GridPos getPlayerStart() { return new GridPos(3, 3); }
        @Override public Block getFloorBlock() { return Blocks.COARSE_DIRT; }
        // Loads pillager_camp.schem from the arena search roots (worldedit/schematics
        // included); the procedural coarse-dirt yard is only the fallback.
        @Override public String getArenaBiomeId() { return "pillager_camp"; }
    }

    /** Bastille: three garrisons on a 9x9 stone court. Carries the scaling the later
     *  waves are spawned with, the same way RaidLevelDef carries its wave budget. */
    public static class BastilleLevelDef extends TrialChamberEvent.TrialChamberLevelDef {
        public final int hpBonus;
        public final int atkBonus;
        public final float ngMult;
        /** Which of the bastille arena layouts this siege uses (bastille1/2/3.schem). */
        private final int arenaVariant;

        BastilleLevelDef(int w, int h, List<EnemySpawn> spawns, Random rng,
                         int hpBonus, int atkBonus, float ngMult) {
            super(w, h, spawns, rng);
            this.hpBonus = hpBonus;
            this.atkBonus = atkBonus;
            this.ngMult = ngMult;
            this.arenaVariant = rng.nextInt(1000);
        }
        @Override public String getName() { return "The Bastille"; }
        @Override public GridPos getPlayerStart() { return new GridPos(4, 4); }
        @Override public Block getFloorBlock() { return Blocks.STONE_BRICKS; }
        // Loads from the bastille1/2/3.schem pool; the stone court is only the fallback.
        @Override public String getArenaBiomeId() { return "bastille"; }
        // Rolled at generation rather than left at the default: a synthetic event level
        // has no biomeLevelIndex, which would otherwise pin every siege to the same
        // layout (the trial chambers had this exact bug).
        @Override public int getArenaVariantIndex() { return arenaVariant; }
    }

    // ─── Generation ──────────────────────────────────────────────────────────

    /** One illager pick: id, base hp/atk/def/range(/speed via the registry at spawn). */
    private record Pick(String id, int hp, int atk, int def, int range) {}

    // Vanilla raiders at their biome-pool stat lines; modded ones at their compat lines.
    private static final Pick PILLAGER   = new Pick("minecraft:pillager", 8, 3, 0, 4);
    private static final Pick VINDICATOR = new Pick("minecraft:vindicator", 12, 4, 1, 1);
    private static final Pick EVOKER     = new Pick("minecraft:evoker",
        TrialChamberEvent.RAID_EVOKER_HP, TrialChamberEvent.RAID_EVOKER_ATK, 0, 1);
    private static final Pick RAVAGER    = new Pick("minecraft:ravager",
        TrialChamberEvent.RAID_RAVAGER_HP, TrialChamberEvent.RAID_RAVAGER_ATK,
        TrialChamberEvent.RAID_RAVAGER_DEF, 1);
    private static final Pick ARCHER = new Pick(TakesAPillageCompat.ARCHER,
        TakesAPillageCompat.ARCHER_HP, TakesAPillageCompat.ARCHER_ATK,
        TakesAPillageCompat.ARCHER_DEF, TakesAPillageCompat.ARCHER_RANGE);
    private static final Pick SKIRMISHER = new Pick(TakesAPillageCompat.SKIRMISHER,
        TakesAPillageCompat.SKIRMISHER_HP, TakesAPillageCompat.SKIRMISHER_ATK,
        TakesAPillageCompat.SKIRMISHER_DEF, 1);
    private static final Pick LEGIONER = new Pick(TakesAPillageCompat.LEGIONER,
        TakesAPillageCompat.LEGIONER_HP, TakesAPillageCompat.LEGIONER_ATK,
        TakesAPillageCompat.LEGIONER_DEF, 1);
    private static final Pick CLAY_GOLEM = new Pick(TakesAPillageCompat.CLAY_GOLEM, 16, 3, 2, 1);

    /** The legioner's HP is authored as UNSCALED - the wall is the same height at any
     *  depth; its damage reduction is what grows (see TakesAPillageCompat). */
    private static int scaledHp(Pick p, int hpBonus, float ngMult) {
        if (TakesAPillageCompat.LEGIONER.equals(p.id())) return p.hp();
        return (int) ((p.hp() + hpBonus) * ngMult);
    }

    /** Authored combat speeds that differ from a modded mob's engine default: the
     *  skirmisher's whole identity is its 3-tile walk, the legioner's its 1-tile trudge. */
    private static int speedOf(String id) {
        if (TakesAPillageCompat.SKIRMISHER.equals(id)) return TakesAPillageCompat.SKIRMISHER_SPEED;
        if (TakesAPillageCompat.LEGIONER.equals(id)) return TakesAPillageCompat.LEGIONER_SPEED;
        return 0; // entity-type default
    }

    private static void addSpawns(List<LevelDefinition.EnemySpawn> out, List<GridPos> used,
                                  Random rng, int w, int h, int hpBonus, int atkBonus,
                                  float ngMult, Pick... picks) {
        for (Pick p : picks) {
            GridPos pos = TrialChamberEvent.findSpawnPos(w, h, used, rng);
            if (pos == null) continue;
            used.add(pos);
            out.add(new LevelDefinition.EnemySpawn(p.id(), pos,
                scaledHp(p, hpBonus, ngMult),
                (int) ((p.atk() + atkBonus) * ngMult),
                p.def(), p.range(), p.id(), speedOf(p.id())));
        }
    }

    /**
     * The camp roster: a mixed patrol - crossbows, an axe, the mod's three, and a clay
     * golem standing watch. Grows a body every three biomes.
     */
    public static LevelDefinition generateCamp(int biomeOrdinal, int ngPlusLevel) {
        Random rng = new Random();
        int hpBonus = biomeOrdinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = biomeOrdinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());
        float ngMult = 1.0f + (ngPlusLevel * 0.08f);

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(3, 3)); // player start

        List<Pick> roster = new ArrayList<>(List.of(
            PILLAGER, ARCHER, ARCHER, SKIRMISHER, VINDICATOR, LEGIONER, CLAY_GOLEM));
        for (int extra = 0; extra < Math.max(0, biomeOrdinal) / 3; extra++) {
            roster.add(rng.nextBoolean() ? SKIRMISHER : ARCHER);
        }
        addSpawns(spawns, used, rng, 8, 8, hpBonus, atkBonus, ngMult,
            roster.toArray(new Pick[0]));

        return new CampLevelDef(8, 8, spawns, rng);
    }

    /** The bastille's opening garrison; waves 2 and 3 come from {@link #waveRoster}. */
    public static LevelDefinition generateBastille(int biomeOrdinal, int ngPlusLevel) {
        Random rng = new Random();
        int hpBonus = biomeOrdinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = biomeOrdinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());
        float ngMult = 1.0f + (ngPlusLevel * 0.08f);

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(4, 4)); // player start
        addSpawns(spawns, used, rng, 9, 9, hpBonus, atkBonus, ngMult,
            waveRoster(1, rng).toArray(new Pick[0]));

        return new BastilleLevelDef(9, 9, spawns, rng, hpBonus, atkBonus, ngMult);
    }

    /**
     * Each battle of the siege is its own garrison, harder than the last:
     * <ol>
     *   <li>the yard watch - crossbow line with a skirmisher runner,</li>
     *   <li>the wall guard - melee-heavy, a legioner anchoring a clay golem pair,</li>
     *   <li>the keep - the shield wall proper plus the garrison elite (evoker, and a
     *       ravager once the run is deep enough to carry one).</li>
     * </ol>
     */
    public static List<String[]> waveSpawnList(int wave, int biomeOrdinal) {
        Random rng = new Random();
        List<String[]> out = new ArrayList<>();
        for (Pick p : waveRoster(wave, rng)) {
            // Deep runs field a ravager in the keep; early ones don't - a 42 HP body in
            // biome one is a wall, not a fight.
            if (p == RAVAGER && biomeOrdinal < 4) continue;
            out.add(new String[]{p.id(),
                String.valueOf(p.hp()), String.valueOf(p.atk()),
                String.valueOf(p.def()), String.valueOf(p.range())});
        }
        return out;
    }

    private static List<Pick> waveRoster(int wave, Random rng) {
        return switch (wave) {
            case 1 -> List.of(PILLAGER, ARCHER, ARCHER, SKIRMISHER);
            case 2 -> List.of(VINDICATOR, SKIRMISHER, LEGIONER, CLAY_GOLEM, CLAY_GOLEM);
            default -> List.of(LEGIONER, LEGIONER, ARCHER, EVOKER, RAVAGER);
        };
    }

    /** Pick stats for a wave entry, so CombatManager can scale them like the raid does. */
    public static int[] statsOf(String[] entry) {
        return new int[]{Integer.parseInt(entry[1]), Integer.parseInt(entry[2]),
            Integer.parseInt(entry[3]), Integer.parseInt(entry[4])};
    }

    /** Whether this id is the legioner (unscaled HP - see scaledHp). */
    public static boolean isUnscaledHp(String entityTypeId) {
        return TakesAPillageCompat.LEGIONER.equals(entityTypeId);
    }

    // ─── The Bastille Map ────────────────────────────────────────────────────

    /**
     * The camp's signature reward: a marked map. Used during any later fight (the Trial
     * Key mechanic - see the UseItemCallback in CrafticsMod), it forces the next
     * between-level event to be the Bastille.
     */
    public static ItemStack createBastilleMap() {
        ItemStack map = new ItemStack(Items.MAP);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(MAP_NBT_KEY, true);
        map.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        map.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§lBastille Map"));
        map.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7A route to the illagers' stronghold."),
            Text.literal("§7Use during a fight to march on the"),
            Text.literal("§7Bastille after it - three battles,"),
            Text.literal("§7one prize. Consumed on use."))));
        map.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return map;
    }

    /** True if the stack is a Bastille Map. */
    public static boolean isBastilleMap(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.MAP) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.copyNbt().contains(MAP_NBT_KEY);
    }

    // ─── Rewards ─────────────────────────────────────────────────────────────

    /** Emeralds the camp pays each participant on top of normal victory loot. */
    public static int campEmeralds(int biomeOrdinal) {
        return 8 + Math.max(0, biomeOrdinal) * 2;
    }

    /** The bastille's emerald purse - deliberately a big number; it cost three fights. */
    public static int bastilleEmeralds(int biomeOrdinal) {
        return 30 + Math.max(0, biomeOrdinal) * 5;
    }

    /** Material bundle every participant gets from the bastille's stores. */
    public static List<ItemStack> bastilleMaterials(Random rng) {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(Items.IRON_INGOT, 6 + rng.nextInt(5)));
        out.add(new ItemStack(Items.GOLD_INGOT, 3 + rng.nextInt(4)));
        out.add(new ItemStack(Items.DIAMOND, 2 + rng.nextInt(3)));
        if (rng.nextFloat() < 0.35f) out.add(new ItemStack(Items.NETHERITE_SCRAP, 1));
        // The mod's own trophy when present - resolves through the registry so a missing
        // id just drops the trophy line rather than crashing.
        var horn = net.minecraft.registry.Registries.ITEM.get(
            net.minecraft.util.Identifier.of("takesapillage", "ravager_horn"));
        if (horn != Items.AIR) out.add(new ItemStack(horn, 1));
        return out;
    }

    /** Deliver a stack, overflow to the ground - matches the reward helpers' behaviour. */
    public static void give(ServerPlayerEntity player, ItemStack stack) {
        LootDelivery.deliver(player, stack);
    }
}
