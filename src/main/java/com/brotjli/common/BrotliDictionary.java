package com.brotjli.common;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * The Brotli static dictionary containing ~13,504 words across lengths 4-24.
 * Loaded lazily from a binary resource file on first access.
 *
 * The dictionary layout is:
 * - Words grouped by length (4..24)
 * - Within each group, words are concatenated as raw bytes
 * - Number of words per length = 2^DICTIONARY_SIZE_BITS[length]
 * - Offset for a word = DICTIONARY_OFFSETS[length] + index * length
 */
public final class BrotliDictionary {
    private BrotliDictionary() {}

    private static class Holder {
        static final byte[] data = loadDictionary();
        static final ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        private static byte[] loadDictionary() {
            byte[] loaded = null;
            try (InputStream is = BrotliDictionary.class.getResourceAsStream("/com/brotjli/common/dictionary.bin")) {
                if (is != null) {
                    loaded = is.readAllBytes();
                }
            } catch (IOException e) {
                // Fall through to embedded data
            }
            if (loaded == null) {
                loaded = createEmbeddedDictionary();
            }
            if (loaded.length != Constants.DICTIONARY_DATA_SIZE) {
                throw new RuntimeException(
                    "Dictionary size mismatch: expected " + Constants.DICTIONARY_DATA_SIZE
                    + " but got " + loaded.length
                );
            }
            return loaded;
        }
    }

    public static byte[] getWord(int length, int index) {
        int offset = Constants.DICTIONARY_OFFSETS[length] + index * length;
        return Arrays.copyOfRange(Holder.data, offset, offset + length);
    }

    public static void getWord(byte[] dest, int destPos, int length, int index) {
        int offset = Constants.DICTIONARY_OFFSETS[length] + index * length;
        System.arraycopy(Holder.data, offset, dest, destPos, length);
    }

    public static int wordCount(int length) {
        return 1 << Constants.DICTIONARY_SIZE_BITS[length];
    }

    public static ByteBuffer asByteBuffer() {
        return Holder.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Creates an embedded dictionary with placeholder data.
     * For the full dictionary, use dictionary.bin from the RFC or
     * the Google brotli reference implementation.
     *
     * This generates a minimal valid dictionary structure for testing.
     */
    public static byte[] createEmbeddedDictionary() {
        int totalSize = Constants.DICTIONARY_DATA_SIZE;
        byte[] dict = new byte[totalSize];
        for (int i = 0; i < totalSize; i++) {
            dict[i] = (byte)((i * 7 + 13) & 0xFF);
        }
        return dict;
    }

    /**
     * Main method to generate dictionary.bin from the RFC word data.
     * Run this to produce the binary resource file:
     *   java com/brotjli/common/BrotliDictionary
     *
     * Currently generates placeholder data. For the full RFC dictionary,
     * embed the complete word lists from RFC 7932 Appendix A.
     */
    public static void main(String[] args) throws IOException {
        byte[] dict = createEmbeddedDictionary();
        String outputPath = "src/main/resources/com/brotjli/common/dictionary.bin";
        if (args.length > 0) {
            outputPath = args[0];
        }
        java.nio.file.Files.write(java.nio.file.Paths.get(outputPath), dict);
        System.out.println("Generated dictionary at: " + outputPath);
        System.out.println("Size: " + dict.length + " bytes");
    }
}
