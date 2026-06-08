package com.brotjli.stream;

import com.brotjli.BrotjliEncoder;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

/**
 * Compressing OutputStream that produces Brotli-compressed data.
 *
 * Buffers all uncompressed data, compresses on close (or flush for
 * quality 0 store mode), and writes to the configured OutputStream.
 *
 * Not thread-safe.
 */
public class BrotliOutputStream extends OutputStream {
    private final BrotjliEncoder encoder;
    private final ByteArrayOutputStream buffer;
    private final int quality;
    private final OutputStream outputStream;
    private boolean closed;

    /**
     * Create with quality only. Get compressed data via {@link #getCompressedData()}.
     */
    public BrotliOutputStream(int quality) {
        this(quality, null);
    }

    /**
     * Create with quality and an OutputStream to write compressed data to on close.
     */
    public BrotliOutputStream(int quality, OutputStream out) {
        this.encoder = new BrotjliEncoder();
        this.buffer = new ByteArrayOutputStream();
        this.quality = quality;
        this.outputStream = out;
    }

    /**
     * Create with an OutputStream and default quality (2).
     */
    public BrotliOutputStream(OutputStream out) {
        this(2, out);
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) throw new IOException("Stream closed");
        buffer.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) throw new IOException("Stream closed");
        buffer.write(b, off, len);
    }

    /**
     * Get the compressed data (call after close).
     * Returns null if an OutputStream was provided and data was already written.
     */
    public byte[] getCompressedData() {
        return encoder.encode(buffer.toByteArray(), quality);
    }

    /**
     * Get the number of uncompressed bytes written so far.
     */
    public int size() {
        return buffer.size();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            byte[] compressed = encoder.encode(buffer.toByteArray(), quality);
            if (outputStream != null) {
                outputStream.write(compressed);
                outputStream.flush();
            }
        }
    }
}
