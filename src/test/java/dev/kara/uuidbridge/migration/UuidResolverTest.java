package dev.kara.uuidbridge.migration;

import dev.kara.uuidbridge.migration.rewrite.OfflineUuid;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UuidResolverTest {
    @Test
    void doesNotTreatOfflineUsercacheUuidAsOnlineUuid() throws Exception {
        var offline = OfflineUuid.forName("Alice");
        var resolver = new UuidResolver();
        var resolved = resolver.resolve(
            MigrationDirection.OFFLINE_TO_ONLINE,
            List.of(new KnownPlayer("Alice", Optional.of(offline), Optional.of(offline))),
            Optional.empty()
        );

        assertEquals(0, resolved.mappings().size());
        assertEquals(1, resolved.missingMappings().size());
    }
}
