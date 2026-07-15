package com.cookiebuild.microbattles.map;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.NumberBinaryTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * Repairs invalid sign block entities in an extracted Anvil world before Paper
 * loads any chunk. Legacy Cookie Build maps can contain a Sign NBT record whose
 * block was later replaced with bedrock. Paper rejects that pending record only
 * when a player starts tracking the chunk, which is too late for a Bukkit chunk
 * listener to prevent the server ERROR.
 */
final class AnvilBlockEntitySanitizer {
    private static final int REGION_HEADER_BYTES = 8192;
    private static final int SECTOR_BYTES = 4096;
    private static final long MAX_NBT_BYTES = 64L * 1024L * 1024L;
    private static final int DATA_VERSION_NON_STRETCHED_BLOCK_STATES = 2529;

    private AnvilBlockEntitySanitizer() {
    }

    /**
     * Applies the audited repair plan for recovered Cookie Build templates.
     * Limiting the scan to the affected game-5 chunk avoids re-reading every
     * region on every standby-arena creation while retaining state validation.
     */
    static int sanitizeKnownMap(String mapName, Path worldDirectory) throws IOException {
        if (!"game-5".equals(mapName)) {
            return 0;
        }
        return sanitizeRegionChunk(worldDirectory.resolve("region").resolve("r.-1.0.mca"), 31);
    }

    static int sanitizeWorld(Path worldDirectory) throws IOException {
        if (!Files.isDirectory(worldDirectory)) {
            throw new IOException("Extracted map directory does not exist: " + worldDirectory);
        }

        List<Path> regionFiles;
        try (Stream<Path> files = Files.walk(worldDirectory)) {
            regionFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getParent() != null
                            && path.getParent().getFileName().toString().equals("region"))
                    .filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .sorted()
                    .toList();
        }

