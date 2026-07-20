package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import com.crackedgames.craftics.client.DamageTypePanel;
import com.crackedgames.craftics.client.StatsPanel;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes clicks on the inventory stat/affinity panels' minimize buttons.
 *
 * <p>Targets {@link HandledScreen} (with an instanceof guard), NOT
 * {@link InventoryScreen}: InventoryScreen only declares its own
 * {@code mouseClicked} override on 1.21.1. On 1.21.3+ the method lives in the
 * parent class, so Loom's static mixin remapper couldn't resolve the name
 * there and shipped it unremapped - which crashed every production launch on
 * those versions with "could not find any targets matching 'mouseClicked'".
 * HandledScreen overrides mouseClicked on every shard we build.
 */
@Mixin(HandledScreen.class)
public class InventoryClickMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void craftics$panelClick(double mouseX, double mouseY, int button,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen screen)) return;
        if (!CombatState.isStatsOverlayVisible()) return;
        if (button != 0) return; // left-click only
        int sw = screen.width, sh = screen.height;

        int[] sb = StatsPanel.buttonRect(sw, sh);
        if (hit(mouseX, mouseY, sb)) {
            CombatState.toggleStatsPanelCollapsed();
            cir.setReturnValue(true);
            return;
        }
        int[] ab = DamageTypePanel.buttonRect(sw, sh);
        if (hit(mouseX, mouseY, ab)) {
            CombatState.toggleAffinityPanelCollapsed();
            cir.setReturnValue(true);
        }
    }

    private static boolean hit(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
