package dev.kara.uuidbridge.migration;

import java.util.List;
import java.util.UUID;

public record PlanConflict(
    UUID targetUuid,
    List<String> names,
    String reason
) {
}
