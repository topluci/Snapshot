package com.luci.snapshot.network;

import com.luci.snapshot.SnapshotInit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CapturePhotoPayload(String title, String settings, boolean pngExported, int width, int height, byte[] thumbnail) implements CustomPacketPayload {
    public static final Type<CapturePhotoPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "capture_photo")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CapturePhotoPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(96),
        CapturePhotoPayload::title,
        ByteBufCodecs.stringUtf8(1024),
        CapturePhotoPayload::settings,
        ByteBufCodecs.BOOL,
        CapturePhotoPayload::pngExported,
        ByteBufCodecs.VAR_INT,
        CapturePhotoPayload::width,
        ByteBufCodecs.VAR_INT,
        CapturePhotoPayload::height,
        ByteBufCodecs.byteArray(128 * 128 * 4),
        CapturePhotoPayload::thumbnail,
        CapturePhotoPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
