package dev.kara.uuidbridge.migration;

import java.util.UUID;

public record SingleplayerPlayerCopy(
    String name,
    String sourcePath,
    String targetPath,
    UUID fromUuid,
    UUID toUuid
) {
}
