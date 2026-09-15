package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handlers referenced by common payload registration. Must avoid direct client class references so dedicated servers
 * can load this class during channel registration/handshake.
 */
public final class CtlCommonPayloadHandlers {
    private CtlCommonPayloadHandlers() {}

    public static void handlePanelData(S2CPanelDataPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> invokeClientPanelHandler(payload, ctx));
    }

    private static void invokeClientPanelHandler(S2CPanelDataPayload payload, IPayloadContext ctx) {
        try {
            Class<?> clazz = Class.forName("uk.co.extraspecialstudio.calml_the_leaks_ctl.client.CtlClientPayloadHandlers");
            clazz.getMethod("handlePanelData", S2CPanelDataPayload.class, IPayloadContext.class)
                .invoke(null, payload, ctx);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[CTL] Failed to handle S2C panel payload on client", e);
        }
    }
}
