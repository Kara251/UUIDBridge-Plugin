package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.io.JsonCodecs;
import dev.kara.uuidbridge.migration.io.SafeFileWriter;
import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class BackupManager {
    private final UuidBridgePaths paths;
    private final String planId;
    private final Path backupRoot;
    private final List<BackupEntry> entries = new ArrayList<>();
    private final String createdAt = Instant.now().toString();

    public BackupManager(UuidBridgePaths paths, String planId) {
        this.paths = paths;
        this.planId = planId;
        this.backupRoot = paths.backupPath(planId);
    }

    public Path root() {
        return backupRoot;
    }

    public List<BackupEntry> entries() {
        return List.copyOf(entries);
    }

    public BackupEntry backup(Path file, Path currentPath, String operation) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Cannot back up missing file: " + file);
        }
        Path relative = relative(file);
        Path target = backupRoot.resolve(relative);
        if (Files.exists(target)) {
            BackupEntry entry = addEntry(file, target, currentPath, operation);
            writeManifest(false);
            return entry;
        }
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
        BackupEntry entry = addEntry(file, target, currentPath, operation);
        writeManifest(false);
        return entry;
    }

    public void writeManifest(boolean complete) throws IOException {
        JsonCodecs.write(backupRoot.resolve("manifest.json"), new BackupManifest(
            planId,
            createdAt,
            Instant.now().toString(),
            complete,
            List.copyOf(entries)
        ));
    }

    public static BackupManifest readManifest(UuidBridgePaths paths, String planId) throws IOException {
        return JsonCodecs.read(paths.backupPath(planId).resolve("manifest.json"), BackupManifest.class);
    }

    public static Path resolveEntryPath(UuidBridgePaths paths, String label) throws IOException {
        if (label.startsWith("world:")) {
            Path root = paths.worldDir().toAbsolutePath().normalize();
            Path resolved = root.resolve(label.substring("world:".length())).normalize();
            if (!resolved.startsWith(root)) {
                throw new IOException("Manifest path escapes world directory: " + label);
            }
            return resolved;
        }
        if (label.startsWith("game:")) {
            Path root = paths.gameDir().toAbsolutePath().normalize();
            Path resolved = root.resolve(label.substring("game:".length())).normalize();
            if (!resolved.startsWith(root)) {
                throw new IOException("Manifest path escapes server directory: " + label);
            }
            return resolved;
        }
        throw new IOException("Unsupported backup manifest path: " + label);
    }

    public static Path resolveBackupPath(UuidBridgePaths paths, String planId, BackupEntry entry) throws IOException {
        Path root = paths.backupPath(planId).toAbsolutePath().normalize();
        Path backup = root.resolve(entry.backupPath()).normalize();
        if (!backup.startsWith(root)) {
            throw new IOException("Backup path escapes backup directory: " + entry.backupPath());
        }
        return backup;
    }

    public static void backupCurrentForRollback(UuidBridgePaths paths, String planId, Path current) throws IOException {
        if (!Files.isRegularFile(current)) {
            return;
        }
        Path root = paths.backupPath(planId).resolve("rollback-current");
        Path relative = rollbackRelative(paths, current);
        Path target = root.resolve(relative);
        int suffix = 1;
        while (Files.exists(target)) {
            target = root.resolve(relative + "." + suffix);
            suffix++;
        }
        SafeFileWriter.copyAtomic(current, target);
    }

    public static String sha256For(Path file) throws IOException {
        return sha256(file);
    }

    private BackupEntry addEntry(Path original, Path backup, Path currentPath, String operation) throws IOException {
        String originalLabel = MigrationPlanner.label(paths, original);
        String currentLabel = MigrationPlanner.label(paths, currentPath);
        String backupLabel = backupRoot.relativize(backup).toString();
        for (int index = 0; index < entries.size(); index++) {
            BackupEntry existing = entries.get(index);
            if (!existing.originalPath().equals(originalLabel)) {
                continue;
            }
            if (existing.currentPath().equals(currentLabel) && existing.operation().equals(operation)) {
                return existing;
            }
            BackupEntry updated = new BackupEntry(
                existing.originalPath(),
                existing.backupPath(),
                currentLabel,
                mergeOperation(existing.operation(), operation),
                existing.originalSize(),
                existing.originalSha256(),
                existing.backupSize(),
                existing.backupSha256()
            );
            entries.set(index, updated);
            return updated;
        }
        BackupEntry entry = new BackupEntry(
            originalLabel,
            backupLabel,
            currentLabel,
            operation,
            Files.size(original),
            sha256(original),
            Files.size(backup),
            sha256(backup)
        );
        entries.add(entry);
        return entry;
    }

    private static String mergeOperation(String existing, String next) {
        if (existing.equals(next)) {
            return existing;
        }
        if (existing.contains(next)) {
            return existing;
        }
        return existing + "+" + next;
    }

    private Path relative(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        Path game = paths.gameDir().toAbsolutePath().normalize();
        Path world = paths.worldDir().toAbsolutePath().normalize();
        if (normalized.startsWith(world)) {
            return Path.of("world").resolve(world.relativize(normalized));
        }
        if (normalized.startsWith(game)) {
            return Path.of("game").resolve(game.relativize(normalized));
        }
        return Path.of("external").resolve(file.getFileName());
    }

    private static Path rollbackRelative(UuidBridgePaths paths, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        Path game = paths.gameDir().toAbsolutePath().normalize();
        Path world = paths.worldDir().toAbsolutePath().normalize();
        if (normalized.startsWith(world)) {
            return Path.of("world").resolve(world.relativize(normalized));
        }
        if (normalized.startsWith(game)) {
            return Path.of("game").resolve(game.relativize(normalized));
        }
        return Path.of("external").resolve(file.getFileName());
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }
}
