package dev.kara.uuidbridge.migration;

public record FileChangeResult(
    long replacements,
    byte[] content
) {
    public boolean changed() {
        return replacements > 0;
    }
}
