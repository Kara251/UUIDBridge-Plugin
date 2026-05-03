package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.adapter.DataAdapters;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PluginTargetPresets {
    private static final List<Preset> PRESETS = List.of(
        new Preset("essentialsx-userdata", "plugins/essentials/userdata/", DataAdapters.YAML_TEXT, true),
        new Preset("luckperms-yaml-users", "plugins/luckperms/yaml-storage/users/", DataAdapters.YAML_TEXT, true),
        new Preset("luckperms-json-users", "plugins/luckperms/json-storage/users/", DataAdapters.JSON, true),
        new Preset("residence-save", "plugins/residence/save/", DataAdapters.YAML_TEXT, false),
        new Preset("worldguard-worlds", "plugins/worldguard/worlds/", DataAdapters.YAML_TEXT, false),
        new Preset("economy-yaml", "plugins/economy/", DataAdapters.YAML_TEXT, false)
    );

    private PluginTargetPresets() {
    }

    public static Optional<Target> targetFor(Path gameDir, Path file) {
        String relative = relative(gameDir, file);
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (Preset preset : PRESETS) {
            if (!relative.startsWith(preset.pathPrefix())) {
                continue;
            }
            if (supportsAdapter(fileName, preset.adapter())) {
                return Optional.of(new Target(preset.name(), preset.adapter(), preset.renameUuidFiles()));
            }
        }
        return Optional.empty();
    }

    public static boolean isRenameTarget(Path gameDir, Path file) {
        return targetFor(gameDir, file)
            .filter(Target::renameUuidFiles)
            .filter(target -> uuidFileStem(file))
            .isPresent();
    }

    private static boolean supportsAdapter(String fileName, String adapter) {
        return switch (adapter) {
            case DataAdapters.JSON -> fileName.endsWith(".json");
            case DataAdapters.YAML_TEXT -> fileName.endsWith(".yml") || fileName.endsWith(".yaml");
            default -> false;
        };
    }

    private static boolean uuidFileStem(Path file) {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        return isUuidLike(name.substring(0, dot));
    }

    private static boolean isUuidLike(String value) {
        if (value.length() == 36) {
            for (int index = 0; index < value.length(); index++) {
                char c = value.charAt(index);
                if (index == 8 || index == 13 || index == 18 || index == 23) {
                    if (c != '-') {
                        return false;
                    }
                } else if (!isHex(c)) {
                    return false;
                }
            }
            return true;
        }
        if (value.length() != 32) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!isHex(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9')
            || (c >= 'a' && c <= 'f')
            || (c >= 'A' && c <= 'F');
    }

    private static String relative(Path gameDir, Path file) {
        Path root;
        Path normalized;
        try {
            root = gameDir.toRealPath();
            normalized = file.toRealPath();
        } catch (IOException exception) {
            root = gameDir.toAbsolutePath().normalize();
            normalized = file.toAbsolutePath().normalize();
        }
        if (!normalized.startsWith(root)) {
            return "";
        }
        return root.relativize(normalized).toString()
            .replace('\\', '/')
            .toLowerCase(Locale.ROOT);
    }

    public record Target(String name, String adapter, boolean renameUuidFiles) {
    }

    private record Preset(String name, String pathPrefix, String adapter, boolean renameUuidFiles) {
    }
}
