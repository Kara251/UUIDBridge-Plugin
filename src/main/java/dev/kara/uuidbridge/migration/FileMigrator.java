package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.io.SafeFileWriter;
import dev.kara.uuidbridge.migration.adapter.DataAdapter;
import dev.kara.uuidbridge.migration.adapter.DataAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FileMigrator {
    private FileMigrator() {
    }

    public static FileChangeResult preview(Path file, List<UuidMapping> mappings) throws IOException {
        return preview(new DiscoveredFile(file, DataAdapters.forFile(file).id(),
            DataAdapters.forFile(file).format(), "legacy", false), mappings);
    }

    public static FileChangeResult preview(DiscoveredFile file, List<UuidMapping> mappings) throws IOException {
        return rewriteBytes(file, mappings);
    }

    public static long rewrite(Path file, List<UuidMapping> mappings) throws IOException {
        return rewrite(new DiscoveredFile(file, DataAdapters.forFile(file).id(),
            DataAdapters.forFile(file).format(), "legacy", false), mappings);
    }

    public static long rewrite(DiscoveredFile file, List<UuidMapping> mappings) throws IOException {
        FileChangeResult result = rewriteBytes(file, mappings);
        if (result.changed()) {
            SafeFileWriter.writeAtomic(file.path(), result.content());
        }
        return result.replacements();
    }

    public static boolean shouldSkipLargeUnknown(DiscoveredFile file) throws IOException {
        if (file.explicit()) {
            return false;
        }
        return DataAdapters.BINARY.equals(file.adapter())
            && Files.size(file.path()) > DataAdapters.DEFAULT_BINARY_SIZE_LIMIT;
    }

    private static FileChangeResult rewriteBytes(DiscoveredFile file, List<UuidMapping> mappings) throws IOException {
        if (shouldSkipLargeUnknown(file)) {
            return new FileChangeResult(0, new byte[0]);
        }
        DataAdapter adapter = DataAdapters.byId(file.adapter())
            .orElseGet(() -> DataAdapters.forFile(file.path()));
        return adapter.rewrite(Files.readAllBytes(file.path()), mappings);
    }
}
