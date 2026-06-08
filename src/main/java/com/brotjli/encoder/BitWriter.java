package com.brotjli.encoder;

import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * LSB-first bit writer for Brotli stream encoding.
 *
 * Accumulates bits in a 64-bit buffer and flushes to a byte array
 * or OutputStream as needed. Supports writing integers, variable-length
 * codes, and aligning to byte boundaries.
 */
public final class BitWriter {
    private static final int INITIAL_CAPACITY = 4096;

    private byte[] buffer;
    private int byteOffset;
    private long accumulator;
    private int bitsInAccumulator;

    private OutputStream outputStream;

    public BitWriter() {
        this.buffer = new byte[INITIAL_CAPACITY];
        this.byteOffset = 0;
        this.accumulator = 0;
        this.bitsInAccumulator = 0;
    }

    public BitWriter(int initialCapacity) {
        this.buffer = new byte[Math.max(initialCapacity, 64)];
        this.byteOffset = 0;
        this.accumulator = 0;
        this.bitsInAccumulator = 0;
    }

    public BitWriter(OutputStream out) {
        this();
        this.outputStream = out;
    }

    public BitWriter(OutputStream out, int initialCapacity) {
        this(initialCapacity);
        this.outputStream = out;
    }

    /**
     * Write a single bit (0 or 1).
     */
    public void writeBit(int bit) {
        accumulator |= ((long)(bit & 1)) << bitsInAccumulator;
        bitsInAccumulator++;
        if (bitsInAccumulator >= 64) {
            flushAccumulator();
        }
    }

    /**
     * Write up to 64 bits (LSB-first).
     *
     * @param value   the value to write (only low numBits are used)
     * @param numBits number of bits to write (0..64)
     */
    public void writeBits(long value, int numBits) {
        if (numBits <= 0) return;
        long mask = (numBits == 64) ? -1L : ((1L << numBits) - 1);
        value &= mask;

        accumulator |= value << bitsInAccumulator;
        bitsInAccumulator += numBits;

        while (bitsInAccumulator >= 8) {
            flushOneByte();
        }
    }

    /**
     * Write bits in reverse order (MSB-first, for Huffman codes).
     * The code value is provided in MSB-first order and is reversed
     * before writing LSB-first.
     */
    public void writeBitsReversed(int value, int numBits) {
        int reversed = Integer.reverse(value) >>> (32 - numBits);
        writeBits(reversed, numBits);
    }

    /**
     * Write a variable-length unsigned integer.
     * For Brotli's prefix-coded integers.
     */
    public void writeVarLenUnsigned(int value) {
        int bits = value;
        while (bits > 1) {
            writeBit(1);
            bits--;
        }
        writeBit(0);
    }

    /**
     * Align to next byte boundary (pad with zeros).
     */
    public void alignToByte() {
        int overshoot = bitsInAccumulator & 7;
        if (overshoot > 0) {
            writeBits(0, 8 - overshoot);
        }
    }

    /**
     * Flush remaining bits to the buffer and pad to byte boundary.
     */
    public void flush() {
        if (bitsInAccumulator > 0) {
            flushAccumulator();
        }
    }

    /**
     * Get the written data as a byte array.
     * Flushes any pending bits. Does not include data already
     * written to an OutputStream.
     */
    public byte[] toByteArray() {
        flush();
        return Arrays.copyOf(buffer, byteOffset);
    }

    /**
     * Write all buffered data to an OutputStream and reset.
     */
    public void writeTo(OutputStream out) throws IOException {
        flush();
        if (byteOffset > 0) {
            out.write(buffer, 0, byteOffset);
            byteOffset = 0;
        }
    }

    /**
     * Get the number of bytes written.
     */
    public int bytesWritten() {
        return byteOffset + (bitsInAccumulator + 7) / 8;
    }

    /**
     * Get the number of bits written.
     */
    public int bitsWritten() {
        return byteOffset * 8 + bitsInAccumulator;
    }

    /**
     * Reset the writer for reuse.
     */
    public void reset() {
        byteOffset = 0;
        accumulator = 0;
        bitsInAccumulator = 0;
    }

    /**
     * Set the OutputStream for streaming writes.
     * Subsequent full-byte flushes will be written to this stream.
     */
    public void setOutputStream(OutputStream out) {
        flushAccumulatorToBuffer();
        this.outputStream = out;
    }

    private void flushOneByte() {
        if (outputStream != null) {
            try {
                outputStream.write((int) (accumulator & 0xFF));
                accumulator >>>= 8;
                bitsInAccumulator -= 8;
            } catch (IOException e) {
                throw new RuntimeException("Error writing to output stream", e);
            }
        } else {
            ensureCapacity(byteOffset + 1);
            buffer[byteOffset++] = (byte) (accumulator & 0xFF);
            accumulator >>>= 8;
            bitsInAccumulator -= 8;
        }
    }

    private void flushAccumulator() {
        flushAccumulatorToBuffer();
    }

    private void flushAccumulatorToBuffer() {
        int bytesToFlush = (bitsInAccumulator + 7) / 8;
        if (outputStream != null) {
            try {
                for (int i = 0; i < bytesToFlush; i++) {
                    outputStream.write((int) (accumulator & 0xFF));
                    accumulator >>>= 8;
                }
                bitsInAccumulator = 0;
                accumulator = 0;
            } catch (IOException e) {
                throw new RuntimeException("Error writing to output stream", e);
            }
        } else {
            ensureCapacity(byteOffset + bytesToFlush);
            for (int i = 0; i < bytesToFlush; i++) {
                buffer[byteOffset++] = (byte) (accumulator & 0xFF);
                accumulator >>>= 8;
            }
            bitsInAccumulator = 0;
            accumulator = 0;
        }
    }

    private void ensureCapacity(int needed) {
        if (needed > buffer.length) {
            int newSize = Math.max(needed, buffer.length * 2);
            buffer = Arrays.copyOf(buffer, newSize);
        }
    }
}
