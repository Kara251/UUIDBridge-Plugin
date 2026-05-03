package dev.kara.uuidbridge.migration.rewrite;

import dev.kara.uuidbridge.migration.FileChangeResult;
import dev.kara.uuidbridge.migration.UuidMapping;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public final class UuidReplacementEngine {
    private UuidReplacementEngine() {
    }

    public static FileChangeResult rewritePlain(byte[] content, List<UuidMapping> mappings) {
        byte[] current = content;
        long replacements = 0;
        for (UuidMapping mapping : mappings) {
            ReplaceResult text = replaceAll(current, mapping.fromUuid().toString().getBytes(StandardCharsets.UTF_8),
                mapping.toUuid().toString().getBytes(StandardCharsets.UTF_8));
            current = text.content();
            replacements += text.replacements();

            ReplaceResult noDash = replaceAll(current, undashed(mapping.fromUuid()).getBytes(StandardCharsets.UTF_8),
                undashed(mapping.toUuid()).getBytes(StandardCharsets.UTF_8));
            current = noDash.content();
            replacements += noDash.replacements();

            ReplaceResult longPair = replaceAll(current, UuidBinaryCodec.asLongPair(mapping.fromUuid()),
                UuidBinaryCodec.asLongPair(mapping.toUuid()));
            current = longPair.content();
            replacements += longPair.replacements();

            ReplaceResult intArray = replaceAll(current, UuidBinaryCodec.asIntArray(mapping.fromUuid()),
                UuidBinaryCodec.asIntArray(mapping.toUuid()));
            current = intArray.content();
            replacements += intArray.replacements();
        }
        return new FileChangeResult(replacements, current);
    }

    public static FileChangeResult rewriteGzip(byte[] content, List<UuidMapping> mappings) throws IOException {
        byte[] decompressed;
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(content))) {
            decompressed = input.readAllBytes();
        }
        FileChangeResult result = rewritePlain(decompressed, mappings);
        if (!result.changed()) {
            return new FileChangeResult(0, content);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(result.content());
        }
        return new FileChangeResult(result.replacements(), output.toByteArray());
    }

    public static byte[] inflateZlib(byte[] content) throws IOException {
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(content))) {
            return input.readAllBytes();
        }
    }

    public static byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(content);
        }
        return output.toByteArray();
    }

    public static String undashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    static ReplaceResult replaceAll(byte[] content, byte[] from, byte[] to) {
        if (from.length != to.length) {
            throw new IllegalArgumentException("UUID replacement patterns must be equal length");
        }
        if (content.length < from.length) {
            return new ReplaceResult(0, content);
        }
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i <= content.length - from.length; i++) {
            if (matchesAt(content, from, i)) {
                matches.add(i);
                i += from.length - 1;
            }
        }
        if (matches.isEmpty()) {
            return new ReplaceResult(0, content);
        }
        byte[] copy = content.clone();
        for (int offset : matches) {
            System.arraycopy(to, 0, copy, offset, to.length);
        }
        return new ReplaceResult(matches.size(), copy);
    }

    private static boolean matchesAt(byte[] content, byte[] pattern, int offset) {
        for (int i = 0; i < pattern.length; i++) {
            if (content[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    record ReplaceResult(long replacements, byte[] content) {
    }
}
