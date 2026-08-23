package uk.co.extraspecialstudio.calml_the_leaks_ctl.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscAnchor;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscLayoutSpec;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.client.CtlClientDiagnosticsCache;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.S2CPanelDataPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Narrow-down toolkit: snapshot/compare leak lists while you manually change the modpack (same explanatory style as the main panel).
 */
public class CtlNarrowScreen extends EscScreen {
    private static final int LINE = 12;
    private static final int COLOR_TITLE = 0xFFDDAA;
    private static final int COLOR_BODY = 0xCCCCCC;
    private static final int COLOR_MUTED = 0xAAAAAA;
    private static final int COLOR_SUMMARY = 0xB8E0FF;
    private static final int COLOR_WARN = 0xFFAA55;

    private final Screen parent;
    private final S2CPanelDataPacket data;
    private int scroll;
    private final List<VisualLine> visualLines = new ArrayList<>();
    private EscRect scrollViewport;

    public CtlNarrowScreen(Screen parent) {
        super(Component.literal("CTL narrow-down toolkit"));
        this.parent = parent;
        this.data = CtlClientDiagnosticsCache.getLastPacket();
    }

    @Override
    protected void init() {
        rebuildContent();
        super.init();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        rebuildContent();
        super.resize(mc, w, h);
    }

