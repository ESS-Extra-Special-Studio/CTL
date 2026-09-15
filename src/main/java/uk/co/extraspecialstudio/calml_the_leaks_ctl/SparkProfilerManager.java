package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages automatic Spark profiling when leaks are detected.
 * Triggers Spark profiling, waits 5 minutes, then reads data for enhanced diagnosis.
 * <p>NeoForge ships Spark in {@code me.lucko.spark.neoforge} — there is no Forge-style {@code getInstance()}
 * plugin singleton; we use {@code me.lucko.spark.profiler.Profiler} when present, else {@code /spark} commands.
 */
@EventBusSubscriber(modid = CalmlTheLeaks.MODID, bus = EventBusSubscriber.Bus.GAME)
public class SparkProfilerManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<LeakSignature, ProfilingSession> activeSessions = new ConcurrentHashMap<>();
    private static final long PROFILING_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    /** Human-readable note for the diagnostics GUI after a profiling window completes (JVM snapshot only). */
    private static volatile String lastSessionNote = "";

    public static String getLastSessionNote() {
        return lastSessionNote;
    }

    /** Exposed for honest Spark status lines (profiler running or not). */
    public static boolean isProfilerRunningReflective() {
        return isSparkAlreadyRunning();
    }
    
    /**
     * Represents an active Spark profiling session for a leak.
     */
    private static class ProfilingSession {
        final LeakSignature signature;
        final long startTime;
        boolean sparkTriggered;
        boolean dataRead;
        
        ProfilingSession(LeakSignature signature) {
            this.signature = signature;
            this.startTime = System.currentTimeMillis();
            this.sparkTriggered = false;
            this.dataRead = false;
        }
        
        boolean shouldReadData() {
            return sparkTriggered && !dataRead && 
                   (System.currentTimeMillis() - startTime) >= PROFILING_DURATION_MS;
        }
        
        boolean isExpired() {
            // Keep session for 10 minutes total (5 min profiling + 5 min buffer)
            return (System.currentTimeMillis() - startTime) > (PROFILING_DURATION_MS * 2);
        }
    }
    
    /**
     * Checks for new leaks and triggers Spark profiling if needed.
     * Only triggers for confirmed leaks or severe leaks (HIGH severity).
     * Called from tick event to check all tracked leaks.
     */
    private static void checkForNewLeaks(MinecraftServer server) {
        // Check if auto Spark profiling is disabled
        if (!Config.autoSparkProfiling) {
            return;
        }
        
        if (server == null || !SparkDataProvider.isSparkAvailable()) return;
        
        // Check if Spark is already running - if so, don't start it again (avoids chat spam)
        if (isSparkAlreadyRunning()) {
            return; // Spark is already profiling, don't start another session
        }
        
        LeakStateTracker tracker = LeakStateTracker.getInstance();
        for (Map.Entry<LeakSignature, LeakState> entry : tracker.getAllStates().entrySet()) {
            LeakSignature signature = entry.getKey();
            LeakState state = entry.getValue();
            
            // Check if we already have a session for this leak
            if (activeSessions.containsKey(signature)) {
                continue; // Already profiling this leak
            }
            
            // Get the verdict to check if it's a confirmed leak
            VerdictEngine.Verdict verdict = VerdictEngine.evaluate(signature, state, server);
            
            // Get the analysis to check severity
            LeakAnalyser.LeakAnalysis analysis = LeakAnalyser.analyse(signature, state, server);
            
            // Only trigger Spark profiling for:
            // 1. Confirmed leaks (CONFIRMED_LEAK verdict)
            // 2. Severe leaks (HIGH severity)
            boolean shouldProfile = false;
            
            if (verdict == VerdictEngine.Verdict.CONFIRMED_LEAK) {
                shouldProfile = true;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[CTL] Leak {} is confirmed - triggering Spark profiling", signature.targetClass);
                }
            } else if (analysis != null && "HIGH".equals(analysis.severity)) {
                shouldProfile = true;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[CTL] Leak {} has HIGH severity - triggering Spark profiling", signature.targetClass);
                }
            }
            
            if (shouldProfile) {
                ProfilingSession session = new ProfilingSession(signature);
                activeSessions.put(signature, session);
                
                // Trigger Spark profiling
                triggerSparkProfiling(server, session);
            }
        }
    }
    
    /**
     * Checks if Spark profiler is already running.
     * Returns true if Spark is currently profiling, false otherwise.
     */
    private static boolean isSparkAlreadyRunning() {
        try {
            // Try Spark 1.x API
            try {
                Class<?> sparkProfilerClass = Class.forName("me.lucko.spark.profiler.Profiler");
                Object profilerInstance = sparkProfilerClass.getMethod("getInstance").invoke(null);
                java.lang.reflect.Method isRunningMethod = sparkProfilerClass.getMethod("isRunning");
                Boolean isRunning = (Boolean) isRunningMethod.invoke(profilerInstance);
                return isRunning != null && isRunning;
            } catch (ClassNotFoundException e) {
                // Newer Spark may not ship this class; command path still works
            }
        } catch (Exception e) {
            // Can't check - assume not running
        }
        return false;
    }
    
    /**
     * Triggers Spark profiling silently in the background.
     * Uses reflection first (silent) and only falls back to commands if needed.
     */
    private static void triggerSparkProfiling(MinecraftServer server, ProfilingSession session) {
        // Try reflection first - this is completely silent and doesn't post to chat
        try {
            triggerSparkViaReflection(server);
            session.sparkTriggered = true;
            LOGGER.debug("[CTL] Triggered Spark profiling silently for leak: {} (will read data in 5 minutes)", 
                session.signature.targetClass);
            return; // Success - don't try command method
        } catch (Exception e) {
            // Reflection failed, try command method as fallback
            LOGGER.debug("[CTL] Reflection method failed, trying command method: {}", e.getMessage());
        }
        
        // Fallback: Try command method (may post to chat, but we suppress output)
        try {
            if (server.getCommands() != null) {
                // Execute Spark profiler start command with suppressed output
                // Note: Spark may still post to chat, but we try to suppress it
                String command = "spark profiler start";
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                    command
                );
                
                session.sparkTriggered = true;
                LOGGER.debug("[CTL] Triggered Spark profiling via command for leak: {} (will read data in 5 minutes)", 
                    session.signature.targetClass);
            }
        } catch (Exception e) {
            LOGGER.debug("[CTL] Could not trigger Spark profiling: {}", e.getMessage());
        }
    }
    
    /**
     * Attempts to trigger Spark profiling via reflection (silent, no chat messages).
     * This is the preferred method as it doesn't post to chat.
     * However, Spark may still post messages internally - we check if it's already running first.
     */
    private static void triggerSparkViaReflection(MinecraftServer server) throws Exception {
        // CRITICAL: Check if Spark is already running first
        // If it is, don't start it again - this prevents chat spam
        if (isSparkAlreadyRunning()) {
            LOGGER.debug("[CTL] Spark profiler is already running, not starting another session");
            return; // Success - Spark is already running, no need to start again
        }
        
        // Try multiple possible Spark API paths
        // Spark 1.x uses me.lucko.spark.profiler.Profiler
        // Spark 2.x might use different paths
        
        // Method 1: Try Spark 1.x API
        try {
            Class<?> sparkProfilerClass = Class.forName("me.lucko.spark.profiler.Profiler");
            Object profilerInstance = sparkProfilerClass.getMethod("getInstance").invoke(null);
            
            // Double-check if profiler is already running (race condition protection)
            java.lang.reflect.Method isRunningMethod = sparkProfilerClass.getMethod("isRunning");
            Boolean isRunning = (Boolean) isRunningMethod.invoke(profilerInstance);
            
            if (isRunning != null && isRunning) {
                LOGGER.debug("[CTL] Spark profiler is already running, skipping start");
                return; // Already running, don't start again
            }
            
            // Try to start profiling silently
            // Note: Spark's start() method may still post to chat internally
            // We can't prevent that, but we at least avoid duplicate starts
            sparkProfilerClass.getMethod("start").invoke(profilerInstance);
            
            LOGGER.debug("[CTL] Triggered Spark profiling via reflection (Spark 1.x API) - Spark may post to chat");
            return;
        } catch (ClassNotFoundException e) {
            // Profiler class absent in this Spark build
        }

        // If all reflection methods fail, throw exception to trigger fallback
        throw new Exception("Spark API not available via reflection");
    }
    
    /**
     * Reads Spark profiling data after the 5-minute wait period.
     */
    private static void readSparkData(MinecraftServer server, ProfilingSession session) {
        try {
            // Try to get Spark profiling data
            SparkDataProvider.MemoryData memoryData = SparkDataProvider.getMemoryData();
            
            // Update the leak state with enhanced memory data
            LeakStateTracker tracker = LeakStateTracker.getInstance();
            LeakState state = tracker.getState(session.signature);
            
            if (state != null) {
                LeakAnalyser.analyse(session.signature, state, server);
                lastSessionNote = "After Spark window: JVM heap was "
                    + String.format("%.1f%%", memoryData.heapUsagePercent)
                    + " — CTL still diagnoses from ATL counts + JVM heap, not Spark heap dumps.";
                LOGGER.info("[CTL] Post-profiling JVM snapshot for {}: heap {}%",
                    session.signature.targetClass,
                    String.format("%.1f", memoryData.heapUsagePercent));
            }
            
            session.dataRead = true;
        } catch (Exception e) {
            LOGGER.debug("[CTL] Could not read Spark data: {}", e.getMessage());
        }
    }
    
    /**
     * Called every server tick to check if profiling sessions are ready to read data.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;
        
        // Check for new leaks that need profiling (only check every 20 ticks = 1 second to avoid overhead)
        if (server.getTickCount() % 20 == 0) {
            checkForNewLeaks(server);
        }
        
        // Check all active sessions
        Iterator<Map.Entry<LeakSignature, ProfilingSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LeakSignature, ProfilingSession> entry = it.next();
            ProfilingSession session = entry.getValue();
            
            // Remove expired sessions
            if (session.isExpired()) {
                it.remove();
                continue;
            }
            
            // Check if it's time to read Spark data
            if (session.shouldReadData()) {
                readSparkData(server, session);
            }
        }
    }
    
    /**
     * Clears all active profiling sessions (e.g., on server stop).
     */
    public static void clear() {
        activeSessions.clear();
        lastSessionNote = "";
    }
}
