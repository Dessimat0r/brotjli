package com.brotjli.common;

import java.util.function.IntSupplier;

/**
 * Static methods for decoding the deterministic prefix-code tables used by the
 * Brotli format (RFC 7932) to encode window size, block-type counts,
 * variable-length unsigned integers, and RLE maximums.
 *
 * <p>Each method reads individual bits from a supplied {@link IntSupplier}
 * that returns {@code 0} or {@code 1} for each call.
 */
public final class PrefixCodeTables {
    private PrefixCodeTables() {}

    /**
     * Decodes the window size exponent (WBITS) from the bit-stream.
     *
     * <p>Implements RFC 7932 Section 2.1 window size encoding.
     * Bits are consumed one at a time from the supplier (LSB-first order).
     *
     * @param readBit supplier of individual bits (0 or 1)
     * @return decoded window size exponent in range [10, 24]
     */
    public static int decodeWindowBits(IntSupplier readBit) {
        int bits = 0;
        int len = 0;
        while (true) {
            int b = readBit.getAsInt();
            bits = (bits << 1) | b;
            len++;
            if (b == 0) {
                if (len == 1) return 16;
            } else {
                if (len == 6 && (bits & 0x3F) == 0x21) return 17;
                if (len == 7 && (bits & 0x7F) == 0x61) return 18;
                if (len == 4 && (bits & 0x0F) == 0x03) return 18;
                if (len == 4 && (bits & 0x0F) == 0x05) return 19;
                if (len == 4 && (bits & 0x0F) == 0x07) return 20;
                if (len == 4 && (bits & 0x0F) == 0x09) return 21;
                if (len == 4 && (bits & 0x0F) == 0x0B) return 22;
                if (len == 4 && (bits & 0x0F) == 0x0D) return 23;
                if (len == 4 && (bits & 0x0F) == 0x0F) return 24;
                if (len == 3 && (bits & 0x07) == 0x03) return 10;
                if (len == 3 && (bits & 0x07) == 0x05) return 11;
                if (len == 3 && (bits & 0x07) == 0x06) return 12;
                bits &= 0x7F;
            }
        }
    }

    /**
     * Decodes the number of block types (NBLTYPES) for a block-switch command.
     *
     * <p>Returns 1 if the first bit is 0, 2 if the pattern is "10", and larger
     * values for longer sequences of 1-bits followed by extra bits.
     *
     * @param readBit supplier of individual bits (0 or 1)
     * @return decoded block-type count in range [1, 256]
     */
    public static int decodeNbltypes(IntSupplier readBit) {
        if (readBit.getAsInt() == 0) return 1;
        if (readBit.getAsInt() == 0) return 2;
        int bits = 1;
        int len = 1;
        while (true) {
            int b = readBit.getAsInt();
            bits = (bits << 1) | b;
            len++;
            if (b == 1) {
                if (len == 3) {
                    int extra = readBit.getAsInt();
                    if (extra == 0) return readBit.getAsInt() == 0 ? 3 : 4;
                }
                if (len == 4) {
                    int extra = 0;
                    extra = (extra << 1) | readBit.getAsInt();
                    extra = (extra << 1) | readBit.getAsInt();
                    return 5 + extra;
                }
            }
            if (len > 10) return 256;
        }
    }

    /**
     * Decodes a variable-length unsigned integer.
     *
     * <p>Each 1-bit increases the result by 1; the first 0-bit terminates.
     * Returns 1 for the pattern {@code 0}, 2 for {@code 10}, 3 for {@code 110}, etc.
     *
     * @param readBit supplier of individual bits (0 or 1)
     * @return decoded integer
     */
    public static int decodeVarLenUnsigned(IntSupplier readBit) {
        int result = 1;
        while (readBit.getAsInt() == 1) {
            result += 1;
        }
        return result;
    }

    /**
     * Decodes the RLE maximum for context maps.
     *
     * <p>Bits are read until a 1 is encountered. The accumulated value before
     * the trailing 1 is returned (0 means RLEMAX of 0, 1 means RLEMAX of 1, etc.).
     *
     * @param readBit supplier of individual bits (0 or 1)
     * @return decoded RLE maximum in range [0, 15]
     */
    public static int decodeRlemax(IntSupplier readBit) {
        int bits = 0;
        while (true) {
            int b = readBit.getAsInt();
            bits = (bits << 1) | b;
            if (b == 1) {
                int value = bits >> 1;
                return value;
            }
        }
    }
}
