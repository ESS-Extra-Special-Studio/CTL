package uk.co.extraspecialstudio.calml_the_leaks_ctl.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CalmlTheLeaks;

@Mod.EventBusSubscriber(modid = CalmlTheLeaks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CtlClientTickHandler {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // F8 removed — CTL opens from ES Hub Utility or /ctl panel
    }

    private CtlClientTickHandler() {}
}
