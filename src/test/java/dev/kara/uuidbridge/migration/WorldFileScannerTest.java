package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.adapter.DataAdapters;
import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFileScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsTargetsFileWithGlobExcludeAndReportsLargeBinarySkip() throws Exception {
        Path gameDir = tempDir.resolve("server");
        Path worldDir = gameDir.resolve("world");
        Files.createDirectories(gameDir.resolve("config/claims"));
        Files.createDirectories(gameDir.resolve("config/backups"));
        Files.createDirectories(worldDir);
        Files.writeString(gameDir.resolve("config/claims/alice.json"), "{\"owner\":\"x\"}");
        Files.writeString(gameDir.resolve("config/backups/old.json"), "{\"owner\":\"x\"}");
        Path large = gameDir.resolve("config/claims/blob.bin");
        try (var channel = Files.newByteChannel(large, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(DataAdapters.DEFAULT_BINARY_SIZE_LIMIT);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
        Path targets = gameDir.resolve("uuidbridge/targets.json");
        Files.createDirectories(targets.getParent());
        Files.writeString(targets, """
            {
              "include": [
                {"path": "config/claims/*.json", "format": "json"}
              ],
              "exclude": ["config/backups/**"]
            }
            """);

        UuidBridgePaths paths = UuidBridgePaths.create(gameDir, worldDir);
        var files = WorldFileScanner.discoverTargets(paths, Optional.of(targets));
        CoverageReport coverage = WorldFileScanner.coverage(paths, List.of(
            new DiscoveredFile(large, DataAdapters.BINARY, DataAdapters.BINARY, "test", false)
        ), 0);

        assertTrue(files.stream().anyMatch(file -> file.path().endsWith("alice.json")));
        assertTrue(files.stream().noneMatch(file -> file.path().endsWith("old.json")));
        assertEquals(1, coverage.skippedFiles());
    }

    @Test
    void rejectsTargetPathTraversal() throws Exception {
        Path gameDir = tempDir.resolve("server");
        Path worldDir = gameDir.resolve("world");
        Files.createDirectories(gameDir.resolve("uuidbridge"));
        Path targets = gameDir.resolve("uuidbridge/targets.json");
        Files.writeString(targets, """
            {
              "include": [
                {"path": "../outside.json", "format": "json"}
              ]
            }
            """);

        UuidBridgePaths paths = UuidBridgePaths.create(gameDir, worldDir);

        assertThrows(SecurityException.class, () -> WorldFileScanner.discoverTargets(paths, Optional.of(targets)));
    }

    @Test
    void discoversVanillaWorldIdentityStorageAcrossDimensions() throws Exception {
        Path gameDir = tempDir.resolve("vanilla-server");
        Path worldDir = gameDir.resolve("world");
        UuidBridgePaths paths = UuidBridgePaths.create(gameDir, worldDir);

        touch(gameDir.resolve("whitelist.json"));
        touch(gameDir.resolve("ops.json"));
        touch(gameDir.resolve("banned-players.json"));
        touch(gameDir.resolve("usercache.json"));
        touch(worldDir.resolve("level.dat"));
        touch(worldDir.resolve("level.dat_old"));
        touch(worldDir.resolve("playerdata/11111111-2222-3333-4444-555555555555.dat"));
        touch(worldDir.resolve("advancements/11111111-2222-3333-4444-555555555555.json"));
        touch(worldDir.resolve("stats/11111111-2222-3333-4444-555555555555.json"));
        touch(worldDir.resolve("data/scoreboard.dat"));
        touch(worldDir.resolve("data/raids.dat"));
        touch(worldDir.resolve("data/map_0.dat"));
        touch(worldDir.resolve("data/command_storage_uuidbridge.dat"));
        touch(worldDir.resolve("region/r.0.0.mca"));
        touch(worldDir.resolve("entities/r.0.0.mca"));
        touch(worldDir.resolve("poi/r.0.0.mca"));
        touch(worldDir.resolve("DIM-1/region/r.0.0.mca"));
        touch(worldDir.resolve("DIM1/entities/r.0.0.mca"));
        touch(worldDir.resolve("uuidbridge/backups/ignored.dat"));
        touch(worldDir.resolve("logs/ignored.dat"));

        Set<String> labels = WorldFileScanner.discoverTargets(paths, Optional.empty()).stream()
            .map(file -> MigrationPlanner.label(paths, file.path()).replace('\\', '/'))
            .collect(Collectors.toSet());

        assertTrue(labels.contains("game:whitelist.json"));
        assertTrue(labels.contains("game:ops.json"));
        assertTrue(labels.contains("game:banned-players.json"));
        assertTrue(labels.contains("game:usercache.json"));
        assertTrue(labels.contains("world:level.dat"));
        assertTrue(labels.contains("world:level.dat_old"));
        assertTrue(labels.contains("world:playerdata/11111111-2222-3333-4444-555555555555.dat"));
        assertTrue(labels.contains("world:advancements/11111111-2222-3333-4444-555555555555.json"));
        assertTrue(labels.contains("world:stats/11111111-2222-3333-4444-555555555555.json"));
        assertTrue(labels.contains("world:data/scoreboard.dat"));
        assertTrue(labels.contains("world:data/raids.dat"));
        assertTrue(labels.contains("world:data/map_0.dat"));
        assertTrue(labels.contains("world:data/command_storage_uuidbridge.dat"));
        assertTrue(labels.contains("world:region/r.0.0.mca"));
        assertTrue(labels.contains("world:entities/r.0.0.mca"));
        assertTrue(labels.contains("world:poi/r.0.0.mca"));
        assertTrue(labels.contains("world:DIM-1/region/r.0.0.mca"));
        assertTrue(labels.contains("world:DIM1/entities/r.0.0.mca"));
        assertTrue(labels.stream().noneMatch(label -> label.contains("uuidbridge/backups")));
        assertTrue(labels.stream().noneMatch(label -> label.contains("logs/ignored")));
    }

    @Test
    void pluginTargetsAreOptInAndConservative() throws Exception {
        Path gameDir = tempDir.resolve("plugin-server");
        Path worldDir = gameDir.resolve("world");
        UuidBridgePaths paths = UuidBridgePaths.create(gameDir, worldDir);

        touch(gameDir.resolve("plugins/Claims/data/owners.json"));
        touch(gameDir.resolve("plugins/Claims/data/owner.dat"));
        touch(gameDir.resolve("plugins/Claims/data/r.0.0.mca"));
        touch(gameDir.resolve("plugins/Claims/cache/ignored.json"));
        touch(gameDir.resolve("plugins/Essentials/userdata/11111111-2222-3333-4444-555555555555.yml"));
        touch(gameDir.resolve("plugins/Residence/Save/residences.yml"));
        touch(gameDir.resolve("plugins/Claims/data/config.yml"));
        touch(gameDir.resolve("plugins/Claims/data/store.sqlite"));
        touch(gameDir.resolve("plugins/Claims/data/plugin.jar"));

        Set<String> defaultLabels = WorldFileScanner.discoverTargets(paths, Optional.empty()).stream()
            .map(file -> MigrationPlanner.label(paths, file.path()).replace('\\', '/'))
            .collect(Collectors.toSet());
        Set<String> pluginLabels = WorldFileScanner.discoverTargets(paths, Optional.empty(), true).stream()
            .map(file -> MigrationPlanner.label(paths, file.path()).replace('\\', '/'))
            .collect(Collectors.toSet());

        assertTrue(defaultLabels.stream().noneMatch(label -> label.startsWith("game:plugins/")));
        assertTrue(pluginLabels.contains("game:plugins/Claims/data/owners.json"));
        assertTrue(pluginLabels.contains("game:plugins/Claims/data/owner.dat"));
        assertTrue(pluginLabels.contains("game:plugins/Claims/data/r.0.0.mca"));
        assertTrue(pluginLabels.contains("game:plugins/Essentials/userdata/11111111-2222-3333-4444-555555555555.yml"));
        assertTrue(pluginLabels.contains("game:plugins/Residence/Save/residences.yml"));
        assertTrue(pluginLabels.contains("game:plugins/Claims/data/config.yml"));
        assertTrue(pluginLabels.contains("game:plugins/Claims/data/store.sqlite"));
        assertTrue(pluginLabels.contains("game:plugins/Claims/cache/ignored.json"));
        assertTrue(pluginLabels.stream().noneMatch(label -> label.endsWith(".jar")));

        var unsupported = WorldFileScanner.discoverTargets(paths, Optional.empty(), true).stream()
            .filter(file -> file.adapter().equals(DataAdapters.UNSUPPORTED))
            .map(file -> MigrationPlanner.label(paths, file.path()).replace('\\', '/'))
            .collect(Collectors.toSet());
        assertTrue(unsupported.contains("game:plugins/Claims/data/config.yml"));
        assertTrue(unsupported.contains("game:plugins/Claims/data/store.sqlite"));
        assertTrue(unsupported.contains("game:plugins/Claims/cache/ignored.json"));

        var renameFiles = WorldFileScanner.pluginUuidFiles(paths).stream()
            .map(file -> MigrationPlanner.label(paths, file).replace('\\', '/'))
            .collect(Collectors.toSet());
        assertTrue(renameFiles.contains("game:plugins/Essentials/userdata/11111111-2222-3333-4444-555555555555.yml"));
    }

    private static void touch(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[] {0});
    }
}
