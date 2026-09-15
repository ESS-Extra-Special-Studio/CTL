package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import net.neoforged.fml.ModList;

/**
 * Reads installed mod versions for diagnostics (ATL / Spark compatibility hints).
 */
public final class ModVersions {
    private ModVersions() {}

    public static String versionOrNotLoaded(String modId) {
        return ModList.get().getModContainerById(modId)
            .map(c -> c.getModInfo().getVersion().toString())
            .orElse("not loaded");
    }

    public static String allTheLeaksLine() {
        return "AllTheLeaks: " + versionOrNotLoaded("alltheleaks");
    }

    public static String sparkLine() {
        return "Spark: " + versionOrNotLoaded("spark");
    }

    /** Shown next to mod versions so the panel reflects the running game (ATL's own string may still say an older platform). */
    public static String mcNeoLine() {
        return "MC " + versionOrNotLoaded("minecraft") + " | NeoForge " + versionOrNotLoaded("neoforge");
    }
}
