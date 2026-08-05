package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.world.CrafticsSavedData;
import com.crackedgames.craftics.mixin.DisplayEntityInvoker;
import com.crackedgames.craftics.mixin.TextDisplayInvoker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * The kiosk's presence: a floating label over every registered lootbox chest, its colour
 * cycling, and a steady drift of themed particles around it.
 *
 * <p>A lootbox is an admin-placed chest sitting in a hub full of other chests. Without a
 * label it is indistinguishable from storage, and a player has no way to know it exists, what
 * it costs, or that the odds are a command away. The hologram carries all three, so the kiosk
 * explains itself from across the room.
 *
 * <p>Everything here is rebuilt from the registration in {@link CrafticsSavedData}, never
 * persisted: the label entities are spawned as needed and re-derived on restart. Display
 * entities are tagged so a reload can find and clear its own leftovers rather than stacking a
 * second label on every chest.
 */
public final class LootboxPresentation {

    private LootboxPresentation() {}

    /** Scoreboard tag marking our display entities, so a rebuild can clear the old ones. */
    private static final String TAG = "craftics_lootbox_label";

    /** How often the kiosks are swept for missing labels, in ticks. */
    private static final int REFRESH_INTERVAL = 40;
    /** How often the label colour advances and particles puff, in ticks. */
    private static final int PULSE_INTERVAL = 4;
    /** Only kiosks within this radius of a player are animated - an empty hub costs nothing. */
    private static final double ACTIVE_RADIUS = 32.0;

    /**
     * The colour cycle each box flashes through. Two shades per type: the label lerps between
     * them so it reads as a pulse rather than a strobe, which is legible for far longer.
     */
    private static int[] cycleFor(LootboxManager.Type type) {
        return switch (type) {
            case WEAPONS -> new int[]{0xFFAA00, 0xFFE066};
            case ARMOR -> new int[]{0x55DDFF, 0xBFF3FF};
            case MATERIALS -> new int[]{0x55DD55, 0xC8F5C8};
            case SPECIAL -> new int[]{0xFF55FF, 0xFFC2FF};
            case BOOKS -> new int[]{0xAA55FF, 0xDCC2FF};
        };
    }

    private static long tickCounter = 0;

    /** Driven from the same aggregate server tick that closes chest lids. */
    public static void tick(MinecraftServer server) {
        tickCounter++;
        boolean refresh = tickCounter % REFRESH_INTERVAL == 0;
        boolean pulse = tickCounter % PULSE_INTERVAL == 0;
        if (!refresh && !pulse) return;

        for (ServerWorld world : server.getWorlds()) {
            CrafticsSavedData data = CrafticsSavedData.get(world);
            Map<BlockPos, String> chests = data.getLootboxChestsIn(world);
            if (chests.isEmpty()) continue;

            for (Map.Entry<BlockPos, String> entry : chests.entrySet()) {
                BlockPos pos = entry.getKey();
                // Only bother with kiosks somebody is near - a hub nobody is standing in
                // shouldn't be spending tick time animating labels at itself.
                if (!playerNear(world, pos)) continue;

                String value = entry.getValue();
                int comma = value.indexOf(',');
                LootboxManager.Type type = LootboxManager.Type.byName(
                    comma < 0 ? value : value.substring(0, comma));
                if (type == null) continue;
                int cost = type.emeraldCost;
                if (comma >= 0) {
                    try {
                        cost = Integer.parseInt(value.substring(comma + 1));
                    } catch (NumberFormatException ignored) {}
                }

                if (refresh) ensureLabel(world, pos, type, cost);
                if (pulse) {
                    pulseLabel(world, pos, type, cost);
                    spawnAmbient(world, pos, type);
                }
            }
        }
    }

