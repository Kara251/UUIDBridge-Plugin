package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.io.JsonCodecs;
import dev.kara.uuidbridge.migration.io.PathSecurity;
import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

public final class MigrationService {
    private final MigrationPlanner planner = new MigrationPlanner();
    private final MigrationExecutor executor = new MigrationExecutor();

    public ScanResult scan(
        UuidBridgePaths paths,
        MigrationDirection direction,
        Optional<String> mapping
    ) throws IOException {
        return scan(paths, direction, mapping, Optional.empty(), Optional.empty());
    }

    public ScanResult scan(
        UuidBridgePaths paths,
        MigrationDirection direction,
        Optional<String> mapping,
        Optional<String> targets,
        Optional<String> singleplayerName
    ) throws IOException {
        return scan(paths, direction, mapping, targets, singleplayerName, false);
    }

    public ScanResult scan(
        UuidBridgePaths paths,
        MigrationDirection direction,
        Optional<String> mapping,
        Optional<String> targets,
        Optional<String> singleplayerName,
        boolean pluginTargetsEnabled
    ) throws IOException {
        Optional<Path> mappingFile = resolveMapping(paths, mapping);
        Optional<Path> targetsFile = resolveControlFile(paths, targets);
        return planner.scan(paths, direction, mappingFile, targetsFile, singleplayerName, pluginTargetsEnabled);
    }

    public MigrationPlan createPlan(
        UuidBridgePaths paths,
        MigrationDirection direction,
        Optional<String> mapping
    ) throws IOException {
        return createPlan(paths, direction, mapping, Optional.empty(), Optional.empty());
    }

    public MigrationPlan createPlan(
        UuidBridgePaths paths,
        MigrationDirection direction,
        Optional<String> mapping,
        Optional<String> targets,
        Optional<String> singleplayerName
    ) throws IOException {
        return createPlan(paths, direction, mapping, targets, singleplayerName, false);
    }

    public MigrationPlan createPlan(
        UuidBridgePaths paths,
        MigrationDirection direction,
        Optional<String> mapping,
        Optional<String> targets,
        Optional<String> singleplayerName,
        boolean pluginTargetsEnabled
    ) throws IOException {
        Optional<Path> mappingFile = resolveMapping(paths, mapping);
        Optional<Path> targetsFile = resolveControlFile(paths, targets);
        MigrationPlan plan = planner.createPlan(paths, direction, mappingFile, targetsFile, singleplayerName,
            pluginTargetsEnabled);
        JsonCodecs.write(paths.planPath(plan.id()), plan);
        return plan;
    }

    public void markPending(UuidBridgePaths paths, String planId) throws IOException {
        markPendingApply(paths, planId, "command");
    }

    public void markPendingApply(UuidBridgePaths paths, String planId, String confirmedBy) throws IOException {
        Path planPath = paths.planPath(planId);
        if (!Files.isRegularFile(planPath)) {
            throw new IOException("Plan not found: " + planId);
        }
        MigrationPlan plan = JsonCodecs.read(planPath, MigrationPlan.class);
        if (!plan.canApply()) {
            throw new IOException("Plan cannot be applied until conflicts and missing mappings are fixed: " + planId);
        }
        PendingMigration pending = new PendingMigration(PendingAction.APPLY, planId, Instant.now().toString(), confirmedBy);
        JsonCodecs.write(paths.pendingFile(), pending);
    }

    public void markPendingRollback(UuidBridgePaths paths, String planId, String confirmedBy) throws IOException {
        Path planPath = paths.planPath(planId);
        if (!Files.isRegularFile(planPath)) {
            throw new IOException("Plan not found: " + planId);
        }
        if (!Files.isRegularFile(paths.backupPath(planId).resolve("manifest.json"))) {
            throw new IOException("Backup manifest not found for rollback: " + planId);
        }
        PendingMigration pending = new PendingMigration(PendingAction.ROLLBACK, planId, Instant.now().toString(), confirmedBy);
        JsonCodecs.write(paths.pendingFile(), pending);
    }

