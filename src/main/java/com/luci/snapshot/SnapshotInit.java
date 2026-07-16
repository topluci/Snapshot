package com.luci.snapshot;

import com.luci.snapshot.command.SnapshotCommands;
import com.luci.snapshot.config.SnapshotConfig;
import com.luci.snapshot.item.SnapshotItems;
import com.luci.snapshot.network.SnapshotNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotInit implements ModInitializer {
    public static final String MOD_ID = "snapshot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SnapshotConfig.load();
        SnapshotItems.register();
        SnapshotNetworking.registerPayloads();
        SnapshotNetworking.registerServerReceivers();
        SnapshotCommands.register();
        LOGGER.info("[Snapshot] Camera systems initialised.");
    }
}
