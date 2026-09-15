package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts and processes ATL log output.
 * This class parses ATL messages and routes them through the CTL evaluation system.
 */
public class AtlOutputInterceptor {
    private static final RateLimiter rateLimiter = new RateLimiter();
    
    // Patterns to match ATL output
    private static final Pattern LEAK_DETECTED_PATTERN = Pattern.compile("Memory Leaks detected|Listing leaks", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHUNK_PATTERN = Pattern.compile("\\|\\s*-\\s*(\\w+)\\s*\\(([^)]+)\\):\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_PATTERN = Pattern.compile("\\|\\s*(\\w+):", Pattern.CASE_INSENSITIVE);
    // Pattern for "Player:" or "ChunkAccess:" type headers (with or without |)
    private static final Pattern TYPE_HEADER_PATTERN = Pattern.compile("^\\s*\\|?\\s*(\\w+):\\s*$", Pattern.CASE_INSENSITIVE);
    // Pattern for entries like "- ServerPlayer (minecraft): 1" (with or without |)
    private static final Pattern ENTRY_PATTERN = Pattern.compile("^\\s*\\|?\\s*-\\s*(\\w+)\\s*\\(([^)]+)\\):\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAKE_LEVEL_MANAGER_PATTERN = Pattern.compile("FakeLevelManagerMixin.*cancelled.*not matching hash", Pattern.CASE_INSENSITIVE);
    /** ATL sometimes prints Level: / Dimension: before leak rows */
    private static final Pattern LEVEL_OR_DIM_LINE = Pattern.compile(
        "^\\s*\\|?\\s*(?:Level|Dimension)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_LEVEL_BRACKET = Pattern.compile("ServerLevel\\[([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private static String currentType = null;
    private static String currentDimension = "unknown";
    private static boolean inLeakReport = false;
    private static boolean fakeLevelManagerReported = false;

    /**
     * Process a log message that might be from ATL.
     * Returns true if the message was intercepted and should be suppressed.
     */
    public static boolean interceptLogMessage(String message) {
        if (message == null) return false;

        // Check for FakeLevelManagerMixin pattern
        if (FAKE_LEVEL_MANAGER_PATTERN.matcher(message).find()) {
            if (!fakeLevelManagerReported) {
                ReportFormatter.reportFakeLevelManagerMixin();
                fakeLevelManagerReported = true;
            }
            return true; // Suppress the original message
        }

        // Check if this is the start of a leak report
        if (LEAK_DETECTED_PATTERN.matcher(message).find()) {
            inLeakReport = true;
            currentType = null;
            currentDimension = "unknown";
            return false; // Allow the header through
        }

        // If we're in a leak report, try to parse it
        if (inLeakReport) {
            String trimmed = message.trim();
            Matcher dimLine = LEVEL_OR_DIM_LINE.matcher(trimmed);
            if (dimLine.find()) {
                currentDimension = sanitizeDimension(dimLine.group(1));
                return false;
            }
            Matcher bracket = SERVER_LEVEL_BRACKET.matcher(message);
            if (bracket.find()) {
                currentDimension = sanitizeDimension(bracket.group(1));
            }

            // Check for type header (e.g., "| ChunkAccess:" or "Player:")
            Matcher typeHeaderMatcher = TYPE_HEADER_PATTERN.matcher(trimmed);
            if (typeHeaderMatcher.find()) {
                currentType = typeHeaderMatcher.group(1);
                return false; // Allow type headers through
            }
            
            // Also check the old pattern for compatibility
            Matcher typeMatcher = TYPE_PATTERN.matcher(message);
            if (typeMatcher.find()) {
                currentType = typeMatcher.group(1);
                return false; // Allow type headers through
            }

            // Check for leak entry (e.g., "- ServerPlayer (minecraft): 1" or "| - LevelChunk (minecraft): 32")
            Matcher entryMatcher = ENTRY_PATTERN.matcher(message.trim());
            if (entryMatcher.find()) {
                String targetClass = entryMatcher.group(1);
                String modName = entryMatcher.group(2);  // Extract mod name from parentheses
                int count = Integer.parseInt(entryMatcher.group(3));
                
                // ALWAYS track the leak, even if we suppress the message
                // Process the leak report and determine if we should suppress this message
                boolean shouldSuppress = processLeakReport(currentType != null ? currentType : "Unknown", targetClass, modName, currentDimension, count);
                return shouldSuppress;
            }
            
            // Also check the old chunk pattern for compatibility
            Matcher chunkMatcher = CHUNK_PATTERN.matcher(message);
            if (chunkMatcher.find()) {
                String targetClass = chunkMatcher.group(1);
                String modName = chunkMatcher.group(2);  // Extract mod name from parentheses
                int count = Integer.parseInt(chunkMatcher.group(3));
                
                // ALWAYS track the leak, even if we suppress the message
                boolean shouldSuppress = processLeakReport(currentType != null ? currentType : "Unknown", targetClass, modName, currentDimension, count);
                return shouldSuppress;
            }

            // Check if this is the end of the report (empty line or different format)
            if (message.trim().isEmpty() || (!message.contains("|") && !message.contains("Memory Leaks") && !message.contains("Listing leaks"))) {
                inLeakReport = false;
                currentType = null;
                currentDimension = "unknown";
            }
        }

        return false;
    }

    /**
     * Process a leak report and determine if the ATL message should be suppressed.
     * @return true if the ATL message should be suppressed, false if it should be allowed through
     */
    private static boolean processLeakReport(String type, String targetClass, String modName, String dimension, int count) {
        LeakSignature signature = new LeakSignature(type, targetClass, modName, dimension, count);
        LeakStateTracker tracker = LeakStateTracker.getInstance();
        
        // ALWAYS track the leak first, regardless of verdict
        LeakState state = tracker.getOrCreateState(signature);
        tracker.updateState(signature, count);
        
        // SparkProfilerManager will check for new leaks on each tick
        
        VerdictEngine.Verdict verdict = VerdictEngine.evaluate(signature, state, null);
        
        // Default behavior: suppress ATL messages, but always track
        // Only allow ATL through if it's a confirmed leak or suspicious trend
        
        // Check rate limiting
        if (!rateLimiter.shouldReport(signature, state) && verdict != VerdictEngine.Verdict.CONFIRMED_LEAK) {
            // Suppress due to rate limiting, but leak is still tracked
            state.suppressed = true;
            return true; // Suppress the ATL message
        }

        // Handle based on verdict only
        // ATL messages should only show for CONFIRMED_LEAK or SUSPICIOUS_TREND verdicts
        // Severity is for display purposes, verdict determines if ATL should warn
        if (VerdictEngine.shouldSuppress(verdict)) {
            state.suppressed = true;
            if (!PhaseManager.isInStartupGracePeriod() && PhaseManager.getCurrentPhase() != PhaseManager.Phase.WARMUP) {
                if (verdict == VerdictEngine.Verdict.BASELINE_RETENTION) {
                    ReportFormatter.reportBaselineRetention(signature, state);
                } else if (verdict == VerdictEngine.Verdict.TRANSITIONAL_NOISE) {
                    ReportFormatter.reportTransitionalNoise(signature, state);
                }
            }
        } else {
            state.suppressed = false;
            if (verdict == VerdictEngine.Verdict.SUSPICIOUS_TREND) {
                ReportFormatter.reportSuspiciousTrend(signature, state);
            } else if (verdict == VerdictEngine.Verdict.CONFIRMED_LEAK) {
                state.confirmedLeak = true;
                ReportFormatter.reportConfirmedLeak(signature, state);
            }
        }

        // Default: hide parsed ATL leak lines so the pack uses the CTL panel instead of chat/log spam
        if (Config.suppressAtlLeakLines) {
            return true;
        }
        return VerdictEngine.shouldSuppress(verdict);
    }

    public static void reset() {
        inLeakReport = false;
        currentType = null;
        currentDimension = "unknown";
        fakeLevelManagerReported = false;
    }

    private static String sanitizeDimension(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String s = raw.trim();
        if (s.length() > 120) {
            s = s.substring(0, 117) + "...";
        }
        return s.isEmpty() ? "unknown" : s;
    }
}
