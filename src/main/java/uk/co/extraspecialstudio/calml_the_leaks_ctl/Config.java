package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Common config for Calm The Leaks ({@code config/calml_the_leaks_ctl-common.toml}).
 * <p>
 * Section banners and push/pop match the RadioTowers / Dead Letters style so the
 * in-game config screen and toml stay easy to scan. Leaf key names are unchanged
 * for existing worlds and modpacks.
 */
@Mod.EventBusSubscriber(modid = CalmlTheLeaks.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // --- Detection ---
    private static final ForgeConfigSpec.IntValue BASELINE_CHUNK_THRESHOLD;
    private static final ForgeConfigSpec.IntValue STARTUP_GRACE_PERIOD_SECONDS;
    private static final ForgeConfigSpec.IntValue TREND_WINDOW_MINUTES;
    private static final ForgeConfigSpec.IntValue ESCALATION_CHUNK_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue LOW_TPS_THRESHOLD;
    private static final ForgeConfigSpec.IntValue TIME_SERIES_MAX_SAMPLES;

    // --- UI ---
    private static final ForgeConfigSpec.BooleanValue SUPPRESS_ATL_LEAK_LINES;
    private static final ForgeConfigSpec.BooleanValue PREFER_DIAGNOSTICS_GUI;
    private static final ForgeConfigSpec.BooleanValue CTL_CHAT_ACK;
    private static final ForgeConfigSpec.BooleanValue ALLOW_WORLD_LEAK_MEMORY;

    // --- Prescription ---
    private static final ForgeConfigSpec.IntValue PRESCRIPTION_MIN_OBSERVATIONS;
    private static final ForgeConfigSpec.DoubleValue PRESCRIPTION_MIN_CONFIDENCE;
    private static final ForgeConfigSpec.IntValue PRESCRIPTION_CONFLICT_MIN_OBSERVATIONS;
    private static final ForgeConfigSpec.IntValue PRESCRIPTION_PERSISTENT_MIN_OBSERVATIONS;
    private static final ForgeConfigSpec.IntValue PATTERN_MATURE_OBSERVATIONS;
    private static final ForgeConfigSpec.DoubleValue HEAP_PRESSURE_CONFIDENCE_BOOST;
    private static final ForgeConfigSpec.BooleanValue HEAP_PRESSURE_EASES_PRESCRIPTION;
    private static final ForgeConfigSpec.DoubleValue HEAP_PRESSURE_PRESCRIPTION_PERCENT;

    // --- Debug ---
    private static final ForgeConfigSpec.BooleanValue LOG_DEBUG_DETAILS;
    private static final ForgeConfigSpec.BooleanValue AUTO_SPARK_PROFILING;

    static {
        // ========== DETECTION ==========
        BUILDER.comment(
                "============================================================",
                "LEAK DETECTION",
                "When CTL treats chunk counts as noise vs a real leak,",
                "how long after startup to stay quiet, and how trends/TPS",
                "feed severity. Tune these first on busy servers.",
                "============================================================"
        ).push("detection");

        BASELINE_CHUNK_THRESHOLD = BUILDER
                .comment(
                        "----- START HERE: CHUNK THRESHOLDS -----",
                        "Chunk count below which retained chunks are treated as baseline",
                        "(normal cache), not a leak. Default: 48."
                )
                .defineInRange("baseline_chunk_threshold", 48, 0, Integer.MAX_VALUE);
        ESCALATION_CHUNK_THRESHOLD = BUILDER
                .comment(
                        "Chunk count above which a leak is treated as confirmed / escalated.",
                        "Default: 96."
                )
                .defineInRange("escalation_chunk_threshold", 96, 0, Integer.MAX_VALUE);
        STARTUP_GRACE_PERIOD_SECONDS = BUILDER
                .comment(
                        "Seconds after server start during which leak warnings are suppressed",
                        "(world load and caches settle). Default: 600 (10 minutes)."
                )
                .defineInRange("startup_grace_period_seconds", 600, 0, Integer.MAX_VALUE);
        TREND_WINDOW_MINUTES = BUILDER
                .comment(
                        "Minutes of chunk-count history used to decide GROWING / STABLE trends.",
                        "Default: 15."
                )
                .defineInRange("trend_window_minutes", 15, 1, 60);
        LOW_TPS_THRESHOLD = BUILDER
                .comment(
                        "Spark TPS (5s) below this, together with a GROWING leak, bumps severity.",
                        "Default: 17.5."
                )
                .defineInRange("low_tps_threshold", 17.5, 1.0, 20.0);
        TIME_SERIES_MAX_SAMPLES = BUILDER
                .comment(
                        "How many (count, heap %) samples to keep per leak for sparklines",
                        "and acceleration. Default: 48."
                )
                .defineInRange("time_series_max_samples", 48, 8, 256);
        BUILDER.pop();

        // ========== UI ==========
        BUILDER.comment(
                "============================================================",
                "UI AND CHAT",
                "Whether ATL lines spam chat/log, whether /ctl opens the panel,",
                "and whether ops may enable world leak memory from the panel.",
                "============================================================"
        ).push("ui");
        SUPPRESS_ATL_LEAK_LINES = BUILDER
                .comment(
                        "true: parsed AllTheLeaks leak lines are not printed to log/chat;",
                        "use /ctl panel instead. Default: true."
                )
                .define("suppress_atl_leak_lines", true);
        PREFER_DIAGNOSTICS_GUI = BUILDER
                .comment(
                        "true: /ctl status|leaks|explain opens the diagnostics GUI for players",
                        "instead of dumping to chat. Default: true."
                )
                .define("prefer_diagnostics_gui", true);
        CTL_CHAT_ACK = BUILDER
                .comment(
                        "When opening the CTL panel from a command, send a short chat",
                        "acknowledgement. Default: false."
                )
                .define("ctl_chat_ack", false);
        ALLOW_WORLD_LEAK_MEMORY = BUILDER
                .comment(
                        "Lets ops (permission 2+) use the CTL panel to turn world leak memory",
                        "on or off. Nothing is written under the world until someone presses",
                        "\"Save leak history in this world\" in the panel.",
                        "Set false only to hide that option and forbid persistence on this server.",
                        "Default: true."
                )
                .define("allow_world_leak_memory", true);
        BUILDER.pop();

        // ========== PRESCRIPTION ==========
        BUILDER.comment(
                "============================================================",
                "PRESCRIPTION GATES",
                "How many ATL refreshes and how much confidence CTL needs before",
                "it prints a prescription block. Heap pressure can ease those gates.",
                "============================================================"
        ).push("prescription");
        PRESCRIPTION_MIN_OBSERVATIONS = BUILDER
                .comment(
                        "Minimum ATL refresh observations before CTL may emit a prescription.",
                        "Default: 3."
                )
                .defineInRange("prescription_min_observations", 3, 1, 50);
        PRESCRIPTION_MIN_CONFIDENCE = BUILDER
                .comment(
                        "Minimum analysis confidence (0–1) required for a prescription.",
                        "Default: 0.62."
                )
                .defineInRange("prescription_min_confidence", 0.62, 0.2, 0.99);
        PRESCRIPTION_CONFLICT_MIN_OBSERVATIONS = BUILDER
                .comment(
                        "Observations needed to prescribe on a mod-conflict pattern alone.",
                        "Default: 3."
                )
                .defineInRange("prescription_conflict_min_observations", 3, 1, 50);
        PRESCRIPTION_PERSISTENT_MIN_OBSERVATIONS = BUILDER
                .comment(
                        "Observations needed for the persistent-leak prescription branch.",
                        "Default: 6."
                )
                .defineInRange("prescription_persistent_min_observations", 6, 2, 100);
        PATTERN_MATURE_OBSERVATIONS = BUILDER
                .comment(
                        "Observations before CONSISTENTLY_* pattern labels tighten.",
                        "Default: 5."
                )
                .defineInRange("pattern_mature_observations", 5, 2, 100);
        HEAP_PRESSURE_CONFIDENCE_BOOST = BUILDER
                .comment(
                        "Extra confidence when heap is above ~80% and trend is GROWING.",
                        "Default: 0.12."
                )
                .defineInRange("heap_pressure_confidence_boost", 0.12, 0.0, 0.5);
        HEAP_PRESSURE_EASES_PRESCRIPTION = BUILDER
                .comment(
                        "true: high heap + GROWING reduces the prescription observation",
                        "requirement by 1. Default: true."
                )
                .define("heap_pressure_eases_prescription", true);
        HEAP_PRESSURE_PRESCRIPTION_PERCENT = BUILDER
                .comment(
                        "Heap % threshold for the prescription fast-track when trend is GROWING.",
                        "Default: 85."
                )
                .defineInRange("heap_pressure_prescription_percent", 85.0, 50.0, 100.0);
        BUILDER.pop();

        // ========== DEBUG ==========
        BUILDER.comment(
                "============================================================",
                "DEBUG",
                "Leave these off for normal play unless you are chasing a leak",
                "or want automatic Spark profiles when CTL escalates.",
                "============================================================"
        ).push("debug");
        LOG_DEBUG_DETAILS = BUILDER
                .comment("Log detailed CTL debug information to the server log. Default: false.")
                .define("log_debug_details", false);
        AUTO_SPARK_PROFILING = BUILDER
                .comment(
                        "true: automatically trigger Spark profiling when leaks are detected.",
                        "false: skip auto profiles (and related chat). Default: true."
                )
                .define("auto_spark_profiling", true);
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int baselineChunkThreshold;
    public static int startupGracePeriodSeconds;
    public static int trendWindowMinutes;
    public static int escalationChunkThreshold;
    public static boolean logDebugDetails;
    public static boolean autoSparkProfiling;
    public static boolean suppressAtlLeakLines;
    public static boolean preferDiagnosticsGui;
    public static boolean ctlChatAck;
    public static int prescriptionMinObservations;
    public static double prescriptionMinConfidence;
    public static int prescriptionConflictMinObservations;
    public static int prescriptionPersistentMinObservations;
    public static int patternMatureObservations;
    public static double heapPressureConfidenceBoost;
    public static boolean heapPressureEasesPrescription;
    public static double heapPressurePercentForPrescription;
    public static double lowTpsThreshold;
    public static int timeSeriesMaxSamples;
    public static boolean allowWorldLeakMemory;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        baselineChunkThreshold = BASELINE_CHUNK_THRESHOLD.get();
        startupGracePeriodSeconds = STARTUP_GRACE_PERIOD_SECONDS.get();
        trendWindowMinutes = TREND_WINDOW_MINUTES.get();
        escalationChunkThreshold = ESCALATION_CHUNK_THRESHOLD.get();
        logDebugDetails = LOG_DEBUG_DETAILS.get();
        autoSparkProfiling = AUTO_SPARK_PROFILING.get();
        suppressAtlLeakLines = SUPPRESS_ATL_LEAK_LINES.get();
        preferDiagnosticsGui = PREFER_DIAGNOSTICS_GUI.get();
        ctlChatAck = CTL_CHAT_ACK.get();
        prescriptionMinObservations = PRESCRIPTION_MIN_OBSERVATIONS.get();
        prescriptionMinConfidence = PRESCRIPTION_MIN_CONFIDENCE.get();
        prescriptionConflictMinObservations = PRESCRIPTION_CONFLICT_MIN_OBSERVATIONS.get();
        prescriptionPersistentMinObservations = PRESCRIPTION_PERSISTENT_MIN_OBSERVATIONS.get();
        patternMatureObservations = PATTERN_MATURE_OBSERVATIONS.get();
        heapPressureConfidenceBoost = HEAP_PRESSURE_CONFIDENCE_BOOST.get();
        heapPressureEasesPrescription = HEAP_PRESSURE_EASES_PRESCRIPTION.get();
        heapPressurePercentForPrescription = HEAP_PRESSURE_PRESCRIPTION_PERCENT.get();
        lowTpsThreshold = LOW_TPS_THRESHOLD.get();
        timeSeriesMaxSamples = TIME_SERIES_MAX_SAMPLES.get();
        allowWorldLeakMemory = ALLOW_WORLD_LEAK_MEMORY.get();
    }
}
