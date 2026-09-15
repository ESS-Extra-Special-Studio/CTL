package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional per-world leak memory: stores tracker state under the world folder when enabled from the GUI.
 */
public final class LeakPersistenceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "calmtheleaks_world_memory";
    private static final String FILE_LEAKS = "leaks.json";
    private static final String FILE_META = "meta.json";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final AtomicBoolean dirty = new AtomicBoolean(false);
    private static volatile boolean sessionActive;
    private static volatile MinecraftServer attachedServer;

    private LeakPersistenceManager() {}

    public static boolean isSessionActive() {
        return sessionActive;
    }

    public static void markDirty() {
        if (sessionActive && Config.allowWorldLeakMemory) {
            dirty.set(true);
        }
    }

    public static Path storageDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
    }

    public static void onServerStarting(MinecraftServer server) {
        attachedServer = server;
        sessionActive = false;
        if (!Config.allowWorldLeakMemory) {
            LeakStateTracker.getInstance().clear();
            return;
        }
        Path dir = storageDir(server);
        Path metaPath = dir.resolve(FILE_META);
        if (Files.isRegularFile(metaPath) && readMetaActive(metaPath)) {
            sessionActive = true;
            LeakStateTracker.getInstance().clear();
            Path leaksPath = dir.resolve(FILE_LEAKS);
            if (Files.isRegularFile(leaksPath)) {
                try {
                    Map<LeakSignature, LeakState> loaded = readLeaks(leaksPath);
                    LeakStateTracker.getInstance().loadFromPersistence(loaded);
                    LOGGER.info("[CTL] Loaded {} leak signature(s) from world memory.", loaded.size());
                } catch (Exception e) {
                    LOGGER.warn("[CTL] Failed to load world leak memory, starting fresh: {}", e.toString());
                }
            }
            return;
        }
        LeakStateTracker.getInstance().clear();
    }

    public static void onServerTickEnd(MinecraftServer server) {
        if (!sessionActive || !Config.allowWorldLeakMemory || server != attachedServer) {
            return;
        }
        if ((server.getTickCount() % 100) != 0 || !dirty.getAndSet(false)) {
            return;
        }
        try {
            saveNow(server);
        } catch (Exception e) {
            LOGGER.warn("[CTL] World leak memory autosave failed: {}", e.toString());
            dirty.set(true);
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        try {
            if (sessionActive && Config.allowWorldLeakMemory && server != null) {
                saveNow(server);
            }
        } catch (Exception e) {
            LOGGER.warn("[CTL] World leak memory final save failed: {}", e.toString());
        } finally {
            attachedServer = null;
        }
    }

    /** Enables persistence for this world (op / permission 2). Writes meta and initial snapshot. */
    public static void enable(MinecraftServer server) throws IOException {
        if (!Config.allowWorldLeakMemory) {
            return;
        }
        Path dir = storageDir(server);
        Files.createDirectories(dir);
        writeMeta(dir.resolve(FILE_META), true);
        sessionActive = true;
        saveNow(server);
        dirty.set(false);
    }

    /** Stops persistence and removes all CTL data from the world folder. Clears in-memory tracker. */
    public static void disableAndDelete(MinecraftServer server) throws IOException {
        sessionActive = false;
        dirty.set(false);
        LeakStateTracker.getInstance().clear();
        Path dir = storageDir(server);
        if (Files.isDirectory(dir)) {
            deleteTree(dir);
        }
    }

    public static void saveNow(MinecraftServer server) throws IOException {
        if (!sessionActive || !Config.allowWorldLeakMemory) {
            return;
        }
        Path dir = storageDir(server);
        Files.createDirectories(dir);
        writeMeta(dir.resolve(FILE_META), true);
        writeLeaks(dir.resolve(FILE_LEAKS));
    }

    private static boolean readMetaActive(Path metaPath) {
        try {
            String raw = Files.readString(metaPath, StandardCharsets.UTF_8);
            JsonObject o = GSON.fromJson(raw, JsonObject.class);
            return o != null && o.has("active") && o.get("active").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeMeta(Path path, boolean active) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("active", active);
        Files.writeString(path, GSON.toJson(o), StandardCharsets.UTF_8);
    }

    private static void writeLeaks(Path path) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray entries = new JsonArray();
        for (Map.Entry<LeakSignature, LeakState> e : LeakStateTracker.getInstance().getAllStates().entrySet()) {
            entries.add(serializeEntry(e.getKey(), e.getValue()));
        }
        root.add("entries", entries);
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
        Files.deleteIfExists(path);
        Files.move(tmp, path);
    }

    private static JsonObject serializeEntry(LeakSignature sig, LeakState st) {
        JsonObject o = new JsonObject();
        o.addProperty("type", sig.type);
        o.addProperty("targetClass", sig.targetClass);
        o.addProperty("modName", sig.modName);
        o.addProperty("dimension", sig.dimension);
        o.addProperty("signatureCount", sig.count);
        o.addProperty("firstSeen", st.firstSeenTimestamp);
        o.addProperty("lastSeen", st.lastSeenTimestamp);
        o.addProperty("previousCount", st.previousCount);
        o.addProperty("currentCount", st.currentCount);
        o.addProperty("trendOrdinal", st.trend.ordinal());
        o.addProperty("confirmedLeak", st.confirmedLeak);
        o.addProperty("suppressed", st.suppressed);
        o.addProperty("reportCount", st.reportCount);
        o.addProperty("growthEvents", st.growthEvents);
        o.addProperty("stableEvents", st.stableEvents);
        o.addProperty("shrinkEvents", st.shrinkEvents);
        o.addProperty("maxCount", st.maxCount);
        o.addProperty("minCount", st.minCount);
        o.addProperty("deathDetanglerActive", st.deathDetanglerActive);
        o.addProperty("deathDetanglerCleanupCount", st.deathDetanglerCleanupCount);
        JsonArray samples = new JsonArray();
        for (LeakSample sm : st.copySamples()) {
            JsonObject s = new JsonObject();
            s.addProperty("timeMs", sm.timeMs);
            s.addProperty("count", sm.count);
            s.addProperty("heapPercent", sm.heapPercent);
            samples.add(s);
        }
        o.add("samples", samples);
        return o;
    }

    private static Map<LeakSignature, LeakState> readLeaks(Path path) throws IOException {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject root = GSON.fromJson(raw, JsonObject.class);
        if (root == null || !root.has("entries")) {
            return new HashMap<>();
        }
        JsonArray arr = root.getAsJsonArray("entries");
        Map<LeakSignature, LeakState> out = new HashMap<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            LeakState st = deserializeEntry(el.getAsJsonObject());
            if (st != null) {
                out.put(st.signature, st);
            }
        }
        return out;
    }

    private static LeakState deserializeEntry(JsonObject o) {
        try {
            String type = getStr(o, "type");
            String targetClass = getStr(o, "targetClass");
            String modName = getStr(o, "modName");
            String dimension = getStr(o, "dimension");
            int signatureCount = o.has("signatureCount") ? o.get("signatureCount").getAsInt() : 0;
            long firstSeen = o.get("firstSeen").getAsLong();
            long lastSeen = o.get("lastSeen").getAsLong();
            int previousCount = o.get("previousCount").getAsInt();
            int currentCount = o.get("currentCount").getAsInt();
            int trendOrdinal = o.get("trendOrdinal").getAsInt();
            boolean confirmedLeak = o.get("confirmedLeak").getAsBoolean();
            boolean suppressed = o.get("suppressed").getAsBoolean();
            int reportCount = o.get("reportCount").getAsInt();
            int growthEvents = o.get("growthEvents").getAsInt();
            int stableEvents = o.get("stableEvents").getAsInt();
            int shrinkEvents = o.get("shrinkEvents").getAsInt();
            int maxCount = o.get("maxCount").getAsInt();
            int minCount = o.get("minCount").getAsInt();
            boolean deathDetanglerActive = o.has("deathDetanglerActive") && o.get("deathDetanglerActive").getAsBoolean();
            int deathDetanglerCleanupCount = o.has("deathDetanglerCleanupCount") ? o.get("deathDetanglerCleanupCount").getAsInt() : 0;
            List<LeakSample> samples = new ArrayList<>();
            if (o.has("samples") && o.get("samples").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("samples")) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject s = el.getAsJsonObject();
                    samples.add(new LeakSample(
                        s.get("timeMs").getAsLong(),
                        s.get("count").getAsInt(),
                        s.get("heapPercent").getAsFloat()
                    ));
                }
            }
            return LeakState.fromPersistence(
                type, targetClass, modName, dimension, signatureCount,
                firstSeen, lastSeen, previousCount, currentCount, trendOrdinal,
                confirmedLeak, suppressed, reportCount,
                growthEvents, stableEvents, shrinkEvents, maxCount, minCount,
                deathDetanglerActive, deathDetanglerCleanupCount,
                samples
            );
        } catch (Exception e) {
            LOGGER.warn("[CTL] Skipping malformed leak persistence entry: {}", e.toString());
            return null;
        }
    }

    private static String getStr(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walk(root)
            .sorted((a, b) -> -a.compareTo(b))
            .forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    LOGGER.warn("[CTL] Could not delete {}: {}", p, e.toString());
                }
            });
    }
}
