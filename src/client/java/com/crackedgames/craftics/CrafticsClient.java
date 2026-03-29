package com.crackedgames.craftics;

import com.crackedgames.craftics.block.ModScreenHandlers;
import com.crackedgames.craftics.client.CombatHudOverlay;
import com.crackedgames.craftics.client.CombatInputHandler;
import com.crackedgames.craftics.client.CombatTooltips;
import com.crackedgames.craftics.client.CombatState;
import com.crackedgames.craftics.client.TileOverlayRenderer;
import com.crackedgames.craftics.client.CombatAnimations;
import com.crackedgames.craftics.client.CombatVisualEffects;
import com.crackedgames.craftics.client.LevelSelectScreen;
import com.crackedgames.craftics.client.TransitionOverlay;
import com.crackedgames.craftics.network.CombatSyncPayload;
import com.crackedgames.craftics.network.EnterCombatPayload;
import com.crackedgames.craftics.network.ExitCombatPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CrafticsClient implements ClientModInitializer {

    private static KeyBinding combatToggleKey;
    private static KeyBinding guideBookKey;
    private static boolean traderScreenOpened = false;
    private static boolean previousBobView = true;
    private static double previousChatScale = 1.0;
    private static double previousChatWidth = 1.0;

    @Override
    public void onInitializeClient() {
        CrafticsMod.LOGGER.info("Craftics client initializing...");

        HandledScreens.register(ModScreenHandlers.LEVEL_SELECT_SCREEN_HANDLER, LevelSelectScreen::new);

        // Register guide book screen opener (called from GuideBookItem.use() on client)
        com.crackedgames.craftics.item.GuideBookItem.openScreenAction = () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(
                new com.crackedgames.craftics.client.guide.GuideBookScreen()
            );
        };

        // --- Network handlers ---

        ClientPlayNetworking.registerGlobalReceiver(EnterCombatPayload.ID, (payload, context) -> {
            CombatState.enterCombat(
                payload.originX(), payload.originY(), payload.originZ(),
                payload.width(), payload.height()
            );
            // Apply custom camera yaw from diamond marker (if set)
            if (payload.cameraYaw() >= 0) {
                CombatState.setCombatYaw(payload.cameraYaw());
            } else {
                CombatState.setCombatYaw(225.0f); // Default SW-facing
            }
            previousBobView = context.client().options.getBobView().getValue();
            context.client().options.getBobView().setValue(false);
            // Set compact chat for combat messages
            previousChatScale = context.client().options.getChatScale().getValue();
            previousChatWidth = context.client().options.getChatWidth().getValue();
            context.client().options.getChatScale().setValue(0.33);
            context.client().options.getChatWidth().setValue(0.39); // ~125px at default GUI scale
            context.client().options.setPerspective(Perspective.THIRD_PERSON_BACK);
            context.client().mouse.unlockCursor();
            TransitionOverlay.startFadeOut();
            CrafticsMod.LOGGER.info("Entered combat mode");
        });

        // Combat state sync
        ClientPlayNetworking.registerGlobalReceiver(CombatSyncPayload.ID, (payload, context) -> {
            CombatState.updateFromSync(
                payload.phase(), payload.ap(), payload.movePoints(),
                payload.playerHp(), payload.playerMaxHp(), payload.turnNumber(),
                payload.maxAp(), payload.maxSpeed(),
                payload.enemyData(), payload.enemyTypeIds(),
                payload.playerEffects(), payload.killStreak()
            );
        });

        // Combat events (damage, death, phase changes) — trigger visual effects
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.CombatEventPayload.ID, (payload, context) -> {
                switch (payload.eventType()) {
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_DAMAGED -> {
                        // Focus camera on the damaged entity
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0) {
                            CombatState.focusOnTile(payload.targetX(), payload.targetZ());
                        }
                        if (payload.valueA() == 0) {
                            // Animation trigger event (0 damage) — start the attack animation
                            if (context.client().player != null) {
                                CombatAnimations.playWeaponAttack(context.client().player);
                            }
                        } else {
                            // Real damage event — show impact visuals immediately
                            // (server already delayed this to match animation timing)
                            CombatVisualEffects.spawnDamageNumberAtEntity(
                                payload.entityId(), payload.valueA(), false);
                            CombatVisualEffects.flashAttack();
                            float shakeAmount = Math.min(1.0f, payload.valueA() / 8.0f) * 0.6f + 0.15f;
                            CombatVisualEffects.triggerShake(shakeAmount);
                        }
                    }
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_MOVED -> {
                        // Focus camera on moving entity
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0) {
                            CombatState.focusOnTile(payload.targetX(), payload.targetZ());
                        }
                    }
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_DIED -> {
                        CombatVisualEffects.spawnDeathTextAtEntity(
                            payload.entityId(), "Enemy");
                    }
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_MOB_ATTACK_ANIM -> {
                        // Focus camera on attacker
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0) {
                            CombatState.focusOnTile(payload.targetX(), payload.targetZ());
                        }
                        // Spawn attack particles at the mob's position
                        var entity = context.client().world != null
                            ? context.client().world.getEntityById(payload.entityId()) : null;
                        if (entity != null) {
                            // Sweep/slash particles near the mob (attack effect)
                            for (int i = 0; i < 6; i++) {
                                double ox = (Math.random() - 0.5) * 1.2;
                                double oy = Math.random() * 1.5;
                                double oz = (Math.random() - 0.5) * 1.2;
                                context.client().world.addParticle(
                                    net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                                    entity.getX() + ox, entity.getY() + oy, entity.getZ() + oz,
                                    0, 0, 0);
                            }
                            // Knockback attacks: extra ground impact particles
                            if (payload.valueA() == 1) {
                                for (int i = 0; i < 4; i++) {
                                    context.client().world.addParticle(
                                        net.minecraft.particle.ParticleTypes.CRIT,
                                        entity.getX() + (Math.random() - 0.5) * 0.8,
                                        entity.getY() + 0.2,
                                        entity.getZ() + (Math.random() - 0.5) * 0.8,
                                        (Math.random() - 0.5) * 0.3, 0.2, (Math.random() - 0.5) * 0.3);
                                }
                            }
                        }
                    }
                }
            }
        );

        // Server tells us to exit combat mode
        ClientPlayNetworking.registerGlobalReceiver(ExitCombatPayload.ID, (payload, context) -> {
            CombatState.setInCombat(false);
            context.client().options.getBobView().setValue(previousBobView);
            context.client().options.getChatScale().setValue(previousChatScale);
            context.client().options.getChatWidth().setValue(previousChatWidth);
            context.client().options.setPerspective(Perspective.FIRST_PERSON);
            context.client().mouse.lockCursor();
            TransitionOverlay.startFadeOut();
            CrafticsMod.LOGGER.info("Exited combat mode (won: {})", payload.won());
        });

        // Victory choice screen (Go Home vs Continue)
        // Stay in isometric view — don't exit combat mode yet
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.VictoryChoicePayload.ID, (payload, context) -> {
                // Unlock cursor for the choice screen but keep isometric camera
                context.client().mouse.unlockCursor();
                TransitionOverlay.startFadeOut();
                // Show the choice screen (overlays the arena view)
                context.client().setScreen(new com.crackedgames.craftics.client.VictoryChoiceScreen(
                    payload.emeraldsEarned(), payload.totalEmeralds(),
                    payload.biomeName(), payload.levelIndex()
                ));
                CombatState.setEmeralds(payload.totalEmeralds());
            }
        );

        // Player stats sync (for inventory display)
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.PlayerStatsSyncPayload.ID, (payload, context) -> {
                CombatState.updateStats(payload.playerLevel(), payload.unspentPoints(), payload.statData(), payload.affinityData());
                CombatState.setEmeralds(payload.emeralds());
            }
        );

        // Level-up screen (after biome boss completion)
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.LevelUpPayload.ID, (payload, context) -> {
                context.client().setScreen(new com.crackedgames.craftics.client.LevelUpScreen(
                    payload.playerLevel(), payload.unspentPoints(), payload.statData()
                ));
            }
        );

        // Trader signal — real wandering trader spawned, track for auto-done on screen close
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.TraderOfferPayload.ID, (payload, context) -> {
                CombatState.setTraderActive(true);
                CombatState.setEmeralds(payload.playerEmeralds());
                TransitionOverlay.startFadeOut();
            }
        );

        // Event room screen (shrine, traveler, vault)
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.EventRoomPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    context.client().setScreen(new com.crackedgames.craftics.client.EventRoomScreen(
                        payload.eventType(), payload.eventData()
                    ));
                });
            }
        );

        // Tile set sync (move/attack/danger overlays)
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.TileSetPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    CombatState.updateTileSets(payload.moveTiles(), payload.attackTiles(),
                        payload.dangerTiles(), payload.warningTiles(), payload.enemyMap(), payload.enemyTypes());
                });
            });

        // Teammate hover relay
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.TeammateHoverPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    CombatState.updateTeammateHover(payload.playerUuid(), payload.playerName(),
                        payload.gridX(), payload.gridZ());
                });
            });

        // --- Keybinds (F6 debug only) ---

        combatToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.toggle_combat",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6,
            "key.categories.misc"
        ));

        guideBookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.guide_book",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.misc"
        ));

        // --- Player combat animations (PlayerAnimator) ---
        CombatAnimations.register();

        // --- Renderers ---
        HudRenderCallback.EVENT.register(new CombatHudOverlay());
        HudRenderCallback.EVENT.register(TransitionOverlay::render);
        CombatTooltips.register();
        TileOverlayRenderer.register();

        // --- Client tick ---

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // F6: debug toggle
            while (combatToggleKey.wasPressed()) {
                CombatState.toggleCombat();
                CrafticsMod.LOGGER.info("Combat mode: {}", CombatState.isInCombat() ? "ON" : "OFF");
                if (CombatState.isInCombat()) {
                    previousBobView = client.options.getBobView().getValue();
                    client.options.getBobView().setValue(false);
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    client.mouse.unlockCursor();
                } else {
                    client.options.getBobView().setValue(previousBobView);
                    client.options.setPerspective(Perspective.FIRST_PERSON);
                    client.mouse.lockCursor();
                }
            }

            // G key: open guide book
            while (guideBookKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new com.crackedgames.craftics.client.guide.GuideBookScreen());
                }
            }

            // Guide book item right-click: open screen when holding guide book
            if (client.player != null && client.currentScreen == null) {
                var held = client.player.getMainHandStack();
                if (held.getItem() instanceof com.crackedgames.craftics.item.GuideBookItem
                        && client.options.useKey.wasPressed()) {
                    client.setScreen(new com.crackedgames.craftics.client.guide.GuideBookScreen());
                }
            }

            // Trader done detection: when trader is active, track merchant screen open/close
            if (CombatState.isTraderActive()) {
                boolean isMerchantScreen = client.currentScreen instanceof
                    net.minecraft.client.gui.screen.ingame.MerchantScreen;
                if (isMerchantScreen) {
                    traderScreenOpened = true;
                } else if (traderScreenOpened && client.currentScreen == null) {
                    // Player opened and then closed the trading screen — signal done
                    traderScreenOpened = false;
                    CombatState.setTraderActive(false);
                    ClientPlayNetworking.send(new com.crackedgames.craftics.network.TraderDonePayload());
                }
            }

            // Tick transition overlay (fade in/out)
            TransitionOverlay.tick();

            // Tick visual effects (damage numbers, flashes)
            CombatVisualEffects.tick();

            // Tick camera focus lerp and player combat animations
            CombatState.tickCameraFocus();
            CombatAnimations.tick();

            // Combat input (mouse clicks, R for end turn, hotbar for mode)
            CombatInputHandler.tick(client);

            // Keep cursor unlocked during combat
            if (CombatState.isInCombat() && client.mouse.isCursorLocked()
                    && client.currentScreen == null) {
                client.mouse.unlockCursor();
            }
        });
    }
}
