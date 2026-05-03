package dev.kara.uuidbridge.migration.adapter;

import dev.kara.uuidbridge.migration.FileChangeResult;
import dev.kara.uuidbridge.migration.UuidMapping;
import dev.kara.uuidbridge.migration.rewrite.JsonIdentityRewriter;
import dev.kara.uuidbridge.migration.rewrite.NbtIdentityRewriter;
import dev.kara.uuidbridge.migration.rewrite.RegionFileRewriter;
import dev.kara.uuidbridge.migration.rewrite.UuidReplacementEngine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class DataAdapters {
    public static final long DEFAULT_BINARY_SIZE_LIMIT = 64L * 1024L * 1024L;
    public static final String JSON = "json";
    public static final String NBT_GZIP = "nbt-gzip";
    public static final String NBT_PLAIN = "nbt-plain";
    public static final String REGION = "region";
    public static final String YAML_TEXT = "yaml-text";
    public static final String BINARY = "binary";
    public static final String UNSUPPORTED = "unsupported";

    private static final Map<String, DataAdapter> BY_ID = Map.of(
        JSON, new JsonAdapter(),
        NBT_GZIP, new GzipNbtAdapter(),
        NBT_PLAIN, new PlainNbtAdapter(),
        REGION, new RegionAdapter(),
        YAML_TEXT, new YamlTextAdapter(),
        BINARY, new BinaryAdapter(),
        UNSUPPORTED, new UnsupportedAdapter()
    );

    private DataAdapters() {
    }

    public static Optional<DataAdapter> byId(String id) {
        return Optional.ofNullable(BY_ID.get(normalize(id)));
    }

    public static DataAdapter forFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mca")) {
            return BY_ID.get(REGION);
        }
        if (name.endsWith(".json")) {
            return BY_ID.get(JSON);
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            return BY_ID.get(YAML_TEXT);
        }
        if (name.endsWith(".dat") || name.endsWith(".dat_old")) {
            return BY_ID.get(NBT_GZIP);
        }
        return BY_ID.get(BINARY);
    }

    public static Optional<String> normalizeFormat(String format) {
        String normalized = normalize(format);
        if (normalized.equals("auto")) {
            return Optional.of("auto");
        }
        if (normalized.equals("nbt")) {
            return Optional.of(NBT_GZIP);
        }
        if (normalized.equals("gzip-nbt")) {
            return Optional.of(NBT_GZIP);
        }
        if (normalized.equals("plain-nbt")) {
            return Optional.of(NBT_PLAIN);
        }
        if (normalized.equals("yaml") || normalized.equals("yml")) {
            return Optional.of(YAML_TEXT);
        }
        if (normalized.equals(BINARY)) {
            return Optional.empty();
        }
        if (BY_ID.containsKey(normalized)) {
            return Optional.of(normalized);
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class JsonAdapter implements DataAdapter {
        @Override
        public String id() {
            return JSON;
        }

        @Override
        public String format() {
            return JSON;
        }

        @Override
        public String risk() {
            return "semantic";
        }

        @Override
        public boolean supports(Path file) {
            return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
        }

        @Override
        public FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) throws IOException {
            return JsonIdentityRewriter.rewrite(content, mappings);
        }
    }

    private static final class GzipNbtAdapter implements DataAdapter {
        @Override
        public String id() {
            return NBT_GZIP;
        }

        @Override
        public String format() {
            return NBT_GZIP;
        }

        @Override
        public String risk() {
            return "semantic-with-binary-fallback";
        }

        @Override
        public boolean supports(Path file) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".dat") || name.endsWith(".dat_old");
        }

        @Override
        public FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) throws IOException {
            byte[] decompressed;
            try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(content))) {
                decompressed = input.readAllBytes();
            } catch (IOException exception) {
                return NbtIdentityRewriter.rewriteOrFallback(content, mappings);
            }
            FileChangeResult result = NbtIdentityRewriter.rewriteOrFallback(decompressed, mappings);
            if (!result.changed()) {
                return new FileChangeResult(0, content);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(result.content());
            }
            return new FileChangeResult(result.replacements(), output.toByteArray());
        }
    }

    private static final class PlainNbtAdapter implements DataAdapter {
        @Override
        public String id() {
            return NBT_PLAIN;
        }

        @Override
        public String format() {
            return NBT_PLAIN;
        }

        @Override
        public String risk() {
            return "semantic-with-binary-fallback";
        }

        @Override
        public boolean supports(Path file) {
            return true;
        }

        @Override
        public FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) {
            return NbtIdentityRewriter.rewriteOrFallback(content, mappings);
        }
    }

    private static final class RegionAdapter implements DataAdapter {
        @Override
        public String id() {
            return REGION;
        }

        @Override
        public String format() {
            return REGION;
        }

        @Override
        public String risk() {
            return "chunked-region";
        }

        @Override
        public boolean supports(Path file) {
            return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mca");
        }

        @Override
        public FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) throws IOException {
            return RegionFileRewriter.rewrite(content, mappings);
        }
    }

    private static final class BinaryAdapter implements DataAdapter {
        @Override
        public String id() {
            return BINARY;
        }

        @Override
        public String format() {
            return BINARY;
        }

        @Override
        public String risk() {
            return "binary-fallback";
        }

        @Override
        public boolean supports(Path file) {
            return true;
        }

        @Override
        public FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) {
            return UuidReplacementEngine.rewritePlain(content, mappings);
        }
    }

    private static final class YamlTextAdapter implements DataAdapter {
        @Override
        public String id() {
            return YAML_TEXT;
        }

        @Override
        public String format() {
            return YAML_TEXT;
        }

        @Override
        public String risk() {
            return "exact-text";
        }

        @Override
        public boolean supports(Path file) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".yml") || name.endsWith(".yaml");
        }

        @Override
        public FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) {
            return UuidReplacementEngine.rewriteText(content, mappings);
        }
    }

    private static final class UnsupportedAdapter implements DataAdapter {
        @Override
        public String id() {
            return UNSUPPORTED;
        }

        @Override
        public String format() {
            return UNSUPPORTED;
        }

        @Override
        public String risk() {
            return "unsupported-report-only";
        }

        @Override
        public boolean supports(Path file) {
            return true;
        }

        @Override
        public FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) {
            return new FileChangeResult(0, content);
        }
    }
}
