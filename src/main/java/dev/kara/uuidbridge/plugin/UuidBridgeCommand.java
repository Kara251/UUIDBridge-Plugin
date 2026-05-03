package dev.kara.uuidbridge.plugin;

import dev.kara.uuidbridge.migration.BackupManifest;
import dev.kara.uuidbridge.migration.CoverageReport;
import dev.kara.uuidbridge.migration.MigrationDirection;
import dev.kara.uuidbridge.migration.MigrationLock;
import dev.kara.uuidbridge.migration.MigrationPlan;
import dev.kara.uuidbridge.migration.MigrationService;
import dev.kara.uuidbridge.migration.PendingMigration;
import dev.kara.uuidbridge.migration.ScanResult;
import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

final class UuidBridgeCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
        "scan", "plan", "apply", "status", "rollback", "cancel"
    );
    private static final List<String> DIRECTIONS = List.of("online-to-offline", "offline-to-online");
    private static final List<String> FLAGS = List.of(
        "--mapping", "--targets", "--singleplayer-name", "--plugins", "--confirm"
    );

    private final Supplier<UuidBridgePaths> paths;
    private final MigrationService service = new MigrationService();

    UuidBridgeCommand(Supplier<UuidBridgePaths> paths) {
        this.paths = paths;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uuidbridge.admin")) {
            fail(sender, "You do not have permission to use UUIDBridge.");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "scan" -> scan(sender, args);
                case "plan" -> plan(sender, args);
                case "apply" -> apply(sender, args);
                case "status" -> status(sender);
                case "rollback" -> rollback(sender, args);
                case "cancel" -> cancel(sender, args);
                default -> usage(sender);
            }
        } catch (Exception exception) {
            fail(sender, message(exception));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("uuidbridge.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return matches(SUBCOMMANDS, args[0]);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ((subcommand.equals("scan") || subcommand.equals("plan")) && args.length == 2) {
            return matches(DIRECTIONS, args[1]);
        }
        if (subcommand.equals("apply") || subcommand.equals("rollback") || subcommand.equals("cancel")) {
            if (args.length == 2) {
                return matches(planIds(), args[1]);
            }
            return matches(List.of("--confirm"), args[args.length - 1]);
        }
        if (subcommand.equals("scan") || subcommand.equals("plan")) {
            return matches(FLAGS, args[args.length - 1]);
        }
        return List.of();
    }

    private void scan(CommandSender sender, String[] args) throws Exception {
        if (args.length < 2) {
            fail(sender, "Usage: /uuidbridge scan <online-to-offline|offline-to-online> [options]");
            return;
        }
        CommandOptions options = CommandOptions.parse(args, 2);
        if (rejectUnknownOptions(sender, options)) {
            return;
        }
        MigrationDirection direction = MigrationDirection.parse(args[1]);
        ScanResult result = service.scan(paths.get(), direction, options.mapping(), options.targets(),
            options.singleplayerName(), options.plugins());
        long replacements = result.estimatedChanges().stream()
            .mapToLong(change -> change.replacements())
            .sum();
        send(sender, "UUIDBridge scan: players=" + result.knownPlayers()
            + ", mappings=" + result.mappings()
            + ", filesWithChanges=" + result.estimatedChanges().size()
            + ", estimatedReplacements=" + replacements + ".");
        sendCoverage(sender, result.coverage());
        sendSkippedCoverage(sender, result.coverage());
        if (options.plugins()) {
            send(sender, "Plugin data scanning: enabled.");
        }
        if (!result.conflicts().isEmpty()) {
            send(sender, "Conflicts: " + result.conflicts().size()
                + " target UUID collision(s); inspect the generated mapping inputs.");
        }
        if (!result.missingMappings().isEmpty()) {
            send(sender, "Missing mappings: " + result.missingMappings().size()
                + "; provide --mapping <file>.");
        }
    }

    private void plan(CommandSender sender, String[] args) throws Exception {
        if (args.length < 2) {
            fail(sender, "Usage: /uuidbridge plan <online-to-offline|offline-to-online> [options]");
            return;
        }
        CommandOptions options = CommandOptions.parse(args, 2);
        if (rejectUnknownOptions(sender, options)) {
            return;
        }
        MigrationDirection direction = MigrationDirection.parse(args[1]);
        MigrationPlan plan = service.createPlan(paths.get(), direction, options.mapping(), options.targets(),
            options.singleplayerName(), options.plugins());
        send(sender, "UUIDBridge plan created: " + plan.id());
        long replacements = plan.estimatedChanges().stream()
            .mapToLong(change -> change.replacements())
            .sum();
        send(sender, "Mappings: " + plan.mappings().size()
            + ", filesWithChanges: " + plan.estimatedChanges().size()
            + ", estimatedReplacements: " + replacements
            + ", conflicts: " + plan.conflicts().size()
            + ", missing: " + plan.missingMappings().size());
        sendCoverage(sender, plan.coverage());
        sendSkippedCoverage(sender, plan.coverage());
        send(sender, "Plugin data scanning: " + (plan.pluginTargetsEnabled() ? "enabled" : "disabled") + ".");
        if (plan.singleplayerPlayerCopy() != null) {
            send(sender, "Singleplayer Player copy planned for " + plan.singleplayerPlayerCopy().name() + ".");
        }
        if (plan.canApply()) {
            send(sender, "Use /uuidbridge apply " + plan.id() + " --confirm, then restart the server.");
        } else {
            send(sender, "Plan cannot be applied: fix conflicts and missing mappings first.");
        }
    }

    private void apply(CommandSender sender, String[] args) throws Exception {
        if (args.length < 2) {
            fail(sender, "Usage: /uuidbridge apply <planId> --confirm");
            return;
        }
        CommandOptions options = CommandOptions.parse(args, 2);
        if (rejectUnknownOptions(sender, options)) {
            return;
        }
        if (!options.confirm()) {
            fail(sender, "Refusing to apply without --confirm.");
            return;
        }
        service.markPendingApply(paths.get(), args[1], sender.getName());
        send(sender, "UUIDBridge plan marked pending: " + args[1]);
        send(sender, "Restart the server to apply it before the world is used.");
    }

    private void status(CommandSender sender) throws Exception {
        UuidBridgePaths currentPaths = paths.get();
        Optional<PendingMigration> pending = service.pendingMigration(currentPaths);
        Optional<Path> latestReport = service.latestReport(currentPaths);
        Optional<MigrationLock> lock = service.lock(currentPaths);
        send(sender, "UUIDBridge pending: " + pending
            .map(value -> value.action().name().toLowerCase(Locale.ROOT) + " " + value.planId())
            .orElse("none"));
        send(sender, "UUIDBridge latest report: " + latestReport.map(Path::toString).orElse("none"));
        send(sender, "UUIDBridge migration lock: " + lock
            .map(value -> value.action().name().toLowerCase(Locale.ROOT) + " " + value.planId())
            .orElse("none"));
        Optional<String> planForManifest = pending.map(PendingMigration::planId)
            .or(() -> latestReport.flatMap(path -> reportPlanId(path.getFileName().toString())));
        if (pending.isPresent()) {
            Optional<MigrationPlan> pendingPlan = service.plan(currentPaths, pending.get().planId());
            if (pendingPlan.isPresent()) {
                sendCoverage(sender, pendingPlan.get().coverage());
                send(sender, "UUIDBridge plugin data scanning: "
                    + (pendingPlan.get().pluginTargetsEnabled() ? "enabled" : "disabled"));
            }
        }
        if (planForManifest.isPresent()) {
            Optional<BackupManifest> manifest = service.backupManifest(currentPaths, planForManifest.get());
            send(sender, "UUIDBridge backup manifest: " + manifest
                .map(value -> value.complete() ? "complete" : "incomplete")
                .orElse("none"));
        } else {
            send(sender, "UUIDBridge backup manifest: none");
        }
    }

    private void rollback(CommandSender sender, String[] args) throws Exception {
        if (args.length < 2) {
            fail(sender, "Usage: /uuidbridge rollback <planId> --confirm");
            return;
        }
        CommandOptions options = CommandOptions.parse(args, 2);
        if (rejectUnknownOptions(sender, options)) {
            return;
        }
        if (!options.confirm()) {
            fail(sender, "Refusing to rollback without --confirm.");
            return;
        }
        service.markPendingRollback(paths.get(), args[1], sender.getName());
        send(sender, "UUIDBridge rollback marked pending: " + args[1]);
        send(sender, "Restart the server to restore files from the backup manifest.");
    }

    private void cancel(CommandSender sender, String[] args) throws Exception {
        if (args.length < 2) {
            fail(sender, "Usage: /uuidbridge cancel <planId>");
            return;
        }
        boolean canceled = service.cancel(paths.get(), args[1]);
        send(sender, canceled ? "UUIDBridge canceled pending plan: " + args[1] : "No matching pending plan.");
    }

    private static void sendCoverage(CommandSender sender, CoverageReport coverage) {
        send(sender, "Coverage: scanned=" + coverage.scannedFiles()
            + ", skipped=" + coverage.skippedFiles()
            + ", targets=" + coverage.targets().size() + ".");
    }

    private static void sendSkippedCoverage(CommandSender sender, CoverageReport coverage) {
        List<String> skipped = coverage.targets().stream()
            .filter(target -> !target.included())
            .limit(5)
            .map(target -> target.path() + ": " + target.skippedReason())
            .toList();
        if (skipped.isEmpty()) {
            return;
        }
        send(sender, "Skipped targets:");
        for (String line : skipped) {
            send(sender, "- " + line);
        }
        long remaining = coverage.skippedFiles() - skipped.size();
        if (remaining > 0) {
            send(sender, "... and " + remaining + " more skipped target(s).");
        }
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    private static void fail(CommandSender sender, String message) {
        sender.sendMessage("UUIDBridge: " + message);
    }

    private static void usage(CommandSender sender) {
        send(sender, "Usage: /uuidbridge <scan|plan|apply|status|rollback|cancel>");
    }

    private static boolean rejectUnknownOptions(CommandSender sender, CommandOptions options) {
        if (options.unknownOptions().isEmpty()) {
            return false;
        }
        fail(sender, "Unknown option(s): " + String.join(", ", options.unknownOptions()));
        return true;
    }

    private List<String> planIds() {
        Path plansDir = paths.get().plansDir();
        if (!Files.isDirectory(plansDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(plansDir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed())
                .map(path -> path.getFileName().toString())
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<String> matches(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(value);
            }
        }
        return result;
    }

    private static Optional<String> reportPlanId(String fileName) {
        if (!fileName.endsWith(".json")) {
            return Optional.empty();
        }
        String stem = fileName.substring(0, fileName.length() - ".json".length());
        if (stem.endsWith("-rollback")) {
            return Optional.of(stem.substring(0, stem.length() - "-rollback".length()));
        }
        return Optional.of(stem);
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
