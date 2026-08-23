package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Formats human-readable CTL messages with the appropriate tone.
 */
public class ReportFormatter {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void reportBaselineRetention(LeakSignature signature, LeakState state) {
        String modInfo = formatModInfo(signature);
        String causeExplanation = explainLeakCause(signature);
        if (signature.isKnownFalsePositive()) {
            LOGGER.info("[CTL] {} detected {} chunks retained by mod '{}' (likely claimed chunks).", 
                modInfo, signature.count, signature.modName);
            LOGGER.info("[CTL] Class: {} ({}). This is expected behavior for chunk claiming systems.", 
                signature.targetClass, causeExplanation);
            LOGGER.info("[CTL] No action required - claimed chunks are intentionally kept loaded.");
        } else {
            LOGGER.info("[CTL] {} detected baseline chunk retention: {} chunks from mod '{}'.", 
                modInfo, signature.count, signature.modName);
            LOGGER.info("[CTL] Class: {} ({}). This is normal Forge behavior.", 
                signature.targetClass, causeExplanation);
            LOGGER.info("[CTL] No action required.");
        }
    }

    public static void reportTransitionalNoise(LeakSignature signature, LeakState state) {
        long uptime = PhaseManager.getServerUptimeSeconds();
        String modInfo = formatModInfo(signature);
        String causeExplanation = explainLeakCause(signature);
        LOGGER.info("[CTL] {} detected temporary chunk retention during server warm-up: {} chunks.", 
            modInfo, signature.count);
        LOGGER.info("[CTL] Source: Mod '{}' retaining {} ({}).", 
            signature.modName, signature.targetClass, causeExplanation);
        LOGGER.info("[CTL] Monitoring for stabilization before escalating. (Uptime: {}s)", uptime);
    }

    public static void reportSuspiciousTrend(LeakSignature signature, LeakState state) {
        String modInfo = formatModInfo(signature);
        
        // Use dynamic analysis
        LeakAnalyser.LeakAnalysis analysis = LeakAnalyser.analyse(signature, state, null);
        
        LOGGER.warn("[CTL] {} detected leak trending upward: {} → {} objects.", 
            modInfo, state.previousCount, signature.count);
        LOGGER.warn("[CTL] Object Type: {} - {}", analysis.objectCategory, analysis.objectDescription);
        
        if (analysis.potentialConflict != null) {
            LOGGER.warn("[CTL] {}", analysis.potentialConflict);
        }
        
        if (analysis.abnormalCount) {
            LOGGER.warn("[CTL] {}", analysis.abnormalReason);
        }
        
        // Mention Death Detangler if it's active
        if (signature.isPlayerRelated() && DeathDetanglerIntegration.isDeathDetanglerActive()) {
            int cleanups = DeathDetanglerIntegration.getTotalCleanups();
            LOGGER.warn("[CTL] Note: Death Detangler is active and has cleaned up {} player(s). Monitoring if leak persists.", cleanups);
        }
        
        LOGGER.warn("[CTL] {}", analysis.recommendation);
    }

    public static void reportConfirmedLeak(LeakSignature signature, LeakState state) {
        String modInfo = formatModInfo(signature);
        
        // Use dynamic analysis
        LeakAnalyser.LeakAnalysis analysis = LeakAnalyser.analyse(signature, state, null);
        
        LOGGER.error("[CTL] {} CONFIRMED LEAK DETECTED", modInfo);
        LOGGER.error("[CTL] Object Type: {} - {}", analysis.objectCategory, analysis.objectDescription);
        LOGGER.error("[CTL] Retained objects: {} (growing from {})", signature.count, state.previousCount);
        
        if (analysis.primaryMod != null && !analysis.primaryMod.equals("minecraft")) {
            LOGGER.error("[CTL] Primary mod: '{}'", analysis.primaryMod);
        }
        
        if (analysis.potentialConflict != null) {
            LOGGER.error("[CTL] {}", analysis.potentialConflict);
        }
        
        if (analysis.abnormalCount) {
            LOGGER.error("[CTL] {}", analysis.abnormalReason);
        }
        
        LOGGER.error("[CTL] {}", analysis.recommendation);
        
        if (state.trend == LeakState.Trend.GROWING) {
            LOGGER.error("[CTL] Growth rate: {} → {} objects. This will cause memory issues if not addressed.", 
                state.previousCount, signature.count);
        }
        
        // Mention Death Detangler if it's active
        if (signature.isPlayerRelated() && DeathDetanglerIntegration.isDeathDetanglerActive()) {
            int cleanups = DeathDetanglerIntegration.getTotalCleanups();
            if (state.deathDetanglerActive) {
                LOGGER.error("[CTL] Death Detangler is active and has cleaned up {} player(s), but leak is still growing. " +
                            "This suggests the leak source is persistent or Death Detangler needs configuration.", cleanups);
            } else {
                LOGGER.error("[CTL] Note: Death Detangler is installed and has cleaned up {} player(s). " +
                            "If leak persists, Death Detangler may need additional configuration.", cleanups);
            }
        }
    }
    
