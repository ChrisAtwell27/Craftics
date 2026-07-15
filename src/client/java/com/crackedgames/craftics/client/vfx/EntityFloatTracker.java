package com.crackedgames.craftics.client.vfx;

import com.crackedgames.craftics.client.CombatState;
import com.crackedgames.craftics.combat.EffectIcons;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Marks combatants with Airtime or Levitation as floating, so the bounce mixin bobs them. */
public final class EntityFloatTracker {

    private EntityFloatTracker() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> updateFloating());
    }

    private static void updateFloating() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !CombatState.isInCombat()) {
            EntityBounceState.setFloating(List.of());
            return;
        }
        Map<Integer, String> enemyTypes = CombatState.getEnemyTypeMap();
        Map<Integer, String> allyTypes = CombatState.getAllyTypeMap();
        List<Integer> floating = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            for (String effect : effectsFor(entity, enemyTypes, allyTypes)) {
                String base = EffectIcons.baseName(effect);
                if ("airtime".equals(base) || "levitation".equals(base)) {
                    floating.add(entity.getId());
                    break;
                }
            }
        }
        EntityBounceState.setFloating(floating);
    }

    private static List<String> effectsFor(Entity entity,
                                           Map<Integer, String> enemyTypes,
                                           Map<Integer, String> allyTypes) {
        if (entity instanceof PlayerEntity player) {
            return EffectIcons.parsePlayerEffects(playerEffectString(player));
        }
        String blob = enemyTypes.get(entity.getId());
        if (blob == null) blob = allyTypes.get(entity.getId());
        if (blob == null) return List.of();
        return EffectIcons.parseEnemyEffects(blob);
    }

    private static String playerEffectString(PlayerEntity player) {
        for (CombatState.PartyMemberHp member : CombatState.getPartyHpList()) {
            if (player.getUuid().toString().equals(member.uuid())) {
                return member.dead() ? "" : member.effects();
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isLocal = client.player != null && client.player.getUuid().equals(player.getUuid());
        return isLocal ? CombatState.getPlayerEffects() : "";
    }
}
