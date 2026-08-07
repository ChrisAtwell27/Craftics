package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.mixin.DisplayEntityInvoker;
import com.crackedgames.craftics.mixin.ItemDisplayInvoker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The two-second flourish after a lootbox chest is opened, ported beat for beat from the
 * CrackedGamesLobbyPlugin's {@code RevealAnimation}.
 *
 * <p>Presentation only. By the time {@link #play} is called the reward has already been rolled,
 * inserted into the player's inventory and logged (see {@link LootboxManager#openChest}) - this
 * class cannot change what was won, and the animation can be missed entirely (a disconnect, a
 * dimension change) without costing anybody a reward.
 *
 * <p>Deliberately <em>not</em> built like a slot machine: no reel of other rewards, no spinning
 * through what might have been, no slowing down near the end. Only the won reward is ever
 * rendered, once. Craftics' kiosks can hand out several items per open (unlike the plugin's
 * single-reward boxes), so {@link LootboxManager} calls {@link #play} once per rolled reward
 * with a small tick stagger between them - each one still gets the exact same beat-for-beat
 * timeline below, they just cascade instead of piling on top of each other.
 */
public final class LootboxReveal {
    private LootboxReveal() {}

    /** Scoreboard tag marking our display entities, so a restart can sweep any left mid-flourish. */
    public static final String TAG = "craftics_lootbox_reveal";

    // Tick boundaries, identical to the plugin's RevealAnimation. Roughly two seconds end to end.
    private static final int WIND_UP_END = 10;
    private static final int BURST = 11;
    private static final int RISE_END = 36;
    private static final int FLOURISH = RISE_END + 1;
    private static final int FINISH_END = 41;

    /** How far the display travels over the rise, in blocks. */
    private static final double RISE_HEIGHT = 1.0;

    /** Ticks between one reward's windup starting and the next's, when a box awards several. */
    public static final int CASCADE_STAGGER = 14;

    private static final List<Sequence> ACTIVE = new ArrayList<>();

    /**
     * Starts a reveal above {@code pos} (the kiosk chest). {@code startDelay} lets a box that
     * hands out multiple rewards cascade them instead of overlapping.
     */
    public static void play(ServerPlayerEntity player, ServerWorld world, BlockPos pos,
                            LootboxRarity rarity, ItemStack reward, String rewardName, int startDelay) {
        ACTIVE.add(new Sequence(player, world, pos, rarity, reward.copy(), rewardName, startDelay));
    }

    /** Driven from the same aggregate server tick that closes chest lids. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Sequence> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (it.next().step()) it.remove();
        }
    }

    /** Discards any tagged reveal entity still standing - leftovers from a crash mid-animation. */
    public static void sweepLeftovers(MinecraftServer server) {
        int swept = 0;
        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (e.getCommandTags().contains(TAG)) {
                    e.discard();
                    swept++;
                }
            }
        }
        if (swept > 0) {
            com.crackedgames.craftics.CrafticsMod.LOGGER.info(
                "Swept {} leftover lootbox reveal display(s) on startup", swept);
        }
    }

    private static final class Sequence {
        private final ServerPlayerEntity player;
        private final ServerWorld world;
        private final Vec3d origin;
        private final LootboxRarity rarity;
        private final ItemStack reward;
        private final String rewardName;

        private DisplayEntity.ItemDisplayEntity display;
        /** Negative while waiting out the cascade stagger; the beats below start at 0. */
        private int tick;

        Sequence(ServerPlayerEntity player, ServerWorld world, BlockPos pos, LootboxRarity rarity,
                ItemStack reward, String rewardName, int startDelay) {
            this.player = player;
            this.world = world;
            this.origin = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            this.rarity = rarity;
            this.reward = reward;
            this.rewardName = rewardName;
            this.tick = -Math.max(0, startDelay);
        }

        /** @return true once this sequence is finished and should be dropped */
        boolean step() {
            // A player who logged out or walked into another world takes their animation with
            // them; the reward is already theirs either way.
            if (player.isRemoved() || player.isDisconnected()
                    || !player.getEntityWorld().equals(world)) {
                end();
                return true;
            }
            if (tick < 0) {
                tick++;
                return false;
            }
            if (tick <= WIND_UP_END) {
                windUp();
            } else if (tick == BURST) {
                burst();
            } else if (tick <= RISE_END) {
                riseAndHold();
            } else if (tick == FLOURISH) {
                flourish();
            } else if (tick >= FINISH_END) {
                end();
                return true;
            } else {
                fade();
            }
            tick++;
            return false;
        }

        /** A ring tightening onto the block, with a note that climbs as it closes. */
        private void windUp() {
            double progress = tick / (double) WIND_UP_END;
            double radius = 1.4 * (1.0 - progress) + 0.15;
            var dust = LootboxPresentation.dustOf(rarity.color, 1.0f);
            for (int step = 0; step < 12; step++) {
                double angle = (Math.PI * 2 * step / 12) + progress * Math.PI;
                world.spawnParticles(dust,
                    origin.x + Math.cos(angle) * radius,
                    origin.y + 0.6 + progress * 0.4,
                    origin.z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
            }
            if (tick % 2 == 0) {
                // 0.8 up to about 1.8: high enough to read as building, inside the range the
                // note block sound actually covers.
                player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(),
                    SoundCategory.PLAYERS, 0.7f, 0.8f + (float) progress);
            }
        }

        /** The flash, the outward blow, and the reward appearing. */
        private void burst() {
            world.spawnParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.END_ROD, origin.x, origin.y + 0.8, origin.z,
                40, 0.1, 0.1, 0.1, 0.25);
            player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS,
                0.8f, 1.6f);

            display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
            display.refreshPositionAndAngles(origin.x, origin.y + 1.0, origin.z, 0f, 0f);
            display.addCommandTag(TAG);
            ((ItemDisplayInvoker) display).craftics$setItemStack(reward);
            var invoker = (DisplayEntityInvoker) display;
            invoker.craftics$setBillboardMode(DisplayEntity.BillboardMode.FIXED);
            invoker.craftics$setTeleportDuration(2);
            invoker.craftics$setInterpolationDuration(2);
            invoker.craftics$setBrightness(new Brightness(15, 15));
            world.spawnEntity(display);
        }

        /** Rising and turning on a column of its rarity's colour. */
        private void riseAndHold() {
            if (display == null || display.isRemoved()) return;
            int elapsed = tick - BURST;
            int riseTicks = RISE_END - BURST;
            double climbed = RISE_HEIGHT * Math.min(1.0, elapsed / (double) riseTicks);

            display.refreshPositionAndAngles(origin.x, origin.y + 1.0 + climbed, origin.z, 0f, 0f);
            float angle = (float) (elapsed * Math.PI / 12);
            ((DisplayEntityInvoker) display).craftics$setTransformation(new AffineTransformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(new AxisAngle4f(angle, 0f, 1f, 0f)),
                new Vector3f(0.7f, 0.7f, 0.7f),
                new Quaternionf()));

            var dust = LootboxPresentation.dustOf(rarity.color, 1.1f);
            world.spawnParticles(dust, origin.x, origin.y + 0.7 + climbed * 0.5, origin.z,
                3, 0.25, 0.35, 0.25, 0);
        }

        /** The finishing burst, sized by rarity, and the announcement. Fires once. */
        private void flourish() {
            double y = origin.y + 1.0 + RISE_HEIGHT;
            var dust = LootboxPresentation.dustOf(rarity.color, 1.4f);
            world.spawnParticles(dust, origin.x, y, origin.z,
                rarity.burstParticles, 0.5, 0.5, 0.5, 0);
            world.spawnParticles(ParticleTypes.END_ROD, origin.x, y, origin.z,
                rarity.burstParticles / 4, 0.3, 0.3, 0.3, 0.12);
            player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS,
                0.7f, 1.4f);

            String line = "§f" + rewardName + " §7(" + rarity.legacyColor + rarity.label + "§7)";
            player.sendMessage(Text.literal("§6You won " + line), false);
            player.sendMessage(Text.literal(line), true);
        }

        /** Shrinks the display over the last few ticks so it leaves rather than blinks out. */
        private void fade() {
            if (display == null || display.isRemoved()) return;
            float remaining = (FINISH_END - tick) / (float) (FINISH_END - RISE_END - 1);
            float scale = Math.max(0.05f, 0.7f * remaining);
            ((DisplayEntityInvoker) display).craftics$setTransformation(new AffineTransformation(
                new Vector3f(0f, 0f, 0f), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
        }

        /** Removes the display and stops the sequence. Safe to reach from any stage. */
        private void end() {
            if (display != null && !display.isRemoved()) {
                display.discard();
            }
            display = null;
        }
    }
}
