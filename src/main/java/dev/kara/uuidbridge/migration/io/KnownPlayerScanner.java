package dev.kara.uuidbridge.migration.io;

import dev.kara.uuidbridge.migration.KnownPlayer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class KnownPlayerScanner {
    private KnownPlayerScanner() {
    }

    public static List<KnownPlayer> scan(Path gameDir, Path worldDir) throws IOException {
        Map<String, KnownPlayer> players = new LinkedHashMap<>();
        for (KnownPlayer player : UserCacheReader.read(gameDir.resolve("usercache.json"))) {
            players.put(player.onlineUuid().map(UUID::toString).orElse(player.displayName()), player);
        }
        addUuidFiles(worldDir.resolve("playerdata"), players);
        addUuidFiles(worldDir.resolve("advancements"), players);
        addUuidFiles(worldDir.resolve("stats"), players);
        return List.copyOf(players.values());
    }

    private static void addUuidFiles(Path directory, Map<String, KnownPlayer> players) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                .map(KnownPlayerScanner::uuidFromFilename)
                .flatMap(Optional::stream)
                .forEach(uuid -> players.putIfAbsent(uuid.toString(),
                    new KnownPlayer(null, Optional.of(uuid), Optional.empty())));
        }
    }

    private static Optional<UUID> uuidFromFilename(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(fileName.substring(0, dot)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
