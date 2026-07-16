package com.luci.snapshot.client.input;

import com.luci.snapshot.SnapshotInit;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class SnapshotKeybinds {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "controls")
    );

    private static KeyMapping toggle;
    private static KeyMapping capture;
    private static KeyMapping nextControl;
    private static KeyMapping previousControl;
    private static KeyMapping increase;
    private static KeyMapping decrease;
    private static KeyMapping autofocus;
    private static KeyMapping flash;
    private static KeyMapping burst;
    private static KeyMapping reset;
    private static KeyMapping toggleHudSize;
    private static KeyMapping tutorial;
    private static KeyMapping applyEnvironment;
    private static KeyMapping filmProfile;
    private static KeyMapping aspectRatio;
    private static KeyMapping mood;
    private static KeyMapping lighttable;
    private static KeyMapping exposureMode;
    private static KeyMapping exposureAssist;
    private static KeyMapping astrophotography;
    private static KeyMapping quickMenu;
    private static KeyMapping focusPointSelector;
    private static KeyMapping aeLock;
    private static KeyMapping afLock;

    private SnapshotKeybinds() {
    }

    public static void register() {
        toggle = register("key.snapshot.toggle_viewfinder", InputConstants.KEY_V);
        capture = register("key.snapshot.capture", InputConstants.KEY_C);
        nextControl = register("key.snapshot.next_control", InputConstants.KEY_B);
        previousControl = register("key.snapshot.previous_control", InputConstants.KEY_N);
        increase = register("key.snapshot.increase", InputConstants.KEY_EQUALS);
        decrease = register("key.snapshot.decrease", InputConstants.KEY_MINUS);
        autofocus = register("key.snapshot.autofocus", InputConstants.KEY_F6);
        flash = register("key.snapshot.flash", InputConstants.KEY_SEMICOLON);
        burst = register("key.snapshot.burst", InputConstants.KEY_H);
        reset = register("key.snapshot.reset", InputConstants.KEY_BACKSPACE);
        toggleHudSize = register("key.snapshot.toggle_hud_size", InputConstants.KEY_U);
        tutorial = register("key.snapshot.tutorial", InputConstants.KEY_J);
        applyEnvironment = register("key.snapshot.apply_environment", InputConstants.KEY_F7);
        filmProfile = register("key.snapshot.film_profile", InputConstants.KEY_Z);
        aspectRatio = register("key.snapshot.aspect_ratio", InputConstants.KEY_Y);
        mood = register("key.snapshot.mood", InputConstants.KEY_F8);
        lighttable = register("key.snapshot.lighttable", InputConstants.KEY_M);
        exposureMode = register("key.snapshot.exposure_mode", InputConstants.KEY_F10);
        exposureAssist = register("key.snapshot.exposure_assist", InputConstants.KEY_F9);
        astrophotography = register("key.snapshot.astrophotography", InputConstants.KEY_F12);
        quickMenu = register("key.snapshot.quick_menu", InputConstants.KEY_GRAVE);
        focusPointSelector = register("key.snapshot.focus_point_selector", InputConstants.KEY_X);
        aeLock = register("key.snapshot.ae_lock", InputConstants.KEY_LBRACKET);
        afLock = register("key.snapshot.af_lock", InputConstants.KEY_RBRACKET);
    }

    public static KeyMapping toggle() {
        return toggle;
    }

    public static KeyMapping capture() {
        return capture;
    }

    public static KeyMapping nextControl() {
        return nextControl;
    }

    public static KeyMapping previousControl() {
        return previousControl;
    }

    public static KeyMapping increase() {
        return increase;
    }

    public static KeyMapping decrease() {
        return decrease;
    }

    public static KeyMapping autofocus() {
        return autofocus;
    }

    public static KeyMapping flash() {
        return flash;
    }

    public static KeyMapping burst() {
        return burst;
    }

    public static KeyMapping reset() {
        return reset;
    }

    public static KeyMapping toggleHudSize() {
        return toggleHudSize;
    }

    public static KeyMapping tutorial() {
        return tutorial;
    }

    public static KeyMapping applyEnvironment() {
        return applyEnvironment;
    }

    public static KeyMapping filmProfile() {
        return filmProfile;
    }

    public static KeyMapping aspectRatio() {
        return aspectRatio;
    }

    public static KeyMapping mood() {
        return mood;
    }

    public static KeyMapping lighttable() {
        return lighttable;
    }

    public static KeyMapping exposureMode() {
        return exposureMode;
    }

    public static KeyMapping exposureAssist() {
        return exposureAssist;
    }

    public static KeyMapping astrophotography() {
        return astrophotography;
    }

    public static KeyMapping quickMenu() {
        return quickMenu;
    }

    public static KeyMapping focusPointSelector() {
        return focusPointSelector;
    }

    public static KeyMapping aeLock() {
        return aeLock;
    }

    public static KeyMapping afLock() {
        return afLock;
    }

    private static KeyMapping register(String translationKey, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(translationKey, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

    public static void migrateLegacyBindings(Minecraft client) {
        boolean changed = false;
        changed |= migrate(autofocus, InputConstants.KEY_F);
        changed |= migrate(flash, InputConstants.KEY_G);
        changed |= migrate(reset, InputConstants.KEY_R);
        changed |= migrate(applyEnvironment, InputConstants.KEY_O);
        changed |= migrate(quickMenu, InputConstants.KEY_K);
        if (changed) {
            KeyMapping.resetMapping();
            client.options.save();
            SnapshotInit.LOGGER.info("[Snapshot] Migrated legacy keybindings away from Minecraft and Iris conflicts.");
        }
    }

    private static boolean migrate(KeyMapping mapping, int legacyKey) {
        InputConstants.Key oldBinding = InputConstants.Type.KEYSYM.getOrCreate(legacyKey);
        if (!mapping.matches(oldBinding)) {
            return false;
        }
        mapping.setKey(mapping.getDefaultKey());
        return true;
    }
}
