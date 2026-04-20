package com.crackedgames.craftics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of 16x16 mob head textures used by the combat HUD.
 *
 * <p>Resolution order for {@link #get(String)}:
 * <ol>
 *   <li>Hardcoded vanilla textures bundled with Craftics.</li>
 *   <li>Runtime overrides registered via {@link #register(String, Identifier)}
 *       (intended for cross-mod compatibility add-ons).</li>
 *   <li>Auto-discovery: the loaded resource manager is probed at two conventions
 *       so users can drop PNGs into a resource pack for modded enemies without any code:
 *       <ul>
 *         <li>{@code craftics:textures/mob_heads/{namespace}/{path}.png}
 *             — resource pack supplies a head for a modded mob under craftics' namespace.</li>
 *         <li>{@code {namespace}:textures/mob_heads/{path}.png}
 *             — mod ships its own head texture in its own namespace.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>Probe results (both positive and negative) are cached so repeated HUD lookups are cheap.
 * Call {@link #clearProbeCache()} from a resource-reload listener when resource packs change.
 */
public final class MobHeadTextures {

    private static final Map<String, Identifier> VANILLA;
    private static final Map<String, Identifier> RUNTIME = new ConcurrentHashMap<>();
    /** Cache of resource-manager probe results. Optional.empty() = confirmed missing. */
    private static final Map<String, Optional<Identifier>> PROBE_CACHE = new ConcurrentHashMap<>();

    static {
        Map<String, Identifier> m = new HashMap<>();
        m.put("minecraft:zombie", vanilla("zombie"));
        m.put("minecraft:husk", vanilla("husk"));
        m.put("minecraft:drowned", vanilla("drowned"));
        m.put("minecraft:zombie_villager", vanilla("zombie_villager"));
        m.put("minecraft:skeleton", vanilla("skeleton"));
        m.put("minecraft:stray", vanilla("stray"));
        m.put("minecraft:wither_skeleton", vanilla("wither_skeleton"));
        m.put("minecraft:bogged", vanilla("bogged"));
        m.put("minecraft:spider", vanilla("spider"));
        m.put("minecraft:cave_spider", vanilla("cave_spider"));
        m.put("minecraft:endermite", vanilla("endermite"));
        m.put("minecraft:silverfish", vanilla("silverfish"));
        m.put("minecraft:bee", vanilla("bee"));
        m.put("minecraft:creeper", vanilla("creeper"));
        m.put("minecraft:blaze", vanilla("blaze"));
        m.put("minecraft:ghast", vanilla("ghast"));
        m.put("minecraft:magma_cube", vanilla("magma_cube"));
        m.put("minecraft:slime", vanilla("slime"));
        m.put("minecraft:breeze", vanilla("breeze"));
        m.put("minecraft:enderman", vanilla("enderman"));
        m.put("minecraft:ender_dragon", vanilla("ender_dragon"));
        m.put("minecraft:shulker", vanilla("shulker"));
        m.put("minecraft:witch", vanilla("witch"));
        m.put("minecraft:pillager", vanilla("pillager"));
        m.put("minecraft:vindicator", vanilla("vindicator"));
        m.put("minecraft:evoker", vanilla("evoker"));
        m.put("minecraft:vex", vanilla("vex"));
        m.put("minecraft:illusioner", vanilla("illusioner"));
        m.put("minecraft:ravager", vanilla("ravager"));
        m.put("minecraft:piglin", vanilla("piglin"));
        m.put("minecraft:piglin_brute", vanilla("piglin_brute"));
        m.put("minecraft:zombified_piglin", vanilla("zombified_piglin"));
        m.put("minecraft:hoglin", vanilla("hoglin"));
        m.put("minecraft:zoglin", vanilla("zoglin"));
        m.put("minecraft:strider", vanilla("strider"));
        m.put("minecraft:warden", vanilla("warden"));
        m.put("minecraft:phantom", vanilla("phantom"));
        m.put("minecraft:guardian", vanilla("guardian"));
        m.put("minecraft:elder_guardian", vanilla("elder_guardian"));
        m.put("minecraft:wither", vanilla("wither"));
        m.put("minecraft:wolf", vanilla("wolf"));
        m.put("minecraft:ocelot", vanilla("ocelot"));
        m.put("minecraft:cat", vanilla("cat"));
        m.put("minecraft:goat", vanilla("goat"));
        m.put("minecraft:polar_bear", vanilla("polar_bear"));
        m.put("minecraft:panda", vanilla("panda"));
        m.put("minecraft:fox", vanilla("fox"));
        m.put("minecraft:bat", vanilla("bat"));
        m.put("minecraft:cow", vanilla("cow"));
        m.put("minecraft:pig", vanilla("pig"));
        m.put("minecraft:sheep", vanilla("sheep"));
        m.put("minecraft:chicken", vanilla("chicken"));
        m.put("minecraft:rabbit", vanilla("rabbit"));
        m.put("minecraft:mooshroom", vanilla("mooshroom"));
        m.put("minecraft:camel", vanilla("camel"));
        m.put("minecraft:sniffer", vanilla("sniffer"));
        m.put("minecraft:iron_golem", vanilla("iron_golem"));
        m.put("minecraft:snow_golem", vanilla("snow_golem"));
        m.put("minecraft:villager", vanilla("villager"));
        m.put("minecraft:wandering_trader", vanilla("wandering_trader"));
        m.put("minecraft:dolphin", vanilla("dolphin"));
        m.put("minecraft:squid", vanilla("squid"));
        m.put("minecraft:glow_squid", vanilla("glow_squid"));
        m.put("minecraft:turtle", vanilla("turtle"));
        m.put("minecraft:cod", vanilla("cod"));
        m.put("minecraft:salmon", vanilla("salmon"));
        m.put("minecraft:pufferfish", vanilla("pufferfish"));
        m.put("minecraft:axolotl", vanilla("axolotl"));
        m.put("minecraft:frog", vanilla("frog"));
        m.put("minecraft:tadpole", vanilla("tadpole"));
        m.put("minecraft:allay", vanilla("allay"));
        m.put("minecraft:horse", vanilla("horse"));
        m.put("minecraft:donkey", vanilla("donkey"));
        m.put("minecraft:mule", vanilla("mule"));
        m.put("minecraft:skeleton_horse", vanilla("skeleton_horse"));
        m.put("minecraft:zombie_horse", vanilla("zombie_horse"));
        m.put("minecraft:llama", vanilla("llama"));
        m.put("minecraft:parrot", vanilla("parrot"));
        VANILLA = Collections.unmodifiableMap(m);
    }

    private static Identifier vanilla(String name) {
        return Identifier.of("craftics", "textures/mob_heads/" + name + ".png");
    }

    /**
     * Register a custom head texture for an entity type at runtime. Intended for mod-compat
     * add-ons that want to provide heads for modded enemies without shipping a resource pack.
     * Replaces any previously registered runtime override for that entity type.
     */
    public static void register(String entityTypeId, Identifier texture) {
        if (entityTypeId == null || entityTypeId.isEmpty() || texture == null) return;
        RUNTIME.put(entityTypeId, texture);
        PROBE_CACHE.remove(entityTypeId);
    }

    /** Clear the auto-discovery cache. Call this from a resource-reload listener. */
    public static void clearProbeCache() {
        PROBE_CACHE.clear();
    }

    /**
     * Resolve a head texture for an entity type, or {@code null} if none is available.
     * Callers are expected to draw a fallback (colored square, initial letter, etc.) on null.
     */
    public static Identifier get(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty()) return null;
        Identifier tex = VANILLA.get(entityTypeId);
        if (tex != null) return tex;
        tex = RUNTIME.get(entityTypeId);
        if (tex != null) return tex;
        Optional<Identifier> cached = PROBE_CACHE.get(entityTypeId);
        if (cached != null) return cached.orElse(null);
        Identifier probed = probe(entityTypeId);
        PROBE_CACHE.put(entityTypeId, Optional.ofNullable(probed));
        return probed;
    }

    private static Identifier probe(String entityTypeId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return null;
        ResourceManager rm = client.getResourceManager();
        if (rm == null) return null;

        int colon = entityTypeId.indexOf(':');
        String ns = colon >= 0 ? entityTypeId.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? entityTypeId.substring(colon + 1) : entityTypeId;

        // 1. craftics:textures/mob_heads/{ns}/{path}.png — resource pack override for modded mobs
        Identifier candidate = tryId("craftics", "textures/mob_heads/" + ns + "/" + path + ".png");
        if (candidate != null && rm.getResource(candidate).isPresent()) return candidate;

        // 2. {ns}:textures/mob_heads/{path}.png — mod ships its own head texture
        if (!"minecraft".equals(ns) && !"craftics".equals(ns)) {
            candidate = tryId(ns, "textures/mob_heads/" + path + ".png");
            if (candidate != null && rm.getResource(candidate).isPresent()) return candidate;
        }
        return null;
    }

    private static Identifier tryId(String namespace, String path) {
        return Identifier.tryParse(namespace + ":" + path);
    }

    public static void drawMobHead(net.minecraft.client.gui.DrawContext ctx, Identifier texture, int x, int y, int size) {
        if (size == 16) {
            //? if <=1.21.1 {
            ctx.drawTexture(texture, x, y, 0f, 0f, 16, 16, 16, 16);
            //?} else {
            /*ctx.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, texture, x, y, 0f, 0f, 16, 16, 16, 16);
            *///?}
        } else {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 0);
            float s = size / 16.0f;
            ctx.getMatrices().scale(s, s, 1.0f);
            //? if <=1.21.1 {
            ctx.drawTexture(texture, 0, 0, 0f, 0f, 16, 16, 16, 16);
            //?} else {
            /*ctx.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, texture, 0, 0, 0f, 0f, 16, 16, 16, 16);
            *///?}
            ctx.getMatrices().pop();
        }
    }

    public static int getMobColor(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:zombie", "minecraft:husk", "minecraft:drowned" -> 0xFF55AA55;
            case "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton" -> 0xFFCCCCCC;
            case "minecraft:creeper" -> 0xFF00CC00;
            case "minecraft:spider" -> 0xFF553333;
            case "minecraft:enderman" -> 0xFF330033;
            case "minecraft:blaze" -> 0xFFFF8800;
            case "minecraft:ghast" -> 0xFFEEEEEE;
            case "minecraft:phantom" -> 0xFF4466AA;
            case "minecraft:witch" -> 0xFF9933CC;
            case "minecraft:pillager" -> 0xFF666666;
            case "minecraft:vindicator" -> 0xFF777777;
            case "minecraft:shulker" -> 0xFF9955CC;
            case "minecraft:wolf" -> 0xFFBBAA99;
            case "minecraft:ocelot" -> 0xFFDDCC44;
            case "minecraft:piglin" -> 0xFFCC8855;
            case "minecraft:hoglin" -> 0xFF885533;
            case "minecraft:warden" -> 0xFF003344;
            case "minecraft:ender_dragon" -> 0xFF220022;
            case "minecraft:magma_cube" -> 0xFFCC4400;
            case "minecraft:goat" -> 0xFFCCBB99;
            case "minecraft:camel" -> 0xFFDDAA55;
            case "minecraft:breeze" -> 0xFF55CCFF;
            case "minecraft:bogged" -> 0xFF668844;
            case "minecraft:cave_spider" -> 0xFF224455;
            case "minecraft:silverfish" -> 0xFFAAAAAA;
            case "minecraft:slime" -> 0xFF55CC55;
            default -> 0xFF888888;
        };
    }

    public static String getDisplayInitial(String entityTypeId) {
        String id = entityTypeId;
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        return switch (entityTypeId) {
            case "minecraft:ender_dragon" -> "D";
            case "minecraft:wither_skeleton" -> "WS";
            case "minecraft:magma_cube" -> "MC";
            case "minecraft:cave_spider" -> "CS";
            case "minecraft:breeze" -> "Br";
            case "minecraft:bogged" -> "Bo";
            default -> id.isEmpty() ? "?" : id.substring(0, 1).toUpperCase();
        };
    }

    private MobHeadTextures() {}
}
