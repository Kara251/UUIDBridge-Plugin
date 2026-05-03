package dev.kara.uuidbridge.migration;

import java.util.List;

public record BackupManifest(
    String planId,
    String createdAt,
    String updatedAt,
    boolean complete,
    List<BackupEntry> files
) {
}
