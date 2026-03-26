package com.crackedgames.craftics.client;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.network.CombatActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

import java.util.List;

public class CombatInputHandler {

    private static boolean lastLeftClick = false;
    private static boolean lastRKey = false;
    private static int lastMode = -1; // track for action bar display
    private static GridPos lastHoveredTile = null; // track for hover highlight updates

    // Middle mouse pan state
    private static boolean middleMouseDown = false;
    private static double panStartX = 0, panStartY = 0;

    public enum ActionMode { MOVE, MELEE_ATTACK, RANGED_ATTACK, USE_ITEM }

    // Food items for client-side detection (must match ItemUseHandler.FOOD_HEAL on server)
    private static final Set<Item> FOODS = Set.of(
        Items.APPLE, Items.BREAD, Items.COOKED_BEEF, Items.COOKED_PORKCHOP,
        Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_COD, Items.COOKED_SALMON,
        Items.BAKED_POTATO, Items.COOKIE, Items.PUMPKIN_PIE, Items.MELON_SLICE,
        Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.GOLDEN_CARROT, Items.GOLDEN_APPLE,
        Items.ENCHANTED_GOLDEN_APPLE, Items.HONEY_BOTTLE, Items.SUSPICIOUS_STEW,
        Items.CHORUS_FRUIT, Items.BEEF, Items.PORKCHOP, Items.CHICKEN,
        Items.MUTTON, Items.COD, Items.SALMON, Items.RABBIT, Items.COOKED_RABBIT,
        Items.TROPICAL_FISH, Items.POTATO, Items.POISONOUS_POTATO, Items.CARROT,
        Items.BEETROOT, Items.DRIED_KELP, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP,
        Items.RABBIT_STEW, Items.SPIDER_EYE, Items.ROTTEN_FLESH, Items.PUFFERFISH
    );

    private static final Set<Item> SWORDS = Set.of(
        Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
        Items.DIAMOND_SWORD, Items.GOLDEN_SWORD, Items.NETHERITE_SWORD
    );

    private static final Set<Item> AXES = Set.of(
        Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE,
        Items.DIAMOND_AXE, Items.GOLDEN_AXE, Items.NETHERITE_AXE
    );

    private static final Set<Item> SPEARS = Set.of(
        // Spears not available in 1.21.1
    );

    private static final Set<Item> USE_ITEMS = Set.of(
        Items.POTION, Items.SPLASH_POTION, Items.SNOWBALL, Items.EGG,
        Items.ENDER_PEARL, Items.TNT, Items.FISHING_ROD, Items.FIRE_CHARGE,
        Items.WIND_CHARGE
    );

    public static ActionMode getActionMode(MinecraftClient client) {
        if (client.player == null) return ActionMode.MOVE;
        Item held = client.player.getMainHandStack().getItem();

        if (held == Items.FEATHER) return ActionMode.MOVE;
        if (held == Items.BOW || held == Items.CROSSBOW) return ActionMode.RANGED_ATTACK;
        if (SWORDS.contains(held) || AXES.contains(held) || SPEARS.contains(held)
            || held == Items.MACE || held == Items.TRIDENT)
            return ActionMode.MELEE_ATTACK;
        if (FOODS.contains(held) || USE_ITEMS.contains(held)
            || com.crackedgames.craftics.combat.ItemUseHandler.isUsableItem(held))
            return ActionMode.USE_ITEM;
        // Empty hand / anything else = melee attack (fists)
        return ActionMode.MELEE_ATTACK;
    }

