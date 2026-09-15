package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import net.minecraft.server.MinecraftServer;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.S2CPanelDataPacket;

import java.util.List;
import java.util.Map;

/**
 * Builds {@link S2CPanelDataPacket} on the logical server from live tracker state.
 */
public final class CtlPanelPayloadBuilder {
    public static S2CPanelDataPacket build(MinecraftServer server) {
        S2CPanelDataPacket p = new S2CPanelDataPacket();
        p.phaseOrdinal = PhaseManager.getCurrentPhase().ordinal();
        p.uptimeSeconds = (int) Math.min(Integer.MAX_VALUE, PhaseManager.getServerUptimeSeconds());
        p.baselineThreshold = Config.baselineChunkThreshold;
        p.escalationThreshold = Config.escalationChunkThreshold;
        p.atlVersionLine = ModVersions.allTheLeaksLine();
        p.sparkVersionLine = ModVersions.sparkLine()
            + " | " + ModVersions.mcNeoLine()
            + (Config.autoSparkProfiling
            ? " | CTL auto-profiler: on (see calmtheleaks-common.toml)"
            : " | CTL auto-profiler: off");
        p.worldPersistenceAllowed = Config.allowWorldLeakMemory;
        p.worldPersistenceActive = LeakPersistenceManager.isSessionActive();

        LeakStateTracker tracker = LeakStateTracker.getInstance();
        p.narrowDisabledMods.clear();
        p.narrowDisabledMods.addAll(NarrowDownStore.get().getDisabledMods());
        p.narrowSummaryLines.clear();
        List<String> narrowLines = NarrowDownStore.get().buildCompareLines(tracker);
        for (int i = 0; i < narrowLines.size() && i < 28; i++) {
            p.narrowSummaryLines.add(narrowLines.get(i));
        }

        p.trackedLeakCount = tracker.getAllStates().size();

        for (Map.Entry<LeakSignature, LeakState> entry : tracker.getAllStates().entrySet()) {
            LeakSignature sig = entry.getKey();
            LeakState state = entry.getValue();
            VerdictEngine.Verdict verdict = VerdictEngine.evaluate(sig, state, server);
            LeakAnalyser.LeakAnalysis analysis = LeakAnalyser.analyse(sig, state, server);

            S2CPanelDataPacket.LeakRow row = new S2CPanelDataPacket.LeakRow();
            row.type = sig.type != null ? sig.type : "";
            row.targetClass = sig.targetClass != null ? sig.targetClass : "";
            row.modName = sig.modName != null ? sig.modName : "";
            row.dimension = sig.dimension != null ? sig.dimension : "";
            row.currentCount = state.currentCount;
            row.previousCount = state.previousCount;
            row.reportCount = state.reportCount;
            row.trendOrdinal = state.trend.ordinal();
            row.verdict = verdict.name();
            row.suppressed = state.suppressed;
            row.setTimeSeries(state.copySamples());
            for (ReportFormatter.GuiTextLine gl : ReportFormatter.buildGuiDetailLines(sig, state, verdict, analysis, server)) {
                row.detailLines.add(new S2CPanelDataPacket.ColoredLine(gl.rgb, gl.text));
            }
            p.leaks.add(row);
        }

        return p;
    }

    private CtlPanelPayloadBuilder() {}
}
