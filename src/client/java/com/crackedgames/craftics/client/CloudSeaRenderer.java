package com.crackedgames.craftics.client;

//? if <=1.21.4 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Stylized cloud sea around a Craftics build: layers of chunky cloud BOXES banked against the
 * footprint's edge and running underneath it, so the build reads as an island floating on cloud
 * instead of a slab hanging in empty void.
 *
 * <p>Volume, not panes. Each cell is a box with a noise-driven height, and only the faces that
 * border an empty (or shorter) neighbour are emitted - the same hidden-face culling a chunk mesher
 * does. That gives real silhouettes, parallax as the camera orbits, and stepped tops that read as
 * cloud from any angle. Faces are shaded the way blocks are (top brightest, sides dimmer, bottoms
 * dimmest), which is what sells them as solid rather than as fog sprites.
 *
 * <p><b>A bowl, with a doorway.</b> Cloud tops start just under the floor at the board's edge and
 * climb {@link #RISE_PER_BLOCK} per block outward, so by the outer ring the wall stands above eye
 * level and the arena is properly surrounded. What keeps that wall off the tiles is
 * {@link #sightWindow}: the cells between the camera and the board fade out, and everything
 * beside and beyond it stays solid. Banking the whole sea below the floor would also stop it
 * covering the board, but then it reads as a lake beside the arena instead of walls around it.
 *
 * <p>This is the near look. {@link CrafticsFog}'s distance band still runs underneath it, pushed
 * out far enough to only swallow hills and treetops these boxes can't reach; both fade in on the
 * same {@link CrafticsFog#closeInProgress()} ramp so they arrive together.
 *
 * <p>Costs nothing on the server and syncs nothing: the footprint is already on the client
 * ({@code CombatState} arena/scene bounds) and the colour comes from the local biome.
 */
public final class CloudSeaRenderer {

    private CloudSeaRenderer() {}

    /** One shaded face in world space: four corners, wound the way vanilla quads are. */
    private record Face(float x0, float y0, float z0,
                        float x1, float y1, float z1,
                        float x2, float y2, float z2,
                        float x3, float y3, float z3,
                        float r, float g, float b, float a,
                        double camDistSq) {}

    /** Side of one cloud box. Chunky on purpose - this is the stylization. */
    private static final float CELL = 3.0f;
    /** How far past the footprint edge the sea reaches. */
    private static final float REACH = 40.0f;
    /** Ring gap held clear just outside the footprint, so cloud never crowds the board edge. */
    private static final float INNER_GAP = 2.5f;
    /** Distance over which cells ramp up to full density after {@link #INNER_GAP}. */
    private static final float RAMP_IN = 8.0f;
    /** Distance over which cells fade back out before {@link #REACH}. */
    private static final float RAMP_OUT = 14.0f;
    /** Cells farther than this from the camera are skipped (they're inside the distance band). */
    private static final float CULL_DIST = 96.0f;
    /** Cloud height at the inner edge of the ring, relative to the floor plane. */
    private static final float RIM_TOP = -0.6f;
    /** Extra height per block of distance from the footprint - this is what makes the bowl. */
    private static final float RISE_PER_BLOCK = 0.34f;
    /** Ceiling on that rise, so the wall tops out instead of closing over the arena. */
    private static final float MAX_RISE = 9.0f;
    /**
     * Half-width of the clear window carved along the camera's line of sight to the board, as a
     * fraction of the footprint's radius, plus the distance over which it feathers back to full
     * density. Without this the near-side wall stands between the camera and the tiles and the
     * arena looks buried - which is exactly what the first pass did.
     */
    private static final float WINDOW_CORE = 0.62f;
    private static final float WINDOW_FEATHER = 11.0f;

    /**
     * A bank of cloud, top down. {@code topY} is the bank's ceiling relative to the floor plane;
     * {@code minHeight}/{@code maxHeight} bound each box's downward extent; {@code drift} is noise
     * scroll per second; {@code scale} is noise cells per world block (smaller = bigger puffs);
     * {@code interior} lets a bank run under the footprint instead of only ringing it.
     */
    private record Bank(float topY, float alpha, float minHeight, float maxHeight,
                        float driftX, float driftZ, float scale, int seed, boolean interior,
                        float rise) {}

    private static final Bank[] BANKS = {
        // Rim bank: starts just under the floor at the board's edge and climbs with distance, so
        // by the outer ring it stands well above eye level and the arena sits in a bowl of cloud.
        new Bank(RIM_TOP, 0.72f, 1.6f, 4.2f, 0.055f, 0.021f, 0.085f, 1207, false, 1.0f),
        // Mid bank, drifting the other way and climbing slower - fills the wall's body and gives
        // the parallax that reads as depth when the camera orbits.
        new Bank(-4.5f, 0.55f, 2.4f, 6.0f, -0.034f, 0.048f, 0.062f, 5519, false, 0.6f),
        // Deep floor of the sea, running under the island too, so the void below the edge is
        // covered as well. Pits are capped with black concrete at floorY-2, so this stays hidden
        // from anyone peering into a hole. Flat: a bowl down here would poke through the rim.
        new Bank(-11.0f, 0.42f, 3.0f, 7.5f, 0.019f, -0.026f, 0.045f, 9241, true, 0.0f)
    };

    /** Noise below this leaves a gap; above the upper edge the cell is a full-height puff. */
    private static final float COVER_LOW = 0.44f;
    private static final float COVER_HIGH = 0.78f;
    /** Luminance floor for the cloud, so a night arena still shows white rather than grey-black. */
    private static final float MIN_LUMINANCE = 0.46f;
    /** Face shading, vanilla-block style: flat top, dimmer sides, dimmest underside. */
    private static final float SHADE_TOP = 1.0f;
    private static final float SHADE_SIDE = 0.84f;
    private static final float SHADE_BOTTOM = 0.68f;
    /** How far each box breathes vertically, and how fast. */
    private static final float BILLOW_HEIGHT = 0.30f;
    private static final float BILLOW_SPEED = 0.32f;
    /** Gap kept between a cloud box's underside and the ground it rests on. */
    private static final float TERRAIN_CLEARANCE = 0.08f;
    /** A box clipped shorter than this is inside the hill - drop it rather than draw a sliver. */
    private static final float MIN_BOX_HEIGHT = 0.55f;
    /** How far above the floor plane the terrain scan starts, and how far below it gives up. */
    private static final int SCAN_ABOVE = 14;
    private static final int SCAN_BELOW = 20;
    /** Banks with a ceiling at or below this are "deep" and clip against the sub-floor scan. */
    private static final float DEEP_BANK_Y = -8.0f;

    /** Ground height per cell for surface banks, and for the deep bank below the island. */
    private static float[] terrainHigh = new float[0];
    private static float[] terrainDeep = new float[0];
    private static long terrainKey = Long.MIN_VALUE;
    private static long terrainStamp = 0L;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (CrafticsFog.closeInProgress() <= 0.02f) return;
            //? if <=1.21.4 {
            render(context.matrixStack(), context.camera());
            //?} else
            /*renderV5(context);*/
        });
    }

    /** Wall-clock seconds for frame-rate-independent drift/billow. */
    private static float timeSeconds() {
        return (System.currentTimeMillis() % 600_000L) / 1000.0f;
    }

    /**
     * Build this frame's cloud faces.
     *
     * <p>Per bank: rasterize a {@link #CELL} grid of box tops/bottoms/alphas over the footprint
     * expanded by {@link #REACH}, then emit only the faces that border a gap or a shorter
     * neighbour. Faces are sorted back-to-front at the end so translucent boxes blend in a sane
     * order without depth writes.
     */
    private static void buildFaces(MinecraftClient mc, List<Face> out) {
        if (mc == null || mc.world == null) return;
        int gridW = CombatState.getArenaWidth();
        int gridH = CombatState.getArenaHeight();
        if (gridW <= 0 || gridH <= 0) return;

        float floorY = CombatState.getArenaOriginY() + 1.0f;
        // The SCHEMATIC's edge, not the tile grid's: the built platform runs a few blocks past
        // the playable board (border ring, stairs, outskirts) and cloud starting at the grid line
        // sits on top of that stonework. Measured from the world, cached per arena.
        float pad = schematicPad(mc, floorY);
        float minX = CombatState.getArenaOriginX() - pad;
        float minZ = CombatState.getArenaOriginZ() - pad;
        float maxX = CombatState.getArenaOriginX() + gridW + pad;
        float maxZ = CombatState.getArenaOriginZ() + gridH + pad;

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        float centerX = (minX + maxX) * 0.5f;
        float centerZ = (minZ + maxZ) * 0.5f;
        float boardRadius = Math.max(maxX - minX, maxZ - minZ) * 0.5f;
        float[] tint = arenaTint(mc, centerX, floorY, centerZ);
        // Black cloud at white cloud's alpha barely registers against a night sky, so the dark
        // biomes get denser banks to go with their near-black colour.
        float darkBoost = tint[3] > 0.5f ? 1.3f : 1.0f;
        float fade = Math.min(1.0f, CrafticsFog.closeInProgress());
        float time = timeSeconds();

        // Unit vector camera -> board, and how far away the board is. Used to carve the clear
        // window through the near wall so the tiles are never behind cloud.
        double axisX = centerX - camPos.x;
        double axisZ = centerZ - camPos.z;
        double axisLen = Math.sqrt(axisX * axisX + axisZ * axisZ);
        double ux = axisLen > 0.001 ? axisX / axisLen : 0.0;
        double uz = axisLen > 0.001 ? axisZ / axisLen : 0.0;

        float gridMinX = (float) (Math.floor((minX - REACH) / CELL) * CELL);
        float gridMinZ = (float) (Math.floor((minZ - REACH) / CELL) * CELL);
        int nx = (int) Math.ceil(((maxX + REACH) - gridMinX) / CELL);
        int nz = (int) Math.ceil(((maxZ + REACH) - gridMinZ) / CELL);
        if (nx <= 0 || nz <= 0) return;

        float[] topY = new float[nx * nz];
        float[] botY = new float[nx * nz];
        float[] alpha = new float[nx * nz];

        // Ground heights for clipping. Cached on the same key as the platform pad.
        long terrainCacheKey = (((long) CombatState.getArenaOriginX()) << 40)
            ^ (((long) CombatState.getArenaOriginZ()) << 16)
            ^ (gridW * 31L + gridH) ^ (nx * 7919L + nz);
        refreshTerrain(mc, gridMinX, gridMinZ, nx, nz, floorY, terrainCacheKey);

        // Deepest bank first so nearer banks blend over it when distances tie.
        for (int b = BANKS.length - 1; b >= 0; b--) {
            Bank bank = BANKS[b];
            // Drift is in NOISE units per second; dividing by the bank's scale would give blocks
            // per second. At scale ~0.08 these work out to roughly half a block a second - a
            // slow roll. (An earlier x10 here made the whole sea sprint past at ~6 blocks/s.)
            float offsetX = time * bank.driftX();
            float offsetZ = time * bank.driftZ();
            java.util.Arrays.fill(alpha, 0.0f);

            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < nz; j++) {
                    float x = gridMinX + i * CELL;
                    float z = gridMinZ + j * CELL;
                    float cx = x + CELL * 0.5f;
                    float cz = z + CELL * 0.5f;

                    double dxCam = cx - camPos.x;
                    double dzCam = cz - camPos.z;
                    if (dxCam * dxCam + dzCam * dzCam > CULL_DIST * CULL_DIST) continue;

                    float edge = edgeDistance(cx, cz, minX, minZ, maxX, maxZ);
                    boolean inside = edge <= 0.0f;
                    if (inside && !bank.interior()) continue;

                    float density = edgeFalloff(inside ? 0.0f : edge, bank.interior());
                    if (density <= 0.0f) continue;

                    float coverage = coverage(cx * bank.scale() + offsetX,
                        cz * bank.scale() + offsetZ, bank.seed());
                    if (coverage <= 0.0f) continue;

                    // Fuller cells stand taller, so the noise field shapes the silhouette as well
                    // as the gaps - a flat-topped slab would read as a pane again.
                    float height = bank.minHeight()
                        + (bank.maxHeight() - bank.minHeight()) * coverage;
                    float billow = (float) Math.sin(time * BILLOW_SPEED + cx * 0.21f + cz * 0.17f)
                        * BILLOW_HEIGHT;
                    // The bowl: cloud tops climb with distance from the board, so the ring reads
                    // as a wall standing around the arena rather than a lake lying beside it.
                    float rise = bank.rise() * Math.min(MAX_RISE,
                        Math.max(0.0f, (inside ? 0.0f : edge) - INNER_GAP) * RISE_PER_BLOCK);
                    float top = floorY + bank.topY() + rise + billow;

                    int idx = i * nz + j;
                    // Rest ON the ground rather than growing through it: a box that intersects
                    // terrain produces coplanar faces that Z-fight against the blocks. Deep bank
                    // clips against the sub-floor scan so the island's own floor doesn't erase it.
                    float ground = (bank.topY() <= DEEP_BANK_Y ? terrainDeep[idx] : terrainHigh[idx])
                        + TERRAIN_CLEARANCE;
                    float bottom = Math.max(top - height, ground);
                    if (top - bottom < MIN_BOX_HEIGHT) continue;   // buried in a hill

                    // Height has to be known before the window test: whether a cell blocks the
                    // board depends on how high it stands, not just where it sits.
                    float a = bank.alpha() * density * coverage * fade * darkBoost
                        * sightWindow(cx, cz, top, camPos, ux, uz, axisLen, boardRadius, floorY);
                    if (a < 0.03f) continue;

                    alpha[idx] = Math.min(0.9f, a);
                    topY[idx] = top;
                    botY[idx] = bottom;
                }
            }

            emitBank(out, gridMinX, gridMinZ, nx, nz, topY, botY, alpha, tint, camPos);
        }

        // Back-to-front: translucent boxes draw without depth writes, so paint order is the only
        // thing keeping overlapping puffs from blending inside out.
        out.sort((l, r) -> Double.compare(r.camDistSq(), l.camDistSq()));
    }

    /** Emit the visible faces of one rasterized bank (hidden-face culled against neighbours). */
    private static void emitBank(List<Face> out, float gridMinX, float gridMinZ, int nx, int nz,
                                 float[] topY, float[] botY, float[] alpha, float[] tint,
                                 Vec3d camPos) {
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                int idx = i * nz + j;
                float a = alpha[idx];
                if (a <= 0.0f) continue;

                float x0 = gridMinX + i * CELL;
                float z0 = gridMinZ + j * CELL;
                float x1 = x0 + CELL;
                float z1 = z0 + CELL;
                float top = topY[idx];
                float bot = botY[idx];
                double dist = camPos.squaredDistanceTo(
                    (x0 + x1) * 0.5, (top + bot) * 0.5, (z0 + z1) * 0.5);

                addTop(out, x0, z0, x1, z1, top, tint, a, dist);
                addBottom(out, x0, z0, x1, z1, bot, tint, a, dist);

                // A side face only exists where the neighbour stops short - exactly the chunk
                // mesher rule. Without it every box would draw four buried walls and the
                // overlapping alpha would turn the bank into a solid slab of colour.
                float westTop = neighborTop(alpha, topY, nx, nz, i - 1, j);
                if (westTop < top) {
                    addSideX(out, x0, Math.max(bot, westTop), top, z0, z1, tint, a, dist);
                }
                float eastTop = neighborTop(alpha, topY, nx, nz, i + 1, j);
                if (eastTop < top) {
                    addSideX(out, x1, Math.max(bot, eastTop), top, z0, z1, tint, a, dist);
                }
                float northTop = neighborTop(alpha, topY, nx, nz, i, j - 1);
                if (northTop < top) {
                    addSideZ(out, z0, Math.max(bot, northTop), top, x0, x1, tint, a, dist);
                }
                float southTop = neighborTop(alpha, topY, nx, nz, i, j + 1);
                if (southTop < top) {
                    addSideZ(out, z1, Math.max(bot, southTop), top, x0, x1, tint, a, dist);
                }
            }
        }
    }

    /** Neighbour's top, or negative infinity when that cell is empty / off the grid. */
    private static float neighborTop(float[] alpha, float[] topY, int nx, int nz, int i, int j) {
        if (i < 0 || j < 0 || i >= nx || j >= nz) return Float.NEGATIVE_INFINITY;
        int idx = i * nz + j;
        return alpha[idx] <= 0.0f ? Float.NEGATIVE_INFINITY : topY[idx];
    }

    private static void addTop(List<Face> out, float x0, float z0, float x1, float z1, float y,
                               float[] tint, float a, double dist) {
        out.add(new Face(x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0,
            tint[0] * SHADE_TOP, tint[1] * SHADE_TOP, tint[2] * SHADE_TOP, a, dist));
    }

    private static void addBottom(List<Face> out, float x0, float z0, float x1, float z1, float y,
                                  float[] tint, float a, double dist) {
        out.add(new Face(x1, y, z0, x1, y, z1, x0, y, z1, x0, y, z0,
            tint[0] * SHADE_BOTTOM, tint[1] * SHADE_BOTTOM, tint[2] * SHADE_BOTTOM, a, dist));
    }

    /** Face in the ZY plane at a fixed X. */
    private static void addSideX(List<Face> out, float x, float yBottom, float yTop,
                                 float z0, float z1, float[] tint, float a, double dist) {
        if (yTop - yBottom <= 0.01f) return;
        out.add(new Face(x, yBottom, z0, x, yTop, z0, x, yTop, z1, x, yBottom, z1,
            tint[0] * SHADE_SIDE, tint[1] * SHADE_SIDE, tint[2] * SHADE_SIDE, a, dist));
    }

    /** Face in the XY plane at a fixed Z. */
    private static void addSideZ(List<Face> out, float z, float yBottom, float yTop,
                                 float x0, float x1, float[] tint, float a, double dist) {
        if (yTop - yBottom <= 0.01f) return;
        out.add(new Face(x0, yBottom, z, x0, yTop, z, x1, yTop, z, x1, yBottom, z,
            tint[0] * SHADE_SIDE, tint[1] * SHADE_SIDE, tint[2] * SHADE_SIDE, a, dist));
    }

    /**
     * Density multiplier that carves a clear window through the wall standing between the camera
     * and the board.
     *
     * <p>A cell only matters if it is in FRONT of the board from the camera's point of view
     * ({@code along} short of the board's near side) and close to the sight line
     * ({@code perp} within the footprint's radius). Those get faded to nothing at the centre of
     * the line and feathered back to full density out to the sides. Everything beside and beyond
     * the board keeps its full density, so the arena still reads as surrounded - the wall just
     * doesn't stand in the doorway.
     *
     * @param along how far down the camera->board axis the cell sits, in blocks
     * @param perp  how far off that axis it sits, in blocks
     */
    private static float sightWindow(float cx, float cz, float cellTop, Vec3d camPos,
                                     double ux, double uz, double axisLen, float boardRadius,
                                     float floorY) {
        double dx = cx - camPos.x;
        double dz = cz - camPos.z;
        double along = dx * ux + dz * uz;
        if (along <= 0.0) return 1.0f;                        // behind the camera - irrelevant
        if (along >= axisLen + boardRadius) return 1.0f;      // past the board - keep the far wall

        // A cell only blocks the board if it rises above the line of sight. The camera looks
        // DOWN, so the lower body of the near wall is under the sightline and can stay - only the
        // parts that poke up into it need to go. Without this the whole near bank vanished.
        double sightY = camPos.y + (floorY - camPos.y) * (along / Math.max(1.0, axisLen));
        if (cellTop < sightY - 0.5) return 1.0f;

        // The occluding region is the CONE the board subtends from the camera, so the clear slot
        // has to widen with distance. A fixed width (what this was) is far too narrow up close:
        // a cell 5 blocks from the camera covers a huge slice of the board.
        double perp = Math.abs(-dx * uz + dz * ux);
        double core = (boardRadius * WINDOW_CORE + 2.0) * (along / Math.max(1.0, axisLen));
        if (perp <= core) return 0.0f;
        return smoothstep((float) ((perp - core) / WINDOW_FEATHER));
    }

    /**
     * Sample the ground height under every cell, so cloud can rest ON the world instead of
     * growing through it.
     *
     * <p>Boxes that intersect terrain produce coplanar and near-coplanar faces, and with depth
     * writes off those flicker against the blocks all frame - the Z-fighting. Clipping each box's
     * underside to the surface underneath removes the intersection entirely, and has the nice
     * side effect of making the sea pool in hollows and lap up hillsides.
     *
     * <p>Two heights per cell: the surface a rim/mid bank sits on, and the highest ground BELOW
     * the island for the deep bank - measured separately because the arena's own floor would
     * otherwise clip the deep bank out of existence everywhere inside the footprint.
     *
     * <p>Fluids count as ground: cloud should lie on a lake, not sink through it. Cached for two
     * seconds; a fight can dig pits and drop walls, but not fast enough to need this per frame.
     */
    private static void refreshTerrain(MinecraftClient mc, float gridMinX, float gridMinZ,
                                       int nx, int nz, float floorY, long key) {
        long now = System.currentTimeMillis();
        if (key == terrainKey && now - terrainStamp < 2000L
            && terrainHigh.length == nx * nz) {
            return;
        }
        if (terrainHigh.length != nx * nz) {
            terrainHigh = new float[nx * nz];
            terrainDeep = new float[nx * nz];
        }
        int top = (int) floorY + SCAN_ABOVE;
        int bottom = (int) floorY - SCAN_BELOW;
        int deepCeiling = (int) (floorY + DEEP_BANK_Y);
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                int x = (int) Math.floor(gridMinX + i * CELL + CELL * 0.5f);
                int z = (int) Math.floor(gridMinZ + j * CELL + CELL * 0.5f);
                float high = bottom;
                float deep = bottom;
                boolean haveHigh = false;
                for (int y = top; y >= bottom; y--) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (mc.world.getBlockState(at).isAir()) continue;
                    if (!haveHigh) {
                        high = y + 1;      // the surface is the TOP face of that block
                        haveHigh = true;
                    }
                    if (y < deepCeiling) {
                        deep = y + 1;
                        break;             // both heights found
                    }
                }
                if (!haveHigh) high = bottom;
                int idx = i * nz + j;
                terrainHigh[idx] = high;
                terrainDeep[idx] = deep;
            }
        }
        terrainKey = key;
        terrainStamp = now;
    }

    /** Furthest the platform scan will look past the tile grid before giving up. */
    private static final int MAX_PAD = 18;
    /** Fraction of a ring's sampled columns that must be flush with the floor to count as built. */
    private static final float FLUSH_FRACTION = 0.55f;
    /** Cached platform pad, and the arena it was measured for. */
    private static float cachedPad = 0.0f;
    private static long cachedPadKey = Long.MIN_VALUE;
    private static long cachedPadStamp = 0L;

    /**
     * How far the SCHEMATIC's built platform extends past the tile grid, in blocks.
     *
     * <p>The playable grid is only the checkerboard; the schematic around it adds a border ring,
     * steps and outskirts that are all flush with the arena floor. Cloud measured from the grid
     * line therefore starts on top of that stonework. This walks outward one ring at a time and
     * keeps going while most of the ring's columns still have their top surface exactly at the
     * floor level - natural terrain beyond the build steps up or down, and water/void has no
     * surface there at all, so the count stops at the schematic's real edge.
     *
     * <p>Cached per arena and re-measured every couple of seconds, since mid-fight terrain edits
     * (dug pits, placed walls, a bridged void tile) can move the edge.
     */
    private static float schematicPad(MinecraftClient mc, float floorY) {
        int ox = CombatState.getArenaOriginX();
        int oz = CombatState.getArenaOriginZ();
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        long key = (((long) ox) << 40) ^ (((long) oz) << 16) ^ (w * 31L + h);
        long now = System.currentTimeMillis();
        if (key == cachedPadKey && now - cachedPadStamp < 2000L) return cachedPad;

        int floor = (int) floorY - 1;   // the floor BLOCK, one below the walkable surface
        int pad = 0;
        for (int ring = 1; ring <= MAX_PAD; ring++) {
            int sampled = 0;
            int flush = 0;
            // Sample the four sides of this ring at a coarse stride - enough to tell a built
            // platform from open terrain without a blockstate lookup per column.
            for (int t = -ring; t <= w + ring; t += 2) {
                sampled += 2;
                if (isFlushWithFloor(mc, ox + t, floor, oz - ring)) flush++;
                if (isFlushWithFloor(mc, ox + t, floor, oz + h + ring)) flush++;
            }
            for (int t = -ring; t <= h + ring; t += 2) {
                sampled += 2;
                if (isFlushWithFloor(mc, ox - ring, floor, oz + t)) flush++;
                if (isFlushWithFloor(mc, ox + w + ring, floor, oz + t)) flush++;
            }
            if (sampled == 0 || (float) flush / sampled < FLUSH_FRACTION) break;
            pad = ring;
        }

        cachedPad = pad;
        cachedPadKey = key;
        cachedPadStamp = now;
        return cachedPad;
    }

    /** True when this column's surface is the arena's own floor level: solid at the floor block,
     *  open directly above it. That pairing is what the built platform looks like everywhere. */
    private static boolean isFlushWithFloor(MinecraftClient mc, int x, int floorBlockY, int z) {
        if (mc.world == null) return false;
        BlockPos at = new BlockPos(x, floorBlockY, z);
        BlockPos above = new BlockPos(x, floorBlockY + 1, z);
        return !mc.world.getBlockState(at).isAir()
            && mc.world.getBlockState(at).getFluidState().isEmpty()
            && mc.world.getBlockState(above).isAir();
    }

    /** Horizontal distance from a point to the footprint rectangle; {@code <= 0} means inside. */
    private static float edgeDistance(float x, float z, float minX, float minZ,
                                     float maxX, float maxZ) {
        float dx = Math.max(Math.max(minX - x, x - maxX), 0.0f);
        float dz = Math.max(Math.max(minZ - z, z - maxZ), 0.0f);
        if (dx == 0.0f && dz == 0.0f) return -1.0f;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Density by distance from the footprint edge: nothing in the held-clear gap, ramping to full
     * over {@link #RAMP_IN}, then fading back out before {@link #REACH} so the sea doesn't end on
     * a hard rectangular line. Interior cells (deep bank only) are always full - they sit under
     * the build where there is no edge to respect.
     */
    private static float edgeFalloff(float edge, boolean interior) {
        if (interior && edge <= 0.0f) return 1.0f;
        if (edge < INNER_GAP) return 0.0f;
        float in = Math.min(1.0f, (edge - INNER_GAP) / RAMP_IN);
        float out = Math.min(1.0f, Math.max(0.0f, (REACH - edge) / RAMP_OUT));
        return smoothstep(in) * smoothstep(out);
    }

    /** Two-octave value noise, thresholded into puffs. 0 = clear sky, 1 = solid cloud. */
    private static float coverage(float x, float z, int seed) {
        float n = valueNoise(x, z, seed) * 0.65f + valueNoise(x * 2.7f, z * 2.7f, seed + 31) * 0.35f;
        if (n <= COVER_LOW) return 0.0f;
        if (n >= COVER_HIGH) return 1.0f;
        return smoothstep((n - COVER_LOW) / (COVER_HIGH - COVER_LOW));
    }

    private static float valueNoise(float x, float z, int seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        float fx = smoothstep(x - x0);
        float fz = smoothstep(z - z0);
        float n00 = hash(x0, z0, seed);
        float n10 = hash(x0 + 1, z0, seed);
        float n01 = hash(x0, z0 + 1, seed);
        float n11 = hash(x0 + 1, z0 + 1, seed);
        float top = n00 + (n10 - n00) * fx;
        float bottom = n01 + (n11 - n01) * fx;
        return top + (bottom - top) * fz;
    }

    /** Deterministic 0..1 hash. Stable across clients, so co-op players see the same cloudscape. */
    private static float hash(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0f;
    }

    private static float smoothstep(float t) {
        float c = Math.min(1.0f, Math.max(0.0f, t));
        return c * c * (3.0f - 2.0f * c);
    }

    /**
     * Cloud colour for the arena you're actually standing in: dusty yellow in a desert, white on
     * plains, rain-blue in a jungle, and so on.
     *
     * <p>Sampled at the board's CENTRE, not the camera - the camera can sit outside the stamped
     * arena biome - and driven by an explicit palette rather than the biome's own fog colour,
     * because vanilla hands nearly every overworld biome the same {@code 0xC0D8FF} and the bank
     * would come out identical everywhere.
     *
     * <p>Rain shifts the bank toward a colder storm grey-blue, so a jungle downpour or a snowy
     * blizzard changes the light in the arena rather than looking like a clear day with cloud.
     */
    /**
     * The current arena's cloud colour, or {@code null} when there's no footprint to sample.
     * {@link CrafticsFog} pulls its distance band toward this so the far haze is the same dusty
     * yellow / rain blue / snow white as the cloud banked around the board.
     */
    public static float[] currentTint() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return null;
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        if (w <= 0 || h <= 0) return null;
        return arenaTint(mc,
            CombatState.getArenaOriginX() + w * 0.5f,
            CombatState.getArenaOriginY() + 1.0f,
            CombatState.getArenaOriginZ() + h * 0.5f);
    }

    private static float[] arenaTint(MinecraftClient mc, float centerX, float floorY,
                                     float centerZ) {
        int packed = 0xEDF3FF;
        boolean dark = false;
        if (mc.world != null) {
            BlockPos center = BlockPos.ofFloored(centerX, floorY, centerZ);
            String biome = mc.world.getBiome(center).getKey()
                .map(key -> key.getValue().getPath())
                .orElse("");
            packed = paletteFor(biome);
            dark = isDarkBiome(biome);
            if (mc.world.isRaining()) {
                // Rain over a dark biome deepens it instead of washing it toward storm grey.
                int weather = dark ? 0x000000 : 0x9FB4C8;
                packed = blend(packed, weather, mc.world.isThundering() ? 0.55f : 0.35f);
            }
        }
        float r = ((packed >> 16) & 0xFF) / 255.0f;
        float g = ((packed >> 8) & 0xFF) / 255.0f;
        float b = (packed & 0xFF) / 255.0f;
        // Mood override: boss phase two floods the banks red, a shrouded arena drives them black,
        // victory washes them bright. Drifts in and out on CrafticsFog's own ramp.
        float mood = CrafticsFog.moodStrength();
        int moodRgb = CrafticsFog.moodColor();
        if (mood > 0.0f && moodRgb != 0) {
            float mr = ((moodRgb >> 16) & 0xFF) / 255.0f;
            float mg = ((moodRgb >> 8) & 0xFF) / 255.0f;
            float mb = (moodRgb & 0xFF) / 255.0f;
            r = r + (mr - r) * mood;
            g = g + (mg - g) * mood;
            b = b + (mb - b) * mood;
            // A mood dark enough to read as gloom keeps its darkness past the luminance floor.
            if (0.2126f * r + 0.7152f * g + 0.0722f * b < 0.25f) dark = true;
        }
        if (dark) {
            // Deliberately black fog (dark forest, deep dark). The luminance floor below exists to
            // rescue accidentally-dark colours; applying it here would grey out the whole point.
            return new float[] { r, g, b, 1.0f };
        }
        // Floor the luminance so a night fight still shows cloud rather than a grey smear.
        float luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        float lift = Math.max(0.0f, MIN_LUMINANCE - luminance);
        return new float[] {
            Math.min(1.0f, r + lift), Math.min(1.0f, g + lift), Math.min(1.0f, b + lift), 0.0f
        };
    }

    /**
     * Biomes whose fog is meant to be near-black. These skip the luminance floor and get denser
     * banks - a black cloud at the same alpha as a white one barely registers against night sky,
     * and the whole point of a dark forest arena is that the walls close in.
     */
    private static boolean isDarkBiome(String biome) {
        return biome.contains("dark_forest") || biome.contains("deep_dark");
    }

    /**
     * Biome id path -> cloud colour. Matched on substrings so every variant of a family lands on
     * its family's colour (all four deserts, every jungle, sparse/windswept savanna...) and an
     * unrecognised or modded biome falls through to a neutral overcast white.
     */
    private static int paletteFor(String biome) {
        // NETHER FIRST. crimson_forest and warped_forest both contain "forest", so anything
        // matched after the generic forest rule below never gets reached - which is exactly
        // how the two nether forests ended up wearing the pale overworld-woodland bank.
        // Every nether biome is resolved here, before a single overworld family is tested.
        if (biome.contains("crimson")) return 0xD98A78;             // nether red
        if (biome.contains("warped")) return 0x7CC8BC;              // warped teal
        if (biome.contains("soul_sand")) return 0xB0A292;           // ash grey-brown
        if (biome.contains("basalt")) return 0x9A9490;              // volcanic grey
        if (biome.contains("nether")) return 0xC98070;              // nether wastes

        if (biome.contains("desert")) return 0xE6D49A;              // dusty yellow
        if (biome.contains("badlands")) return 0xE0AC7A;            // ochre dust
        if (biome.contains("savanna")) return 0xDFCF9E;             // dry grass haze
        if (biome.contains("jungle")) return 0x9CC9DE;              // humid rain blue
        if (biome.contains("swamp") || biome.contains("mangrove")) return 0xA8B892;  // murky green
        if (biome.contains("mushroom")) return 0xD3AED8;            // spore violet
        if (biome.contains("cherry")) return 0xF2C6DC;              // blossom pink
        if (biome.contains("snowy") || biome.contains("frozen")
            || biome.contains("ice")) return 0xE4F0FF;              // cold blue-white
        if (biome.contains("taiga") || biome.contains("grove")) return 0xD6E2E8;     // pale alpine
        // Before the generic forest match - dark_forest contains "forest" and must not fall
        // through to the pale one.
        if (biome.contains("dark_forest")) return 0x14161A;         // near-black canopy gloom
        if (biome.contains("forest") || biome.contains("birch")
            || biome.contains("meadow")) return 0xDCE8DA;           // soft green-grey
        if (biome.contains("ocean") || biome.contains("beach")
            || biome.contains("river")) return 0xBCDCEC;            // sea haze
        if (biome.contains("deep_dark")) return 0x7C97A8;           // sculk blue-grey
        if (biome.contains("dripstone") || biome.contains("caves")) return 0xC9B9A6; // cave dust
        if (biome.contains("end") || biome.contains("void")) return 0xCEBCE8;        // end violet
        if (biome.contains("peaks") || biome.contains("slopes")) return 0xEAF2FF;    // summit white
        return 0xEDF3FF;                                            // plains and anything unknown
    }

    /** Blend two packed RGB colours, {@code t} = 0 keeps {@code from}. */
    private static int blend(int from, int to, float t) {
        int r = Math.round(lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t));
        int g = Math.round(lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t));
        int b = Math.round(lerp(from & 0xFF, to & 0xFF, t));
        return (r << 16) | (g << 8) | b;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    // Per-version GPU flush. Mirrors TileOverlayRenderer's split: immediate-mode buffer with
    // explicit RenderSystem state up to 1.21.4, the layered VertexConsumer path on 1.21.5.
    // Depth WRITES are off for both so stacked boxes blend into each other instead of punching
    // each other out, while still being occluded by real terrain.

    //? if <=1.21.4 {
    private static void render(MatrixStack matrices, Camera camera) {
        if (matrices == null || camera == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        List<Face> faces = new ArrayList<>();
        buildFaces(mc, faces);
        if (faces.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        //? if <=1.21.1 {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        //?} else {
        /*RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
        *///?}

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Face f : faces) {
            buffer.vertex(matrix, f.x0(), f.y0(), f.z0()).color(f.r(), f.g(), f.b(), f.a());
            buffer.vertex(matrix, f.x1(), f.y1(), f.z1()).color(f.r(), f.g(), f.b(), f.a());
            buffer.vertex(matrix, f.x2(), f.y2(), f.z2()).color(f.r(), f.g(), f.b(), f.a());
            buffer.vertex(matrix, f.x3(), f.y3(), f.z3()).color(f.r(), f.g(), f.b(), f.a());
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }
    //?} else {
    /*private static void renderV5(WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        if (matrices == null || camera == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        List<Face> faces = new ArrayList<>();
        buildFaces(mc, faces);
        if (faces.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer vc = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        for (Face f : faces) {
            vc.vertex(matrix, f.x0(), f.y0(), f.z0()).color(f.r(), f.g(), f.b(), f.a());
            vc.vertex(matrix, f.x1(), f.y1(), f.z1()).color(f.r(), f.g(), f.b(), f.a());
            vc.vertex(matrix, f.x2(), f.y2(), f.z2()).color(f.r(), f.g(), f.b(), f.a());
            vc.vertex(matrix, f.x3(), f.y3(), f.z3()).color(f.r(), f.g(), f.b(), f.a());
        }

        matrices.pop();
    }
    *///?}
}
