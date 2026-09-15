package uk.co.extraspecialstudio.calml_the_leaks_ctl.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.co.extraspecialstudio.calml_the_leaks_ctl.CalmlTheLeaks;

public record C2SWorldMemoryPayload(byte action) implements CustomPacketPayload {
    public static final byte ACTION_ENABLE = 0;
    public static final byte ACTION_DISABLE_DELETE = 1;

    public static final CustomPacketPayload.Type<C2SWorldMemoryPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CalmlTheLeaks.MODID, "c2s_world_memory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SWorldMemoryPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeByte(payload.action()),
        buf -> new C2SWorldMemoryPayload(buf.readByte())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