    private static boolean playerNear(ServerWorld world, BlockPos pos) {
        return world.getClosestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            ACTIVE_RADIUS, false) != null;
    }

    /** The label entity above this chest, or null when it hasn't been spawned yet. */
    private static DisplayEntity.TextDisplayEntity findLabel(ServerWorld world, BlockPos pos) {
        Box box = new Box(pos).expand(0.6, 2.2, 0.6);
        List<DisplayEntity.TextDisplayEntity> found = world.getEntitiesByClass(
            DisplayEntity.TextDisplayEntity.class, box, e -> e.getCommandTags().contains(TAG));
        return found.isEmpty() ? null : found.get(0);
    }

    /** Spawn the floating label if it's missing (first placement, chunk reload, restart). */
    private static void ensureLabel(ServerWorld world, BlockPos pos,
                                    LootboxManager.Type type, int cost) {
        if (!world.getBlockState(pos).isOf(net.minecraft.block.Blocks.CHEST)) return;
        if (findLabel(world, pos) != null) return;

        DisplayEntity.TextDisplayEntity label =
            new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        label.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, 0f, 0f);
        label.addCommandTag(TAG);
        ((DisplayEntityInvoker) label).craftics$setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        ((TextDisplayInvoker) label).craftics$setLineWidth(220);
        applyText(label, type, cost, 0.0f);
        world.spawnEntity(label);
    }

    /** Advance the colour cycle on an existing label. */
    private static void pulseLabel(ServerWorld world, BlockPos pos,
                                   LootboxManager.Type type, int cost) {
        DisplayEntity.TextDisplayEntity label = findLabel(world, pos);
        if (label == null) return;
        // A slow triangle wave: 0 -> 1 -> 0 rather than a saw, so the colour eases back
        // instead of snapping at the loop point.
        float phase = (tickCounter % 80) / 80.0f;
        float blend = phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
        applyText(label, type, cost, blend);
    }

    /**
     * Write the label: title in the cycling colour, then the price and the odds command.
     *
     * <p>The whole text is rewritten each pulse rather than patched, because the vanilla text
     * getter is protected and only the setter is exposed through the invoker mixin. Rebuilding
     * three short lines every four ticks is cheaper than another mixin.
     */
    private static void applyText(DisplayEntity.TextDisplayEntity label,
                                  LootboxManager.Type type, int cost, float blend) {
        int[] cycle = cycleFor(type);
        int colour = lerpColor(cycle[0], cycle[1], blend);
        String priceLine = cost <= 0 ? "§aFree to open" : "§f" + cost + " §7emeralds §8or a Key";
        var text = Text.literal("§l" + type.display).styled(s -> s.withColor(colour))
            .append(Text.literal("\n" + priceLine))
            .append(Text.literal("\n§8/craftics lootbox odds " + type.name().toLowerCase()));
        ((TextDisplayInvoker) label).craftics$setText(text);
    }

    private static int lerpColor(int from, int to, float t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** A slow drift of type-coloured motes around the chest so it catches the eye at rest. */
    private static void spawnAmbient(ServerWorld world, BlockPos pos, LootboxManager.Type type) {
        int colour = cycleFor(type)[0];
        DustParticleEffect dust = dustOf(colour);
        double angle = (tickCounter % 120) / 120.0 * Math.PI * 2;
        double radius = 0.55;
        world.spawnParticles(dust,
            pos.getX() + 0.5 + Math.cos(angle) * radius,
            pos.getY() + 0.4 + ((tickCounter % 40) / 40.0) * 0.7,
            pos.getZ() + 0.5 + Math.sin(angle) * radius,
            1, 0.0, 0.0, 0.0, 0.0);
        // The counter-rotating mote keeps the ring reading as a ring rather than one orbiting dot.
        world.spawnParticles(dust,
            pos.getX() + 0.5 - Math.cos(angle) * radius,
            pos.getY() + 0.4 + ((tickCounter % 40) / 40.0) * 0.7,
            pos.getZ() + 0.5 - Math.sin(angle) * radius,
            1, 0.0, 0.0, 0.0, 0.0);
        if (tickCounter % 20 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                1, 0.12, 0.05, 0.12, 0.005);
        }
    }

    /** Coloured dust, built through the shard-appropriate constructor. */
    private static DustParticleEffect dustOf(int colour) {
        //? if <=1.21.1 {
        return new DustParticleEffect(
            new Vector3f(((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f,
                (colour & 0xFF) / 255f), 1.0f);
        //?} else {
        /*return new DustParticleEffect(colour, 1.0f);
        *///?}
    }

    /**
     * The burst played when a box is opened - louder than the ambient drift and coloured to the
     * box, so a Special Cache popping looks different from a Tome Cache across the room.
     */
    public static void openBurst(ServerWorld world, BlockPos pos, LootboxManager.Type type) {
        DustParticleEffect dust = dustOf(cycleFor(type)[1]);
        Vec3d centre = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        world.spawnParticles(dust, centre.x, centre.y, centre.z, 40, 0.5, 0.5, 0.5, 0.08);
        world.spawnParticles(ParticleTypes.FIREWORK, centre.x, centre.y, centre.z,
            24, 0.3, 0.4, 0.3, 0.12);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, centre.x, centre.y + 0.3, centre.z,
            18, 0.35, 0.45, 0.35, 0.14);
        world.spawnParticles(ParticleTypes.FLASH, centre.x, centre.y + 0.4, centre.z,
            1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Drop the label above {@code pos} (chest removed / re-registered). */
    public static void clearLabel(ServerWorld world, BlockPos pos) {
        DisplayEntity.TextDisplayEntity label = findLabel(world, pos);
        if (label != null) label.discard();
    }
}
