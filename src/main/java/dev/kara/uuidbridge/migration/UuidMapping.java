package dev.kara.uuidbridge.migration;

import java.util.Objects;
import java.util.UUID;

public record UuidMapping(
    String name,
    UUID onlineUuid,
    UUID offlineUuid,
    MigrationDirection direction,
    MappingSource source
) {
    public UuidMapping {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(onlineUuid, "onlineUuid");
        Objects.requireNonNull(offlineUuid, "offlineUuid");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(source, "source");
    }

    public UUID fromUuid() {
        return direction == MigrationDirection.ONLINE_TO_OFFLINE ? onlineUuid : offlineUuid;
    }

    public UUID toUuid() {
        return direction == MigrationDirection.ONLINE_TO_OFFLINE ? offlineUuid : onlineUuid;
    }
}
