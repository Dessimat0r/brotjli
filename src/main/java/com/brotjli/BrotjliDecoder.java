package com.brotjli;

import com.brotjli.decoder.DecoderImpl;
import java.nio.ByteBuffer;

/**
 * Brotli decoder instance. Not thread-safe; use DecoderPool or
 * {@link #getInstance()} for thread-local reuse.
 *
 * Usage:
 * <pre>{@code
 * BrotjliDecoder decoder = new BrotjliDecoder();
 * decoder.decode(compressedData, output);
 * }</pre>
 */
public final class BrotjliDecoder implements AutoCloseable {
    private static final ThreadLocal<BrotjliDecoder> THREAD_LOCAL =
        ThreadLocal.withInitial(BrotjliDecoder::new);

    private final DecoderImpl impl;

    public BrotjliDecoder() {
        this.impl = new DecoderImpl();
    }

    /**
     * Get the thread-local decoder instance (one per thread).
     */
    public static BrotjliDecoder getInstance() {
        return THREAD_LOCAL.get();
    }

    /**
     * Decompress Brotli-compressed data.
     *
     * @param compressed the compressed data
     * @return decompressed data
     */
    public byte[] decode(byte[] compressed) {
        impl.reset();
        impl.feed(compressed);
        return impl.getOutput();
    }

    /**
     * Decompress data from a ByteBuffer.
     */
    public byte[] decode(ByteBuffer compressed) {
        impl.reset();
        impl.feed(compressed);
        return impl.getOutput();
    }

    /**
     * Reset the decoder for reuse.
     */
    public void reset() {
        impl.reset();
    }

    /**
     * Get the underlying DecoderImpl (for advanced use).
     */
    public DecoderImpl getImpl() {
        return impl;
    }

    @Override
    public void close() {
        // No-op for now; may pool resources later
        reset();
    }
}
