package dev.kara.uuidbridge.migration;

public record PlannedChange(
    String path,
    long replacements,
    String action
) {
}
