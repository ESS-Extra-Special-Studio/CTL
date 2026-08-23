package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CalmlTheLeaks;

/**
 * Client–server channel for opening the diagnostics GUI on the client with server-authoritative data.
 */
public final class CtlNetwork {
    private static final String PROTOCOL = "ctl-8";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(CalmlTheLeaks.MODID, "main"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals
    );

    public static void register() {
        CHANNEL.messageBuilder(C2SRequestPanelPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(C2SRequestPanelPacket::encode)
            .decoder(C2SRequestPanelPacket::decode)
            .consumerMainThread(C2SRequestPanelPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CPanelDataPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(S2CPanelDataPacket::encode)
            .decoder(S2CPanelDataPacket::decode)
            .consumerMainThread(S2CPanelDataPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SWorldMemoryPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(C2SWorldMemoryPacket::encode)
            .decoder(C2SWorldMemoryPacket::decode)
            .consumerMainThread(C2SWorldMemoryPacket::handle)
            .add();
    }

    private CtlNetwork() {}
}
