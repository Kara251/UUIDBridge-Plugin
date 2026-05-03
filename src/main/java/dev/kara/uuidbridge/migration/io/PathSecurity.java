package dev.kara.uuidbridge.migration.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PathSecurity {
    private PathSecurity() {
    }

    public static Path resolveInside(Path root, String userPath) throws IOException {
        Path base = ensureDirectory(root).toRealPath();
        Path resolved = base.resolve(userPath).normalize();
        if (Files.exists(resolved)) {
            resolved = resolved.toRealPath();
        } else {
            resolved = resolved.toAbsolutePath().normalize();
        }
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Path escapes server directory: " + userPath);
        }
        return resolved;
    }

    public static Path ensureDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        return path;
    }
}
