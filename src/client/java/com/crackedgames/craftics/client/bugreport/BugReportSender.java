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
     * The {@code count} most recently written screenshots, newest first.
     *
     * <p>What the inline {@code /bug <count> <description>} form attaches, and the same
     * newest-first ordering the report screen lists. Capped at {@link #MAX_IMAGES} whatever
     * the caller asks for, since that is what the upload accepts anyway. Returns an empty
     * list when the folder is missing or empty - a report with no pictures still sends.
     */
    public static List<File> recentScreenshots(int count) {
        List<File> out = new ArrayList<>();
        if (count <= 0) return out;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return out;
        File dir = new File(client.runDirectory, "screenshots");
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        });
        if (files == null) return out;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified).reversed());
        for (int i = 0; i < files.length && out.size() < Math.min(count, MAX_IMAGES); i++) {
            out.add(files[i]);
        }
        return out;
    }

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

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Craftics-Report", modVersion());
            String token = CrafticsMod.CONFIG.bugReportToken();
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token.trim());
            }
            HttpRequest request = builder
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
            // The intake's own failure codes, said plainly. A player who is rate limited or
            // blocked needs to know that retrying is pointless, not read "upload failed".
            String reason = switch (response.statusCode()) {
                case 401, 403 -> "Reporting is not available for this client.";
                case 413 -> "Too large - try fewer or smaller screenshots.";
                case 429 -> "You have sent several reports recently. Try again later.";
                case 503 -> "The report service is starting up. Try again shortly.";
                default -> "Server said " + response.statusCode();
            };
            return saveFallback(title, summary, meta, log, screenshots, reason);
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

    /**
     * The reporter's Minecraft name, read from the session rather than typed by anyone.
     *
     * <p>Worth being clear about what this is: a CLAIM. It is accurate for every player using
     * the mod normally, and it is whatever an abuser wants it to be, because the intake cannot
     * tell a real client's POST from a hand-rolled one. Treat it as a label on the report, not
     * as an identity to ban on.
     */
    private static String playerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSession() == null) return "";
        String name = client.getSession().getUsername();
        return name == null ? "" : name;
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
        // Field names are the mcdebug intake contract: version, description, logs, username,
        // screenshots. All three images go under the SAME "screenshots" name - that is what
        // makes multer collect them as an array rather than only seeing the last one.
        writeField(out, boundary, "version", modVersion());
        // The title is the first line of the description rather than its own field: the
        // intake has no title, and dropping it would lose what the form asked the player for.
        String break2 = System.lineSeparator() + System.lineSeparator();
        writeField(out, boundary, "description",
            (title == null || title.isBlank() ? "" : title + break2) + summary
                + (meta == null || meta.isBlank() ? "" : break2 + meta));
        writeField(out, boundary, "username", playerName());
        if (log != null && !log.isEmpty()) {
            writeFile(out, boundary, "logs", "latest-log.txt", "text/plain",
                log.getBytes(StandardCharsets.UTF_8));
        }
        for (byte[] image : images) {
            writeFile(out, boundary, "screenshots", "screenshot.png", "image/png", image);
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
