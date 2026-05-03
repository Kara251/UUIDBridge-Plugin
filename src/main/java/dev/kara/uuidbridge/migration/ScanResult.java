package dev.kara.uuidbridge.migration;

import java.util.List;

public record ScanResult(
    MigrationDirection direction,
    int knownPlayers,
    int mappings,
    List<PlanConflict> conflicts,
    List<MissingMapping> missingMappings,
    List<PlannedChange> estimatedChanges,
    CoverageReport coverage
) {
    public ScanResult(
        MigrationDirection direction,
        int knownPlayers,
        int mappings,
        List<PlanConflict> conflicts,
        List<MissingMapping> missingMappings,
        List<PlannedChange> estimatedChanges
    ) {
        this(direction, knownPlayers, mappings, conflicts, missingMappings, estimatedChanges, CoverageReport.empty());
    }

    public ScanResult {
        coverage = coverage == null ? CoverageReport.empty() : coverage;
    }
}
