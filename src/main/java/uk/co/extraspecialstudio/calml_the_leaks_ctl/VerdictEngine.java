package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import net.minecraft.server.MinecraftServer;

/**
 * Decides whether a leak report is benign, suspicious, or confirmed.
 */
public class VerdictEngine {
    public enum Verdict {
        BASELINE_RETENTION,  // Normal, ignore
        TRANSITIONAL_NOISE,  // Startup noise, soft warning
        SUSPICIOUS_TREND,   // Growing, heads-up
        CONFIRMED_LEAK        // Real leak, escalate
    }

    public static Verdict evaluate(LeakSignature signature, LeakState state, MinecraftServer server) {
        long currentTime = System.currentTimeMillis();
        long age = state.getAgeSeconds(currentTime);
        PhaseManager.Phase phase = PhaseManager.getCurrentPhase();
        
        // Use currentCount from state (current leak count) instead of signature.count (initial count)
        int currentCount = state.currentCount;

        // Check if it's a confirmed leak
        if (state.confirmedLeak) {
            return Verdict.CONFIRMED_LEAK;
        }

        // Special handling for player-related leaks - these are often problematic
        if (signature.isPlayerRelated()) {
            // Check if Death Detangler is active and handling player cleanups
            boolean deathDetanglerActive = DeathDetanglerIntegration.isDeathDetanglerActive();
            if (state.deathDetanglerActive) {
                deathDetanglerActive = true;
            }
            
            // If Death Detangler is active, be more lenient with player leak reports
            // Death Detangler is actively cleaning up, so some leaks might be transient
            if (deathDetanglerActive) {
                // If Death Detangler has cleaned up recently and count is stable or shrinking, it's likely being handled
                if ((state.trend == LeakState.Trend.STABLE || state.trend == LeakState.Trend.SHRINKING) 
                        && currentCount <= 2) {
                    // Death Detangler is active and count is normal - likely being handled
                    return Verdict.BASELINE_RETENTION;
                }
                // If count is growing despite Death Detangler, it's still a leak but less urgent
                if (state.trend == LeakState.Trend.GROWING && currentCount <= 3) {
                    // Death Detangler is active but leak is still growing - suspicious but not critical yet
                    return Verdict.SUSPICIOUS_TREND;
                }
            }
            
            // Player leaks that persist beyond warmup are suspicious
            if (age > 120 && phase != PhaseManager.Phase.STARTUP && phase != PhaseManager.Phase.WARMUP) {
                // If player count is higher than expected (more than 1-2 for single player)
                // or if it's growing, it's likely a leak
                if (currentCount > 2 || state.trend == LeakState.Trend.GROWING) {
                    // Growing player count = player clone leak (common with Hardcore Revival + Corpse)
                    if (state.trend == LeakState.Trend.GROWING) {
                        // If Death Detangler is active, give it a chance before confirming leak
                        if (deathDetanglerActive && currentCount <= 3) {
                            // But if we have multiple growth events (confirmed pattern), it's still a leak
                            if (state.reportCount >= 2 && state.growthEvents >= 2) {
                                return Verdict.CONFIRMED_LEAK; // Multiple growth events = confirmed despite Death Detangler
                            }
                            return Verdict.SUSPICIOUS_TREND; // Suspicious but Death Detangler might handle it
                        }
                        // Multiple growth events = confirmed leak (even on first few checks)
                        if (state.reportCount >= 2 && state.growthEvents >= 2) {
                            return Verdict.CONFIRMED_LEAK; // Player clones growing = confirmed leak
                        }
                        // First growth event - suspicious but not confirmed yet
                        return Verdict.SUSPICIOUS_TREND;
                    }
                    // High player count that persists = suspicious
                    if (currentCount > 2 && age > 300) {
                        return Verdict.SUSPICIOUS_TREND;
                    }
                }
            }
            // During warmup, single player is normal
            if (currentCount == 1 && (phase == PhaseManager.Phase.STARTUP || phase == PhaseManager.Phase.WARMUP)) {
                return Verdict.TRANSITIONAL_NOISE;
            }
        }

        // Special handling for known false positives (claimed chunks, etc.)
        // If it's a claiming mod and the count is stable (not growing), it's baseline
        if (signature.isKnownFalsePositive()) {
            // If it's growing, it might be a real leak even from a claiming mod
            if (state.trend == LeakState.Trend.GROWING && currentCount > Config.baselineChunkThreshold * 2) {
                // Growing claimed chunks could indicate a leak in the claiming system
                return Verdict.SUSPICIOUS_TREND;
            }
            // Stable claimed chunks are normal - suppress them
            if (state.trend == LeakState.Trend.STABLE || state.trend == LeakState.Trend.SHRINKING) {
                return Verdict.BASELINE_RETENTION;
            }
        }

        // Check escalation threshold
        if (currentCount >= Config.escalationChunkThreshold) {
            return Verdict.CONFIRMED_LEAK;
        }

        // Baseline retention check
        if (currentCount <= Config.baselineChunkThreshold) {
            if (state.trend == LeakState.Trend.STABLE && age > 60) {
                // But if it's a problematic type and persists, escalate it
                if (signature.isKnownProblematicType() && age > 300) {
                    return Verdict.SUSPICIOUS_TREND;
                }
                return Verdict.BASELINE_RETENTION;
            }
        }

        // Transitional noise during startup/warmup - but only for non-problematic types
        if (PhaseManager.isInStartupGracePeriod() || phase == PhaseManager.Phase.STARTUP || phase == PhaseManager.Phase.WARMUP) {
            if (currentCount <= Config.baselineChunkThreshold * 2) {
                // If it's a problematic type, don't dismiss it as noise
                if (!signature.isKnownProblematicType()) {
                    return Verdict.TRANSITIONAL_NOISE;
                }
            }
        }

        // Suspicious trend: growing but not yet confirmed
        if (state.trend == LeakState.Trend.GROWING) {
            // Growing leaks are always suspicious, even if small
            if (currentCount > Config.baselineChunkThreshold) {
                return Verdict.SUSPICIOUS_TREND;
            }
            // Even small growing leaks are suspicious if they're problematic types
            if (signature.isKnownProblematicType() && age > 60) {
                return Verdict.SUSPICIOUS_TREND;
            }
        }

        // If it's stable and low, it's baseline (unless it's problematic and persists)
        if (state.trend == LeakState.Trend.STABLE && currentCount <= Config.baselineChunkThreshold) {
            // Problematic types that persist should be monitored
            if (signature.isKnownProblematicType() && age > 600) {
                return Verdict.SUSPICIOUS_TREND;
            }
            return Verdict.BASELINE_RETENTION;
        }

        // Default to transitional noise if we're not sure (but not for problematic types)
        if (PhaseManager.isInStartupGracePeriod() && !signature.isKnownProblematicType()) {
            return Verdict.TRANSITIONAL_NOISE;
        }

        // Otherwise, if it's growing, it's suspicious
        if (state.trend == LeakState.Trend.GROWING) {
            return Verdict.SUSPICIOUS_TREND;
        }

        return Verdict.BASELINE_RETENTION;
    }

    public static boolean shouldSuppress(Verdict verdict) {
        return verdict == Verdict.BASELINE_RETENTION || verdict == Verdict.TRANSITIONAL_NOISE;
    }
}
