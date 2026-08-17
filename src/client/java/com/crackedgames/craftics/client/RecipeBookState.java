package com.crackedgames.craftics.client;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;

/**
 * Vanilla's recipe book, as far as Craftics needs to know about it.
 *
 * <p>The book unfolds sideways out of the inventory and shoves the whole screen over with it.
 * Craftics' stat and affinity panels live in that space, so the two cannot both be up - see the
 * visibility guard in {@code InventoryStatsMixin} and the click guard in
 * {@code InventoryClickMixin}, which both read {@link #isOpen}.
 *
 * <p>The widget is found by scanning the screen's children rather than asked for directly,
 * because the direct route moved: on 1.21.1 the screen implements {@code RecipeBookProvider}
 * and hands the widget over via {@code getRecipeBookWidget()}, while from 1.21.3 that method is
 * gone from the interface and the widget is a private field on {@code RecipeBookScreen} - a
 * class that does not exist on 1.21.1 at all, so it cannot even be named in shared code. Every
 * version registers the widget as a screen child (vanilla needs it focusable), and that is the
 * one handle all four shards agree on.
 */
public final class RecipeBookState {

    private RecipeBookState() {}

    /** The screen's recipe book, or null if it hasn't got one. */
    @SuppressWarnings("rawtypes") // generic on 1.21.3+, raw on 1.21.1 - only isOpen/toggleOpen used
    private static RecipeBookWidget find(Screen screen) {
        if (screen == null) return null;
        for (Element child : screen.children()) {
            if (child instanceof RecipeBookWidget widget) return widget;
        }
        return null;
    }

    /** True when {@code screen} owns a recipe book and that book is currently open. */
    public static boolean isOpen(Screen screen) {
        var widget = find(screen);
        return widget != null && widget.isOpen();
    }

    /**
     * Fold the recipe book away if it is open. Called when the owning screen closes, so the
     * book isn't left waiting to reopen with the inventory next time: vanilla remembers the
     * open state, so a book opened once stayed in the way of every inventory after it until
     * it was dismissed by hand.
     */
    public static void closeIfOpen(Screen screen) {
        var widget = find(screen);
        if (widget != null && widget.isOpen()) widget.toggleOpen();
    }
}
