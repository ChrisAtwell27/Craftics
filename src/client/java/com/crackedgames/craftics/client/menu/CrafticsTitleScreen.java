package com.crackedgames.craftics.client.menu;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.client.music.MusicManager;
import com.crackedgames.craftics.sound.MusicDirector;
import com.crackedgames.craftics.sound.MusicTracks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Craftics' replacement for the vanilla title screen (swapped in by
 * {@code TitleScreenMixin}). Designed to read as the front door of a tactics
 * game rather than Minecraft's menu, while every pixel of content is the
 * player's own run:
 *
 * <ul>
 *   <li><b>Backdrop</b> - the biome card art of the biome the player is
 *       currently on in their most recent world, slowly zooming/panning
 *       (Ken Burns), cross-fading through every biome they've discovered.</li>
 *   <li><b>Hero CONTINUE card</b> - world name, current biome, campaign
 *       progress and NG+ tier; one click boots the integrated server straight
 *       into that world.</li>
 *   <li><b>Campaign strip</b> - the full biome path as cleared / current /
 *       locked nodes, mirroring the in-game world select.</li>
 *   <li><b>Music</b> - the current biome's own battle theme (vanilla menu
 *       music is suppressed mod-wide, so without this the menu is silent).</li>
 * </ul>
 *
 * All world/progress data comes from {@link MenuWorldState} (async disk scan);
 * until it lands the menu renders with fresh-run defaults, so the screen is
 * usable instantly.
 */
public class CrafticsTitleScreen extends Screen {

    private static final Identifier LOGO =
        Identifier.of("craftics", "textures/gui/title/craftics_title.png");

    /** Cross-fade time when the backdrop biome changes (hover / pin / load). */
    private static final float BACKDROP_FADE_SECONDS = 0.65f;
    private static final int PARTICLE_COUNT = 44;

    // ── async world snapshot ────────────────────────────────────────────
    private CompletableFuture<MenuWorldState.Snapshot> pending;
    private MenuWorldState.Snapshot snap;
    private boolean musicStarted;

    // ── animation clock (wall time; render delta is a tick fraction) ────
    private long lastFrameMs = -1;
    private float t;

    // ── backdrop state ──────────────────────────────────────────────────
    // The backdrop rests on the player's current (farthest) biome. Hovering a
    // discovered node on the campaign strip previews that biome; clicking pins
    // it (persisted via MenuPrefs). Priority: hover > pin > current.
    private String shownBiome;
    private String prevBiome;
    private float backdropFade = 1f;
    /** Discovered strip node under the cursor this frame, or null. */
    private String hoverBiome;
    /** Player-pinned backdrop biome, or null for "follow my progress". */
    private String chosenBiome;
    private final Map<Identifier, Boolean> textureExists = new HashMap<>();

    // ── drifting particles (normalized 0..1 coords) ─────────────────────
    private float[] px, py, pSpeed, pSeed, pSize;

    /** One-shot switch of the logo texture to bilinear filtering. */
    private boolean logoFiltered;

    /** Splash line beside the logo; picked once per screen visit. */
    private String splash;

    // ── menu entries (custom-rendered; manual hit boxes like LevelSelect) ─
    private final List<Entry> entries = new ArrayList<>();
    private float[] hoverT = new float[0];
    private int keyboardSel = -1;

    /** Progress-strip node hit boxes from the last frame: {x1,y1,x2,y2,pathIndex}. */
    private final List<int[]> nodeBounds = new ArrayList<>();

    private static final class Entry {
        final String label;
        final boolean hero;
        final boolean enabled;
        final Runnable action;
        int x, y, w, h;

        Entry(String label, boolean hero, boolean enabled, Runnable action) {
            this.label = label;
            this.hero = hero;
            this.enabled = enabled;
            this.action = action;
        }
    }

