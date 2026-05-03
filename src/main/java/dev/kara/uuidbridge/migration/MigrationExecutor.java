package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.io.JsonCodecs;
import dev.kara.uuidbridge.migration.io.SafeFileWriter;
import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import dev.kara.uuidbridge.migration.rewrite.SingleplayerPlayerExtractor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public final class MigrationExecutor {
    public MigrationReport execute(UuidBridgePaths paths, MigrationPlan plan) throws IOException {
        return executeApply(paths, plan);
    }

    public MigrationReport executeApply(UuidBridgePaths paths, MigrationPlan plan) throws IOException {
        if (!plan.canApply()) {
            throw new IOException("Plan cannot be applied because it has conflicts, missing mappings, or no mappings.");
        }

        Path lock = writeLock(paths, PendingAction.APPLY, plan.id());

        List<PlannedChange> changed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> applyErrors = new ArrayList<>();
        List<String> rollbackErrors = new ArrayList<>();
        BackupManager backups = new BackupManager(paths, plan.id());
        String finalState = "APPLY_FAILED";

        try {
            if (applyRewrites(paths, plan, backups, changed, skipped, applyErrors)
                && applySingleplayerPlayerCopy(paths, plan, backups, changed, skipped, applyErrors)
                && applyRenames(paths, plan, backups, changed, skipped, applyErrors)) {
                finalState = "APPLIED";
                backups.writeManifest(true);
                Files.deleteIfExists(lock);
                MigrationReport report = report(plan, PendingAction.APPLY, changed, skipped,
                    applyErrors, rollbackErrors, backups.root(), finalState);
                JsonCodecs.write(paths.reportPath(plan.id()), report);
                return report;
            }
        } catch (IOException | RuntimeException exception) {
            applyErrors.add("fatal: " + message(exception));
        }

        RestoreResult rollback = restoreEntries(paths, plan.id(), backups.entries(), false, "auto-rollback");
        changed.addAll(rollback.restored());
        rollbackErrors.addAll(rollback.errors());
        if (rollbackErrors.isEmpty()) {
            finalState = "ROLLED_BACK";
            Files.deleteIfExists(lock);
        } else {
            finalState = "ROLLBACK_FAILED";
        }
        backups.writeManifest(false);
        MigrationReport report = report(plan, PendingAction.APPLY, changed, skipped,
            applyErrors, rollbackErrors, backups.root(), finalState);
        JsonCodecs.write(paths.reportPath(plan.id()), report);
        return report;
    }

    public MigrationReport executeRollback(UuidBridgePaths paths, MigrationPlan plan) throws IOException {
        Path lock = writeLock(paths, PendingAction.ROLLBACK, plan.id());
        BackupManifest manifest;
        try {
            manifest = BackupManager.readManifest(paths, plan.id());
        } catch (IOException exception) {
            MigrationReport report = report(plan, PendingAction.ROLLBACK, List.of(), List.of(), List.of(),
                List.of("manifest: " + exception.getMessage()), paths.backupPath(plan.id()), "ROLLBACK_FAILED");
            JsonCodecs.write(paths.reportPath(plan.id() + "-rollback"), report);
            return report;
        }
        RestoreResult rollback = restoreEntries(paths, plan.id(), manifest.files(), true, "rollback");
        List<String> applyErrors = List.of();
        List<String> skipped = List.of();
        String finalState = rollback.errors().isEmpty() ? "ROLLED_BACK" : "ROLLBACK_FAILED";
        if (rollback.errors().isEmpty()) {
            Files.deleteIfExists(lock);
        }
        MigrationReport report = report(plan, PendingAction.ROLLBACK, rollback.restored(), skipped,
            applyErrors, rollback.errors(), paths.backupPath(plan.id()), finalState);
        JsonCodecs.write(paths.reportPath(plan.id() + "-rollback"), report);
        return report;
    }

    private static Path writeLock(UuidBridgePaths paths, PendingAction action, String planId) throws IOException {
        Path lock = paths.controlDir().resolve("migration.lock");
        Files.createDirectories(paths.controlDir());
        if (Files.exists(lock)) {
            throw new IOException("Another UUIDBridge migration appears to be running: " + lock);
        }
        JsonCodecs.write(lock, new MigrationLock(action, planId, Instant.now().toString()));
        return lock;
    }

    private static boolean applyRewrites(
        UuidBridgePaths paths,
        MigrationPlan plan,
        BackupManager backups,
        List<PlannedChange> changed,
        List<String> skipped,
        List<String> errors
    ) throws IOException {
        Optional<Path> targetsFile = plan.targetsFile().isBlank()
            ? Optional.empty()
            : Optional.of(Path.of(plan.targetsFile()));
        for (DiscoveredFile file : WorldFileScanner.discoverTargets(paths, targetsFile, plan.pluginTargetsEnabled())) {
            try {
                if (FileMigrator.shouldSkipLargeUnknown(file)) {
                    skipped.add(MigrationPlanner.label(paths, file.path()) + ": skipped large unknown binary file");
                    continue;
                }
                FileChangeResult preview = FileMigrator.preview(file, plan.mappings());
                if (!preview.changed()) {
                    continue;
                }
                backups.backup(file.path(), file.path(), "rewrite");
                long replacements = FileMigrator.rewrite(file, plan.mappings());
                if (replacements > 0) {
                    changed.add(new PlannedChange(MigrationPlanner.label(paths, file.path()), replacements,
                        "rewrite:" + file.adapter()));
                }
            } catch (IOException exception) {
                errors.add(MigrationPlanner.label(paths, file.path()) + ": " + exception.getMessage());
                return false;
            }
        }
        return true;
    }

    private static boolean applySingleplayerPlayerCopy(
        UuidBridgePaths paths,
        MigrationPlan plan,
        BackupManager backups,
        List<PlannedChange> changed,
        List<String> skipped,
        List<String> errors
    ) throws IOException {
        SingleplayerPlayerCopy copy = plan.singleplayerPlayerCopy();
        if (copy == null) {
            return true;
        }
        try {
            Path source = BackupManager.resolveEntryPath(paths, copy.sourcePath());
            Path target = BackupManager.resolveEntryPath(paths, copy.targetPath());
            if (Files.exists(target)) {
                String message = copy.targetPath() + " target already exists.";
                skipped.add(message);
                errors.add(message);
                return false;
            }
            Optional<byte[]> playerData = SingleplayerPlayerExtractor.extractGzipPlayerData(
                Files.readAllBytes(source), plan.mappings());
            if (playerData.isEmpty()) {
                errors.add(copy.sourcePath() + ": Data.Player tag was not found.");
                return false;
            }
            backups.backup(source, target, "singleplayer-player-copy");
            SafeFileWriter.writeAtomic(target, playerData.get());
            changed.add(new PlannedChange(copy.sourcePath() + " -> " + copy.targetPath(), 1,
                "singleplayer-player-copy"));
            return true;
        } catch (IOException exception) {
            errors.add(copy.sourcePath() + ": " + exception.getMessage());
            return false;
        }
    }

    private static boolean applyRenames(
        UuidBridgePaths paths,
        MigrationPlan plan,
        BackupManager backups,
        List<PlannedChange> changed,
        List<String> skipped,
        List<String> errors
    ) throws IOException {
        for (Path file : WorldFileScanner.playerUuidFiles(paths)) {
            String newName = MigrationPlanner.renamedPlayerFile(file, plan.mappings());
            if (newName == null) {
                continue;
            }
            Path target = file.resolveSibling(newName);
            if (Files.exists(target)) {
                String message = MigrationPlanner.label(paths, file) + " target already exists: " + target.getFileName();
                skipped.add(message);
                errors.add(message);
                return false;
            }
            try {
                backups.backup(file, target, "rename");
                SafeFileWriter.moveAtomic(file, target);
                changed.add(new PlannedChange(MigrationPlanner.label(paths, file) + " -> " + target.getFileName(), 1, "rename"));
            } catch (IOException exception) {
                errors.add(MigrationPlanner.label(paths, file) + ": " + exception.getMessage());
                return false;
            }
        }
        return true;
    }

    private static RestoreResult restoreEntries(
        UuidBridgePaths paths,
        String planId,
        List<BackupEntry> entries,
        boolean protectCurrent,
        String operation
    ) {
        List<PlannedChange> restored = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (BackupEntry entry : entries) {
            try {
                Path backup = BackupManager.resolveBackupPath(paths, planId, entry);
                if (!Files.isRegularFile(backup)) {
                    throw new IOException("Backup file is missing: " + entry.backupPath());
                }
                if (Files.size(backup) != entry.backupSize()) {
                    throw new IOException("Backup size mismatch: " + entry.backupPath());
                }
                String backupSha = BackupManager.sha256For(backup);
                if (!backupSha.equals(entry.backupSha256())) {
                    throw new IOException("Backup SHA-256 mismatch: " + entry.backupPath());
                }

                Path original = BackupManager.resolveEntryPath(paths, entry.originalPath());
                Path current = entry.currentPath() == null || entry.currentPath().isBlank()
                    ? original
                    : BackupManager.resolveEntryPath(paths, entry.currentPath());
                if (protectCurrent) {
                    BackupManager.backupCurrentForRollback(paths, planId, current);
                    if (!current.equals(original)) {
                        BackupManager.backupCurrentForRollback(paths, planId, original);
                    }
                }
                if (!current.equals(original)) {
                    Files.deleteIfExists(current);
                }
                SafeFileWriter.copyAtomic(backup, original);
                String restoredSha = BackupManager.sha256For(original);
                if (!restoredSha.equals(entry.backupSha256())) {
                    throw new IOException("Restored SHA-256 mismatch: " + entry.originalPath());
                }
                restored.add(new PlannedChange(entry.originalPath(), 1, operation));
            } catch (IOException exception) {
                errors.add(entry.originalPath() + ": " + exception.getMessage());
            }
        }
        return new RestoreResult(List.copyOf(restored), List.copyOf(errors));
    }

    private static MigrationReport report(
        MigrationPlan plan,
        PendingAction action,
        List<PlannedChange> changed,
        List<String> skipped,
        List<String> applyErrors,
        List<String> rollbackErrors,
        Path backupRoot,
        String finalState
    ) throws IOException {
        return new MigrationReport(
            plan.id(),
            action,
            plan.direction(),
            List.copyOf(changed),
            List.copyOf(skipped),
            List.copyOf(applyErrors),
            List.copyOf(rollbackErrors),
            backupRoot.toString(),
            checksum(changed, skipped, applyErrors, rollbackErrors, finalState),
            Instant.now().toString(),
            finalState
        );
    }

    private static String checksum(
        List<PlannedChange> changed,
        List<String> skipped,
        List<String> applyErrors,
        List<String> rollbackErrors,
        String finalState
    ) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (PlannedChange change : changed) {
                digest.update(change.toString().getBytes(StandardCharsets.UTF_8));
            }
            for (String value : skipped) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
            }
            for (String value : applyErrors) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
            }
            for (String value : rollbackErrors) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
            }
            digest.update(finalState.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private record RestoreResult(List<PlannedChange> restored, List<String> errors) {
    }
}
