package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamically analyses leaks using raw ATL data and installed mod information.
 * Performs real-time analysis of the modpack to identify root causes.
 */
public class LeakAnalyser {
    
    /**
     * Analyses a leak signature and returns a detailed analysis of what's causing it.
     */
    public static LeakAnalysis analyse(LeakSignature signature, LeakState state, MinecraftServer server) {
        LeakAnalysis analysis = new LeakAnalysis();
        analysis.signature = signature;
        analysis.state = state;
        
        // Get installed mods
        Set<String> installedMods = getInstalledMods();
        analysis.installedMods = installedMods;

        analysis.memoryData = SparkDataProvider.getMemoryData();
        analysis.heapPressureFastTrack = Config.heapPressureEasesPrescription
            && analysis.memoryData.heapUsagePercent >= Config.heapPressurePercentForPrescription
            && state.trend == LeakState.Trend.GROWING;
        
        // Analyse the leak class
        analyseLeakClass(signature, analysis);
        
        // Find mods that might be related to this leak
        findRelatedMods(signature, installedMods, analysis);
        
        // Detect patterns and correlations
        detectPatterns(signature, state, analysis);

        applyVelocityContext(signature, state, analysis);
        applyPackCorrelations(signature, state, analysis);
        applyTimeSeriesContext(state, analysis);
        applyServerPerformanceContext(state, analysis);
        
        // Calculate confidence based on data quality
        calculateConfidence(state, analysis);
        
        // Heap confidence tweak and stable+low-memory severity (uses analysis.memoryData)
        SparkDataProvider.enhanceAnalysisWithMemory(analysis, state);
        
        // Generate intelligent explanation (with confidence-aware recommendations)
        generateExplanation(analysis);
        
        return analysis;
    }
    
