package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.Config;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CtlPanelPayloadBuilder;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.LeakPersistenceManager;

import java.util.function.Supplier;

/**
 * Client requests enabling or disabling world-scoped leak persistence (op / permission 2).
 */
public final class C2SWorldMemoryPacket {
    public static final byte ACTION_ENABLE = 0;
    public static final byte ACTION_DISABLE_DELETE = 1;

    public final byte action;

    public C2SWorldMemoryPacket(byte action) {
        this.action = action;
    }

    public static void encode(C2SWorldMemoryPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
    }

    public static C2SWorldMemoryPacket decode(FriendlyByteBuf buf) {
        return new C2SWorldMemoryPacket(buf.readByte());
    }

    public static void handle(C2SWorldMemoryPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(
                    "[CTL] World leak memory controls require permission level 2 (op)."));
                return;
            }
            MinecraftServer server = player.server;
            if (!Config.allowWorldLeakMemory) {
                player.sendSystemMessage(Component.literal("[CTL] World leak memory is disabled in server config."));
                refreshPanel(player);
                return;
            }
            try {
                if (msg.action == ACTION_ENABLE) {
                    LeakPersistenceManager.enable(server);
                    player.sendSystemMessage(Component.literal(
                        "[CTL] World leak memory on: history is saved under this world's calmtheleaks_world_memory folder."));
                } else if (msg.action == ACTION_DISABLE_DELETE) {
                    LeakPersistenceManager.disableAndDelete(server);
                    player.sendSystemMessage(Component.literal(
                        "[CTL] World leak memory off: folder removed and in-memory diagnosis cleared."));
                }
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("[CTL] World memory action failed: " + e.getMessage()));
            }
            refreshPanel(player);
        });
        ctx.setPacketHandled(true);
    }

    private static void refreshPanel(ServerPlayer player) {
        S2CPanelDataPacket payload = CtlPanelPayloadBuilder.build(player.server);
        CtlNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
