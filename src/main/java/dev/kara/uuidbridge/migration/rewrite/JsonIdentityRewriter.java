package dev.kara.uuidbridge.migration.rewrite;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.kara.uuidbridge.migration.FileChangeResult;
import dev.kara.uuidbridge.migration.UuidMapping;
import dev.kara.uuidbridge.migration.io.JsonCodecs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class JsonIdentityRewriter {
    private JsonIdentityRewriter() {
    }

    public static FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) throws IOException {
        JsonElement root;
        try {
            root = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            return UuidReplacementEngine.rewritePlain(content, mappings);
        }
        Counter counter = new Counter();
        JsonElement rewritten = rewriteElement(root, mappings, counter);
        if (counter.value == 0) {
            return new FileChangeResult(0, content);
        }
        byte[] serialized = (JsonCodecs.gson().toJson(rewritten) + System.lineSeparator())
            .getBytes(StandardCharsets.UTF_8);
        return new FileChangeResult(counter.value, serialized);
    }

    private static JsonElement rewriteElement(JsonElement element, List<UuidMapping> mappings, Counter counter) {
        if (element == null || element instanceof JsonNull) {
            return JsonNull.INSTANCE;
        }
        if (element.isJsonObject()) {
            JsonObject copy = new JsonObject();
            for (var entry : element.getAsJsonObject().entrySet()) {
                copy.add(entry.getKey(), rewriteElement(entry.getValue(), mappings, counter));
            }
            return copy;
        }
        if (element.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                copy.add(rewriteElement(child, mappings, counter));
            }
            return copy;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                String replacement = replaceExact(primitive.getAsString(), mappings);
                if (!replacement.equals(primitive.getAsString())) {
                    counter.value++;
                    return new JsonPrimitive(replacement);
                }
            }
        }
        return element.deepCopy();
    }

    static String replaceExact(String value, List<UuidMapping> mappings) {
        for (UuidMapping mapping : mappings) {
            UUID from = mapping.fromUuid();
            UUID to = mapping.toUuid();
            if (from.toString().equalsIgnoreCase(value)) {
                return to.toString();
            }
            if (UuidReplacementEngine.undashed(from).equalsIgnoreCase(value)) {
                return UuidReplacementEngine.undashed(to);
            }
        }
        return value;
    }

    private static final class Counter {
        long value;
    }
}
