package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import java.util.Objects;

/**
 * Represents a normalized signature of a leak report from ATL.
 */
public class LeakSignature {
    public final String type;
    public final String targetClass;
    public final String modName;  // The mod that owns this class (e.g., "minecraft", "ftbchunks")
    public final String dimension;
    public final int count;

    public LeakSignature(String type, String targetClass, String modName, String dimension, int count) {
        this.type = type;
        this.targetClass = targetClass;
        this.modName = modName;
        this.dimension = dimension;
        this.count = count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeakSignature that = (LeakSignature) o;
        return Objects.equals(type, that.type) &&
               Objects.equals(targetClass, that.targetClass) &&
               Objects.equals(modName, that.modName) &&
               Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, targetClass, modName, dimension);
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s:%s:%d", type, targetClass, modName, dimension, count);
    }
    
    /**
     * Check if this signature represents a known false positive (e.g., claimed chunks).
     */
    public boolean isKnownFalsePositive() {
        // Known claiming mods that legitimately hold chunks
        String modLower = modName != null ? modName.toLowerCase() : "";
        return modLower.contains("ftbchunks") || 
               modLower.contains("ftb") ||
               modLower.contains("chunk") ||
               modLower.contains("claim");
    }
    
    /**
     * Check if this is a player-related leak (ServerPlayer, LocalPlayer, etc.)
     */
    public boolean isPlayerRelated() {
        String classLower = targetClass != null ? targetClass.toLowerCase() : "";
        return classLower.contains("player") || 
               classLower.contains("serverplayer") || 
               classLower.contains("localplayer");
    }
    
    /**
     * Check if this leak type is known to be problematic (e.g., player clones from mod conflicts).
     */
    public boolean isKnownProblematicType() {
        // Player-related leaks are often problematic, especially if they grow
        return isPlayerRelated();
    }
}
