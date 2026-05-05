package dev.kara.uuidbridge.migration;

public record StartupFailure(
    String timestamp,
    String action,
    String planId,
    String message
) {
}
