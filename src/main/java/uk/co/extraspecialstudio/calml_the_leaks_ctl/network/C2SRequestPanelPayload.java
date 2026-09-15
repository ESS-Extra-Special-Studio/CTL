package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CalmlTheLeaks;

public record C2SRequestPanelPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<C2SRequestPanelPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CalmlTheLeaks.MODID, "c2s_request_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestPanelPayload> STREAM_CODEC =
        StreamCodec.unit(new C2SRequestPanelPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
