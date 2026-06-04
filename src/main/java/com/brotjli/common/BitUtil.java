package com.brotjli.common;

/**
 * Utility methods for bit-level manipulation of byte arrays.
 *
 * <p>Provides bit-level read and write operations on byte array backings,
 * along with common bit-length, logarithm, and bit-reversal helpers.
 * All methods work on contiguous blocks of bits described by a bit offset
 * and a bit count.
 */
public final class BitUtil {
    private BitUtil() {}

    /**
     * Returns the minimum number of bits required to represent the given
     * value (its position of the highest set bit + 1, or 0 for zero).
     */
    public static int bitLength(int value) {
        return 32 - Integer.numberOfLeadingZeros(value);
    }

    /**
     * Returns the ceiling of the base-2 logarithm of the given value.
     * Returns 0 for values ≤ 1.
     */
    public static int ceilLog2(int value) {
        if (value <= 1) return 0;
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    /**
     * Reverses the lowest {@code numBits} bits of the given value.
     * The reversed bits are placed in the lowest {@code numBits} positions
     * of the returned integer.
     */
    public static int reverseBits(int value, int numBits) {
        int reversed = Integer.reverse(value) >>> (32 - numBits);
        return reversed;
    }

    /**
     * Reads up to 31 bits starting at {@code bitOffset} from the given byte array.
     * The bits are read from little-endian bytes, least-significant bit first.
     */
    public static int readBits(byte[] data, int bitOffset, int numBits) {
        int byteOffset = bitOffset >>> 3;
        int bitShift = bitOffset & 7;
        long word = 0;
        int bytesNeeded = (bitShift + numBits + 7) >>> 3;
        for (int i = 0; i < bytesNeeded && (byteOffset + i) < data.length; i++) {
            word |= ((long) (data[byteOffset + i] & 0xFF)) << (i * 8);
        }
        return (int) ((word >>> bitShift) & ((1L << numBits) - 1));
    }

    /**
     * Reads up to 63 bits starting at {@code bitOffset} from the given byte array.
     * The bits are read from little-endian bytes, least-significant bit first.
     * Returns the result as a signed {@code long}; the caller should mask or
     * interpret the result as unsigned if necessary.
     */
    public static long readBits64(byte[] data, int bitOffset, int numBits) {
        int byteOffset = bitOffset >>> 3;
        int bitShift = bitOffset & 7;
        long word = 0;
        int bytesNeeded = (bitShift + numBits + 7) >>> 3;
        for (int i = 0; i < bytesNeeded && (byteOffset + i) < data.length; i++) {
            word |= ((long) (data[byteOffset + i] & 0xFF)) << (i * 8);
        }
        return (word >>> bitShift) & ((1L << numBits) - 1);
    }

    /**
     * Writes {@code numBits} bits of {@code value} at {@code bitOffset} into
     * the given byte array. The write is performed on little-endian bytes,
     * least-significant bit first. Only the lowest {@code numBits} of
     * {@code value} are written.
     */
    public static void writeBits(byte[] data, int bitOffset, long value, int numBits) {
        int byteOffset = bitOffset >>> 3;
        int bitShift = bitOffset & 7;
        long mask = (1L << numBits) - 1;
        value &= mask;
        long existing = 0;
        int bytesNeeded = (bitShift + numBits + 7) >>> 3;
        for (int i = 0; i < bytesNeeded; i++) {
            if (byteOffset + i < data.length) {
                existing |= ((long) (data[byteOffset + i] & 0xFF)) << (i * 8);
            }
        }
        long combined = existing | (value << bitShift);
        for (int i = 0; i < bytesNeeded; i++) {
            if (byteOffset + i < data.length) {
                data[byteOffset + i] = (byte) ((combined >>> (i * 8)) & 0xFF);
            }
        }
    }
}