    public CrafticsTitleScreen() {
        super(Text.literal("Craftics"));
        this.chosenBiome = MenuPrefs.loadBackdropBiome();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        if (snap == null && pending == null) {
            pending = MenuWorldState.load(this.client);
        }
        rebuildEntries();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Esc must not close: setScreen(null) with no world would spawn a fresh
        // TitleScreen, which the mixin swaps right back - a pointless rebuild.
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void rebuildEntries() {
        entries.clear();
        boolean compact = this.height < 300;

        if (snap != null && snap.hasWorld()) {
            entries.add(new Entry("CONTINUE", true, true, this::continueGame));
        } else if (snap != null) {
            entries.add(new Entry("BEGIN YOUR RUN", true, true,
                () -> this.client.setScreen(new SelectWorldScreen(this))));
        } else {
            entries.add(new Entry("READING WORLDS...", true, false, () -> {}));
        }
        entries.add(new Entry("WORLDS", false, true,
            () -> this.client.setScreen(new SelectWorldScreen(this))));
        entries.add(new Entry("MULTIPLAYER", false, this.client.isMultiplayerEnabled(),
            () -> this.client.setScreen(new MultiplayerScreen(this))));
        entries.add(new Entry("OPTIONS", false, true,
            () -> this.client.setScreen(new OptionsScreen(this, this.client.options))));
        entries.add(new Entry("QUIT", false, true, () -> this.client.scheduleStop()));

        if (hoverT.length != entries.size()) {
            hoverT = new float[entries.size()];
        }

        // Column layout under the logo.
        int marginX = compact ? 22 : 30;
        int logoH = logoHeight();
        int y = logoTop() + logoH + (compact ? 10 : 22);
        int menuW = Math.max(Math.min((int) (this.width * 0.34f), 340), compact ? 185 : 230);
        int heroH = compact ? 46 : 58;
        int btnH = compact ? 20 : 24;
        int gap = compact ? 5 : 7;

        for (Entry e : entries) {
            e.x = marginX;
            e.y = y;
            e.w = menuW;
            e.h = e.hero ? heroH : btnH;
            y += e.h + gap;
        }
        if (keyboardSel >= entries.size()) keyboardSel = -1;
    }

    private int logoTop() {
        return this.height < 300 ? 14 : 26;
    }

    private int logoWidth() {
        // The rebuilt art is a wide, short strip (873x177), so it can run
        // noticeably wider than the menu column without eating vertical space.
        return Math.max(Math.min((int) (this.width * 0.48f), 560), 200);
    }

    private int logoHeight() {
        // Source art is 873x177 - the original logo rebuilt with a tight
        // outline (the raw export bakes in a very thick outline + shadow blob).
        return logoWidth() * 177 / 873;
    }

    /** Fade the menu theme and boot the integrated server into the last world. */
    private void continueGame() {
        if (snap == null || !snap.hasWorld()) return;
        MusicManager.request("");
        try {
            this.client.createIntegratedServerLoader()
                .start(snap.worldFolder(), () -> this.client.setScreen(this));
        } catch (Exception e) {
            CrafticsMod.LOGGER.warn("Craftics menu: failed to quick-load '{}', opening world list",
                snap.worldFolder(), e);
            this.client.setScreen(new SelectWorldScreen(this));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        float dt = lastFrameMs < 0 ? 0f : Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        t += dt;

        pollSnapshot();

        renderBackdrop(context, dt);
        // The biome card art is chunky pixel art; blown up to full screen it
        // reads as giant hard-edged blocks. Flush the batched backdrop draws,
        // then run vanilla's menu-background gaussian blur over them so the
        // backdrop becomes soft atmosphere. Everything after this stays crisp.
        context.draw();
        //? if <=1.21.1 {
        this.applyBlur(delta);
        //?} else {
        /*this.applyBlur();
        *///?}
        renderOverlays(context);
        renderParticles(context, dt);
        renderLogo(context);
        renderMenu(context, mouseX, mouseY, dt);
        renderPathStrip(context, mouseX, mouseY);
        renderCaptions(context);
    }

    private void pollSnapshot() {
        if (pending != null && pending.isDone()) {
            snap = pending.join(); // load() never completes exceptionally
            pending = null;
            rebuildEntries();
        }
        if (splash == null && (snap != null || pending == null)) {
            splash = CrafticsSplashes.pick(new Random(), snap);
        }
        if (snap != null && !musicStarted) {
            musicStarted = true;
            MusicTracks track = MusicDirector.biomeTrack(snap.biomeId());
            if (track != null) {
                MusicManager.request(track.key);
            }
        }
    }

    // ── backdrop ─────────────────────────────────────────────────────────

    /** True when this biome may be shown as a backdrop (no spoilers). */
    private boolean isBackdropAllowed(String biomeId) {
        if (snap == null || biomeId == null) return false;
        for (MenuWorldState.NodeView node : snap.path()) {
            if (node.biomeId().equals(biomeId)) {
                return node.discovered() || node.cleared() || node.current();
            }
        }
        return false;
    }

    /** hover > pinned choice > the biome the player is on. */
    private String targetBackdropBiome() {
        if (hoverBiome != null) return hoverBiome;
        if (chosenBiome != null && isBackdropAllowed(chosenBiome)) return chosenBiome;
        return snap != null ? snap.biomeId() : "plains";
    }

    private void renderBackdrop(DrawContext context, float dt) {
        String target = targetBackdropBiome();
        if (shownBiome == null) {
            shownBiome = target;
        } else if (!target.equals(shownBiome)) {
            prevBiome = shownBiome;
            shownBiome = target;
            backdropFade = 0f;
        }
        if (backdropFade < 1f) {
            backdropFade = Math.min(1f, backdropFade + dt / BACKDROP_FADE_SECONDS);
        }

        if (backdropFade < 1f && prevBiome != null) {
            drawBiomeCover(context, prevBiome, 1f, variantOf(prevBiome));
        }
        drawBiomeCover(context, shownBiome, backdropFade, variantOf(shownBiome));
    }

    /** Stable per-biome phase for the Ken Burns drift. */
    private static int variantOf(String biomeId) {
        return biomeId == null ? 0 : (biomeId.hashCode() & 7);
    }

    /** Cover-fit one biome's card art over the whole screen with a slow drift. */
    private void drawBiomeCover(DrawContext context, String biomeId, float alpha, int variant) {
        Identifier tex = Identifier.of("craftics", "textures/gui/biomes/" + biomeId + ".png");
        if (!textureResourceExists(tex)) {
            // No card art (custom campaign biome): moody flat backdrop in the
            // run's accent color so the menu still reads as themed.
            int accent = snap != null ? snap.regionColor() : 0xFF223044;
            context.fill(0, 0, this.width, this.height,
                withAlpha(mix(0xFF090D16, accent, 0.18f), alpha));
            return;
        }

        // Ken Burns: gentle zoom + pan, phase-shifted per image so consecutive
        // backdrops never move in lockstep. Kept subtle - the art is square and
        // cover-fitting a wide screen already crops it; more zoom reads as
        // "picture blown up too far".
        float zoom = 1.045f + 0.025f * (float) Math.sin(t * 0.11f + variant * 1.7f);
        float side = Math.max(this.width, this.height) * zoom;
        float panX = (float) Math.sin(t * 0.083f + variant * 2.3f) * side * 0.020f;
        float panY = (float) Math.cos(t * 0.067f + variant * 1.1f) * side * 0.015f;
        float x = (this.width - side) / 2f + panX;
        float y = (this.height - side) / 2f + panY;

        // Float translate + float scale of a fixed-size draw keeps the drift
        // sub-pixel smooth; rounding the size to whole pixels made the zoom
        // visibly step.
        float base = 1024f;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0f);
        context.getMatrices().scale(side / base, side / base, 1f);
        drawTexTinted(context, tex, 0, 0, (int) base, (int) base, 1f, 1f, 1f, alpha);
        context.getMatrices().pop();
    }

    // ── readability overlays ─────────────────────────────────────────────

    private void renderOverlays(DrawContext context) {
        // Global vertical mood gradient.
        context.fillGradient(0, 0, this.width, this.height, 0x50060913, 0xA8050810);

        // Left panel gradient behind logo + menu.
        int panelW = (int) (this.width * 0.55f);
        int steps = 32;
        for (int i = 0; i < steps; i++) {
            float f = 1f - i / (float) steps;
            int a = (int) (f * f * 0xB4);
            int x1 = panelW * i / steps;
            int x2 = panelW * (i + 1) / steps;
            context.fill(x1, 0, x2, this.height, (a << 24) | 0x05070E);
        }

        // Bottom band for the campaign strip / captions.
        context.fillGradient(0, this.height - 70, this.width, this.height, 0x00000000, 0x99000000);
    }

    // ── particles ────────────────────────────────────────────────────────

    private void renderParticles(DrawContext context, float dt) {
        if (px == null) {
            Random rng = new Random(0xC4A7F1C5L);
            px = new float[PARTICLE_COUNT];
            py = new float[PARTICLE_COUNT];
            pSpeed = new float[PARTICLE_COUNT];
            pSeed = new float[PARTICLE_COUNT];
            pSize = new float[PARTICLE_COUNT];
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                px[i] = rng.nextFloat();
                py[i] = rng.nextFloat();
                pSpeed[i] = 0.012f + rng.nextFloat() * 0.028f;
                pSeed[i] = rng.nextFloat();
                pSize[i] = rng.nextFloat() < 0.3f ? 2f : 1f;
            }
        }

        int accent = snap != null ? snap.regionColor() : 0xFFB8C8E8;
        int bright = mix(accent, 0xFFFFFFFF, 0.55f);

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            py[i] -= pSpeed[i] * dt;                     // rise like embers/spores
            if (py[i] < -0.04f) {
                py[i] = 1.04f;
                px[i] = (px[i] * 7919f) % 1f;            // cheap deterministic re-roll
                if (px[i] < 0) px[i] += 1f;
            }
            float sway = (float) Math.sin(t * 0.6f + pSeed[i] * 6.283f) * 0.008f;
            float sx = (px[i] + sway) * this.width;
            float sy = py[i] * this.height;
            float twinkle = 0.5f + 0.5f * (float) Math.sin(t * 0.8f + pSeed[i] * 9f);
            int a = (int) ((0.10f + 0.26f * twinkle) * 255f);
            int size = (int) pSize[i] + (pSize[i] > 1 ? 1 : 0);
            // Sub-pixel positioning via matrix translate - int coordinates made
            // the slow drift stutter one GUI pixel (= several screen pixels) at
            // a time.
            context.getMatrices().push();
            context.getMatrices().translate(sx, sy, 0f);
            context.fill(0, 0, size, size, (a << 24) | (bright & 0x00FFFFFF));
            context.getMatrices().pop();
        }
    }

    // ── logo ─────────────────────────────────────────────────────────────

    private void renderLogo(DrawContext context) {
        // The art bakes in its own outline and shadow; nearest-neighbor scaling
        // turns those into craggy blobs, so give this one texture bilinear
        // filtering (it isn't used anywhere else). No extra drawn shadow.
        if (!logoFiltered) {
            logoFiltered = true;
            try {
                this.client.getTextureManager().getTexture(LOGO).setFilter(true, false);
            } catch (Exception e) {
                CrafticsMod.LOGGER.warn("Craftics menu: could not smooth logo texture", e);
            }
        }

        int lw = logoWidth();
        int lh = logoHeight();
        int lx = this.height < 300 ? 22 : 30;
        int ly = logoTop();

        // The bob rides a float matrix translate so it glides instead of
        // stepping whole GUI pixels; the splash is drawn inside the same
        // transform so it moves with the logo.
        float bob = (float) Math.sin(t * 0.8f) * 2.5f;
        context.getMatrices().push();
        context.getMatrices().translate(0f, bob, 0f);
        drawTex(context, LOGO, lx, ly, lw, lh);

        if (splash != null) {
            float pulse = 1.0f - 0.06f * Math.abs((float) Math.sin(t * (float) Math.PI * 2f));
            float scale = Math.min(1.35f, 115f / Math.max(1, this.textRenderer.getWidth(splash)))
                * pulse;
            context.getMatrices().push();
            context.getMatrices().translate(lx + lw * 0.92f, ly + lh * 0.86f, 0f);
            context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-15f));
            context.getMatrices().scale(scale, scale, 1f);
            context.drawCenteredTextWithShadow(this.textRenderer, splash, 0, -4, 0xFFFFFF00);
            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    // ── menu column ──────────────────────────────────────────────────────

    private void renderMenu(DrawContext context, int mouseX, int mouseY, float dt) {
        int accent = snap != null ? snap.regionColor() : 0xFF55FF55;

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            boolean hovered = e.enabled
                && ((mouseX >= e.x && mouseX < e.x + e.w && mouseY >= e.y && mouseY < e.y + e.h)
                    || keyboardSel == i);

            hoverT[i] = clamp01(hoverT[i] + (hovered ? dt * 8f : -dt * 8f));
            float ease = hoverT[i] * hoverT[i] * (3f - 2f * hoverT[i]);
            int x = e.x + (int) (ease * 6f);

            int bg = e.enabled
                ? mix(0xC00D1220, 0xE61A2436, ease)
                : 0x900A0E18;
            context.fill(x, e.y, x + e.w, e.y + e.h, bg);
            // Bevel: faint light top edge, dark bottom edge.
            context.fill(x, e.y, x + e.w, e.y + 1, 0x16FFFFFF);
            context.fill(x, e.y + e.h - 1, x + e.w, e.y + e.h, 0x50000000);
            // Accent bar.
            int barA = e.enabled ? (int) ((0.55f + 0.45f * ease) * 255f) : 0x44;
            int barColor = e.enabled ? accent : 0xFF445566;
            context.fill(x, e.y, x + 3, e.y + e.h, (barA << 24) | (barColor & 0x00FFFFFF));

            if (e.hero) {
                renderHeroContent(context, e, x, ease, accent);
            } else {
                int labelColor = e.enabled ? (ease > 0.01f ? 0xFFFFFFFF : 0xFFC8CFDD) : 0xFF5A6272;
                context.drawTextWithShadow(this.textRenderer, e.label,
                    x + 14, e.y + (e.h - 8) / 2 + 1, labelColor);
            }

            // Hover arrow on the right edge.
            if (ease > 0.05f && e.enabled) {
                int aa = (int) (ease * 255f);
                context.drawTextWithShadow(this.textRenderer, "▶",
                    x + e.w - 14, e.y + (e.h - 8) / 2 + 1, (aa << 24) | 0x00FFFFFF);
            }
        }
    }

    private void renderHeroContent(DrawContext context, Entry e, int x, float ease, int accent) {
        boolean compact = e.h < 52;
        float scale = compact ? 1.15f : 1.35f;
        int textX = x + 14;

        // Big title.
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1f);
        int titleColor = e.enabled ? 0xFFFFFFFF : 0xFF7A8292;
        context.drawTextWithShadow(this.textRenderer, "§l" + e.label,
            (int) (textX / scale), (int) ((e.y + (compact ? 5 : 7)) / scale), titleColor);
        context.getMatrices().pop();

        if (snap == null) return;

        // Biome thumbnail on the right of the card.
        int thumb = e.h - 10;
        int tx = x + e.w - thumb - 6;
        int ty = e.y + 5;
        if (snap.hasWorld()) {
            Identifier art = Identifier.of("craftics",
                "textures/gui/biomes/" + snap.biomeId() + ".png");
            if (textureResourceExists(art)) {
                drawTex(context, art, tx, ty, thumb, thumb);
            } else {
                context.fill(tx, ty, tx + thumb, ty + thumb, snap.regionColor());
                context.fill(tx + 2, ty + 2, tx + thumb - 2, ty + thumb - 2,
                    mix(snap.regionColor(), 0xFF000000, 0.4f));
            }
            int borderA = (int) ((0.5f + 0.5f * ease) * 255f);
            drawBorder(context, tx - 1, ty - 1, thumb + 2, thumb + 2,
                (borderA << 24) | (accent & 0x00FFFFFF));
        }

        int infoRight = snap.hasWorld() ? tx - 8 : x + e.w - 8;

        // World name.
        int line1Y = e.y + (compact ? 5 + 12 : 7 + 15);
        if (snap.hasWorld()) {
            String world = trimToWidth(snap.worldName(), infoRight - textX);
            context.drawTextWithShadow(this.textRenderer, world, textX, line1Y, 0xFF98A2B4);
        } else {
            context.drawTextWithShadow(this.textRenderer, "Create a world to start the campaign",
                textX, line1Y, 0xFF98A2B4);
        }

        // Biome + progress line. Long biome names get trimmed; the progress
        // numbers and NG+ tag always survive intact.
        if (snap.hasWorld()) {
            int cleared = Math.max(0, Math.min(snap.highestUnlocked() - 1, snap.totalBiomes()));
            String suffix = "  §8· §7" + cleared + "/" + snap.totalBiomes()
                + (snap.ngPlus() > 0 ? "  §6★ NG+" + snap.ngPlus() : "");
            String name = snap.regionIcon() + " " + snap.biomeName();
            int avail = infoRight - textX;
            int nameMax = avail - this.textRenderer.getWidth(suffix);
            String line = nameMax >= 40
                ? trimToWidth(name, nameMax) + suffix
                : trimToWidth(name + suffix, avail);
            context.drawTextWithShadow(this.textRenderer, line, textX, line1Y + 11, accent);
        }
    }

    // ── campaign path strip (bottom-right) ───────────────────────────────

    private void renderPathStrip(DrawContext context, int mouseX, int mouseY) {
        nodeBounds.clear();
        hoverBiome = null;
        if (snap == null || snap.path().isEmpty()) return;
        List<MenuWorldState.NodeView> path = snap.path();

        int dot = 6;
        int gap = 4;
        int regionGap = 12;

        // Measure: nodes plus a region glyph at each region change.
        int w = 0;
        String lastRegion = null;
        for (MenuWorldState.NodeView node : path) {
            if (!node.regionId().equals(lastRegion)) {
                w += (lastRegion == null ? 0 : regionGap) + 11;
                lastRegion = node.regionId();
            }
            w += dot + gap;
        }
        w -= gap;

        int xRight = this.width - 24;
        int x = xRight - w;
        int y = this.height - 22;
        if (x < 24) return; // window too narrow - drop the strip

        // Connector line behind everything.
        context.fill(x, y + dot / 2, xRight, y + dot / 2 + 1, 0x30FFFFFF);

        lastRegion = null;
        int hoveredIdx = -1;
        int hoveredX = 0;
        for (int i = 0; i < path.size(); i++) {
            MenuWorldState.NodeView node = path.get(i);
            if (!node.regionId().equals(lastRegion)) {
                if (lastRegion != null) x += regionGap;
                // Region marker: a slightly taller square in the region's
                // color - matches the pixel-square language of the nodes
                // (glyph icons looked out of place here).
                context.fill(x, y - 1, x + 8, y + dot + 1, node.regionColor());
                context.fill(x + 1, y, x + 7, y + dot,
                    mix(node.regionColor(), 0xFF000000, 0.35f));
                x += 11;
                lastRegion = node.regionId();
            }

            int color;
            if (node.current()) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4f);
                color = 0xFFFFC94A;
                int ringA = (int) ((0.35f + 0.5f * pulse) * 255f);
                context.fill(x - 2, y - 2, x + dot + 2, y + dot + 2,
                    (ringA << 24) | 0x00FFC94A);
            } else if (node.cleared()) {
                color = 0xFF55CC55;
            } else {
                color = node.discovered() ? 0xFF39415A : 0xFF232A3D;
            }
            context.fill(x, y, x + dot, y + dot, color);

            boolean hovered = mouseX >= x - 2 && mouseX < x + dot + 2
                && mouseY >= y - 4 && mouseY < y + dot + 4;
            if (hovered) {
                hoveredIdx = i;
                hoveredX = x + dot / 2;
                context.fill(x - 1, y - 1, x + dot + 1, y + dot + 1, 0x66FFFFFF);
                context.fill(x, y, x + dot, y + dot, color);
                // Live backdrop preview for discovered biomes (picked up next
                // frame; undiscovered ones stay a mystery).
                if (node.discovered() || node.cleared() || node.current()) {
                    hoverBiome = node.biomeId();
                }
            }
            // Pin marker: a small white notch under the biome the player has
            // pinned as their backdrop.
            if (chosenBiome != null && chosenBiome.equals(node.biomeId())) {
                context.fill(x + dot / 2 - 1, y + dot + 2, x + dot / 2 + 1, y + dot + 4, 0xCCFFFFFF);
            }
            nodeBounds.add(new int[] { x - 2, y - 4, x + dot + 2, y + dot + 4, i });
            x += dot + gap;
        }

        // Hover label above the strip. Undiscovered biomes stay a mystery.
        if (hoveredIdx >= 0) {
            MenuWorldState.NodeView node = path.get(hoveredIdx);
            boolean known = node.discovered() || node.cleared() || node.current();
            String name = known ? node.displayName() : "???";
            if (node.current()) name = "§e" + name + " §7(you are here)";
            else if (known) name = name + " §8· §7click to pin";
            int tw = this.textRenderer.getWidth(name);
            int lx = Math.max(8, Math.min(hoveredX - tw / 2, this.width - tw - 8));
            context.fill(lx - 3, y - 17, lx + tw + 3, y - 5, 0xC8060A14);
            context.drawTextWithShadow(this.textRenderer, name, lx, y - 15, 0xFFE8ECF4);
        }
    }

    // ── captions ─────────────────────────────────────────────────────────

    private void renderCaptions(DrawContext context) {
        // Top-right: version stamp + now playing.
        // Strip the "+<mc>" build metadata - the MC version is shown separately.
        String version = "Craftics v" + FabricLoader.getInstance().getModContainer("craftics")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .map(v -> v.indexOf('+') > 0 ? v.substring(0, v.indexOf('+')) : v)
            .orElse("dev");
        String mc = FabricLoader.getInstance().getModContainer("minecraft")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        String stamp = version + (mc.isEmpty() ? "" : " §8·§7 MC " + mc);
        context.drawTextWithShadow(this.textRenderer, stamp,
            this.width - this.textRenderer.getWidth(stamp) - 8, 8, 0x88AAB4C6);

        if (snap != null) {
            MusicTracks track = MusicDirector.biomeTrack(snap.biomeId());
            if (track != null) {
                // Track name and source on separate lines - one combined line
                // can run long enough to collide with the logo.
                int maxW = this.width / 2;
                String l1 = trimToWidth("♪ " + track.displayName, maxW);
                String l2 = trimToWidth(track.source, maxW);
                context.drawTextWithShadow(this.textRenderer, l1,
                    this.width - this.textRenderer.getWidth(l1) - 8, 20, 0x7790A0B8);
                context.drawTextWithShadow(this.textRenderer, l2,
                    this.width - this.textRenderer.getWidth(l2) - 8, 31, 0x66707E94);
            }
        }

        // Bottom-right, above the strip: which biome the backdrop is showing.
        if (snap != null) {
            String showing = shownBiome != null ? shownBiome : snap.biomeId();
            String name = MenuWorldState.prettify(showing);
            String icon = snap.regionIcon();
            int color = snap.regionColor();
            for (MenuWorldState.NodeView node : snap.path()) {
                if (node.biomeId().equals(showing)) {
                    name = node.displayName();
                    icon = node.regionIcon();
                    color = node.regionColor();
                    break;
                }
            }
            String label = showing.equals(snap.biomeId())
                ? "§7YOU ARE HERE"
                : (showing.equals(chosenBiome) ? "§7PINNED" : "§8DISCOVERED");
            String line = icon + " " + name.toUpperCase();
            int lw = this.textRenderer.getWidth(line);
            int lx = this.width - lw - 24;
            context.drawTextWithShadow(this.textRenderer, label,
                this.width - this.textRenderer.getWidth(label) - 24, this.height - 46, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, line, lx, this.height - 35, color);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Entry e : entries) {
                if (e.enabled && mouseX >= e.x && mouseX < e.x + e.w
                        && mouseY >= e.y && mouseY < e.y + e.h) {
                    playClick();
                    e.action.run();
                    return true;
                }
            }
            // Campaign strip: click a discovered node to pin it as the
            // backdrop (persisted); clicking the pinned node unpins it so the
            // backdrop follows progress again.
            for (int[] nb : nodeBounds) {
                if (mouseX >= nb[0] && mouseX < nb[2] && mouseY >= nb[1] && mouseY < nb[3]) {
                    if (snap == null || nb[4] >= snap.path().size()) return true;
                    MenuWorldState.NodeView node = snap.path().get(nb[4]);
                    if (node.discovered() || node.cleared() || node.current()) {
                        playClick();
                        if (node.biomeId().equals(chosenBiome)) {
                            chosenBiome = null;
                            MenuPrefs.saveBackdropBiome(null);
                        } else {
                            chosenBiome = node.biomeId();
                            MenuPrefs.saveBackdropBiome(chosenBiome);
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 265 = UP, 264 = DOWN, 257/335 = ENTER
        if (keyCode == 265 || keyCode == 264) {
            int dir = keyCode == 265 ? -1 : 1;
            int n = entries.size();
            int idx = keyboardSel < 0 ? (dir > 0 ? -1 : 0) : keyboardSel;
            for (int step = 1; step <= n; step++) {
                int candidate = ((idx + dir * step) % n + n) % n;
                if (entries.get(candidate).enabled) {
                    keyboardSel = candidate;
                    break;
                }
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            Entry target = keyboardSel >= 0 && keyboardSel < entries.size()
                ? entries.get(keyboardSel) : entries.get(0);
            if (target.enabled) {
                playClick();
                target.action.run();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // Mouse takes over from keyboard highlighting.
        if (keyboardSel >= 0) {
            Entry e = entries.get(keyboardSel);
            if (!(mouseX >= e.x && mouseX < e.x + e.w && mouseY >= e.y && mouseY < e.y + e.h)) {
                keyboardSel = -1;
            }
        }
        super.mouseMoved(mouseX, mouseY);
    }

    private void playClick() {
        this.client.getSoundManager().play(
            PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Draw helpers
    // ─────────────────────────────────────────────────────────────────────

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Stretch-draw a whole texture into the given rect. */
    private void drawTex(DrawContext context, Identifier tex, int x, int y, int w, int h) {
        //? if <=1.21.1 {
        context.drawTexture(tex, x, y, 0f, 0f, w, h, w, h);
        //?} else {
        /*context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured,
            tex, x, y, 0f, 0f, w, h, w, h);
        *///?}
    }

    /** Stretch-draw a whole texture with an RGBA tint (used for fades/shadows). */
    private void drawTexTinted(DrawContext context, Identifier tex, int x, int y, int w, int h,
                               float r, float g, float b, float a) {
        if (a <= 0.004f) return;
        //? if <=1.21.1 {
        context.setShaderColor(r, g, b, a);
        context.drawTexture(tex, x, y, 0f, 0f, w, h, w, h);
        context.setShaderColor(1f, 1f, 1f, 1f);
        //?} else {
        /*int color = ((int) (a * 255f) & 0xFF) << 24
            | ((int) (r * 255f) & 0xFF) << 16
            | ((int) (g * 255f) & 0xFF) << 8
            | ((int) (b * 255f) & 0xFF);
        context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured,
            tex, x, y, 0f, 0f, w, h, w, h, color);
        *///?}
    }

    /** Whether a texture resolves to a real resource (cached; screen lifetime). */
    private boolean textureResourceExists(Identifier texture) {
        Boolean cached = textureExists.get(texture);
        if (cached != null) return cached;
        boolean exists = false;
        MinecraftClient mc = this.client != null ? this.client : MinecraftClient.getInstance();
        if (mc != null) {
            ResourceManager rm = mc.getResourceManager();
            if (rm != null) {
                exists = rm.getResource(texture).isPresent();
            }
        }
        textureExists.put(texture, exists);
        return exists;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.textRenderer.getWidth(text) <= maxWidth) return text;
        String trimmed = text;
        while (trimmed.length() > 1
                && this.textRenderer.getWidth(trimmed + "…") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "…";
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    /** Blend two ARGB colors: {@code f = 0} gives {@code a}, {@code f = 1} gives {@code b}. */
    private static int mix(int a, int b, float f) {
        f = clamp01(f);
        int aa = (int) (((a >>> 24) & 0xFF) + f * (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)));
        int rr = (int) (((a >> 16) & 0xFF) + f * (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)));
        int gg = (int) (((a >> 8) & 0xFF) + f * (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)));
        int bb = (int) ((a & 0xFF) + f * ((b & 0xFF) - (a & 0xFF)));
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private static int withAlpha(int rgb, float alpha) {
        return (((int) (clamp01(alpha) * 255f) & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }
}
