package com.brotjli.stream;

import com.brotjli.BrotjliDecoder;
import java.io.InputStream;
import java.io.IOException;

/**
 * Decompressing InputStream that reads Brotli-compressed data
 * and decompresses it on the fly.
 *
 * Not thread-safe.
 */
public class BrotliInputStream extends InputStream {
    private final BrotjliDecoder decoder;
    private byte[] decompressedBuffer;
    private int position;
    private int limit;
    private boolean closed;

    public BrotliInputStream(byte[] compressedData) {
        this.decoder = new BrotjliDecoder();
        this.decompressedBuffer = decoder.decode(compressedData);
        this.position = 0;
        this.limit = decompressedBuffer.length;
    }

    @Override
    public int read() throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (position >= limit) return -1;
        return decompressedBuffer[position++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (position >= limit) return -1;
        int toCopy = Math.min(len, limit - position);
        System.arraycopy(decompressedBuffer, position, b, off, toCopy);
        position += toCopy;
        return toCopy;
    }

    @Override
    public int available() throws IOException {
        if (closed) return 0;
        return limit - position;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
