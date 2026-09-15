package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Client–server payloads for the diagnostics GUI (protocol id ctl-8).
 */
public final class CtlNetwork {
    public static final String PROTOCOL = "ctl-8";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(PROTOCOL);
        reg.playToServer(C2SRequestPanelPayload.TYPE, C2SRequestPanelPayload.STREAM_CODEC, CtlServerPayloadHandlers::handleRequestPanel);
        reg.playToServer(C2SWorldMemoryPayload.TYPE, C2SWorldMemoryPayload.STREAM_CODEC, CtlServerPayloadHandlers::handleWorldMemory);
        reg.playToClient(S2CPanelDataPayload.TYPE, S2CPanelDataPayload.STREAM_CODEC, CtlCommonPayloadHandlers::handlePanelData);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private CtlNetwork() {}
}
