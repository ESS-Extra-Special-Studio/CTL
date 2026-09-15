package uk.co.extraspecialstudio.calml_the_leaks_ctl.client.esh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CalmlTheLeaks;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.C2SRequestPanelPayload;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.CtlNetwork;

/**
 * CTL diagnostics live under ES Hub → UTILITY when ESH is present (F8 removed).
 * ESH types are loaded only via {@link Class#forName} so missing hub JARs do not crash CTL.
 */
@EventBusSubscriber(modid = CalmlTheLeaks.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CtlEshIntegration {
    static final String ESH_MOD_ID = "extraspecialhub";

    private CtlEshIntegration() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!ModList.get().isLoaded(ESH_MOD_ID)) {
                return;
            }
            try {
                Class.forName("uk.co.extraspecialstudio.calml_the_leaks_ctl.client.esh.CtlEshHooks")
                    .getMethod("register")
                    .invoke(null);
            } catch (Throwable ignored) {
                // ESH optional
            }
        });
    }

    static Screen requestPanel(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CtlNetwork.sendToServer(new C2SRequestPanelPayload());
        }
        // Keep hub open until S2C opens the panel; returning parent avoids a blank screen.
        return parent;
    }
}

/** Loaded only when extraspecialhub is present. */
final class CtlEshHooks {
    private CtlEshHooks() {
    }

    public static void register() {
        uk.co.extraspecialstudio.esh.api.EshApi.registerModWindow(
            uk.co.extraspecialstudio.esh.api.EshWindowSpec.builder(
                    CalmlTheLeaks.MODID,
                    "panel",
                    "Calm The Leaks"
                )
                .section(uk.co.extraspecialstudio.esh.api.EshSection.UTILITY)
                .author("Extra Special Studio")
                .factory(CtlEshIntegration::requestPanel)
                .build());
    }
}

/** One-shot per session when ESH is not installed. */
@EventBusSubscriber(modid = CalmlTheLeaks.MODID, value = Dist.CLIENT)
final class CtlEshInstallNudge {
    private static boolean nudged;

    private CtlEshInstallNudge() {
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (nudged || ModList.get().isLoaded(CtlEshIntegration.ESH_MOD_ID)) {
            return;
        }
        nudged = true;
        if (event.getPlayer() != null) {
            event.getPlayer().displayClientMessage(Component.literal(
                "Extra Special Hub (ESH) is recommended for F9 utility access. CTL still works without it."
            ), false);
        }
    }
}
