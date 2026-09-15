package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Integration with Death Detangler mod.
 * This class receives notifications from Death Detangler when it cleans up player clones,
 * allowing CTL to track that Death Detangler is actively working and adjust its analysis accordingly.
 */
public class DeathDetanglerIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Track cleanups by Death Detangler
    // Key: player UUID, Value: cleanup timestamp
    private static final Map<UUID, Long> deathDetanglerCleanups = new ConcurrentHashMap<>();
    
    // Track total cleanups performed by Death Detangler
    private static int totalCleanups = 0;
    
    /**
     * Called by Death Detangler when it successfully cleans up a player clone.
     * 
     * @param playerUUID The UUID of the player that was cleaned up
     * @param originalPlayer The original player entity that was removed
     * @param removalReason The removal reason that was set
     * @param source Where the cleanup came from (e.g., "clone_event", "periodic_cleanup")
     */
    public static void onPlayerCleanup(UUID playerUUID, ServerPlayer originalPlayer, 
                                       Entity.RemovalReason removalReason, String source) {
        if (playerUUID == null) return;
        
        long currentTime = System.currentTimeMillis();
        deathDetanglerCleanups.put(playerUUID, currentTime);
        totalCleanups++;
        
        // If Death Detangler is actively cleaning up players, we should be more lenient
        // with player-related leak reports, as Death Detangler is handling them
        // Note: CTL's Config might not have enableLogNotifications, so we'll just log at debug level
        LOGGER.debug("[CTL] Death Detangler cleaned up player {} (source: {}, reason: {})", 
                playerUUID, source, removalReason);
        
        // Update leak state tracker to reflect that Death Detangler is handling this
        // This helps CTL understand that player leaks are being actively managed
        LeakSignature playerSignature = new LeakSignature("Player", "ServerPlayer", "minecraft", "overworld", 0);
        LeakStateTracker tracker = LeakStateTracker.getInstance();
        LeakState state = tracker.getOrCreateState(playerSignature);
        
        // Mark that Death Detangler is active and handling player cleanups
        state.deathDetanglerActive = true;
        state.deathDetanglerCleanupCount = totalCleanups;
    }
    
    /**
     * Check if Death Detangler has cleaned up a specific player recently.
     * 
     * @param playerUUID The player UUID to check
     * @param withinSeconds How many seconds ago to check (default 60)
     * @return true if Death Detangler cleaned up this player within the time window
     */
    public static boolean wasPlayerCleanedUp(UUID playerUUID, int withinSeconds) {
        Long cleanupTime = deathDetanglerCleanups.get(playerUUID);
        if (cleanupTime == null) return false;
        
        long age = (System.currentTimeMillis() - cleanupTime) / 1000;
        return age <= withinSeconds;
    }
    
    /**
     * Get the total number of cleanups performed by Death Detangler.
     * 
     * @return Total cleanup count
     */
    public static int getTotalCleanups() {
        return totalCleanups;
    }
    
    /**
     * Check if Death Detangler is active and handling player cleanups.
     * 
     * @return true if Death Detangler has performed any cleanups
     */
    public static boolean isDeathDetanglerActive() {
        return totalCleanups > 0;
    }
    
    /**
     * Reset cleanup tracking (called on server stop).
     */
    public static void reset() {
        deathDetanglerCleanups.clear();
        totalCleanups = 0;
    }
}
