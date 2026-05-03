package dev.kara.uuidbridge.migration.rewrite;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfflineUuidTest {
    @Test
    void matchesKnownOfflineUuidAlgorithm() {
        UUID expected = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(expected, OfflineUuid.forName("Steve"));
    }

    @Test
    void preservesMinecraftNameCaseSensitivity() {
        UUID upper = OfflineUuid.forName("Steve");
        UUID lower = OfflineUuid.forName("steve");
        org.junit.jupiter.api.Assertions.assertNotEquals(upper, lower);
    }
}
