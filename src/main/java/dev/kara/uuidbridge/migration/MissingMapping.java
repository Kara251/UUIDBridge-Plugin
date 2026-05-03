package dev.kara.uuidbridge.migration;

import java.util.UUID;

public record MissingMapping(
    String name,
    UUID knownUuid,
    String reason
) {
}
