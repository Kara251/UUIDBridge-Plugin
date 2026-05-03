package dev.kara.uuidbridge.plugin;

import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginPathsTest {
    @TempDir
    Path tempDir;

    @Test
    void readsLevelNameFromServerProperties() throws Exception {
        Files.writeString(tempDir.resolve("server.properties"), "level-name=survival\n");

        UuidBridgePaths paths = PluginPaths.create(tempDir);

        assertEquals(tempDir.resolve("survival").toAbsolutePath().normalize(), paths.worldDir());
    }

    @Test
    void defaultsWorldWhenServerPropertiesIsMissing() throws Exception {
        UuidBridgePaths paths = PluginPaths.create(tempDir);

        assertEquals(tempDir.resolve("world").toAbsolutePath().normalize(), paths.worldDir());
    }

    @Test
    void rejectsEscapingLevelName() throws Exception {
        Files.writeString(tempDir.resolve("server.properties"), "level-name=../outside\n");

        assertThrows(Exception.class, () -> PluginPaths.create(tempDir));
    }
}