    public static void tick(MinecraftClient client) {
        if (!CombatState.isInCombat()) return;
        if (client.currentScreen != null) return;

        long window = client.getWindow().getHandle();

        // --- Middle mouse pan ---
        boolean middleDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        double[] mouseXArr = new double[1], mouseYArr = new double[1];
        GLFW.glfwGetCursorPos(window, mouseXArr, mouseYArr);
        double mouseX = mouseXArr[0], mouseY = mouseYArr[0];

        if (middleDown) {
            if (middleMouseDown) {
                // Dragging — compute delta and pan
                double dx = (mouseX - panStartX) * 0.02;
                double dz = (mouseY - panStartY) * 0.02;
                // Convert screen delta to world-space pan (account for camera yaw)
                double yawRad = Math.toRadians(CombatState.getCombatYaw());
                double worldDx = dx * Math.cos(yawRad) - dz * Math.sin(yawRad);
                double worldDz = dx * Math.sin(yawRad) + dz * Math.cos(yawRad);
                CombatState.pan(worldDx, worldDz);
            }
            panStartX = mouseX;
            panStartY = mouseY;
            middleMouseDown = true;
        } else {
            middleMouseDown = false;
        }

        // --- Double-click middle mouse to reset pan ---
        // (holding Shift + middle click resets)
        if (middleDown && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
            CombatState.resetPan();
        }

        // Detect left click (rising edge) — only when NOT panning
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = leftDown && !lastLeftClick;
        lastLeftClick = leftDown;

        // Detect R key (end turn)
        boolean rDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        if (rDown && !lastRKey) {
            sendEndTurn();
        }
        lastRKey = rDown;

        // Show current mode on action bar when hotbar selection changes
        ActionMode mode = getActionMode(client);
        if (mode.ordinal() != lastMode) {
            lastMode = mode.ordinal();
            if (client.player != null) {
                String label = switch (mode) {
                    case MOVE -> "\u00a7aMove Mode";
                    case MELEE_ATTACK -> "\u00a7cMelee Attack";
                    case RANGED_ATTACK -> "\u00a76Ranged Attack";
                    case USE_ITEM -> "\u00a7dUse Item";
                };
                client.player.sendMessage(net.minecraft.text.Text.literal(label), true);
            }
        }

        // Send hover updates when the tile under cursor changes
        GridPos currentHover = TileRaycast.getGridPosUnderCursor();
        if (!java.util.Objects.equals(currentHover, lastHoveredTile)) {
            lastHoveredTile = currentHover;
            int hx = currentHover != null ? currentHover.x() : -1;
            int hz = currentHover != null ? currentHover.z() : -1;
            ClientPlayNetworking.send(new CombatActionPayload(
                CombatActionPayload.ACTION_HOVER, hx, hz, 0
            ));

            // Track hovered enemy for inspect panel
            if (currentHover != null) {
                CombatState.setHoveredEnemyId(findEnemyAtGridPos(client, currentHover));
            } else {
                CombatState.setHoveredEnemyId(-1);
            }
        }

        if (clicked) {
            handleClick(client, mode);
        }
    }

    private static void handleClick(MinecraftClient client, ActionMode mode) {
        GridPos tilePos = TileRaycast.getGridPosUnderCursor();
        if (tilePos == null) return;

        switch (mode) {
            case MOVE -> {
                ClientPlayNetworking.send(new CombatActionPayload(
                    CombatActionPayload.ACTION_MOVE, tilePos.x(), tilePos.z(), 0
                ));
            }
            case MELEE_ATTACK, RANGED_ATTACK -> {
                int entityId = findEnemyAtGridPos(client, tilePos);
                if (entityId != -1) {
                    ClientPlayNetworking.send(new CombatActionPayload(
                        CombatActionPayload.ACTION_ATTACK, 0, 0, entityId
                    ));
                } else if (client.player != null) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("\u00a7cNo enemy on that tile!"), false
                    );
                }
            }
            case USE_ITEM -> {
                // For food/potions, target doesn't matter. For throwables, it does.
                ClientPlayNetworking.send(new CombatActionPayload(
                    CombatActionPayload.ACTION_USE_ITEM, tilePos.x(), tilePos.z(), 0
                ));
            }
        }
    }

    private static int findEnemyAtGridPos(MinecraftClient client, GridPos gridPos) {
        if (client.world == null) return -1;

        double worldX = CombatState.getArenaOriginX() + gridPos.x() + 0.5;
        double worldZ = CombatState.getArenaOriginZ() + gridPos.z() + 0.5;
        double worldY = CombatState.getArenaCenterY();

        // Search a generous area around the tile for any mob entity
        Box searchBox = new Box(worldX - 1.5, worldY - 2.0, worldZ - 1.5,
                                worldX + 1.5, worldY + 4.0, worldZ + 1.5);

        List<Entity> entities = client.world.getOtherEntities(client.player, searchBox);

        // Filter to known alive enemies from sync data
        java.util.Map<Integer, int[]> aliveEnemies = CombatState.getEnemyHpMap();

        for (Entity entity : entities) {
            if (entity instanceof net.minecraft.entity.mob.MobEntity
                    && aliveEnemies.containsKey(entity.getId())) {
                return entity.getId();
            }
        }

        // Fallback: find the closest synced enemy entity within 2 tiles of the click
        int bestId = -1;
        double bestDist = Double.MAX_VALUE;
        for (int entityId : aliveEnemies.keySet()) {
            Entity entity = client.world.getEntityById(entityId);
            if (entity == null || !entity.isAlive()) continue;
            double dx = entity.getX() - worldX;
            double dz = entity.getZ() - worldZ;
            double dist = dx * dx + dz * dz;
            if (dist < bestDist && dist < 4.0) { // within ~2 blocks
                bestDist = dist;
                bestId = entityId;
            }
        }
        return bestId;
    }

    public static void sendEndTurn() {
        ClientPlayNetworking.send(new CombatActionPayload(
            CombatActionPayload.ACTION_END_TURN, 0, 0, 0
        ));
    }
}
