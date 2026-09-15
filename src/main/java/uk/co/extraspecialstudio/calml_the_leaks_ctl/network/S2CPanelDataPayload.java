package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CalmlTheLeaks;

public record S2CPanelDataPayload(S2CPanelDataPacket data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<S2CPanelDataPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CalmlTheLeaks.MODID, "s2c_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPanelDataPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> S2CPanelDataPacket.encode(payload.data(), buf),
        buf -> new S2CPanelDataPayload(S2CPanelDataPacket.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
