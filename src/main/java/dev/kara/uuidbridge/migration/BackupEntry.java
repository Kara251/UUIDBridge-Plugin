package dev.kara.uuidbridge.migration;

public record BackupEntry(
    String originalPath,
    String backupPath,
    String currentPath,
    String operation,
    long originalSize,
    String originalSha256,
    long backupSize,
    String backupSha256
) {
    public long size() {
        return backupSize;
    }

    public String sha256() {
        return backupSha256;
    }
}
