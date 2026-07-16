package com.luci.snapshot.client.photo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luci.snapshot.SnapshotInit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class PhotographyJournal {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private PhotographyJournal() {
    }

    public static synchronized void record(Minecraft client, JsonObject capture) {
        Path path = client.gameDirectory.toPath().resolve("screenshots/snapshot/journal.json");
        JsonObject journal = read(path);
        JsonArray captures = array(journal, "captures");
        captures.add(capture.deepCopy());
        addUnique(array(journal, "discovered_biomes"), string(capture, "biome"));
        addUnique(array(journal, "weather_conditions"), string(capture, "weather"));
        addUnique(array(journal, "photographed_subjects"), string(capture, "subject"));
        addUnique(array(journal, "celestial_conditions"), string(capture, "celestial_condition"));
        journal.addProperty("total_captures", captures.size());
        journal.addProperty("best_composition_score", bestScore(captures));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, JSON.toJson(journal), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not update the photography journal.", exception);
        }
    }

    public static synchronized JournalView load(Minecraft client) {
        Path path = client.gameDirectory.toPath().resolve("screenshots/snapshot/journal.json");
        JsonObject journal = read(path);
        JsonArray captures = array(journal, "captures");
        List<JournalEntry> recent = new ArrayList<>();
        for (int index = captures.size() - 1; index >= 0; index--) {
            JsonElement element = captures.get(index);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject capture = element.getAsJsonObject();
            recent.add(new JournalEntry(
                string(capture, "title"),
                string(capture, "objective"),
                capture.has("composition_score") ? capture.get("composition_score").getAsInt() : 0,
                string(capture, "biome"),
                string(capture, "subject")
            ));
        }
        return new JournalView(
            journal.has("total_captures") ? journal.get("total_captures").getAsInt() : captures.size(),
            journal.has("best_composition_score") ? journal.get("best_composition_score").getAsInt() : bestScore(captures),
            strings(array(journal, "discovered_biomes")),
            strings(array(journal, "weather_conditions")),
            strings(array(journal, "photographed_subjects")),
            strings(array(journal, "celestial_conditions")),
            List.copyOf(recent)
        );
    }

    private static JsonObject read(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) {
                    return parsed.getAsJsonObject();
                }
            } catch (RuntimeException | IOException exception) {
                SnapshotInit.LOGGER.warn("[Snapshot] Could not read the photography journal; rebuilding it.", exception);
            }
        }
        return new JsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonArray()) {
            return object.getAsJsonArray(key);
        }
        JsonArray value = new JsonArray();
        object.add(key, value);
        return value;
    }

    private static void addUnique(JsonArray values, String candidate) {
        if (candidate.isBlank() || "none".equals(candidate)) {
            return;
        }
        for (JsonElement value : values) {
            if (candidate.equals(value.getAsString())) {
                return;
            }
        }
        values.add(candidate);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static int bestScore(JsonArray captures) {
        int best = 0;
        for (JsonElement capture : captures) {
            if (capture.isJsonObject() && capture.getAsJsonObject().has("composition_score")) {
                best = Math.max(best, capture.getAsJsonObject().get("composition_score").getAsInt());
            }
        }
        return best;
    }

    private static List<String> strings(JsonArray values) {
        List<String> result = new ArrayList<>();
        for (JsonElement value : values) {
            if (value.isJsonPrimitive()) {
                result.add(value.getAsString());
            }
        }
        return List.copyOf(result);
    }

    public record JournalView(int totalCaptures, int bestScore, List<String> biomes, List<String> weather,
                              List<String> subjects, List<String> celestial, List<JournalEntry> recent) {
    }

    public record JournalEntry(String title, String objective, int score, String biome, String subject) {
    }
}
