package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks leak signatures and their state over time.
 */
public class LeakStateTracker {
    private final Map<LeakSignature, LeakState> leakStates = new ConcurrentHashMap<>();
    private static LeakStateTracker instance;

    public static LeakStateTracker getInstance() {
        if (instance == null) {
            instance = new LeakStateTracker();
        }
        return instance;
    }

    public LeakState getOrCreateState(LeakSignature signature) {
        return leakStates.computeIfAbsent(signature, sig -> new LeakState(sig, System.currentTimeMillis()));
    }

    public LeakState getState(LeakSignature signature) {
        return leakStates.get(signature);
    }

    public void updateState(LeakSignature signature, int newCount) {
        LeakState state = getOrCreateState(signature);
        long now = System.currentTimeMillis();
        state.update(newCount, now);
        double heap = SparkDataProvider.getMemoryData().heapUsagePercent;
        state.recordSample(now, state.currentCount, heap);
        LeakPersistenceManager.markDirty();
    }

    public void loadFromPersistence(java.util.Map<LeakSignature, LeakState> incoming) {
        leakStates.clear();
        leakStates.putAll(incoming);
    }

    public void clear() {
        leakStates.clear();
    }

    public Map<LeakSignature, LeakState> getAllStates() {
        return leakStates;
    }
}
