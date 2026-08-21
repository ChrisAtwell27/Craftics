package com.crackedgames.craftics.client.guide;

import com.crackedgames.craftics.level.BiomeAtlasCodec;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-side store for the biome atlas, and the builder for its guide book category.
 *
 * <p>The atlas is a bestiary for places: one page per biome, filled in the first time the island
 * goes there. Undiscovered biomes are listed but empty, because a table of contents with gaps in
 * it is the thing that makes discovery feel like discovery - a list you cannot see at all is
 * just an absence.
 *
 * <p>The built category is cached and rebuilt only when a new payload arrives.
 * {@link GuideBookData#getCategories()} is called from the render path, so building a dozen
 * biome pages there would be a per-frame allocation of the entire atlas.
 */
public final class BiomeAtlasData {
    private BiomeAtlasData() {}

    /** Category name. Also the key the screen uses to recognise this category. */
    public static final String CATEGORY = "Biome Atlas";

    /**
     * Cards per page.
     *
     * <p>The boxed-page renderer clips any card that does not fit and its doc says the data is
     * expected to hand-split long lists across pages. Hand-splitting is not available to a page
     * built from a datapack's loot table, so the split happens here instead. Four is what fits
     * the <em>smallest</em> book the screen will lay out; picking a number that only works on a
     * tall window would silently drop the last drop in the list.
     */
    private static final int CARDS_PER_PAGE = 4;

    private static List<BiomeAtlasCodec.Entry> entries = List.of();
    private static GuideBookData.Category cached = null;

    /**
     * Replace the atlas with a freshly synced one.
     *
     * <p>Also hands the set of discovered biome page names to {@link GuideBookData}, which owns
     * lock state for the whole book. That keeps one answer to "is this entry readable" rather
     * than a second, parallel one that the screen would have to know to ask.
     */
    public static void apply(String encoded) {
        entries = BiomeAtlasCodec.decode(encoded);
        cached = null;

        List<String> unlocked = new ArrayList<>();
        for (BiomeAtlasCodec.Entry e : entries) {
            if (e.discovered()) unlocked.add(pageName(e));
        }
        GuideBookData.setAtlasUnlocks(unlocked);
    }

    /** Drop everything. Called on disconnect, alongside the rest of the book's per-world state. */
    public static void clear() {
        entries = List.of();
        cached = null;
        GuideBookData.setAtlasUnlocks(List.of());
    }

    /** The atlas category, or null when no atlas has arrived (single player pre-join, old server). */
    public static GuideBookData.Category category() {
        if (entries.isEmpty()) return null;
        if (cached == null) cached = build();
        return cached;
    }

    /** Guide book entry name for a biome. Falls back to the id when a pack ships no name. */
    private static String pageName(BiomeAtlasCodec.Entry e) {
        String name = e.displayName();
        return (name == null || name.isEmpty()) ? prettify(e.biomeId()) : name;
    }

    private static GuideBookData.Category build() {
        List<GuideBookData.Entry> out = new ArrayList<>();
        for (BiomeAtlasCodec.Entry e : entries) {
            out.add(new GuideBookData.Entry(pageName(e), icon(e.biomeId()), pages(e)));
        }
        return new GuideBookData.Category(CATEGORY, "minecraft:filled_map",
            "Every place a run can take you, who lives there and what it drops. Pages fill in as your island explores.",
            out);
    }

    private static List<GuideBookData.Page> pages(BiomeAtlasCodec.Entry e) {
        String name = pageName(e);
        if (!e.discovered()) {
            return List.of(new GuideBookData.Page(name,
                "Not yet explored.\n\nRun this biome and its page fills in: who lives here, the boss that guards it, what they drop, and the biome's own loot pool."));
        }

        List<GuideBookData.Page> pages = new ArrayList<>();

        // Page one is the biome at a glance plus its whole roster as a grid of heads. The
        // roster is a set of creatures to recognise, so it wants to be seen at once rather
        // than read one card at a time - and each head links to that creature's bestiary
        // entry, where its drops live.
        pages.add(GuideBookData.Page.withMobs(name, overview(e), residents(e), e.bossId()));

        addPaged(pages, "Level Reward", boxes(e.loot()),
            "Rolled once when you clear a level here. What the creatures themselves drop is on their bestiary pages.");

        addPaged(pages, "Enchantments", boxes(e.enchants()),
            "Enchanted books this biome can drop.");

        return pages;
    }

    private static String overview(BiomeAtlasCodec.Entry e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.levelCount()).append(e.levelCount() == 1 ? " level" : " levels")
          .append(" before the biome is cleared.\n");
        sb.append(e.nightLevel() ? "Fought at night.\n" : "Fought in daylight.\n");
        if (!e.effectId().isEmpty()) {
            sb.append("\n§6").append(prettify(e.effectId()));
            // startLevel 0 means the effect is not on a timer at all; anything else is the
            // level it begins on, which is worth knowing before committing to the run.
            if (e.effectStartLevel() > 1) sb.append(" from level ").append(e.effectStartLevel());
            sb.append("§r\n");
            String weather = effectDescription(e.effectId());
            sb.append(weather.isEmpty() ? "" : weather + "\n");
        }
        String special = specialCondition(e);
        if (!special.isEmpty()) sb.append("\n").append(special);

        if (!e.hostileIds().isEmpty() || !e.passiveIds().isEmpty() || !e.bossId().isEmpty()) {
            sb.append("\n§7Click a creature for its stats and drops.");
        }
        return sb.toString().trim();
    }

    /**
     * What this biome's persistent weather does, described by the effect itself.
     *
     * <p>Same reasoning as {@link #specialCondition}: the effects register in the mod's main
     * entrypoint, which runs on the client, so the sentence is read rather than synced. The
     * page used to print the bare id - "Sculk Sensors from level 1" names the thing about to
     * blind your party and says nothing about the boots that prevent it.
     *
     * <p>Empty for an addon effect that has not written one, which leaves it with exactly the
     * bare name it had before.
     */
    private static String effectDescription(String effectId) {
        var effect = com.crackedgames.craftics.combat.biomeeffect.BiomeEffectRegistry.get(effectId);
        if (effect == null) return "";
        String text = effect.description();
        return (text == null || text.isBlank()) ? "" : text;
    }

    /**
     * The biome's level-4 encounter, described by the mechanic itself.
     *
     * <p>Read straight from {@link MinibossRegistry} rather than sent over the wire, because
     * the mechanics register in the mod's main entrypoint - which runs on the client too. There
     * is nothing here the client does not already have, and a synced copy would be a second
     * version of the same sentence waiting to disagree with the first.
     *
     * <p>The gate is the same one {@code LevelGenerator.isMinibossLevel} applies: a registered
     * mechanic AND a biome long enough to have a level 4 to put it on. Duplicating the
     * condition is the risk here, so it is written to match that method exactly - a biome that
     * shows the line but never runs the encounter would be worse than showing nothing.
     */
    private static String specialCondition(BiomeAtlasCodec.Entry e) {
        if (e.levelCount() < 7) return "";
        var mechanic = com.crackedgames.craftics.combat.miniboss.MinibossRegistry.get(e.biomeId());
        if (mechanic == null) return "";
        String text = mechanic.description();
        if (text == null || text.isBlank()) return "";
        // The banner carries its own colour codes and a symbol; strip the formatting so it sits
        // in the page's prose rather than shouting out of it.
        String name = mechanic.introTitle() == null ? "" : mechanic.introTitle().replaceAll("§.", "").trim();
        return "\n§6Level 4" + (name.isEmpty() ? "" : " - " + name) + "§r\n" + text;
    }

    /**
     * Every creature in the biome, hostiles first, then passives, then the boss.
     *
     * <p>Built from the rosters rather than from the drop tables, so a creature that drops
     * nothing still appears. The roster is what the biome contains; leaving out the ones with
     * empty pockets would make the page look incomplete.
     */
    private static List<String> residents(BiomeAtlasCodec.Entry e) {
        List<String> out = new ArrayList<>();
        for (String id : e.hostileIds()) addResident(out, id);
        for (String id : e.passiveIds()) addResident(out, id);
        // The boss is NOT added here: it gets its own named line above the grid. A biome whose
        // boss is also in its hostile pool would otherwise show the same creature twice, once
        // named and once as an anonymous head.
        return out;
    }

    private static void addResident(List<String> out, String mobId) {
        if (mobId == null || mobId.isEmpty() || out.contains(mobId)) return;
        out.add(mobId);
    }

    /**
     * What a creature drops, as ready-to-draw lines, looked up by its bestiary entry name.
     *
     * <p>Keyed on the creature rather than on the biome because the drop table itself is: the
     * game rolls it from the entity type alone, so a zombie drops the same things wherever it
     * is met. Repeating that on every biome page that contains a zombie would be several
     * copies of one fact, and the first balance change would leave them disagreeing.
     *
     * <p>Returns empty for anything the atlas has never described - an undiscovered biome's
     * creatures, or a mob that drops nothing.
     */
    public static List<String> dropLinesFor(String bestiaryEntryName) {
        if (bestiaryEntryName == null || bestiaryEntryName.isEmpty()) return List.of();
        for (BiomeAtlasCodec.Entry e : entries) {
            for (BiomeAtlasCodec.MobDrops m : e.mobDrops()) {
                if (!prettify(m.mobId()).equals(bestiaryEntryName)) continue;
                int total = 0;
                for (BiomeAtlasCodec.Drop d : m.drops()) total += d.weight();
                if (total <= 0) total = 1;
                List<String> out = new ArrayList<>();
                for (BiomeAtlasCodec.Drop d : m.drops()) {
                    double share = 100.0 * d.weight() / total;
                    out.add("§7- §r" + displayName(d.id()) + " §8" + formatShare(share));
                }
                return out;
            }
        }
        return List.of();
    }

    /**
     * Append {@code cards} as however many pages they need, or nothing at all when empty.
     *
     * <p>Later pages repeat the title with a counter rather than inventing new headings, so a
     * three-page loot list reads as one list. Only item lists paginate now - the creature
     * roster is a grid on page one and never splits.
     */
    private static void addPaged(List<GuideBookData.Page> pages, String title,
                                 List<GuideBookData.Box> cards, String intro) {
        if (cards.isEmpty()) return;
        int total = (cards.size() + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE;
        for (int p = 0; p < total; p++) {
            List<GuideBookData.Box> slice = new ArrayList<>(cards.subList(
                p * CARDS_PER_PAGE, Math.min(cards.size(), (p + 1) * CARDS_PER_PAGE)));
            String heading = total == 1 ? title : title + " (" + (p + 1) + "/" + total + ")";
            pages.add(new GuideBookData.Page(heading, p == 0 ? intro : "", slice));
        }
    }

    /**
     * One card per drop: the item's own icon and name, its share of the pool, and the rarity
     * word for that share.
     *
     * <p>Both the number and the word, deliberately. The percentage is the honest figure and the
     * word is the one a player actually reasons with - "4.2%" and "rare" answer different
     * questions, and printing only the first makes the page a table nobody reads.
     */
    private static List<GuideBookData.Box> boxes(List<BiomeAtlasCodec.Drop> drops) {
        int total = 0;
        for (BiomeAtlasCodec.Drop d : drops) total += d.weight();
        if (total <= 0) total = 1;

        List<GuideBookData.Box> out = new ArrayList<>();
        for (BiomeAtlasCodec.Drop d : drops) {
            double share = 100.0 * d.weight() / total;
            out.add(new GuideBookData.Box(d.id(), displayName(d.id()),
                formatShare(share), rarityWord(share)));
        }
        return out;
    }

    /** "12.5%", or "<0.1%" rather than a misleading "0.0%" for a genuinely present drop. */
    static String formatShare(double share) {
        if (share > 0 && share < 0.1) return "<0.1%";
        return String.format(Locale.ROOT, share >= 10 ? "%.0f%%" : "%.1f%%", share);
    }

    /** Plain-language rarity for a share of the pool. */
    static String rarityWord(double share) {
        if (share >= 15) return "Common";
        if (share >= 7) return "Uncommon";
        if (share >= 2) return "Rare";
        return "Very rare";
    }

    /** Localized item name, falling back to a prettified id for an item this client lacks. */
    private static String displayName(String itemId) {
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id != null) {
                Item item = Registries.ITEM.get(id);
                if (item != null && item != net.minecraft.item.Items.AIR) {
                    return item.getName().getString();
                }
            }
        } catch (Exception ignored) {}
        return prettify(itemId);
    }

    /** "minecraft:zombified_piglin" / "deep_dark" -> "Zombified Piglin" / "Deep Dark". */
    static String prettify(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String tail = raw;
        int colon = tail.indexOf(':');
        if (colon >= 0) tail = tail.substring(colon + 1);
        int slash = tail.lastIndexOf('/');
        if (slash >= 0) tail = tail.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (String part : tail.split("[_\\s]+")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /**
     * A representative item for a biome's sidebar icon, matched on its id.
     *
     * <p>Keyword matching rather than a synced block, because the icon is decoration: getting it
     * wrong costs a slightly odd picture, and the fallback map icon is a perfectly good answer
     * for anything unrecognised - including every biome an addon ships.
     */
    static String icon(String biomeId) {
        String id = biomeId == null ? "" : biomeId.toLowerCase(Locale.ROOT);
        if (id.contains("desert") || id.contains("badlands")) return "minecraft:sand";
        if (id.contains("nether") || id.contains("crimson") || id.contains("warped")) return "minecraft:netherrack";
        if (id.contains("basalt")) return "minecraft:basalt";
        if (id.contains("soul")) return "minecraft:soul_sand";
        if (id.contains("end") || id.contains("chorus")) return "minecraft:end_stone";
        if (id.contains("deep_dark") || id.contains("sculk")) return "minecraft:sculk";
        if (id.contains("cave") || id.contains("dripstone")) return "minecraft:stone";
        if (id.contains("ocean") || id.contains("river") || id.contains("reef")) return "minecraft:water_bucket";
        if (id.contains("ice") || id.contains("snow") || id.contains("frozen") || id.contains("tundra")) return "minecraft:packed_ice";
        if (id.contains("swamp") || id.contains("mangrove")) return "minecraft:lily_pad";
        if (id.contains("jungle") || id.contains("bamboo")) return "minecraft:jungle_log";
        if (id.contains("forest") || id.contains("taiga") || id.contains("grove")) return "minecraft:oak_log";
        if (id.contains("mushroom") || id.contains("fungus")) return "minecraft:red_mushroom_block";
        if (id.contains("peak") || id.contains("mountain") || id.contains("stony")) return "minecraft:stone";
        if (id.contains("savanna") || id.contains("plains") || id.contains("meadow")) return "minecraft:grass_block";
        return "minecraft:filled_map";
    }
}
