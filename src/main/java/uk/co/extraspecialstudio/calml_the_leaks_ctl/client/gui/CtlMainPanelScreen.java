package uk.co.extraspecialstudio.calml_the_leaks_ctl.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscAnchor;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscInsets;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscLayoutSpec;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.PhaseManager;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.client.CtlClientDiagnosticsCache;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.C2SWorldMemoryPayload;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.CtlNetwork;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.S2CPanelDataPacket;

import java.util.List;

/**
 * Diagnostics panel with two tabs: full-width guide (scrollable) and full-width leak list.
 */
public class CtlMainPanelScreen extends EscScreen {
    private static final int LEAK_ROW_HEIGHT = 22;
    private static final int GAP_AFTER_SECTION = 3;
    private static final int BUTTON_H = 20;
    private static final int TAB_W = 80;
    private static final int TAB_H = 20;
    private static final int TAB_ROW_BAND = TAB_H + 4;
    private static final int GUIDE_VIEW_TOP_PAD = 4;
    private static final int TEXT_RIGHT_GUTTER = 12;

    private static final int TAB_GUIDE = 0;
    private static final int TAB_LEAKS = 1;

    private static final String INTRO =
        "CTL reads AllTheLeaks output and adds plain-language context so you can spot real leaks versus normal noise. "
            + "Numbers update when you open this panel from ES Hub or /ctl panel.";
    private static final String VERSION_HELP =
        "The cyan lines are mod versions detected on the server (from the server's view, not your client folder).";
    private static final String SPARK_ATL_CTL =
        "How Spark works with AllTheLeaks and CTL: "
            + "AllTheLeaks scans the game and prints leak-style warnings. "
            + "CTL listens to those lines and turns them into this panel and plain-English advice. "
            + "Spark is a separate optional mod: install it on the server (matching your MC version). "
            + "Use /spark on the server to open Spark's menu, start a CPU profile, or open the web viewer — "
            + "that is where deep call stacks and timing live. "
            + "When Spark is present, CTL can read live TPS/MSPT-style stats to enrich diagnosis. "
            + "Heap percentage and the coloured bars in a leak's detail screen always come from the Java VM (MemoryMXBean), "
            + "not from Spark's own heap reports — so you can trust heap in CTL even without opening Spark. "
            + "In config/calmtheleaks-common.toml the option auto_spark_profiling lets CTL try to start Spark's profiler "
            + "automatically when a leak looks severe; set it false if you prefer to run /spark yourself only.";
    private static final String NARROW_HELP =
        "Opens the narrow-down toolkit: snapshot and compare leak lists while you change the modpack yourself (restart required). "
            + "CTL never disables mods mid-game. Commands use /ctl narrow … (see that screen).";
    private static final String LIST_HELP =
        "Scroll with the mouse wheel in the list. Click a row to open a separate window with the full diagnosis.";

    private S2CPanelDataPacket data;
    private int activeTab = TAB_GUIDE;
    private int leakScrollPx;
    private int guideScrollPx;

    private EscRect panelContent;
    private EscRect tabRow;
    private EscRect tabGuideBounds;
    private EscRect tabLeaksBounds;
    private EscRect guideViewport;
    private EscRect leakHeaderArea;
    private EscRect leakViewport;
    private EscRect tipLineArea;

    private int guideTextWrapWidth;
    private int leakHelpWrapWidth;
    private int guideContentHeight;

    public CtlMainPanelScreen() {
        super(Component.literal("Calm The Leaks"));
        this.data = CtlClientDiagnosticsCache.getLastPacket();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        super.init();
    }

