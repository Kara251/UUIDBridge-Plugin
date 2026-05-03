package dev.kara.uuidbridge.migration.io;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kara.uuidbridge.migration.KnownPlayer;
import dev.kara.uuidbridge.migration.rewrite.OfflineUuid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class UserCacheReader {
    private UserCacheReader() {
    }

    public static List<KnownPlayer> read(Path userCache) throws IOException {
        if (!Files.isRegularFile(userCache)) {
            return List.of();
        }
        JsonElement tree = JsonCodecs.readTree(userCache);
        if (!tree.isJsonArray()) {
            return List.of();
        }
        List<KnownPlayer> players = new ArrayList<>();
        JsonArray array = tree.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String name = stringField(object, "name");
            String uuid = stringField(object, "uuid");
            if (name == null || uuid == null) {
                continue;
            }
            players.add(new KnownPlayer(name, Optional.of(UUID.fromString(uuid)), Optional.of(OfflineUuid.forName(name))));
        }
        return players;
    }

    private static String stringField(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        return value.getAsString();
    }
}
