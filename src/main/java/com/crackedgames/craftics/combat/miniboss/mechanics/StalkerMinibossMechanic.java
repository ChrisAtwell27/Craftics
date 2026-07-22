package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deeper-and-Darker deep_dark level-4 miniboss: The Stalker (Ancient Temple).
 *
 * <p>Registered ONLY when Deeper and Darker is installed (see
 * {@code DeeperAndDarkerCompat}); otherwise the vanilla
 * {@link DeepDarkWaveMechanic} stays the deep_dark miniboss. This is a literal
 * miniboss - one tall Stalker whose two-mode invisible/attacker behavior lives
 * in {@code StalkerAI} - so killing it completes the fight via the base
 * "all enemies cleared" check (default {@link #isComplete}).
 *
 * <p>To keep pressure on while the Stalker phases in and out, a Sculk Leech
 * skitters out of the dark every {@link #LEECH_CADENCE} rounds, up to
 * {@link #LEECH_CAP} alive - a small nod to the mod's real Stalker, which
 * summons leeches during its fight.
 */
public final class StalkerMinibossMechanic implements MinibossMechanic {

    public static final String STALKER_ID = "deeperdarker:stalker";
    public static final String LEECH_ID = "deeperdarker:sculk_leech";

    private static final int LEECH_CADENCE = 3; // a leech add every 3rd round
    private static final int LEECH_CAP = 3;     // never more than this many leeches alive

    @Override
    public String biomeId() {
        return "deep_dark";
    }

    @Override
    public String introTitle() {
        return "§3§l☠ The Stalker";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        // The Stalker: a beefy elite placed near arena center. Scaled like the
        // other biome elites. Its StalkerAI drives the HUNT/STALK mode toggle.
        GridPos pos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (pos == null) pos = new GridPos(width / 2, height / 2);
        used.add(pos);
        spawns.add(new LevelDefinition.EnemySpawn(STALKER_ID, pos,
            55 + hpBonus * 2, 9 + atkBonus, 3, 1));

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.ENTITY_WARDEN_EMERGE, 0.6f, 1.1f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % LEECH_CADENCE != 0) return;

        int liveLeeches = 0;
        for (var e : ctx.enemies()) {
            if (e.isAlive() && LEECH_ID.equals(e.getEntityTypeId())) liveLeeches++;
        }
        if (liveLeeches >= LEECH_CAP) return;

        GridArena arena = ctx.arena();
        List<GridPos> used = new ArrayList<>();
        for (var e : ctx.enemies()) {
            if (e.isAlive() && e.getGridPos() != null) used.add(e.getGridPos());
        }
        GridPos pos = MinibossSpawns.findOpen(arena.getWidth(), arena.getHeight(), used, ctx.rng());
        if (pos == null) return;

        ctx.spawnMob(LEECH_ID, pos, 6, 3, 0, 1);
        ctx.spawnHazardBurst(ParticleTypes.SCULK_SOUL, pos);
        ctx.message("§3A sculk leech slithers from the dark!");
        ctx.playSound(SoundEvents.ENTITY_SILVERFISH_AMBIENT, 0.5f, 0.8f);
    }
}
