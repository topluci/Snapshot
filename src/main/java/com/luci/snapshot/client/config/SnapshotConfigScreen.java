package com.luci.snapshot.client.config;

import com.luci.snapshot.config.SnapshotConfig;
import com.luci.snapshot.config.SnapshotConfig.ConfigSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SnapshotConfigScreen extends OptionsSubScreen {
    private static final int NORMAL_TEXT = 0xE0E0E0;
    private static final int INVALID_TEXT = 0xFF5555;

    private final Map<ConfigSpec, EditBox> fields = new LinkedHashMap<>();
    private Properties properties;
    private Component status = Component.literal("Edit values, then Save.");
    private int statusColor = 0xA0A0A0;

    public SnapshotConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.literal("Snapshot Config"));
        this.properties = SnapshotConfig.loadEditableProperties();
    }

    @Override
    protected void addOptions() {
        String section = "";
        for (ConfigSpec spec : SnapshotConfig.SPECS) {
            if (!section.equals(spec.section())) {
                section = spec.section();
                list.addHeader(Component.literal(section));
            }

            StringWidget label = new StringWidget(0, 0, 150, 20, Component.literal(spec.label()), font).setMaxWidth(148);
            EditBox field = new EditBox(font, 0, 0, 150, 20, Component.literal(spec.label()));
            field.setValue(properties.getProperty(spec.key(), spec.defaultValue()));
            field.setMaxLength(spec.textValue() ? 24 : 8);
            field.setTooltip(Tooltip.create(Component.literal(spec.key() + "\nRange: " + spec.rangeText())));
            field.setResponder(value -> validateField(spec, field));
            validateField(spec, field);
            fields.put(spec, field);
            list.addSmall(label, field);
        }
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = LinearLayout.horizontal().spacing(8);
        footer.addChild(Button.builder(Component.literal("Save"), button -> saveAndClose()).width(96).build());
        footer.addChild(Button.builder(Component.literal("Reset Defaults"), button -> resetDefaults()).width(124).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).width(96).build());
        layout.addToFooter(footer);
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(font, status, width / 2, height - 48, statusColor);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(lastScreen);
    }

    private void saveAndClose() {
        Properties next = SnapshotConfig.defaultEditableProperties();
        boolean valid = true;

        for (Map.Entry<ConfigSpec, EditBox> entry : fields.entrySet()) {
            ConfigSpec spec = entry.getKey();
            EditBox field = entry.getValue();
            if (!validateField(spec, field)) {
                valid = false;
            }
            next.setProperty(spec.key(), field.getValue().trim());
        }

        if (!valid) {
            status = Component.literal("Fix highlighted values before saving.");
            statusColor = INVALID_TEXT;
            return;
        }

        SnapshotConfig.save(next);
        minecraft.setScreenAndShow(lastScreen);
    }

    private void resetDefaults() {
        properties = SnapshotConfig.defaultEditableProperties();
        for (Map.Entry<ConfigSpec, EditBox> entry : fields.entrySet()) {
            ConfigSpec spec = entry.getKey();
            EditBox field = entry.getValue();
            field.setValue(properties.getProperty(spec.key(), spec.defaultValue()));
            validateField(spec, field);
        }
        status = Component.literal("Defaults loaded. Save to apply them.");
        statusColor = 0xA0A0A0;
    }

    private boolean validateField(ConfigSpec spec, EditBox field) {
        boolean valid = isValid(spec, field.getValue().trim());
        field.setTextColor(valid ? NORMAL_TEXT : INVALID_TEXT);
        return valid;
    }

    private static boolean isValid(ConfigSpec spec, String value) {
        if (spec.booleanValue()) {
            return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
        }
        if (spec.textValue()) {
            return "low".equalsIgnoreCase(value)
                || "medium".equalsIgnoreCase(value)
                || "ultra".equalsIgnoreCase(value)
                || "screenshot_ultra".equalsIgnoreCase(value);
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= (int) spec.min() && parsed <= (int) spec.max();
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
