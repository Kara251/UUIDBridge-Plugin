package dev.kara.uuidbridge.plugin;

import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class PluginPaths {
    private PluginPaths() {
    }

    static UuidBridgePaths create(Path serverDir) throws IOException {
        Path root = serverDir.toAbsolutePath().normalize();
        String levelName = levelName(root);
        Path worldDir = root.resolve(levelName).normalize();
        if (!worldDir.startsWith(root)) {
            throw new IOException("level-name escapes server directory: " + levelName);
        }
        return UuidBridgePaths.create(root, worldDir);
    }

    private static String levelName(Path serverDir) throws IOException {
        Path propertiesFile = serverDir.resolve("server.properties");
        if (!Files.isRegularFile(propertiesFile)) {
            return "world";
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        }
        String levelName = properties.getProperty("level-name", "world").trim();
        return levelName.isEmpty() ? "world" : levelName;
    }
}
