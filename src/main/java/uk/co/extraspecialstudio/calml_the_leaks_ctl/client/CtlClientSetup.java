package uk.co.extraspecialstudio.calml_the_leaks_ctl.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CalmlTheLeaks;

@Mod.EventBusSubscriber(modid = CalmlTheLeaks.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CtlClientSetup {
    // F8 keybind removed — CTL is registered into ES Hub UTILITY

    private CtlClientSetup() {}
}
