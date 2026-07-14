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

    /**
     * Whether the camera should pan to follow enemies/allies as they move and
     * attack during their turn. Off by default. Guarded so a config-load failure
     * (e.g. before the wrapper initializes) leaves the camera on the player rather
     * than throwing in a network-receiver thread.
     */
    private static boolean cameraFollowEnemies() {
        try { return com.crackedgames.craftics.CrafticsMod.CONFIG.cameraFollowEnemies(); }
        catch (Exception ignored) { return false; }
    }

    private static KeyBinding guideBookKey;
    private static KeyBinding respecKey;
    private static KeyBinding endTurnKey;
    private static KeyBinding affinityRespecKey;
    private static KeyBinding toggleUiKey;
    private static KeyBinding moveSlotLeftKey;
    private static KeyBinding moveSlotRightKey;
    private static KeyBinding clearPartyKey;
    private static KeyBinding mountAbilityKey;
    private static KeyBinding threatOverlayKey;

    /** The "hide/reveal inventory UI" keybind, read by {@code HandledScreenKeyMixin}. */
    public static KeyBinding getToggleUiKey() { return toggleUiKey; }

    /** The end-turn keybind, read by {@code CombatHudOverlay} for the End Turn button label. */
    public static KeyBinding getEndTurnKey() { return endTurnKey; }
    private static boolean traderScreenOpened = false;
    private static boolean previousBobView = true;
    private static double previousChatScale = 1.0;
    private static double previousChatWidth = 1.0;

    @Override
    public void onInitializeClient() {
        CrafticsMod.LOGGER.info("Craftics client initializing...");

        HandledScreens.register(ModScreenHandlers.LEVEL_SELECT_SCREEN_HANDLER, LevelSelectScreen::new);
        HandledScreens.register(ModScreenHandlers.LOOT_MANAGEMENT_SCREEN_HANDLER,
            com.crackedgames.craftics.client.LootManagementScreen::new);

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
                // Non-host clients can still have a Victory / Game Over / event screen open
                // from the previous level - the host's choice pulled the whole party into the
                // next arena, so that screen is now stale. Close it (container screens manage
                // their own lifecycle and are left alone).
                net.minecraft.client.gui.screen.Screen openScreen = context.client().currentScreen;
                if (openScreen != null
                        && !(openScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)
                        && openScreen.getClass().getName().startsWith("com.crackedgames.craftics")) {
                    context.client().setScreen(null);
                }
                // Only set camera yaw on first combat entry; keep orientation between levels
                if (!wasInCombat) {
                    if (payload.cameraYaw() >= 0) {
                        CombatState.setCombatYaw(payload.cameraYaw());
                    } else {
                        CombatState.setCombatYaw(225.0f);
                    }
                }
                // Polygon mask updates every level so the cursor tracks each arena's
                // actual shape (empty = rectangular = whole bbox valid).
                CombatState.setPolygonMask(payload.polygonMask(), payload.width(), payload.height());
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
                payload.partyHpData(), payload.turnOrderData(),
                payload.playerStatsData()
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
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0
                                && cameraFollowEnemies()) {
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
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0
                                && cameraFollowEnemies()) {
                            CombatState.focusOnTile(payload.targetX(), payload.targetZ());
                        }
                        // Movement-only turns light up the act-order strip too;
                        // previously only attacks marked the acting unit.
                        CombatState.noteActingEnemy(payload.entityId());
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
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_BOSS_MOMENT -> {
                        // Boss spectacle beats - valueA: 1 = phase two, 2 = defeated.
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0) {
                            CombatState.focusOnTile(payload.targetX(), payload.targetZ());
                        }
                        if (payload.valueA() == com.crackedgames.craftics.network.CombatEventPayload.BOSS_MOMENT_PHASE_TWO) {
                            CombatVisualEffects.triggerShakeTimed(0.55f, 12);
                            CombatVisualEffects.flashWithColor(0x55AA0000, 14); // dark red surge
                        } else if (payload.valueA() == com.crackedgames.craftics.network.CombatEventPayload.BOSS_MOMENT_DEFEATED) {
                            // The boss-corpse spectacle always plays. The "BOSS DEFEATED"
                            // celebration (freeze-frame punch, banner, victory toll) only fires
                            // on the winning kill - valueB == 1 means no enemies remain. Otherwise
                            // a banner could linger over a fight that's still going.
                            CombatVisualEffects.triggerShakeTimed(0.7f, 16);
                            CombatVisualEffects.flashWithColor(0x55FFAA00, 18); // golden boss-corpse flash
                            if (payload.valueB() == 1) {
                                com.crackedgames.craftics.client.vfx.HitPauseState.freeze(4);
                                CombatVisualEffects.showBanner("§l☠ BOSS DEFEATED ☠", 0xFFFFC030, 34);
                                com.crackedgames.craftics.client.RewardReveal.playMaster(
                                    net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, 0.7f, 0.9f);
                            }
                        }
                    }
                    case com.crackedgames.craftics.network.CombatEventPayload.EVENT_MOB_ATTACK_ANIM -> {
                        if (payload.targetX() >= 0 && payload.targetZ() >= 0
                                && cameraFollowEnemies()) {
                            CombatState.focusOnTile(payload.targetX(), payload.targetZ());
                        }
                        // Mark the attacker as "acting now" so the HUD act-order
                        // strip can highlight it during the enemy phase.
                        CombatState.noteActingEnemy(payload.entityId());
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
                                //?} else {
                                /*context.client().world.addParticleClient(
                                    net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                                    entity.getX() + ox, entity.getY() + oy, entity.getZ() + oz,
                                    0, 0, 0);
                                *///?}
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
                                    //?} else {
                                    /*context.client().world.addParticleClient(
                                        net.minecraft.particle.ParticleTypes.CRIT,
                                        entity.getX() + (Math.random() - 0.5) * 0.8,
                                        entity.getY() + 0.2,
                                        entity.getZ() + (Math.random() - 0.5) * 0.8,
                                        (Math.random() - 0.5) * 0.3, 0.2, (Math.random() - 0.5) * 0.3);
                                    *///?}
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
                com.crackedgames.craftics.client.vfx.EntityBounceState.clear();
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
                        payload.biomeName(), payload.levelIndex(), payload.nextIsBoss(),
                        payload.isLeader(), payload.rewards()
                    ));
                    CombatState.setEmeralds(payload.totalEmeralds());
                });
            }
        );

        // Mid-event reward reveal (piglin barter gamble / treasure-vault loot).
        // Opens over the active event cinematic - no fade-out (unlike victory/game-over),
        // mirroring the result dialogue it replaces.
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.RewardRevealPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    context.client().mouse.unlockCursor();
                    // This reveal replaces the prior dialogue/reveal as normal flow - mark it
                    // superseded so its close() safety net doesn't fire a stray DISMISS.
                    net.minecraft.client.gui.screen.Screen prev = context.client().currentScreen;
                    if (prev instanceof com.crackedgames.craftics.client.DialogueScreen ds) ds.markSuperseded();
                    else if (prev instanceof com.crackedgames.craftics.client.RewardRevealScreen rr) rr.markSuperseded();
                    context.client().setScreen(new com.crackedgames.craftics.client.RewardRevealScreen(
                        payload.style(), payload.success(), payload.title(),
                        payload.subtitle(), payload.items()
                    ));
                });
            }
        );

        // Run-join invite: a party member started a run and is asking whether we want in.
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.RunInvitePayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    context.client().mouse.unlockCursor();
                    context.client().setScreen(new com.crackedgames.craftics.client.RunJoinScreen(
                        payload.biomeName(), payload.starterName(), payload.timeoutSeconds()));
                });
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.GameOverItemsPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    context.client().mouse.unlockCursor();
                    com.crackedgames.craftics.client.TransitionOverlay.startFadeOut();
                    context.client().setScreen(new com.crackedgames.craftics.client.GameOverScreen(
                        payload.items(), payload.lostCounts(),
                        payload.emeraldsLost(), payload.xpLevelsLost()
                    ));
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
                    // Trading hall booth: open (or refresh in place) the parchment
                    // shop screen. Refresh-in-place keeps the screen steady across
                    // the server's post-purchase re-send, and a refresh payload
                    // (openScreen == 0) is dropped when no screen is showing so an
                    // in-flight re-send can't resurrect a shop the player closed.
                    if (CombatState.isInScene()) {
                        if (context.client().currentScreen
                                instanceof com.crackedgames.craftics.client.TraderScreen ts) {
                            ts.updateOffer(payload.tradeData(), payload.stacks(), payload.playerEmeralds());
                        } else if (payload.openScreen() != 0) {
                            context.client().setScreen(new com.crackedgames.craftics.client.TraderScreen(
                                payload.traderName(), payload.traderIcon(),
                                payload.tradeData(), payload.stacks(), payload.playerEmeralds()));
                        }
                        return;
                    }
                    // Legacy event flow (vanilla merchant screen opens separately).
                    CombatState.setTraderActive(true);
                    CombatState.setEmeralds(payload.playerEmeralds());
                    TransitionOverlay.startFadeOut();
                });
            }
        );

        // NPC dialogue box (intro / shopping prompts during event cinematics).
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.DialoguePayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    java.util.List<String> lines =
                        com.crackedgames.craftics.network.DialoguePayload.decodeLines(payload.lines());
                    java.util.List<String> labels =
                        com.crackedgames.craftics.network.DialoguePayload.decodeChoiceLabels(payload.choices());
                    java.util.List<String> actions =
                        com.crackedgames.craftics.network.DialoguePayload.decodeChoiceActions(payload.choices());
                    // If a transition fade is still holding from the last screen
                    // (e.g. the Continue button on the post-battle screen), reveal
                    // the dialogue. Otherwise events like the trial intro sit
                    // behind a black overlay and the player can't see them.
                    TransitionOverlay.startFadeOut();
                    // This dialogue replaces the prior one as normal flow - mark it superseded
                    // so the outgoing box's close() safety net doesn't fire a stray DISMISS.
                    net.minecraft.client.gui.screen.Screen prev = context.client().currentScreen;
                    if (prev instanceof com.crackedgames.craftics.client.DialogueScreen ds) ds.markSuperseded();
                    else if (prev instanceof com.crackedgames.craftics.client.RewardRevealScreen rr) rr.markSuperseded();
                    context.client().setScreen(new com.crackedgames.craftics.client.DialogueScreen(
                        payload.speaker(), lines, labels, actions, payload.background()));
                });
            }
        );

        // Piglin barter +/- gold stepper context. The barter intro narration is sent as a
        // separate DialoguePayload that opens (or already opened) a DialogueScreen; this
        // packet supplies the player's gold and offer cap. Because either packet can arrive
        // first, we both park the context (so a DialogueScreen built afterwards adopts it in
        // its init()) AND, if a DialogueScreen is already showing, push it on directly so the
        // stepper appears immediately. active=false clears the parked context (offer resolved).
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.BarterContextPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    if (payload.active()) {
                        com.crackedgames.craftics.client.DialogueScreen.setPendingBarterContext(
                            payload.gold(), payload.maxOffer());
                        if (context.client().currentScreen
                                instanceof com.crackedgames.craftics.client.DialogueScreen ds) {
                            ds.applyBarterContext(true, payload.gold(), payload.maxOffer());
                        }
                    } else {
                        com.crackedgames.craftics.client.DialogueScreen.clearPendingBarterContext();
                        // Also drop the live screen out of barter mode so the result/leave
                        // line behaves like any other choiceless dialogue: a click sends
                        // ACTION_DISMISS and finalizes the event. Without this the stepper
                        // stays "active" and the dismiss is suppressed, softlocking the event.
                        if (context.client().currentScreen
                                instanceof com.crackedgames.craftics.client.DialogueScreen ds) {
                            ds.applyBarterContext(false, 0, 0);
                        }
                    }
                });
            }
        );

        // Enter the non-combat event cinematic: lock camera + movement, isometric
        // view + free cursor (mirrors the EnterCombatPayload perspective/cursor path).
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.EnterEventCinematicPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    CombatState.setCinematicActive(true);
                    // Seed the focus on the player so the camera starts centered on
                    // them and follows the walk-up smoothly (instead of sweeping in
                    // from a stale arena center).
                    CombatState.seedCinematicFocusOnPlayer();
                    context.client().options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    context.client().mouse.unlockCursor();
                });
            }
        );

        // Exit the event cinematic: restore first-person + cursor lock
        // (mirrors the ExitCombatPayload path).
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.ExitEventCinematicPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    CombatState.setCinematicActive(false);
                    // Barter only exists inside a cinematic/scene, and every clean
                    // barter flow already sent BarterContextPayload(false) by now -
                    // this catch-all drops parked stepper context that an abnormal
                    // close (party leave, ejection into a run) would otherwise
                    // resurrect on the next unrelated dialogue.
                    com.crackedgames.craftics.client.DialogueScreen.clearPendingBarterContext();
                    context.client().options.setPerspective(Perspective.FIRST_PERSON);
                    context.client().mouse.lockCursor();
                    // Leaving a cinematic (event OR merchant scene) must clear any lingering
                    // custom walk-animation layer. A scene's cinematic walk starts a WalkAnimation
                    // on the local player's layer; without this reset it (and the shared
                    // currentLayer/wasAnimating statics) carries into the NEXT combat, leaving that
                    // fight's walk animation dead. Mirrors the entity-swap recovery in
                    // CombatAnimations.tick(). Safe for events too (combat re-inits on start).
                    if (context.client().player != null) {
                        com.crackedgames.craftics.client.CombatAnimations.stopWalking(context.client().player);
                    }
                    com.crackedgames.craftics.client.CombatAnimations.clearCache();
                });
            }
        );

        // Explicit "in a merchant scene" toggle from SceneController.build/leave.
        // Drives scene tile-click routing + the HUD Leave button, kept separate
        // from the cinematic flag (which non-combat events also raise).
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.SceneStatePayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    CombatState.setInScene(payload.active());
                    if (payload.active()) {
                        // Seed TileRaycast's grid bounds from the scene footprint so floor
                        // clicks resolve to a tile (a scene never calls enterCombat).
                        CombatState.setSceneBounds(payload.ox(), payload.oy(), payload.oz(),
                            payload.w(), payload.h());
                        CombatState.setSceneBooths(payload.boothData());
                        // Start centered on the player: a pan left over from the last fight
                        // would otherwise offset the scene camera by that stale drag.
                        CombatState.resetPan();
                    } else {
                        CombatState.clearSceneBounds();
                    }
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
                        payload.dangerTiles(), payload.warningTiles(), payload.enemyMap(), payload.enemyTypes(),
                        payload.mountTiles(), payload.warningArrows(),
                        payload.forecastPath(), payload.forecastStrike());
                });
            });

        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.TileFlashPayload.ID, (payload, context) -> {
                context.client().execute(() ->
                    CombatState.addTileFlash(payload.tiles(), payload.color(), payload.durationTicks()));
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

        affinityRespecKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.respec_affinity",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J,
            KEYBIND_CATEGORY
        ));

        toggleUiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.toggle_ui",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U,
            KEYBIND_CATEGORY
        ));

        moveSlotLeftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.move_slot_left",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT,
            KEYBIND_CATEGORY
        ));

        moveSlotRightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.move_slot_right",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT,
            KEYBIND_CATEGORY
        ));

        clearPartyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.clear_party",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_0,
            KEYBIND_CATEGORY
        ));

        mountAbilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.mount_ability",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M,
            KEYBIND_CATEGORY
        ));

        threatOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftics.threat_overlay",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y,
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

        // Battle-party membership -> "Active In Party" floating labels
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.PartyMobsSyncPayload.ID, (payload, context) -> {
                context.client().execute(() -> {
                    java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
                    String raw = payload.mobUuids();
                    if (!raw.isEmpty()) {
                        for (String s : raw.split("\\|")) {
                            if (s.isEmpty()) continue;
                            try { ids.add(java.util.UUID.fromString(s)); }
                            catch (IllegalArgumentException ignored) {}
                        }
                    }
                    com.crackedgames.craftics.client.PartyLabelRenderer.setPartyMobs(ids);
                });
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

        // Dynamic music: server picks the track (biome / boss / event); the client
        // cross-fades to it and pops a bottom-left "now playing" toast.
        ClientPlayNetworking.registerGlobalReceiver(
            com.crackedgames.craftics.network.MusicSyncPayload.ID, (payload, context) ->
                context.client().execute(() ->
                    com.crackedgames.craftics.client.music.MusicManager.request(payload.trackKey())));

        HudRenderCallback.EVENT.register(new CombatHudOverlay());
        HudRenderCallback.EVENT.register(new com.crackedgames.craftics.client.SceneHudOverlay());
        HudRenderCallback.EVENT.register(TransitionOverlay::render);
        HudRenderCallback.EVENT.register(new AchievementToast());
        HudRenderCallback.EVENT.register(new com.crackedgames.craftics.client.music.MusicToast());
        HudRenderCallback.EVENT.register(new com.crackedgames.craftics.client.hints.HintHudRenderer());
        CombatTooltips.register();
        TileOverlayRenderer.register();
        com.crackedgames.craftics.client.PartyLabelRenderer.register();
        com.crackedgames.craftics.client.TesterLabelRenderer.register();
        com.crackedgames.craftics.client.EffectIconRenderer.register();
        com.crackedgames.craftics.client.EffectParticleEmitter.register();
        com.crackedgames.craftics.client.HoverTargetArrowRenderer.register();

        // Client-side deferred copper-tier registration. The MP client never sees
        // ServerLifecycleEvents, but tooltips still need WeaponRegistry populated.
        // CLIENT_STARTED fires after every mod's main entrypoint has run, so the
        // copperagebackport items are guaranteed to be in Registries.ITEM by now.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
            client -> {
                com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat.registerDeferred();
                com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat.registerDeferred();
                com.crackedgames.craftics.compat.immersivearmors.ImmersiveArmorsCompat.registerDeferred();
                com.crackedgames.craftics.compat.simplybows.SimplyBowsCompat.registerDeferred();
            });

        // Resolve keybind conflicts at startup. CLIENT_STARTED runs after every
        // mod has registered its bindings and after options.txt has loaded, so
        // we can see the final bound key of every other mod here. If an other
        // mod's binding (e.g. Iris's "Reload Shaders" on R) shares a key with a
        // Craftics combat binding, we unbind it so combat keys always win.
        // Players can re-bind in vanilla controls if they prefer to revert.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
            CrafticsClient::resolveKeybindConflicts);

        // The custom title screen plays the current biome's theme
        // (CrafticsTitleScreen). Fade it out the moment a world connection
        // starts so menu music never bleeds into gameplay - the server pushes
        // the correct in-game track once combat/scene state syncs.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            client.execute(() ->
                com.crackedgames.craftics.client.music.MusicManager.request("")));

        // Without this, leaving a world mid-battle leaves CombatState.inCombat
        // stuck true, CameraLockMixin then overrides camera rotation/position
        // forever on the title screen and in every subsequent world, effectively
        // bricking the client until restart. The server sends ExitCombatPayload
        // on clean end-of-fight, but never gets the chance on abrupt disconnect.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            boolean wasInCombat = CombatState.isInCombat();
            CombatState.resetAll();
            // Hard-stop the soundtrack so it never bleeds onto the title screen.
            com.crackedgames.craftics.client.music.MusicManager.stopAll();
            CombatVisualEffects.resetOverlays();
            com.crackedgames.craftics.client.guide.GuideBookData.resetToDefaults();
            com.crackedgames.craftics.client.PartyLabelRenderer.clear();
            // Parked barter-stepper context and live bounce offsets are session
            // state: a disconnect mid-barter/mid-fight must not carry them into
            // the next world (a stale stepper would hijack unrelated dialogues).
            com.crackedgames.craftics.client.DialogueScreen.clearPendingBarterContext();
            com.crackedgames.craftics.client.vfx.EntityBounceState.clear();
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
            com.crackedgames.craftics.client.music.MusicToast.tick();

            // Lead-command ally glow is server-driven via LeadSelectPayload:
            // the server toggles glowing on the picked mob so the data tracker
            // sync makes it visible to everyone in the party.

            // If the player drops the Lead while an ally is selected, clear
            // the local selection so the COMMAND pill doesn't lie about state.
            if (client.player != null
                    && CombatState.getLeadSelectedAllyId() != null
                    && client.player.getMainHandStack().getItem() != net.minecraft.item.Items.LEAD) {
                CombatState.setLeadSelectedAllyId(null);
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.crackedgames.craftics.network.LeadSelectPayload(-1));
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

            while (affinityRespecKey.wasPressed()) {
                if (client.currentScreen == null && !CombatState.isInCombat()) {
                    client.setScreen(new com.crackedgames.craftics.client.AffinityRespecScreen());
                }
            }

            while (toggleUiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    CombatState.toggleStatsOverlay();
                }
            }

            while (moveSlotLeftKey.wasPressed()) {
                if (client.currentScreen == null) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.crackedgames.craftics.network.MoveSlotShiftPayload(-1));
                }
            }
            while (moveSlotRightKey.wasPressed()) {
                if (client.currentScreen == null) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.crackedgames.craftics.network.MoveSlotShiftPayload(1));
                }
            }

            while (mountAbilityKey.wasPressed()) {
                // Use the current mount's ability (netherite golem summons coal golems).
                // The server re-checks that the player is in combat and actually mounted.
                if (CombatState.isInCombat() && client.currentScreen == null) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.crackedgames.craftics.network.MountAbilityPayload());
                }
            }

            while (threatOverlayKey.wasPressed()) {
                // Toggle the client-side "danger zone" overlay: every tile an
                // enemy could reach and attack this turn.
                if (CombatState.isInCombat() && client.currentScreen == null) {
                    boolean on = CombatState.toggleThreatOverlay();
                    com.crackedgames.craftics.client.CombatLog.addMessage(on
                        ? "§cEnemy threat ranges shown"
                        : "§7Enemy threat ranges hidden");
                }
            }

            while (clearPartyKey.wasPressed()) {
                // Hub-only: clear the whole battle party. Server re-checks the
                // in-combat state, so this is only a convenience gate.
                if (client.currentScreen == null && !CombatState.isInCombat()) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.crackedgames.craftics.network.ClearPartyPayload());
                }
            }

            if (client.player != null && client.currentScreen == null) {
                var held = client.player.getMainHandStack();
                if (held.getItem() instanceof com.crackedgames.craftics.item.GuideBookItem
                        && client.options.useKey.wasPressed()) {
                    client.setScreen(new com.crackedgames.craftics.client.guide.GuideBookScreen());
                }
            }

            // Detect the vanilla merchant screen opening/closing during an event
            // cinematic so we can show the "Are you done shopping?" dialogue on close.
            // Self-arm here (rather than relying on a server-pushed trader-active flag)
            // so opening the shop from dialogue doesn't need a side-effect-laden payload.
            {
                boolean isMerchantScreen = client.currentScreen instanceof
                    net.minecraft.client.gui.screen.ingame.MerchantScreen;
                if (isMerchantScreen && CombatState.isCinematicActive()) {
                    traderScreenOpened = true;
                } else if (traderScreenOpened && client.currentScreen == null) {
                    traderScreenOpened = false;
                    // Tell the server the merchant screen closed so it shows the
                    // "Are you done shopping?" dialogue (Yes=finish, No=reopen_shop).
                    ClientPlayNetworking.send(new com.crackedgames.craftics.network.DialogueChoicePayload(
                        com.crackedgames.craftics.network.DialogueChoicePayload.ACTION_MERCHANT_CLOSED));
                }
            }

            TransitionOverlay.tick();
            CombatVisualEffects.tick();
            CombatState.tickCameraFocus();
            CombatAnimations.tick();
            CombatInputHandler.tick(client);

            // Keep combat/cinematic players' body facing aligned with their head, so
            // they don't render "crooked" (diagonal body, forward head). Vanilla lets a
            // player's body lag behind the head, but our turn-based movement forces a
            // fixed facing each tile, so the body never naturally catches up. Pin
            // bodyYaw = headYaw every client tick for arena players.
            if ((CombatState.isInCombat() || CombatState.isCinematicActive()) && client.world != null) {
                for (net.minecraft.entity.player.PlayerEntity p : client.world.getPlayers()) {
                    if (p.getCommandTags().contains("craftics_arena") || p == client.player) {
                        p.setBodyYaw(p.getHeadYaw());
                        // prev/last body yaw field renamed in 1.21.5, set it too so the
                        // body doesn't interpolate from a stale angle for one frame.
                        //? if <=1.21.4 {
                        p.prevBodyYaw = p.getHeadYaw();
                        //?} else {
                        /*p.lastBodyYaw = p.getHeadYaw();
                        *///?}
                    }
                }
            }

            // Hint system tick: applies idle multiplier, evaluates sensors, ticks renderers.
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

            if ((CombatState.isInCombat() || CombatState.isInScene()) && client.mouse.isCursorLocked()
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
        KeyBinding[] ours = { guideBookKey, respecKey, endTurnKey, affinityRespecKey, toggleUiKey, moveSlotLeftKey, moveSlotRightKey, threatOverlayKey };
        java.util.Set<KeyBinding> oursSet = new java.util.HashSet<>(java.util.Arrays.asList(ours));
        int cleared = 0;
        for (KeyBinding mine : ours) {
            if (mine == null || mine.isUnbound()) continue;
            String mineBoundKey = mine.getBoundKeyTranslationKey();
            for (KeyBinding other : client.options.allKeys) {
                if (other == null || oursSet.contains(other) || other.isUnbound()) continue;
                if (mineBoundKey.equals(other.getBoundKeyTranslationKey())) {
                    CrafticsMod.LOGGER.info(
                        "Unbinding {} (was on {}) - conflicts with Craftics's {}",
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
