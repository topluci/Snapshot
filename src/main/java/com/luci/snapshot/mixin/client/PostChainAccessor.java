package com.luci.snapshot.mixin.client;

import java.util.Map;
import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor("passes")
    List<PostPass> snapshot$passes();

    @Accessor("internalTargets")
    Map<Identifier, PostChainConfig.InternalTarget> snapshot$internalTargets();

    @Mutable
    @Accessor("internalTargets")
    void snapshot$setInternalTargets(Map<Identifier, PostChainConfig.InternalTarget> targets);
}
