package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents spam by rate-limiting repeated messages.
 */
public class RateLimiter {
    private static final long MIN_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private final Map<LeakSignature, Long> lastReportTime = new ConcurrentHashMap<>();

    public boolean shouldReport(LeakSignature signature, LeakState state) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastReportTime.get(signature);

        // Always report first occurrence
        if (lastTime == null) {
            lastReportTime.put(signature, currentTime);
            return true;
        }

        // Check if enough time has passed
        long timeSinceLastReport = currentTime - lastTime;
        
        // Always report if count changed significantly
        if (Math.abs(signature.count - state.previousCount) > 5) {
            lastReportTime.put(signature, currentTime);
            return true;
        }

        // Otherwise, rate limit
        if (timeSinceLastReport >= MIN_INTERVAL_MS) {
            lastReportTime.put(signature, currentTime);
            return true;
        }

        return false;
    }

    public void clear() {
        lastReportTime.clear();
    }
}
