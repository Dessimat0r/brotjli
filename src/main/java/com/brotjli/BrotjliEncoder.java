package com.brotjli;

import com.brotjli.encoder.EncoderImpl;
import java.nio.ByteBuffer;

/**
 * Brotli encoder instance. Not thread-safe; use EncoderPool or
 * {@link #getInstance()} for thread-local reuse.
 *
 * Usage:
 * <pre>{@code
 * BrotjliEncoder encoder = new BrotjliEncoder();
 * byte[] compressed = encoder.encode(data, 2);
 * }</pre>
 */
public final class BrotjliEncoder implements AutoCloseable {
    private static final ThreadLocal<BrotjliEncoder> THREAD_LOCAL =
        ThreadLocal.withInitial(BrotjliEncoder::new);

    private final EncoderImpl impl;

    public BrotjliEncoder() {
        this.impl = new EncoderImpl();
    }

    /**
     * Get the thread-local encoder instance (one per thread).
     */
    public static BrotjliEncoder getInstance() {
        return THREAD_LOCAL.get();
    }

    /**
     * Compress data using Brotli.
     *
     * @param input   uncompressed data
     * @param quality compression quality (0-3)
     * @return compressed data
     */
    public byte[] encode(byte[] input, int quality) {
        impl.init(quality);
        return impl.compress(input);
    }

    /**
     * Compress data with default quality (2).
     */
    public byte[] encode(byte[] input) {
        return encode(input, 2);
    }

    /**
     * Compress data from a ByteBuffer.
     */
    public byte[] encode(ByteBuffer input, int quality) {
        byte[] bytes = new byte[input.remaining()];
        input.get(bytes);
        return encode(bytes, quality);
    }

    /**
     * Reset the encoder for reuse.
     */
    public void reset() {
        impl.reset();
    }

    @Override
    public void close() {
        reset();
    }
}
