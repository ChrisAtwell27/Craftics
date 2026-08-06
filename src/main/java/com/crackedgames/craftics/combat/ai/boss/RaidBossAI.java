package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.raid.RaidBossBuff;
import com.crackedgames.craftics.raid.RaidBossDefinition;

/**
 * A daily raid boss: authored movepool, authored name, and either a double move
 * or a permanent buff.
 *
 * <p>The buff is re-asserted on every action rather than applied once, so it can
 * never expire, be cleansed, or be overwritten by a weaker value. Absorption is
 * the exception and is applied once at spawn by
 * {@link #applySpawnPower(CombatEntity, RaidBossDefinition)}: re-asserting it
 * every action would heal the boss back up. Regeneration and double move are
 * mutually exclusive (a definition carries exactly one power), so regen ticking
 * per action is always exactly once per boss turn.
 */
public class RaidBossAI extends MovepoolBossAI {

    /** aiMemory key CombatManager's enemy-turn loop reads for extra actions. */
    public static final String ACTIONS_PER_TURN_KEY = "inf_actions_per_turn";

    private final RaidBossDefinition definition;
    private final RaidBossBuff buff;
    private final int amplifier;

    public RaidBossAI(RaidBossDefinition def) {
        super(MovepoolBossAI.resolve(def.moves()));
        this.definition = def;
        this.buff = def.power().isDoubleMove() ? null : RaidBossBuff.of(def.power().buffEffect());
        this.amplifier = def.power().amplifier();
        if (!hasMoves()) {
            CrafticsMod.LOGGER.error(
                "Raid boss '{}' resolved zero abilities from {}; it will only melee. "
                + "Fix its 'moves' list.", def.id(), def.moves());
        }
        if (!def.power().isDoubleMove() && buff == null) {
            CrafticsMod.LOGGER.error(
                "Raid boss '{}' names unknown buff '{}'; it will fight with no power.",
                def.id(), def.power().buffEffect());
        }
    }

    public RaidBossDefinition definition() { return definition; }

    public String getDisplayName() { return definition.name(); }

    /**
     * One-shot power setup at spawn: extra actions for a double-move boss, the
     * absorption HP pad for an absorption boss. Everything else is re-asserted per
     * action by {@link #beforeAction}.
     */
    public static void applySpawnPower(CombatEntity boss, RaidBossDefinition def) {
        if (def.power().isDoubleMove()) {
            boss.setAiMemory(ACTIONS_PER_TURN_KEY, 2);
            return;
        }
        RaidBossBuff b = RaidBossBuff.of(def.power().buffEffect());
        if (b == RaidBossBuff.ABSORPTION) {
            int pad = RaidBossBuff.absorptionBonus(def.hp(), def.power().amplifier());
            boss.setMaxHp(boss.getMaxHp() + pad);
            boss.heal(pad);
        }
    }

    @Override
    protected void beforeAction(CombatEntity self) {
        if (buff == null) return;
        switch (buff) {
            case STRENGTH -> self.setBonusAttack(RaidBossBuff.attackBonus(amplifier));
            case RESISTANCE -> self.setBonusDefense(RaidBossBuff.defenseBonus(amplifier));
            case SPEED -> self.setSpeedBonus(RaidBossBuff.speedBonus(amplifier));
            case REGENERATION -> self.heal(RaidBossBuff.regenPerTurn(amplifier));
            // FIRE_RESISTANCE: CombatEntity already has this exact behaviour under the name
            // extinguish() (zeroes burningTurns/burningAmplifier and clears the vanilla fire
            // ticks) - see task-7-report.md for why no separate clearBurning() was added.
            case FIRE_RESISTANCE -> self.extinguish();
            case ABSORPTION -> { /* spawn-time only; see applySpawnPower */ }
        }
    }
}
