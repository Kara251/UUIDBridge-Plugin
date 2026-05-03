package dev.kara.uuidbridge.migration.io;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kara.uuidbridge.migration.MappingSource;
import dev.kara.uuidbridge.migration.MigrationDirection;
import dev.kara.uuidbridge.migration.UuidMapping;
import dev.kara.uuidbridge.migration.rewrite.OfflineUuid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MappingFileParser {
    private MappingFileParser() {
    }

    public static List<UuidMapping> parse(Path path, MigrationDirection direction) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".json")) {
            return parseJson(path, direction);
        }
        return parseCsv(path, direction);
    }

    private static List<UuidMapping> parseJson(Path path, MigrationDirection direction) throws IOException {
        JsonElement tree = JsonCodecs.readTree(path);
        JsonArray array = tree.isJsonArray() ? tree.getAsJsonArray() : tree.getAsJsonObject().getAsJsonArray("mappings");
        List<UuidMapping> mappings = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String name = requiredString(object, "name");
            UUID online = UUID.fromString(requiredString(object, "onlineUuid"));
            UUID offline = object.has("offlineUuid")
                ? UUID.fromString(requiredString(object, "offlineUuid"))
                : OfflineUuid.forName(name);
            mappings.add(new UuidMapping(name, online, offline, direction, MappingSource.MAPPING_FILE));
        }
        return mappings;
    }

    private static List<UuidMapping> parseCsv(Path path, MigrationDirection direction) throws IOException {
        List<UuidMapping> mappings = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("name,")) {
                continue;
            }
            String[] parts = trimmed.split(",", -1);
            if (parts.length < 2) {
                throw new IOException("Invalid mapping row: " + line);
            }
            String name = parts[0].trim();
            UUID online = UUID.fromString(parts[1].trim());
            UUID offline = parts.length >= 3 && !parts[2].isBlank()
                ? UUID.fromString(parts[2].trim())
                : OfflineUuid.forName(name);
            mappings.add(new UuidMapping(name, online, offline, direction, MappingSource.MAPPING_FILE));
        }
        return mappings;
    }

    private static String requiredString(JsonObject object, String field) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            throw new IOException("Missing mapping field: " + field);
        }
        return value.getAsString();
    }
}
