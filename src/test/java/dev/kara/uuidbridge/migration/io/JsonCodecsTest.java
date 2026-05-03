package dev.kara.uuidbridge.migration.io;

import dev.kara.uuidbridge.migration.PendingAction;
import dev.kara.uuidbridge.migration.PendingMigration;
import dev.kara.uuidbridge.migration.MigrationDirection;
import dev.kara.uuidbridge.migration.MigrationPlan;
import dev.kara.uuidbridge.migration.MappingSource;
import dev.kara.uuidbridge.migration.UuidMapping;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCodecsTest {
    @TempDir
    Path tempDir;

    @Test
    void failedAtomicWriteLeavesExistingJsonReadable() throws Exception {
        Assumptions.assumeTrue(!"root".equals(System.getProperty("user.name")));
        Assumptions.assumeTrue(tempDir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path directory = tempDir.resolve("control");
        Files.createDirectories(directory);
        Path pending = directory.resolve("pending.json");
        PendingMigration oldValue = new PendingMigration(PendingAction.APPLY, "old", "before", "test");
        JsonCodecs.write(pending, oldValue);

        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(directory);
        try {
            Files.setPosixFilePermissions(directory, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE
            ));
            assertThrows(java.io.IOException.class, () ->
                JsonCodecs.write(pending, new PendingMigration(PendingAction.ROLLBACK, "new", "after", "test")));
        } finally {
            Files.setPosixFilePermissions(directory, originalPermissions);
        }

        PendingMigration readBack = JsonCodecs.read(pending, PendingMigration.class);
        assertEquals("old", readBack.planId());
        assertEquals(PendingAction.APPLY, readBack.action());
    }

    @Test
    void preservesPluginTargetsEnabledInPlanJson() throws Exception {
        Path planFile = tempDir.resolve("plans/plan.json");
        MigrationPlan plan = new MigrationPlan(
            "plan",
            MigrationDirection.ONLINE_TO_OFFLINE,
            List.of(new UuidMapping("Alice", UUID.randomUUID(), UUID.randomUUID(),
                MigrationDirection.ONLINE_TO_OFFLINE, MappingSource.DERIVED)),
            List.of("server"),
            List.of(),
            List.of(),
            List.of(),
            "now",
            null,
            null,
            "",
            true
        );

        JsonCodecs.write(planFile, plan);
        MigrationPlan readBack = JsonCodecs.read(planFile, MigrationPlan.class);

        assertTrue(readBack.pluginTargetsEnabled());
    }
}
