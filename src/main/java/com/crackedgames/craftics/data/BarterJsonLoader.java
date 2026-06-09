package com.crackedgames.craftics.data;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.RegistrationSource;
import com.crackedgames.craftics.api.registry.BarterCategoryRegistry;
import com.crackedgames.craftics.api.registry.BarterRegistry;
import com.crackedgames.craftics.combat.barter.BarterCategory;
import com.crackedgames.craftics.combat.barter.BarterEntry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads barter categories + reward pools from {@code data/<namespace>/craftics/barter/*.json}.
 * Each file declares one category and its rewards. Unknown item ids are skipped (not handed out
 * as air), mirroring how unknown loot ids are skipped elsewhere.
 */
public final class BarterJsonLoader extends CrafticsDataLoader<BarterJsonLoader.BarterFile> {

    public BarterJsonLoader() { super("craftics/barter", "barter"); }

    /** Parsed result for one file: a category plus its reward entries. */
    public record BarterFile(BarterCategory category, List<BarterEntry> rewards) {}

    public BarterFile parseForTest(Identifier id, JsonObject json) { return parse(id, json); }
    public void registerForTest(BarterFile f) { register(f, RegistrationSource.DATAPACK); }

    @Override
    protected BarterFile parse(Identifier fileId, JsonObject json) {
        if (!json.has("category")) {
            CrafticsMod.LOGGER.warn("Barter JSON {} missing 'category' - skipping", fileId);
            return null;
        }
        String categoryId = json.get("category").getAsString();
        String displayName = json.has("displayName") ? json.get("displayName").getAsString() : categoryId;
        String icon = json.has("icon") ? json.get("icon").getAsString() : "";
        String hint = json.has("dialogueHint") ? json.get("dialogueHint").getAsString() : "";
        int catTier = json.has("minBiomeTier") ? json.get("minBiomeTier").getAsInt() : 0;

        BarterCategory category = new BarterCategory(categoryId, displayName, icon, hint, catTier);

        List<BarterEntry> rewards = new ArrayList<>();
        if (json.has("rewards")) {
            for (JsonElement el : json.getAsJsonArray("rewards")) {
                JsonObject r = el.getAsJsonObject();
                if (!r.has("item")) {
                    CrafticsMod.LOGGER.warn("Barter {} reward missing 'item' - skipping", fileId);
                    continue;
                }
                Identifier itemId = Identifier.tryParse(r.get("item").getAsString());
                if (itemId == null || !Registries.ITEM.containsId(itemId)) {
                    CrafticsMod.LOGGER.warn("Barter {} unknown item '{}' - skipping", fileId, r.get("item").getAsString());
                    continue;
                }
                Item item = Registries.ITEM.get(itemId);
                int[] range = parseCount(r);
                int weight = r.has("weight") ? r.get("weight").getAsInt() : 1;
                int tier = r.has("minBiomeTier") ? r.get("minBiomeTier").getAsInt() : 0;
                rewards.add(new BarterEntry(categoryId, new ItemStack(item), range[0], range[1], weight, tier));
            }
        }
        return new BarterFile(category, rewards);
    }

    /** Parse a "count" that may be an int (5) or a range string ("6-12"). Defaults to 1.
     *  Package-private + static so BarterJsonLoaderTest can unit-test it without touching items. */
    static int[] parseCount(JsonObject r) {
        if (!r.has("count")) return new int[]{1, 1};
        JsonElement c = r.get("count");
        try {
            if (c.getAsJsonPrimitive().isNumber()) {
                int n = c.getAsInt();
                return new int[]{n, n};
            }
            String s = c.getAsString();
            int dash = s.indexOf('-');
            if (dash > 0) {
                int lo = Integer.parseInt(s.substring(0, dash).trim());
                int hi = Integer.parseInt(s.substring(dash + 1).trim());
                return new int[]{lo, hi};
            }
            int n = Integer.parseInt(s.trim());
            return new int[]{n, n};
        } catch (RuntimeException ex) {
            return new int[]{1, 1};
        }
    }

    @Override
    protected void register(BarterFile parsed, RegistrationSource source) {
        if (parsed == null) return;
        BarterCategoryRegistry.register(parsed.category(), source);
        for (BarterEntry e : parsed.rewards()) BarterRegistry.register(e, source);
    }

    @Override
    protected void clearDatapackEntries() {
        BarterRegistry.clearDatapackEntries();
        BarterCategoryRegistry.clearDatapackEntries();
    }
}
