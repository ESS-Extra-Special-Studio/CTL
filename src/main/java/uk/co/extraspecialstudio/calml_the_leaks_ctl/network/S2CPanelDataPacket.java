package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.LeakSample;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Full diagnostics snapshot computed on the server for display in the client GUI.
 */
public final class S2CPanelDataPacket {
    public int phaseOrdinal;
    public int uptimeSeconds;
    public int baselineThreshold;
    public int escalationThreshold;
    public int trackedLeakCount;
    public String atlVersionLine = "";
    public String sparkVersionLine = "";
    public boolean worldPersistenceAllowed;
    public boolean worldPersistenceActive;
    public final List<String> narrowDisabledMods = new ArrayList<>();
    public final List<String> narrowSummaryLines = new ArrayList<>();
    public final List<LeakRow> leaks = new ArrayList<>();

    public static void encode(S2CPanelDataPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.phaseOrdinal);
        buf.writeVarInt(msg.uptimeSeconds);
        buf.writeVarInt(msg.baselineThreshold);
        buf.writeVarInt(msg.escalationThreshold);
        buf.writeUtf(msg.atlVersionLine != null ? msg.atlVersionLine : "");
        buf.writeUtf(msg.sparkVersionLine != null ? msg.sparkVersionLine : "");
        buf.writeBoolean(msg.worldPersistenceAllowed);
        buf.writeBoolean(msg.worldPersistenceActive);
        buf.writeVarInt(msg.narrowDisabledMods.size());
        for (String m : msg.narrowDisabledMods) {
            buf.writeUtf(m);
        }
        buf.writeVarInt(msg.narrowSummaryLines.size());
        for (String line : msg.narrowSummaryLines) {
            buf.writeUtf(line.length() > 512 ? line.substring(0, 509) + "..." : line);
        }
        buf.writeVarInt(msg.trackedLeakCount);
        buf.writeVarInt(msg.leaks.size());
        for (LeakRow row : msg.leaks) {
            row.write(buf);
        }
    }

    public static S2CPanelDataPacket decode(FriendlyByteBuf buf) {
        S2CPanelDataPacket p = new S2CPanelDataPacket();
        p.phaseOrdinal = buf.readVarInt();
        p.uptimeSeconds = buf.readVarInt();
        p.baselineThreshold = buf.readVarInt();
        p.escalationThreshold = buf.readVarInt();
        p.atlVersionLine = buf.readUtf();
        p.sparkVersionLine = buf.readUtf();
        p.worldPersistenceAllowed = buf.readBoolean();
        p.worldPersistenceActive = buf.readBoolean();
        int bm = buf.readVarInt();
        for (int i = 0; i < bm; i++) {
            p.narrowDisabledMods.add(buf.readUtf());
        }
        int bl = buf.readVarInt();
        for (int i = 0; i < bl; i++) {
            p.narrowSummaryLines.add(buf.readUtf());
        }
        p.trackedLeakCount = buf.readVarInt();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            p.leaks.add(LeakRow.read(buf));
        }
        return p;
    }

    public static void handle(S2CPanelDataPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> invokeClientHandler(msg)));
        ctx.setPacketHandled(true);
    }

    /**
     * Reflective indirection keeps {@link S2CPanelDataPacket} free of client class references so dedicated servers
     * can load encode/decode without pulling in {@code Minecraft} or GUI types.
     */
    private static void invokeClientHandler(S2CPanelDataPacket msg) {
        try {
            Class<?> c = Class.forName("uk.co.extraspecialstudio.calml_the_leaks_ctl.client.S2CPanelClientHandler");
            c.getMethod("handle", S2CPanelDataPacket.class).invoke(null, msg);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[CTL] Failed to open diagnostics panel on client", e);
        }
    }

    public static final class LeakRow {
        public String type;
        public String targetClass;
        public String modName;
        public String dimension;
        public int currentCount;
        public int previousCount;
        public int reportCount;
        public int trendOrdinal;
        public String verdict;
        public boolean suppressed;
        public final List<ColoredLine> detailLines = new ArrayList<>();
        public int[] tsCounts = new int[0];
        public byte[] tsHeaps = new byte[0];

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(type);
            buf.writeUtf(targetClass);
            buf.writeUtf(modName);
            buf.writeUtf(dimension);
            buf.writeVarInt(currentCount);
            buf.writeVarInt(previousCount);
            buf.writeVarInt(reportCount);
            buf.writeVarInt(trendOrdinal);
            buf.writeUtf(verdict);
            buf.writeBoolean(suppressed);
            buf.writeVarInt(detailLines.size());
            for (ColoredLine line : detailLines) {
                buf.writeInt(line.rgb);
                buf.writeUtf(line.text);
            }
            int n = Math.min(64, tsCounts != null ? tsCounts.length : 0);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeVarInt(tsCounts[i]);
                int h = tsHeaps != null && i < tsHeaps.length ? tsHeaps[i] & 0xFF : 0;
                buf.writeByte(Math.min(100, h));
            }
        }

        static LeakRow read(FriendlyByteBuf buf) {
            LeakRow r = new LeakRow();
            r.type = buf.readUtf();
            r.targetClass = buf.readUtf();
            r.modName = buf.readUtf();
            r.dimension = buf.readUtf();
            r.currentCount = buf.readVarInt();
            r.previousCount = buf.readVarInt();
            r.reportCount = buf.readVarInt();
            r.trendOrdinal = buf.readVarInt();
            r.verdict = buf.readUtf();
            r.suppressed = buf.readBoolean();
            int lines = buf.readVarInt();
            for (int i = 0; i < lines; i++) {
                r.detailLines.add(new ColoredLine(buf.readInt(), buf.readUtf()));
            }
            int n = buf.readVarInt();
            r.tsCounts = new int[n];
            r.tsHeaps = new byte[n];
            for (int i = 0; i < n; i++) {
                r.tsCounts[i] = buf.readVarInt();
                r.tsHeaps[i] = buf.readByte();
            }
            return r;
        }

        public void setTimeSeries(List<LeakSample> samples) {
            if (samples == null || samples.isEmpty()) {
                tsCounts = new int[0];
                tsHeaps = new byte[0];
                return;
            }
            int n = Math.min(64, samples.size());
            tsCounts = new int[n];
            tsHeaps = new byte[n];
            for (int i = 0; i < n; i++) {
                LeakSample s = samples.get(i);
                tsCounts[i] = s.count;
                tsHeaps[i] = (byte) Math.min(100, Math.max(0, Math.round(s.heapPercent)));
            }
        }

        public String summaryTitle() {
            return "[" + type + "] " + targetClass;
        }

        public String summarySubtitle() {
            String dim = (dimension == null || dimension.isEmpty() || "unknown".equalsIgnoreCase(dimension))
                ? ""
                : dimension + " | ";
            return dim + modName + " | count " + currentCount + " | " + verdict;
        }
    }

    public static final class ColoredLine {
        public final int rgb;
        public final String text;

        public ColoredLine(int rgb, String text) {
            this.rgb = rgb;
            this.text = text;
        }
    }
}
