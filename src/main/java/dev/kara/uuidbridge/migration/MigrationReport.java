package dev.kara.uuidbridge.migration;

import java.util.ArrayList;
import java.util.List;

public record MigrationReport(
    String planId,
    PendingAction action,
    MigrationDirection direction,
    List<PlannedChange> changedFiles,
    List<String> skipped,
    List<String> applyErrors,
    List<String> rollbackErrors,
    String backupPath,
    String checksum,
    String createdAt,
    String finalState
) {
    public boolean successful() {
        return applyErrors.isEmpty() && rollbackErrors.isEmpty()
            && ("APPLIED".equals(finalState) || "ROLLED_BACK".equals(finalState));
    }

    public List<String> errors() {
        List<String> all = new ArrayList<>(applyErrors);
        all.addAll(rollbackErrors);
        return List.copyOf(all);
    }
}