    @Override
    protected void buildLayout() {
        EscRect content = contentRect();
        EscRect body = EscPanel.bodyBelowTitle(content, style);
        EscRect footer = EscPanel.footer(content, style);
        scrollViewport = new EscRect(body.x(), body.y(), body.width(),
            Math.max(0, footer.y() - body.y() - 4));
        addAnchoredButton(Component.translatable("gui.back"), footer,
            EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, 120, 20),
            b -> this.minecraft.setScreen(parent));
    }

    private int contentWidth() {
        return scrollViewport != null ? scrollViewport.width() : Math.max(40, contentRect().width());
    }

    private void rebuildContent() {
        visualLines.clear();
        int w = Math.max(40, contentWidth());
        if (data == null) {
            appendWrapped(COLOR_WARN,
                "No diagnostics packet loaded. Open the main CTL panel first (F8 or /ctl panel), then open the narrow-down toolkit again.",
                w);
            return;
        }

        appendWrappedTitle("What this toolkit is for", w);
        appendWrapped(COLOR_MUTED,
            "Narrow-down means you remove suspects from the pack, restart, and see whether AllTheLeaks reports improve — "
                + "a step-by-step way to find a culprit mod. Calm The Leaks is not affiliated with BisectHosting.",
            w);

        appendWrappedTitle("CTL never disables mods for you", w);
        appendWrapped(COLOR_WARN,
            "CTL cannot turn mods off mid-game. While the server or world is running, Forge has already loaded your jars; "
                + "nothing in CTL unloads them. Only you can change what loads next time: edit the mods folder or launcher profile, "
                + "then do a full restart. This screen only records snapshots, compares leak lists, and stores a checklist of mod ids.",
            w);

        appendWrappedTitle("What CTL helps you do", w);
        appendWrapped(COLOR_BODY,
            "You still do the real work (remove or disable mods, restart, wait for AllTheLeaks to print again). "
                + "CTL saves a snapshot of tracked leak signatures, then after your restart you run compare to see what grew, shrank, or vanished. "
                + "Work in small steps: snapshot, change mods + restart, compare, repeat.",
            w);

        appendWrappedTitle("Spark, ATL, and CTL together", w);
        appendWrapped(COLOR_BODY,
            "ATL still produces warnings; CTL still explains them on the main panel. Spark is optional: /spark on the server "
                + "for CPU profiling while you test. Full setup is under \"Spark, AllTheLeaks & CTL\" on the main panel.",
            w);

        appendBlank();

        appendWrappedTitle("Disabled mods list (server)", w);
        appendWrapped(COLOR_MUTED,
            "These ids are a checklist in CTL's server config — CTL does not delete jars or toggle mods. "
                + "You disable mods only by changing files on disk (or the launcher) and restarting. "
                + "The list is a reminder of what you meant to test without when you read compare output.",
            w);
        String modLine = String.join(", ",
            data.narrowDisabledMods.isEmpty() ? List.of("(none — list is empty)") : data.narrowDisabledMods);
        appendWrapped(COLOR_BODY, "Recorded mod ids: " + modLine, w);

        appendBlank();

        appendWrappedTitle("Server commands (console or op)", w);
        appendWrapped(COLOR_MUTED,
            "Run on the dedicated server or in single-player with permission to use server commands. Tab-complete suggests mod ids where useful.",
            w);
        appendWrapped(COLOR_BODY,
            "/ctl narrow snapshot — Saves every leak signature CTL is tracking now. Use as your \"before\" baseline once ATL has refreshed so the tracker is not empty.",
            w);
        appendWrapped(COLOR_BODY,
            "/ctl narrow compare — Shows differences between that snapshot and the current tracker. Run after you change mods, restart, and let ATL run again.",
            w);
        appendWrapped(COLOR_BODY,
            "/ctl narrow mod add <modid> — Adds to the checklist. /ctl narrow mod remove <modid> — Removes. /ctl narrow mod list — Prints the list in chat.",
            w);
        appendWrapped(COLOR_BODY,
            "/ctl narrow clear snapshot — Deletes the saved snapshot only.",
            w);
        appendWrapped(COLOR_BODY,
            "/ctl narrow clear mods — Clears the disabled-mod checklist; the snapshot (if any) stays until cleared separately.",
            w);

        appendBlank();

        appendWrappedTitle("Compare summary (from server)", w);
        appendWrapped(COLOR_MUTED,
            "Bundled when the main diagnostics panel was last built. Run /ctl narrow compare, then refresh the main panel (F8 or /ctl panel) and re-open this toolkit to update.",
            w);
        if (data.narrowSummaryLines.isEmpty()) {
            appendWrapped(COLOR_WARN,
                "No summary lines yet. Run snapshot, change the pack, run compare, then refresh the main panel.",
                w);
        } else {
            for (String line : data.narrowSummaryLines) {
                appendWrapped(COLOR_SUMMARY, line, w);
            }
        }
    }

    private void appendWrappedTitle(String title, int maxWidth) {
        for (FormattedCharSequence seq : font.split(Component.literal(title).withStyle(s -> s.withFont(EscFonts.DEFAULT)), maxWidth)) {
            visualLines.add(new VisualLine(COLOR_TITLE, seq));
        }
    }

    private void appendWrapped(int color, String text, int maxWidth) {
        for (FormattedCharSequence seq : font.split(Component.literal(text).withStyle(s -> s.withFont(EscFonts.DEFAULT)), maxWidth)) {
            visualLines.add(new VisualLine(color, seq));
        }
    }

    private void appendBlank() {
        visualLines.add(VisualLine.emptyLine());
    }

    private int totalScrollableHeight() {
        return visualLines.size() * LINE;
    }

    private record VisualLine(int color, FormattedCharSequence seq, boolean lineBreak) {
        static VisualLine emptyLine() {
            return new VisualLine(0, null, true);
        }

        VisualLine(int color, FormattedCharSequence seq) {
            this(color, seq, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scrollViewport == null) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int viewH = scrollViewport.height();
        int max = Math.max(0, totalScrollableHeight() - viewH);
        scroll = (int) Math.max(0, Math.min(max, scroll - delta * LINE * 3));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        EscRect content = contentRect();
        EscText.drawComponent(graphics, font, title, content.x(), content.y() + 4, 0xFFFFFF, false);

        if (scrollViewport != null) {
            graphics.enableScissor(scrollViewport.x(), scrollViewport.y(), scrollViewport.right(), scrollViewport.bottom());
            int y = scrollViewport.y() - scroll;
            for (VisualLine vl : visualLines) {
                if (vl.lineBreak()) {
                    y += LINE;
                    continue;
                }
                if (y + LINE >= scrollViewport.y() && y <= scrollViewport.bottom()) {
                    graphics.drawString(font, vl.seq(), scrollViewport.x(), y, vl.color(), false);
                }
                y += LINE;
            }
            graphics.disableScissor();
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
