package com.luci.snapshot.compat.modmenu;

import com.luci.snapshot.client.config.SnapshotConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class SnapshotModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SnapshotConfigScreen::new;
    }
}
