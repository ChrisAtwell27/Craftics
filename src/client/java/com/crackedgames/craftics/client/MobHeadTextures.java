package com.crackedgames.craftics.client;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Static registry of 16x16 mob head textures used by the combat HUD.
 */
public final class MobHeadTextures {

    private static final Map<String, Identifier> TEXTURES;

    static {
        Map<String, Identifier> m = new HashMap<>();
        m.put("minecraft:zombie", Identifier.of("craftics", "textures/mob_heads/zombie.png"));
        m.put("minecraft:husk", Identifier.of("craftics", "textures/mob_heads/husk.png"));
        m.put("minecraft:drowned", Identifier.of("craftics", "textures/mob_heads/drowned.png"));
        m.put("minecraft:zombie_villager", Identifier.of("craftics", "textures/mob_heads/zombie_villager.png"));
        m.put("minecraft:skeleton", Identifier.of("craftics", "textures/mob_heads/skeleton.png"));
        m.put("minecraft:stray", Identifier.of("craftics", "textures/mob_heads/stray.png"));
        m.put("minecraft:wither_skeleton", Identifier.of("craftics", "textures/mob_heads/wither_skeleton.png"));
        m.put("minecraft:bogged", Identifier.of("craftics", "textures/mob_heads/bogged.png"));
        m.put("minecraft:spider", Identifier.of("craftics", "textures/mob_heads/spider.png"));
        m.put("minecraft:cave_spider", Identifier.of("craftics", "textures/mob_heads/cave_spider.png"));
        m.put("minecraft:endermite", Identifier.of("craftics", "textures/mob_heads/endermite.png"));
        m.put("minecraft:silverfish", Identifier.of("craftics", "textures/mob_heads/silverfish.png"));
        m.put("minecraft:bee", Identifier.of("craftics", "textures/mob_heads/bee.png"));
        m.put("minecraft:creeper", Identifier.of("craftics", "textures/mob_heads/creeper.png"));
        m.put("minecraft:blaze", Identifier.of("craftics", "textures/mob_heads/blaze.png"));
        m.put("minecraft:ghast", Identifier.of("craftics", "textures/mob_heads/ghast.png"));
        m.put("minecraft:magma_cube", Identifier.of("craftics", "textures/mob_heads/magma_cube.png"));
        m.put("minecraft:slime", Identifier.of("craftics", "textures/mob_heads/slime.png"));
        m.put("minecraft:breeze", Identifier.of("craftics", "textures/mob_heads/breeze.png"));
        m.put("minecraft:enderman", Identifier.of("craftics", "textures/mob_heads/enderman.png"));
        m.put("minecraft:ender_dragon", Identifier.of("craftics", "textures/mob_heads/ender_dragon.png"));
        m.put("minecraft:shulker", Identifier.of("craftics", "textures/mob_heads/shulker.png"));
        m.put("minecraft:witch", Identifier.of("craftics", "textures/mob_heads/witch.png"));
        m.put("minecraft:pillager", Identifier.of("craftics", "textures/mob_heads/pillager.png"));
        m.put("minecraft:vindicator", Identifier.of("craftics", "textures/mob_heads/vindicator.png"));
        m.put("minecraft:evoker", Identifier.of("craftics", "textures/mob_heads/evoker.png"));
        m.put("minecraft:vex", Identifier.of("craftics", "textures/mob_heads/vex.png"));
        m.put("minecraft:illusioner", Identifier.of("craftics", "textures/mob_heads/illusioner.png"));
        m.put("minecraft:ravager", Identifier.of("craftics", "textures/mob_heads/ravager.png"));
        m.put("minecraft:piglin", Identifier.of("craftics", "textures/mob_heads/piglin.png"));
        m.put("minecraft:piglin_brute", Identifier.of("craftics", "textures/mob_heads/piglin_brute.png"));
        m.put("minecraft:zombified_piglin", Identifier.of("craftics", "textures/mob_heads/zombified_piglin.png"));
        m.put("minecraft:hoglin", Identifier.of("craftics", "textures/mob_heads/hoglin.png"));
        m.put("minecraft:zoglin", Identifier.of("craftics", "textures/mob_heads/zoglin.png"));
        m.put("minecraft:strider", Identifier.of("craftics", "textures/mob_heads/strider.png"));
        m.put("minecraft:warden", Identifier.of("craftics", "textures/mob_heads/warden.png"));
        m.put("minecraft:phantom", Identifier.of("craftics", "textures/mob_heads/phantom.png"));
        m.put("minecraft:guardian", Identifier.of("craftics", "textures/mob_heads/guardian.png"));
        m.put("minecraft:elder_guardian", Identifier.of("craftics", "textures/mob_heads/elder_guardian.png"));
        m.put("minecraft:wither", Identifier.of("craftics", "textures/mob_heads/wither.png"));
        m.put("minecraft:wolf", Identifier.of("craftics", "textures/mob_heads/wolf.png"));
        m.put("minecraft:ocelot", Identifier.of("craftics", "textures/mob_heads/ocelot.png"));
        m.put("minecraft:cat", Identifier.of("craftics", "textures/mob_heads/cat.png"));
        m.put("minecraft:goat", Identifier.of("craftics", "textures/mob_heads/goat.png"));
        m.put("minecraft:polar_bear", Identifier.of("craftics", "textures/mob_heads/polar_bear.png"));
        m.put("minecraft:panda", Identifier.of("craftics", "textures/mob_heads/panda.png"));
        m.put("minecraft:fox", Identifier.of("craftics", "textures/mob_heads/fox.png"));
        m.put("minecraft:bat", Identifier.of("craftics", "textures/mob_heads/bat.png"));
        m.put("minecraft:cow", Identifier.of("craftics", "textures/mob_heads/cow.png"));
        m.put("minecraft:pig", Identifier.of("craftics", "textures/mob_heads/pig.png"));
        m.put("minecraft:sheep", Identifier.of("craftics", "textures/mob_heads/sheep.png"));
        m.put("minecraft:chicken", Identifier.of("craftics", "textures/mob_heads/chicken.png"));
        m.put("minecraft:rabbit", Identifier.of("craftics", "textures/mob_heads/rabbit.png"));
        m.put("minecraft:mooshroom", Identifier.of("craftics", "textures/mob_heads/mooshroom.png"));
        m.put("minecraft:camel", Identifier.of("craftics", "textures/mob_heads/camel.png"));
        m.put("minecraft:sniffer", Identifier.of("craftics", "textures/mob_heads/sniffer.png"));
        m.put("minecraft:iron_golem", Identifier.of("craftics", "textures/mob_heads/iron_golem.png"));
        m.put("minecraft:snow_golem", Identifier.of("craftics", "textures/mob_heads/snow_golem.png"));
        m.put("minecraft:villager", Identifier.of("craftics", "textures/mob_heads/villager.png"));
        m.put("minecraft:wandering_trader", Identifier.of("craftics", "textures/mob_heads/wandering_trader.png"));
        m.put("minecraft:dolphin", Identifier.of("craftics", "textures/mob_heads/dolphin.png"));
        m.put("minecraft:squid", Identifier.of("craftics", "textures/mob_heads/squid.png"));
        m.put("minecraft:glow_squid", Identifier.of("craftics", "textures/mob_heads/glow_squid.png"));
        m.put("minecraft:turtle", Identifier.of("craftics", "textures/mob_heads/turtle.png"));
        m.put("minecraft:cod", Identifier.of("craftics", "textures/mob_heads/cod.png"));
        m.put("minecraft:salmon", Identifier.of("craftics", "textures/mob_heads/salmon.png"));
        m.put("minecraft:pufferfish", Identifier.of("craftics", "textures/mob_heads/pufferfish.png"));
        m.put("minecraft:axolotl", Identifier.of("craftics", "textures/mob_heads/axolotl.png"));
        m.put("minecraft:frog", Identifier.of("craftics", "textures/mob_heads/frog.png"));
        m.put("minecraft:tadpole", Identifier.of("craftics", "textures/mob_heads/tadpole.png"));
        m.put("minecraft:allay", Identifier.of("craftics", "textures/mob_heads/allay.png"));
        m.put("minecraft:horse", Identifier.of("craftics", "textures/mob_heads/horse.png"));
        m.put("minecraft:donkey", Identifier.of("craftics", "textures/mob_heads/donkey.png"));
        m.put("minecraft:mule", Identifier.of("craftics", "textures/mob_heads/mule.png"));
        m.put("minecraft:skeleton_horse", Identifier.of("craftics", "textures/mob_heads/skeleton_horse.png"));
        m.put("minecraft:zombie_horse", Identifier.of("craftics", "textures/mob_heads/zombie_horse.png"));
        m.put("minecraft:llama", Identifier.of("craftics", "textures/mob_heads/llama.png"));
        m.put("minecraft:parrot", Identifier.of("craftics", "textures/mob_heads/parrot.png"));
        TEXTURES = Collections.unmodifiableMap(m);
    }

    public static Identifier get(String entityTypeId) {
        return TEXTURES.get(entityTypeId);
    }

    public static void drawMobHead(net.minecraft.client.gui.DrawContext ctx, Identifier texture, int x, int y, int size) {
        if (size == 16) {
            ctx.drawTexture(texture, x, y, 0f, 0f, 16, 16, 16, 16);
        } else {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 0);
            float s = size / 16.0f;
            ctx.getMatrices().scale(s, s, 1.0f);
            ctx.drawTexture(texture, 0, 0, 0f, 0f, 16, 16, 16, 16);
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
            default -> id.substring(0, 1).toUpperCase();
        };
    }

    private MobHeadTextures() {}
}
