package com.cookiebuild.microbattles.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnvilBlockEntitySanitizerTest {
    private static final int INVALID_X = -2;
    private static final int VALID_X = -1;
    private static final int Y = 39;
    private static final int Z = 1;

    @Test
    void removesLegacySignAttachedToBedrockButPreservesRealSignAndOtherBlockEntities() {
        CompoundBinaryTag root = legacyChunk();

        AnvilBlockEntitySanitizer.ChunkSanitization result =
                AnvilBlockEntitySanitizer.sanitizeChunk(root);

        assertEquals(1, result.removed());
        ListBinaryTag remaining = result.root().getCompound("Level")
                .getList("TileEntities", BinaryTagTypes.COMPOUND);
        assertEquals(2, remaining.size());
        assertEquals("Sign", remaining.getCompound(0).getString("id"));
        assertEquals(VALID_X, remaining.getCompound(0).getInt("x"));
        assertEquals("Chest", remaining.getCompound(1).getString("id"));

        byte[] originalBlocks = root.getCompound("Level")
                .getList("Sections", BinaryTagTypes.COMPOUND).getCompound(0).getByteArray("Blocks");
        byte[] updatedBlocks = result.root().getCompound("Level")
                .getList("Sections", BinaryTagTypes.COMPOUND).getCompound(0).getByteArray("Blocks");
        assertArrayEquals(originalBlocks, updatedBlocks, "terrain data must remain byte-for-byte unchanged");
    }

    @Test
    void removesModernSignOnlyWhenPaletteStateIsNotASign() {
        int invalidIndex = blockIndex(INVALID_X, Y, Z);
        int validIndex = blockIndex(VALID_X, Y, Z);
        long[] data = new long[256];
        setNonStretchedPaletteIndex(data, validIndex, 4, 1);

        ListBinaryTag palette = compounds(
                CompoundBinaryTag.builder().putString("Name", "minecraft:bedrock").build(),
                CompoundBinaryTag.builder().putString("Name", "minecraft:oak_wall_sign").build());
        CompoundBinaryTag section = CompoundBinaryTag.builder()
                .putByte("Y", (byte) Math.floorDiv(Y, 16))
                .put("block_states", CompoundBinaryTag.builder()
                        .put("palette", palette)
                        .putLongArray("data", data)
                        .build())
                .build();
        CompoundBinaryTag root = CompoundBinaryTag.builder()
                .putInt("DataVersion", 4000)
                .put("sections", compounds(section))
                .put("block_entities", compounds(sign(INVALID_X), sign(VALID_X)))
                .build();

        AnvilBlockEntitySanitizer.ChunkSanitization result =
                AnvilBlockEntitySanitizer.sanitizeChunk(root);

        assertEquals(1, result.removed());
        ListBinaryTag remaining = result.root().getList("block_entities", BinaryTagTypes.COMPOUND);
        assertEquals(1, remaining.size());
        assertEquals(VALID_X, remaining.getCompound(0).getInt("x"));
    }

    @Test
    void atomicallyRewritesRegionChunkBeforeWorldLoad(@TempDir Path temporaryDirectory) throws Exception {
        Path regionDirectory = Files.createDirectories(temporaryDirectory.resolve("region"));
        Path regionFile = regionDirectory.resolve("r.-1.0.mca");
        writeSingleChunkRegion(regionFile, legacyChunk());

        assertEquals(1, AnvilBlockEntitySanitizer.sanitizeWorld(temporaryDirectory));
        CompoundBinaryTag updated = readSingleChunkRegion(regionFile);
        ListBinaryTag remaining = updated.getCompound("Level")
                .getList("TileEntities", BinaryTagTypes.COMPOUND);
        assertEquals(2, remaining.size());
        assertEquals(0, AnvilBlockEntitySanitizer.sanitizeWorld(temporaryDirectory),
                "sanitization must be idempotent");
    }

    @Test
    void knownMapRepairTargetsOnlyRecoveredGameFiveChunk(@TempDir Path temporaryDirectory) throws Exception {
        Path regionDirectory = Files.createDirectories(temporaryDirectory.resolve("region"));
        writeSingleChunkRegion(regionDirectory.resolve("r.-1.0.mca"), 31, legacyChunk());

        assertEquals(0, AnvilBlockEntitySanitizer.sanitizeKnownMap("game-4", temporaryDirectory));
        assertEquals(1, AnvilBlockEntitySanitizer.sanitizeKnownMap("game-5", temporaryDirectory));
        assertEquals(0, AnvilBlockEntitySanitizer.sanitizeKnownMap("game-5", temporaryDirectory));
    }

    private static CompoundBinaryTag legacyChunk() {
        byte[] blocks = new byte[4096];
        blocks[blockIndex(INVALID_X, Y, Z)] = 7; // bedrock
        blocks[blockIndex(VALID_X, Y, Z)] = 63; // standing sign
        CompoundBinaryTag section = CompoundBinaryTag.builder()
                .putByte("Y", (byte) Math.floorDiv(Y, 16))
                .putByteArray("Blocks", blocks)
                .build();
        CompoundBinaryTag chest = CompoundBinaryTag.builder()
                .putString("id", "Chest")
                .putInt("x", 4)
                .putInt("y", Y)
                .putInt("z", Z)
                .build();
        return CompoundBinaryTag.builder()
                .put("Level", CompoundBinaryTag.builder()
                        .put("Sections", compounds(section))
                        .put("TileEntities", compounds(sign(INVALID_X), sign(VALID_X), chest))
                        .build())
                .build();
    }

    private static CompoundBinaryTag sign(int x) {
        return CompoundBinaryTag.builder()
                .putString("id", "Sign")
                .putInt("x", x)
                .putInt("y", Y)
                .putInt("z", Z)
                .build();
    }

    private static ListBinaryTag compounds(CompoundBinaryTag... tags) {
        return ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, List.<BinaryTag>of(tags));
    }

    private static int blockIndex(int x, int y, int z) {
        return ((Math.floorMod(y, 16) * 16) + Math.floorMod(z, 16)) * 16 + Math.floorMod(x, 16);
    }

    private static void setNonStretchedPaletteIndex(long[] data, int blockIndex, int bitsPerBlock, int value) {
        int valuesPerLong = 64 / bitsPerBlock;
        int dataIndex = blockIndex / valuesPerLong;
        int bitOffset = blockIndex % valuesPerLong * bitsPerBlock;
        data[dataIndex] |= (long) value << bitOffset;
    }

    private static void writeSingleChunkRegion(Path regionFile, CompoundBinaryTag root) throws Exception {
        writeSingleChunkRegion(regionFile, 0, root);
    }

    private static void writeSingleChunkRegion(Path regionFile, int chunkIndex, CompoundBinaryTag root)
            throws Exception {
        byte[] compressed = compress(root);
        int storedLength = compressed.length + 1;
        int sectorCount = Math.max(1, (storedLength + Integer.BYTES + 4095) / 4096);
        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "rw")) {
            file.setLength((2L + sectorCount) * 4096L);
            file.seek((long) chunkIndex * 4L);
            file.writeByte(0);
            file.writeByte(0);
            file.writeByte(2);
            file.writeByte(sectorCount);
            file.seek(8192);
            file.writeInt(storedLength);
            file.writeByte(2);
            file.write(compressed);
        }
    }

    private static CompoundBinaryTag readSingleChunkRegion(Path regionFile) throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "r")) {
            file.seek(8192);
            int storedLength = file.readInt();
            assertEquals(2, file.readUnsignedByte());
            byte[] compressed = new byte[storedLength - 1];
            file.readFully(compressed);
            try (DataInputStream input = new DataInputStream(
                    new InflaterInputStream(new ByteArrayInputStream(compressed)))) {
                return BinaryTagIO.reader().readNamed((DataInput) input).getValue();
            }
        }
    }

    private static byte[] compress(CompoundBinaryTag root) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            BinaryTagIO.writer().writeNamed(Map.entry("", root), (DataOutput) output);
        }
        return bytes.toByteArray();
    }

}
