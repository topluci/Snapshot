package com.luci.snapshot.client.photo;

import com.google.gson.JsonObject;
import java.util.Locale;

enum PhotoAlbum {
    ALL("ALL"),
    UNSORTED("UNSORTED"),
    PORTFOLIO("PORTFOLIO"),
    LANDSCAPES("LANDSCAPES"),
    WILDLIFE("WILDLIFE"),
    ASTRO("ASTRO"),
    WEATHER("WEATHER");

    private final String label;

    PhotoAlbum(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    PhotoAlbum nextFilter() {
        PhotoAlbum[] albums = values();
        return albums[(ordinal() + 1) % albums.length];
    }

    PhotoAlbum nextAssignable() {
        PhotoAlbum[] albums = values();
        int next = ordinal() + 1;
        if (next >= albums.length || albums[next] == ALL) {
            next = UNSORTED.ordinal();
        }
        return albums[next];
    }

    static PhotoAlbum fromMetadata(JsonObject metadata) {
        if (metadata.has("album")) {
            String stored = metadata.get("album").getAsString().trim().toUpperCase(Locale.ROOT);
            for (PhotoAlbum album : values()) {
                if (album != ALL && (album.name().equals(stored) || album.label.equals(stored))) {
                    return album;
                }
            }
        }
        String objective = metadata.has("objective") ? metadata.get("objective").getAsString().toLowerCase(Locale.ROOT) : "";
        if (objective.contains("celestial")) {
            return ASTRO;
        }
        if (objective.contains("subject")) {
            return WILDLIFE;
        }
        if (objective.contains("weather")) {
            return WEATHER;
        }
        if (objective.contains("landscape")) {
            return LANDSCAPES;
        }
        return UNSORTED;
    }
}
