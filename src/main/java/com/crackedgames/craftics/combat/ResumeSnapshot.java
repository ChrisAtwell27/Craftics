package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A mid-fight save point, so leaving a level is not a way to heal out of one.
 *
 * <p>The hole this closes: a player losing a fight could Go Home, top their health off, walk back
 * in, and get the level rebuilt from scratch at full HP. Every level is now snapshotted at the
 * start of each player turn, and re-entering a level a snapshot still exists for resumes from it -
 * same health, same effects, same enemies at the HP the player left them on.
 *
 * <p>What a snapshot holds is deliberately the state a player can feel: the roster (hostiles and
 * allies), health, status effects, and positions. It does NOT hold boss phase state, AI cooldowns,
 * tile state (fire, ice, webs, rubble), or mid-level event gates. Those rebuild clean, which is
 * why {@link #turnNumber} rides along: cadences that key off the turn counter (sudden death, the
 * every-third-round hooks) pick up where they were rather than restarting.
 *
 * <p>Two rules bound the storage, both from the design brief:
 * <ul>
 *   <li>At most {@link #KEEP} snapshots survive per player. Anything older is dropped on write.</li>
 *   <li>A snapshot older than {@link #MAX_AGE_MILLIS} is not resumed. The player simply gets the
 *       level they were on, rebuilt fresh, which is the pre-existing behaviour.</li>
 * </ul>
 *
 * <p>Serialization is a flat string rather than nested NBT on purpose. {@code PlayerData} is read
 * back through two version-specific {@code fromNbt} bodies (the 1.21.5 NBT API returns Optionals),
 * so every nested compound would have to be written twice and kept in step. One string field is
 * one line in each. The delimiters ({@code ~ | # , + }) are all characters an {@code Identifier}
 * cannot contain, so no mob or effect id can ever collide with the framing.
 *
 * <p>Minecraft-free apart from {@link GridPos} and {@link CombatEntity}, both of which are
 * bootstrap-safe, so the whole codec is unit-testable without a live game.
 */
public final class ResumeSnapshot {

    /** Snapshots kept per player: the current turn and the one before it. */
    public static final int KEEP = 2;

    /** A snapshot older than this is discarded rather than resumed. */
    public static final long MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L;

    private static final String FORMAT = "v1";
    private static final String SNAPSHOT_SEP = "~";
    private static final String SECTION_SEP = "|";
    private static final String RECORD_SEP = "#";
    private static final String FIELD_SEP = ",";
    private static final String STATUS_SEP = "+";
    private static final String PAIR_SEP = ":";

    private ResumeSnapshot() {}

    // ── Records ─────────────────────────────────────────────────────────────

    /**
     * One status effect: the mod's own {@code CombatEffects.EffectType} name for players, or one of
     * the short codes in {@link #captureStatus} for enemies. Kept as a plain string so a removed or
     * renamed effect drops out on load instead of failing the whole snapshot.
     */
    public record EffectState(String type, int turns, int amp) {}

    /** A party member's own state. Health is float because player health is. */
    public record PlayerState(UUID uuid, float health, int food, float saturation,
                              int gx, int gz, List<EffectState> effects) {
        public GridPos gridPos() { return new GridPos(gx, gz); }
    }

    /**
     * One combatant on the field. {@code atk}/{@code def} are the EFFECTIVE values at capture
     * time and are restored as the entity's base, so a buff that was folded into them survives as
     * a flat stat rather than being counted twice by also restoring the buff.
     *
     * <p>{@code flags} is a character set rather than a column per boolean, so a new flag costs no
     * format change: {@code a}=ally, {@code t}=temporary ally, {@code b}=boss, {@code s}=scenery,
     * {@code i}=inert object, {@code g}=background boss, {@code m}=backed by a real mob entity.
     */
    public record EnemyState(String typeId, int gx, int gz, int curHp, int maxHp,
                             int atk, int def, int range, int sizeX, int sizeZ,
                             String flags, String ownerUuid, List<EffectState> status) {
        public GridPos gridPos() { return new GridPos(gx, gz); }
        public boolean has(char flag) { return flags.indexOf(flag) >= 0; }
        public boolean ally() { return has('a'); }
        public boolean boss() { return has('b'); }
        public boolean scenery() { return has('s'); }
        /** True when the entity had a real mob in the world. Block-backed props do not. */
        public boolean hasMob() { return has('m'); }
    }

    /**
     * One save point. {@code biomeId} + {@code levelIndex} is the key a re-entry matches against:
     * the same pair the run cursor in {@code PlayerData} uses, so a snapshot can only ever be
     * resumed into the level it was taken in.
     */
    public record Snapshot(long savedAt, String biomeId, int levelIndex, int turnNumber,
                           List<PlayerState> players, List<EnemyState> enemies) {

        public boolean matches(String biome, int level) {
            return biomeId.equals(biome) && levelIndex == level;
        }

        public boolean isExpired(long now) {
            return now - savedAt >= MAX_AGE_MILLIS;
        }

        public PlayerState playerState(UUID uuid) {
            for (PlayerState p : players) {
                if (p.uuid().equals(uuid)) return p;
            }
            return null;
        }
    }

    // ── Store ───────────────────────────────────────────────────────────────

    /**
     * Add {@code snap} to a serialized store, newest last, trimming to {@link #KEEP}.
     *
     * <p>Snapshots for a DIFFERENT level are dropped rather than kept alongside: the moment a new
     * level is snapshotted the old one can never be resumed anyway (the run cursor has moved on),
     * and keeping them would let a stale fight resurface if a run ever revisited a level index.
     */
    public static String push(String stored, Snapshot snap) {
        List<Snapshot> kept = new ArrayList<>();
        for (Snapshot existing : parseStore(stored)) {
            if (existing.matches(snap.biomeId(), snap.levelIndex())) kept.add(existing);
        }
        kept.add(snap);
        while (kept.size() > KEEP) kept.remove(0);
        return serializeStore(kept);
    }

    /**
     * The snapshot to resume {@code biomeId}/{@code levelIndex} from, or null when there is none,
     * it is for another level, or it has aged out. Newest wins: a rejoin picks up at the start of
     * the turn the player dropped on.
     */
    public static Snapshot newestFor(String stored, String biomeId, int levelIndex, long now) {
        Snapshot best = null;
        for (Snapshot s : parseStore(stored)) {
            if (!s.matches(biomeId, levelIndex)) continue;
            if (s.isExpired(now)) continue;
            if (best == null || s.savedAt() >= best.savedAt()) best = s;
        }
        return best;
    }

    // ── Live state -> record ─────────────────────────────────────────────────

    /** Snapshot one combatant. */
    public static EnemyState capture(CombatEntity e) {
        StringBuilder flags = new StringBuilder();
        if (e.isAlly()) flags.append('a');
        if (e.isTemporaryAlly()) flags.append('t');
        if (e.isBoss()) flags.append('b');
        if (e.isScenery()) flags.append('s');
        if (e.isInertObject()) flags.append('i');
        if (e.isBackgroundBoss()) flags.append('g');
        if (e.getMobEntity() != null) flags.append('m');
        GridPos pos = e.getGridPos() != null ? e.getGridPos() : new GridPos(0, 0);
        return new EnemyState(e.getEntityTypeId(), pos.x(), pos.z(),
            e.getCurrentHp(), e.getMaxHp(), e.getAttackPower(), e.getDefense(), e.getRange(),
            e.getSizeX(), e.getSizeZ(), flags.toString(),
            e.getOwnerUuid() != null ? e.getOwnerUuid().toString() : "",
            captureStatus(e));
    }

    /**
     * The enemy-side status block. Short codes rather than the field names so the string stays
     * small; the table is the single place the two directions have to agree, which is why capture
     * and {@link #applyStatus} sit next to each other.
     *
     * <p>Attack and defense boosts are deliberately absent: {@link #capture} already folds them
     * into the stats it records.
     */
    public static List<EffectState> captureStatus(CombatEntity e) {
        List<EffectState> out = new ArrayList<>();
        add(out, "poi", e.getPoisonTurns(), e.getPoisonAmplifier());
        add(out, "wit", e.getWitherTurns(), e.getWitherAmplifier());
        add(out, "brn", e.getBurningTurns(), e.getBurningAmplifier());
        add(out, "sbn", e.getSoulBurningTurns(), e.getSoulBurningAmplifier());
        add(out, "soa", e.getSoakedTurns(), e.getSoakedAmplifier());
        add(out, "con", e.getConfusionTurns(), e.getConfusionAmplifier());
        add(out, "slo", e.getSlownessTurns(), e.getSlownessPenalty());
        add(out, "atp", e.getAttackPenaltyTurns(), e.getAttackPenalty());
        add(out, "dfp", e.getDefensePenaltyTurns(), e.getDefensePenalty());
        add(out, "lev", e.getLevitationStateTurns(), e.getLevitationStateAmplifier());
        add(out, "air", e.getAirtimeStateTurns(), 0);
        add(out, "sfa", e.getSlowFallingTurns(), 0);
        // Bleed has no duration, only stacks, so it rides in the amplifier slot with turns=1.
        // Without the turns=1 the add() guard below would drop it.
        if (e.getBleedStacks() > 0) out.add(new EffectState("bld", 1, e.getBleedStacks()));
        if (e.isEnraged()) out.add(new EffectState("rag", 1, 0));
        return out;
    }

    private static void add(List<EffectState> out, String code, int turns, int amp) {
        if (turns > 0) out.add(new EffectState(code, turns, amp));
    }

    /**
     * Put a captured status block back on an entity. Unknown codes are ignored so a snapshot
     * written by an older build still loads.
     *
     * <p>Sets rather than stacks: the {@code stackX} helpers would add the restored duration on
     * top of whatever the fresh spawn already had, and a resumed fight must read exactly like the
     * one that was left.
     */
    public static void applyStatus(CombatEntity e, List<EffectState> status) {
        for (EffectState s : status) {
            switch (s.type()) {
                case "poi" -> { e.setPoisonTurns(s.turns()); e.setPoisonAmplifier(s.amp()); }
                case "wit" -> { e.setWitherTurns(s.turns()); e.setWitherAmplifier(s.amp()); }
                case "brn" -> { e.setBurningTurns(s.turns()); e.setBurningAmplifier(s.amp()); }
                case "sbn" -> { e.setSoulBurningTurns(s.turns()); e.setSoulBurningAmplifier(s.amp()); }
                case "soa" -> { e.setSoakedTurns(s.turns()); e.setSoakedAmplifier(s.amp()); }
                case "con" -> { e.setConfusionTurns(s.turns()); e.setConfusionAmplifier(s.amp()); }
                case "slo" -> { e.setSlownessTurns(s.turns()); e.setSlownessPenalty(s.amp()); }
                case "atp" -> { e.setAttackPenaltyTurns(s.turns()); e.setAttackPenalty(s.amp()); }
                case "dfp" -> { e.setDefensePenaltyTurns(s.turns()); e.setDefensePenalty(s.amp()); }
                case "lev" -> e.applyLevitationState(s.turns(), s.amp());
                case "air" -> e.applyAirtimeState(s.turns());
                case "sfa" -> e.applySlowFalling(s.turns());
                case "bld" -> e.setBleedStacks(s.amp());
                case "rag" -> e.setEnraged(true);
                default -> { /* written by a build that knew an effect this one does not */ }
            }
        }
    }

    // ── Codec ───────────────────────────────────────────────────────────────

    public static String serializeStore(List<Snapshot> snapshots) {
        StringBuilder sb = new StringBuilder();
        for (Snapshot s : snapshots) {
            if (sb.length() > 0) sb.append(SNAPSHOT_SEP);
            sb.append(serialize(s));
        }
        return sb.toString();
    }

    /** Every snapshot in {@code stored}, oldest first. Unreadable entries are skipped. */
    public static List<Snapshot> parseStore(String stored) {
        List<Snapshot> out = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return out;
        for (String part : stored.split(java.util.regex.Pattern.quote(SNAPSHOT_SEP))) {
            Snapshot s = parse(part);
            if (s != null) out.add(s);
        }
        return out;
    }

    public static String serialize(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append(FORMAT).append(FIELD_SEP)
          .append(s.savedAt()).append(FIELD_SEP)
          .append(s.biomeId()).append(FIELD_SEP)
          .append(s.levelIndex()).append(FIELD_SEP)
          .append(s.turnNumber());
        sb.append(SECTION_SEP);
        boolean first = true;
        for (PlayerState p : s.players()) {
            if (!first) sb.append(RECORD_SEP);
            first = false;
            sb.append(p.uuid()).append(FIELD_SEP)
              .append(p.health()).append(FIELD_SEP)
              .append(p.food()).append(FIELD_SEP)
              .append(p.saturation()).append(FIELD_SEP)
              .append(p.gx()).append(FIELD_SEP)
              .append(p.gz()).append(FIELD_SEP)
              .append(serializeStatus(p.effects()));
        }
        sb.append(SECTION_SEP);
        first = true;
        for (EnemyState e : s.enemies()) {
            if (!first) sb.append(RECORD_SEP);
            first = false;
            sb.append(e.typeId()).append(FIELD_SEP)
              .append(e.gx()).append(FIELD_SEP)
              .append(e.gz()).append(FIELD_SEP)
              .append(e.curHp()).append(FIELD_SEP)
              .append(e.maxHp()).append(FIELD_SEP)
              .append(e.atk()).append(FIELD_SEP)
              .append(e.def()).append(FIELD_SEP)
              .append(e.range()).append(FIELD_SEP)
              .append(e.sizeX()).append(FIELD_SEP)
              .append(e.sizeZ()).append(FIELD_SEP)
              .append(e.flags()).append(FIELD_SEP)
              .append(e.ownerUuid()).append(FIELD_SEP)
              .append(serializeStatus(e.status()));
        }
        return sb.toString();
    }

    /** One snapshot, or null when the string is not one this build can read. */
    public static Snapshot parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        // -1 keeps trailing empty sections: a fight with no enemies left still has to parse.
        String[] sections = raw.split(java.util.regex.Pattern.quote(SECTION_SEP), -1);
        if (sections.length < 3) return null;
        String[] head = sections[0].split(FIELD_SEP, -1);
        if (head.length < 5 || !FORMAT.equals(head[0])) return null;
        long savedAt = parseLong(head[1]);
        String biomeId = head[2];
        int levelIndex = parseInt(head[3]);
        int turnNumber = parseInt(head[4]);
        if (biomeId.isEmpty()) return null;

        List<PlayerState> players = new ArrayList<>();
        for (String rec : records(sections[1])) {
            String[] f = rec.split(FIELD_SEP, -1);
            if (f.length < 7) continue;
            UUID uuid;
            try {
                uuid = UUID.fromString(f[0]);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            players.add(new PlayerState(uuid, parseFloat(f[1]), parseInt(f[2]), parseFloat(f[3]),
                parseInt(f[4]), parseInt(f[5]), parseStatus(f[6])));
        }

        List<EnemyState> enemies = new ArrayList<>();
        for (String rec : records(sections[2])) {
            String[] f = rec.split(FIELD_SEP, -1);
            if (f.length < 13) continue;
            if (f[0].isEmpty()) continue;
            enemies.add(new EnemyState(f[0], parseInt(f[1]), parseInt(f[2]), parseInt(f[3]),
                parseInt(f[4]), parseInt(f[5]), parseInt(f[6]), parseInt(f[7]),
                Math.max(1, parseInt(f[8])), Math.max(1, parseInt(f[9])),
                f[10], f[11], parseStatus(f[12])));
        }
        return new Snapshot(savedAt, biomeId, levelIndex, turnNumber, players, enemies);
    }

    private static List<String> records(String section) {
        List<String> out = new ArrayList<>();
        if (section == null || section.isEmpty()) return out;
        for (String rec : section.split(java.util.regex.Pattern.quote(RECORD_SEP))) {
            if (!rec.isEmpty()) out.add(rec);
        }
        return out;
    }

    private static String serializeStatus(List<EffectState> effects) {
        StringBuilder sb = new StringBuilder();
        for (EffectState e : effects) {
            if (sb.length() > 0) sb.append(STATUS_SEP);
            sb.append(e.type()).append(PAIR_SEP).append(e.turns()).append(PAIR_SEP).append(e.amp());
        }
        return sb.toString();
    }

    private static List<EffectState> parseStatus(String raw) {
        List<EffectState> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String entry : raw.split(java.util.regex.Pattern.quote(STATUS_SEP))) {
            String[] parts = entry.split(PAIR_SEP, -1);
            if (parts.length < 3 || parts[0].isEmpty()) continue;
            out.add(new EffectState(parts[0], parseInt(parts[1]), parseInt(parts[2])));
        }
        return out;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static float parseFloat(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}
