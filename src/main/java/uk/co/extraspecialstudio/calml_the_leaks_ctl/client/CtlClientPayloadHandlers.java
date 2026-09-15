package uk.co.extraspecialstudio.calml_the_leaks_ctl.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.client.gui.CtlMainPanelScreen;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.S2CPanelDataPayload;

/**
 * Client-only S2C payload handling (not loaded on dedicated server).
 */
public final class CtlClientPayloadHandlers {
    private CtlClientPayloadHandlers() {}

    public static void handlePanelData(S2CPanelDataPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            CtlClientDiagnosticsCache.setLastPacket(payload.data());
            Minecraft.getInstance().setScreen(new CtlMainPanelScreen());
        });
    }
}