    private void switchTab(int tab) {
        if (data == null || tab == activeTab) {
            return;
        }
        activeTab = tab;
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void buildLayout() {
        panelContent = contentRect();
        if (data == null) {
            addAnchoredButton(Component.literal("Close"), panelContent,
                EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, 100, 20),
                b -> this.onClose());
            return;
        }

        addAnchoredButton(Component.literal("Close (ESC)"), panelContent,
            EscLayoutSpec.of(EscAnchor.TOP_RIGHT, 0, 0, 98, BUTTON_H),
            b -> this.onClose());

        EscRect body = EscPanel.bodyBelowTitle(panelContent, style);
        tabRow = new EscRect(body.x(), body.y(), body.width(), TAB_ROW_BAND);
        EscRect mainBody = new EscRect(body.x(), tabRow.bottom() + 4, body.width(),
            Math.max(0, body.bottom() - tabRow.bottom() - 4));

        int guideTabW = Math.min(guideTabW(), tabRow.width() / 2 - 4);
        int leaksTabW = Math.min(leaksTabW(), tabRow.width() / 2 - 4);
        tabGuideBounds = resolve(tabRow,
            EscLayoutSpec.centeredPairLeft(EscAnchor.TOP_CENTER, 0, guideTabW, TAB_H, 6));
        tabLeaksBounds = resolve(tabRow,
            EscLayoutSpec.centeredPairRight(EscAnchor.TOP_CENTER, 0, leaksTabW, TAB_H, 6));

        addButton(Component.literal("Guide & how to use"), tabGuideBounds, b -> switchTab(TAB_GUIDE));
        addButton(Component.literal("Tracked leaks"), tabLeaksBounds, b -> switchTab(TAB_LEAKS));

        if (activeTab == TAB_LEAKS) {
            layoutLeaksTab(mainBody);
        } else {
            layoutGuideTab(mainBody);
        }
        clampScroll();
    }

    private void layoutLeaksTab(EscRect mainBody) {
        Font f = this.font;
        int lh = f.lineHeight;
        leakHelpWrapWidth = Math.max(40, mainBody.width() - TEXT_RIGHT_GUTTER);
        int headerH = lh + wrapHeight(f, LIST_HELP, leakHelpWrapWidth) + 6;
        leakHeaderArea = new EscRect(mainBody.x(), mainBody.y(), mainBody.width(), headerH);
        leakViewport = new EscRect(mainBody.x(), leakHeaderArea.bottom() + 4, mainBody.width(),
            Math.max(0, mainBody.bottom() - leakHeaderArea.bottom() - 4));
        guideViewport = null;
        tipLineArea = null;
        guideTextWrapWidth = 0;
        guideContentHeight = 0;
    }

    private void layoutGuideTab(EscRect mainBody) {
        Font f = this.font;
        int lh = f.lineHeight;
        int minStackH = (data.worldPersistenceAllowed ? BUTTON_H + 4 : 0) + BUTTON_H + 4 + lh;
        int stackH = Math.min(mainBody.height(), Math.max(minStackH, 52));
        EscRect actionStack = new EscRect(mainBody.x(), mainBody.bottom() - stackH, mainBody.width(), stackH);
        guideViewport = new EscRect(mainBody.x(), mainBody.y(), mainBody.width(),
            Math.max(0, actionStack.y() - mainBody.y() - 8)).inset(EscInsets.of(0, GUIDE_VIEW_TOP_PAD, TEXT_RIGHT_GUTTER, 0));

        guideTextWrapWidth = Math.max(40, guideViewport.width());
        guideContentHeight = measureGuideScrollableHeight(f, guideTextWrapWidth);

        leakHeaderArea = null;
        leakViewport = null;
        leakHelpWrapWidth = Math.max(40, mainBody.width() - TEXT_RIGHT_GUTTER);

        int y = actionStack.y();
        if (data.worldPersistenceAllowed) {
            EscRect worldBtn = new EscRect(actionStack.x(), y, actionStack.width(), BUTTON_H);
            if (data.worldPersistenceActive) {
                addButton(Component.literal("Stop saving & delete world memory"), worldBtn,
                    b -> CtlNetwork.sendToServer(new C2SWorldMemoryPayload(C2SWorldMemoryPayload.ACTION_DISABLE_DELETE)));
            } else {
                addButton(Component.literal("Save leak history in this world"), worldBtn,
                    b -> CtlNetwork.sendToServer(new C2SWorldMemoryPayload(C2SWorldMemoryPayload.ACTION_ENABLE)));
            }
            y += BUTTON_H + 4;
        }
        addButton(Component.literal("Open narrow-down toolkit"), new EscRect(actionStack.x(), y, actionStack.width(), BUTTON_H),
            b -> this.minecraft.setScreen(new CtlNarrowScreen(this)));
        y += BUTTON_H + 4;
        tipLineArea = new EscRect(actionStack.x(), y, actionStack.width(), Math.max(lh, actionStack.bottom() - y));
    }

