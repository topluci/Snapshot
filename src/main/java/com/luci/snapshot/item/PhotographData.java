package com.luci.snapshot.item;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class PhotographData {
    private static final String TITLE = "snapshot_photo_title";
    private static final String COLORS = "snapshot_photo_colors";
    public static final int MAP_PIXEL_COUNT = 128 * 128;

    private PhotographData() {
    }

    public static void apply(ItemStack stack, String title, byte[] colors) {
        stack.set(DataComponents.CUSTOM_DATA, create(title, colors));
    }

    static CustomData create(String title, byte[] colors) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TITLE, title);
        if (colors != null && colors.length == MAP_PIXEL_COUNT) {
            tag.putByteArray(COLORS, colors);
        }
        return CustomData.of(tag);
    }

    public static String title(ItemStack stack) {
        return title(stack.get(DataComponents.CUSTOM_DATA));
    }

    static String title(CustomData customData) {
        if (customData == null) {
            return "";
        }
        return customData.copyTag().getStringOr(TITLE, "");
    }

    public static Optional<byte[]> colors(ItemStack stack) {
        return colors(stack.get(DataComponents.CUSTOM_DATA));
    }

    static Optional<byte[]> colors(CustomData customData) {
        if (customData == null) {
            return Optional.empty();
        }
        return customData.copyTag().getByteArray(COLORS)
            .filter(colors -> colors.length == MAP_PIXEL_COUNT);
    }
}
