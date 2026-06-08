package com.brotjli.decoder;

import com.brotjli.common.Constants;
import java.util.Arrays;

/**
 * Canonical Huffman decoder for Brotli.
 *
 * Uses a two-level lookup table for fast decoding:
 * - Level 1: 10-bit lookup (1024 entries)
 * - Level 2: sub-tables for codes longer than 10 bits
 */
public final class HuffmanDecoder {
    private static final int MAX_BITS = 15;
    private static final int TABLE_BITS = 10;
    private static final int TABLE_SIZE = 1 << TABLE_BITS;
    private static final int MAX_SUBTABLE_BITS = 5;
    private static final int ENTRY_MASK_SYMBOL = 0x0000FFFF; // 16 bits for symbol/subOff
    private static final int ENTRY_SHIFT_BITS = 16;
    private static final int ENTRY_MASK_FLAG = 0x01000000;  // bit 24 for subtable flag
    private static final int SUBTABLE_MASK_OFFSET = 0x000FFFFF; // 20 bits for subtable offset
    private static final int SUBTABLE_SHIFT_BITS = 20;

    private int[] table;
    private int maxSymbol;

    public HuffmanDecoder() {
        this.table = new int[TABLE_SIZE];
        this.maxSymbol = 0;
    }

    /**
     * Build a Huffman table from code lengths.
     *
     * @param codeLengths array of code lengths (indexed by symbol value)
     * @param alphabetSize number of symbols
     */
    public void buildFromCodeLengths(int[] codeLengths, int alphabetSize) {
        this.maxSymbol = 0;
        Arrays.fill(table, 0);
        int maxLen = 0;
        int nonZeroCount = 0;
        for (int i = 0; i < alphabetSize; i++) {
            if (codeLengths[i] > 0) {
                maxLen = Math.max(maxLen, codeLengths[i]);
                maxSymbol = i;
                nonZeroCount++;
            }
        }
        if (nonZeroCount == 0) {
            throw new IllegalArgumentException("No non-zero code lengths");
        }

        int[] blCount = new int[MAX_BITS + 1];
        for (int i = 0; i < alphabetSize; i++) {
            int len = codeLengths[i];
            if (len > 0) {
                blCount[len]++;
            }
        }

        int[] nextCode = new int[MAX_BITS + 1];
        int code = 0;
        for (int bits = 1; bits <= MAX_BITS; bits++) {
            code = (code + blCount[bits - 1]) << 1;
            nextCode[bits] = code;
        }

        int[] reversedCodes = new int[alphabetSize];
        for (int sym = 0; sym < alphabetSize; sym++) {
            int len = codeLengths[sym];
            if (len > 0) {
                int c = nextCode[len];
                nextCode[len]++;
                reversedCodes[sym] = reverseBits(c, len);
            }
        }

        buildTableFromCodes(alphabetSize, codeLengths, reversedCodes);
    }

    /**
     * Decode a single symbol from the bit reader.
     */
    public int decodeSymbol(BitReader reader) {
        int peek = (int)reader.peekBits(TABLE_BITS);
        int entry = table[peek];

        if ((entry & ENTRY_MASK_FLAG) != 0) {
            int subtableOffset = entry & SUBTABLE_MASK_OFFSET;
            int subBits = (entry >>> SUBTABLE_SHIFT_BITS) & 0xF;
            int subIndex = (int)((reader.peekBits(TABLE_BITS + subBits) >>> TABLE_BITS) & ((1 << subBits) - 1));
            entry = table[subtableOffset + subIndex];
            int len = (entry >>> ENTRY_SHIFT_BITS) & 0xF;
            reader.skipBits(len);
            return entry & ENTRY_MASK_SYMBOL;
        }

        int symbol = entry & ENTRY_MASK_SYMBOL;
        int bitsUsed = (entry >>> ENTRY_SHIFT_BITS) & 0xF;
        reader.skipBits(bitsUsed);
        return symbol;
    }

    /**
     * Build a prefix code table for simple prefix codes with 1-4 symbols.
     */
    public void buildSimplePrefixCode(int[] symbols, int nsym, int treeSelect, int alphabetBits) {
        if (nsym == 1) {
            // Single symbol: 0-bit code, always returned
            int entry = symbols[0];
            Arrays.fill(table, entry);
            this.maxSymbol = symbols[0];
            return;
        }
        int[] codeLengths = new int[1 << alphabetBits];
        Arrays.fill(codeLengths, 0);

        int[] lengths;
        if (nsym == 2) {
            lengths = new int[]{1, 1};
        } else if (nsym == 3) {
            lengths = new int[]{1, 2, 2};
        } else {
            if (treeSelect == 0) {
                lengths = new int[]{2, 2, 2, 2};
            } else {
                lengths = new int[]{1, 2, 3, 3};
            }
        }

        for (int i = 0; i < nsym; i++) {
            codeLengths[symbols[i]] = lengths[i];
        }
        buildFromCodeLengths(codeLengths, codeLengths.length);
    }

