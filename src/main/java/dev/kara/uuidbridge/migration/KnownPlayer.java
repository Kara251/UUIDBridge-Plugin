package dev.kara.uuidbridge.migration;

import java.util.Optional;
import java.util.UUID;

public record KnownPlayer(
    String name,
    Optional<UUID> onlineUuid,
    Optional<UUID> offlineUuid
) {
    public String displayName() {
        return name == null || name.isBlank() ? "<unknown>" : name;
    }
}
