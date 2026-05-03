package dev.kara.uuidbridge.migration.adapter;

import dev.kara.uuidbridge.migration.FileChangeResult;
import dev.kara.uuidbridge.migration.UuidMapping;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface DataAdapter {
    String id();

    String format();

    String risk();

    boolean supports(Path file);

    FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) throws IOException;
}
