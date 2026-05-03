package dev.kara.uuidbridge.migration.rewrite;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UuidBinaryCodec {
    private UuidBinaryCodec() {
    }

    public static byte[] asLongPair(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static byte[] asIntArray(UUID uuid) {
        ByteBuffer source = ByteBuffer.wrap(asLongPair(uuid));
        ByteBuffer target = ByteBuffer.allocate(16);
        target.putInt(source.getInt());
        target.putInt(source.getInt());
        target.putInt(source.getInt());
        target.putInt(source.getInt());
        return target.array();
    }
}
