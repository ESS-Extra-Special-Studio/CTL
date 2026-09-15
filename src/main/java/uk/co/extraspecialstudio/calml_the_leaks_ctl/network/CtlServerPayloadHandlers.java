package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.Config;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CtlPanelPayloadBuilder;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.LeakPersistenceManager;

/**
 * Payload handlers that run on the logical server only (safe on dedicated server).
 */
public final class CtlServerPayloadHandlers {
    private CtlServerPayloadHandlers() {}

    public static void handleRequestPanel(C2SRequestPanelPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || player.server == null) {
                return;
            }
            S2CPanelDataPacket data = CtlPanelPayloadBuilder.build(player.server);
            CtlNetwork.sendToPlayer(player, new S2CPanelDataPayload(data));
        });
    }

    public static void handleWorldMemory(C2SWorldMemoryPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
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
                if (payload.action() == C2SWorldMemoryPayload.ACTION_ENABLE) {
                    LeakPersistenceManager.enable(server);
                    player.sendSystemMessage(Component.literal(
                        "[CTL] World leak memory on: history is saved under this world's calmtheleaks_world_memory folder."));
                } else if (payload.action() == C2SWorldMemoryPayload.ACTION_DISABLE_DELETE) {
                    LeakPersistenceManager.disableAndDelete(server);
                    player.sendSystemMessage(Component.literal(
                        "[CTL] World leak memory off: folder removed and in-memory diagnosis cleared."));
                }
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("[CTL] World memory action failed: " + e.getMessage()));
            }
            refreshPanel(player);
        });
    }

    private static void refreshPanel(ServerPlayer player) {
        S2CPanelDataPacket data = CtlPanelPayloadBuilder.build(player.server);
        CtlNetwork.sendToPlayer(player, new S2CPanelDataPayload(data));
    }
}
