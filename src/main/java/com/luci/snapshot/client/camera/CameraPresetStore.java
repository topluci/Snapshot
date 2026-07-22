package com.luci.snapshot.client.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luci.snapshot.SnapshotInit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

final class CameraPresetStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<CameraPresetSlot, CameraSettings> SLOTS = new EnumMap<>(CameraPresetSlot.class);
    private static boolean loaded;
    private static CameraPresetSlot activeSlot;
    private static Path pathOverride;

    private CameraPresetStore() {
    }

    static CameraSettings settings(CameraPresetSlot slot) {
        ensureLoaded();
        return SLOTS.getOrDefault(slot, slot.defaults()).copy();
    }

    static void load(CameraPresetSlot slot, CameraSettings target) {
        target.applyFrom(settings(slot));
        activeSlot = slot;
    }

    static void save(CameraPresetSlot slot, CameraSettings source) {
        ensureLoaded();
        SLOTS.put(slot, source.copy());
        activeSlot = slot;
        write();
    }

    static boolean active(CameraPresetSlot slot) {
        return activeSlot == slot;
    }

    static Path path() {
        return pathOverride != null
            ? pathOverride : FabricLoader.getInstance().getConfigDir().resolve("snapshot-presets.json");
    }

    static void resetForTests() {
        loaded = false;
        activeSlot = null;
        SLOTS.clear();
    }

    static void usePathForTests(Path path) {
        pathOverride = path;
        resetForTests();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (CameraPresetSlot slot : CameraPresetSlot.values()) {
            SLOTS.put(slot, slot.defaults());
        }
        Path path = path();
        if (!Files.isRegularFile(path)) {
            write();
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject slots = root.has("slots") && root.get("slots").isJsonObject()
                ? root.getAsJsonObject("slots") : new JsonObject();
            for (CameraPresetSlot slot : CameraPresetSlot.values()) {
                if (!slots.has(slot.id())) {
                    continue;
                }
                CameraSettings settings = JSON.fromJson(slots.get(slot.id()), CameraSettings.class);
                CameraSettings validated = new CameraSettings();
                validated.applyFrom(settings);
                SLOTS.put(slot, validated);
            }
        } catch (RuntimeException | IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not read camera presets; using built-in slot defaults.", exception);
        }
    }

    private static void write() {
        Path path = path();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonObject slots = new JsonObject();
            for (CameraPresetSlot slot : CameraPresetSlot.values()) {
                slots.add(slot.id(), JSON.toJsonTree(SLOTS.getOrDefault(slot, slot.defaults())));
            }
            root.add("slots", slots);
            Files.writeString(temporary, JSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not save camera presets.", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }
}