    /**
     * Gets the list of installed mod IDs.
     */
    private static Set<String> getInstalledMods() {
        return ModList.get().getMods().stream()
                .map(modContainer -> modContainer.getModId().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
    
    /**
     * Analyses the leak class to understand what type of object is leaking.
     */
    private static void analyseLeakClass(LeakSignature signature, LeakAnalysis analysis) {
        String className = signature.targetClass.toLowerCase();
        String type = signature.type != null ? signature.type.toLowerCase() : "";
        
        // Determine object category
        if (className.contains("player") || className.contains("serverplayer") || className.contains("localplayer")) {
            analysis.objectCategory = "Player Entity";
            analysis.objectDescription = "Player-related objects. These should match the number of online players.";
            analysis.expectedBehavior = "Should be 1-2 for single player, matches online player count for multiplayer.";
        } else if (className.contains("chunk") || type.contains("chunk")) {
            analysis.objectCategory = "Chunk";
            analysis.objectDescription = "World chunk data. Chunks are loaded when players are nearby or when claimed.";
            analysis.expectedBehavior = "Baseline: 32-64 chunks. Growing chunks indicate a leak.";
        } else if (className.contains("entity")) {
            analysis.objectCategory = "Entity";
            analysis.objectDescription = "Game entity objects. Entities should be cleaned up when removed.";
            analysis.expectedBehavior = "Should decrease when entities despawn. Growing count indicates entities not being removed.";
        } else if (className.contains("tile") || className.contains("blockentity")) {
            analysis.objectCategory = "Block Entity";
            analysis.objectDescription = "Tile entity objects attached to blocks.";
            analysis.expectedBehavior = "Should match placed blocks. Growing indicates blocks not being cleaned up.";
        } else {
            analysis.objectCategory = "Unknown";
            analysis.objectDescription = "Object type: " + signature.targetClass;
            analysis.expectedBehavior = "Requires investigation.";
        }
    }
    
    /**
     * Finds mods that might be related to this leak based on class name and installed mods.
     */
    private static void findRelatedMods(LeakSignature signature, Set<String> installedMods, LeakAnalysis analysis) {
        String className = signature.targetClass.toLowerCase();
        String modName = signature.modName != null ? signature.modName.toLowerCase() : "";
        
        // Direct mod match
        if (modName != null && !modName.isEmpty() && !modName.equals("minecraft")) {
            analysis.primaryMod = modName;
        }
        
        // Find mods that might interact with this class
        List<String> relatedMods = new ArrayList<>();
        
        // Check for mods that commonly interact with this object type
        if (signature.isPlayerRelated()) {
            // Check if Death Detangler is active and managing leaks
            boolean deathDetanglerActive = DeathDetanglerIntegration.isDeathDetanglerActive();
            if (analysis.state != null && analysis.state.deathDetanglerActive) {
                deathDetanglerActive = true;
            }
            
            // Look for death/resurrection mods
            for (String modId : installedMods) {
                // Exclude Death Detangler from conflict list if it's active and managing leaks
                // Death Detangler is a solution, not a cause of conflicts
                if (modId.equals("death_detangler")) {
                    // Only include Death Detangler if it's NOT active (meaning it's not helping)
                    if (!deathDetanglerActive) {
                        relatedMods.add(modId);
                    }
                    // If Death Detangler is active, skip it - it's managing the leaks, not causing them
                    continue;
                }
                
                if (modId.contains("revival") || modId.contains("corpse") || 
                    (modId.contains("death") && !modId.equals("death_detangler")) || 
                    modId.contains("respawn") || modId.contains("grave") || modId.contains("tombstone")) {
                    relatedMods.add(modId);
                }
            }
        }
        
        if (className.contains("chunk")) {
            for (String modId : installedMods) {
                if (modId.contains("chunk") || modId.contains("claim") || modId.contains("protect")) {
                    relatedMods.add(modId);
                }
            }
        }

        if (className.contains("entity") || className.contains("mob") || className.contains("path")) {
            for (String modId : installedMods) {
                if (modId.contains("mob") || modId.contains("spawn") || modId.contains("entity")
                    || modId.contains("performance") || modId.contains("optimization")) {
                    relatedMods.add(modId);
                }
            }
        }

        relatedMods = new ArrayList<>(new LinkedHashSet<>(relatedMods));
        analysis.relatedMods = relatedMods;
        
        // Detect potential conflicts
        // Don't report conflicts if Death Detangler is active and managing leaks
        if (relatedMods.size() > 1 && signature.isPlayerRelated()) {
            boolean deathDetanglerActive = DeathDetanglerIntegration.isDeathDetanglerActive();
            if (analysis.state != null && analysis.state.deathDetanglerActive) {
                deathDetanglerActive = true;
            }
            
            // If Death Detangler is active and managing leaks, don't report conflicts
            // It's solving the problem, not causing it
            if (!deathDetanglerActive || analysis.state.currentCount > 3) {
                analysis.potentialConflict = "Multiple mods detected that handle " + analysis.objectCategory.toLowerCase() + 
                        " objects: " + String.join(", ", relatedMods) + 
                        ". These mods may be conflicting.";
            }
        }
    }
    
    /**
     * Detects patterns in the leak data.
     */
    private static void detectPatterns(LeakSignature signature, LeakState state, LeakAnalysis analysis) {
        // Analyse trend
        // IMPORTANT: Don't set HIGH severity on first detection - need multiple observations to confirm
        // A single GROWING trend might be noise, but multiple GROWING observations = real leak
        if (state.trend == LeakState.Trend.GROWING) {
            analysis.pattern = "GROWING_LEAK";
            // Only set HIGH severity if we have multiple observations confirming the growth
            // First detection should be MEDIUM or LOW until we're confident
            if (state.reportCount >= 2 && state.growthEvents >= 2) {
                // Multiple growth events = confirmed growing leak
                analysis.severity = "HIGH";
                analysis.recommendation = "This leak is actively growing. Each update increases the count, indicating objects are not being cleaned up.";
            } else if (state.reportCount >= 2) {
                // Growing but only 2 observations - suspicious but not confirmed yet
                analysis.severity = "MEDIUM";
                analysis.recommendation = "Leak appears to be growing. Monitoring for confirmation.";
            } else {
                // First detection - don't jump to conclusions
                analysis.severity = "LOW";
                analysis.recommendation = "Initial detection shows growth. Gathering more data to confirm.";
            }
        } else if (state.trend == LeakState.Trend.STABLE) {
            analysis.pattern = "STABLE_RETENTION";
            if (state.currentCount > 2 && signature.isPlayerRelated()) {
                analysis.severity = "MEDIUM";
                analysis.recommendation = "Stable but elevated count. This may indicate leftover objects from a previous event.";
            } else {
                analysis.severity = "LOW";
                analysis.recommendation = "Count is stable. Monitor for changes.";
            }
        } else {
            analysis.pattern = "SHRINKING";
            analysis.severity = "LOW";
            analysis.recommendation = "Leak is resolving itself. Objects are being cleaned up.";
        }
        
        // Check if count is abnormal
        if (signature.isPlayerRelated()) {
            if (state.currentCount > 2) {
                analysis.abnormalCount = true;
                analysis.abnormalReason = "Player count is higher than expected. Normal: 1-2 players. Current: " + state.currentCount;
            }
        }
        
        // Check age
        long age = state.getAgeSeconds(System.currentTimeMillis());
        if (age > 300 && state.trend == LeakState.Trend.STABLE && state.currentCount > 1) {
            analysis.persistentLeak = true;
            analysis.persistentReason = "This leak has persisted for " + (age / 60) + " minutes without resolving.";
        }
        
        // Track pattern history for better analysis over time
        if (state.reportCount > Config.patternMatureObservations) {
            if (state.growthEvents > state.reportCount * 0.7) {
                analysis.pattern = "CONSISTENTLY_GROWING";
                analysis.severity = "HIGH";
            } else if (state.stableEvents > state.reportCount * 0.8) {
                analysis.pattern = "CONSISTENTLY_STABLE";
            }
        }
    }

    private static void applyVelocityContext(LeakSignature signature, LeakState state, LeakAnalysis analysis) {
        long ageSec = Math.max(1L, state.getAgeSeconds(System.currentTimeMillis()));
        int netVsMin = state.currentCount - state.minCount;
        if (netVsMin > 0) {
            double perMin = netVsMin * 60.0 / ageSec;
            analysis.velocityNote = String.format(
                "Retention velocity: +%d vs lowest seen (~%.2f objects/min over %ds). Peak count: %d.",
                netVsMin, perMin, ageSec, state.maxCount);
        } else if (state.trend == LeakState.Trend.GROWING) {
            analysis.velocityNote = String.format(
                "Count is rising vs previous sample; peak %d, min %d — watch for sustained growth.",
                state.maxCount, state.minCount);
        }
    }

    private static void applyPackCorrelations(LeakSignature signature, LeakState state, LeakAnalysis analysis) {
        if (signature.modName == null || signature.modName.isEmpty() || "minecraft".equalsIgnoreCase(signature.modName)) {
            return;
        }
        int sameModRows = 0;
        for (LeakSignature sig : LeakStateTracker.getInstance().getAllStates().keySet()) {
            if (sig.modName != null && sig.modName.equalsIgnoreCase(signature.modName) && !sig.equals(signature)) {
                sameModRows++;
            }
        }
        if (sameModRows > 0) {
            analysis.packContextNote = "Pack correlation: mod '" + signature.modName + "' appears in "
                + (sameModRows + 1) + " separate leak row(s). Worth prioritising updates or narrow-down testing for this mod.";
        }
    }

    private static void applyTimeSeriesContext(LeakState state, LeakAnalysis analysis) {
        List<LeakSample> samples = state.copySamples();
        analysis.timeSeriesAcceleration = TimeSeriesDiagnostics.accelerationSummary(samples);
        analysis.timeSeriesCountSpark = TimeSeriesDiagnostics.countSparkline(samples);
        analysis.timeSeriesHeapSpark = TimeSeriesDiagnostics.heapSparkline(samples);
    }

    private static void applyServerPerformanceContext(LeakState state, LeakAnalysis analysis) {
        double tps = SparkApiReflection.pollTps5sOrNaN();
        if (Double.isNaN(tps)) {
            return;
        }
        if (state.trend == LeakState.Trend.GROWING && tps < Config.lowTpsThreshold) {
            analysis.serverPerfNote = String.format(
                "Spark TPS (5s avg) is %.1f while this leak is growing — server may be struggling; use Spark profiler/world tab.",
                tps);
            if ("MEDIUM".equals(analysis.severity)) {
                analysis.severity = "HIGH";
            }
        }
    }
    
    /**
     * Calculates confidence level and determines if we should suggest specific fixes.
     * Acts like a doctor - only prescribes when confident in diagnosis.
     */
    private static void calculateConfidence(LeakState state, LeakAnalysis analysis) {
        double baseConfidence = state.getConfidence();
        double patternConsistency = state.getPatternConsistency();
        
        // Combine confidence factors
        analysis.confidence = (baseConfidence * 0.6) + (patternConsistency * 0.4);
        
        boolean hasClearPattern = analysis.pattern.equals("GROWING_LEAK") ||
            analysis.pattern.equals("CONSISTENTLY_GROWING") ||
            (analysis.potentialConflict != null && state.reportCount >= Config.prescriptionConflictMinObservations);

        int minObs = Config.prescriptionMinObservations;
        if (analysis.heapPressureFastTrack && Config.heapPressureEasesPrescription) {
            minObs = Math.max(2, minObs - 1);
        }

        analysis.hasSpecificFix = analysis.confidence >= Config.prescriptionMinConfidence
            && hasClearPattern
            && state.reportCount >= minObs;

        List<String> confidenceHints = new ArrayList<>();
        if (state.reportCount < Config.prescriptionMinObservations) {
            confidenceHints.add("Run /atl force_refresh "
                + (Config.prescriptionMinObservations - state.reportCount) + " more time(s) toward prescription threshold");
        }
        if (state.reportCount < 10) {
            confidenceHints.add("More observations will improve confidence (currently " + state.reportCount + " observations)");
        }
        if (state.getPatternConsistency() < 0.7) {
            confidenceHints.add("Pattern is inconsistent - wait for more consistent data");
        }
        
        // Store confidence hints separately (don't add to uncertainty note)
        analysis.confidenceHints = confidenceHints;
        
        if (analysis.confidence < 0.5) {
            analysis.uncertaintyNote = "Analysis confidence is low - monitoring for more data before making recommendations.";
        } else if (analysis.confidence < Config.prescriptionMinConfidence) {
            analysis.uncertaintyNote = "Analysis confidence is moderate - gathering more data to confirm patterns.";
        }
        
        // Generate specific fix only if confident
        if (analysis.hasSpecificFix) {
            generateSpecificFix(analysis, state);
        } else {
            analysis.specificFix = null;
        }
    }
    
    /**
     * Generates a specific fix recommendation when we're confident enough.
     * This is the "prescription" - only given when diagnosis is clear.
     */
    private static void generateSpecificFix(LeakAnalysis analysis, LeakState state) {
        StringBuilder fix = new StringBuilder();
        
        if (analysis.pattern.equals("GROWING_LEAK") || analysis.pattern.equals("CONSISTENTLY_GROWING")) {
            if (analysis.signature.isPlayerRelated() && analysis.relatedMods.size() > 1) {
                // High confidence: Player clone leak from mod conflict
                fix.append("PRESCRIPTION (High Confidence):\n");
                fix.append("This is a confirmed player clone leak caused by conflicting mods.\n");
                fix.append("Recommended Action:\n");
                fix.append("1. Disable one of these mods: ").append(String.join(", ", analysis.relatedMods)).append("\n");
                fix.append("2. OR check for compatibility patches between these mods\n");
                fix.append("3. After disabling, restart server and monitor with /ctl status\n");
                fix.append("Confidence: ").append(String.format("%.0f", analysis.confidence * 100)).append("% based on ").append(state.reportCount).append(" observations");
            } else if (analysis.primaryMod != null && !analysis.primaryMod.equals("minecraft")) {
                fix.append("PRESCRIPTION (High Confidence):\n");
                fix.append("The mod '").append(analysis.primaryMod).append("' is strongly associated with growing ")
                    .append(analysis.objectCategory.toLowerCase()).append(" retention.\n");
                fix.append("Recommended Action:\n");
                fix.append("1. Update '").append(analysis.primaryMod).append("' to the latest version\n");
                if ("Chunk".equals(analysis.objectCategory)) {
                    fix.append("2. Review chunk loaders, claims, maps, and dimension mods interacting with '")
                        .append(analysis.primaryMod).append("'\n");
                    fix.append("3. CTL thresholds: baseline ").append(Config.baselineChunkThreshold)
                        .append(", escalation ").append(Config.escalationChunkThreshold).append("\n");
                    fix.append("4. If issue persists, report to the mod author with this leak row\n");
                } else {
                    fix.append("2. Check the mod's configuration for cleanup/despawn settings\n");
                    fix.append("3. If issue persists, report to the mod author with this leak data\n");
                }
                fix.append("Confidence: ").append(String.format("%.0f", analysis.confidence * 100)).append("% based on ")
                    .append(state.reportCount).append(" observations");
            } else {
                // Medium confidence: Growing leak but unclear cause
                fix.append("PRESCRIPTION (Moderate Confidence):\n");
                fix.append("A growing leak has been confirmed, but the exact cause needs more investigation.\n");
                fix.append("Recommended Action:\n");
                fix.append("1. Monitor with /ctl explain to gather more data\n");
                fix.append("2. Check server logs for related errors from mods\n");
                fix.append("3. Consider temporarily disabling recently added mods\n");
                fix.append("Confidence: ").append(String.format("%.0f", analysis.confidence * 100)).append("% - more data needed");
            }
        } else if (analysis.persistentLeak && state.reportCount >= Config.prescriptionPersistentMinObservations) {
            fix.append("PRESCRIPTION (High Confidence):\n");
            fix.append("This leak has persisted for ").append(state.getAgeSeconds(System.currentTimeMillis()) / 60).append(" minutes across ").append(state.reportCount).append(" checks.\n");
            fix.append("Recommended Action:\n");
            fix.append("1. This is a confirmed persistent leak requiring attention\n");
            fix.append("2. Check mod configurations related to: ").append(analysis.objectCategory).append("\n");
            fix.append("3. Consider server restart if leak count is high\n");
            fix.append("Confidence: ").append(String.format("%.0f", analysis.confidence * 100)).append("% - pattern confirmed over time");
        }

        analysis.specificFix = fix.length() > 0 ? fix.toString() : null;
    }
    
    /**
     * Generates an intelligent explanation based on all the analysis data.
     */
    private static void generateExplanation(LeakAnalysis analysis) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("Analysis of ").append(analysis.signature.targetClass).append(" leak:\n");
        explanation.append("─────────────────────────────────\n\n");
        
        // Object description
        explanation.append("Object Type: ").append(analysis.objectCategory).append("\n");
        explanation.append("Description: ").append(analysis.objectDescription).append("\n");
        explanation.append("Expected: ").append(analysis.expectedBehavior).append("\n\n");
        
        // Current state
        explanation.append("Current State:\n");
        explanation.append("- Count: ").append(analysis.state.currentCount);
        if (analysis.state.previousCount > 0) {
            explanation.append(" (was ").append(analysis.state.previousCount).append(")");
        }
        explanation.append("\n");
        explanation.append("- Trend: ").append(analysis.state.trend).append("\n");
        explanation.append("- Pattern: ").append(analysis.pattern).append("\n");
        explanation.append("- Severity: ").append(analysis.severity).append("\n\n");
        
        // Mod information
        if (analysis.primaryMod != null && !analysis.primaryMod.equals("minecraft")) {
            explanation.append("Primary Mod: ").append(analysis.primaryMod).append("\n");
        }
        
        if (!analysis.relatedMods.isEmpty()) {
            explanation.append("Related Mods: ").append(String.join(", ", analysis.relatedMods)).append("\n");
        }
        
        if (analysis.potentialConflict != null) {
            explanation.append("\n⚠️  ").append(analysis.potentialConflict).append("\n");
        }

        if (analysis.velocityNote != null) {
            explanation.append("\n").append(analysis.velocityNote).append("\n");
        }
        if (analysis.timeSeriesAcceleration != null && !analysis.timeSeriesAcceleration.isEmpty()) {
            explanation.append("\n").append(analysis.timeSeriesAcceleration).append("\n");
        }
        if (analysis.timeSeriesCountSpark != null && !analysis.timeSeriesCountSpark.isEmpty()) {
            explanation.append(analysis.timeSeriesCountSpark).append("\n");
        }
        if (analysis.timeSeriesHeapSpark != null && !analysis.timeSeriesHeapSpark.isEmpty()) {
            explanation.append(analysis.timeSeriesHeapSpark).append("\n");
        }
        if (analysis.packContextNote != null) {
            explanation.append("\n").append(analysis.packContextNote).append("\n");
        }
        if (analysis.serverPerfNote != null) {
            explanation.append("\n").append(analysis.serverPerfNote).append("\n");
        }
        
        // Abnormal conditions
        if (analysis.abnormalCount) {
            explanation.append("\n⚠️  ").append(analysis.abnormalReason).append("\n");
        }
        
        if (analysis.persistentLeak) {
            explanation.append("\n⚠️  ").append(analysis.persistentReason).append("\n");
        }
        
        // Death Detangler status if active (for player leaks)
        if (analysis.signature.isPlayerRelated() && DeathDetanglerIntegration.isDeathDetanglerActive()) {
            int cleanups = DeathDetanglerIntegration.getTotalCleanups();
            if (analysis.state.deathDetanglerActive) {
                explanation.append("\n✓ Death Detangler is active and has cleaned up ").append(cleanups).append(" player(s).\n");
                explanation.append("This suggests Death Detangler is managing player clone cleanup.\n");
            } else {
                explanation.append("\nℹ Death Detangler is installed (has cleaned ").append(cleanups).append(" player(s)).\n");
            }
        }
        
        // Recommendation (always shown, but may be general)
        explanation.append("\nRecommendation:\n");
        explanation.append(analysis.recommendation).append("\n");
        
        // Show uncertainty if confidence is low (but don't duplicate confidence info)
        if (analysis.uncertaintyNote != null) {
            explanation.append("\n⚠️  ").append(analysis.uncertaintyNote).append("\n");
            explanation.append("CTL is gathering more data to improve analysis. Check back after more observations.\n");
        }
        
        // Always show confidence separately (not in uncertainty note to avoid duplication)
        explanation.append("\nConfidence: ").append(String.format("%.0f", analysis.confidence * 100)).append("% (").append(analysis.state.reportCount).append(" observations)\n");
        
        // Specific fix (prescription) - only shown when confident
        if (analysis.hasSpecificFix && analysis.specificFix != null) {
            explanation.append("\n").append(analysis.specificFix).append("\n");
        } else if (analysis.confidence < Config.prescriptionMinConfidence) {
            explanation.append("\nNote: CTL needs more data before suggesting specific fixes.\n");
            explanation.append("Continue monitoring - analysis will improve as more patterns emerge.\n");
        }
        
        analysis.explanation = explanation.toString();
    }
    
    /**
     * Result of leak analysis.
     */
    public static class LeakAnalysis {
        public LeakSignature signature;
        public LeakState state;
        public Set<String> installedMods;
        public String objectCategory;
        public String objectDescription;
        public String expectedBehavior;
        public String primaryMod;
        public List<String> relatedMods = new ArrayList<>();
        public String potentialConflict;
        public String pattern;
        public String severity;
        public String recommendation;
        public boolean abnormalCount;
        public String abnormalReason;
        public boolean persistentLeak;
        public String persistentReason;
        public String explanation;
        public double confidence;  // 0.0 to 1.0 - how confident we are in the analysis
        public boolean hasSpecificFix;  // True if we're confident enough to suggest a specific fix
        public String specificFix;  // The actual fix recommendation (only if confident)
        public String uncertaintyNote;  // Note about uncertainty if confidence is low
        public List<String> confidenceHints;  // Hints for increasing confidence
        public SparkDataProvider.MemoryData memoryData;  // Memory usage data if available
        /** High heap + growing: lowers observation bar for prescriptions when enabled in config */
        public boolean heapPressureFastTrack;
        public String velocityNote;
        public String packContextNote;
        public String serverPerfNote;
        public String timeSeriesAcceleration;
        public String timeSeriesCountSpark;
        public String timeSeriesHeapSpark;
    }
}
