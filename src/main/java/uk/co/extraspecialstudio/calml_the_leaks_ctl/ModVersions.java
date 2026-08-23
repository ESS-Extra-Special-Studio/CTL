package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import net.minecraftforge.fml.ModList;

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
}
