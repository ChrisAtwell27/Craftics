package com.crackedgames.craftics.client;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.network.CombatActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public class CombatInputHandler {

    private static boolean lastLeftClick = false;
    private static long lastHoverBroadcastTime = 0;
    private static GridPos lastBroadcastedHover = null;

    // Middle mouse pan state
    private static boolean middleMouseDown = false;
    private static double panStartX = 0, panStartY = 0;

    // Right mouse camera-orbit state
    private static boolean rightMouseDown = false;
    private static double orbitStartX = 0, orbitStartY = 0;
    private static final float ORBIT_YAW_SENSITIVITY = 0.35f;
    private static final float ORBIT_PITCH_SENSITIVITY = 0.25f;

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
        if (held == Items.BOW || held == Items.CROSSBOW || held == Items.TRIDENT) return ActionMode.RANGED_ATTACK;
        if (SWORDS.contains(held) || AXES.contains(held) || SPEARS.contains(held)
            || held == Items.MACE)
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

        // --- Right mouse drag: orbit the camera (yaw + pitch) ---
        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (rightDown) {
            if (rightMouseDown) {
                double dx = mouseX - orbitStartX;
                double dy = mouseY - orbitStartY;
                CombatState.adjustCameraAngles(
                    (float) (dx * ORBIT_YAW_SENSITIVITY),
                    (float) (dy * ORBIT_PITCH_SENSITIVITY)
                );
            }
            orbitStartX = mouseX;
            orbitStartY = mouseY;
            rightMouseDown = true;
        } else {
            rightMouseDown = false;
        }

        // Detect left click (rising edge) — only when NOT panning
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = leftDown && !lastLeftClick;
        lastLeftClick = leftDown;

        // Update hover tile locally (no server round-trip)
        GridPos hoverPos = TileRaycast.getGridPosUnderCursor();
        CombatState.setHoveredTile(hoverPos);
        if (hoverPos != null) {
            Integer entityId = CombatState.getEnemyGridMap().get(hoverPos);
            CombatState.setHoveredEnemyId(entityId != null ? entityId : -1);
        } else {
            CombatState.setHoveredEnemyId(-1);
        }

        // Throttled hover broadcast for teammates
        long now = System.currentTimeMillis();
        if (hoverPos != null && (lastHoverBroadcastTime == 0 || now - lastHoverBroadcastTime > 200)) {
            if (!java.util.Objects.equals(hoverPos, lastBroadcastedHover)) {
                ClientPlayNetworking.send(
                    new com.crackedgames.craftics.network.HoverUpdatePayload(hoverPos.x(), hoverPos.z())
                );
                lastBroadcastedHover = hoverPos;
                lastHoverBroadcastTime = now;
            }
        }

        if (clicked) {
            handleClick(client, getActionMode(client));
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
                Integer mappedId = CombatState.getEnemyGridMap().get(tilePos);
                int entityId = mappedId != null ? mappedId : -1;
                if (entityId != -1) {
                    ClientPlayNetworking.send(new CombatActionPayload(
                        CombatActionPayload.ACTION_ATTACK, tilePos.x(), tilePos.z(), entityId
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

    public static void sendEndTurn() {
        ClientPlayNetworking.send(new CombatActionPayload(
            CombatActionPayload.ACTION_END_TURN, 0, 0, 0
        ));
    }
}
