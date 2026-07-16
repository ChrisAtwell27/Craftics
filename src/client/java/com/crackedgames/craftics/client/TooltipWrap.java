package com.crackedgames.craftics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Word wrap for tooltip lines whose text isn't authored to a fixed width.
 *
 * <p>Most Craftics tooltips are hand-written and hand-broken with {@code \n}, so they need
 * nothing from this class. The ones that do are the strings assembled at runtime from a
 * registry - an armor set's or hybrid set's description, which compat modules write as full
 * sentences. Those went into a single {@link Text#literal} and ran off the edge of the
 * screen, because a tooltip line, unlike the guide book, does no wrapping of its own.
 *
 * <p>The §-code carry is the reason this is worth sharing rather than re-deriving: a colour
 * opened on one line must be reopened on the next, or a wrapped description loses its colour
 * halfway through. {@code GuideBookScreen} solved this already; this is that solution made
 * static so a tooltip can use it too.
 */
public final class TooltipWrap {

    private TooltipWrap() {}

    /**
     * Tooltip width to wrap at, in pixels. Vanilla lets a tooltip grow to whatever its widest
     * line needs, so this is a house style rather than a hard limit - wide enough not to
     * fragment ordinary lines, narrow enough that a paragraph stays readable beside the item.
     */
    private static final int MAX_WIDTH = 220;

    /**
     * Wrap {@code text} to {@link #MAX_WIDTH} and append each resulting line to {@code out},
     * prefixing every line (including continuations) with {@code indent}.
     *
     * <p>Falls back to appending the text unwrapped if no text renderer is available yet,
     * which keeps a tooltip that renders early from vanishing.
     */
    public static void addWrapped(List<Text> out, String indent, String text) {
        if (text == null || text.isEmpty()) return;
        for (String line : wrap(text, MAX_WIDTH)) {
            out.add(Text.literal(indent + line));
        }
    }

    /** Word-wrap {@code text} to {@code maxWidth} pixels, carrying the active §-code over. */
    public static List<String> wrap(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            result.add(text);
            return result;
        }
        var textRenderer = client.textRenderer;

        StringBuilder line = new StringBuilder();
        String carry = "";
        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (textRenderer.getWidth(test) > maxWidth && !line.isEmpty()) {
                result.add(line.toString());
                carry = lastFormatCode(line.toString(), carry);
                line = new StringBuilder(carry + word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    /**
     * The §-code still in force at the end of {@code line} ("" if none, or if it was reset).
     * Colour codes replace each other; style codes (bold, italic) stack onto the colour.
     */
    private static String lastFormatCode(String line, String inherited) {
        String code = inherited;
        for (int i = 0; i < line.length() - 1; i++) {
            if (line.charAt(i) == '§') {
                char c = Character.toLowerCase(line.charAt(i + 1));
                if (c == 'r') code = "";
                else if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) code = "§" + c;
                else code = code + "§" + c;
            }
        }
        return code;
    }
}
