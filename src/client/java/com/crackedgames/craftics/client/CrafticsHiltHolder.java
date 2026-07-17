package com.crackedgames.craftics.client;

/**
 * Duck interface implemented by the 1.21.5+ {@code ItemRenderState} mixin so the
 * "this stack is Hilt-enchanted" bit can be ferried from where the render state is
 * built (which sees the {@code ItemStack}) to where it is drawn (which does not).
 *
 * <p>The 1.21.4 render-state refactor split item rendering into a build step
 * ({@code ItemModelManager.clearAndUpdate}, which has the stack) and a draw step
 * ({@code ItemRenderState.render}, which only has the display context and baked
 * layers). The GUI flip needs the Hilt flag at draw time, so it is stashed on the
 * render state here, mirroring how {@code CrafticsAnimHolder} ferries anim state
 * onto {@code LivingEntityRenderState}.
 *
 * <p>Unused on 1.21.1, where {@code ItemRenderer.renderItem} still receives the
 * {@code ItemStack} directly and detection happens inline.
 */
public interface CrafticsHiltHolder {
    boolean craftics$isHiltState();
    void craftics$setHiltState(boolean hilt);
}
