package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Logger;

public final class PendingMigrationRunner {
    private PendingMigrationRunner() {
    }

    public static void runIfPresent(UuidBridgePaths paths, Logger logger) throws IOException {
        MigrationService service = new MigrationService();
        var pending = service.pendingMigration(paths);
        if (pending.isEmpty()) {
            return;
        }
        logger.warning("UUIDBridge pending " + pending.get().action().name().toLowerCase(Locale.ROOT)
            + " found for " + pending.get().planId() + "; running before server is available for play.");
        MigrationReport report = service.executePending(paths);
        logger.info("UUIDBridge " + report.action().name().toLowerCase(Locale.ROOT) + " "
            + report.planId() + " finished with state " + report.finalState()
            + " and " + report.changedFiles().size() + " changed files.");
    }
}
