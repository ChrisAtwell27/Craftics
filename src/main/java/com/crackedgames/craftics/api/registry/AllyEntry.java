package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.ai.ally.AllyAI;
import com.crackedgames.craftics.combat.ai.ally.AllyRoundHook;
import com.crackedgames.craftics.combat.ai.ally.MeleeAllyAI;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable definition of a combat ally — a mob recruited from the player's hub
 * that fights alongside them in the arena.
 *
 * <p>Registered allies are referenced by {@link #entityTypeId() entityTypeId}.
 * {@code HubPetCollector} collects the mobs the player has explicitly added to their
 * battle party (Shift+Right-Click) — there is no automatic hub-yard scan. Build
 * entries with {@link #builder(String)}.
 *
 * @param entityTypeId        the mob this entry describes, e.g. {@code minecraft:wolf}
 * @param hp                  base health
 * @param attack              base attack
 * @param defense             base defense
 * @param range               attack range in tiles
 * @param speed               movement tiles per turn
 * @param recruitMode         how the ally is collected from the hub
 * @param ai                  combat behavior
 * @param scalesWithOwnerGear whether attack gains the owner's armor/trim bonuses
 * @param roundHook           optional per-round effect, or {@code null}
 * @param healItem            optional item that heals this ally in combat, or {@code null}
 * @param healAmount          HP restored by {@code healItem}
 * @param rideable            whether the player can mount this ally as a combat mount
 * @since 0.2.0
 */
public record AllyEntry(
    String entityTypeId,
    int hp,
    int attack,
    int defense,
    int range,
    int speed,
    RecruitMode recruitMode,
    AllyAI ai,
    boolean scalesWithOwnerGear,
    @Nullable AllyRoundHook roundHook,
    @Nullable Item healItem,
    int healAmount,
    boolean rideable
) {
    /** How a hub mob qualifies to be recruited into combat. */
    public enum RecruitMode {
        /** Must be tamed and owned by the hub's player (wolves, cats, horses, …). */
        TAMED,
        /**
         * No taming/ownership required (golems). Recruited like any party mob — add it
         * to your battle party manually (Shift+Right-Click it in the hub); it then joins
         * the next battle and returns home afterward via the standard party-mob path.
         * (There is no automatic hub-yard scan; this mode just waives the taming check.)
         */
        BUILT,
        /**
         * Never recruited from the hub. Registered only so its combat stats are
         * defined — used when a mob of this type is tamed mid-battle.
         */
        IN_COMBAT_ONLY
    }

    /** Shared default AI instance for allies with no custom behavior. */
    public static final AllyAI DEFAULT_AI = new MeleeAllyAI();

    public static Builder builder(String entityTypeId) {
        return new Builder(entityTypeId);
    }

    /** Fluent builder for {@link AllyEntry}. */
    public static class Builder {
        private final String entityTypeId;
        private int hp = 6;
        private int attack = 1;
        private int defense = 0;
        private int range = 1;
        private int speed = 2;
        private RecruitMode recruitMode = RecruitMode.TAMED;
        private AllyAI ai = DEFAULT_AI;
        private boolean scalesWithOwnerGear = true;
        private AllyRoundHook roundHook = null;
        private Item healItem = null;
        private int healAmount = 0;
        private boolean rideable = false;

        public Builder(String entityTypeId) {
            this.entityTypeId = entityTypeId;
        }

        /** Base health. Default {@code 6}. */
        public Builder hp(int hp) { this.hp = hp; return this; }

        /** Base attack. Default {@code 1}. */
        public Builder attack(int attack) { this.attack = attack; return this; }

        /** Base defense. Default {@code 0}. */
        public Builder defense(int defense) { this.defense = defense; return this; }

        /** Attack range in tiles. Default {@code 1}. */
        public Builder range(int range) { this.range = range; return this; }

        /** Movement tiles per turn. Default {@code 2}. */
        public Builder speed(int speed) { this.speed = speed; return this; }

        /** How the ally is recruited from the hub. Default {@link RecruitMode#TAMED}. */
        public Builder recruitMode(RecruitMode mode) { this.recruitMode = mode; return this; }

        /** Combat behavior. Default {@link AllyEntry#DEFAULT_AI}. */
        public Builder ai(AllyAI ai) { this.ai = ai; return this; }

        /** Whether the ally's attack gains the owner's armor and trim bonuses. Default {@code true}. */
        public Builder scalesWithOwnerGear(boolean v) { this.scalesWithOwnerGear = v; return this; }

        /** Optional per-round effect hook, or {@code null} for none. */
        public Builder roundHook(AllyRoundHook hook) { this.roundHook = hook; return this; }

        /** Bind a combat heal item: using {@code item} on this ally restores {@code amount} HP. */
        public Builder healItem(Item item, int amount) {
            this.healItem = item;
            this.healAmount = amount;
            return this;
        }

        /** Whether the player can mount this ally as a combat mount. Default {@code false}. */
        public Builder rideable(boolean v) { this.rideable = v; return this; }

        public AllyEntry build() {
            if (entityTypeId == null || entityTypeId.isBlank()) {
                throw new IllegalStateException("AllyEntry requires a non-blank entityTypeId");
            }
            return new AllyEntry(entityTypeId, hp, attack, defense, range, speed,
                recruitMode, ai, scalesWithOwnerGear, roundHook, healItem, healAmount, rideable);
        }
    }
}
