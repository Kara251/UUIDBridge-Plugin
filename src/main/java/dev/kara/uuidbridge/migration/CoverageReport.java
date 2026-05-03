package dev.kara.uuidbridge.migration;

import java.util.List;

public record CoverageReport(
    List<CoverageTarget> targets,
    long scannedFiles,
    long skippedFiles,
    long estimatedReplacements
) {
    public static CoverageReport empty() {
        return new CoverageReport(List.of(), 0, 0, 0);
    }
}
