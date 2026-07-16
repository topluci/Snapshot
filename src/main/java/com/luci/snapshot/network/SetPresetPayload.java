package com.luci.snapshot.network;

import com.luci.snapshot.SnapshotInit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetPresetPayload(String preset) implements CustomPacketPayload {
    public static final Type<SetPresetPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "set_preset")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPresetPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(32),
        SetPresetPayload::preset,
        SetPresetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
