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
import com.crackedgames.craftics.client.AchievementToast;
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

    private static final String KEYBIND_CATEGORY = "key.categories.craftics";

    private static KeyBinding combatToggleKey;
    private static KeyBinding guideBookKey;
    private static KeyBinding respecKey;
    private static KeyBinding endTurnKey;
    private static boolean traderScreenOpened = false;
    private static boolean previousBobView = true;
    private static double previousChatScale = 1.0;
    private static double previousChatWidth = 1.0;

    @Override
    public void onInitializeClient() {
        CrafticsMod.LOGGER.info("Craftics client initializing...");

        HandledScreens.register(ModScreenHandlers.LEVEL_SELECT_SCREEN_HANDLER, LevelSelectScreen::new);

        // GuideBookItem.use() fires server-side, this opens the screen client-side
        com.crackedgames.craftics.item.GuideBookItem.openScreenAction = () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(
                new com.crackedgames.craftics.client.guide.GuideBookScreen()
            );
        };

        ClientPlayNetworking.registerGlobalReceiver(EnterCombatPayload.ID, (payload, context) -> {
            CombatState.enterCombat(
                payload.originX(), payload.originY(), payload.originZ(),
                payload.width(), payload.height()
            );
            if (payload.cameraYaw() >= 0) {
                CombatState.setCombatYaw(payload.cameraYaw());
            } else {
                CombatState.setCombatYaw(225.0f);
            }
            previousBobView = context.client().options.getBobView().getValue();
            context.client().options.getBobView().setValue(false);
            // Shrink chat so it doesn't cover the arena
            previousChatScale = context.client().options.getChatScale().getValue();
            previousChatWidth = context.client().options.getChatWidth().getValue();
            context.client().options.getChatScale().setValue(0.33);
            context.client().options.getChatWidth().setValue(0.39);
            context.client().options.setPerspective(Perspective.THIRD_PERSON_BACK);
            context.client().mouse.unlockCursor();
            TransitionOverlay.startFadeOut();
            CrafticsMod.LOGGER.info("Entered combat mode");
        });

        ClientPlayNetworking.registerGlobalReceiver(CombatSyncPayload.ID, (payload, context) -> {
            CombatState.updateFromSync(
                payload.phase(), payload.ap(), payload.movePoints(),
                payload.playerHp(), payload.playerMaxHp(), payload.turnNumber(),
                payload.maxAp(), payload.maxSpeed(),
                payload.enemyData(), payload.enemyTypeIds(),
                payload.playerEffects(), payload.killStreak(),
                payload.partyHpData()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.CombatEventPayload.ID, (payload, context) -> {
                switch (payload.eventType()) {
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_DAMAGED -> {
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0) {
                            CombatState.focusOnTile(payload.targetX(), payload.targetZ());
                        }
                        if (payload.valueA() == 0) {
                            // 0 damage = animation-only event, valueB = attacker entity ID
                            int attackerEntityId = payload.valueB();
                            var attackerEntity = context.client().world != null
                                ? context.client().world.getEntityById(attackerEntityId) : null;
                            if (attackerEntity instanceof net.minecraft.client.network.AbstractClientPlayerEntity attacker) {
                                CombatAnimations.playWeaponAttack(attacker);
                            } else if (context.client().player != null
                                    && context.client().player.getId() == attackerEntityId) {
                                CombatAnimations.playWeaponAttack(context.client().player);
                            }
                        } else {
                            // Server delays this to sync with animation impact frame
                            CombatVisualEffects.spawnDamageNumberAtEntity(
                                payload.entityId(), payload.valueA(), false);
                            CombatVisualEffects.flashAttack();
                            float shakeAmount = Math.min(1.0f, payload.valueA() / 8.0f) * 0.6f + 0.15f;
                            CombatVisualEffects.triggerShake(shakeAmount);
                        }
                    }
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_MOVED -> {
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0) {
                            CombatState.focusOnTile(payload.targetX(), payload.targetZ());
                        }
                    }
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_DIED -> {
                        CombatVisualEffects.spawnDeathTextAtEntity(
                            payload.entityId(), "Enemy");
                    }
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_COMBAT_LOST -> {
                        CombatVisualEffects.startDeathOverlay(payload.valueA());
                    }
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_PLAYER_DOWNED -> {
                        CombatVisualEffects.flashDowned();
                        CombatVisualEffects.triggerShake(0.8f);
                    }
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_MOB_ATTACK_ANIM -> {
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0) {
                            CombatState.focusOnTile(payload.targetX(), payload.targetZ());
                        }
                        var entity = context.client().world != null
                            ? context.client().world.getEntityById(payload.entityId()) : null;
                        if (entity != null) {
                            for (int i = 0; i < 6; i++) {
                                double ox = (Math.random() - 0.5) * 1.2;
                                double oy = Math.random() * 1.5;
                                double oz = (Math.random() - 0.5) * 1.2;
                                context.client().world.addParticle(
                                    net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                                    entity.getX() + ox, entity.getY() + oy, entity.getZ() + oz,
                                    0, 0, 0);
                            }
                            // Extra ground impact particles for knockback attacks
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

        ClientPlayNetworking.registerGlobalReceiver(ExitCombatPayload.ID, (payload, context) -> {
            CombatState.setInCombat(false);
            CombatVisualEffects.resetOverlays();
            context.client().options.getBobView().setValue(previousBobView);
            context.client().options.getChatScale().setValue(previousChatScale);
            context.client().options.getChatWidth().setValue(previousChatWidth);
            context.client().options.setPerspective(Perspective.FIRST_PERSON);
            context.client().mouse.lockCursor();
            TransitionOverlay.startFadeOut();
            CrafticsMod.LOGGER.info("Exited combat mode (won: {})", payload.won());
        });

        // Stay in isometric view for the choice screen
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.VictoryChoicePayload.ID, (payload, context) -> {
                context.client().mouse.unlockCursor();
                TransitionOverlay.startFadeOut();
                context.client().setScreen(new com.crackedgames.craftics.client.VictoryChoiceScreen(
                    payload.emeraldsEarned(), payload.totalEmeralds(),
                    payload.biomeName(), payload.levelIndex()
                ));
                CombatState.setEmeralds(payload.totalEmeralds());
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.PlayerStatsSyncPayload.ID, (payload, context) -> {
                CombatState.updateStats(payload.playerLevel(), payload.unspentPoints(), payload.statData(), payload.affinityData());
                CombatState.setEmeralds(payload.emeralds());
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.AddonBonusSyncPayload.ID, (payload, context) -> {
                CombatState.updateAddonBonuses(payload.bonusData());
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.LevelUpPayload.ID, (payload, context) -> {
                context.client().setScreen(new com.crackedgames.craftics.client.LevelUpScreen(
                    payload.playerLevel(), payload.unspentPoints(), payload.statData()
                ));
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.TraderOfferPayload.ID, (payload, context) -> {
                CombatState.setTraderActive(true);
                CombatState.setEmeralds(payload.playerEmeralds());
                TransitionOverlay.startFadeOut();
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.EventRoomPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    context.client().setScreen(new com.crackedgames.craftics.client.EventRoomScreen(
                        payload.eventType(), payload.eventData()
                    ));
                });
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.TileSetPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    CombatState.updateTileSets(payload.moveTiles(), payload.attackTiles(),
                        payload.dangerTiles(), payload.warningTiles(), payload.enemyMap(), payload.enemyTypes());
                });
            });

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.TeammateHoverPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    CombatState.updateTeammateHover(payload.playerUuid(), payload.playerName(),
                        payload.gridX(), payload.gridZ());
                });
            });

        combatToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.toggle_combat",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6,
            KEYBIND_CATEGORY
        ));

        guideBookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.guide_book",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
            KEYBIND_CATEGORY
        ));

        respecKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.respec",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
            KEYBIND_CATEGORY
        ));

        endTurnKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.end_turn",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R,
            KEYBIND_CATEGORY
        ));

        CombatAnimations.register();

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.AchievementUnlockPayload.ID, (payload, context) -> {
                AchievementToast.enqueue(payload.displayName(), payload.description(), payload.categoryColor());
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.GuideBookSyncPayload.ID, (payload, context) -> {
                com.crackedgames.craftics.client.guide.GuideBookData.applySyncFromServer(payload.unlockedEntries());
            }
        );

        HudRenderCallback.EVENT.register(new CombatHudOverlay());
        HudRenderCallback.EVENT.register(TransitionOverlay::render);
        HudRenderCallback.EVENT.register(new AchievementToast());
        CombatTooltips.register();
        TileOverlayRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AchievementToast.tick();

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

            while (guideBookKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new com.crackedgames.craftics.client.guide.GuideBookScreen());
                }
            }

            while (respecKey.wasPressed()) {
                if (client.currentScreen == null && !CombatState.isInCombat()) {
                    client.setScreen(new com.crackedgames.craftics.client.RespecScreen());
                }
            }

            while (endTurnKey.wasPressed()) {
                if (CombatState.isInCombat() && client.currentScreen == null) {
                    CombatInputHandler.sendEndTurn();
                }
            }

            if (client.player != null && client.currentScreen == null) {
                var held = client.player.getMainHandStack();
                if (held.getItem() instanceof com.crackedgames.craftics.item.GuideBookItem
                        && client.options.useKey.wasPressed()) {
                    client.setScreen(new com.crackedgames.craftics.client.guide.GuideBookScreen());
                }
            }

            // When player closes the vanilla merchant screen, tell server we're done
            if (CombatState.isTraderActive()) {
                boolean isMerchantScreen = client.currentScreen instanceof
                    net.minecraft.client.gui.screen.ingame.MerchantScreen;
                if (isMerchantScreen) {
                    traderScreenOpened = true;
                } else if (traderScreenOpened && client.currentScreen == null) {
                    traderScreenOpened = false;
                    CombatState.setTraderActive(false);
                    ClientPlayNetworking.send(new com.crackedgames.craftics.network.TraderDonePayload());
                }
            }

            TransitionOverlay.tick();
            CombatVisualEffects.tick();
            CombatState.tickCameraFocus();
            CombatAnimations.tick();
            CombatInputHandler.tick(client);

            if (CombatState.isInCombat() && client.mouse.isCursorLocked()
                    && client.currentScreen == null) {
                client.mouse.unlockCursor();
            }
        });
    }
}
