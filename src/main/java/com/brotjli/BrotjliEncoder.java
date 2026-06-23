package com.brotjli;

import com.brotjli.encoder.EncoderImpl;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Brotli encoder instance.
 *
 * <p>Encodes uncompressed data into the Brotli compressed format
 * (RFC 7932). Supports quality levels 0&ndash;11.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Byte array
 * BrotjliEncoder enc = new BrotjliEncoder();
 * byte[] compressed = enc.encode(data, 5);
 *
 * // From InputStream
 * byte[] compressed = enc.encode(inputStream, 5);
 *
 * // Stream-to-stream
 * enc.encode(inputStream, outputStream, 5);
 * }</pre>
 *
 * <p><b>Not thread-safe.</b> For concurrent use, prefer
 * {@link com.brotjli.pool.EncoderPool} or the thread-local
 * {@link #getInstance()}.
 *
 * <p>Implements {@link AutoCloseable} — calling {@link #close()}
 * resets the encoder for reuse.
 *
 * @see BrotjliDecoder
 * @see com.brotjli.pool.EncoderPool
 */
public final class BrotjliEncoder implements AutoCloseable {
    private static final ThreadLocal<BrotjliEncoder> THREAD_LOCAL =
        ThreadLocal.withInitial(BrotjliEncoder::new);

    private final EncoderImpl impl;

    /** Create a new encoder instance. */
    public BrotjliEncoder() {
        this.impl = new EncoderImpl();
    }

    /**
     * Get the thread-local encoder instance (one per thread).
     * Suitable for single-threaded use without explicit pooling.
     *
     * @return the thread-local encoder
     */
    public static BrotjliEncoder getInstance() {
        return THREAD_LOCAL.get();
    }

    /**
     * Compress data using Brotli at the given quality.
     *
     * @param input   uncompressed data
     * @param quality compression quality (0&ndash;11)
     * @return Brotli-compressed data
     */
    public byte[] encode(byte[] input, int quality) {
        impl.init(quality);
        return impl.compress(input);
    }

    /**
     * Compress with default quality 2.
     *
     * @param input uncompressed data
     * @return Brotli-compressed data
     */
    public byte[] encode(byte[] input) {
        return encode(input, 2);
    }

    /**
     * Compress data from a {@link ByteBuffer}.
     * Uses zero-copy path when the buffer has a backing array.
     *
     * @param input   uncompressed data
     * @param quality compression quality (0&ndash;11)
     * @return Brotli-compressed data
     */
    public byte[] encode(ByteBuffer input, int quality) {
        byte[] bytes;
        if (input.hasArray()) {
            bytes = input.array();
            int offset = input.arrayOffset() + input.position();
            int length = input.remaining();
            impl.init(quality);
            return impl.compress(bytes, offset, length);
        } else {
            bytes = new byte[input.remaining()];
            input.get(bytes);
            return encode(bytes, quality);
        }
    }

    /**
     * Compress all data read from an {@link InputStream}.
     *
     * @param input   uncompressed data stream
     * @param quality compression quality (0&ndash;11)
     * @return Brotli-compressed data
     * @throws IOException if reading the stream fails
     */
    public byte[] encode(InputStream input, int quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = input.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        return encode(baos.toByteArray(), quality);
    }

    /**
     * Compress from an {@link InputStream} with default quality 2.
     *
     * @param input uncompressed data stream
     * @return Brotli-compressed data
     * @throws IOException if reading fails
     */
    public byte[] encode(InputStream input) throws IOException {
        return encode(input, 2);
    }

    /**
     * Compress data from an {@link InputStream} and write the compressed
     * result directly to an {@link OutputStream}.
     *
     * @param input   uncompressed data source
     * @param output  compressed data destination
     * @param quality compression quality (0&ndash;11)
     * @throws IOException on I/O error
     */
    public void encode(InputStream input, OutputStream output, int quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = input.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        byte[] compressed = encode(baos.toByteArray(), quality);
        output.write(compressed);
    }

    /**
     * Reset the encoder for reuse. Clears all internal state.
     */
    public void reset() {
        impl.reset();
    }

    /**
     * Get the underlying {@link EncoderImpl} for advanced use.
     *
     * @return the internal encoder implementation
     */
    public EncoderImpl getImpl() {
        return impl;
    }

    /** Reset and release resources. */
    @Override
    public void close() {
        reset();
    }
}
