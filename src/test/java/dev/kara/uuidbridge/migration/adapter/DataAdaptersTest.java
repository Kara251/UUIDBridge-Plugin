package dev.kara.uuidbridge.migration.adapter;

import dev.kara.uuidbridge.migration.FileChangeResult;
import dev.kara.uuidbridge.migration.MappingSource;
import dev.kara.uuidbridge.migration.MigrationDirection;
import dev.kara.uuidbridge.migration.UuidMapping;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataAdaptersTest {
    private static final UUID ONLINE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OFFLINE = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UuidMapping MAPPING = new UuidMapping(
        "Alice",
        ONLINE,
        OFFLINE,
        MigrationDirection.ONLINE_TO_OFFLINE,
        MappingSource.MAPPING_FILE
    );

    @Test
    void jsonAdapterRewritesUuidValuesButNotScoreboardNames() throws Exception {
        String json = """
            {
              "scores": [
                {
                  "Name": "Alice",
                  "Owner": "11111111-2222-3333-4444-555555555555"
                }
              ]
            }
            """;

        FileChangeResult result = DataAdapters.byId(DataAdapters.JSON).orElseThrow()
            .rewrite(json.getBytes(StandardCharsets.UTF_8), List.of(MAPPING));

        String rewritten = new String(result.content(), StandardCharsets.UTF_8);
        assertEquals(1, result.replacements());
        assertTrue(rewritten.contains("\"Name\": \"Alice\""));
        assertTrue(rewritten.contains(OFFLINE.toString()));
    }

    @Test
    void yamlTextAdapterRewritesExactUuidTextOnly() throws Exception {
        String yaml = """
            # owner 11111111-2222-3333-4444-555555555555
            uuid: "11111111-2222-3333-4444-555555555555"
            compact: 11111111222233334444555555555555
            name: Alice
            """;

        FileChangeResult result = DataAdapters.byId(DataAdapters.YAML_TEXT).orElseThrow()
            .rewrite(yaml.getBytes(StandardCharsets.UTF_8), List.of(MAPPING));

        String rewritten = new String(result.content(), StandardCharsets.UTF_8);
        assertEquals(3, result.replacements());
        assertTrue(rewritten.contains("# owner " + OFFLINE));
        assertTrue(rewritten.contains("uuid: \"" + OFFLINE + "\""));
        assertTrue(rewritten.contains("compact: " + OFFLINE.toString().replace("-", "")));
        assertTrue(rewritten.contains("name: Alice"));
    }
}
