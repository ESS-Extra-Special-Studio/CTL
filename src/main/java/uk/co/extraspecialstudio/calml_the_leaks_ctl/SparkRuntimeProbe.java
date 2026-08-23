package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort checks against the Spark mod when present. CTL does not bundle Spark on the compile classpath.
 * Heap numbers in CTL always come from the JVM; this class only adds what we can observe via reflection.
 */
public final class SparkRuntimeProbe {
    public final boolean sparkModLoaded;
    public final boolean profilerRunning;
    public final List<String> notes = new ArrayList<>();

    private SparkRuntimeProbe(boolean loaded, boolean running) {
        this.sparkModLoaded = loaded;
        this.profilerRunning = running;
    }

    public static SparkRuntimeProbe probe() {
        boolean loaded = SparkDataProvider.isSparkAvailable();
        if (!loaded) {
            return new SparkRuntimeProbe(false, false);
        }
        boolean running = SparkProfilerManager.isProfilerRunningReflective();
        SparkRuntimeProbe p = new SparkRuntimeProbe(true, running);
        if (running) {
            p.notes.add("Spark profiler reports running (reflection).");
        } else {
            p.notes.add("Spark is installed; profiler is not running right now.");
        }
        String sessionNote = SparkProfilerManager.getLastSessionNote();
        if (sessionNote != null && !sessionNote.isEmpty()) {
            p.notes.add(sessionNote);
        }
        SparkApiReflection.appendLiveTelemetry(p.notes);
        return p;
    }
}
