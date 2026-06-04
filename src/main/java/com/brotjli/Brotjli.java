package com.brotjli;

import java.nio.ByteBuffer;

/**
 * Convenience API for Brotli compression and decompression.
 *
 * Thread-safe (creates new instances per call).
 */
public final class Brotjli {
    private Brotjli() {}

    /**
     * Compress data with Brotli.
     *
     * @param input   uncompressed data
     * @param quality compression quality (0-3)
     * @return compressed data
     */
    public static byte[] compress(byte[] input, int quality) {
        return BrotjliEncoder.getInstance().encode(input, quality);
    }

    /**
     * Compress with default quality (2).
     */
    public static byte[] compress(byte[] input) {
        return compress(input, 2);
    }

    /**
     * Decompress Brotli-compressed data.
     *
     * @param compressed compressed data
     * @return decompressed data
     */
    public static byte[] decompress(byte[] compressed) {
        return BrotjliDecoder.getInstance().decode(compressed);
    }

    /**
     * Decompress from ByteBuffer.
     */
    public static byte[] decompress(ByteBuffer compressed) {
        return BrotjliDecoder.getInstance().decode(compressed);
    }
}
