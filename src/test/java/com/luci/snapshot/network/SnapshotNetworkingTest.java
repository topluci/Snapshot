package com.luci.snapshot.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.server.permissions.PermissionLevel;
import org.junit.jupiter.api.Test;

class SnapshotNetworkingTest {
    @Test
    void environmentPermissionLevelsMapToMinecraftRoles() {
        assertEquals(PermissionLevel.ALL, SnapshotNetworking.permissionForLevel(0));
        assertEquals(PermissionLevel.MODERATORS, SnapshotNetworking.permissionForLevel(1));
        assertEquals(PermissionLevel.GAMEMASTERS, SnapshotNetworking.permissionForLevel(2));
        assertEquals(PermissionLevel.ADMINS, SnapshotNetworking.permissionForLevel(3));
        assertEquals(PermissionLevel.OWNERS, SnapshotNetworking.permissionForLevel(4));
    }

    @Test
    void payloadIdentifiersStayInsideTheSnapshotNamespace() {
        assertEquals("snapshot:capture_photo", CapturePhotoPayload.TYPE.id().toString());
        assertEquals("snapshot:apply_environment", ApplyEnvironmentPayload.TYPE.id().toString());
        assertEquals("snapshot:set_preset", SetPresetPayload.TYPE.id().toString());
    }
}
