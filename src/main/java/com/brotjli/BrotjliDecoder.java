package com.brotjli;

import com.brotjli.decoder.DecoderImpl;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Brotli decoder instance.
 *
 * <p>Decodes Brotli-compressed data (RFC 7932) back to its original
 * uncompressed form. Supports all quality levels (0&ndash;11).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Byte array
 * BrotjliDecoder dec = new BrotjliDecoder();
 * byte[] decompressed = dec.decode(compressedData);
 *
 * // From InputStream
 * byte[] decompressed = dec.decode(compressedStream);
 *
 * // Stream-to-stream
 * dec.decode(compressedStream, outputStream);
 * }</pre>
 *
 * <p><b>Not thread-safe.</b> For concurrent use, prefer
 * {@link com.brotjli.pool.DecoderPool} or the thread-local
 * {@link #getInstance()}.
 *
 * <p>Implements {@link AutoCloseable} — calling {@link #close()}
 * resets the decoder for reuse.
 *
 * @see BrotjliEncoder
 * @see com.brotjli.pool.DecoderPool
 */
public final class BrotjliDecoder implements AutoCloseable {
    private static final ThreadLocal<BrotjliDecoder> THREAD_LOCAL =
        ThreadLocal.withInitial(BrotjliDecoder::new);

    private final DecoderImpl impl;

    /** Create a new decoder instance. */
    public BrotjliDecoder() {
        this.impl = new DecoderImpl();
    }

    /**
     * Get the thread-local decoder instance (one per thread).
     *
     * @return the thread-local decoder
     */
    public static BrotjliDecoder getInstance() {
        return THREAD_LOCAL.get();
    }

    /**
     * Decompress Brotli-compressed data.
     *
     * @param compressed Brotli-compressed bytes
     * @return decompressed data
     */
    public byte[] decode(byte[] compressed) {
        impl.reset();
        impl.feed(compressed);
        return impl.getOutput();
    }

    /**
     * Decompress data from a {@link ByteBuffer}.
     *
     * @param compressed Brotli-compressed data
     * @return decompressed data
     */
    public byte[] decode(ByteBuffer compressed) {
        impl.reset();
        impl.feed(compressed);
        return impl.getOutput();
    }

    /**
     * Decompress all data read from an {@link InputStream} containing
     * Brotli-compressed data.
     *
     * @param compressed compressed data stream
     * @return decompressed data
     * @throws IOException if reading the stream fails
     */
    public byte[] decode(InputStream compressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = compressed.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        return decode(baos.toByteArray());
    }

    /**
     * Decompress from an {@link InputStream} and write uncompressed data
     * directly to an {@link OutputStream}.
     *
     * @param compressed compressed data stream
     * @param output     decompressed data destination
     * @throws IOException on I/O error
     */
    public void decode(InputStream compressed, OutputStream output) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = compressed.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        byte[] decompressed = decode(baos.toByteArray());
        output.write(decompressed);
    }

    /**
     * Reset the decoder for reuse. Clears all internal state.
     */
    public void reset() {
        impl.reset();
    }

    /**
     * Get the underlying {@link DecoderImpl} for advanced use.
     *
     * @return the internal decoder implementation
     */
    public DecoderImpl getImpl() {
        return impl;
    }

    /** Reset and release resources. */
    @Override
    public void close() {
        reset();
    }
}
