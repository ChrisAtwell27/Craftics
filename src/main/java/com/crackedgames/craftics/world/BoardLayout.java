package com.crackedgames.craftics.world;

import com.crackedgames.craftics.mixin.DisplayEntityInvoker;
import com.crackedgames.craftics.mixin.TextDisplayInvoker;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared geometry for the floating text boards - the infinite scoreboard, the career board
 * and the season board. All three are vanilla text displays, and all three had the same two
 * problems until this was factored out.
 *
 * <p>A text display draws its text <b>upward from the entity position</b>: the renderer
 * scales by 0.025 and then translates by {@code -lines * 10}, which works out to
 * {@value #LINE_HEIGHT} blocks per line rising from the origin. The entity position is
 * therefore the BOTTOM of the board, and a CENTER billboard spins about the entity position -
 * so a tall board swung around its bottom edge and appeared to swing sideways as a player
 * looked up at it, rather than turning in place.
 *
 * <p>{@link #applyText} fixes that by translating the text down by half its own height, which
 * puts the board's middle on the entity position and makes the pivot the middle of the board.
 * The translation lives in the display's transformation, which is applied INSIDE the billboard
 * rotation, so it moves with the board instead of fighting it.
 *
 * <p>Because the pivot is now the middle, {@code spawn} has to raise the entity by half the
 * board's height for the board to sit where it is asked to. The spawn methods take the
 * position of the board's bottom edge and do that themselves.
 */
public final class BoardLayout {

    private BoardLayout() {}

    /**
     * Blocks one line of a text display occupies: the renderer's 10px line box (9px glyph plus
     * 1px leading) at its fixed 0.025 scale.
     */
    public static final double LINE_HEIGHT = 0.25;

    /**
     * Half the rendered height of {@code text}, in blocks - the offset between a board's
     * bottom edge and its middle.
     *
     * <p>Counts hard line breaks only. A row long enough to wrap at the display's line width
     * renders taller than this says, which would let a board with a very long player name sit
     * slightly low; every row here is a rank, a name and a number, so it does not come up in
     * practice and a wrap-aware count would need the client's font.
     */
    public static double halfHeight(Text text) {
        return lineCount(text) * LINE_HEIGHT / 2.0;
    }

    /** Write {@code text} onto {@code board} and re-centre it on its own pivot. */
    public static void applyText(DisplayEntity.TextDisplayEntity board, Text text) {
        ((TextDisplayInvoker) board).craftics$setText(text);
        // Re-applied on every refresh, not just at spawn: the board grows a line each time a
        // player qualifies for it, and a stale offset would leave it hanging off its pivot
        // again. Identity rotation and scale are passed explicitly rather than as nulls, since
        // this is the only transformation the boards ever set.
        ((DisplayEntityInvoker) board).craftics$setTransformation(new AffineTransformation(
            new Vector3f(0f, (float) -halfHeight(text), 0f),
            new Quaternionf(),
            new Vector3f(1f, 1f, 1f),
            new Quaternionf()));
    }

    private static int lineCount(Text text) {
        String s = text.getString();
        int lines = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') lines++;
        }
        return lines;
    }
}
