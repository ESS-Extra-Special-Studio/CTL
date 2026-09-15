package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Tracks server lifecycle phases: startup, warmup, live, idle.
 */
@EventBusSubscriber(modid = CalmlTheLeaks.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PhaseManager {
    public enum Phase {
        STARTUP,
        WARMUP,
        LIVE,
        IDLE
    }

    private static Phase currentPhase = Phase.STARTUP;
    private static long serverStartTime = 0;

    public static Phase getCurrentPhase() {
        return currentPhase;
    }

    public static long getServerUptimeSeconds() {
        if (serverStartTime == 0) return 0;
        return (System.currentTimeMillis() - serverStartTime) / 1000;
    }

    public static boolean isInStartupGracePeriod() {
        if (serverStartTime == 0) return true;
        long uptime = getServerUptimeSeconds();
        return uptime < Config.startupGracePeriodSeconds;
    }

    public static void onServerStart() {
        serverStartTime = System.currentTimeMillis();
        currentPhase = Phase.STARTUP;
    }

    public static void onServerStop() {
        serverStartTime = 0;
        currentPhase = Phase.STARTUP;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        if (serverStartTime == 0) return;

        long currentTime = System.currentTimeMillis();
        long uptime = (currentTime - serverStartTime) / 1000;

        if (uptime < Config.startupGracePeriodSeconds) {
            if (currentPhase != Phase.STARTUP && currentPhase != Phase.WARMUP) {
                currentPhase = Phase.WARMUP;
            } else if (uptime < 60) {
                currentPhase = Phase.STARTUP;
            } else {
                currentPhase = Phase.WARMUP;
            }
        } else {
            if (event.getServer() != null && event.getServer().getPlayerCount() > 0) {
                currentPhase = Phase.LIVE;
            } else {
                currentPhase = Phase.IDLE;
            }
        }
    }
}