    /**
     * Provides a human-friendly explanation of what the leak type means.
     */
    private static String explainLeakCause(LeakSignature signature) {
        String type = signature.type != null ? signature.type.toLowerCase() : "";
        String className = signature.targetClass != null ? signature.targetClass.toLowerCase() : "";
        
        // Player-related leaks - these are often problematic
        if (signature.isPlayerRelated()) {
            if (className.contains("serverplayer")) {
                return "server player object - if count grows, indicates player clone leak (common with Hardcore Revival + Corpse mod conflict)";
            } else if (className.contains("localplayer")) {
                return "local player object - normally 1, multiple indicates client-side player leak";
            } else {
                return "player-related object - growing count indicates player entities not being cleaned up after death";
            }
        }
        
        // Explain based on type
        if (type.contains("chunkaccess")) {
            return "chunk reference holder - something is keeping chunks loaded";
        } else if (type.contains("chunk") || className.contains("chunk")) {
            if (className.contains("levelchunk")) {
                return "actual chunk data - chunks are being retained in memory";
            } else {
                return "chunk-related object - may indicate chunk loading issue";
            }
        }
        
        // Explain based on class name
        if (className.contains("ticket") || className.contains("chunkticket")) {
            return "chunk ticket holder - preventing chunk unloading";
        } else if (className.contains("entity")) {
            return "entity-related - entities may be preventing chunk unload";
        } else if (className.contains("tile") || className.contains("blockentity")) {
            return "block entity - tile entities may be holding chunk references";
        }
        
        return "memory retention - requires investigation";
    }
    
    /**
     * Identifies known mod conflicts that cause specific leak patterns.
     */
    public static String identifyModConflict(LeakSignature signature, LeakState state) {
        // Check if Death Detangler is active and handling this
        boolean deathDetanglerActive = DeathDetanglerIntegration.isDeathDetanglerActive();
        if (state.deathDetanglerActive) {
            deathDetanglerActive = true;
        }
        
        // If Death Detangler is active and managing leaks, don't blame it - it's helping!
        // Only report conflicts if Death Detangler is NOT active, or if leaks are growing despite Death Detangler
        if (signature.isPlayerRelated() && state.trend == LeakState.Trend.GROWING) {
            if (deathDetanglerActive && signature.count <= 3) {
                // Death Detangler is active and count is reasonable - it's managing it
                return null; // Don't report a conflict - Death Detangler is handling it
            } else if (deathDetanglerActive) {
                // Death Detangler is active but leaks are still growing significantly
                return "MOD CONFLICT DETECTED: Hardcore Revival + Corpse mods are creating player clone leaks. " +
                       "Death Detangler is active and has cleaned up " + state.deathDetanglerCleanupCount + " player(s), " +
                       "but leaks are still growing. The conflict may be too severe for automatic cleanup.";
            } else {
                return "LIKELY MOD CONFLICT: Hardcore Revival + Corpse mods both create player corpses on death, causing player clone leaks. " +
                       "Each death creates a new player entity that isn't cleaned up. Consider installing Death Detangler mod or disabling one of these mods.";
            }
        }
        
        if (signature.isPlayerRelated() && signature.count > 2) {
            if (deathDetanglerActive && signature.count <= 2) {
                // Death Detangler is active and count is normal - it's working
                return null; // Don't report a conflict
            } else if (deathDetanglerActive) {
                // Death Detangler is active but count is still elevated
                return "MULTIPLE PLAYER ENTITIES: Death Detangler is active and has cleaned up " + state.deathDetanglerCleanupCount + 
                       " player(s), but " + signature.count + " player entities remain. " +
                       "This may be expected if corpse mods are holding references until inventory recovery.";
            } else {
                return "SUSPECTED MOD CONFLICT: Multiple player entities detected. This often occurs when death-related mods conflict. " +
                   "Check for mods that handle player death/resurrection (Hardcore Revival, Corpse, etc.)";
            }
        }
        
        return null;
    }
    
    /**
     * Formats mod information for messages (e.g., "ATL" or "CTL").
     */
    private static String formatModInfo(LeakSignature signature) {
        return "ATL";
    }

    public static void reportFakeLevelManagerMixin() {
        LOGGER.info("[CTL] ATL mixin safety fallback detected.");
        LOGGER.info("[CTL] This indicates a version mismatch or MixinSquared guard.");
        LOGGER.info("[CTL] It does NOT represent a memory leak and can be ignored unless ATL functionality is broken.");
    }

