package com.brotjli.stream;

import com.brotjli.BrotjliEncoder;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

/**
 * Compressing OutputStream that writes Brotli-compressed data.
 *
 * Buffers all data, compresses on close.
 * Not thread-safe.
 */
public class BrotliOutputStream extends OutputStream {
    private final BrotjliEncoder encoder;
    private final ByteArrayOutputStream buffer;
    private final int quality;
    private boolean closed;

    public BrotliOutputStream(int quality) {
        this.encoder = new BrotjliEncoder();
        this.buffer = new ByteArrayOutputStream();
        this.quality = quality;
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
     */
    public byte[] getCompressedData() {
        return encoder.encode(buffer.toByteArray(), quality);
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
        }
    }
}
