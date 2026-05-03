package dev.kara.uuidbridge.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kara.uuidbridge.migration.adapter.DataAdapters;
import dev.kara.uuidbridge.migration.io.JsonCodecs;
import dev.kara.uuidbridge.migration.io.PathSecurity;
import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class WorldFileScanner {
    private static final Set<String> SERVER_JSON_FILES = Set.of(
        "whitelist.json",
        "ops.json",
        "banned-players.json",
        "usercache.json"
    );

    private WorldFileScanner() {
    }

    public static List<Path> discover(UuidBridgePaths paths) throws IOException {
        return discoverTargets(paths, Optional.empty()).stream()
            .filter(file -> !isSkippedLargeUnknown(file))
            .map(DiscoveredFile::path)
            .distinct()
            .toList();
    }

    public static List<DiscoveredFile> discoverTargets(UuidBridgePaths paths, Optional<Path> targetsFile) throws IOException {
        return discoverTargets(paths, targetsFile, false);
    }

    public static List<DiscoveredFile> discoverTargets(
        UuidBridgePaths paths,
        Optional<Path> targetsFile,
        boolean pluginTargetsEnabled
    ) throws IOException {
        Map<Path, DiscoveredFile> result = new LinkedHashMap<>();
        for (String file : SERVER_JSON_FILES) {
            Path path = paths.gameDir().resolve(file);
            if (Files.isRegularFile(path)) {
                add(result, path, DataAdapters.JSON, "server-list", false);
            }
        }
        addIfExists(result, paths.worldDir().resolve("level.dat"), DataAdapters.NBT_GZIP, "level");
        addDirectoryFiles(result, paths.worldDir().resolve("data"), Set.of(".dat"), "saved-data");
        addDirectoryFiles(result, paths.worldDir().resolve("playerdata"), Set.of(".dat"), "playerdata");
        addDirectoryFiles(result, paths.worldDir().resolve("advancements"), Set.of(".json"), "advancements");
        addDirectoryFiles(result, paths.worldDir().resolve("stats"), Set.of(".json"), "stats");
        addRecursiveWorldFiles(result, paths.worldDir());
        if (pluginTargetsEnabled) {
            addRecursivePluginFiles(result, paths);
        }
        addExtraTargets(result, paths, targetsFile);
        return List.copyOf(result.values());
    }

    public static List<Path> playerUuidFiles(UuidBridgePaths paths) throws IOException {
        List<Path> result = new ArrayList<>();
        addDirectoryFiles(result, paths.worldDir().resolve("playerdata"), Set.of(".dat"));
        addDirectoryFiles(result, paths.worldDir().resolve("advancements"), Set.of(".json"));
        addDirectoryFiles(result, paths.worldDir().resolve("stats"), Set.of(".json"));
        return result;
    }

    public static List<Path> pluginUuidFiles(UuidBridgePaths paths) throws IOException {
        Path pluginsDir = paths.gameDir().resolve("plugins");
        if (!Files.isDirectory(pluginsDir)) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        Path root = canonicalRoot(pluginsDir);
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> !isPluginExcluded(root, path))
                .map(path -> displayPath(pluginsDir, root, path))
                .filter(path -> PluginTargetPresets.isRenameTarget(paths.gameDir(), path))
                .forEach(result::add);
        }
        return List.copyOf(result);
    }

    private static void addDirectoryFiles(List<Path> result, Path directory, Set<String> extensions) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> extensions.stream().anyMatch(ext -> path.getFileName().toString().endsWith(ext)))
                .forEach(result::add);
        }
    }

    public static CoverageReport coverage(UuidBridgePaths paths, List<DiscoveredFile> files, long estimatedReplacements) throws IOException {
        List<CoverageTarget> targets = new ArrayList<>();
        long skipped = 0;
        for (DiscoveredFile file : files) {
            String skipReason = skipReason(file);
            if (skipReason != null) {
                skipped++;
            }
            targets.add(new CoverageTarget(
                MigrationPlanner.label(paths, file.path()),
                file.adapter(),
                file.format(),
                Files.isRegularFile(file.path()) ? Files.size(file.path()) : 0,
                file.source(),
                skipReason
            ));
        }
        return new CoverageReport(List.copyOf(targets), targets.size(), skipped, estimatedReplacements);
    }

    private static void addRecursiveWorldFiles(Map<Path, DiscoveredFile> result, Path worldDir) throws IOException {
        if (!Files.isDirectory(worldDir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(worldDir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> !isDefaultExcluded(worldDir, path))
                .filter(WorldFileScanner::isWorldDataFile)
                .forEach(path -> add(result, path, DataAdapters.forFile(path).id(), "world-recursive", false));
        }
    }

    private static boolean isWorldDataFile(Path path) {
        String normalized = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return normalized.endsWith(".mca")
            || normalized.endsWith(".dat")
            || normalized.endsWith(".dat_old");
    }

    private static void addDirectoryFiles(
        Map<Path, DiscoveredFile> result,
        Path directory,
        Set<String> extensions,
        String source
    ) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> extensions.stream().anyMatch(ext -> path.getFileName().toString().endsWith(ext)))
                .forEach(path -> add(result, path, DataAdapters.forFile(path).id(), source, false));
        }
    }

    private static void addIfExists(Map<Path, DiscoveredFile> result, Path path, String adapter, String source) {
        if (Files.isRegularFile(path)) {
            add(result, path, adapter, source, false);
        }
    }

    private static void add(
        Map<Path, DiscoveredFile> result,
        Path path,
        String adapter,
        String source,
        boolean explicit
    ) {
        Path normalized = path.toAbsolutePath().normalize();
        DataAdapters.byId(adapter).ifPresentOrElse(
            dataAdapter -> result.putIfAbsent(normalized,
                new DiscoveredFile(normalized, dataAdapter.id(), dataAdapter.format(), source, explicit)),
            () -> result.putIfAbsent(normalized,
                new DiscoveredFile(normalized, DataAdapters.BINARY, DataAdapters.BINARY, source, explicit))
        );
    }

    private static boolean isDefaultExcluded(Path root, Path path) {
        Path normalized;
        Path base;
        try {
            normalized = path.toRealPath();
            base = canonicalRoot(root);
        } catch (IOException exception) {
            normalized = path.toAbsolutePath().normalize();
            base = root.toAbsolutePath().normalize();
        }
        for (String name : List.of("uuidbridge", "logs", "crash-reports", ".git", "build", "mods")) {
            if (normalized.startsWith(base.resolve(name).toAbsolutePath().normalize())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSkippedLargeUnknown(DiscoveredFile file) {
        try {
            return skipReason(file) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private static String skipReason(DiscoveredFile file) throws IOException {
        if (DataAdapters.UNSUPPORTED.equals(file.adapter())) {
            return "unsupported plugin storage";
        }
        if (FileMigrator.shouldSkipLargeUnknown(file)) {
            return "unknown binary file over " + DataAdapters.DEFAULT_BINARY_SIZE_LIMIT + " bytes";
        }
        return null;
    }

    private static void addExtraTargets(
        Map<Path, DiscoveredFile> result,
        UuidBridgePaths paths,
        Optional<Path> targetsFile
    ) throws IOException {
        Optional<Path> file = targetsFile;
        if (file.isEmpty()) {
            Path defaultTargets = paths.controlDir().resolve("targets.json");
            if (Files.isRegularFile(defaultTargets)) {
                file = Optional.of(defaultTargets);
            }
        }
        if (file.isEmpty()) {
            return;
        }
        JsonElement root = JsonCodecs.readTree(file.get());
        if (!root.isJsonObject()) {
            return;
        }
        JsonObject object = root.getAsJsonObject();
        List<String> excludes = stringList(object.getAsJsonArray("exclude"));
        JsonArray includes = object.getAsJsonArray("include");
        if (includes == null) {
            return;
        }
        for (JsonElement element : includes) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject target = element.getAsJsonObject();
            String path = stringValue(target.get("path"));
            if (path.isBlank()) {
                continue;
            }
            String targetFormat = stringValue(target.get("format"));
            Optional<String> format = DataAdapters.normalizeFormat(targetFormat.isBlank() ? "auto" : targetFormat);
            if (format.isEmpty() || Set.of("sqlite", "leveldb", "database").contains(format.get())) {
                continue;
            }
            addExtraTarget(result, paths, path, format.get(), excludes);
        }
    }

    private static void addRecursivePluginFiles(Map<Path, DiscoveredFile> result, UuidBridgePaths paths)
        throws IOException {
        Path pluginsDir = paths.gameDir().resolve("plugins");
        if (!Files.isDirectory(pluginsDir)) {
            return;
        }
        Path root = canonicalRoot(pluginsDir);
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> !isDefaultExcluded(paths.gameDir(), path))
                .filter(path -> !isDefaultExcluded(pluginsDir, path))
                .forEach(path -> addPluginFile(result, paths, pluginsDir, root, path));
        }
    }

    private static void addPluginFile(
        Map<Path, DiscoveredFile> result,
        UuidBridgePaths paths,
        Path pluginsDir,
        Path root,
        Path file
    ) {
        Path display = displayPath(pluginsDir, root, file);
        if (isPluginExcluded(root, file)) {
            if (isPluginReportableSkippedFile(file)) {
                add(result, display, DataAdapters.UNSUPPORTED, "plugins-skipped", false);
            }
            return;
        }
        Optional<PluginTargetPresets.Target> preset = PluginTargetPresets.targetFor(paths.gameDir(), display);
        if (preset.isPresent()) {
            add(result, display, preset.get().adapter(), "plugin-preset:" + preset.get().name(), false);
            return;
        }
        if (isPluginSupportedDataFile(file)) {
            add(result, display, DataAdapters.forFile(file).id(), "plugins", false);
            return;
        }
        if (isPluginUnsupportedReportFile(file)) {
            add(result, display, DataAdapters.UNSUPPORTED, "plugins-unsupported", false);
        }
    }

    private static boolean isPluginReportableSkippedFile(Path path) {
        return isPluginSupportedDataFile(path) || isPluginUnsupportedReportFile(path);
    }

    private static boolean isPluginSupportedDataFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json")
            || name.endsWith(".dat")
            || name.endsWith(".dat_old")
            || name.endsWith(".mca");
    }

    private static boolean isPluginUnsupportedReportFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml")
            || name.endsWith(".yaml")
            || name.endsWith(".db")
            || name.endsWith(".sqlite")
            || name.endsWith(".sqlite3")
            || name.endsWith(".h2.db")
            || name.endsWith(".mv.db")
            || name.endsWith(".ldb");
    }

    private static boolean isPluginExcluded(Path pluginsRoot, Path path) {
        Path relative = pluginsRoot.relativize(path.toAbsolutePath().normalize());
        for (Path part : relative) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (name.startsWith(".")) {
                return true;
            }
            if (Set.of(
                "uuidbridge",
                "cache",
                "caches",
                "log",
                "logs",
                "backup",
                "backups",
                "library",
                "libraries",
                "lib",
                "libs",
                "update",
                "updates",
                "updater",
                "temp",
                "tmp"
            ).contains(name)) {
                return true;
            }
        }
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jar")
            || fileName.endsWith(".log")
            || fileName.endsWith(".tmp");
    }

    private static void addExtraTarget(
        Map<Path, DiscoveredFile> result,
        UuidBridgePaths paths,
        String rawPath,
        String format,
        List<String> excludes
    ) throws IOException {
        TargetRoot targetRoot = targetRoot(paths, rawPath);
        String pattern = targetRoot.path();
        if (pattern.contains("..")) {
            throw new SecurityException("Target path may not contain '..': " + rawPath);
        }
        Path root = canonicalRoot(targetRoot.root());
        if (containsGlob(pattern)) {
            Path searchRoot = globSearchRoot(root, pattern);
            if (!Files.isDirectory(searchRoot)) {
                return;
            }
            PathMatcher matcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + pattern.replace('\\', '/'));
            try (Stream<Path> stream = Files.walk(searchRoot)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> !isDefaultExcluded(paths.gameDir(), path))
                    .filter(path -> !isExcludedByTargets(paths, path, excludes))
                    .filter(path -> matcher.matches(root.relativize(path.toAbsolutePath().normalize())))
                    .forEach(path -> addExtraFile(result, targetRoot.root(), root, path, format));
            }
            return;
        }
        Path resolved = PathSecurity.resolveInside(targetRoot.root(), pattern);
        if (Files.isRegularFile(resolved) && !isExcludedByTargets(paths, resolved, excludes)) {
            addExtraFile(result, targetRoot.root(), root, resolved, format);
        }
    }

    private static void addExtraFile(
        Map<Path, DiscoveredFile> result,
        Path displayRoot,
        Path canonicalRoot,
        Path file,
        String format
    ) {
        String adapter = adapterFor(format, file);
        if (DataAdapters.BINARY.equals(adapter)) {
            return;
        }
        add(result, displayPath(displayRoot, canonicalRoot, file), adapter, "targets.json", true);
    }

    private static Path displayPath(Path displayRoot, Path canonicalRoot, Path file) {
        Path relative = canonicalRoot.relativize(file.toAbsolutePath().normalize());
        return displayRoot.resolve(relative).normalize();
    }

    private static boolean isExcludedByTargets(UuidBridgePaths paths, Path file, List<String> excludes) {
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        Path normalized;
        try {
            normalized = file.toRealPath();
        } catch (IOException exception) {
            normalized = file.toAbsolutePath().normalize();
        }
        for (String rawExclude : excludes) {
            if (rawExclude == null || rawExclude.isBlank()) {
                continue;
            }
            TargetRoot targetRoot = targetRoot(paths, rawExclude);
            Path root;
            try {
                root = canonicalRoot(targetRoot.root());
            } catch (IOException exception) {
                continue;
            }
            if (!normalized.startsWith(root)) {
                continue;
            }
            Path relative = root.relativize(normalized);
            String pattern = targetRoot.path();
            if (containsGlob(pattern)) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern.replace('\\', '/'));
                if (matcher.matches(relative)) {
                    return true;
                }
            } else if (relative.startsWith(Path.of(pattern))) {
                return true;
            }
        }
        return false;
    }

    private static String adapterFor(String format, Path path) {
        if ("auto".equals(format)) {
            return DataAdapters.forFile(path).id();
        }
        return format;
    }

    private static TargetRoot targetRoot(UuidBridgePaths paths, String rawPath) {
        if (rawPath.startsWith("world:")) {
            return new TargetRoot(paths.worldDir(), rawPath.substring("world:".length()));
        }
        if (rawPath.startsWith("game:")) {
            return new TargetRoot(paths.gameDir(), rawPath.substring("game:".length()));
        }
        return new TargetRoot(paths.gameDir(), rawPath);
    }

    private static boolean containsGlob(String path) {
        return path.indexOf('*') >= 0 || path.indexOf('?') >= 0 || path.indexOf('[') >= 0;
    }

    private static Path canonicalRoot(Path root) throws IOException {
        return PathSecurity.ensureDirectory(root).toRealPath();
    }

    private static Path globSearchRoot(Path root, String pattern) throws IOException {
        int firstGlob = firstGlobIndex(pattern);
        if (firstGlob <= 0) {
            return root;
        }
        int slash = pattern.lastIndexOf('/', firstGlob);
        if (slash <= 0) {
            return root;
        }
        return PathSecurity.resolveInside(root, pattern.substring(0, slash));
    }

    private static int firstGlobIndex(String pattern) {
        int result = -1;
        for (char marker : new char[] {'*', '?', '['}) {
            int index = pattern.indexOf(marker);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private static List<String> stringList(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            String value = stringValue(element);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static String stringValue(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return "";
        }
        return element.getAsString();
    }

    private record TargetRoot(Path root, String path) {
    }
}
