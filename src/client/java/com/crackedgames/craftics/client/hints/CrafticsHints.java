package com.crackedgames.craftics.client.hints;

import com.crackedgames.craftics.block.ModBlocks;
import com.crackedgames.craftics.client.CombatState;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class CrafticsHints {

    private CrafticsHints() {}

    /** Search radius (in blocks) for the nearest Level Select Block in the hub. */
    private static final int LEVEL_SELECT_SCAN_RADIUS = 16;

    public static void registerAll(HintManager mgr) {
        mgr.register(new Hint(
            "hub.level_select_arrow",
            ctx -> ctx.inHub() && nearestLevelSelectBlock(ctx) != null,
            3,
            new HintPresenter.WorldArrow(BlockPos.ORIGIN), // target resolved dynamically by HintArrowRenderer
            Hint.Priority.LOW,
            Hint.Mode.RECURRING
        ));

        mgr.register(new Hint(
            "combat.move_no_feather",
            ctx -> ctx.inCombat() && ctx.isPlayerTurn()
                && !ctx.tookActionThisTurn()
                && ctx.apRemaining() == ctx.apMax()
                && ctx.speedRemaining() == ctx.speedMax()
                && !ctx.holdingMoveFeather(),
            8,
            new HintPresenter.HudPopup(Text.literal("Hold the Move feather to walk"), 240),
            Hint.Priority.MEDIUM,
            Hint.Mode.RECURRING
        ));

        mgr.register(new Hint(
            "combat.move_with_feather",
            ctx -> ctx.inCombat() && ctx.isPlayerTurn()
                && !ctx.tookActionThisTurn()
                && ctx.apRemaining() == ctx.apMax()
                && ctx.speedRemaining() == ctx.speedMax()
                && ctx.holdingMoveFeather(),
            8,
            new HintPresenter.HudPopup(Text.literal("Click a highlighted tile to move there"), 240),
            Hint.Priority.MEDIUM,
            Hint.Mode.RECURRING
        ));

        mgr.register(new Hint(
            "combat.end_turn",
            ctx -> ctx.inCombat() && ctx.isPlayerTurn() && ctx.tookActionThisTurn(),
            15,
            new HintPresenter.HudPopup(Text.literal("Press R to end your turn"), 240),
            Hint.Priority.MEDIUM,
            Hint.Mode.RECURRING
        ));

        mgr.register(new Hint(
            "combat.heal_low_hp",
            ctx -> ctx.inCombat() && ctx.isPlayerTurn()
                && ctx.hpCurrent() <= ctx.hpMax() * 0.25
                && ctx.hasFoodOrPotionInHotbar(),
            5,
            new HintPresenter.HudPopup(Text.literal("Hold a food or potion and click the grid to use it"), 240),
            Hint.Priority.HIGH,
            Hint.Mode.RECURRING
        ));

        mgr.register(new Hint(
            "combat.first_combat",
            ctx -> ctx.inCombat() && ctx.isPlayerTurn() && ctx.turnNumber() == 1,
            0,
            new HintPresenter.HudPopup(
                Text.literal("Hold the Move feather and click a tile to move"), 240),
            Hint.Priority.HIGH,
            Hint.Mode.ONE_SHOT_PERSISTENT
        ));
    }

    /** Build a HintContext snapshot from the current client state. Called once per tick. */
    public static HintContext snapshot(MinecraftClient client, long lastInputAtMs) {
        ClientPlayerEntity player = client.player;
        boolean inCombat = CombatState.isInCombat();
        boolean inHub = !inCombat;
        boolean isPlayerTurn = inCombat && CombatState.isPlayerTurn();
        boolean isAnyScreenOpen = client.currentScreen != null;

        boolean holdingFeather = player != null && isMoveFeather(player.getMainHandStack());
        boolean hasFoodOrPotion = player != null && hotbarHasFoodOrPotion(player.getInventory());

        return new HintContext(
            client, player,
            inHub, inCombat, isPlayerTurn,
            CombatState.getApRemaining(), CombatState.getMaxAp(),
            CombatState.getMovePointsRemaining(), CombatState.getMaxSpeed(),
            CombatState.getPlayerHp(), CombatState.getPlayerMaxHp(),
            CombatState.getTurnNumber(),
            HintManager.get().tookActionThisTurn(),
            holdingFeather,
            hasFoodOrPotion,
            isAnyScreenOpen,
            System.currentTimeMillis(),
            lastInputAtMs
        );
    }

    /** Mirrors the server-side check in ItemUseHandler.isMoveFeather. */
    public static boolean isMoveFeather(ItemStack s) {
        return s.getItem() == Items.FEATHER && s.contains(DataComponentTypes.CUSTOM_NAME);
    }

    private static boolean hotbarHasFoodOrPotion(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            Item it = s.getItem();
            if (s.get(DataComponentTypes.FOOD) != null) return true;
            if (it == Items.POTION || it == Items.SPLASH_POTION || it == Items.LINGERING_POTION) return true;
        }
        return false;
    }

    /** Scan a small AABB around the player for the Level Select Block. */
    public static BlockPos nearestLevelSelectBlock(HintContext ctx) {
        ClientPlayerEntity p = ctx.player();
        if (p == null) return null;
        World w = p.getEntityWorld();
        if (w == null) return null;
        BlockPos origin = p.getBlockPos();
        BlockPos.Mutable cur = new BlockPos.Mutable();
        BlockPos best = null;
        int bestDistSq = Integer.MAX_VALUE;
        for (int dx = -LEVEL_SELECT_SCAN_RADIUS; dx <= LEVEL_SELECT_SCAN_RADIUS; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -LEVEL_SELECT_SCAN_RADIUS; dz <= LEVEL_SELECT_SCAN_RADIUS; dz++) {
                    cur.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState bs = w.getBlockState(cur);
                    if (bs.getBlock() == ModBlocks.LEVEL_SELECT_BLOCK) {
                        int d = dx * dx + dy * dy + dz * dz;
                        if (d < bestDistSq) {
                            bestDistSq = d;
                            best = cur.toImmutable();
                        }
                    }
                }
            }
        }
        return best;
    }
}
