package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.io.JsonCodecs;
import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import java.io.IOException;
import java.time.Instant;
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
        MigrationReport report;
        try {
            report = service.executePending(paths);
        } catch (IOException | RuntimeException exception) {
            writeFailureMarker(paths, pending.get(), exception);
            throw exception;
        }
        logger.info("UUIDBridge " + report.action().name().toLowerCase(Locale.ROOT) + " "
            + report.planId() + " finished with state " + report.finalState()
            + " and " + report.changedFiles().size() + " changed files.");
    }

    private static void writeFailureMarker(UuidBridgePaths paths, PendingMigration pending, Throwable exception) {
        try {
            JsonCodecs.write(paths.controlDir().resolve("startup-failure.json"),
                new StartupFailure(
                    Instant.now().toString(),
                    pending.action().name().toLowerCase(Locale.ROOT),
                    pending.planId(),
                    message(exception)
                ));
        } catch (IOException markerException) {
            exception.addSuppressed(markerException);
        }
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
