package uk.co.extraspecialstudio.calml_the_leaks_ctl.client.esh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CalmlTheLeaks;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.C2SRequestPanelPacket;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.CtlNetwork;
import uk.co.extraspecialstudio.esh.api.EshApi;
import uk.co.extraspecialstudio.esh.api.EshSection;
import uk.co.extraspecialstudio.esh.api.EshWindowSpec;

/**
 * CTL diagnostics live under ES Hub → UTILITY (F8 removed).
 */
@Mod.EventBusSubscriber(modid = CalmlTheLeaks.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CtlEshIntegration {
    private CtlEshIntegration() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> EshApi.registerModWindow(EshWindowSpec.builder(
                CalmlTheLeaks.MODID,
                "panel",
                "Calm The Leaks"
            )
            .section(EshSection.UTILITY)
            .author("Extra Special Studio")
            .factory(CtlEshIntegration::requestPanel)
            .build()));
    }

    private static Screen requestPanel(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CtlNetwork.CHANNEL.sendToServer(new C2SRequestPanelPacket());
        }
        // Keep hub open until S2C opens the panel; returning parent avoids a blank screen.
        return parent;
    }
}
