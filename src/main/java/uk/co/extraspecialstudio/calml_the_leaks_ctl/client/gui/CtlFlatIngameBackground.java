package uk.co.extraspecialstudio.calml_the_leaks_ctl.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Vanilla {@code Screen.renderBackground} captures and blurs the world for in-game overlays; on 1.21+ that path
 * can make mod-drawn text look smeared. Screens can override {@code renderBackground} to use {@link #INGAME_DIM} instead.
 * <p>
 * If a screen calls {@code renderBackground}, draws its own strings, then {@code super.render(...)}, vanilla will call
 * {@code renderBackground} <em>again</em> at the start of {@code Screen.render} — painting the blurred/menu layer on top
 * of that text while widgets (drawn after) stay sharp. Call {@link #skipDuplicateBackgroundOnce} immediately before
 * {@code super.render} so the second pass is skipped.
 */
public final class CtlFlatIngameBackground {
    /** ARGB: nearly opaque dark grey (no blur pass). */
    public static final int INGAME_DIM = 0xD8101010;

    private static final Set<Screen> SKIP_NEXT_BACKGROUND =
        Collections.newSetFromMap(new WeakHashMap<>());

    private CtlFlatIngameBackground() {}

    public static boolean isInGame(Minecraft mc) {
        return mc != null && mc.level != null;
    }

    /**
     * The next {@link Screen#renderBackground} for {@code screen} becomes a no-op (same frame). Call right before
     * {@code super.render} when this frame already invoked {@code renderBackground} once above custom text.
     */
    public static void skipDuplicateBackgroundOnce(Screen screen) {
        if (screen != null) {
            SKIP_NEXT_BACKGROUND.add(screen);
        }
    }

    /**
     * If {@link #skipDuplicateBackgroundOnce} was called for this screen, clears the flag and returns {@code true}.
     */
    public static boolean consumeSkipDuplicateBackground(Screen screen) {
        return screen != null && SKIP_NEXT_BACKGROUND.remove(screen);
    }
}
