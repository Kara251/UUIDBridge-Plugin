package dev.kara.uuidbridge.migration.io;

import java.nio.file.Path;

public record UuidBridgePaths(
    Path gameDir,
    Path worldDir,
    Path controlDir,
    Path plansDir,
    Path reportsDir,
    Path backupsDir,
    Path pendingFile
) {
    public static UuidBridgePaths create(Path gameDir, Path worldDir) {
        Path controlDir = gameDir.resolve("uuidbridge");
        return new UuidBridgePaths(
            gameDir,
            worldDir,
            controlDir,
            controlDir.resolve("plans"),
            controlDir.resolve("reports"),
            controlDir.resolve("backups"),
            controlDir.resolve("pending.json")
        );
    }

    public Path planPath(String planId) {
        return plansDir.resolve(planId + ".json");
    }

    public Path reportPath(String planId) {
        return reportsDir.resolve(planId + ".json");
    }

    public Path backupPath(String planId) {
        return backupsDir.resolve(planId);
    }
}
