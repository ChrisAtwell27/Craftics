package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridArena;
import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface WeaponAbilityHandler {
    WeaponAbility.AttackResult apply(ServerPlayerEntity player, CombatEntity target,
                                      GridArena arena, int baseDamage,
                                      PlayerProgression.PlayerStats stats, int luckPoints);

    default WeaponAbilityHandler and(WeaponAbilityHandler next) {
        WeaponAbilityHandler first = this;
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            WeaponAbility.AttackResult r1 = first.apply(player, target, arena, baseDamage, stats, luckPoints);
            WeaponAbility.AttackResult r2 = next.apply(player, target, arena, r1.totalDamage(), stats, luckPoints);
            var msgs = new java.util.ArrayList<>(r1.messages());
            msgs.addAll(r2.messages());
            var extras = new java.util.ArrayList<>(r1.extraTargets());
            extras.addAll(r2.extraTargets());
            return new WeaponAbility.AttackResult(r2.totalDamage(), msgs, extras);
        };
    }
}
