package dev.kara.uuidbridge.migration.rewrite;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kara.uuidbridge.migration.UuidMapping;
import dev.kara.uuidbridge.migration.io.JsonCodecs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class SingleplayerPlayerExtractor {
    private SingleplayerPlayerExtractor() {
    }

    public static Optional<byte[]> extractGzipPlayerData(byte[] levelDat, List<UuidMapping> mappings) throws IOException {
        byte[] decompressed;
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(levelDat))) {
            decompressed = input.readAllBytes();
        } catch (IOException exception) {
            decompressed = levelDat;
        }
        Optional<byte[]> nbt = tryExtractNbt(decompressed, mappings);
        if (nbt.isPresent()) {
            return Optional.of(gzip(nbt.get()));
        }
        Optional<byte[]> json = tryExtractJson(decompressed, mappings);
        if (json.isPresent()) {
            return Optional.of(gzip(json.get()));
        }
        return Optional.empty();
    }

    private static Optional<byte[]> tryExtractNbt(byte[] decompressed, List<UuidMapping> mappings) throws IOException {
        try {
            return NbtIdentityRewriter.extractDataPlayer(decompressed, mappings);
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> tryExtractJson(byte[] decompressed, List<UuidMapping> mappings) throws IOException {
        JsonElement root;
        try {
            root = JsonParser.parseString(new String(decompressed, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (!root.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = root.getAsJsonObject();
        JsonElement data = object.get("Data");
        if (data == null || !data.isJsonObject()) {
            return Optional.empty();
        }
        JsonElement player = data.getAsJsonObject().get("Player");
        if (player == null || !player.isJsonObject()) {
            return Optional.empty();
        }
        byte[] playerJson = JsonCodecs.gson().toJson(player).getBytes(StandardCharsets.UTF_8);
        return Optional.of(JsonIdentityRewriter.rewrite(playerJson, mappings).content());
    }

    private static byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(content);
        }
        return output.toByteArray();
    }
}
