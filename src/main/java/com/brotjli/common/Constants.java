package com.brotjli.common;

/**
 * Brotli format constants.
 *
 * <p>This class contains all compile-time constants defined by the Brotli specification
 * (RFC 7932), including Huffman coding limits, window sizes, alphabet sizes, block
 * switching parameters, code length encoding tables, context modes, dictionary layout
 * metadata, and insert/copy length encoding tables.
 */
public final class Constants {
    private Constants() {}

    // Huffman coding limits
    public static final int MAX_BITS = 15;
    public static final int MAX_SYMBOL_BITS = 15;

    // Window size limits
    public static final int MIN_WBITS = 10;
    public static final int MAX_WBITS = 24;
    public static final int MIN_WINDOW_SIZE = (1 << 10) - 16;   // 1008
    public static final int MAX_WINDOW_SIZE = (1 << 24) - 16;   // 16777200

    // Alphabet sizes
    public static final int LITERAL_ALPHABET_SIZE = 256;
    public static final int INSERT_COPY_ALPHABET_SIZE = 704;
    public static final int MAX_DISTANCE_ALPHABET_SIZE = 16 + 120 + (48 << 3); // 520

    // Block switching
    public static final int BLOCK_TYPE_ALPHABET_OFFSET = 2;
    public static final int MAX_BLOCK_TYPES = 256;
    public static final int BLOCK_COUNT_ALPHABET_SIZE = 26;

    // Code length encoding
    public static final int CODE_LENGTH_CODES = 18;
    public static final int[] CODE_LENGTH_ORDER = {
        1, 2, 3, 4, 0, 5, 17, 6, 16, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };

    // Context modes
    public static final int CONTEXT_LSB6 = 0;
    public static final int CONTEXT_MSB6 = 1;
    public static final int CONTEXT_UTF8 = 2;
    public static final int CONTEXT_SIGNED = 3;
    public static final int NUM_CONTEXT_MODES = 4;

    // Context map sizes
    public static final int LITERAL_CONTEXT_MAP_SIZE = 64;
    public static final int DISTANCE_CONTEXT_MAP_SIZE = 4;

    // Dictionary
    public static final int DICTIONARY_SIZE_BITS_LENGTH = 32;
    public static final int[] DICTIONARY_SIZE_BITS = {
        0, 0, 0, 0, 10, 10, 11, 11, 10, 10, 10, 10, 10, 9, 9, 8,
        7, 7, 8, 7, 7, 6, 6, 5, 5, 0, 0, 0, 0, 0, 0, 0
    };
    public static final int MIN_DICT_WORD_LENGTH = 4;
    public static final int MAX_DICT_WORD_LENGTH = 24;
    public static final int NUM_DICT_TRANSFORMS = 121;

    // Precomputed dictionary offsets (DOFFSET[length] for length 0..24, then DICTSIZE)
    public static final int[] DICTIONARY_OFFSETS = {
        0, 0, 0, 0, 0, 4096, 9216, 21504, 35840, 44032, 53248,
        63488, 74752, 87040, 93696, 100864, 104704, 106752, 108928,
        113536, 115968, 118528, 119872, 121280, 122016, 122784
    };
    public static final int DICTIONARY_DATA_SIZE = 122784;

    // Insert-and-copy length encoding (indices 0..23)
    public static final int[] INSERT_LENGTH_EXTRA_BITS = {
        0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 12, 14, 24
    };
    public static final int[] INSERT_LENGTH_BASE = {
        0, 1, 2, 3, 4, 5, 6, 8, 10, 14, 18, 26, 34, 50, 66, 98, 130, 194, 322, 578, 1090, 2114, 6210, 22594
    };
    public static final int[] COPY_LENGTH_EXTRA_BITS = {
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 9, 10, 24
    };
    public static final int[] COPY_LENGTH_BASE = {
        2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 18, 22, 30, 38, 54, 70, 102, 134, 198, 326, 582, 1094, 2118
    };

    // Block count encoding
    public static final int[] BLOCK_COUNT_EXTRA_BITS = {
        2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 7, 8, 9, 10, 11, 12, 13, 24
    };
    public static final int[] BLOCK_COUNT_BASE = {
        1, 5, 9, 13, 17, 25, 33, 41, 49, 65, 81, 97, 113, 145, 177, 209, 241, 305, 369, 497, 753, 1265, 2289, 4337, 8433, 16625
    };

    // Encoding quality levels
    public static final int QUALITY_STORE = 0;
    public static final int QUALITY_SIMPLE = 1;
    public static final int QUALITY_NORMAL = 2;
    public static final int QUALITY_HIGH = 3;
    public static final int MIN_QUALITY = 0;
    public static final int MAX_QUALITY = 11;

    // Window sizes per quality level (RFC 7932)
    public static final int[] QUALITY_WBITS = {
        16, // q=0  — 64KB
        16, // q=1  — 64KB
        18, // q=2  — 256KB
        19, // q=3  — 512KB
        20, // q=4  — 1MB
        20, // q=5  — 1MB
        21, // q=6  — 2MB
        21, // q=7  — 2MB
        22, // q=8  — 4MB
        22, // q=9  — 4MB
        22, // q=10 — 4MB
        24  // q=11 — 16MB
    };
}
