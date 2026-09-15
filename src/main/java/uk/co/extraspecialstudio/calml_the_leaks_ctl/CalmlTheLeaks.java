package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.CtlNetwork;

@Mod(CalmlTheLeaks.MODID)
public class CalmlTheLeaks {
    public static final String MODID = "calmtheleaks";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CalmlTheLeaks(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(CtlNetwork::register);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("[CTL] Calm The Leaks initialized. Because not every warning deserves panic.");
        event.enqueueWork(() -> {
            try {
                LogInterceptor.initialize();
            } catch (Exception e) {
                LOGGER.warn("[CTL] Failed to initialize log interceptor: {}", e.getMessage());
            }
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        PhaseManager.onServerStart();
        LeakPersistenceManager.onServerStarting(event.getServer());
        AtlOutputInterceptor.reset();
        NarrowDownStore.get().onServerStarting();
        LOGGER.info("[CTL] Server starting. Monitoring ATL output with context and restraint.");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server != null) {
            LeakPersistenceManager.onServerTickEnd(server);
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        LeakPersistenceManager.onServerStopped(event.getServer());
        PhaseManager.onServerStop();
        LeakStateTracker.getInstance().clear();
        AtlOutputInterceptor.reset();
        SparkProfilerManager.clear();
        DeathDetanglerIntegration.reset();
        LOGGER.info("[CTL] Server stopped. CTL state cleared.");
    }
}