    /** Build a table for a single-symbol alphabet (0-bit code). */
    public void buildSingleSymbol(int symbol) {
        Arrays.fill(table, 0);
        int entry = symbol;
        for (int i = 0; i < TABLE_SIZE; i++) {
            table[i] = entry;
        }
        this.maxSymbol = symbol;
    }

    public int getMaxSymbol() { return maxSymbol; }

    private void buildTableFromCodes(int alphabetSize, int[] codeLengths, int[] reversedCodes) {
        Arrays.fill(table, 0);

        int maxLen = maxCodeLength(codeLengths, alphabetSize);
        if (maxLen == 0) {
            for (int sym = 0; sym < alphabetSize; sym++) {
                if (codeLengths[sym] == 0) {
                    for (int i = 0; i < TABLE_SIZE; i++) {
                        table[i] = sym;
                    }
                    return;
                }
            }
        }

        // Collect symbols by decreasing code length so longer codes
        // take priority over shorter codes that share the same prefix.
        java.util.ArrayList<Integer> ordered = new java.util.ArrayList<>();
        for (int i = 0; i < alphabetSize; i++) {
            if (codeLengths[i] > 0) ordered.add(i);
        }
        ordered.sort((a, b) -> Integer.compare(codeLengths[b], codeLengths[a]));

        for (int sym : ordered) {
            int len = codeLengths[sym];
            if (len == 0) continue;

            // The encoder uses writeBitsReversed(canonicalCode, len) which
            // reverses the MSB-first canonical code and writes it LSB-first.
            // peekBits(len) returns those reversed bits as an integer value.
            // The reversedCodes[] stores reverseBits(canonicalCode, len),
            // which matches what peekBits(len) returns. Use it as table index.
            int revCode = reversedCodes[sym];
            int entry = sym | (len << ENTRY_SHIFT_BITS);

            if (len <= TABLE_BITS) {
                // Fill all 2^(TABLE_BITS - len) entries matching this prefix
                int count = 1 << (TABLE_BITS - len);
                for (int i = 0; i < count; i++) {
                    int idx = revCode | (i << len);
                    if (idx < TABLE_SIZE) {
                        table[idx] = entry;
                    }
                }
            } else {
                // Codes longer than TABLE_BITS -> two-level lookup
                int primaryIndex = revCode & (TABLE_SIZE - 1);
                int current = table[primaryIndex];
                if ((current & ENTRY_MASK_FLAG) == 0) {
                    int subBits = Math.min(len - TABLE_BITS, MAX_SUBTABLE_BITS);
                    int subOff = allocateSubTable(subBits);
                    int subEntry = subOff | (subBits << SUBTABLE_SHIFT_BITS) | ENTRY_MASK_FLAG;
                    table[primaryIndex] = subEntry;
                    current = subEntry;
                }
                int subOff = current & SUBTABLE_MASK_OFFSET;
                int subBits = getSubTableBits(current);
                int subCode = revCode >>> TABLE_BITS;
                int subCount = 1 << (subBits - (len - TABLE_BITS));
                for (int i = 0; i < subCount; i++) {
                    int idx = subOff + subCode + (i << (len - TABLE_BITS));
                    if (idx < table.length) {
                        table[idx] = entry;
                    }
                }
            }
        }
    }

    private int allocateSubTable(int subBits) {
        int newSize = table.length + (1 << subBits);
        table = Arrays.copyOf(table, newSize);
        return table.length - (1 << subBits);
    }

    private int getSubTableBits(int entry) {
        return (entry >>> SUBTABLE_SHIFT_BITS) & 0xF;
    }

    private int maxCodeLength(int[] lengths, int alphabetSize) {
        int max = 0;
        for (int i = 0; i < alphabetSize; i++) {
            if (lengths[i] > max) max = lengths[i];
        }
        return max;
    }

    private static int reverseBits(int value, int numBits) {
        return Integer.reverse(value) >>> (32 - numBits);
    }

    /** Read flat prefix code: symCount (16 bits) + code lengths as 4-bit values. */
    public static HuffmanDecoder readFlatPrefixCode(BitReader reader, int alphabetSize) {
        int symCount = reader.readBitsInt(16);
        int[] resultLengths = new int[alphabetSize];
        for (int i = 0; i < symCount; i++) {
            resultLengths[i] = reader.readBitsInt(4);
        }
        HuffmanDecoder result = new HuffmanDecoder();
        result.buildFromCodeLengths(resultLengths, alphabetSize);
        return result;
    }