    private static int guideTabW() {
        return TAB_W + 48;
    }

    private static int leaksTabW() {
        return TAB_W + 32;
    }

    private int measureGuideScrollableHeight(Font f, int maxW) {
        int lh = f.lineHeight;
        int y = GUIDE_VIEW_TOP_PAD;
        y += lh + wrapHeight(f, INTRO, maxW);
        y += lh + GAP_AFTER_SECTION;
        y += lh;
        String v1 = data.atlVersionLine != null ? data.atlVersionLine : "";
        String v2 = data.sparkVersionLine != null ? data.sparkVersionLine : "";
        y += wrapHeight(f, (v1 + "  |  " + v2).trim(), maxW);
        y += wrapHeight(f, VERSION_HELP, maxW);
        y += lh + GAP_AFTER_SECTION;
        y += lh;
        y += wrapHeight(f, SPARK_ATL_CTL, maxW);
        y += GAP_AFTER_SECTION;
        PhaseManager.Phase[] phases = PhaseManager.Phase.values();
        PhaseManager.Phase phase = data.phaseOrdinal >= 0 && data.phaseOrdinal < phases.length
            ? phases[data.phaseOrdinal] : PhaseManager.Phase.STARTUP;
        y += wrapHeight(f, "Phase: " + phase + " — how settled the server is since it started (startup is expected to be noisy).", maxW);
        y += wrapHeight(f, "Uptime: " + data.uptimeSeconds + " s — longer uptime makes trends more meaningful.", maxW);
        y += wrapHeight(f,
            "Chunk hints: below " + data.baselineThreshold + " is often normal retention; above " + data.escalationThreshold
                + " is treated as a stronger signal (configurable on the server).",
            maxW);
        y += wrapHeight(f,
            "Tracked right now: " + data.trackedLeakCount + " — open the Tracked leaks tab to see each one as a row.",
            maxW);
        y += lh + GAP_AFTER_SECTION;
        if (data.worldPersistenceAllowed) {
            y += lh;
            if (data.worldPersistenceActive) {
                y += wrapHeight(f,
                    "World memory is ON. CTL stores leak history inside this world's save folder so it survives restarts. "
                        + "Folder name: calmtheleaks_world_memory",
                    maxW);
            } else {
                y += wrapHeight(f,
                    "World memory is OFF. Turn it on only if you want CTL to remember leaks across game restarts for this world (stored in the save).",
                    maxW);
            }
            y += lh + GAP_AFTER_SECTION;
        }
        y += lh;
        y += wrapHeight(f, NARROW_HELP, maxW);
        y += lh;
        return y;
    }

    private static int wrapHeight(Font font, String text, int maxWidth) {
        return EscText.wrapHeight(font, text, maxWidth);
    }

