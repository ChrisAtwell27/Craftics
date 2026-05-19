package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.registry.AllyEntry;
import com.crackedgames.craftics.api.registry.AllyRegistry;
import com.crackedgames.craftics.combat.ai.ally.AllyArchetypes;
import com.crackedgames.craftics.network.PartyMobSync;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Battle-party logic: deciding which mobs may join a player's party, toggling
 * membership when a mob is Shift+Right-Clicked, and deriving combat stats for
 * mobs that have no hand-tuned {@link AllyEntry}.
 *
 * <p>A party is an ordered list of entity UUIDs persisted per player in
 * {@link CrafticsSavedData.PlayerData#getPartyMobs()}. Only mobs in that list
 * are collected into combat (see {@code HubPetCollector}).
 *
 * @since 0.3.0
 */
public final class PartyMobs {

    private PartyMobs() {}

    /** Max mobs in a party — mirrors {@link CrafticsSavedData.PlayerData#MAX_PARTY_MOBS}. */
    public static final int MAX_PARTY = CrafticsSavedData.PlayerData.MAX_PARTY_MOBS;
    /** Only one rideable mob may be in a party at a time. */
    public static final int MAX_RIDEABLE = 1;

    //? if <=1.21.1 {
    private static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> ATTACK_DAMAGE_ATTR =
        net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE;
    //?} else {
    /*private static final net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> ATTACK_DAMAGE_ATTR =
        net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE;
    *///?}

    /**
     * Vanilla mobs that attack on sight, unprovoked — never party-eligible. Every
     * other mob (passive animals, and neutral mobs like endermen/spiders that only
     * fight when provoked) can be added. Matched by registry id so version-only
     * mobs simply never match on shards that lack them.
     */
    private static final Set<String> ALWAYS_HOSTILE = Set.of(
        "minecraft:zombie", "minecraft:zombie_villager", "minecraft:husk", "minecraft:drowned",
        "minecraft:skeleton", "minecraft:stray", "minecraft:bogged", "minecraft:wither_skeleton",
        "minecraft:creeper", "minecraft:witch", "minecraft:slime", "minecraft:magma_cube",
        "minecraft:blaze", "minecraft:ghast", "minecraft:silverfish", "minecraft:endermite",
        "minecraft:guardian", "minecraft:elder_guardian", "minecraft:shulker", "minecraft:phantom",
        "minecraft:vex", "minecraft:vindicator", "minecraft:pillager", "minecraft:evoker",
        "minecraft:illusioner", "minecraft:ravager", "minecraft:zoglin", "minecraft:piglin_brute",
        "minecraft:warden", "minecraft:breeze", "minecraft:creaking",
        "minecraft:ender_dragon", "minecraft:wither", "minecraft:giant"
    );

    /** Mobs the player can ride — capped at {@link #MAX_RIDEABLE} per party. */
    private static final Set<String> RIDEABLE_TYPES = Set.of(
        "minecraft:horse", "minecraft:donkey", "minecraft:mule",
        "minecraft:skeleton_horse", "minecraft:zombie_horse", "minecraft:camel",
        "minecraft:pig", "minecraft:strider"
    );

    /** Registry id of a mob's entity type, e.g. {@code minecraft:wolf}. */
    public static String typeId(MobEntity mob) {
        return Registries.ENTITY_TYPE.getId(mob.getType()).toString();
    }

    /** Whether this mob may be added to a battle party. */
    public static boolean isEligible(MobEntity mob) {
        if (mob == null || !mob.isAlive()) return false;
        // An arena combatant — never a hub party candidate.
        if (mob.getCommandTags().contains("craftics_arena")) return false;
        return !ALWAYS_HOSTILE.contains(typeId(mob));
    }

    /** Whether this mob is a rideable type (horse family, pig, strider, camel). */
    public static boolean isRideable(MobEntity mob) {
        return RIDEABLE_TYPES.contains(typeId(mob));
    }

    /** Whether a rideable mob currently has a saddle — required for it to mount the player. */
    public static boolean isSaddled(MobEntity mob) {
        //? if <=1.21.4 {
        if (mob instanceof net.minecraft.entity.passive.AbstractHorseEntity horse) return horse.isSaddled();
        if (mob instanceof net.minecraft.entity.passive.PigEntity pig) return pig.isSaddled();
        if (mob instanceof net.minecraft.entity.passive.StriderEntity strider) return strider.isSaddled();
        return false;
        //?} else {
        /*// 1.21.5 reworked saddles into a dedicated equipment slot.
        return !mob.getEquippedStack(net.minecraft.entity.EquipmentSlot.SADDLE).isEmpty();
        *///?}
    }

    /** A rideable mob that has a saddle on — it will auto-mount the player in battle. */
    public static boolean isSaddledMount(MobEntity mob) {
        return isRideable(mob) && isSaddled(mob);
    }

    /**
     * Combat stats for a mob with no hand-tuned {@link AllyEntry}. Health scales
     * off the mob's max health; attack reads its attack-damage attribute when it
     * has one, otherwise a token value derived from bulk. Lets <em>any</em>
     * passive/neutral mob fight without every species needing a registry entry.
     */
    public static AllyEntry deriveEntry(MobEntity mob) {
        String id = typeId(mob);
        int maxHealth = Math.max(1, (int) Math.ceil(mob.getMaxHealth()));

        int hp = clamp(Math.round(maxHealth * 0.6f), 3, 20);

        int atk;
        EntityAttributeInstance dmg = mob.getAttributeInstance(ATTACK_DAMAGE_ATTR);
        if (dmg != null) {
            atk = clamp((int) Math.round(dmg.getValue()), 1, 6);
        } else {
            atk = clamp(maxHealth / 10, 1, 3);
        }
        int def = clamp(maxHealth / 25, 0, 3);

        return AllyEntry.builder(id)
            .hp(hp).attack(atk).defense(def).speed(2).range(1)
            .recruitMode(AllyEntry.RecruitMode.TAMED)
            .ai(AllyArchetypes.aiFor(id))
            .scalesWithOwnerGear(true)
            .build();
    }

    /**
     * Toggle {@code mob}'s membership in {@code player}'s battle party — the
     * server-side handler for a Shift+Right-Click on a mob. Enforces the party
     * cap, the single-rideable cap, and combat lockout, and reports the result
     * on the player's action bar.
     *
     * @return {@link ActionResult#SUCCESS} when the interaction was consumed
     *         (always, for an eligible-or-not mob), so vanilla interaction
     *         (mounting, breeding, …) does not also fire.
     */
    public static ActionResult toggleParty(ServerPlayerEntity player, MobEntity mob) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());

        if (pd.inCombat) {
            actionBar(player, "§cYou can't manage your battle party during combat.");
            return ActionResult.SUCCESS;
        }
        if (!isEligible(mob)) {
            actionBar(player, "§c" + mob.getName().getString() + " can't join your battle party.");
            return ActionResult.SUCCESS;
        }

        List<UUID> party = pd.getPartyMobs();
        UUID mobId = mob.getUuid();
        String name = mob.getName().getString();

        // Already in the party — Shift+Right-Click again removes it.
        if (party.contains(mobId)) {
            party.remove(mobId);
            data.markDirty();
            PartyMobSync.sync(player);
            actionBar(player, "§e" + name + " left your battle party. (" + party.size() + "/" + MAX_PARTY + ")");
            return ActionResult.SUCCESS;
        }

        // Adding — drop any dead/gone members first so their slots free up.
        if (pruneDangling(world, party)) data.markDirty();

        if (party.size() >= MAX_PARTY) {
            actionBar(player, "§cYour battle party is full (" + MAX_PARTY + "/" + MAX_PARTY + ").");
            return ActionResult.SUCCESS;
        }
        if (isRideable(mob) && countRideable(world, party) >= MAX_RIDEABLE) {
            actionBar(player, "§cYou can only bring 1 rideable mob into battle.");
            return ActionResult.SUCCESS;
        }

        party.add(mobId);
        mob.setPersistent(); // party mobs must not despawn before the next fight
        data.markDirty();
        PartyMobSync.sync(player);

        String suffix = isSaddledMount(mob)
            ? " §7(saddled — it will mount you in battle)"
            : (isRideable(mob) ? " §7(no saddle — it will fight on foot)" : "");
        actionBar(player, "§a" + name + " — §a§lActive In Party §r§7(" + party.size() + "/" + MAX_PARTY + ")" + suffix);
        return ActionResult.SUCCESS;
    }

    /**
     * Remove party UUIDs whose entity is no longer a live mob in {@code world}
     * (died in combat, despawned, unloaded). Returns whether anything changed.
     */
    public static boolean pruneDangling(ServerWorld world, List<UUID> party) {
        boolean changed = false;
        var it = party.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = world.getEntity(id);
            if (!(e instanceof MobEntity mob) || !mob.isAlive()) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    /** Count rideable mobs currently in the party list. */
    private static int countRideable(ServerWorld world, List<UUID> party) {
        int count = 0;
        for (UUID id : party) {
            Entity e = world.getEntity(id);
            if (e instanceof MobEntity mob && isRideable(mob)) count++;
        }
        return count;
    }

    private static void actionBar(ServerPlayerEntity player, String msg) {
        player.sendMessage(Text.literal(msg), true);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
