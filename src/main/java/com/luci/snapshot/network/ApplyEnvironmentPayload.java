package com.luci.snapshot.network;

import com.luci.snapshot.SnapshotInit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ApplyEnvironmentPayload(String preset) implements CustomPacketPayload {
    public static final Type<ApplyEnvironmentPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "apply_environment")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyEnvironmentPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(32),
        ApplyEnvironmentPayload::preset,
        ApplyEnvironmentPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
