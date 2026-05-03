package dev.kara.uuidbridge.migration;

public record PendingMigration(
    PendingAction action,
    String planId,
    String createdAt,
    String confirmedBy
) {
}