    /**
     * Formats status with colors (compact format).
     */
    public static List<Component> formatStatusColored(LeakStateTracker tracker) {
        List<Component> components = new ArrayList<>();
        
        // Header
        components.add(Component.literal("CTL Status").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)).withBold(true)));
        
        // Status info
        PhaseManager.Phase phase = PhaseManager.getCurrentPhase();
        TextColor phaseColor = phase == PhaseManager.Phase.LIVE ? TextColor.fromRgb(0x55FF55) : 
                              phase == PhaseManager.Phase.WARMUP ? TextColor.fromRgb(0xFFAA00) : 
                              TextColor.fromRgb(0xAAAAAA);
        
        components.add(Component.literal("  Phase: ").withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC)))
            .append(Component.literal(phase.toString()).withStyle(style -> style.withColor(phaseColor))));
        
        components.add(Component.literal("  Uptime: ").withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC)))
            .append(Component.literal(PhaseManager.getServerUptimeSeconds() + "s").withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF)))));
        
        components.add(Component.literal("  Baseline threshold: ").withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC)))
            .append(Component.literal(String.valueOf(Config.baselineChunkThreshold)).withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF)))));
        
        components.add(Component.literal("  Tracked leaks: ").withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC)))
            .append(Component.literal(String.valueOf(tracker.getAllStates().size())).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFB84D)))));
        
        if (!tracker.getAllStates().isEmpty()) {
            components.add(Component.literal("").withStyle(style -> style.withColor(TextColor.fromRgb(0x555555)))); // Empty line
            components.add(Component.literal("Tracked Leaks:").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)).withBold(true)));
            
            int leakIndex = 0;
            for (Map.Entry<LeakSignature, LeakState> entry : tracker.getAllStates().entrySet()) {
                LeakSignature sig = entry.getKey();
                LeakState state = entry.getValue();
                VerdictEngine.Verdict verdict = VerdictEngine.evaluate(sig, state, null);
                
                TextColor leakColor = getLeakColor(leakIndex++);
                TextColor verdictColor = verdict == VerdictEngine.Verdict.CONFIRMED_LEAK ? TextColor.fromRgb(0xFF5555) :
                                        verdict == VerdictEngine.Verdict.SUSPICIOUS_TREND ? TextColor.fromRgb(0xFFAA00) :
                                        TextColor.fromRgb(0x55FF55);
                
                String suppressed = state.suppressed ? " [SUPPRESSED]" : "";
                // Use currentCount from state (current leak count) instead of sig.count (initial count)
                String dimTag = (sig.dimension != null && !sig.dimension.isEmpty() && !"unknown".equalsIgnoreCase(sig.dimension))
                    ? " @" + sig.dimension + " " : " ";
                Component leakLine = Component.literal("  ").withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC)))
                    .append(Component.literal("[" + sig.type + "] ").withStyle(style -> style.withColor(leakColor).withBold(true)))
                    .append(Component.literal(sig.targetClass).withStyle(style -> style.withColor(leakColor)))
                    .append(Component.literal(dimTag + "(" + sig.modName + ")").withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))))
                    .append(Component.literal(" - Count: " + state.currentCount).withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF))))
                    .append(Component.literal(" | Trend: " + state.trend).withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC))))
                    .append(Component.literal(" | Verdict: " + verdict).withStyle(style -> style.withColor(verdictColor)))
                    .append(Component.literal(suppressed).withStyle(style -> style.withColor(TextColor.fromRgb(0x888888))));
                
                components.add(leakLine);
            }
        } else {
            components.add(Component.literal("  No leaks tracked yet. Run /atl force refresh to check for leaks.")
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
        }
        
        return components;
    }
    
    public static String formatStatus(LeakStateTracker tracker) {
        StringBuilder sb = new StringBuilder();
        sb.append("CTL Status:\n");
        sb.append("- Phase: ").append(PhaseManager.getCurrentPhase()).append("\n");
        sb.append("- Uptime: ").append(PhaseManager.getServerUptimeSeconds()).append("s\n");
        sb.append("- Baseline threshold: ").append(Config.baselineChunkThreshold).append("\n");
        sb.append("- Tracked leaks: ").append(tracker.getAllStates().size()).append("\n");
        
        if (!tracker.getAllStates().isEmpty()) {
            sb.append("\nAll Tracked Leaks (including suppressed):\n");
            for (Map.Entry<LeakSignature, LeakState> entry : tracker.getAllStates().entrySet()) {
                LeakSignature sig = entry.getKey();
                LeakState state = entry.getValue();
                VerdictEngine.Verdict verdict = VerdictEngine.evaluate(sig, state, null);
                String suppressed = state.suppressed ? " [SUPPRESSED]" : "";
                sb.append(String.format("  - %s: %s (%s) - Count: %d, Trend: %s, Verdict: %s%s\n", 
                    sig.type, sig.targetClass, sig.modName, state.currentCount, state.trend, verdict, suppressed));
            }
        } else {
            sb.append("\nNo leaks tracked yet. Run /atl force refresh to check for leaks.\n");
        }
        
        return sb.toString();
    }

    public static String formatExplain(LeakSignature signature, LeakState state, VerdictEngine.Verdict verdict) {
        // Use dynamic analysis instead of static explanation
        LeakAnalyser.LeakAnalysis analysis = LeakAnalyser.analyse(signature, state, null);
        
        StringBuilder sb = new StringBuilder();
        sb.append(analysis.explanation);
        
        // Add verdict information
        sb.append("\nCTL Verdict: ").append(verdict).append("\n");
        if (state.suppressed) {
            sb.append("ATL message is currently suppressed.\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Formats explanations for all tracked leaks using dynamic analysis with colors.
     */
    public static List<Component> formatExplainAllColored(LeakStateTracker tracker, net.minecraft.server.MinecraftServer server) {
        List<Component> components = new ArrayList<>();
        
        if (tracker.getAllStates().isEmpty()) {
            components.add(Component.literal("No leaks currently tracked. Run /atl force refresh to check for leaks.").withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
            return components;
        }
        
        components.add(Component.literal("CTL Dynamic Leak Analysis").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)).withBold(true)));
        
        int leakIndex = 0;
        // Analyse each leak dynamically
        for (Map.Entry<LeakSignature, LeakState> entry : tracker.getAllStates().entrySet()) {
            LeakSignature sig = entry.getKey();
            LeakState state = entry.getValue();
            
            // Only show leaks that are actually concerning (not baseline/transitional)
            // Check verdict to filter out noise
            VerdictEngine.Verdict verdict = VerdictEngine.evaluate(sig, state, server);
            if (VerdictEngine.shouldSuppress(verdict)) {
                // Skip baseline and transitional leaks - they're not worth showing in explain
                continue;
            }
            
            // Get unique color for this leak
            TextColor leakColor = getLeakColor(leakIndex++);
            
            // Perform dynamic analysis
            LeakAnalyser.LeakAnalysis analysis = LeakAnalyser.analyse(sig, state, server);
            
            // Format compact explanation with color
            formatCompactExplanation(components, analysis, leakColor);
        }
        
        // If we filtered everything out, show a message
        if (components.size() == 1) { // Only the header was added
            components.add(Component.literal("No concerning leaks detected. All tracked leaks are baseline or transitional.").withStyle(style -> style.withColor(TextColor.fromRgb(0x55FF55))));
        }
        
        return components;
    }
    
    /**
     * Gets a unique, readable color for each leak index.
     */
    private static TextColor getLeakColor(int index) {
        // Use a palette of readable colors
        int[] colors = {
            0xFF6B9D, // Light blue
            0xFFB84D, // Orange
            0x4ECDC4, // Cyan
            0xFFE66D, // Yellow
            0x95E1D3, // Mint
            0xF38181, // Coral
            0xAA96DA, // Lavender
            0xFCBAD3, // Pink
            0xFFFFD2, // Light yellow
            0xC7CEEA, // Light purple
        };
        return TextColor.fromRgb(colors[index % colors.length]);
    }
    
    /**
     * Formats a compact explanation for a single leak with color.
     */
    private static void formatCompactExplanation(List<Component> components, LeakAnalyser.LeakAnalysis analysis, TextColor leakColor) {
        LeakSignature sig = analysis.signature;
        LeakState state = analysis.state;
        
        // Header with leak identifier in color
        Component header = Component.literal("[" + sig.type + "] " + sig.targetClass)
            .withStyle(style -> style.withColor(leakColor).withBold(true));
        components.add(header);
        
        // Compact info line
        // Use currentCount from state (current leak count) instead of sig.count (initial count)
        String info = String.format("Type: %s | Mod: %s | Count: %d", 
            analysis.objectCategory, 
            sig.modName != null ? sig.modName : "unknown",
            state.currentCount);
        if (state.previousCount > 0 && state.previousCount != state.currentCount) {
            info += String.format(" (%d→%d)", state.previousCount, state.currentCount);
        }
        components.add(Component.literal("  " + info).withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC))));
        
        // Trend and severity
        String status = String.format("Trend: %s | Severity: %s | Pattern: %s", 
            state.trend, analysis.severity, analysis.pattern);
        TextColor statusColor = analysis.severity.equals("HIGH") ? TextColor.fromRgb(0xFF5555) : 
                               analysis.severity.equals("MEDIUM") ? TextColor.fromRgb(0xFFAA00) : 
                               TextColor.fromRgb(0x55FF55);
        components.add(Component.literal("  " + status).withStyle(style -> style.withColor(statusColor)));
        
        // Description (compact)
        components.add(Component.literal("  " + analysis.objectDescription).withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
        
        // Warnings if any
        if (analysis.potentialConflict != null) {
            components.add(Component.literal("  ⚠ " + analysis.potentialConflict).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAA00))));
            // Don't show related mods separately if they're already in the conflict warning
            // The conflict warning already lists the mods
        }
        if (analysis.abnormalCount) {
            components.add(Component.literal("  ⚠ " + analysis.abnormalReason).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAA00))));
        }
        
        // Recommendation (compact)
        components.add(Component.literal("  → " + analysis.recommendation).withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF))));

        if (analysis.velocityNote != null) {
            components.add(Component.literal("  " + analysis.velocityNote)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xC7CEEA))));
        }
        if (analysis.timeSeriesAcceleration != null && !analysis.timeSeriesAcceleration.isEmpty()) {
            components.add(Component.literal("  " + analysis.timeSeriesAcceleration)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xAA96DA))));
        }
        if (analysis.timeSeriesCountSpark != null && !analysis.timeSeriesCountSpark.isEmpty()) {
            components.add(Component.literal("  " + analysis.timeSeriesCountSpark)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF))));
        }
        if (analysis.timeSeriesHeapSpark != null && !analysis.timeSeriesHeapSpark.isEmpty()) {
            components.add(Component.literal("  " + analysis.timeSeriesHeapSpark)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0x4ECDC4))));
        }
        if (analysis.packContextNote != null) {
            components.add(Component.literal("  " + analysis.packContextNote)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFB84D))));
        }
        if (analysis.serverPerfNote != null) {
            components.add(Component.literal("  " + analysis.serverPerfNote)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAA00))));
        }
        
        // Death Detangler status if active
        if (sig.isPlayerRelated() && DeathDetanglerIntegration.isDeathDetanglerActive()) {
            int cleanups = DeathDetanglerIntegration.getTotalCleanups();
            if (state.deathDetanglerActive) {
                components.add(Component.literal("  ✓ Death Detangler active - cleaned up " + cleanups + " player(s)")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(0x55FF55))));
            } else {
                components.add(Component.literal("  ℹ Death Detangler installed (has cleaned " + cleanups + " player(s))")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF))));
            }
        }
        
        // Heap is always JVM (MemoryMXBean), not a Spark heap parse
        if (analysis.memoryData != null) {
            TextColor memoryColor = analysis.memoryData.heapUsagePercent > 80 ? TextColor.fromRgb(0xFF5555) :
                                   analysis.memoryData.heapUsagePercent > 60 ? TextColor.fromRgb(0xFFAA00) :
                                   TextColor.fromRgb(0x55FF55);
            String memoryText = "Heap (JVM): " + analysis.memoryData.getHeapUsageString();
            components.add(Component.literal("  " + memoryText).withStyle(style -> style.withColor(memoryColor)));

            SparkRuntimeProbe spark = SparkRuntimeProbe.probe();
            if (spark.sparkModLoaded) {
                String prof = spark.profilerRunning ? "running" : "idle";
                components.add(Component.literal("  Spark mod: installed | profiler (reflection): " + prof)
                    .withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF))));
                for (String note : spark.notes) {
                    components.add(Component.literal("  · " + note)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
                }
                components.add(Component.literal("  Diagnosis uses ATL counts + JVM heap; Spark profiles help you dig deeper in Spark's viewer, not inside CTL text.")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(0x888888))));
            } else {
                components.add(Component.literal("  Spark not loaded — optional; install Spark if you want interactive CPU/heap profiles.")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(0x888888))));
            }
        }
        
        // Confidence and uncertainty note (only show once, avoid duplication)
        // Check if uncertainty note contains a confidence percentage (not just the word "confidence")
        // We want to avoid showing "Confidence: X%" twice, but the uncertainty note just mentions "confidence" as a concept
        boolean uncertaintyNoteHasConfidencePercent = analysis.uncertaintyNote != null && 
                                                       (analysis.uncertaintyNote.contains("Confidence:") || 
                                                        analysis.uncertaintyNote.matches(".*[Cc]onfidence:?\\s*\\d+%.*"));
        
        if (analysis.uncertaintyNote != null && !uncertaintyNoteHasConfidencePercent) {
            components.add(Component.literal("  ⚠ " + analysis.uncertaintyNote)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAA00))));
        }
        
        // Always show confidence separately (uncertainty note doesn't contain the percentage)
        double pc = Config.prescriptionMinConfidence;
        TextColor confidenceColor = analysis.confidence >= pc + 0.08 ? TextColor.fromRgb(0x55FF55) :
            analysis.confidence >= pc * 0.75 ? TextColor.fromRgb(0xFFAA00) :
            TextColor.fromRgb(0xFF5555);
        String confidenceText = String.format("Confidence: %.0f%% (%d obs) | prescription ≥%.0f%% & ≥%d obs",
            analysis.confidence * 100, state.reportCount, pc * 100, Config.prescriptionMinObservations);
        components.add(Component.literal("  " + confidenceText).withStyle(style -> style.withColor(confidenceColor)));
        
        // Show confidence hints if available (these are separate from uncertainty note)
        if (analysis.confidenceHints != null && !analysis.confidenceHints.isEmpty()) {
            for (String hint : analysis.confidenceHints) {
                components.add(Component.literal("  💡 " + hint)
                    .withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF))));
            }
        }
        
        // Always show diagnosis, but indicate confidence level
        // For explain command, show full analysis regardless of confidence
        if (analysis.specificFix != null && !analysis.specificFix.isEmpty()) {
            String[] fixLines = analysis.specificFix.split("\n");
            for (String line : fixLines) {
                if (line.startsWith("PRESCRIPTION")) {
                    components.add(Component.literal("  " + line).withStyle(style -> style.withColor(TextColor.fromRgb(0x55FF55)).withBold(true)));
                } else if (line.startsWith("Recommended Action:") || line.startsWith("Confidence:")) {
                    components.add(Component.literal("  " + line).withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF))));
                } else if (line.matches("^\\d+\\..*")) {
                    components.add(Component.literal("    " + line).withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC))));
                } else {
                    components.add(Component.literal("  " + line).withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC))));
                }
            }
        } else {
            // Even without specific fix, show what we know
            if (analysis.primaryMod != null && !analysis.primaryMod.equals("minecraft")) {
                components.add(Component.literal("  → Primary suspect: " + analysis.primaryMod)
                    .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAA00))));
            }
            if (analysis.potentialConflict != null) {
                components.add(Component.literal("  → Potential conflict detected")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAA00))));
            }
            if (analysis.confidence < Config.prescriptionMinConfidence) {
                components.add(Component.literal("  → Gathering more data to improve diagnosis...")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
            }
        }
        
        // Related mods if any (only show if not already in conflict warning)
        // The conflict warning already lists related mods, so don't duplicate
        if (!analysis.relatedMods.isEmpty() && analysis.potentialConflict == null) {
            components.add(Component.literal("  Related: " + String.join(", ", analysis.relatedMods))
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
        }
        
        // Separator
        components.add(Component.literal("─".repeat(40)).withStyle(style -> style.withColor(TextColor.fromRgb(0x555555))));
    }

    /**
     * Plain colored lines for the diagnostics GUI (server-built, sent to the client).
     */
    public static final class GuiTextLine {
        public final int rgb;
        public final String text;

        public GuiTextLine(int rgb, String text) {
            this.rgb = rgb & 0xFFFFFF;
            this.text = text;
        }
    }

    public static List<GuiTextLine> buildGuiDetailLines(
        LeakSignature sig,
        LeakState state,
        VerdictEngine.Verdict verdict,
        LeakAnalyser.LeakAnalysis analysis,
        net.minecraft.server.MinecraftServer server
    ) {
        List<GuiTextLine> lines = new ArrayList<>();
        int leakIndex = Math.floorMod(Objects.hash(sig.type, sig.targetClass, sig.modName), 10);
        TextColor leakColor = getLeakColor(leakIndex);
        lines.add(new GuiTextLine(leakColor.getValue(), "[" + sig.type + "] " + sig.targetClass));

        String info = String.format("Type: %s | Mod: %s | Count: %d",
            analysis.objectCategory,
            sig.modName != null ? sig.modName : "unknown",
            state.currentCount);
        if (state.previousCount > 0 && state.previousCount != state.currentCount) {
            info += String.format(" (%d→%d)", state.previousCount, state.currentCount);
        }
        lines.add(new GuiTextLine(0xCCCCCC, "  " + info));

        String status = String.format("Trend: %s | Severity: %s | Pattern: %s",
            state.trend, analysis.severity, analysis.pattern);
        int statusRgb = analysis.severity.equals("HIGH") ? 0xFF5555
            : analysis.severity.equals("MEDIUM") ? 0xFFAA00 : 0x55FF55;
        lines.add(new GuiTextLine(statusRgb, "  " + status));

        lines.add(new GuiTextLine(0xAAAAAA, "  " + analysis.objectDescription));
        lines.add(new GuiTextLine(0xCCCCCC, "  Verdict: " + verdict + (state.suppressed ? " (ATL line suppressed)" : "")));

        if (analysis.potentialConflict != null) {
            lines.add(new GuiTextLine(0xFFAA00, "  ! " + analysis.potentialConflict));
        }
        if (analysis.abnormalCount) {
            lines.add(new GuiTextLine(0xFFAA00, "  ! " + analysis.abnormalReason));
        }

        lines.add(new GuiTextLine(0x88CCFF, "  -> " + analysis.recommendation));

        lines.add(new GuiTextLine(0xFFDDAA, "  Spark, AllTheLeaks & CTL (quick guide):"));
        lines.add(new GuiTextLine(0xAAAAAA, "  • ATL: finds suspicious counts and logs them."));
        lines.add(new GuiTextLine(0xAAAAAA, "  • CTL: explains ATL output and tracks trends (this text + main panel)."));
        lines.add(new GuiTextLine(0xAAAAAA, "  • Spark (optional): server command /spark — profiler + viewer for CPU and deep detail."));
        lines.add(new GuiTextLine(0xAAAAAA, "  • Heap %% below uses the Java VM; bar charts sample count + JVM heap — not Spark's heap UI."));
        lines.add(new GuiTextLine(Config.autoSparkProfiling ? 0x88CCFF : 0x888888,
            Config.autoSparkProfiling
                ? "  • auto_spark_profiling is ON — CTL may start Spark when a leak looks severe (calmtheleaks-common.toml)."
                : "  • auto_spark_profiling is OFF — start Spark yourself with /spark when you want a profile."));
        SparkRuntimeProbe sparkGuide = SparkRuntimeProbe.probe();
        lines.add(new GuiTextLine(sparkGuide.sparkModLoaded ? 0x55FF55 : 0xFFAA55,
            sparkGuide.sparkModLoaded
                ? "  • Spark mod is loaded on this server (TPS/telemetry can feed into CTL)."
                : "  • Spark mod is not loaded on this server — add Spark to the pack to use its profiler with CTL/ATL."));

        if (analysis.velocityNote != null) {
            lines.add(new GuiTextLine(0xC7CEEA, "  " + analysis.velocityNote));
        }
        if (analysis.timeSeriesAcceleration != null && !analysis.timeSeriesAcceleration.isEmpty()) {
            lines.add(new GuiTextLine(0xAA96DA, "  " + analysis.timeSeriesAcceleration));
        }
        if (analysis.timeSeriesCountSpark != null && !analysis.timeSeriesCountSpark.isEmpty()) {
            lines.add(new GuiTextLine(0x88CCFF, "  " + analysis.timeSeriesCountSpark));
        }
        if (analysis.timeSeriesHeapSpark != null && !analysis.timeSeriesHeapSpark.isEmpty()) {
            lines.add(new GuiTextLine(0x4ECDC4, "  " + analysis.timeSeriesHeapSpark));
        }
        if (analysis.packContextNote != null) {
            lines.add(new GuiTextLine(0xFFB84D, "  " + analysis.packContextNote));
        }
        if (analysis.serverPerfNote != null) {
            lines.add(new GuiTextLine(0xFFAA00, "  " + analysis.serverPerfNote));
        }

        if (sig.isPlayerRelated() && DeathDetanglerIntegration.isDeathDetanglerActive()) {
            int cleanups = DeathDetanglerIntegration.getTotalCleanups();
            if (state.deathDetanglerActive) {
                lines.add(new GuiTextLine(0x55FF55, "  Death Detangler active - cleaned up " + cleanups + " player(s)"));
            } else {
                lines.add(new GuiTextLine(0x88CCFF, "  Death Detangler installed (has cleaned " + cleanups + " player(s))"));
            }
        }

        if (analysis.memoryData != null) {
            int memRgb = analysis.memoryData.heapUsagePercent > 80 ? 0xFF5555
                : analysis.memoryData.heapUsagePercent > 60 ? 0xFFAA00 : 0x55FF55;
            lines.add(new GuiTextLine(memRgb, "  Heap (JVM): " + analysis.memoryData.getHeapUsageString()));
            SparkRuntimeProbe spark = SparkRuntimeProbe.probe();
            if (spark.sparkModLoaded) {
                String prof = spark.profilerRunning ? "running" : "idle";
                lines.add(new GuiTextLine(0x88CCFF, "  Spark mod: installed | profiler (reflection): " + prof));
                for (String note : spark.notes) {
                    lines.add(new GuiTextLine(0xAAAAAA, "  · " + note));
                }
                lines.add(new GuiTextLine(0x888888,
                    "  Stacks & timing: open Spark's viewer from /spark — CTL stays text-only here."));
            } else {
                lines.add(new GuiTextLine(0x888888,
                    "  Spark not on server — see 'Spark, AllTheLeaks & CTL' above to wire it up."));
            }
        }

        boolean uncertaintyNoteHasConfidencePercent = analysis.uncertaintyNote != null
            && (analysis.uncertaintyNote.contains("Confidence:")
            || analysis.uncertaintyNote.matches(".*[Cc]onfidence:?\\s*\\d+%.*"));

        if (analysis.uncertaintyNote != null && !uncertaintyNoteHasConfidencePercent) {
            lines.add(new GuiTextLine(0xFFAA00, "  ! " + analysis.uncertaintyNote));
        }

        double pc = Config.prescriptionMinConfidence;
        int confRgb = analysis.confidence >= pc + 0.08 ? 0x55FF55 : analysis.confidence >= pc * 0.75 ? 0xFFAA00 : 0xFF5555;
        lines.add(new GuiTextLine(confRgb,
            String.format("  Confidence: %.0f%% (%d obs) | prescription ≥%.0f%% & ≥%d obs",
                analysis.confidence * 100, state.reportCount, pc * 100, Config.prescriptionMinObservations)));

        if (analysis.confidenceHints != null) {
            for (String hint : analysis.confidenceHints) {
                lines.add(new GuiTextLine(0x88CCFF, "  * " + hint));
            }
        }

        if (analysis.specificFix != null && !analysis.specificFix.isEmpty()) {
            for (String fixLine : analysis.specificFix.split("\n")) {
                lines.add(new GuiTextLine(0xCCCCCC, "  " + fixLine));
            }
        } else {
            if (analysis.primaryMod != null && !analysis.primaryMod.equals("minecraft")) {
                lines.add(new GuiTextLine(0xFFAA00, "  -> Primary suspect: " + analysis.primaryMod));
            }
            if (analysis.potentialConflict != null) {
                lines.add(new GuiTextLine(0xFFAA00, "  -> Potential conflict detected"));
            }
            if (analysis.confidence < 0.7) {
                lines.add(new GuiTextLine(0xAAAAAA, "  -> Gathering more data to improve diagnosis..."));
            }
        }

        if (!analysis.relatedMods.isEmpty() && analysis.potentialConflict == null) {
            lines.add(new GuiTextLine(0xAAAAAA, "  Related: " + String.join(", ", analysis.relatedMods)));
        }

        lines.add(new GuiTextLine(0x555555, "─".repeat(40)));
        return lines;
    }
    
    /**
     * Formats explanations for all tracked leaks using dynamic analysis (legacy string version).
     */
    public static String formatExplainAll(LeakStateTracker tracker, net.minecraft.server.MinecraftServer server) {
        StringBuilder sb = new StringBuilder();
        sb.append("CTL Dynamic Leak Analysis:\n");
        sb.append("==========================\n\n");
        
        if (tracker.getAllStates().isEmpty()) {
            sb.append("No leaks currently tracked.\n");
            sb.append("Run /atl force refresh to check for leaks.\n");
            return sb.toString();
        }
        
        // Analyse each leak dynamically
        for (Map.Entry<LeakSignature, LeakState> entry : tracker.getAllStates().entrySet()) {
            LeakSignature sig = entry.getKey();
            LeakState state = entry.getValue();
            
            // Perform dynamic analysis
            LeakAnalyser.LeakAnalysis analysis = LeakAnalyser.analyse(sig, state, server);
            
            // Use the generated explanation
            sb.append(analysis.explanation);
            sb.append("\n");
            sb.append("═".repeat(50)).append("\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Formats a detailed list of all tracked leaks with mod information (colored, compact).
     */
    public static List<Component> formatLeaksColored(LeakStateTracker tracker, net.minecraft.server.MinecraftServer server) {
        List<Component> components = new ArrayList<>();
        
        components.add(Component.literal("CTL Tracked Leaks (Summary)").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)).withBold(true)));
        
        if (tracker.getAllStates().isEmpty()) {
            components.add(Component.literal("No leaks currently tracked. Run /atl force refresh to check for leaks.")
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
            return components;
        }
        
        // Group by mod for easier reading
        Map<String, java.util.List<Map.Entry<LeakSignature, LeakState>>> byMod = new java.util.HashMap<>();
        for (Map.Entry<LeakSignature, LeakState> entry : tracker.getAllStates().entrySet()) {
            String modName = entry.getKey().modName != null ? entry.getKey().modName : "unknown";
            byMod.computeIfAbsent(modName, k -> new java.util.ArrayList<>()).add(entry);
        }
        
        int leakIndex = 0;
        for (Map.Entry<String, java.util.List<Map.Entry<LeakSignature, LeakState>>> modEntry : byMod.entrySet()) {
            String modName = modEntry.getKey();
            
            components.add(Component.literal("").withStyle(style -> style.withColor(TextColor.fromRgb(0x555555)))); // Empty line
            components.add(Component.literal("Mod: ").withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC)))
                .append(Component.literal(modName).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFB84D)).withBold(true))));
            
            for (Map.Entry<LeakSignature, LeakState> leakEntry : modEntry.getValue()) {
                LeakSignature sig = leakEntry.getKey();
                LeakState state = leakEntry.getValue();
                VerdictEngine.Verdict verdict = VerdictEngine.evaluate(sig, state, null);
                
                TextColor leakColor = getLeakColor(leakIndex++);
                TextColor verdictColor = verdict == VerdictEngine.Verdict.CONFIRMED_LEAK ? TextColor.fromRgb(0xFF5555) :
                                        verdict == VerdictEngine.Verdict.SUSPICIOUS_TREND ? TextColor.fromRgb(0xFFAA00) :
                                        TextColor.fromRgb(0x55FF55);
                
                // Compact summary format (different from explain)
                // Use currentCount from state (current leak count) instead of sig.count (initial count)
                net.minecraft.network.chat.MutableComponent leakLine = Component.literal("  ")
                    .append(Component.literal("[" + sig.type + "] ").withStyle(style -> style.withColor(leakColor).withBold(true)))
                    .append(Component.literal(sig.targetClass).withStyle(style -> style.withColor(leakColor)))
                    .append(Component.literal(" - Count: " + state.currentCount).withStyle(style -> style.withColor(TextColor.fromRgb(0x88CCFF))));
                
                if (state.previousCount > 0 && state.previousCount != state.currentCount) {
                    leakLine = leakLine.append(Component.literal(" (" + state.previousCount + "→" + state.currentCount + ")")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC))));
                }
                
                leakLine = leakLine.append(Component.literal(" | Trend: " + state.trend).withStyle(style -> style.withColor(TextColor.fromRgb(0xCCCCCC))))
                    .append(Component.literal(" | " + verdict).withStyle(style -> style.withColor(verdictColor)));
                
                if (state.suppressed) {
                    leakLine = leakLine.append(Component.literal(" [SUPPRESSED]").withStyle(style -> style.withColor(TextColor.fromRgb(0x888888))));
                }
                
                components.add(leakLine);
            }
        }
        
        components.add(Component.literal("").withStyle(style -> style.withColor(TextColor.fromRgb(0x555555)))); // Empty line
        components.add(Component.literal("Use /ctl explain for detailed diagnosis").withStyle(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
        
        return components;
    }
    
    /**
     * Formats a detailed list of all tracked leaks with mod information.
     */
    public static String formatLeaks(LeakStateTracker tracker) {
        StringBuilder sb = new StringBuilder();
        sb.append("CTL Tracked Leaks:\n");
        sb.append("==================\n");
        
        if (tracker.getAllStates().isEmpty()) {
            sb.append("No leaks currently tracked.\n");
            sb.append("Run /atl force refresh to check for leaks.\n");
            return sb.toString();
        }
        
        // Group by mod for easier reading
        Map<String, java.util.List<Map.Entry<LeakSignature, LeakState>>> byMod = new java.util.HashMap<>();
        for (Map.Entry<LeakSignature, LeakState> entry : tracker.getAllStates().entrySet()) {
            String modName = entry.getKey().modName != null ? entry.getKey().modName : "unknown";
            byMod.computeIfAbsent(modName, k -> new java.util.ArrayList<>()).add(entry);
        }
        
        for (Map.Entry<String, java.util.List<Map.Entry<LeakSignature, LeakState>>> modEntry : byMod.entrySet()) {
            String modName = modEntry.getKey();
            sb.append("\nMod: ").append(modName).append("\n");
            
            for (Map.Entry<LeakSignature, LeakState> leakEntry : modEntry.getValue()) {
                LeakSignature sig = leakEntry.getKey();
                LeakState state = leakEntry.getValue();
                VerdictEngine.Verdict verdict = VerdictEngine.evaluate(sig, state, null);
                String causeExplanation = explainLeakCause(sig);
                
                sb.append(String.format("  Type: %s\n", sig.type));
                sb.append(String.format("  Class: %s (%s)\n", sig.targetClass, causeExplanation));
                // Use currentCount from state (current leak count) instead of sig.count (initial count)
                sb.append(String.format("  Count: %d", state.currentCount));
                if (state.previousCount > 0) {
                    sb.append(String.format(" (was %d)", state.previousCount));
                }
                sb.append("\n");
                sb.append(String.format("  Trend: %s\n", state.trend));
                sb.append(String.format("  Status: %s", verdict));
                if (state.suppressed) {
                    sb.append(" [ATL message suppressed]");
                }
                sb.append("\n");
                sb.append(String.format("  Age: %d seconds\n", state.getAgeSeconds(System.currentTimeMillis())));
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }
}