    public Optional<String> pendingPlan(UuidBridgePaths paths) throws IOException {
        return pendingMigration(paths).map(PendingMigration::planId);
    }

    public Optional<PendingMigration> pendingMigration(UuidBridgePaths paths) throws IOException {
        if (!Files.isRegularFile(paths.pendingFile())) {
            return Optional.empty();
        }
        PendingMigration pending = JsonCodecs.read(paths.pendingFile(), PendingMigration.class);
        if (pending.action() == null) {
            return Optional.of(new PendingMigration(PendingAction.APPLY, pending.planId(), "", "legacy"));
        }
        return Optional.of(pending);
    }

    public Optional<Path> latestReport(UuidBridgePaths paths) throws IOException {
        if (!Files.isDirectory(paths.reportsDir())) {
            return Optional.empty();
        }
        try (var stream = Files.list(paths.reportsDir())) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                .max(Comparator.comparing(path -> path.toFile().lastModified()));
        }
    }

    public boolean hasLock(UuidBridgePaths paths) {
        return Files.exists(paths.controlDir().resolve("migration.lock"));
    }

    public Optional<MigrationLock> lock(UuidBridgePaths paths) throws IOException {
        Path lock = paths.controlDir().resolve("migration.lock");
        if (!Files.isRegularFile(lock)) {
            return Optional.empty();
        }
        return Optional.of(JsonCodecs.read(lock, MigrationLock.class));
    }

    public Optional<BackupManifest> backupManifest(UuidBridgePaths paths, String planId) throws IOException {
        Path manifest = paths.backupPath(planId).resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return Optional.empty();
        }
        return Optional.of(JsonCodecs.read(manifest, BackupManifest.class));
    }

    public Optional<MigrationPlan> plan(UuidBridgePaths paths, String planId) throws IOException {
        Path planPath = paths.planPath(planId);
        if (!Files.isRegularFile(planPath)) {
            return Optional.empty();
        }
        return Optional.of(JsonCodecs.read(planPath, MigrationPlan.class));
    }

    public boolean cancel(UuidBridgePaths paths, String planId) throws IOException {
        if (hasLock(paths)) {
            throw new IOException("Cannot cancel while migration.lock is present; inspect status and resolve recovery first.");
        }
        Optional<PendingMigration> pending = pendingMigration(paths);
        if (pending.isPresent() && pending.get().planId().equals(planId)) {
            Files.deleteIfExists(paths.pendingFile());
            return true;
        }
        return false;
    }

    public MigrationReport executePending(UuidBridgePaths paths) throws IOException {
        PendingMigration pending = pendingMigration(paths)
            .orElseThrow(() -> new IOException("No pending migration plan."));
        MigrationPlan plan = JsonCodecs.read(paths.planPath(pending.planId()), MigrationPlan.class);
        MigrationReport report = switch (pending.action()) {
            case APPLY -> executor.executeApply(paths, plan);
            case ROLLBACK -> executor.executeRollback(paths, plan);
        };
        if (!report.successful()) {
            throw new IOException("UUIDBridge " + pending.action().name().toLowerCase(java.util.Locale.ROOT)
                + " completed with errors. See " + reportPath(paths, report));
        }
        Files.deleteIfExists(paths.pendingFile());
        return report;
    }

    private static Optional<Path> resolveMapping(UuidBridgePaths paths, Optional<String> mapping) throws IOException {
        if (mapping.isEmpty() || mapping.get().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PathSecurity.resolveInside(paths.gameDir(), mapping.get()));
    }

    private static Optional<Path> resolveControlFile(UuidBridgePaths paths, Optional<String> file) throws IOException {
        if (file.isEmpty() || file.get().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PathSecurity.resolveInside(paths.gameDir(), file.get()));
    }

    private static Path reportPath(UuidBridgePaths paths, MigrationReport report) {
        if (report.action() == PendingAction.ROLLBACK) {
            return paths.reportPath(report.planId() + "-rollback");
        }
        return paths.reportPath(report.planId());
    }
}
