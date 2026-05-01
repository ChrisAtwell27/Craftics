package com.crackedgames.craftics;

import com.crackedgames.craftics.block.ModScreenHandlers;
import com.crackedgames.craftics.client.CombatHudOverlay;
import com.crackedgames.craftics.client.CombatInputHandler;
import com.crackedgames.craftics.client.CombatTooltips;
import com.crackedgames.craftics.client.CombatState;
import com.crackedgames.craftics.client.MobHeadTextures;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CrafticsClient implements ClientModInitializer {

    private static final String KEYBIND_CATEGORY = "key.categories.craftics";

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

        // Ghost block uses a fully-transparent texture so MC's break-overlay
        // has faces to render the crack animation on. Cutout render layer
        // discards alpha=0 pixels so the block stays invisible in normal render.
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
            com.crackedgames.craftics.block.ModBlocks.LEVEL_SELECT_GHOST_BLOCK,
            net.minecraft.client.render.RenderLayer.getCutout()
        );

        // GuideBookItem.use() fires server-side, this opens the screen client-side
        com.crackedgames.craftics.item.GuideBookItem.openScreenAction = () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(
                new com.crackedgames.craftics.client.guide.GuideBookScreen()
            );
        };

        ClientPlayNetworking.registerGlobalReceiver(EnterCombatPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                boolean wasInCombat = CombatState.isInCombat();
                CombatState.enterCombat(
                    payload.originX(), payload.originY(), payload.originZ(),
                    payload.width(), payload.height()
                );
                // Only set camera yaw on first combat entry; keep orientation between levels
                if (!wasInCombat) {
                    if (payload.cameraYaw() >= 0) {
                        CombatState.setCombatYaw(payload.cameraYaw());
                    } else {
                        CombatState.setCombatYaw(225.0f);
                    }
                }
                previousBobView = context.client().options.getBobView().getValue();
                context.client().options.getBobView().setValue(false);
                // Shrink chat so it doesn't cover the arena
                previousChatScale = context.client().options.getChatScale().getValue();
                previousChatWidth = context.client().options.getChatWidth().getValue();
                context.client().options.getChatScale().setValue(0.5);
                context.client().options.getChatWidth().setValue(0.39);
                context.client().options.setPerspective(Perspective.THIRD_PERSON_BACK);
                context.client().mouse.unlockCursor();
                TransitionOverlay.startFadeOut();
                CrafticsMod.LOGGER.info("Entered combat mode");
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CombatSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> CombatState.updateFromSync(
                payload.phase(), payload.ap(), payload.movePoints(),
                payload.playerHp(), payload.playerMaxHp(), payload.turnNumber(),
                payload.maxAp(), payload.maxSpeed(),
                payload.enemyData(), payload.enemyTypeIds(),
                payload.playerEffects(), payload.killStreak(),
                payload.partyHpData(), payload.turnOrderData()
            ));
        });

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.ScoreboardSyncPayload.ID, (payload, context) -> {
                context.client().execute(() -> CombatState.updateScoreboard(payload.scoreData()));
            });

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.CombatEventPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
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
                            // Vanilla red hurt flash: Craftics bypasses mob.damage() so
                            // we set hurtTime directly on the client entity. The renderer
                            // reads hurtTime > 0 to apply the overlay; base tick decrements
                            // it so the flash fades naturally over ~10 ticks.
                            var hurtEntity = context.client().world != null
                                ? context.client().world.getEntityById(payload.entityId()) : null;
                            if (hurtEntity instanceof net.minecraft.entity.LivingEntity hurtLe) {
                                hurtLe.maxHurtTime = 10;
                                hurtLe.hurtTime = 10;
                            }
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
                                //? if <=1.21.4 {
                                context.client().world.addParticle(
                                    net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                                    entity.getX() + ox, entity.getY() + oy, entity.getZ() + oz,
                                    0, 0, 0);
                                //?} else
                                /*context.client().world.addParticleClient(
                                    net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                                    entity.getX() + ox, entity.getY() + oy, entity.getZ() + oz,
                                    0, 0, 0);*/
                            }
                            // Extra ground impact particles for knockback attacks
                            if (payload.valueA() == 1) {
                                for (int i = 0; i < 4; i++) {
                                    //? if <=1.21.4 {
                                    context.client().world.addParticle(
                                        net.minecraft.particle.ParticleTypes.CRIT,
                                        entity.getX() + (Math.random() - 0.5) * 0.8,
                                        entity.getY() + 0.2,
                                        entity.getZ() + (Math.random() - 0.5) * 0.8,
                                        (Math.random() - 0.5) * 0.3, 0.2, (Math.random() - 0.5) * 0.3);
                                    //?} else
                                    /*context.client().world.addParticleClient(
                                        net.minecraft.particle.ParticleTypes.CRIT,
                                        entity.getX() + (Math.random() - 0.5) * 0.8,
                                        entity.getY() + 0.2,
                                        entity.getZ() + (Math.random() - 0.5) * 0.8,
                                        (Math.random() - 0.5) * 0.3, 0.2, (Math.random() - 0.5) * 0.3);*/
                                }
                            }
                        }
                    }
                }
                });
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(ExitCombatPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                CombatState.setInCombat(false);
                CombatVisualEffects.resetOverlays();
                context.client().options.getBobView().setValue(previousBobView);
                context.client().options.getChatScale().setValue(previousChatScale);
                context.client().options.getChatWidth().setValue(previousChatWidth);
                if (!payload.eventRoomFollows()) {
                    context.client().options.setPerspective(Perspective.FIRST_PERSON);
                    context.client().mouse.lockCursor();
                }
                TransitionOverlay.startFadeOut();
                CrafticsMod.LOGGER.info("Exited combat mode (won: {}, eventRoom: {})", payload.won(), payload.eventRoomFollows());
            });
        });

        // Stay in isometric view for the choice screen
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.VictoryChoicePayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    context.client().mouse.unlockCursor();
                    TransitionOverlay.startFadeOut();
                    context.client().setScreen(new com.crackedgames.craftics.client.VictoryChoiceScreen(
                        payload.emeraldsEarned(), payload.totalEmeralds(),
                        payload.biomeName(), payload.levelIndex(), payload.nextIsBoss()
                    ));
                    CombatState.setEmeralds(payload.totalEmeralds());
                });
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.PlayerStatsSyncPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    CombatState.updateStats(payload.playerLevel(), payload.unspentPoints(), payload.statData(), payload.affinityData());
                    CombatState.setEmeralds(payload.emeralds());
                });
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.AddonBonusSyncPayload.ID, (payload, context) -> {
                context.client().execute(() -> CombatState.updateAddonBonuses(payload.bonusData()));
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.LevelUpPayload.ID, (payload, context) -> {
                context.client().execute(() -> context.client().setScreen(new com.crackedgames.craftics.client.LevelUpScreen(
                    payload.playerLevel(), payload.unspentPoints(), payload.statData()
                )));
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.TraderOfferPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    CombatState.setTraderActive(true);
                    CombatState.setEmeralds(payload.playerEmeralds());
                    TransitionOverlay.startFadeOut();
                });
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.EventRoomPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    // Restore first-person + cursor lock now that the event screen is ready
                    context.client().options.setPerspective(Perspective.FIRST_PERSON);
                    context.client().mouse.lockCursor();
                    context.client().setScreen(new com.crackedgames.craftics.client.EventRoomScreen(
                        payload.eventType(), payload.eventData()
                    ));
                });
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.LoadingScreenPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    if (payload.show()) {
                        TransitionOverlay.startTransition(payload.title(), payload.subtitle(), () -> {});
                    } else {
                        TransitionOverlay.startFadeOut();
                    }
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
                context.client().execute(() -> AchievementToast.enqueue(
                    payload.displayName(), payload.description(), payload.categoryColor()));
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.GuideBookSyncPayload.ID, (payload, context) -> {
                context.client().execute(() ->
                    com.crackedgames.craftics.client.guide.GuideBookData.applySyncFromServer(payload.unlockedEntries()));
            }
        );

        // Hint system: dismissal store, builtin registrations, and HUD renderer.
        java.nio.file.Path hintsFile = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("craftics_hints.json");
        com.crackedgames.craftics.client.hints.HintDismissalStore hintStore =
            new com.crackedgames.craftics.client.hints.HintDismissalStore(hintsFile);
        com.crackedgames.craftics.client.hints.HintManager.get().setDismissalStore(
            new com.crackedgames.craftics.client.hints.HintManager.DismissalSink() {
                @Override public boolean isDismissed(String id) { return hintStore.isDismissed(id); }
                @Override public void markDismissed(String id) { hintStore.markDismissed(id); }
            });
        com.crackedgames.craftics.client.hints.CrafticsHints.registerAll(
            com.crackedgames.craftics.client.hints.HintManager.get());

        HudRenderCallback.EVENT.register(new CombatHudOverlay());
        HudRenderCallback.EVENT.register(TransitionOverlay::render);
        HudRenderCallback.EVENT.register(new AchievementToast());
        HudRenderCallback.EVENT.register(new com.crackedgames.craftics.client.hints.HintHudRenderer());
        CombatTooltips.register();
        TileOverlayRenderer.register();

        // Client-side deferred copper-tier registration. The MP client never sees
        // ServerLifecycleEvents, but tooltips still need WeaponRegistry populated.
        // CLIENT_STARTED fires after every mod's main entrypoint has run, so the
        // copperagebackport items are guaranteed to be in Registries.ITEM by now.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
            client -> com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.registerDeferred());

        // Resolve keybind conflicts at startup. CLIENT_STARTED runs after every
        // mod has registered its bindings and after options.txt has loaded, so
        // we can see the final bound key of every other mod here. If an other
        // mod's binding (e.g. Iris's "Reload Shaders" on R) shares a key with a
        // Craftics combat binding, we unbind it so combat keys always win.
        // Players can re-bind in vanilla controls if they prefer to revert.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
            CrafticsClient::resolveKeybindConflicts);

        // Without this, leaving a world mid-battle leaves CombatState.inCombat
        // stuck true — CameraLockMixin then overrides camera rotation/position
        // forever on the title screen and in every subsequent world, effectively
        // bricking the client until restart. The server sends ExitCombatPayload
        // on clean end-of-fight, but never gets the chance on abrupt disconnect.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            boolean wasInCombat = CombatState.isInCombat();
            CombatState.resetAll();
            CombatVisualEffects.resetOverlays();
            com.crackedgames.craftics.client.guide.GuideBookData.resetToDefaults();
            if (wasInCombat && client != null) {
                client.options.getBobView().setValue(previousBobView);
                client.options.getChatScale().setValue(previousChatScale);
                client.options.getChatWidth().setValue(previousChatWidth);
                client.options.setPerspective(Perspective.FIRST_PERSON);
                if (!client.mouse.isCursorLocked()) {
                    client.mouse.lockCursor();
                }
            }
            traderScreenOpened = false;
        });

        // Clear mob head auto-discovery cache whenever resource packs reload,
        // so newly added custom head PNGs are picked up without a restart.
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(
                net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override
                public net.minecraft.util.Identifier getFabricId() {
                    return net.minecraft.util.Identifier.of("craftics", "mob_head_probe_cache");
                }
                @Override
                public void reload(net.minecraft.resource.ResourceManager manager) {
                    MobHeadTextures.clearProbeCache();
                }
            });

        // VFX: receive client-side VfxPrimitives from server
        com.crackedgames.craftics.client.vfx.VfxClientDispatcher.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AchievementToast.tick();

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

            // Hint system tick — applies idle multiplier, evaluates sensors, ticks renderers.
            try {
                var cfg = com.crackedgames.craftics.CrafticsMod.CONFIG;
                var hintMgr = com.crackedgames.craftics.client.hints.HintManager.get();
                hintMgr.setIdleMultiplier(cfg.hintIdleMultiplier());
                if (cfg.hintsEnabled()) {
                    long now = System.currentTimeMillis();
                    var ctx = com.crackedgames.craftics.client.hints.CrafticsHints.snapshot(client, now);
                    hintMgr.tickWith(ctx, now);
                    com.crackedgames.craftics.client.hints.HintArrowRenderer.tick(client);
                }
            } catch (Exception ignored) {
                // Never let a hint bug crash the client tick.
            }

            if (CombatState.isInCombat() && client.mouse.isCursorLocked()
                    && client.currentScreen == null) {
                client.mouse.unlockCursor();
            }
        });
    }

    /**
     * Walk every registered keybind and unbind any non-Craftics binding that
     * shares a key with one of ours. Without this, mods like Iris (which puts
     * "Reload Shaders" on R) silently steal combat keys and the player has to
     * dig into vanilla controls to fix it. Run once at CLIENT_STARTED, after
     * every mod has registered and options.txt has loaded.
     */
    private static void resolveKeybindConflicts(net.minecraft.client.MinecraftClient client) {
        KeyBinding[] ours = { guideBookKey, respecKey, endTurnKey };
        java.util.Set<KeyBinding> oursSet = new java.util.HashSet<>(java.util.Arrays.asList(ours));
        int cleared = 0;
        for (KeyBinding mine : ours) {
            if (mine == null || mine.isUnbound()) continue;
            String mineBoundKey = mine.getBoundKeyTranslationKey();
            for (KeyBinding other : client.options.allKeys) {
                if (other == null || oursSet.contains(other) || other.isUnbound()) continue;
                if (mineBoundKey.equals(other.getBoundKeyTranslationKey())) {
                    CrafticsMod.LOGGER.info(
                        "Unbinding {} (was on {}) — conflicts with Craftics's {}",
                        other.getTranslationKey(),
                        other.getBoundKeyTranslationKey(),
                        mine.getTranslationKey());
                    other.setBoundKey(InputUtil.UNKNOWN_KEY);
                    cleared++;
                }
            }
        }
        if (cleared > 0) {
            KeyBinding.updateKeysByCode();
            client.options.write();
            CrafticsMod.LOGGER.info("Resolved {} keybind conflict(s)", cleared);
        }
    }
}
