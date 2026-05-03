package dev.kara.uuidbridge.migration;

public record MigrationLock(
    PendingAction action,
    String planId,
    String startedAt
) {
}
