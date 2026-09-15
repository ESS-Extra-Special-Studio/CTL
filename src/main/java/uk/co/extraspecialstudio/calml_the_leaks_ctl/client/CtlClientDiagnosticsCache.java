package uk.co.extraspecialstudio.calml_the_leaks_ctl.client;

import uk.co.extraspecialstudio.calml_the_leaks_ctl.network.S2CPanelDataPacket;

/**
 * Holds the last diagnostics snapshot received from the server for GUI screens.
 */
public final class CtlClientDiagnosticsCache {
    private static S2CPanelDataPacket last;

    public static void setLastPacket(S2CPanelDataPacket packet) {
        last = packet;
    }

    public static S2CPanelDataPacket getLastPacket() {
        return last;
    }

    private CtlClientDiagnosticsCache() {}
}
