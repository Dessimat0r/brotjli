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

        // Track the maximum code length that maps to each primary index.
        // This lets us allocate the correct subtable size beforehand.
        int[] maxLenForIndex = new int[TABLE_SIZE];
        for (int sym = 0; sym < alphabetSize; sym++) {
            int len = codeLengths[sym];
            if (len > TABLE_BITS) {
                int primaryIndex = reversedCodes[sym] & (TABLE_SIZE - 1);
                maxLenForIndex[primaryIndex] = Math.max(maxLenForIndex[primaryIndex], len);
            }
        }

        // Sort symbols by increasing code length so longer codes
        // are processed later and correctly overwrite shorter codes.
        java.util.ArrayList<Integer> ordered = new java.util.ArrayList<>();
        for (int i = 0; i < alphabetSize; i++) {
            if (codeLengths[i] > 0) ordered.add(i);
        }
        ordered.sort((a, b) -> Integer.compare(codeLengths[a], codeLengths[b]));

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
                    int subBits = Math.min(maxLenForIndex[primaryIndex] - TABLE_BITS, MAX_SUBTABLE_BITS);
                    int subOff = allocateSubTable(subBits);
                    int subEntry = subOff | (subBits << SUBTABLE_SHIFT_BITS) | ENTRY_MASK_FLAG;
                    // Initialize the new subtable with the existing primary entry
                    int subSize = 1 << subBits;
                    for (int i = 0; i < subSize; i++) {
                        table[subOff + i] = current;
                    }
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

    /** Read a complex prefix code from the bit reader. */
    public static HuffmanDecoder readComplexPrefixCode(BitReader reader, int alphabetSize, int hskip) {
        int[] clCodeLengths = new int[Constants.CODE_LENGTH_CODES];
        int startIdx = hskip;
        int clSpace = 32;
        for (int i = startIdx; i < 18; i++) {
            int codeLen = readCodeLengthSymbol(reader);
            clCodeLengths[Constants.CODE_LENGTH_ORDER[i]] = codeLen;
            if (codeLen > 0) {
                clSpace -= (32 >> codeLen);
                if (clSpace <= 0) {
                    break;
                }
            }
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

        int[] resultLengths = new int[alphabetSize];
        int idx = 0;
        int prevValue = 8;
        int repeat = 0;
        int repeatCodeLen = 0;
        int space = 32768;

        while (idx < alphabetSize && space > 0) {
            int sym = clDecoder.decodeSymbol(reader);

            if (sym < 16) {
                repeat = 0;
                resultLengths[idx++] = sym;
                if (sym > 0) {
                    prevValue = sym;
                    space -= 32768 >> sym;
                }
            } else { // sym == 16 or 17
                int extraBits = (sym == 16) ? 2 : 3;
                int repeatDelta = reader.readBitsInt(extraBits);
                int newLen = (sym == 16) ? prevValue : 0;
                if (repeatCodeLen != newLen) {
                    repeat = 0;
                    repeatCodeLen = newLen;
                }
                int oldRepeat = repeat;
                if (repeat > 0) {
                    repeat -= 2;
                    repeat <<= extraBits;
                }
                repeat += repeatDelta + 3;
                int numToWrite = repeat - oldRepeat;
                
                if (idx + numToWrite > alphabetSize) {
                    throw new IllegalStateException("Repeat code length extends past alphabet size");
                }
                
                for (int j = 0; j < numToWrite; j++) {
                    resultLengths[idx++] = repeatCodeLen;
                    if (repeatCodeLen > 0) {
                        space -= 32768 >> repeatCodeLen;
                    }
                }
            }
        }

        if (space != 0) {
            throw new IllegalStateException(
                "Invalid Huffman code: unused space = " + space + " (expected 0)"
            );
        }

        if (alphabetSize == 256) {
            System.out.println("Literal tree code lengths parsed by decoder:");
            for (int i = 0; i < 256; i++) {
                if (resultLengths[i] > 0) {
                    System.out.printf("%d:%d ", i, resultLengths[i]);
                }
            }
            System.out.println();
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

    // Brotli fixed prefix code lookup table for CL code length symbols (RFC 7932 Section 3.5).
    private static final int[] kCodeLengthPrefixLength = {
        2, 2, 2, 3, 2, 2, 2, 4, 2, 2, 2, 3, 2, 2, 2, 4
    };
    private static final int[] kCodeLengthPrefixValue = {
        0, 4, 3, 2, 0, 4, 3, 1, 0, 4, 3, 2, 0, 4, 3, 5
    };

    private static int readCodeLengthSymbol(BitReader reader) {
        int peek = reader.peekBitsInt(4);
        int len = kCodeLengthPrefixLength[peek];
        int val = kCodeLengthPrefixValue[peek];
        reader.skipBits(len);
        return val;
    }
}
