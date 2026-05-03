package dev.kara.uuidbridge.migration.rewrite;

import dev.kara.uuidbridge.migration.FileChangeResult;
import dev.kara.uuidbridge.migration.MappingSource;
import dev.kara.uuidbridge.migration.MigrationDirection;
import dev.kara.uuidbridge.migration.UuidMapping;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidReplacementEngineTest {
    private static final UUID ONLINE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OFFLINE = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UuidMapping MAPPING = new UuidMapping(
        "Alice",
        ONLINE,
        OFFLINE,
        MigrationDirection.ONLINE_TO_OFFLINE,
        MappingSource.MAPPING_FILE
    );

    @Test
    void rewritesStringUuidWithoutTouchingUnrelatedUuid() {
        String source = "{\"Owner\":\"" + ONLINE + "\",\"Other\":\"00000000-0000-0000-0000-000000000000\"}";
        FileChangeResult result = UuidReplacementEngine.rewritePlain(source.getBytes(StandardCharsets.UTF_8), List.of(MAPPING));
        String rewritten = new String(result.content(), StandardCharsets.UTF_8);
        assertEquals(1, result.replacements());
        assertTrue(rewritten.contains(OFFLINE.toString()));
        assertTrue(rewritten.contains("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void rewritesTouhouLittleMaidOwnerUuidFixture() {
        String source = """
            {
              "id": "touhou_little_maid:maid",
              "owner_uuid": "11111111-2222-3333-4444-555555555555",
              "MaidTask": "touhou_little_maid:idle"
            }
            """;
        FileChangeResult result = UuidReplacementEngine.rewritePlain(source.getBytes(StandardCharsets.UTF_8), List.of(MAPPING));
        String rewritten = new String(result.content(), StandardCharsets.UTF_8);
        assertEquals(1, result.replacements());
        assertTrue(rewritten.contains("\"owner_uuid\": \"" + OFFLINE + "\""));
    }

    @Test
    void rewritesBinaryLongPair() {
        byte[] source = UuidBinaryCodec.asLongPair(ONLINE);
        FileChangeResult result = UuidReplacementEngine.rewritePlain(source, List.of(MAPPING));
        assertEquals(1, result.replacements());
        assertArrayEquals(UuidBinaryCodec.asLongPair(OFFLINE), result.content());
    }

    @Test
    void rewritesBinaryIntArrayShape() {
        ByteBuffer source = ByteBuffer.wrap(UuidBinaryCodec.asIntArray(ONLINE));
        ByteBuffer rewritten = ByteBuffer.wrap(UuidReplacementEngine.rewritePlain(source.array(), List.of(MAPPING)).content());
        assertEquals(OFFLINE.getMostSignificantBits() >>> 32, Integer.toUnsignedLong(rewritten.getInt()));
    }
}
