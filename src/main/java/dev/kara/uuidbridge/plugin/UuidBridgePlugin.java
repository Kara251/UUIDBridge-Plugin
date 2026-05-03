package dev.kara.uuidbridge.plugin;

import dev.kara.uuidbridge.migration.PendingMigrationRunner;
import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import java.io.IOException;
import java.nio.file.Path;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class UuidBridgePlugin extends JavaPlugin {
    private UuidBridgePaths paths;

    @Override
    public void onLoad() {
        try {
            paths = PluginPaths.create(serverRoot());
            PendingMigrationRunner.runIfPresent(paths, getLogger());
        } catch (IOException | RuntimeException exception) {
            getLogger().severe("UUIDBridge failed during startup: " + message(exception));
            throw new IllegalStateException("UUIDBridge startup failed", exception);
        }
    }

    @Override
    public void onEnable() {
        PluginCommand command = getCommand("uuidbridge");
        if (command == null) {
            getLogger().severe("UUIDBridge command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        UuidBridgeCommand executor = new UuidBridgeCommand(this::paths);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private UuidBridgePaths paths() {
        if (paths == null) {
            throw new IllegalStateException("UUIDBridge paths were not initialized.");
        }
        return paths;
    }

    private Path serverRoot() {
        return getServer().getWorldContainer().toPath();
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
