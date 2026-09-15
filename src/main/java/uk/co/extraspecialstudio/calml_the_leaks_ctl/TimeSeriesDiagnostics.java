package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import java.util.ArrayList;
import java.util.List;

/**
 * Sparkline strings and acceleration summary from {@link LeakState} samples.
 */
public final class TimeSeriesDiagnostics {
    private static final char[] SPARK = {'\u2581', '\u2582', '\u2583', '\u2584', '\u2585', '\u2586', '\u2587', '\u2588'};

    private TimeSeriesDiagnostics() {}

    public static String countSparkline(List<LeakSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return "";
        }
        int min = samples.stream().mapToInt(s -> s.count).min().orElse(0);
        int max = samples.stream().mapToInt(s -> s.count).max().orElse(0);
        int span = Math.max(1, max - min);
        StringBuilder sb = new StringBuilder();
        sb.append("Count sparkline (oldest->newest): ");
        for (LeakSample s : samples) {
            int idx = (int) ((7L * (s.count - min)) / span);
            sb.append(SPARK[Math.min(7, Math.max(0, idx))]);
        }
        return sb.toString();
    }

    public static String heapSparkline(List<LeakSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Heap % sparkline: ");
        for (LeakSample s : samples) {
            int h = Math.min(100, Math.max(0, Math.round(s.heapPercent)));
            int idx = (h * 7) / 100;
            sb.append(SPARK[Math.min(7, Math.max(0, idx))]);
        }
        return sb.toString();
    }

    /**
     * Rough acceleration label using first vs last third of samples by time.
     */
    public static String accelerationSummary(List<LeakSample> samples) {
        if (samples == null || samples.size() < 3) {
            return samples == null || samples.isEmpty()
                ? "Time-series: waiting for samples."
                : "Time-series: need a few more ATL refreshes for acceleration.";
        }
        List<LeakSample> sorted = new ArrayList<>(samples);
        sorted.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        int n = sorted.size();
        int third = Math.max(1, n / 3);
        double earlyAvg = avgCount(sorted, 0, third);
        double lateAvg = avgCount(sorted, n - third, n);
        double delta = lateAvg - earlyAvg;
        long t0 = sorted.get(0).timeMs;
        long t1 = sorted.get(n - 1).timeMs;
        double minutes = Math.max(1.0 / 60.0, (t1 - t0) / 60000.0);
        double perMin = delta / minutes;

        String trend;
        if (perMin > 0.5) {
            trend = "accelerating (count rising over the window)";
        } else if (perMin < -0.5) {
            trend = "decelerating / draining (count falling)";
        } else {
            trend = "roughly flat velocity (no strong drift)";
        }
        return String.format("Acceleration: %s (~%.2f count/min over %.1f min, n=%d).",
            trend, perMin, (t1 - t0) / 60000.0, n);
    }

    private static double avgCount(List<LeakSample> sorted, int from, int to) {
        int sum = 0;
        int c = 0;
        for (int i = from; i < to && i < sorted.size(); i++) {
            sum += sorted.get(i).count;
            c++;
        }
        return c == 0 ? 0 : (double) sum / c;
    }
}
