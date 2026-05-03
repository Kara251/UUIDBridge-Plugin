package dev.kara.uuidbridge.migration.rewrite;

import dev.kara.uuidbridge.migration.FileChangeResult;
import dev.kara.uuidbridge.migration.UuidMapping;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class RegionFileRewriter {
    private static final int HEADER_BYTES = 8192;
    private static final int SECTOR_BYTES = 4096;
    private static final int CHUNK_COUNT = 1024;
    private static final byte COMPRESSION_GZIP = 1;
    private static final byte COMPRESSION_ZLIB = 2;
    private static final byte COMPRESSION_NONE = 3;

    private RegionFileRewriter() {
    }

    public static FileChangeResult rewrite(byte[] region, List<UuidMapping> mappings) throws IOException {
        if (region.length < HEADER_BYTES) {
            return new FileChangeResult(0, region);
        }

        byte[] locations = Arrays.copyOfRange(region, 0, 4096);
        byte[] timestamps = Arrays.copyOfRange(region, 4096, HEADER_BYTES);
        byte[][] rewrittenChunks = new byte[CHUNK_COUNT][];
        int[] originalSectors = new int[CHUNK_COUNT];
        long replacements = 0;
        boolean changed = false;

        for (int index = 0; index < CHUNK_COUNT; index++) {
            int location = ByteBuffer.wrap(locations, index * 4, 4).getInt();
            int sectorOffset = (location >> 8) & 0xFFFFFF;
            int sectorCount = location & 0xFF;
            originalSectors[index] = sectorCount;
            if (sectorOffset == 0 || sectorCount == 0) {
                continue;
            }
            int byteOffset = sectorOffset * SECTOR_BYTES;
            int available = sectorCount * SECTOR_BYTES;
            if (byteOffset < 0 || byteOffset + 5 > region.length || byteOffset + available > region.length) {
                continue;
            }
            int chunkLength = ByteBuffer.wrap(region, byteOffset, 4).getInt();
            if (chunkLength <= 1 || chunkLength + 4 > available) {
                continue;
            }
            byte compression = region[byteOffset + 4];
            byte[] compressed = Arrays.copyOfRange(region, byteOffset + 5, byteOffset + 4 + chunkLength);
            byte[] payload = decompress(compressed, compression);
            FileChangeResult result = NbtIdentityRewriter.rewriteOrFallback(payload, mappings);
            if (!result.changed()) {
                rewrittenChunks[index] = Arrays.copyOfRange(region, byteOffset, byteOffset + 4 + chunkLength);
                continue;
            }
            byte[] recompressed = compress(result.content(), compression);
            ByteBuffer chunk = ByteBuffer.allocate(5 + recompressed.length);
            chunk.putInt(recompressed.length + 1);
            chunk.put(compression);
            chunk.put(recompressed);
            rewrittenChunks[index] = chunk.array();
            replacements += result.replacements();
            changed = true;
        }

        if (!changed) {
            return new FileChangeResult(0, region);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(region.length);
        output.write(new byte[HEADER_BYTES]);
        byte[] newLocations = new byte[4096];
        for (int index = 0; index < CHUNK_COUNT; index++) {
            byte[] chunk = rewrittenChunks[index];
            if (chunk == null) {
                continue;
            }
            int sectorOffset = output.size() / SECTOR_BYTES;
            int sectorCount = Math.max(1, (chunk.length + SECTOR_BYTES - 1) / SECTOR_BYTES);
            output.write(chunk);
            int padding = sectorCount * SECTOR_BYTES - chunk.length;
            if (padding > 0) {
                output.write(new byte[padding]);
            }
            int location = (sectorOffset << 8) | (sectorCount & 0xFF);
            ByteBuffer.wrap(newLocations, index * 4, 4).putInt(location);
        }
        byte[] rebuilt = output.toByteArray();
        System.arraycopy(newLocations, 0, rebuilt, 0, newLocations.length);
        System.arraycopy(timestamps, 0, rebuilt, 4096, timestamps.length);
        return new FileChangeResult(replacements, rebuilt);
    }

    private static byte[] decompress(byte[] compressed, byte compression) throws IOException {
        return switch (compression) {
            case COMPRESSION_GZIP -> {
                try (GZIPInputStream input = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
                    yield input.readAllBytes();
                }
            }
            case COMPRESSION_ZLIB -> {
                try (InflaterInputStream input = new InflaterInputStream(new java.io.ByteArrayInputStream(compressed))) {
                    yield input.readAllBytes();
                }
            }
            case COMPRESSION_NONE -> compressed;
            default -> throw new IOException("Unsupported region compression type: " + compression);
        };
    }

    private static byte[] compress(byte[] content, byte compression) throws IOException {
        return switch (compression) {
            case COMPRESSION_GZIP -> UuidReplacementEngine.gzip(content);
            case COMPRESSION_ZLIB -> {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
                    deflater.write(content);
                }
                yield output.toByteArray();
            }
            case COMPRESSION_NONE -> content;
            default -> throw new IOException("Unsupported region compression type: " + compression);
        };
    }
}
