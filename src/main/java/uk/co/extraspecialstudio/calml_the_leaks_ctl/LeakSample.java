package uk.co.extraspecialstudio.calml_the_leaks_ctl;

/**
 * One point in the leak time-series (count + JVM heap at sample time).
 */
public final class LeakSample {
    public final long timeMs;
    public final int count;
    public final float heapPercent;

    public LeakSample(long timeMs, int count, float heapPercent) {
        this.timeMs = timeMs;
        this.count = count;
        this.heapPercent = heapPercent;
    }
}
