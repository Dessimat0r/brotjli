package com.brotjli.stream;

import com.brotjli.BrotjliDecoder;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Decompressing InputStream that reads Brotli-compressed data
 * and decompresses it on the fly.
 *
 * Accepts compressed data from a byte array or an InputStream.
 * Not thread-safe.
 */
public class BrotliInputStream extends InputStream {
    private final BrotjliDecoder decoder;
    private byte[] decompressedBuffer;
    private int position;
    private int limit;
    private boolean closed;

    /**
     * Create from a compressed byte array.
     */
    public BrotliInputStream(byte[] compressedData) {
        this.decoder = new BrotjliDecoder();
        this.decompressedBuffer = decoder.decode(compressedData);
        this.position = 0;
        this.limit = decompressedBuffer.length;
    }

    /**
     * Create from an InputStream containing compressed data.
     * Reads the entire stream, decompresses it, and provides the result.
     */
    public BrotliInputStream(InputStream compressedStream) throws IOException {
        this.decoder = new BrotjliDecoder();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = compressedStream.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        this.decompressedBuffer = decoder.decode(baos.toByteArray());
        this.position = 0;
        this.limit = decompressedBuffer.length;
    }

    /**
     * Create from compressed data using a specific decoder instance.
     */
    public BrotliInputStream(byte[] compressedData, BrotjliDecoder decoder) {
        this.decoder = decoder;
        this.decompressedBuffer = decoder.decode(compressedData);
        this.position = 0;
        this.limit = decompressedBuffer.length;
    }

    /**
     * Create from an InputStream using a specific decoder instance.
     */
    public BrotliInputStream(InputStream compressedStream, BrotjliDecoder decoder) throws IOException {
        this.decoder = decoder;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = compressedStream.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        this.decompressedBuffer = decoder.decode(baos.toByteArray());
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
    public long skip(long n) throws IOException {
        if (closed) throw new IOException("Stream closed");
        long toSkip = Math.min(n, (long)(limit - position));
        position += (int) toSkip;
        return toSkip;
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

    /**
     * Get the underlying decompressed data array. The returned array
     * is the internal buffer; do not modify it.
     */
    public byte[] getData() {
        return decompressedBuffer;
    }

    /**
     * Get the number of decompressed bytes available.
     */
    public int length() {
        return limit;
    }
}
