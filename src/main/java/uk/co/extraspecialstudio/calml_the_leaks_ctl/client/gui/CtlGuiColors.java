package uk.co.extraspecialstudio.calml_the_leaks_ctl.client.gui;

/**
 * Minecraft 1.21+ treats {@link net.minecraft.client.gui.GuiGraphics} text colors as ARGB.
 * Literals like {@code 0xCCCCCC} are stored as {@code 0x00CCCCCC} (alpha 0) and draw effectively black.
 */
public final class CtlGuiColors {
    private CtlGuiColors() {}

    public static int text(int color) {
        return (color & 0xFF000000) != 0 ? color : (0xFF000000 | (color & 0xFFFFFF));
    }
}
