package dev.kara.uuidbridge.migration;

public record CoverageTarget(
    String path,
    String adapter,
    String format,
    long sizeBytes,
    String source,
    String skippedReason
) {
    public boolean included() {
        return skippedReason == null || skippedReason.isBlank();
    }
}
