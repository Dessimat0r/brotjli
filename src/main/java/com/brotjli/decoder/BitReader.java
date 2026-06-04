package com.brotjli.decoder;

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

/**
 * LSB-first bit reader for Brotli stream decoding.
 *
 * Reads bits from a byte source with a 64-bit lookahead for performance.
 * All integer values are read LSB-first. Huffman codes are read MSB-first
 * via separate methods.
 */
public final class BitReader {
    private byte[] buffer;
    private int bufferOffset;
    private int bufferLimit;
    private long accumulator;
    private int bitsInAccumulator;

    private static final int ACCUMULATOR_CAPACITY = 64;
    private static final int REFILL_THRESHOLD = 32;

    public BitReader() {
        this.buffer = new byte[0];
    }

    public BitReader(byte[] data) {
        this();
        reset(data);
    }

    /** Reset reader with new data. */
    public void reset(byte[] data) {
        this.buffer = data;
        this.bufferOffset = 0;
        this.bufferLimit = data.length;
        this.accumulator = 0;
        this.bitsInAccumulator = 0;
    }

    /** Reset reader with a ByteBuffer. */
    public void reset(ByteBuffer bb) {
        byte[] data = new byte[bb.remaining()];
        bb.get(data);
        reset(data);
    }

    /**
     * Read a single bit (0 or 1) LSB-first.
     */
    public int readBit() {
        if (bitsInAccumulator < 1) {
            refillAccumulator();
        }
        int bit = (int)(accumulator & 1);
        accumulator >>>= 1;
        bitsInAccumulator--;
        return bit;
    }

    /**
     * Read up to 64 bits as an unsigned value (LSB-first).
     */
    public long readBits(int numBits) {
        if (numBits <= 0) return 0;
        if (numBits > ACCUMULATOR_CAPACITY) {
            throw new IllegalArgumentException("Cannot read more than 64 bits at once");
        }
        if (bitsInAccumulator < numBits) {
            refillAccumulator();
        }
        long result = accumulator & ((1L << numBits) - 1);
        accumulator >>>= numBits;
        bitsInAccumulator -= numBits;
        return result;
    }

    /**
     * Read numBits as unsigned int (up to 31 bits).
     */
    public int readBitsInt(int numBits) {
        return (int)readBits(numBits);
    }

    /**
     * Peek at the next numBits without consuming them.
     */
    public long peekBits(int numBits) {
        if (bitsInAccumulator < numBits) {
            refillAccumulator();
        }
        return accumulator & ((1L << numBits) - 1);
    }

    /**
     * Peek at up to 31 bits as an int.
     */
    public int peekBitsInt(int numBits) {
        if (bitsInAccumulator < numBits) {
            refillAccumulator();
        }
        return (int)(accumulator & ((1L << numBits) - 1));
    }

    /**
     * Peek at bits but reversed (for Huffman MSB-first decoding).
     */
    public int peekBitsReversed(int numBits) {
        if (bitsInAccumulator < numBits) {
            refillAccumulator();
        }
        long bits = accumulator & ((1L << numBits) - 1);
        return Integer.reverse((int)bits) >>> (32 - numBits);
    }

    /** Consume bits without reading them. */
    public void skipBits(int numBits) {
        if (bitsInAccumulator < numBits) {
            refillAccumulator();
        }
        accumulator >>>= numBits;
        bitsInAccumulator -= numBits;
    }

    /** Align to next byte boundary. */
    public void alignToByte() {
        int overshoot = bitsInAccumulator & 7;
        if (overshoot > 0) {
            skipBits(overshoot);
        }
    }

    /** Check if we've consumed all input. */
    public boolean isFinished() {
        return bitsInAccumulator == 0 && bufferOffset >= bufferLimit;
    }

    /** Number of bytes consumed so far. */
    public int bytesConsumed() {
        return bufferOffset - (bitsInAccumulator / 8);
    }

    /** Total bytes available. */
    public int totalBytes() {
        return bufferLimit;
    }

    /** Remaining unread bytes in the source buffer. */
    public int remainingBytes() {
        return Math.max(0, bufferLimit - bufferOffset);
    }

    /** Get an IntSupplier that reads bits one at a time. */
    public IntSupplier asBitSupplier() {
        return this::readBit;
    }

    private void refillAccumulator() {
        int bytesToRead = Math.min(
            (ACCUMULATOR_CAPACITY - bitsInAccumulator + 7) / 8,
            bufferLimit - bufferOffset
        );
        for (int i = 0; i < bytesToRead; i++) {
            long b = buffer[bufferOffset++] & 0xFFL;
            accumulator |= b << bitsInAccumulator;
            bitsInAccumulator += 8;
        }
    }
}
