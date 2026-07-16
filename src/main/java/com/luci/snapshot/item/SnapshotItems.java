package com.luci.snapshot.item;

import com.luci.snapshot.SnapshotInit;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class SnapshotItems {
    public static final Identifier CAMERA_ID = Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "camera");
    public static final Identifier PHOTOGRAPHIC_PAPER_ID = Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "photographic_paper");
    public static final Identifier PHOTOGRAPH_ID = Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "photograph");
    public static final Identifier TRIPOD_ID = Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "tripod");

    private static final ResourceKey<Item> CAMERA_KEY = ResourceKey.create(Registries.ITEM, CAMERA_ID);
    private static final ResourceKey<Item> PHOTOGRAPHIC_PAPER_KEY = ResourceKey.create(Registries.ITEM, PHOTOGRAPHIC_PAPER_ID);
    private static final ResourceKey<Item> PHOTOGRAPH_KEY = ResourceKey.create(Registries.ITEM, PHOTOGRAPH_ID);
    private static final ResourceKey<Item> TRIPOD_KEY = ResourceKey.create(Registries.ITEM, TRIPOD_ID);

    public static final Item CAMERA = Registry.register(
        BuiltInRegistries.ITEM,
        CAMERA_KEY,
        new CameraItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).setId(CAMERA_KEY))
    );

    public static final Item PHOTOGRAPHIC_PAPER = Registry.register(
        BuiltInRegistries.ITEM,
        PHOTOGRAPHIC_PAPER_KEY,
        new Item(new Item.Properties().stacksTo(64).setId(PHOTOGRAPHIC_PAPER_KEY))
    );

    public static final Item PHOTOGRAPH = Registry.register(
        BuiltInRegistries.ITEM,
        PHOTOGRAPH_KEY,
        new PhotographItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON).setId(PHOTOGRAPH_KEY))
    );

    public static final Item TRIPOD = Registry.register(
        BuiltInRegistries.ITEM,
        TRIPOD_KEY,
        new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).setId(TRIPOD_KEY))
    );

    private SnapshotItems() {
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(CAMERA);
            output.accept(PHOTOGRAPHIC_PAPER);
            output.accept(PHOTOGRAPH);
            output.accept(TRIPOD);
        });
        SnapshotInit.LOGGER.debug("[Snapshot] Registered items.");
    }
}
