package dev.kara.uuidbridge.migration.io;

import dev.kara.uuidbridge.migration.MigrationDirection;
import dev.kara.uuidbridge.migration.UuidMapping;
import dev.kara.uuidbridge.migration.rewrite.OfflineUuid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MappingFileParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesCsvAndDerivesOfflineUuid() throws Exception {
        Path mapping = tempDir.resolve("mapping.csv");
        Files.writeString(mapping, "name,onlineUuid,offlineUuid\nAlice,11111111-2222-3333-4444-555555555555,\n");
        List<UuidMapping> mappings = MappingFileParser.parse(mapping, MigrationDirection.ONLINE_TO_OFFLINE);
        assertEquals(1, mappings.size());
        assertEquals(OfflineUuid.forName("Alice"), mappings.getFirst().offlineUuid());
    }

    @Test
    void parsesJsonMappings() throws Exception {
        Path mapping = tempDir.resolve("mapping.json");
        Files.writeString(mapping, """
            [
              {
                "name": "Bob",
                "onlineUuid": "22222222-2222-3333-4444-555555555555",
                "offlineUuid": "bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee"
              }
            ]
            """);
        List<UuidMapping> mappings = MappingFileParser.parse(mapping, MigrationDirection.OFFLINE_TO_ONLINE);
        assertEquals(UUID.fromString("22222222-2222-3333-4444-555555555555"), mappings.getFirst().onlineUuid());
        assertEquals(UUID.fromString("bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee"), mappings.getFirst().offlineUuid());
    }
}
