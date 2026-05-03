package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.io.JsonCodecs;
import dev.kara.uuidbridge.migration.io.UuidBridgePaths;
import dev.kara.uuidbridge.migration.rewrite.OfflineUuid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationIntegrationTest {
    private static final UUID ONLINE_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER_UUID = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final String NAME = "Alice";
    private static final UUID OFFLINE_UUID = OfflineUuid.forName(NAME);

    @TempDir
    Path tempDir;

    @Test
    void migratesOnlineToOfflineAcrossWorldFilesAndWritesManifest() throws Exception {
        UuidBridgePaths paths = fixture(ONLINE_UUID, ONLINE_UUID, Optional.empty());
        MigrationService service = new MigrationService();

        MigrationPlan plan = service.createPlan(paths, MigrationDirection.ONLINE_TO_OFFLINE, Optional.empty());
        assertTrue(plan.canApply());
        service.markPending(paths, plan.id());
        MigrationReport report = service.executePending(paths);

        assertTrue(report.successful());
        assertEquals(PendingAction.APPLY, report.action());
        assertEquals("APPLIED", report.finalState());
        assertTrue(report.applyErrors().isEmpty());
        assertTrue(report.rollbackErrors().isEmpty());
        assertFalse(Files.exists(paths.pendingFile()));
        assertFalse(service.hasLock(paths));
        assertTrue(Files.exists(paths.worldDir().resolve("playerdata").resolve(OFFLINE_UUID + ".dat")));
        assertFalse(Files.exists(paths.worldDir().resolve("playerdata").resolve(ONLINE_UUID + ".dat")));
        assertContains(paths.gameDir().resolve("whitelist.json"), OFFLINE_UUID.toString());
        assertContains(paths.worldDir().resolve("advancements").resolve(OFFLINE_UUID + ".json"), OFFLINE_UUID.toString());
        assertGzipContains(paths.worldDir().resolve("playerdata").resolve(OFFLINE_UUID + ".dat"), OFFLINE_UUID.toString());
        assertGzipContains(paths.worldDir().resolve("data").resolve("scoreboard.dat"), OFFLINE_UUID.toString());
        assertGzipContains(paths.worldDir().resolve("data").resolve("raids.dat"), OFFLINE_UUID.toString());
        assertGzipContains(paths.worldDir().resolve("data").resolve("map_0.dat"), OFFLINE_UUID.toString());
        assertGzipContains(paths.worldDir().resolve("data").resolve("command_storage_uuidbridge.dat"), OFFLINE_UUID.toString());
        assertGzipContains(paths.worldDir().resolve("level.dat"), OFFLINE_UUID.toString());
        assertGzipContains(paths.worldDir().resolve("level.dat_old"), OFFLINE_UUID.toString());
        assertContains(paths.gameDir().resolve("config").resolve("claims").resolve("alice.json"), OFFLINE_UUID.toString());
        assertRegionContains(paths.worldDir().resolve("entities").resolve("r.0.0.mca"), OFFLINE_UUID.toString());
        assertRegionContains(paths.worldDir().resolve("entities").resolve("r.0.0.mca"), OTHER_UUID.toString());
        assertRegionMissing(paths.worldDir().resolve("entities").resolve("r.0.0.mca"), ONLINE_UUID.toString());
        assertRegionContains(paths.worldDir().resolve("poi").resolve("r.0.0.mca"), OFFLINE_UUID.toString());
        assertRegionContains(paths.worldDir().resolve("DIM-1").resolve("region").resolve("r.0.0.mca"), OFFLINE_UUID.toString());

        BackupManifest manifest = JsonCodecs.read(Path.of(report.backupPath()).resolve("manifest.json"), BackupManifest.class);
        assertTrue(manifest.complete());
        assertTrue(manifest.files().stream().anyMatch(entry -> entry.originalPath().contains("playerdata")));
        assertTrue(manifest.files().stream().allMatch(entry -> !entry.originalSha256().isBlank()));
        assertTrue(manifest.files().stream().allMatch(entry -> !entry.backupSha256().isBlank()));
    }

    @Test
    void migratesOfflineToOnlineWithMappingFile() throws Exception {
        UuidBridgePaths paths = fixture(OFFLINE_UUID, OFFLINE_UUID, Optional.of(mappingFile()));
        MigrationService service = new MigrationService();

        MigrationPlan plan = service.createPlan(paths, MigrationDirection.OFFLINE_TO_ONLINE,
            Optional.of("mapping.csv"));
        assertTrue(plan.canApply());
        service.markPending(paths, plan.id());
        MigrationReport report = service.executePending(paths);

        assertTrue(report.successful());
        assertTrue(Files.exists(paths.worldDir().resolve("playerdata").resolve(ONLINE_UUID + ".dat")));
        assertFalse(Files.exists(paths.worldDir().resolve("playerdata").resolve(OFFLINE_UUID + ".dat")));
        assertContains(paths.gameDir().resolve("ops.json"), ONLINE_UUID.toString());
        assertGzipContains(paths.worldDir().resolve("playerdata").resolve(ONLINE_UUID + ".dat"), ONLINE_UUID.toString());
    }

    @Test
    void manuallyRollsBackSuccessfulMigrationAndProtectsCurrentFiles() throws Exception {
        UuidBridgePaths paths = fixture(ONLINE_UUID, ONLINE_UUID, Optional.empty());
        MigrationService service = new MigrationService();
        MigrationPlan plan = service.createPlan(paths, MigrationDirection.ONLINE_TO_OFFLINE, Optional.empty());

        service.markPending(paths, plan.id());
        assertTrue(service.executePending(paths).successful());
        assertTrue(Files.exists(paths.worldDir().resolve("playerdata").resolve(OFFLINE_UUID + ".dat")));

        service.markPendingRollback(paths, plan.id(), "test");
        MigrationReport rollback = service.executePending(paths);

        assertTrue(rollback.successful());
        assertEquals(PendingAction.ROLLBACK, rollback.action());
        assertEquals("ROLLED_BACK", rollback.finalState());
        assertTrue(Files.exists(paths.worldDir().resolve("playerdata").resolve(ONLINE_UUID + ".dat")));
        assertFalse(Files.exists(paths.worldDir().resolve("playerdata").resolve(OFFLINE_UUID + ".dat")));
        assertContains(paths.gameDir().resolve("whitelist.json"), ONLINE_UUID.toString());
        assertGzipContains(paths.worldDir().resolve("playerdata").resolve(ONLINE_UUID + ".dat"), ONLINE_UUID.toString());
        assertTrue(hasAnyFile(paths.backupPath(plan.id()).resolve("rollback-current")));
    }

    @Test
    void failedApplyAutomaticallyRollsBackAndKeepsPendingReport() throws Exception {
        UuidBridgePaths paths = fixture(ONLINE_UUID, ONLINE_UUID, Optional.empty());
        MigrationService service = new MigrationService();
        MigrationPlan plan = service.createPlan(paths, MigrationDirection.ONLINE_TO_OFFLINE, Optional.empty());

        service.markPending(paths, plan.id());
        Files.writeString(paths.worldDir().resolve("playerdata").resolve(OFFLINE_UUID + ".dat"), "existing");
        assertThrows(java.io.IOException.class, () -> service.executePending(paths));
        MigrationReport report = JsonCodecs.read(paths.reportPath(plan.id()), MigrationReport.class);

        assertFalse(report.successful());
        assertEquals("ROLLED_BACK", report.finalState());
        assertFalse(report.applyErrors().isEmpty());
        assertTrue(report.rollbackErrors().isEmpty());
        assertTrue(Files.exists(paths.pendingFile()));
        assertFalse(service.hasLock(paths));
        assertTrue(Files.exists(paths.reportPath(plan.id())));
        assertTrue(Files.exists(paths.worldDir().resolve("playerdata").resolve(ONLINE_UUID + ".dat")));
        assertContains(paths.gameDir().resolve("whitelist.json"), ONLINE_UUID.toString());
        BackupManifest manifest = JsonCodecs.read(paths.backupPath(plan.id()).resolve("manifest.json"), BackupManifest.class);
        assertFalse(manifest.complete());
    }

    @Test
    void damagedRegionIsReportedWithoutStoppingOtherFiles() throws Exception {
        UuidBridgePaths paths = fixture(ONLINE_UUID, ONLINE_UUID, Optional.empty());
        MigrationService service = new MigrationService();
        MigrationPlan plan = service.createPlan(paths, MigrationDirection.ONLINE_TO_OFFLINE, Optional.empty());
        Files.write(paths.worldDir().resolve("entities").resolve("r.1.0.mca"), damagedRegionWithTargetUuid());

        service.markPending(paths, plan.id());
        assertThrows(java.io.IOException.class, () -> service.executePending(paths));
        MigrationReport report = JsonCodecs.read(paths.reportPath(plan.id()), MigrationReport.class);

        assertFalse(report.successful());
        assertEquals("ROLLED_BACK", report.finalState());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("r.1.0.mca")));
        assertTrue(Files.exists(paths.worldDir().resolve("playerdata").resolve(ONLINE_UUID + ".dat")));
        assertFalse(Files.exists(paths.worldDir().resolve("playerdata").resolve(OFFLINE_UUID + ".dat")));
        assertContains(paths.gameDir().resolve("whitelist.json"), ONLINE_UUID.toString());
        assertTrue(Files.exists(paths.pendingFile()));
        assertFalse(service.hasLock(paths));
    }

    @Test
    void corruptedBackupMakesManualRollbackKeepPendingLockAndReport() throws Exception {
        UuidBridgePaths paths = fixture(ONLINE_UUID, ONLINE_UUID, Optional.empty());
        MigrationService service = new MigrationService();
        MigrationPlan plan = service.createPlan(paths, MigrationDirection.ONLINE_TO_OFFLINE, Optional.empty());
        service.markPending(paths, plan.id());
        assertTrue(service.executePending(paths).successful());

        BackupManifest manifest = JsonCodecs.read(paths.backupPath(plan.id()).resolve("manifest.json"), BackupManifest.class);
        BackupEntry first = manifest.files().getFirst();
        Files.writeString(paths.backupPath(plan.id()).resolve(first.backupPath()), "corrupted");

        service.markPendingRollback(paths, plan.id(), "test");
        assertThrows(java.io.IOException.class, () -> service.executePending(paths));
        MigrationReport report = JsonCodecs.read(paths.reportPath(plan.id() + "-rollback"), MigrationReport.class);

        assertFalse(report.successful());
        assertEquals(PendingAction.ROLLBACK, report.action());
        assertEquals("ROLLBACK_FAILED", report.finalState());
        assertFalse(report.rollbackErrors().isEmpty());
        assertTrue(Files.exists(paths.pendingFile()));
        assertTrue(service.hasLock(paths));
    }

    @Test
    void cancelIsRejectedWhenLockExists() throws Exception {
        UuidBridgePaths paths = fixture(ONLINE_UUID, ONLINE_UUID, Optional.empty());
        MigrationService service = new MigrationService();
        MigrationPlan plan = service.createPlan(paths, MigrationDirection.ONLINE_TO_OFFLINE, Optional.empty());
        service.markPending(paths, plan.id());
        JsonCodecs.write(paths.controlDir().resolve("migration.lock"),
            new MigrationLock(PendingAction.APPLY, plan.id(), "now"));

        assertThrows(java.io.IOException.class, () -> service.cancel(paths, plan.id()));
        assertTrue(Files.exists(paths.pendingFile()));
    }

    @Test
    void copiesSingleplayerPlayerTagWhenExplicitlyPlanned() throws Exception {
        Path gameDir = tempDir.resolve("singleplayer-server");
        Path worldDir = gameDir.resolve("world");
        Files.createDirectories(worldDir);
        Files.createDirectories(worldDir.resolve("playerdata"));
        Files.writeString(gameDir.resolve("usercache.json"), """
            [
              {
                "name": "%s",
                "uuid": "%s"
              }
            ]
            """.formatted(NAME, ONLINE_UUID));
        Files.write(worldDir.resolve("level.dat"), gzip("""
            {
              "Data": {
                "Player": {
                  "UUID": "%s",
                  "InventoryOwner": "%s"
                }
              }
            }
            """.formatted(ONLINE_UUID, ONLINE_UUID)));
        UuidBridgePaths paths = UuidBridgePaths.create(gameDir, worldDir);
        MigrationService service = new MigrationService();

        MigrationPlan plan = service.createPlan(paths, MigrationDirection.ONLINE_TO_OFFLINE,
            Optional.empty(), Optional.empty(), Optional.of(NAME));

        assertTrue(plan.canApply());
        assertTrue(plan.singleplayerPlayerCopy() != null);
        service.markPending(paths, plan.id());
        MigrationReport report = service.executePending(paths);

        assertTrue(report.successful());
        assertTrue(Files.exists(worldDir.resolve("playerdata").resolve(OFFLINE_UUID + ".dat")));
        assertGzipContains(worldDir.resolve("playerdata").resolve(OFFLINE_UUID + ".dat"), OFFLINE_UUID.toString());
        assertGzipContains(worldDir.resolve("level.dat"), OFFLINE_UUID.toString());
    }

    @Test
    void autoCopiesSingleplayerPlayerTagWhenOnlyOneMappingExists() throws Exception {
        Path gameDir = tempDir.resolve("auto-singleplayer-server");
        Path worldDir = gameDir.resolve("world");
        Files.createDirectories(worldDir);
        Files.createDirectories(worldDir.resolve("playerdata"));
        Files.writeString(gameDir.resolve("usercache.json"), """
            [
              {
                "name": "%s",
                "uuid": "%s"
              }
            ]
            """.formatted(NAME, ONLINE_UUID));
        Files.write(worldDir.resolve("level.dat"), gzip("""
            {
              "Data": {
                "Player": {
                  "UUID": "%s"
                }
              }
            }
            """.formatted(ONLINE_UUID)));
        UuidBridgePaths paths = UuidBridgePaths.create(gameDir, worldDir);
        MigrationService service = new MigrationService();

        MigrationPlan plan = service.createPlan(paths, MigrationDirection.ONLINE_TO_OFFLINE, Optional.empty());

        assertTrue(plan.canApply());
        assertTrue(plan.singleplayerPlayerCopy() != null);
        service.markPending(paths, plan.id());
        assertTrue(service.executePending(paths).successful());
        assertTrue(Files.exists(worldDir.resolve("playerdata").resolve(OFFLINE_UUID + ".dat")));
    }

    @Test
    void migratesPluginFixtureAndRollsBack() throws Exception {
        UuidBridgePaths paths = fixture(ONLINE_UUID, ONLINE_UUID, Optional.empty());
        Path essentials = paths.gameDir().resolve("plugins/Essentials/userdata").resolve(ONLINE_UUID + ".yml");
        Path residence = paths.gameDir().resolve("plugins/Residence/Save/residences.yml");
        Path unknownYaml = paths.gameDir().resolve("plugins/Claims/data/config.yml");
        Path database = paths.gameDir().resolve("plugins/LuckPerms/luckperms-h2.mv.db");
        Files.createDirectories(essentials.getParent());
        Files.createDirectories(residence.getParent());
        Files.createDirectories(unknownYaml.getParent());
        Files.createDirectories(database.getParent());
        Files.writeString(essentials, """
            # user %s
            uuid: "%s"
            compact: %s
            money: '42.0'
            """.formatted(ONLINE_UUID, ONLINE_UUID, ONLINE_UUID.toString().replace("-", "")));
        Files.writeString(residence, """
            residences:
              spawn:
                owner: "%s"
                trusted:
                - "%s"
            """.formatted(ONLINE_UUID, OTHER_UUID));
        Files.writeString(unknownYaml, "owner: \"%s\"\n".formatted(ONLINE_UUID));
        Files.writeString(database, ONLINE_UUID.toString());

        MigrationService service = new MigrationService();
        MigrationPlan plan = service.createPlan(paths, MigrationDirection.ONLINE_TO_OFFLINE,
            Optional.empty(), Optional.empty(), Optional.empty(), true);

        assertTrue(plan.canApply());
        assertTrue(plan.pluginTargetsEnabled());
        assertTrue(plan.estimatedChanges().stream().anyMatch(change -> change.action().equals("plugin-rename")));
        assertTrue(plan.coverage().targets().stream().anyMatch(target ->
            target.path().contains("Claims/data/config.yml") && target.skippedReason() != null));
        service.markPending(paths, plan.id());
        MigrationReport report = service.executePending(paths);

        Path migratedEssentials = essentials.resolveSibling(OFFLINE_UUID + ".yml");
        assertTrue(report.successful());
        assertTrue(Files.exists(migratedEssentials));
        assertFalse(Files.exists(essentials));
        assertContains(migratedEssentials, OFFLINE_UUID.toString());
        assertContains(migratedEssentials, OFFLINE_UUID.toString().replace("-", ""));
        assertContains(residence, OFFLINE_UUID.toString());
        assertContains(residence, OTHER_UUID.toString());
        assertContains(unknownYaml, ONLINE_UUID.toString());
        assertContains(database, ONLINE_UUID.toString());
        assertTrue(report.skipped().stream().anyMatch(value -> value.contains("unsupported plugin storage")));

        service.markPendingRollback(paths, plan.id(), "test");
        MigrationReport rollback = service.executePending(paths);

        assertTrue(rollback.successful());
        assertTrue(Files.exists(essentials));
        assertFalse(Files.exists(migratedEssentials));
        assertContains(essentials, ONLINE_UUID.toString());
        assertContains(residence, ONLINE_UUID.toString());
    }

    private UuidBridgePaths fixture(UUID fileUuid, UUID contentUuid, Optional<Path> mappingFile) throws Exception {
        Path gameDir = tempDir.resolve("server");
        Path worldDir = gameDir.resolve("world");
        Files.createDirectories(worldDir.resolve("playerdata"));
        Files.createDirectories(worldDir.resolve("advancements"));
        Files.createDirectories(worldDir.resolve("stats"));
        Files.createDirectories(worldDir.resolve("entities"));
        Files.createDirectories(worldDir.resolve("poi"));
        Files.createDirectories(worldDir.resolve("DIM-1").resolve("region"));
        Files.createDirectories(worldDir.resolve("data"));
        Files.createDirectories(gameDir.resolve("config").resolve("claims"));
        Files.createDirectories(gameDir.resolve("uuidbridge"));

        Files.writeString(gameDir.resolve("usercache.json"), """
            [
              {
                "name": "%s",
                "uuid": "%s"
              }
            ]
            """.formatted(NAME, contentUuid));
        Files.writeString(gameDir.resolve("whitelist.json"), listJson(contentUuid));
        Files.writeString(gameDir.resolve("ops.json"), listJson(contentUuid));
        Files.writeString(gameDir.resolve("banned-players.json"), listJson(contentUuid));
        Files.writeString(gameDir.resolve("config").resolve("claims").resolve("alice.json"), """
            {
              "claimOwner": "%s",
              "members": ["%s"]
            }
            """.formatted(contentUuid, OTHER_UUID));
        Files.writeString(gameDir.resolve("uuidbridge").resolve("targets.json"), """
            {
              "include": [
                {"path": "config/claims/*.json", "format": "json"}
              ]
            }
            """);

        Files.write(worldDir.resolve("playerdata").resolve(fileUuid + ".dat"), gzip("""
            {"Owner":"%s","id":"minecraft:wolf","Other":"%s"}
            """.formatted(contentUuid, OTHER_UUID)));
        Files.write(worldDir.resolve("level.dat"), gzip("""
            {"Data":{"CustomBossEvents":{"uuidbridge:test":{"Players":["%s"]}}}}
            """.formatted(contentUuid)));
        Files.write(worldDir.resolve("level.dat_old"), gzip("""
            {"Data":{"WanderingTraderId":"%s"}}
            """.formatted(contentUuid)));
        Files.write(worldDir.resolve("data").resolve("scoreboard.dat"), gzip("""
            {"data":{"Objectives":[{"Name":"Alice","Owner":"%s"}]}}
            """.formatted(contentUuid)));
        Files.write(worldDir.resolve("data").resolve("raids.dat"), gzip("""
            {"HeroesOfTheVillage":["%s"],"Random":"%s"}
            """.formatted(contentUuid, OTHER_UUID)));
        Files.write(worldDir.resolve("data").resolve("map_0.dat"), gzip("""
            {"Decorations":[{"Owner":"%s"}]}
            """.formatted(contentUuid)));
        Files.write(worldDir.resolve("data").resolve("command_storage_uuidbridge.dat"), gzip("""
            {"data":{"uuidbridge:test":{"owner":"%s","name":"Alice"}}}
            """.formatted(contentUuid)));
        Files.writeString(worldDir.resolve("advancements").resolve(fileUuid + ".json"), """
            {"criteria":{"uuid":"%s","other":"%s"}}
            """.formatted(contentUuid, OTHER_UUID));
        Files.writeString(worldDir.resolve("stats").resolve(fileUuid + ".json"), """
            {"stats":{"minecraft:custom":{"uuid":"%s"}}}
            """.formatted(contentUuid));
        Files.write(worldDir.resolve("entities").resolve("r.0.0.mca"), region("""
            {"id":"touhou_little_maid:maid","owner_uuid":"%s","Other":"%s"}
            """.formatted(contentUuid, OTHER_UUID)));
        Files.write(worldDir.resolve("poi").resolve("r.0.0.mca"), region("""
            {"Records":[{"ticket_holder":"%s"}]}
            """.formatted(contentUuid)));
        Files.write(worldDir.resolve("DIM-1").resolve("region").resolve("r.0.0.mca"), region("""
            {"Level":{"Owner":"%s"}}
            """.formatted(contentUuid)));

        if (mappingFile.isPresent()) {
            Files.copy(mappingFile.get(), gameDir.resolve("mapping.csv"));
        }
        return UuidBridgePaths.create(gameDir, worldDir);
    }

    private Path mappingFile() throws Exception {
        Path mapping = tempDir.resolve("mapping.csv");
        Files.writeString(mapping, "name,onlineUuid,offlineUuid\n%s,%s,%s\n".formatted(NAME, ONLINE_UUID, OFFLINE_UUID));
        return mapping;
    }

    private static String listJson(UUID uuid) {
        return """
            [
              {
                "name": "%s",
                "uuid": "%s"
              }
            ]
            """.formatted(NAME, uuid);
    }

    private static void assertContains(Path path, String text) throws Exception {
        assertTrue(Files.readString(path).contains(text), path + " should contain " + text);
    }

    private static void assertGzipContains(Path path, String text) throws Exception {
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(path))) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains(text), path + " should contain " + text);
        }
    }

    private static void assertRegionContains(Path path, String text) throws Exception {
        assertTrue(regionContent(path).contains(text), path + " should contain " + text);
    }

    private static void assertRegionMissing(Path path, String text) throws Exception {
        assertFalse(regionContent(path).contains(text), path + " should not contain " + text);
    }

    private static String regionContent(Path path) throws Exception {
        byte[] region = Files.readAllBytes(path);
        int location = ByteBuffer.wrap(region, 0, 4).getInt();
        int offset = ((location >> 8) & 0xFFFFFF) * 4096;
        int length = ByteBuffer.wrap(region, offset, 4).getInt();
        byte[] compressed = java.util.Arrays.copyOfRange(region, offset + 5, offset + 4 + length);
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] gzip(String content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(output)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    private static byte[] region(String payload) throws Exception {
        byte[] compressed = zlib(payload.getBytes(StandardCharsets.UTF_8));
        byte[] chunk = ByteBuffer.allocate(5 + compressed.length)
            .putInt(compressed.length + 1)
            .put((byte) 2)
            .put(compressed)
            .array();
        int sectors = Math.max(1, (chunk.length + 4095) / 4096);
        ByteBuffer region = ByteBuffer.allocate(8192 + sectors * 4096);
        region.putInt((2 << 8) | sectors);
        region.position(8192);
        region.put(chunk);
        return region.array();
    }

    private static byte[] damagedRegionWithTargetUuid() {
        byte[] payload = ONLINE_UUID.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer region = ByteBuffer.allocate(8192 + 4096);
        region.putInt((2 << 8) | 1);
        region.position(8192);
        region.putInt(payload.length + 1);
        region.put((byte) 99);
        region.put(payload);
        return region.array();
    }

    private static byte[] zlib(byte[] payload) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
            deflater.write(payload);
        }
        return output.toByteArray();
    }

    private static boolean hasAnyFile(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var stream = Files.walk(directory)) {
            return stream.anyMatch(Files::isRegularFile);
        }
    }

    @SuppressWarnings("unused")
    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(file));
        return HexFormat.of().formatHex(digest.digest());
    }
}
