package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Reads live TPS / MSPT / CPU / GC from the Spark mod via reflection so CTL does not hard-link Spark types.
 * Safe when Spark is absent: this class only references JDK and String class names.
 * (Optional: add {@code compileOnly me.lucko:spark-api} locally to mirror API in your IDE — do not reference those types from here.)
 */
public final class SparkApiReflection {
    private SparkApiReflection() {}

    /**
     * Appends human-readable lines from Spark's public API when the mod is loaded and initialised.
     */
    public static void appendLiveTelemetry(List<String> out) {
        if (!SparkDataProvider.isSparkAvailable()) {
            return;
        }
        try {
            Class<?> provider = Class.forName("me.lucko.spark.api.SparkProvider");
            Object spark = provider.getMethod("get").invoke(null);

            Object tpsStat = spark.getClass().getMethod("tps").invoke(spark);
            if (tpsStat != null) {
                Class<?> tpsWindow = Class.forName("me.lucko.spark.api.statistic.StatisticWindow$TicksPerSecond");
                Object sec5 = Enum.valueOf((Class<Enum>) tpsWindow, "SECONDS_5");
                double tps = (Double) tpsStat.getClass().getMethod("poll", tpsWindow).invoke(tpsStat, sec5);
                if (!Double.isNaN(tps) && tps > 0) {
                    out.add(String.format("Spark TPS (~5s avg): %.2f", tps));
                }
            }

            Object msptStat = spark.getClass().getMethod("mspt").invoke(spark);
            if (msptStat != null) {
                Class<?> msptWindow = Class.forName("me.lucko.spark.api.statistic.StatisticWindow$MillisPerTick");
                Object sec10 = Enum.valueOf((Class<Enum>) msptWindow, "SECONDS_10");
                Object val = msptStat.getClass().getMethod("poll", msptWindow).invoke(msptStat, sec10);
                if (val instanceof Double d && !Double.isNaN(d)) {
                    out.add(String.format("Spark MSPT (~10s): %.2f ms", d));
                } else if (val instanceof Number n) {
                    out.add(String.format("Spark MSPT (~10s): %s ms", n));
                } else if (val != null) {
                    out.add("Spark MSPT (~10s): " + val);
                }
            }

            Object cpuStat = spark.getClass().getMethod("cpuProcess").invoke(spark);
            if (cpuStat != null) {
                Class<?> cpuWindow = Class.forName("me.lucko.spark.api.statistic.StatisticWindow$CpuUsage");
                Object sec10 = Enum.valueOf((Class<Enum>) cpuWindow, "SECONDS_10");
                double cpu = (Double) cpuStat.getClass().getMethod("poll", cpuWindow).invoke(cpuStat, sec10);
                if (!Double.isNaN(cpu)) {
                    out.add(String.format("Spark process CPU (~10s): %.1f%%", cpu));
                }
            }

            Object gcMap = spark.getClass().getMethod("gc").invoke(spark);
            if (gcMap instanceof Map<?, ?> map && !map.isEmpty()) {
                int n = 0;
                Collection<?> collectors = map.values();
                for (Object gc : collectors) {
                    if (gc == null || n >= 2) {
                        break;
                    }
                    String name = (String) gc.getClass().getMethod("name").invoke(gc);
                    long collections = (Long) gc.getClass().getMethod("totalCollections").invoke(gc);
                    long totalMs = (Long) gc.getClass().getMethod("totalTime").invoke(gc);
                    out.add(String.format("GC %s: %d collections, %d ms total", name, collections, totalMs));
                    n++;
                }
            }
        } catch (Throwable t) {
            out.add("Spark API (reflection) incomplete: " + t.getClass().getSimpleName());
        }
    }

    /**
     * @return Spark TPS from 5s window, or NaN if unknown
     */
    public static double pollTps5sOrNaN() {
        if (!SparkDataProvider.isSparkAvailable()) {
            return Double.NaN;
        }
        try {
            Class<?> provider = Class.forName("me.lucko.spark.api.SparkProvider");
            Object spark = provider.getMethod("get").invoke(null);
            Object tpsStat = spark.getClass().getMethod("tps").invoke(spark);
            if (tpsStat == null) {
                return Double.NaN;
            }
            Class<?> tpsWindow = Class.forName("me.lucko.spark.api.statistic.StatisticWindow$TicksPerSecond");
            Object sec5 = Enum.valueOf((Class<Enum>) tpsWindow, "SECONDS_5");
            return (Double) tpsStat.getClass().getMethod("poll", tpsWindow).invoke(tpsStat, sec5);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }
}
