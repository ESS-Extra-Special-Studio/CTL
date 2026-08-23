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
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.S2CPanelDataPacket;

import java.util.Arrays;
import java.util.List;

/**
 * Detail view for one leak; lines from server + optional count/heap bar sparkline.
 */
public class CtlLeakDetailScreen extends EscScreen {
    private final Screen parent;
    private final S2CPanelDataPacket.LeakRow row;
    private int scroll;
    private static final int LINE_HEIGHT = 12;
    private static final int CHART_HEIGHT = 40;
    private static final String SPARK_CHART_NOTE =
        "Bars = AllTheLeaks-style counts over time; colour = JVM heap %% (Java), not Spark's heap screen. "
            + "For CPU profiles and Spark's own tools, run /spark on the server. "
            + "Main CTL panel explains ATL + Spark + CTL together.";

    private EscRect noteArea;
    private EscRect chartArea;
    private EscRect textViewport;
    private int textWrapWidth;

    public CtlLeakDetailScreen(Screen parent, S2CPanelDataPacket.LeakRow row) {
        super(Component.literal(row.summaryTitle()));
        this.parent = parent;
        this.row = row;
    }

    @Override
    protected void buildLayout() {
        EscRect content = contentRect();
        EscRect body = EscPanel.bodyBelowTitle(content, style);
        EscRect footer = EscPanel.footer(content, style);

        int[] counts = row.tsCounts;
        boolean hasChart = counts != null && counts.length >= 2;
        int noteLines = font.split(Component.literal(SPARK_CHART_NOTE).withStyle(s -> s.withFont(EscFonts.DEFAULT)),
            Math.max(20, body.width() - 8)).size();
        int noteBandH = LINE_HEIGHT + noteLines * LINE_HEIGHT + 4;
        int chartBandH = hasChart ? CHART_HEIGHT + 14 : 0;

        noteArea = new EscRect(body.x(), body.y(), body.width(), noteBandH);
        int belowNote = noteArea.bottom() + 4;
        if (hasChart) {
            chartArea = new EscRect(body.x(), belowNote, body.width(), chartBandH);
            textViewport = new EscRect(body.x(), chartArea.bottom() + 4, body.width(),
                Math.max(0, body.bottom() - chartArea.bottom() - 4));
        } else {
            chartArea = null;
            textViewport = new EscRect(body.x(), belowNote, body.width(), Math.max(0, body.bottom() - belowNote));
        }
        textWrapWidth = Math.max(20, textViewport.width() - 8);

        addAnchoredButton(Component.translatable("gui.back"), footer,
            EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, 120, 20),
            b -> this.minecraft.setScreen(parent));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (textViewport == null) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int maxScroll = Math.max(0, totalContentHeight() - textViewport.height());
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - delta * LINE_HEIGHT * 3));
        return true;
    }

    private int totalContentHeight() {
        List<S2CPanelDataPacket.ColoredLine> lines = row.detailLines;
        if (lines.isEmpty()) {
            return LINE_HEIGHT;
        }
        int h = 0;
        int wrap = textWrapWidth > 0 ? textWrapWidth : Math.max(20, contentRect().width() - 8);
        for (S2CPanelDataPacket.ColoredLine line : lines) {
            for (String part : line.text.split("\n", -1)) {
                List<FormattedCharSequence> wrapped = font.split(Component.literal(part).withStyle(s -> s.withFont(EscFonts.DEFAULT)), wrap);
                h += Math.max(1, wrapped.size()) * LINE_HEIGHT;
            }
        }
        return h + 8;
    }

    private void renderTimeSeriesChart(GuiGraphics graphics) {
        int[] counts = row.tsCounts;
        if (counts == null || counts.length < 2 || chartArea == null) {
            return;
        }
        int left = chartArea.x() + 4;
        int top = chartArea.y();
        int w = chartArea.width() - 8;
        int h = CHART_HEIGHT;
        EscText.drawString(graphics, font, "Count (recent samples)", left, top - 10, 0xAAAAAA, false, EscFonts.DEFAULT);
        int max = Arrays.stream(counts).max().orElse(1);
        int min = Arrays.stream(counts).min().orElse(0);
        int span = Math.max(1, max - min);
        int barW = Math.max(2, w / counts.length);
        for (int i = 0; i < counts.length; i++) {
            int bh = 2 + (h - 4) * (counts[i] - min) / span;
            int x = left + i * barW;
            int heap = row.tsHeaps != null && i < row.tsHeaps.length ? row.tsHeaps[i] & 0xFF : 50;
            int r = Math.min(255, heap * 255 / 100);
            int g = Math.min(255, (100 - heap) * 255 / 100);
            int col = 0xFF000000 | (r << 16) | (g << 8) | 0x44;
            graphics.fill(x, top + h - bh, Math.min(x + barW - 1, left + w), top + h, col);
        }
        EscText.drawString(graphics, font, "Bar color: heap % (red=high)", left, top + h + 2, 0x888888, false, EscFonts.DEFAULT);
    }

    private void renderSparkChartNote(GuiGraphics graphics) {
        if (noteArea == null) {
            return;
        }
        int x = noteArea.x() + 4;
        int w = noteArea.width() - 8;
        int y = noteArea.y();
        EscText.drawString(graphics, font, "Spark vs this chart", x, y, 0xFFDDAA, false, EscFonts.DEFAULT);
        y += LINE_HEIGHT;
        for (FormattedCharSequence line : font.split(Component.literal(SPARK_CHART_NOTE).withStyle(s -> s.withFont(EscFonts.DEFAULT)), w)) {
            graphics.drawString(font, line, x, y, 0xAAAAAA, false);
            y += LINE_HEIGHT;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        EscRect content = contentRect();
        EscText.drawComponent(graphics, font, title, content.x(), content.y() + 4, 0xFFFFFF, false);

        renderSparkChartNote(graphics);
        renderTimeSeriesChart(graphics);

        if (textViewport == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int y = textViewport.y() - scroll;
        int x = textViewport.x() + 4;
        int wrap = textWrapWidth;
        List<S2CPanelDataPacket.ColoredLine> lines = row.detailLines;

        graphics.enableScissor(textViewport.x(), textViewport.y(), textViewport.right(), textViewport.bottom());
        if (lines.isEmpty()) {
            EscText.drawString(graphics, font, "No detail lines.", x, y, 0xAAAAAA, false, EscFonts.DEFAULT);
        } else {
            for (S2CPanelDataPacket.ColoredLine line : lines) {
                int color = 0xFF000000 | (line.rgb & 0xFFFFFF);
                for (String part : line.text.split("\n", -1)) {
                    if (part.isEmpty()) {
                        y += LINE_HEIGHT;
                        continue;
                    }
                    for (FormattedCharSequence seq : font.split(Component.literal(part).withStyle(s -> s.withFont(EscFonts.DEFAULT)), wrap)) {
                        if (y + LINE_HEIGHT >= textViewport.y() && y <= textViewport.bottom()) {
                            graphics.drawString(font, seq, x, y, color, false);
                        }
                        y += LINE_HEIGHT;
                    }
                }
            }
        }
        graphics.disableScissor();

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
