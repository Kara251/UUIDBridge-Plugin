package dev.kara.uuidbridge.migration;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationPlannerTest {
    @Test
    void plansPlayerFileRename() {
        UUID online = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID offline = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UuidMapping mapping = new UuidMapping(
            "Alice",
            online,
            offline,
            MigrationDirection.ONLINE_TO_OFFLINE,
            MappingSource.MAPPING_FILE
        );
        String renamed = MigrationPlanner.renamedPlayerFile(Path.of(online + ".dat"), List.of(mapping));
        assertEquals(offline + ".dat", renamed);
    }
}
