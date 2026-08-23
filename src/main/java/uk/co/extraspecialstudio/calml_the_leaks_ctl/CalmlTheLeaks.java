package uk.co.extraspecialstudio.calml_the_leaks_ctl;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.CtlNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Calm The Leaks (CTL) - A companion mod for All The Leaks that adds context,
 * memory, and emotional intelligence to leak reporting.
 */
@Mod(CalmlTheLeaks.MODID)
public class CalmlTheLeaks {
    public static final String MODID = "calmtheleaks";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CalmlTheLeaks() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[CTL] Calm The Leaks initialized. Because not every warning deserves panic.");
        
        // Initialize log interceptor
        event.enqueueWork(() -> {
            try {
                LogInterceptor.initialize();
            } catch (Exception e) {
                LOGGER.warn("[CTL] Failed to initialize log interceptor: {}", e.getMessage());
            }
            CtlNetwork.register();
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MinecraftServer server = event.getServer();
            if (server != null) {
                LeakPersistenceManager.onServerTickEnd(server);
            }
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
