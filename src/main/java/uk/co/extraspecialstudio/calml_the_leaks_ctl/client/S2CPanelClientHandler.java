package uk.co.extraspecialstudio.calml_the_leaks_ctl.client;

import net.minecraft.client.Minecraft;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.client.gui.CtlMainPanelScreen;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.S2CPanelDataPacket;

/**
 * Opens the diagnostics GUI when an S2C panel packet arrives. Lives in the client package so the dedicated server
 * never loads {@link net.minecraft.client.Minecraft} through the packet class.
 */
public final class S2CPanelClientHandler {
    public static void handle(S2CPanelDataPacket msg) {
        CtlClientDiagnosticsCache.setLastPacket(msg);
        Minecraft.getInstance().setScreen(new CtlMainPanelScreen());
    }

    private S2CPanelClientHandler() {}
}
