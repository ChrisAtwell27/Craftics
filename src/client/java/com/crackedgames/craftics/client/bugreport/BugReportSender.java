package com.crackedgames.craftics.client.bugreport;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds and ships a bug report to the Craftics website endpoint
 * ({@code POST <endpoint>} multipart/form-data), which forwards it to the
 * Discord bug-report forum. Everything runs off-thread; the {@code onDone}
 * callback is posted back to the client thread.
 *
 * <p>Screenshots are downscaled to {@value #MAX_IMAGE_WIDTH}px wide PNGs
 * before upload (Vercel caps request bodies at ~4.5MB). Uses only JDK
 * imaging/HTTP - no Minecraft rendering or network classes - so the same
 * source compiles on every version shard.
 *
 * <p>If the upload fails for any reason (offline, endpoint down, oversized),
 * the full report is saved to {@code craftics-bugreports/} in the game folder
 * so nothing the player wrote is lost.
 */
public final class BugReportSender {
    private BugReportSender() {}

    /** Result handed back to the UI. */
    public record Result(boolean sent, String detail) {}

    private static final int MAX_IMAGE_WIDTH = 1280;
    private static final int MAX_IMAGES = 3;
    private static final int MAX_LOG_BYTES = 64 * 1024;
    private static final int MAX_LOG_LINES = 300;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Fire the report. Never throws; always calls {@code onDone} on the client thread.
     */
    public static void sendAsync(String endpoint, String title, String summary,
                                 List<File> screenshots, boolean attachLog,
                                 Consumer<Result> onDone) {
        MinecraftClient client = MinecraftClient.getInstance();
        Thread worker = new Thread(() -> {
            Result result = doSend(endpoint, title, summary, screenshots, attachLog);
            client.execute(() -> onDone.accept(result));
        }, "craftics-bugreport");
        worker.setDaemon(true);
        worker.start();
    }

    private static Result doSend(String endpoint, String title, String summary,
                                 List<File> screenshots, boolean attachLog) {
        String meta = gatherMetadata();
        String log = attachLog ? readLogTail() : null;

        List<byte[]> images = new ArrayList<>();
        for (File f : screenshots) {
            if (images.size() >= MAX_IMAGES) break;
            try {
                byte[] png = downscalePng(f);
                if (png != null) images.add(png);
            } catch (Exception e) {
                CrafticsMod.LOGGER.warn("Bug report: could not read screenshot {}", f.getName(), e);
            }
        }

        try {
            String boundary = "----CrafticsBugReport" + UUID.randomUUID();
            byte[] body = buildMultipart(boundary, title, summary, meta, log, images);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Craftics-Report", modVersion())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

            HttpClient http = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new Result(true, "Report sent. Thank you!");
            }
            CrafticsMod.LOGGER.warn("Bug report endpoint returned {}: {}",
                response.statusCode(), truncate(response.body(), 200));
            return saveFallback(title, summary, meta, log, screenshots,
                "Server said " + response.statusCode());
        } catch (Exception e) {
            CrafticsMod.LOGGER.warn("Bug report upload failed", e);
            return saveFallback(title, summary, meta, log, screenshots, "No connection");
        }
    }

    // === Metadata ==========================================================

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer("craftics")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    private static String gatherMetadata() {
        MinecraftClient client = MinecraftClient.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("Craftics: ").append(modVersion()).append('\n');
        sb.append("Minecraft: ").append(FabricLoader.getInstance().getModContainer("minecraft")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown")).append('\n');
        sb.append("Loader: ").append(FabricLoader.getInstance().getModContainer("fabricloader")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown")).append('\n');
        sb.append("Java: ").append(System.getProperty("java.version")).append('\n');
        sb.append("OS: ").append(System.getProperty("os.name")).append(' ')
            .append(System.getProperty("os.version")).append('\n');
        try {
            sb.append("Player: ").append(client.getSession().getUsername()).append('\n');
            if (client.getCurrentServerEntry() != null) {
                sb.append("Server: ").append(client.getCurrentServerEntry().address).append('\n');
            } else if (client.isInSingleplayer()) {
                sb.append("Server: singleplayer\n");
            }
        } catch (Exception ignored) {}

        List<ModContainer> mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
        sb.append("Mods (").append(mods.size()).append("): ");
        int listed = 0;
        for (ModContainer mod : mods) {
            String id = mod.getMetadata().getId();
            // Skip the bundled fabric-* modules; they're implied by the API version.
            if (id.startsWith("fabric-")) continue;
            if (listed++ > 0) sb.append(", ");
            if (listed > 60) { sb.append("..."); break; }
            sb.append(id).append('@').append(mod.getMetadata().getVersion().getFriendlyString());
        }
        return sb.toString();
    }

    private static String readLogTail() {
        try {
            Path log = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("logs").resolve("latest.log");
            if (!Files.isRegularFile(log)) return null;
            List<String> lines = Files.readAllLines(log, StandardCharsets.ISO_8859_1);
            int from = Math.max(0, lines.size() - MAX_LOG_LINES);
            String tail = String.join("\n", lines.subList(from, lines.size()));
            if (tail.length() > MAX_LOG_BYTES) {
                tail = tail.substring(tail.length() - MAX_LOG_BYTES);
            }
            return tail;
        } catch (Exception e) {
            CrafticsMod.LOGGER.warn("Bug report: could not read latest.log", e);
            return null;
        }
    }

    // === Screenshot processing =============================================

    /** Read a PNG and downscale to at most {@link #MAX_IMAGE_WIDTH} wide. Null on failure. */
    private static byte[] downscalePng(File file) throws IOException {
        BufferedImage src = ImageIO.read(file);
        if (src == null) return null;
        BufferedImage out = src;
        if (src.getWidth() > MAX_IMAGE_WIDTH) {
            int h = Math.max(1, (int) ((long) src.getHeight() * MAX_IMAGE_WIDTH / src.getWidth()));
            out = new BufferedImage(MAX_IMAGE_WIDTH, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, MAX_IMAGE_WIDTH, h, null);
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", baos);
        return baos.toByteArray();
    }

    // === Multipart =========================================================

    private static byte[] buildMultipart(String boundary, String title, String summary,
                                         String meta, String log, List<byte[]> images)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeField(out, boundary, "title", title);
        writeField(out, boundary, "summary", summary);
        writeField(out, boundary, "meta", meta);
        if (log != null && !log.isEmpty()) {
            writeFile(out, boundary, "log", "latest-log.txt", "text/plain",
                log.getBytes(StandardCharsets.UTF_8));
        }
        for (int i = 0; i < images.size(); i++) {
            writeFile(out, boundary, "screenshot" + i, "screenshot" + i + ".png",
                "image/png", images.get(i));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream out, String boundary,
                                   String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFile(ByteArrayOutputStream out, String boundary, String name,
                                  String filename, String contentType, byte[] data)
            throws IOException {
        out.write(("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
            + "Content-Type: " + contentType + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    // === Offline fallback ==================================================

    private static Result saveFallback(String title, String summary, String meta, String log,
                                       List<File> screenshots, String reason) {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path dir = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("craftics-bugreports").resolve("report-" + stamp);
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            sb.append("TITLE: ").append(title).append("\n\n")
              .append(summary).append("\n\n=== METADATA ===\n").append(meta).append('\n');
            Files.writeString(dir.resolve("report.txt"), sb.toString(), StandardCharsets.UTF_8);
            if (log != null && !log.isEmpty()) {
                Files.writeString(dir.resolve("latest-log.txt"), log, StandardCharsets.UTF_8);
            }
            for (File shot : screenshots) {
                try {
                    Files.copy(shot.toPath(), dir.resolve(shot.getName()),
                        StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {}
            }
            return new Result(false, reason + ". Saved to craftics-bugreports/report-" + stamp);
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("Bug report fallback save failed", e);
            return new Result(false, reason + ", and saving locally failed too.");
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
