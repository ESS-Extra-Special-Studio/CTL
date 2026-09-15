package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persists the narrow-down checklist and a baseline snapshot of leak counts (before/after pack changes).
 * Primary file: {@code config/calmtheleaks_narrow.json}. Older installs may have data under a legacy filename (decoded at runtime for one-time migration).
 */
public final class NarrowDownStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_MODS = 48;
    /** Legacy on-disk filename before the narrow JSON rename, decoded at runtime for one-time migration. */
    private static final String LEGACY_FILE_NAME = new String(
        Base64.getDecoder().decode("Y2FsbXRoZWxlYWtzX2Jpc2VjdC5qc29u"),
        StandardCharsets.UTF_8
    );
    private static final NarrowDownStore INSTANCE = new NarrowDownStore();

    private final CopyOnWriteArrayList<String> disabledMods = new CopyOnWriteArrayList<>();
    private long snapshotTimeMs;
    private final Map<String, Integer> snapshotCounts = new LinkedHashMap<>();

    public static NarrowDownStore get() {
        return INSTANCE;
    }

    private static Path primaryStorePath() {
        return FMLPaths.CONFIGDIR.get().resolve("calmtheleaks_narrow.json");
    }

    private static Path legacyStorePath() {
        return FMLPaths.CONFIGDIR.get().resolve(LEGACY_FILE_NAME);
    }

    public void onServerStarting() {
        load();
    }

    public void load() {
        Path primary = primaryStorePath();
        Path legacy = legacyStorePath();
        Path p = Files.isRegularFile(primary) ? primary : legacy;
        if (!Files.isRegularFile(p)) {
            return;
        }
        boolean fromLegacy = p.equals(legacy);
        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject o = GSON.fromJson(json, JsonObject.class);
            if (o == null) {
                return;
            }
            disabledMods.clear();
            if (o.has("disabledMods") && o.get("disabledMods").isJsonArray()) {
                JsonArray arr = o.getAsJsonArray("disabledMods");
                for (var el : arr) {
                    if (el.isJsonPrimitive()) {
                        String id = el.getAsString().trim().toLowerCase();
                        if (!id.isEmpty() && disabledMods.size() < MAX_MODS && !disabledMods.contains(id)) {
                            disabledMods.add(id);
                        }
                    }
                }
            }
            if (o.has("snapshotTimeMs") && o.get("snapshotTimeMs").isJsonPrimitive()) {
                snapshotTimeMs = o.get("snapshotTimeMs").getAsLong();
            }
            snapshotCounts.clear();
            if (o.has("snapshotCounts") && o.get("snapshotCounts").isJsonObject()) {
                JsonObject c = o.getAsJsonObject("snapshotCounts");
                for (Map.Entry<String, com.google.gson.JsonElement> en : c.entrySet()) {
                    if (en.getValue().isJsonPrimitive()) {
                        snapshotCounts.put(en.getKey(), en.getValue().getAsInt());
                    }
                }
            }
            if (fromLegacy && Files.isRegularFile(legacy)) {
                save();
                try {
                    Files.deleteIfExists(legacy);
                } catch (IOException io) {
                    LOGGER.warn("[CTL] Migrated narrow-down data to calmtheleaks_narrow.json but could not remove old file: {}",
                        io.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[CTL] Could not load narrow-down store: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Path out = primaryStorePath();
            Files.createDirectories(out.getParent());
            JsonObject o = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String m : disabledMods) {
                arr.add(m);
            }
            o.add("disabledMods", arr);
            o.addProperty("snapshotTimeMs", snapshotTimeMs);
            JsonObject counts = new JsonObject();
            snapshotCounts.forEach((k, v) -> counts.addProperty(k, v));
            o.add("snapshotCounts", counts);
            Files.writeString(out, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[CTL] Could not save narrow-down store: {}", e.getMessage());
        }
    }

    public List<String> getDisabledMods() {
        return List.copyOf(disabledMods);
    }

    public boolean addDisabledMod(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        String id = modId.trim().toLowerCase();
        if (disabledMods.contains(id) || disabledMods.size() >= MAX_MODS) {
            return false;
        }
        disabledMods.add(id);
        save();
        return true;
    }

    public boolean removeDisabledMod(String modId) {
        if (modId == null) {
            return false;
        }
        boolean r = disabledMods.remove(modId.trim().toLowerCase());
        if (r) {
            save();
        }
        return r;
    }

    public void clearDisabledMods() {
        disabledMods.clear();
        save();
    }

    public void takeSnapshot(LeakStateTracker tracker) {
        snapshotCounts.clear();
        long now = System.currentTimeMillis();
        for (Map.Entry<LeakSignature, LeakState> e : tracker.getAllStates().entrySet()) {
            snapshotCounts.put(signatureKey(e.getKey()), e.getValue().currentCount);
        }
        snapshotTimeMs = now;
        save();
    }

    public void clearSnapshot() {
        snapshotCounts.clear();
        snapshotTimeMs = 0;
        save();
    }

    public static String signatureKey(LeakSignature sig) {
        return (sig.type != null ? sig.type : "") + "|"
            + (sig.targetClass != null ? sig.targetClass : "") + "|"
            + (sig.modName != null ? sig.modName : "") + "|"
            + (sig.dimension != null ? sig.dimension : "");
    }

    public List<String> buildCompareLines(LeakStateTracker tracker) {
        List<String> lines = new ArrayList<>();
        if (snapshotTimeMs == 0 || snapshotCounts.isEmpty()) {
            lines.add("No snapshot yet. Run /ctl narrow snapshot after a baseline ATL refresh.");
            return lines;
        }
        lines.add("Snapshot age: " + (System.currentTimeMillis() - snapshotTimeMs) / 1000 + "s ago, "
            + snapshotCounts.size() + " signature(s).");
        lines.add("Disabled mods checklist (" + disabledMods.size() + "): "
            + (disabledMods.isEmpty() ? "(none — use /ctl narrow mod add <id>)" : String.join(", ", disabledMods)));

        for (Map.Entry<LeakSignature, LeakState> e : tracker.getAllStates().entrySet()) {
            String key = signatureKey(e.getKey());
            if (!snapshotCounts.containsKey(key)) {
                continue;
            }
            int was = snapshotCounts.get(key);
            int now = e.getValue().currentCount;
            int d = now - was;
            String arrow = d > 0 ? "+" + d : String.valueOf(d);
            LeakSignature s = e.getKey();
            lines.add(String.format("  %s [%s] %s: %d -> %d (%s)",
                s.type, s.dimension, s.targetClass, was, now, arrow));
        }
        for (String key : snapshotCounts.keySet()) {
            boolean stillPresent = tracker.getAllStates().keySet().stream()
                .anyMatch(sig -> signatureKey(sig).equals(key));
            if (!stillPresent) {
                lines.add("  (removed since snapshot) " + key + " was " + snapshotCounts.get(key));
            }
        }
        return lines;
    }

    public int getSnapshotSize() {
        return snapshotCounts.size();
    }
}
