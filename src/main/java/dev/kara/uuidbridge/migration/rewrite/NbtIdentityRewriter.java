package dev.kara.uuidbridge.migration.rewrite;

import dev.kara.uuidbridge.migration.FileChangeResult;
import dev.kara.uuidbridge.migration.UuidMapping;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class NbtIdentityRewriter {
    private static final byte TAG_END = 0;
    private static final byte TAG_BYTE = 1;
    private static final byte TAG_SHORT = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_LONG = 4;
    private static final byte TAG_FLOAT = 5;
    private static final byte TAG_DOUBLE = 6;
    private static final byte TAG_BYTE_ARRAY = 7;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;
    private static final byte TAG_LONG_ARRAY = 12;

    private NbtIdentityRewriter() {
    }

    public static FileChangeResult rewriteOrFallback(byte[] content, List<UuidMapping> mappings) {
        try {
            return rewrite(content, mappings);
        } catch (IOException | RuntimeException exception) {
            return UuidReplacementEngine.rewritePlain(content, mappings);
        }
    }

    public static FileChangeResult rewrite(byte[] content, List<UuidMapping> mappings) throws IOException {
        NamedTag root = readNamed(content);
        Counter counter = new Counter();
        Tag rewritten = rewriteTag(root.tag(), mappings, counter);
        if (counter.value == 0) {
            return new FileChangeResult(0, content);
        }
        return new FileChangeResult(counter.value, writeNamed(new NamedTag(root.type(), root.name(), rewritten)));
    }

    public static Optional<byte[]> extractDataPlayer(byte[] content, List<UuidMapping> mappings) throws IOException {
        NamedTag root = readNamed(content);
        if (!(root.tag() instanceof CompoundTag compound)) {
            return Optional.empty();
        }
        Optional<Tag> player = compound.child("Data")
            .filter(Data -> Data.tag() instanceof CompoundTag)
            .flatMap(data -> ((CompoundTag) data.tag()).child("Player"))
            .map(NamedTag::tag);
        if (player.isEmpty()) {
            return Optional.empty();
        }

        Counter counter = new Counter();
        Tag rewritten = rewriteTag(player.get(), mappings, counter);
        if (!(rewritten instanceof CompoundTag)) {
            return Optional.empty();
        }
        return Optional.of(writeNamed(new NamedTag(TAG_COMPOUND, "", rewritten)));
    }

    private static Tag rewriteTag(Tag tag, List<UuidMapping> mappings, Counter counter) {
        if (tag instanceof StringTag stringTag) {
            String replacement = JsonIdentityRewriter.replaceExact(stringTag.value(), mappings);
            if (!replacement.equals(stringTag.value())) {
                counter.value++;
                return new StringTag(replacement);
            }
            return tag;
        }
        if (tag instanceof IntArrayTag intArrayTag) {
            int[] replacement = replaceIntArray(intArrayTag.value(), mappings);
            if (replacement != intArrayTag.value()) {
                counter.value++;
                return new IntArrayTag(replacement);
            }
            return tag;
        }
        if (tag instanceof LongArrayTag longArrayTag) {
            long[] replacement = replaceLongArray(longArrayTag.value(), mappings);
            if (replacement != longArrayTag.value()) {
                counter.value++;
                return new LongArrayTag(replacement);
            }
            return tag;
        }
        if (tag instanceof ListTag listTag) {
            boolean changed = false;
            List<Tag> values = new ArrayList<>(listTag.values().size());
            for (Tag value : listTag.values()) {
                Tag rewritten = rewriteTag(value, mappings, counter);
                changed |= rewritten != value;
                values.add(rewritten);
            }
            return changed ? new ListTag(listTag.elementType(), values) : tag;
        }
        if (tag instanceof CompoundTag compoundTag) {
            boolean changed = false;
            List<NamedTag> entries = new ArrayList<>(compoundTag.entries().size());
            for (NamedTag entry : compoundTag.entries()) {
                Tag rewritten = rewriteTag(entry.tag(), mappings, counter);
                changed |= rewritten != entry.tag();
                entries.add(new NamedTag(entry.type(), entry.name(), rewritten));
            }
            PairRewrite pairRewrite = replaceLongPairs(entries, mappings);
            if (pairRewrite.replacements() > 0) {
                counter.value += pairRewrite.replacements();
                changed = true;
                entries = pairRewrite.entries();
            }
            return changed ? new CompoundTag(entries) : tag;
        }
        return tag;
    }

    private static int[] replaceIntArray(int[] values, List<UuidMapping> mappings) {
        if (values.length != 4) {
            return values;
        }
        for (UuidMapping mapping : mappings) {
            byte[] from = UuidBinaryCodec.asIntArray(mapping.fromUuid());
            if (matchesIntArray(values, from)) {
                return toIntArray(UuidBinaryCodec.asIntArray(mapping.toUuid()));
            }
        }
        return values;
    }

    private static long[] replaceLongArray(long[] values, List<UuidMapping> mappings) {
        if (values.length != 2) {
            return values;
        }
        for (UuidMapping mapping : mappings) {
            if (values[0] == mapping.fromUuid().getMostSignificantBits()
                && values[1] == mapping.fromUuid().getLeastSignificantBits()) {
                return new long[] {
                    mapping.toUuid().getMostSignificantBits(),
                    mapping.toUuid().getLeastSignificantBits()
                };
            }
        }
        return values;
    }

    private static PairRewrite replaceLongPairs(List<NamedTag> entries, List<UuidMapping> mappings) {
        List<NamedTag> rewritten = entries;
        long replacements = 0;
        for (int mostIndex = 0; mostIndex < rewritten.size(); mostIndex++) {
            NamedTag most = rewritten.get(mostIndex);
            if (!(most.tag() instanceof LongTag mostLong) || !most.name().endsWith("Most")) {
                continue;
            }
            String prefix = most.name().substring(0, most.name().length() - "Most".length());
            int leastIndex = indexOfLong(rewritten, prefix + "Least");
            if (leastIndex < 0) {
                continue;
            }
            long least = ((LongTag) rewritten.get(leastIndex).tag()).value();
            for (UuidMapping mapping : mappings) {
                if (mostLong.value() == mapping.fromUuid().getMostSignificantBits()
                    && least == mapping.fromUuid().getLeastSignificantBits()) {
                    if (rewritten == entries) {
                        rewritten = new ArrayList<>(entries);
                    }
                    rewritten.set(mostIndex, new NamedTag(TAG_LONG, most.name(),
                        new LongTag(mapping.toUuid().getMostSignificantBits())));
                    rewritten.set(leastIndex, new NamedTag(TAG_LONG, prefix + "Least",
                        new LongTag(mapping.toUuid().getLeastSignificantBits())));
                    replacements++;
                    break;
                }
            }
        }
        return new PairRewrite(rewritten, replacements);
    }

    private static int indexOfLong(List<NamedTag> entries, String name) {
        for (int index = 0; index < entries.size(); index++) {
            NamedTag entry = entries.get(index);
            if (entry.name().equals(name) && entry.tag() instanceof LongTag) {
                return index;
            }
        }
        return -1;
    }

    private static boolean matchesIntArray(int[] values, byte[] encoded) {
        int[] expected = toIntArray(encoded);
        return Arrays.equals(values, expected);
    }

    private static int[] toIntArray(byte[] encoded) {
        return new int[] {
            readInt(encoded, 0),
            readInt(encoded, 4),
            readInt(encoded, 8),
            readInt(encoded, 12)
        };
    }

    private static int readInt(byte[] encoded, int offset) {
        return ((encoded[offset] & 0xFF) << 24)
            | ((encoded[offset + 1] & 0xFF) << 16)
            | ((encoded[offset + 2] & 0xFF) << 8)
            | (encoded[offset + 3] & 0xFF);
    }

    private static NamedTag readNamed(byte[] content) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(content))) {
            byte type = input.readByte();
            if (type == TAG_END) {
                throw new IOException("NBT root cannot be TAG_End");
            }
            String name = input.readUTF();
            Tag tag = readPayload(input, type);
            if (input.available() != 0) {
                throw new IOException("Trailing bytes after NBT root");
            }
            return new NamedTag(type, name, tag);
        }
    }

    private static Tag readPayload(DataInputStream input, byte type) throws IOException {
        return switch (type) {
            case TAG_BYTE -> new ByteTag(input.readByte());
            case TAG_SHORT -> new ShortTag(input.readShort());
            case TAG_INT -> new IntTag(input.readInt());
            case TAG_LONG -> new LongTag(input.readLong());
            case TAG_FLOAT -> new FloatTag(input.readFloat());
            case TAG_DOUBLE -> new DoubleTag(input.readDouble());
            case TAG_BYTE_ARRAY -> {
                int length = input.readInt();
                if (length < 0) {
                    throw new IOException("Negative NBT byte array length");
                }
                byte[] values = input.readNBytes(length);
                if (values.length != length) {
                    throw new IOException("Truncated NBT byte array");
                }
                yield new ByteArrayTag(values);
            }
            case TAG_STRING -> new StringTag(input.readUTF());
            case TAG_LIST -> readList(input);
            case TAG_COMPOUND -> readCompound(input);
            case TAG_INT_ARRAY -> readIntArray(input);
            case TAG_LONG_ARRAY -> readLongArray(input);
            default -> throw new IOException("Unsupported NBT tag type: " + type);
        };
    }

    private static ListTag readList(DataInputStream input) throws IOException {
        byte elementType = input.readByte();
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Negative NBT list length");
        }
        List<Tag> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(readPayload(input, elementType));
        }
        return new ListTag(elementType, values);
    }

    private static CompoundTag readCompound(DataInputStream input) throws IOException {
        List<NamedTag> entries = new ArrayList<>();
        while (true) {
            byte type = input.readByte();
            if (type == TAG_END) {
                return new CompoundTag(entries);
            }
            entries.add(new NamedTag(type, input.readUTF(), readPayload(input, type)));
        }
    }

    private static IntArrayTag readIntArray(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Negative NBT int array length");
        }
        int[] values = new int[length];
        for (int index = 0; index < length; index++) {
            values[index] = input.readInt();
        }
        return new IntArrayTag(values);
    }

    private static LongArrayTag readLongArray(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Negative NBT long array length");
        }
        long[] values = new long[length];
        for (int index = 0; index < length; index++) {
            values[index] = input.readLong();
        }
        return new LongArrayTag(values);
    }

    private static byte[] writeNamed(NamedTag tag) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(output)) {
            data.writeByte(tag.type());
            data.writeUTF(tag.name());
            writePayload(data, tag.type(), tag.tag());
        }
        return output.toByteArray();
    }

    private static void writePayload(DataOutputStream output, byte type, Tag tag) throws IOException {
        switch (type) {
            case TAG_BYTE -> output.writeByte(((ByteTag) tag).value());
            case TAG_SHORT -> output.writeShort(((ShortTag) tag).value());
            case TAG_INT -> output.writeInt(((IntTag) tag).value());
            case TAG_LONG -> output.writeLong(((LongTag) tag).value());
            case TAG_FLOAT -> output.writeFloat(((FloatTag) tag).value());
            case TAG_DOUBLE -> output.writeDouble(((DoubleTag) tag).value());
            case TAG_BYTE_ARRAY -> {
                byte[] values = ((ByteArrayTag) tag).value();
                output.writeInt(values.length);
                output.write(values);
            }
            case TAG_STRING -> output.writeUTF(((StringTag) tag).value());
            case TAG_LIST -> writeList(output, (ListTag) tag);
            case TAG_COMPOUND -> writeCompound(output, (CompoundTag) tag);
            case TAG_INT_ARRAY -> {
                int[] values = ((IntArrayTag) tag).value();
                output.writeInt(values.length);
                for (int value : values) {
                    output.writeInt(value);
                }
            }
            case TAG_LONG_ARRAY -> {
                long[] values = ((LongArrayTag) tag).value();
                output.writeInt(values.length);
                for (long value : values) {
                    output.writeLong(value);
                }
            }
            default -> throw new IOException("Unsupported NBT tag type: " + type);
        }
    }

    private static void writeList(DataOutputStream output, ListTag tag) throws IOException {
        output.writeByte(tag.elementType());
        output.writeInt(tag.values().size());
        for (Tag value : tag.values()) {
            writePayload(output, tag.elementType(), value);
        }
    }

    private static void writeCompound(DataOutputStream output, CompoundTag tag) throws IOException {
        for (NamedTag entry : tag.entries()) {
            output.writeByte(entry.type());
            output.writeUTF(entry.name());
            writePayload(output, entry.type(), entry.tag());
        }
        output.writeByte(TAG_END);
    }

    private interface Tag {
    }

    private record NamedTag(byte type, String name, Tag tag) {
    }

    private record ByteTag(byte value) implements Tag {
    }

    private record ShortTag(short value) implements Tag {
    }

    private record IntTag(int value) implements Tag {
    }

    private record LongTag(long value) implements Tag {
    }

    private record FloatTag(float value) implements Tag {
    }

    private record DoubleTag(double value) implements Tag {
    }

    private record ByteArrayTag(byte[] value) implements Tag {
    }

    private record StringTag(String value) implements Tag {
    }

    private record ListTag(byte elementType, List<Tag> values) implements Tag {
    }

    private record CompoundTag(List<NamedTag> entries) implements Tag {
        Optional<NamedTag> child(String name) {
            return entries.stream()
                .filter(entry -> entry.name().equals(name))
                .findFirst();
        }
    }

    private record IntArrayTag(int[] value) implements Tag {
    }

    private record LongArrayTag(long[] value) implements Tag {
    }

    private record PairRewrite(List<NamedTag> entries, long replacements) {
    }

    private static final class Counter {
        long value;
    }
}
