package dev.kara.uuidbridge.migration;

import java.util.Locale;

public enum MigrationDirection {
    ONLINE_TO_OFFLINE("online-to-offline"),
    OFFLINE_TO_ONLINE("offline-to-online");

    private final String id;

    MigrationDirection(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static MigrationDirection parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        for (MigrationDirection direction : values()) {
            if (direction.id.equals(normalized)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown direction: " + value);
    }
}
