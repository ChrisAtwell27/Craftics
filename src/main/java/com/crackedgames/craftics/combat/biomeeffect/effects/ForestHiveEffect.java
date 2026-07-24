package com.crackedgames.craftics.combat.biomeeffect.effects;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.biomeeffect.BiomeEffect;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The forest's occasional hazard: wild bee hives hanging in the arena.
 *
 * <p>A hive is a destructible block object, not scenery you can ignore - while
 * it stands it releases a hostile bee every round (the per-round release itself
 * lives in {@code CombatManager.tickBeeHives}, which drives player-placed hives
 * from the same code). Leaving one up is a slowly growing swarm, so the fight
 * pressures you into spending attacks on the hive.
 *
 * <p>The payoff for dealing with it: break a hive with a <b>Silk Touch</b> tool
 * and you keep the hive as an item, which you can then place on your own side to
 * spawn ALLIED bees. A forest obstacle becomes a reusable summon if you bring
 * the right tool.
 *
 * <p>Deliberately "occasionally": {@link #HIVE_CHANCE} of forest levels have any
 * hives at all, so they read as a thing you run into rather than forest furniture.
 */
public final class ForestHiveEffect implements BiomeEffect {

    /** Chance a given forest level grows hives at all. */
    private static final float HIVE_CHANCE = 0.45f;
    private static final int MIN_HIVES = 1;
    private static final int MAX_HIVES = 2; // inclusive
    private static final int HIVE_HP = 12;

    @Override
    public String id() {
        return "forest_hives";
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        Random rng = ctx.rng();
        if (rng.nextFloat() > HIVE_CHANCE) return; // a quiet forest this time

        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();

        int count = MIN_HIVES + rng.nextInt(MAX_HIVES - MIN_HIVES + 1);
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never place here

        int placed = 0;
        for (int i = 0; i < count; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            CombatEntity hive = ctx.spawnBlockObject(
                CombatManager.HIVE_WILD_ID, pos, HIVE_HP, Blocks.BEEHIVE);
            if (hive == null) continue;
            ctx.spawnHazardBurst(ParticleTypes.HAPPY_VILLAGER, pos);
            placed++;
        }

        if (placed > 0) {
            ctx.message("§eYou hear buzzing - wild hives hang in this clearing. §7(Silk Touch harvests them)");
            ctx.playSound(SoundEvents.ENTITY_BEE_LOOP_AGGRESSIVE, 0.5f, 1.0f);
        }
    }
}
