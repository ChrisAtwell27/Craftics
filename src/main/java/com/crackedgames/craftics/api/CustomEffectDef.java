package com.crackedgames.craftics.api;

import org.jetbrains.annotations.Nullable;

/**
 * Definition of a custom (addon-registered) combat status effect.
 *
 * <p>Custom effects run alongside Craftics' 23 built-in effects but are keyed by a
 * string id rather than the fixed {@code CombatEffects.EffectType} enum, so addons can
 * add their own. They tick once per round on every combatant carrying them: the
 * {@link #hpChangePerTurn} is applied (scaled by the amplifier - negative damages,
 * positive heals), then the optional {@link #tickHandler} runs.
 *
 * <p>Build with {@link #builder(String)}:
 *
 * <pre>{@code
 * CustomEffectDef.builder("mymod:frostbite")
 *     .displayName("Frostbite").description("-2 HP/turn")
 *     .colorCode("§b").harmful(true).hpChangePerTurn(-2)
 *     .build();
 * }</pre>
 *
 * @param id              unique effect id, e.g. {@code "mymod:frostbite"}
 * @param displayName     name shown to players
 * @param description     short description for tooltips/guides
 * @param colorCode       §-color code used in messages and the effect display
 * @param harmful         whether the effect is a debuff (vs. a buff)
 * @param hpChangePerTurn per-turn HP change, scaled by {@code (amplifier + 1)};
 *                        negative damages, positive heals, {@code 0} for none
 * @param tickHandler     optional scripted per-turn logic, or {@code null}
 * @since 0.2.0
 */
public record CustomEffectDef(
    String id,
    String displayName,
    String description,
    String colorCode,
    boolean harmful,
    int hpChangePerTurn,
    @Nullable CustomEffectTickHandler tickHandler
) {
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder for {@link CustomEffectDef}. */
    public static class Builder {
        private final String id;
        private String displayName;
        private String description = "";
        private String colorCode = "§7";
        private boolean harmful = true;
        private int hpChangePerTurn = 0;
        private CustomEffectTickHandler tickHandler = null;

        public Builder(String id) {
            this.id = id;
            this.displayName = id;
        }

        /** Name shown to players. Defaults to the id. */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Short description for tooltips and guides. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** §-color code for messages and the effect display. Default {@code "§7"}. */
        public Builder colorCode(String colorCode) {
            this.colorCode = colorCode;
            return this;
        }

        /** Whether this is a debuff. Default {@code true}. */
        public Builder harmful(boolean harmful) {
            this.harmful = harmful;
            return this;
        }

        /** Per-turn HP change, scaled by {@code (amplifier + 1)}. Negative damages, positive heals. */
        public Builder hpChangePerTurn(int hpChangePerTurn) {
            this.hpChangePerTurn = hpChangePerTurn;
            return this;
        }

        /** Optional scripted per-turn logic. */
        public Builder tickHandler(CustomEffectTickHandler tickHandler) {
            this.tickHandler = tickHandler;
            return this;
        }

        public CustomEffectDef build() {
            return new CustomEffectDef(id, displayName, description, colorCode,
                harmful, hpChangePerTurn, tickHandler);
        }
    }
}
