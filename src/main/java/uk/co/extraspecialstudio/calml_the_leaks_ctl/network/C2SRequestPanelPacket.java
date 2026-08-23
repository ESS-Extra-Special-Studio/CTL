package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CtlPanelPayloadBuilder;

import java.util.function.Supplier;

/**
 * Client asks the server for a fresh diagnostics payload (opens GUI on client).
 */
public final class C2SRequestPanelPacket {
    public static void encode(C2SRequestPanelPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static C2SRequestPanelPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestPanelPacket();
    }

    public static void handle(C2SRequestPanelPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.server == null) {
                return;
            }
            S2CPanelDataPacket payload = CtlPanelPayloadBuilder.build(player.server);
            CtlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        });
        ctx.setPacketHandled(true);
    }
}