    private void clampScroll() {
        if (data == null) {
            return;
        }
        if (activeTab == TAB_LEAKS) {
            int viewH = leakViewport.height();
            int contentH = data.leaks.size() * LEAK_ROW_HEIGHT;
            int maxScroll = Math.max(0, contentH - viewH);
            leakScrollPx = Math.max(0, Math.min(maxScroll, leakScrollPx));
        } else if (guideViewport != null && guideViewport.height() > 0) {
            int viewH = guideViewport.height();
            int maxScroll = Math.max(0, guideContentHeight - viewH);
            guideScrollPx = Math.max(0, Math.min(maxScroll, guideScrollPx));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (data == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (activeTab == TAB_LEAKS && leakViewport != null && leakViewport.contains(mouseX, mouseY)) {
            leakScrollPx -= (int) (scrollY * LEAK_ROW_HEIGHT * 2);
            clampScroll();
            return true;
        }
        if (activeTab == TAB_GUIDE && guideViewport != null && guideViewport.contains(mouseX, mouseY)) {
            guideScrollPx -= (int) (scrollY * this.font.lineHeight * 3);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (data == null || button != 0 || activeTab != TAB_LEAKS || leakViewport == null) {
            return false;
        }
        if (leakViewport.contains(mouseX, mouseY)) {
            int relY = (int) (mouseY - leakViewport.y() + leakScrollPx);
            int idx = relY / LEAK_ROW_HEIGHT;
            List<S2CPanelDataPacket.LeakRow> leaks = data.leaks;
            if (idx >= 0 && idx < leaks.size()) {
                this.minecraft.setScreen(new CtlLeakDetailScreen(this, leaks.get(idx)));
                return true;
            }
        }
        return false;
    }

    private static String truncate(String s, int maxChars) {
        return EscText.truncate(s, maxChars);
    }

    private void drawWrapped(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
        EscText.drawWrapped(g, font, text, x, y, maxWidth, CtlGuiColors.text(color));
    }

    private void drawEscString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        EscText.drawString(graphics, font, text, x, y, CtlGuiColors.text(color), false, EscFonts.DEFAULT);
    }

    private void renderGuideScrollable(GuiGraphics graphics) {
        Font f = this.font;
        int lh = f.lineHeight;
        int maxW = guideTextWrapWidth;
        int x = guideViewport.x();
        int y = guideViewport.y() - guideScrollPx;

        drawEscString(graphics, f, "Understanding this panel", x, y, 0xFFDDAA);
        y += lh;
        drawWrapped(graphics, f, INTRO, x, y, maxW, 0xCCCCCC);
        y += wrapHeight(f, INTRO, maxW);
        y += lh + GAP_AFTER_SECTION;

        drawEscString(graphics, f, "Server snapshot", x, y, 0xFFDDAA);
        y += lh;
        String v1 = data.atlVersionLine != null ? data.atlVersionLine : "";
        String v2 = data.sparkVersionLine != null ? data.sparkVersionLine : "";
        drawWrapped(graphics, f, (v1 + "  |  " + v2).trim(), x, y, maxW, 0x88CCFF);
        y += wrapHeight(f, (v1 + "  |  " + v2).trim(), maxW);
        drawWrapped(graphics, f, VERSION_HELP, x, y, maxW, 0xAAAAAA);
        y += wrapHeight(f, VERSION_HELP, maxW);
        y += lh + GAP_AFTER_SECTION;

        drawEscString(graphics, f, "Spark, AllTheLeaks & CTL", x, y, 0xFFDDAA);
        y += lh;
        drawWrapped(graphics, f, SPARK_ATL_CTL, x, y, maxW, 0xCCCCCC);
        y += wrapHeight(f, SPARK_ATL_CTL, maxW);
        y += GAP_AFTER_SECTION;

        PhaseManager.Phase[] phases = PhaseManager.Phase.values();
        PhaseManager.Phase phase = data.phaseOrdinal >= 0 && data.phaseOrdinal < phases.length
            ? phases[data.phaseOrdinal] : PhaseManager.Phase.STARTUP;
        drawWrapped(graphics, f, "Phase: " + phase + " — how settled the server is since it started (startup is expected to be noisy).", x, y, maxW, 0xCCCCCC);
        y += wrapHeight(f, "Phase: " + phase + " — how settled the server is since it started (startup is expected to be noisy).", maxW);
        drawWrapped(graphics, f, "Uptime: " + data.uptimeSeconds + " s — longer uptime makes trends more meaningful.", x, y, maxW, 0xCCCCCC);
        y += wrapHeight(f, "Uptime: " + data.uptimeSeconds + " s — longer uptime makes trends more meaningful.", maxW);
        drawWrapped(graphics, f,
            "Chunk hints: below " + data.baselineThreshold + " is often normal retention; above " + data.escalationThreshold
                + " is treated as a stronger signal (configurable on the server).",
            x, y, maxW, 0xCCCCCC);
        y += wrapHeight(f,
            "Chunk hints: below " + data.baselineThreshold + " is often normal retention; above " + data.escalationThreshold
                + " is treated as a stronger signal (configurable on the server).",
            maxW);
        drawWrapped(graphics, f,
            "Tracked right now: " + data.trackedLeakCount + " — open the Tracked leaks tab to see each one as a row.",
            x, y, maxW, 0xFFB84D);
        y += wrapHeight(f,
            "Tracked right now: " + data.trackedLeakCount + " — open the Tracked leaks tab to see each one as a row.",
            maxW);
        y += lh + GAP_AFTER_SECTION;

        if (data.worldPersistenceAllowed) {
            drawEscString(graphics, f, "World memory (optional)", x, y, 0xFFDDAA);
            y += lh;
            if (data.worldPersistenceActive) {
                drawWrapped(graphics, f,
                    "World memory is ON. CTL stores leak history inside this world's save folder so it survives restarts. "
                        + "Folder name: calmtheleaks_world_memory",
                    x, y, maxW, 0xAADDFF);
            } else {
                drawWrapped(graphics, f,
                    "World memory is OFF. Turn it on only if you want CTL to remember leaks across game restarts for this world (stored in the save).",
                    x, y, maxW, 0xAADDFF);
            }
            y += lh + GAP_AFTER_SECTION;
        }

        drawEscString(graphics, f, "Narrow-down toolkit", x, y, 0xFFDDAA);
        y += lh;
        drawWrapped(graphics, f, NARROW_HELP, x, y, maxW, 0xCCCCCC);
    }

    private void renderTabChrome(GuiGraphics graphics) {
        if (tabRow == null || tabGuideBounds == null || tabLeaksBounds == null) {
            return;
        }
        graphics.fill(tabRow.x(), tabRow.y() - 2, tabRow.right(), tabRow.bottom() + 2, 0x33000000);
        EscRect active = activeTab == TAB_GUIDE ? tabGuideBounds : tabLeaksBounds;
        graphics.fill(active.x() - 2, active.y() - 1, active.right() + 2, active.bottom() + 1, 0x55FFCC66);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (CtlFlatIngameBackground.consumeSkipDuplicateBackground(this)) {
            return;
        }
        if (CtlFlatIngameBackground.isInGame(this.minecraft)) {
            graphics.fill(0, 0, this.width, this.height, CtlFlatIngameBackground.INGAME_DIM);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        EscRect content = contentRect();
        EscText.drawComponent(graphics, this.font, this.title, content.x(), 8, CtlGuiColors.text(0xFFFFFF), false);

        if (data == null) {
            drawEscString(graphics, this.font, "No diagnostics loaded. Run /ctl panel on the server.", content.x(), 32, 0xFFAA55);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderTabChrome(graphics);

        if (activeTab == TAB_GUIDE && guideViewport != null && guideViewport.height() > 0) {
            int viewH = guideViewport.height();
            graphics.fill(guideViewport.x() - 2, guideViewport.y() - 2, guideViewport.right() + 2, guideViewport.bottom() + 2, 0x22000000);
            graphics.enableScissor(guideViewport.x(), guideViewport.y(), guideViewport.right(), guideViewport.bottom());
            renderGuideScrollable(graphics);
            graphics.disableScissor();

            int maxG = Math.max(0, guideContentHeight - viewH);
            if (maxG > 0 && guideContentHeight > 0) {
                int barX = guideViewport.right() - 5;
                int thumbH = Math.max(18, (int) (viewH * (viewH / (float) guideContentHeight)));
                int thumbY = guideViewport.y() + Math.max(0, (viewH - thumbH) * guideScrollPx / maxG);
                graphics.fill(barX, guideViewport.y(), barX + 4, guideViewport.bottom(), 0x66000000);
                graphics.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xCC888888);
            }

            if (tipLineArea != null && tipLineArea.height() > 0) {
                drawEscString(graphics, this.font, "Tip: refresh from ES Hub or /ctl panel.",
                    tipLineArea.x(), tipLineArea.y(), 0x888888);
            }
        } else if (activeTab == TAB_LEAKS && leakViewport != null && leakHeaderArea != null) {
            graphics.fill(leakHeaderArea.x() - 2, leakHeaderArea.y() - 2, leakViewport.right() + 2, leakViewport.bottom() + 2, 0x22000000);
            drawEscString(graphics, this.font, "All tracked leaks", leakHeaderArea.x(), leakHeaderArea.y(), 0xFFDDAA);
            drawWrapped(graphics, this.font, LIST_HELP, leakHeaderArea.x(), leakHeaderArea.y() + this.font.lineHeight, leakHelpWrapWidth, 0xAAAAAA);

            List<S2CPanelDataPacket.LeakRow> leaks = data.leaks;
            int viewH = leakViewport.height();
            int contentH = leaks.size() * LEAK_ROW_HEIGHT;
            int maxScroll = Math.max(0, contentH - viewH);

            graphics.enableScissor(leakViewport.x(), leakViewport.y(), leakViewport.right(), leakViewport.bottom());
            int rowY = leakViewport.y() - leakScrollPx;
            for (int i = 0; i < leaks.size(); i++) {
                S2CPanelDataPacket.LeakRow row = leaks.get(i);
                if (rowY + LEAK_ROW_HEIGHT >= leakViewport.y() && rowY <= leakViewport.bottom()) {
                    boolean hover = mouseX >= leakViewport.x() && mouseX < leakViewport.right()
                        && mouseY >= Math.max(leakViewport.y(), rowY) && mouseY < Math.min(leakViewport.bottom(), rowY + LEAK_ROW_HEIGHT);
                    int bg = hover ? 0x55FFFFFF : 0x33000000;
                    graphics.fill(leakViewport.x(), rowY, leakViewport.right(), rowY + LEAK_ROW_HEIGHT, bg);
                    int approxChars = Math.max(12, (leakViewport.width() - TEXT_RIGHT_GUTTER - 10) / 6);
                    String label = truncate(row.summaryTitle() + " — " + row.summarySubtitle(), approxChars);
                    drawEscString(graphics, this.font, label, leakViewport.x() + 6, rowY + 6, hover ? 0xFFFFA0 : 0xFFFFFF);
                }
                rowY += LEAK_ROW_HEIGHT;
            }
            graphics.disableScissor();

            if (maxScroll > 0 && contentH > 0) {
                int trackH = viewH;
                int thumbH = Math.max(18, (int) (trackH * (viewH / (float) contentH)));
                int thumbY = leakViewport.y() + Math.max(0, (trackH - thumbH) * leakScrollPx / maxScroll);
                int barX = leakViewport.right() - 5;
                graphics.fill(barX, leakViewport.y(), barX + 4, leakViewport.bottom(), 0x66000000);
                graphics.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xCC888888);
            } else if (leaks.isEmpty()) {
                drawEscString(graphics, this.font, "No leaks yet — when AllTheLeaks reports one, it will show up here.",
                    leakViewport.x() + 4, leakViewport.y() + 8, 0x888888);
            }
        }

        CtlFlatIngameBackground.skipDuplicateBackgroundOnce(this);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
