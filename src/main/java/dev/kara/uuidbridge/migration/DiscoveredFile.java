package dev.kara.uuidbridge.migration;

import java.nio.file.Path;

public record DiscoveredFile(
    Path path,
    String adapter,
    String format,
    String source,
    boolean explicit
) {
}
