package com.luci.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SnapshotResourceIntegrityTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path RESOURCES = ROOT.resolve("src/main/resources");
    private static final Path SOURCES = ROOT.resolve("src/main/java");
    private static final List<Path> RELEASE_DOCUMENTS = List.of(
        ROOT.resolve("README.md"),
        ROOT.resolve("CHANGELOG.md"),
        ROOT.resolve("MODRINTH_CHANGELOG.md"),
        ROOT.resolve("PUBLISHING.md"),
        ROOT.resolve("docs/FEATURE_STATUS.md")
    );

    @Test
    void everyJsonResourceParses() throws IOException {
        try (Stream<Path> paths = Files.walk(RESOURCES)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    assertNotNull(JsonParser.parseReader(reader), () -> "Empty JSON resource: " + path);
                }
            }
        }
    }

    @Test
    void fabricMetadataAndEntrypointsUseSnapshotIdentity() throws IOException {
        JsonObject metadata = readObject(RESOURCES.resolve("fabric.mod.json"));

        assertEquals("snapshot", metadata.get("id").getAsString());
        assertEquals("Snapshot", metadata.get("name").getAsString());
        assertEquals("LicenseRef-All-Rights-Reserved", metadata.get("license").getAsString());
        assertEquals(1, metadata.getAsJsonArray("authors").size());
        assertEquals("luci", metadata.getAsJsonArray("authors").get(0).getAsString());

        JsonObject contact = metadata.getAsJsonObject("contact");
        assertNotNull(contact);
        assertEquals("https://modrinth.com/project/snapshotphoto", contact.get("homepage").getAsString());
        assertEquals("https://github.com/topluci/Snapshot/issues", contact.get("issues").getAsString());

        JsonObject dependencies = metadata.getAsJsonObject("depends");
        assertEquals("26.2", dependencies.get("minecraft").getAsString());
        assertEquals(">=0.152.2+26.2", dependencies.get("fabric-api").getAsString());

        JsonObject entrypoints = metadata.getAsJsonObject("entrypoints");
        assertEntrypointExists(entrypoints, "main");
        assertEntrypointExists(entrypoints, "client");
        assertEntrypointExists(entrypoints, "modmenu");
    }

    @Test
    void everyDeclaredMixinClassExists() throws IOException {
        JsonObject metadata = readObject(RESOURCES.resolve("fabric.mod.json"));
        for (JsonElement declaration : metadata.getAsJsonArray("mixins")) {
            String name = declaration.isJsonPrimitive()
                ? declaration.getAsString()
                : declaration.getAsJsonObject().get("config").getAsString();
            JsonObject mixinConfig = readObject(RESOURCES.resolve(name));
            String packageName = mixinConfig.get("package").getAsString();
            for (String section : List.of("mixins", "client", "server")) {
                JsonArray classes = mixinConfig.getAsJsonArray(section);
                if (classes == null) {
                    continue;
                }
                for (JsonElement className : classes) {
                    Path source = sourcePath(packageName + "." + className.getAsString());
                    assertTrue(Files.isRegularFile(source), () -> "Missing mixin source: " + source);
                }
            }
        }
    }

    @Test
    void itemModelsTexturesAndRecipesStayConnected() throws IOException {
        Path itemDirectory = RESOURCES.resolve("assets/snapshot/items");
        try (Stream<Path> paths = Files.list(itemDirectory)) {
            for (Path itemDefinition : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                String itemName = stripExtension(itemDefinition.getFileName().toString());
                JsonObject item = readObject(itemDefinition);
                String modelId = item.getAsJsonObject("model").get("model").getAsString();
                assertEquals("snapshot:item/" + itemName, modelId);

                Path modelPath = RESOURCES.resolve("assets/snapshot/models/item/" + itemName + ".json");
                Path texturePath = RESOURCES.resolve("assets/snapshot/textures/item/" + itemName + ".png");
                assertTrue(Files.isRegularFile(modelPath), () -> "Missing item model: " + modelPath);
                assertTrue(Files.isRegularFile(texturePath), () -> "Missing item texture: " + texturePath);
            }
        }

        try (Stream<Path> paths = Files.list(RESOURCES.resolve("data/snapshot/recipe"))) {
            for (Path recipePath : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                JsonObject result = readObject(recipePath).getAsJsonObject("result");
                String itemId = result.get("id").getAsString();
                assertTrue(itemId.startsWith("snapshot:"), () -> "Unexpected recipe output: " + itemId);
                String itemName = itemId.substring("snapshot:".length());
                assertTrue(Files.isRegularFile(itemDirectory.resolve(itemName + ".json")),
                    () -> "Recipe output has no item definition: " + itemId);
            }
        }
    }

    @Test
    void pngAssetsAreReadableAndItemTexturesRemainBlocky() throws IOException {
        try (Stream<Path> paths = Files.walk(RESOURCES.resolve("assets/snapshot"))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".png")).toList()) {
                BufferedImage image = ImageIO.read(path.toFile());
                assertNotNull(image, () -> "Unreadable PNG: " + path);
                assertTrue(image.getWidth() > 0 && image.getHeight() > 0, () -> "Empty PNG: " + path);
                if (path.toString().contains("/textures/item/")) {
                    assertEquals(32, image.getWidth(), () -> "Item texture width changed: " + path);
                    assertEquals(32, image.getHeight(), () -> "Item texture height changed: " + path);
                }
            }
        }
    }

    @Test
    void oldBrandingDoesNotReturnToSourceOrResources() throws IOException {
        try (Stream<Path> paths = Stream.concat(Files.walk(SOURCES), Files.walk(RESOURCES))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (lowerName.endsWith(".png")) {
                    continue;
                }
                String contents = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                assertFalse(contents.contains("lucilens"), () -> "Old branding found in " + path);
            }
        }
    }

    @Test
    void releaseDocumentsStayPresentAndAvoidRetiredClaims() throws IOException {
        for (Path document : RELEASE_DOCUMENTS) {
            assertTrue(Files.isRegularFile(document), () -> "Missing release document: " + document);
            String contents = Files.readString(document, StandardCharsets.UTF_8);
            assertFalse(contents.isBlank(), () -> "Empty release document: " + document);
            assertFalse(contents.contains("Portra 400"), () -> "Retired film name found in " + document);
            assertFalse(contents.contains("Classic Chrome"), () -> "Retired film name found in " + document);
            assertFalse(contents.contains("Blocks, entities, and particles participate"),
                () -> "Overbroad depth claim found in " + document);
        }

        String license = Files.readString(ROOT.resolve("LICENSE"), StandardCharsets.UTF_8);
        assertTrue(license.contains("Copyright (c) 2026 luci. All rights reserved."));
        assertTrue(license.contains("No open-source license is granted"));
        assertTrue(license.contains("include unmodified official Snapshot release files in public or"));
        assertTrue(license.contains("redistribute those files solely as part of such modpacks"));
        assertFalse(license.contains("Permission is hereby granted"));

        String readme = Files.readString(ROOT.resolve("README.md"), StandardCharsets.UTF_8);
        assertTrue(readme.contains("does not send the full-resolution PNG or its local"));
        assertTrue(readme.contains("no analytics or telemetry"));
        assertTrue(readme.contains("singleplayer-only") || readme.contains("singleplayer"));
        assertTrue(readme.contains("Copyright (c) 2026 luci"));
        assertTrue(readme.replaceAll("\\s+", " ").contains("All Rights Reserved/No License"));
        assertTrue(readme.contains("included in public or private modpacks"));
        assertFalse(readme.contains("Snapshot is MIT licensed"));
        assertFalse(readme.contains("original assets are MIT licensed"));

        String fontLicense = Files.readString(ROOT.resolve("FONT_LICENSE.md"), StandardCharsets.UTF_8);
        assertTrue(fontLicense.startsWith("Copyright 2014-2021 Adobe"));

        assertTrue(Files.isRegularFile(ROOT.resolve(".github/workflows/build.yml")));
        assertTrue(Files.isRegularFile(ROOT.resolve(".github/workflows/release.yml")));
        assertTrue(Files.isRegularFile(ROOT.resolve("scripts/package-release.sh")));
    }

    @Test
    void serverCapturePayloadDoesNotExposeLocalFileLocations() throws IOException {
        String payload = Files.readString(
            SOURCES.resolve("com/luci/snapshot/network/CapturePhotoPayload.java"),
            StandardCharsets.UTF_8
        );
        assertTrue(payload.contains("boolean pngExported"));
        assertFalse(payload.contains("imageUri"));
        assertFalse(payload.contains("toUri("));
    }

    private static void assertEntrypointExists(JsonObject entrypoints, String name) {
        JsonArray declarations = entrypoints.getAsJsonArray(name);
        assertNotNull(declarations, () -> "Missing " + name + " entrypoint");
        for (JsonElement declaration : declarations) {
            Path source = sourcePath(declaration.getAsString());
            assertTrue(Files.isRegularFile(source), () -> "Missing entrypoint source: " + source);
        }
    }

    private static JsonObject readObject(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Path sourcePath(String className) {
        return SOURCES.resolve(className.replace('.', '/') + ".java");
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