    /** Read a complex prefix code from the bit reader. */
    public static HuffmanDecoder readComplexPrefixCode(BitReader reader, int alphabetSize, int hskip) {
        int[] clCodeLengths = new int[Constants.CODE_LENGTH_CODES];
        int startIdx = hskip;
        for (int i = startIdx; i < 18; i++) {
            clCodeLengths[Constants.CODE_LENGTH_ORDER[i]] = readCodeLengthSymbol(reader);
        }

        int nonZeroCount = 0;
        int lastNonZero = -1;
        for (int i = 0; i < 18; i++) {
            if (clCodeLengths[i] > 0) {
                nonZeroCount++;
                lastNonZero = i;
            }
        }

        HuffmanDecoder clDecoder = new HuffmanDecoder();
        if (nonZeroCount == 1) {
            clDecoder.buildSingleSymbol(lastNonZero);
        } else {
            clDecoder.buildFromCodeLengths(clCodeLengths, Constants.CODE_LENGTH_CODES);
        }

        // Read encoded symbol count
        int symCount = reader.readBitsInt(16);

        int[] resultLengths = new int[alphabetSize];
        int idx = 0;
        int prevValue = 8;
        int prevRepeat = 0;
        int space = 32768;
        boolean lastWas16 = false;
        boolean lastWas17 = false;

        while (idx < alphabetSize) {
            if (idx >= symCount) {
                resultLengths[idx++] = 0;
                continue;
            }
            int sym = clDecoder.decodeSymbol(reader);

            if (sym < 16) {
                resultLengths[idx++] = sym;
                if (sym > 0) {
                    prevValue = sym;
                    space -= 32768 >> sym;
                }
                prevRepeat = 0;
                lastWas16 = false;
                lastWas17 = false;
            } else if (sym == 16) {
                int extra = reader.readBitsInt(2);
                int repeat;
                if (!lastWas16) {
                    repeat = 3 + extra;
                } else {
                    repeat = 4 * (prevRepeat - 2) + (3 + extra);
                }
                if (prevValue == 0) prevValue = 8;
                prevRepeat = repeat;
                int maxRepeat = Math.min(repeat, alphabetSize - idx);
                for (int j = 0; j < maxRepeat; j++) {
                    resultLengths[idx++] = prevValue;
                    space -= 32768 >> prevValue;
                }
                lastWas16 = true;
                lastWas17 = false;
            } else if (sym == 17) {
                int extra = reader.readBitsInt(3);
                int repeat;
                if (!lastWas17) {
                    repeat = 3 + extra;
                } else {
                    repeat = 8 * (prevRepeat - 2) + (3 + extra);
                }
                prevValue = 0;
                prevRepeat = repeat;
                int maxRepeat = Math.min(repeat, alphabetSize - idx);
                for (int j = 0; j < maxRepeat; j++) {
                    resultLengths[idx++] = 0;
                }
                lastWas16 = false;
                lastWas17 = true;
            } else if (sym == 18) {
                int extra = reader.readBitsInt(7);
                int repeat = 11 + extra;
                prevValue = 0;
                prevRepeat = 0;
                int maxRepeat = Math.min(repeat, alphabetSize - idx);
                for (int j = 0; j < maxRepeat; j++) {
                    resultLengths[idx++] = 0;
                }
                lastWas16 = false;
                lastWas17 = false;
            }
        }

        if (space != 0) {
            throw new IllegalStateException(
                "Invalid Huffman code: unused space = " + space + " (expected 0)"
            );
        }

        HuffmanDecoder result = new HuffmanDecoder();
        result.buildFromCodeLengths(resultLengths, alphabetSize);
        return result;
    }

    /** Read a simple prefix code from the bit reader. */
    public static HuffmanDecoder readSimplePrefixCode(BitReader reader, int alphabetSize) {
        int nsym = reader.readBitsInt(2) + 1;
        int alphabetBits = com.brotjli.common.BitUtil.ceilLog2(alphabetSize);
        int[] symbols = new int[nsym];

        for (int i = 0; i < nsym; i++) {
            symbols[i] = reader.readBitsInt(alphabetBits);
            if (symbols[i] >= alphabetSize) {
                throw new IllegalArgumentException(
                    "Symbol " + symbols[i] + " out of range for alphabet size " + alphabetSize
                );
            }
        }

        for (int i = 0; i < nsym; i++) {
            for (int j = i + 1; j < nsym; j++) {
                if (symbols[i] == symbols[j]) {
                    throw new IllegalArgumentException("Duplicate symbol in simple prefix code: " + symbols[i]);
                }
            }
        }

        int treeSelect = 0;
        if (nsym == 4) {
            treeSelect = reader.readBitsInt(1);
        }

        HuffmanDecoder decoder = new HuffmanDecoder();
        decoder.buildSimplePrefixCode(symbols, nsym, treeSelect, alphabetBits);
        return decoder;
    }

    // Brotli fixed prefix code lookup table for CL code length symbols (RFC 7932).
    // Maps 32 5-bit peek values to (symbol << 3) | bitCount.
    // Codes: 0=00(2), 4=01(2), 3=10(2), 2=0110(4), 1=0111(4), 5=1111(4)
    private static final int[] CL_SYMBOL_TABLE = {
        2, 26, 34, 2, 2, 26, 20, 2, 2, 26, 34, 2, 2, 26, 12, 44,
        2, 26, 34, 2, 2, 26, 20, 2, 2, 26, 34, 2, 2, 26, 12, 44
    };

    private static int readCodeLengthSymbol(BitReader reader) {
        int peek = (int)reader.peekBits(5);
        int entry = CL_SYMBOL_TABLE[peek];
        reader.skipBits(entry & 0x7);
        return entry >>> 3;
    }
}