        int removed = 0;
        for (Path regionFile : regionFiles) {
            removed += sanitizeRegionFile(regionFile);
        }
        return removed;
    }

    static int sanitizeRegionFile(Path regionFile) throws IOException {
        return sanitizeRegionFile(regionFile, ignored -> true);
    }

    private static int sanitizeRegionChunk(Path regionFile, int chunkIndex) throws IOException {
        if (chunkIndex < 0 || chunkIndex >= 1024) {
            throw new IllegalArgumentException("Anvil chunk header index must be between 0 and 1023");
        }
        return sanitizeRegionFile(regionFile, index -> index == chunkIndex);
    }

    private static int sanitizeRegionFile(Path regionFile, IntPredicate chunkFilter) throws IOException {
        if (Files.size(regionFile) < REGION_HEADER_BYTES) {
            throw new IOException("Invalid Anvil region header: " + regionFile);
        }

        Path temporaryFile = regionFile.resolveSibling(regionFile.getFileName()
                + ".sanitize-" + UUID.randomUUID() + ".tmp");
        Files.copy(regionFile, temporaryFile, StandardCopyOption.COPY_ATTRIBUTES);

        int removed = 0;
        try {
            try (RandomAccessFile file = new RandomAccessFile(temporaryFile.toFile(), "rw")) {
                for (int chunkIndex = 0; chunkIndex < 1024; chunkIndex++) {
                    if (!chunkFilter.test(chunkIndex)) {
                        continue;
                    }
                    file.seek((long) chunkIndex * 4L);
                    int sectorOffset = (file.readUnsignedByte() << 16)
                            | (file.readUnsignedByte() << 8)
                            | file.readUnsignedByte();
                    int sectorCount = file.readUnsignedByte();
                    if (sectorOffset == 0 && sectorCount == 0) {
                        continue;
                    }
                    if (sectorOffset < 2 || sectorCount == 0
                            || ((long) sectorOffset + sectorCount) * SECTOR_BYTES > file.length()) {
                        throw new IOException("Invalid chunk location in " + regionFile
                                + " at header index " + chunkIndex);
                    }

                    long chunkOffset = (long) sectorOffset * SECTOR_BYTES;
                    file.seek(chunkOffset);
                    int storedLength = file.readInt();
                    int allocatedPayloadBytes = sectorCount * SECTOR_BYTES - Integer.BYTES;
                    if (storedLength < 1 || storedLength > allocatedPayloadBytes) {
                        throw new IOException("Invalid chunk length in " + regionFile
                                + " at header index " + chunkIndex);
                    }

                    int compressionType = file.readUnsignedByte();
                    if ((compressionType & 0x80) != 0) {
                        throw new IOException("External Anvil chunk streams are not supported in " + regionFile);
                    }
                    byte[] compressedNbt = new byte[storedLength - 1];
                    file.readFully(compressedNbt);

                    Map.Entry<String, CompoundBinaryTag> namedRoot = readChunk(compressedNbt, compressionType);
                    ChunkSanitization sanitization = sanitizeChunk(namedRoot.getValue());
                    if (sanitization.removed() == 0) {
                        continue;
                    }

                    byte[] updatedNbt = writeChunk(
                            Map.entry(namedRoot.getKey(), sanitization.root()), compressionType);
                    int updatedStoredLength = updatedNbt.length + 1;
                    if (updatedStoredLength > allocatedPayloadBytes) {
                        throw new IOException("Sanitized chunk no longer fits its allocated sectors in "
                                + regionFile + " at header index " + chunkIndex);
                    }

                    file.seek(chunkOffset);
                    file.writeInt(updatedStoredLength);
                    file.writeByte(compressionType);
                    file.write(updatedNbt);
                    int remaining = allocatedPayloadBytes - updatedStoredLength;
                    if (remaining > 0) {
                        file.write(new byte[remaining]);
                    }
                    removed += sanitization.removed();
                }
                if (removed > 0) {
                    file.getChannel().force(true);
                }
            }

            if (removed == 0) {
                Files.deleteIfExists(temporaryFile);
                return 0;
            }
            replaceAtomically(temporaryFile, regionFile);
            return removed;
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(temporaryFile);
            throw failure;
        }
    }

    static ChunkSanitization sanitizeChunk(CompoundBinaryTag root) {
        CompoundBinaryTag container = root.contains("Level", BinaryTagTypes.COMPOUND)
                ? root.getCompound("Level")
                : root;
        String blockEntitiesKey = container.contains("block_entities", BinaryTagTypes.LIST)
                ? "block_entities"
                : container.contains("TileEntities", BinaryTagTypes.LIST) ? "TileEntities" : null;
        if (blockEntitiesKey == null) {
            return new ChunkSanitization(root, 0);
        }

        ListBinaryTag blockEntities = container.getList(blockEntitiesKey, BinaryTagTypes.COMPOUND);
        List<BinaryTag> retained = new ArrayList<>(blockEntities.size());
        int removed = 0;
        for (BinaryTag tag : blockEntities) {
            if (!(tag instanceof CompoundBinaryTag blockEntity)
                    || !isSignBlockEntity(blockEntity)
                    || signBlockValidity(container, root, blockEntity) != Validity.INVALID) {
                retained.add(tag);
            } else {
                removed++;
            }
        }
        if (removed == 0) {
            return new ChunkSanitization(root, 0);
        }

        ListBinaryTag updatedEntities = ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, retained);
        CompoundBinaryTag updatedContainer = container.put(blockEntitiesKey, updatedEntities);
        CompoundBinaryTag updatedRoot = root.contains("Level", BinaryTagTypes.COMPOUND)
                ? root.put("Level", updatedContainer)
                : updatedContainer;
        return new ChunkSanitization(updatedRoot, removed);
    }

    private static boolean isSignBlockEntity(CompoundBinaryTag blockEntity) {
        String id = blockEntity.getString("id", "").toLowerCase(Locale.ROOT);
        return id.equals("sign") || id.equals("hanging_sign")
                || id.endsWith(":sign") || id.endsWith(":hanging_sign");
    }

    private static Validity signBlockValidity(CompoundBinaryTag chunk, CompoundBinaryTag root,
            CompoundBinaryTag blockEntity) {
        if (!hasNumericCoordinate(blockEntity, "x") || !hasNumericCoordinate(blockEntity, "y")
                || !hasNumericCoordinate(blockEntity, "z")) {
            return Validity.UNKNOWN;
        }
        int x = ((NumberBinaryTag) blockEntity.get("x")).intValue();
        int y = ((NumberBinaryTag) blockEntity.get("y")).intValue();
        int z = ((NumberBinaryTag) blockEntity.get("z")).intValue();

        CompoundBinaryTag section = findSection(chunk, Math.floorDiv(y, 16));
        if (section == null) {
            return Validity.UNKNOWN;
        }
        int blockIndex = ((Math.floorMod(y, 16) * 16) + Math.floorMod(z, 16)) * 16
                + Math.floorMod(x, 16);

        if (section.contains("Blocks", BinaryTagTypes.BYTE_ARRAY)) {
            return legacySignValidity(section, blockIndex);
        }

        String blockName = modernBlockName(section, blockIndex, root.getInt("DataVersion", -1));
        if (blockName == null) {
            return Validity.UNKNOWN;
        }
        String normalizedName = blockName.toLowerCase(Locale.ROOT);
        return normalizedName.endsWith("_sign") || normalizedName.endsWith(":sign")
                ? Validity.VALID
                : Validity.INVALID;
    }

    private static boolean hasNumericCoordinate(CompoundBinaryTag blockEntity, String key) {
        return blockEntity.get(key) instanceof NumberBinaryTag;
    }

    private static CompoundBinaryTag findSection(CompoundBinaryTag chunk, int sectionY) {
        String sectionsKey = chunk.contains("sections", BinaryTagTypes.LIST)
                ? "sections"
                : chunk.contains("Sections", BinaryTagTypes.LIST) ? "Sections" : null;
        if (sectionsKey == null) {
            return null;
        }
        for (BinaryTag tag : chunk.getList(sectionsKey, BinaryTagTypes.COMPOUND)) {
            if (tag instanceof CompoundBinaryTag section && section.get("Y") instanceof NumberBinaryTag y
                    && y.intValue() == sectionY) {
                return section;
            }
        }
        return null;
    }

    private static Validity legacySignValidity(CompoundBinaryTag section, int blockIndex) {
        byte[] blocks = section.getByteArray("Blocks");
        if (blockIndex >= blocks.length) {
            return Validity.UNKNOWN;
        }
        int blockId = blocks[blockIndex] & 0xff;
        if (section.contains("Add", BinaryTagTypes.BYTE_ARRAY)) {
            byte[] additionalIds = section.getByteArray("Add");
            if (blockIndex / 2 >= additionalIds.length) {
                return Validity.UNKNOWN;
            }
            blockId |= nibble(additionalIds, blockIndex) << 8;
        }
        return blockId == 63 || blockId == 68 ? Validity.VALID : Validity.INVALID;
    }

    private static int nibble(byte[] values, int index) {
        int packed = values[index / 2] & 0xff;
        return index % 2 == 0 ? packed & 0x0f : packed >>> 4;
    }

    private static String modernBlockName(CompoundBinaryTag section, int blockIndex, int dataVersion) {
        CompoundBinaryTag blockStates;
        String paletteKey;
        String dataKey;
        if (section.contains("block_states", BinaryTagTypes.COMPOUND)) {
            blockStates = section.getCompound("block_states");
            paletteKey = "palette";
            dataKey = "data";
        } else if (section.contains("Palette", BinaryTagTypes.LIST)) {
            blockStates = section;
            paletteKey = "Palette";
            dataKey = "BlockStates";
        } else {
            return null;
        }

        ListBinaryTag palette = blockStates.getList(paletteKey, BinaryTagTypes.COMPOUND);
        if (palette.isEmpty()) {
            return null;
        }
        int paletteIndex;
        if (palette.size() == 1) {
            paletteIndex = 0;
        } else {
            long[] data = blockStates.getLongArray(dataKey, null);
            if (data == null || data.length == 0) {
                return null;
            }
            int bitsPerBlock = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            paletteIndex = dataVersion >= DATA_VERSION_NON_STRETCHED_BLOCK_STATES
                    ? unpackNonStretched(data, blockIndex, bitsPerBlock)
                    : unpackStretched(data, blockIndex, bitsPerBlock);
        }
        if (paletteIndex < 0 || paletteIndex >= palette.size()) {
            return null;
        }
        return palette.getCompound(paletteIndex).getString("Name", null);
    }

    private static int unpackNonStretched(long[] data, int blockIndex, int bitsPerBlock) {
        int valuesPerLong = 64 / bitsPerBlock;
        int dataIndex = blockIndex / valuesPerLong;
        if (dataIndex >= data.length) {
            return -1;
        }
        int bitOffset = blockIndex % valuesPerLong * bitsPerBlock;
        long mask = (1L << bitsPerBlock) - 1L;
        return (int) ((data[dataIndex] >>> bitOffset) & mask);
    }

    private static int unpackStretched(long[] data, int blockIndex, int bitsPerBlock) {
        long bitIndex = (long) blockIndex * bitsPerBlock;
        int dataIndex = (int) (bitIndex >>> 6);
        int bitOffset = (int) (bitIndex & 63);
        if (dataIndex >= data.length) {
            return -1;
        }
        long value = data[dataIndex] >>> bitOffset;
        if (bitOffset + bitsPerBlock > 64) {
            if (++dataIndex >= data.length) {
                return -1;
            }
            value |= data[dataIndex] << (64 - bitOffset);
        }
        return (int) (value & ((1L << bitsPerBlock) - 1L));
    }

    private static Map.Entry<String, CompoundBinaryTag> readChunk(byte[] compressedNbt, int compressionType)
            throws IOException {
        try (InputStream rawInput = new ByteArrayInputStream(compressedNbt);
                InputStream decompressed = decompressionStream(rawInput, compressionType);
                DataInputStream input = new DataInputStream(decompressed)) {
            return BinaryTagIO.reader(MAX_NBT_BYTES).readNamed((DataInput) input);
        }
    }

    private static byte[] writeChunk(Map.Entry<String, CompoundBinaryTag> namedRoot, int compressionType)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (OutputStream compressed = compressionStream(output, compressionType);
                DataOutputStream dataOutput = new DataOutputStream(compressed)) {
            BinaryTagIO.writer().writeNamed(namedRoot, (DataOutput) dataOutput);
        }
        return output.toByteArray();
    }

    private static InputStream decompressionStream(InputStream input, int compressionType) throws IOException {
        return switch (compressionType) {
            case 1 -> new GZIPInputStream(input);
            case 2 -> new InflaterInputStream(input);
            case 3 -> input;
            default -> throw new IOException("Unsupported Anvil compression type: " + compressionType);
        };
    }

    private static OutputStream compressionStream(OutputStream output, int compressionType) throws IOException {
        return switch (compressionType) {
            case 1 -> new GZIPOutputStream(output);
            case 2 -> new DeflaterOutputStream(output);
            case 3 -> output;
            default -> throw new IOException("Unsupported Anvil compression type: " + compressionType);
        };
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private enum Validity {
        VALID,
        INVALID,
        UNKNOWN
    }

    record ChunkSanitization(CompoundBinaryTag root, int removed) {
    }
}
