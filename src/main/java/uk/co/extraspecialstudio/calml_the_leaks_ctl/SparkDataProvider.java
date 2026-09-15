package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import net.neoforged.fml.ModList;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * JVM heap metrics via {@link MemoryMXBean}. Spark mod presence is detected separately for labelling;
 * CTL does not parse Spark heap reports — see {@link SparkRuntimeProbe} and {@link SparkProfilerManager}.
 */
public class SparkDataProvider {
    private static Boolean sparkAvailable = null;
    private static MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    /**
     * Checks if Spark mod is installed.
     */
    public static boolean isSparkAvailable() {
        if (sparkAvailable == null) {
            sparkAvailable = ModList.get().isLoaded("spark");
        }
        return sparkAvailable;
    }
    
    /**
     * Gets memory usage data (works even without Spark, but enhanced if Spark is available).
     */
    public static MemoryData getMemoryData() {
        MemoryData data = new MemoryData();
        
        // Always available: JVM memory stats
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        data.heapUsed = heapUsage.getUsed();
        data.heapMax = heapUsage.getMax();
        data.heapCommitted = heapUsage.getCommitted();
        data.heapUsagePercent = (double) data.heapUsed / data.heapMax * 100.0;
        
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        data.nonHeapUsed = nonHeapUsage.getUsed();
        data.nonHeapMax = nonHeapUsage.getMax();
        
        return data;
    }
    
    /**
     * Correlates memory usage with leak data to enhance diagnosis.
     */
    public static void enhanceAnalysisWithMemory(LeakAnalyser.LeakAnalysis analysis, LeakState state) {
        MemoryData memory = analysis.memoryData != null ? analysis.memoryData : getMemoryData();
        analysis.memoryData = memory;

        if (memory.heapUsagePercent > 80 && state.trend == LeakState.Trend.GROWING) {
            analysis.confidence = Math.min(1.0, analysis.confidence + Config.heapPressureConfidenceBoost);
        }

        if (memory.heapUsagePercent < 50 && state.trend == LeakState.Trend.STABLE) {
            if (analysis.severity.equals("MEDIUM")) {
                analysis.severity = "LOW";
            }
        }
    }
    
    /**
     * Memory usage data.
     */
    public static class MemoryData {
        public long heapUsed;
        public long heapMax;
        public long heapCommitted;
        public double heapUsagePercent;
        public long nonHeapUsed;
        public long nonHeapMax;
        
        public String getHeapUsageString() {
            return String.format("%.1f%% (%s / %s)", 
                heapUsagePercent, 
                formatBytes(heapUsed), 
                formatBytes(heapMax));
        }
        
        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
