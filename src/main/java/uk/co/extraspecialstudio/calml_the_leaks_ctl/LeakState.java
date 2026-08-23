package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks the state of a single leak signature over time.
 */
public class LeakState {
    public enum Trend {
        STABLE,
        GROWING,
        SHRINKING
    }

    public final LeakSignature signature;
    public long firstSeenTimestamp;
    public long lastSeenTimestamp;
    public int previousCount;
    public int currentCount;
    public Trend trend;
    public boolean confirmedLeak;
    public boolean suppressed;
    public int reportCount;
    public int growthEvents;  // Number of times count increased
    public int stableEvents;  // Number of times count stayed same
    public int shrinkEvents;  // Number of times count decreased
    public int maxCount;      // Highest count seen
    public int minCount;      // Lowest count seen
    public boolean deathDetanglerActive;  // True if Death Detangler is active and handling this leak type
    public int deathDetanglerCleanupCount;  // Number of cleanups Death Detangler has performed

    private final ArrayDeque<LeakSample> recentSamples = new ArrayDeque<>();

    /**
     * Rebuilds tracker state from world disk (signature count is historical; equality ignores count).
     */
    public static LeakState fromPersistence(
        String type,
        String targetClass,
        String modName,
        String dimension,
        int signatureCount,
        long firstSeen,
        long lastSeen,
        int previousCount,
        int currentCount,
        int trendOrdinal,
        boolean confirmedLeak,
        boolean suppressed,
        int reportCount,
        int growthEvents,
        int stableEvents,
        int shrinkEvents,
        int maxCount,
        int minCount,
        boolean deathDetanglerActive,
        int deathDetanglerCleanupCount,
        List<LeakSample> samples
    ) {
        Trend[] trends = Trend.values();
        Trend tr = trendOrdinal >= 0 && trendOrdinal < trends.length ? trends[trendOrdinal] : Trend.STABLE;
        LeakSignature sig = new LeakSignature(
            type != null ? type : "",
            targetClass != null ? targetClass : "",
            modName != null ? modName : "",
            dimension != null ? dimension : "",
            signatureCount
        );
        LeakState s = new LeakState(sig, firstSeen);
        s.lastSeenTimestamp = lastSeen;
        s.previousCount = previousCount;
        s.currentCount = currentCount;
        s.trend = tr;
        s.confirmedLeak = confirmedLeak;
        s.suppressed = suppressed;
        s.reportCount = reportCount;
        s.growthEvents = growthEvents;
        s.stableEvents = stableEvents;
        s.shrinkEvents = shrinkEvents;
        s.maxCount = maxCount;
        s.minCount = minCount;
        s.deathDetanglerActive = deathDetanglerActive;
        s.deathDetanglerCleanupCount = deathDetanglerCleanupCount;
        s.replaceSamplesFromPersistence(samples != null ? samples : Collections.emptyList());
        return s;
    }

    public LeakState(LeakSignature signature, long timestamp) {
        this.signature = signature;
        this.firstSeenTimestamp = timestamp;
        this.lastSeenTimestamp = timestamp;
        this.previousCount = signature.count;
        this.currentCount = signature.count;
        this.trend = Trend.STABLE;
        this.confirmedLeak = false;
        this.suppressed = false;
        this.reportCount = 1;
        this.growthEvents = 0;
        this.stableEvents = 0;
        this.shrinkEvents = 0;
        this.maxCount = signature.count;
        this.minCount = signature.count;
        this.deathDetanglerActive = false;
        this.deathDetanglerCleanupCount = 0;
    }

    public void update(int newCount, long timestamp) {
        this.previousCount = this.currentCount;
        this.currentCount = newCount;
        this.lastSeenTimestamp = timestamp;
        this.reportCount++;

        if (newCount > previousCount) {
            this.trend = Trend.GROWING;
            this.growthEvents++;
        } else if (newCount < previousCount) {
            this.trend = Trend.SHRINKING;
            this.shrinkEvents++;
        } else {
            this.trend = Trend.STABLE;
            this.stableEvents++;
        }
        
        if (newCount > maxCount) {
            this.maxCount = newCount;
        }
        if (newCount < minCount) {
            this.minCount = newCount;
        }
    }
    
    /**
     * Calculates confidence level based on how much data we have.
     * More reports = higher confidence.
     * Confidence increases gradually with each observation.
     */
    public double getConfidence() {
        // Gradual increase: 30% base, +5% per observation up to 95%
        double baseConfidence = 0.3;
        double perObservation = 0.05;
        double maxConfidence = 0.95;
        
        // Cap at max confidence
        double calculated = baseConfidence + (reportCount * perObservation);
        return Math.min(calculated, maxConfidence);
    }
    
    /**
     * Gets pattern consistency - how consistent the trend is over time.
     */
    public double getPatternConsistency() {
        if (reportCount < 2) return 0.0;
        int totalEvents = growthEvents + stableEvents + shrinkEvents;
        if (totalEvents == 0) return 0.0;
        
        // If one pattern dominates, high consistency
        double maxPatternRatio = Math.max(
            Math.max((double)growthEvents / totalEvents, (double)stableEvents / totalEvents),
            (double)shrinkEvents / totalEvents
        );
        return maxPatternRatio;
    }

    public long getAgeSeconds(long currentTime) {
        return (currentTime - firstSeenTimestamp) / 1000;
    }

    public long getTimeSinceLastSeen(long currentTime) {
        return (currentTime - lastSeenTimestamp) / 1000;
    }

    /**
     * Records one point for sparkline / acceleration (called after {@link #update}).
     */
    public synchronized void recordSample(long timeMs, int count, double heapPercent) {
        int cap = Config.timeSeriesMaxSamples;
        while (recentSamples.size() >= cap) {
            recentSamples.removeFirst();
        }
        float hp = (float) Math.max(0.0, Math.min(100.0, heapPercent));
        recentSamples.addLast(new LeakSample(timeMs, count, hp));
    }

    /** Thread-safe copy for networking / analysis. */
    public synchronized List<LeakSample> copySamples() {
        return new ArrayList<>(recentSamples);
    }

    private synchronized void replaceSamplesFromPersistence(List<LeakSample> samples) {
        recentSamples.clear();
        int cap = Config.timeSeriesMaxSamples;
        for (LeakSample sm : samples) {
            while (recentSamples.size() >= cap) {
                recentSamples.removeFirst();
            }
            recentSamples.addLast(sm);
        }
    }
}
