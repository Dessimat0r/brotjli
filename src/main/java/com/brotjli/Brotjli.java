package com.brotjli;

import com.brotjli.stream.BrotliInputStream;
import com.brotjli.stream.BrotliOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

/**
 * Static convenience API for Brotli compression and decompression
 * (RFC 7932).
 *
 * <h2>Byte-array API</h2>
 * <pre>{@code
 * byte[] compressed   = Brotjli.compress(data, 5);
 * byte[] decompressed = Brotjli.decompress(compressed);
 * }</pre>
 *
 * <h2>Stream API</h2>
 * <pre>{@code
 * // Compress a file
 * try (var in = new FileInputStream("input.bin");
 *      var out = new BrotliOutputStream(new FileOutputStream("input.bin.br"), 5)) {
 *     in.transferTo(out);
 * }
 *
 * // Decompress a file
 * try (var in = new BrotliInputStream(new FileInputStream("input.bin.br"));
 *      var out = new FileOutputStream("output.bin")) {
 *     in.transferTo(out);
 * }
 * }</pre>
 *
 * <h2>Parallel bulk compression</h2>
 * <pre>{@code
 * List<byte[]> results = Brotjli.compressAll(inputs, 5);
 * }</pre>
 *
 * <p>All methods are thread-safe — each call uses a thread-local encoder
 * or decoder instance via {@link BrotjliEncoder#getInstance()} /
 * {@link BrotjliDecoder#getInstance()}. For high-concurrency scenarios,
 * use {@link com.brotjli.pool.EncoderPool} / {@link com.brotjli.pool.DecoderPool}.
 *
 * <p>Quality range is {@code 0} (store / no compression) through
 * {@code 11} (maximum compression). Default quality is {@code 2}.
 *
 * @see BrotjliEncoder
 * @see BrotjliDecoder
 * @see BrotliInputStream
 * @see BrotliOutputStream
 */
public final class Brotjli {
    private Brotjli() {}

    // ==================== Byte array API ====================

    /**
     * Compress {@code input} at the given quality level.
     *
     * @param input   uncompressed data
     * @param quality compression quality (0&ndash;11)
     * @return Brotli-compressed data (never {@code null})
     */
    public static byte[] compress(byte[] input, int quality) {
        return BrotjliEncoder.getInstance().encode(input, quality);
    }

    /**
     * Compress with default quality 2.
     *
     * @param input uncompressed data
     * @return Brotli-compressed data
     */
    public static byte[] compress(byte[] input) {
        return compress(input, 2);
    }

    /**
     * Decompress Brotli-compressed data.
     *
     * @param compressed Brotli-compressed bytes
     * @return decompressed data
     */
    public static byte[] decompress(byte[] compressed) {
        return BrotjliDecoder.getInstance().decode(compressed);
    }

    /**
     * Decompress data from a {@link ByteBuffer}.
     *
     * @param compressed Brotli-compressed data in a ByteBuffer
     * @return decompressed data
     */
    public static byte[] decompress(ByteBuffer compressed) {
        return BrotjliDecoder.getInstance().decode(compressed);
    }

    // ==================== Stream API ====================

    /**
     * Compress all bytes read from an {@link InputStream}.
     *
     * @param input   uncompressed data stream (not closed)
     * @param quality compression quality (0&ndash;11)
     * @return compressed data
     * @throws IOException if reading the stream fails
     */
    public static byte[] compress(InputStream input, int quality) throws IOException {
        return BrotjliEncoder.getInstance().encode(input, quality);
    }

    /**
     * Compress all bytes read from an {@link InputStream} with default quality 2.
     *
     * @param input uncompressed data stream
     * @return compressed data
     * @throws IOException if reading fails
     */
    public static byte[] compress(InputStream input) throws IOException {
        return compress(input, 2);
    }

    /**
     * Compress from an {@link InputStream} and write compressed data
     * directly to an {@link OutputStream}.
     *
     * @param input   uncompressed data source
     * @param output  compressed data destination
     * @param quality compression quality (0&ndash;11)
     * @throws IOException on I/O error
     */
    public static void compress(InputStream input, OutputStream output, int quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = input.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        byte[] compressed = BrotjliEncoder.getInstance().encode(baos.toByteArray(), quality);
        output.write(compressed);
    }

    /**
     * Decompress all bytes read from an {@link InputStream} containing
     * Brotli-compressed data.
     *
     * @param compressed compressed data stream
     * @return decompressed data
     * @throws IOException if reading fails
     */
    public static byte[] decompress(InputStream compressed) throws IOException {
        return BrotjliDecoder.getInstance().decode(compressed);
    }

    /**
     * Decompress from an {@link InputStream} and write uncompressed data
     * directly to an {@link OutputStream}.
     *
     * @param compressed compressed data stream
     * @param output     decompressed data destination
     * @throws IOException on I/O error
     */
    public static void decompress(InputStream compressed, OutputStream output) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = compressed.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        byte[] decompressed = BrotjliDecoder.getInstance().decode(baos.toByteArray());
        output.write(decompressed);
    }

    /**
     * Decompress from a {@link ReadableByteChannel} to an {@link OutputStream}.
     *
     * @param compressed compressed data channel
     * @param output     decompressed data destination
     * @throws IOException on I/O error
     */
    public static void decompress(ReadableByteChannel compressed, OutputStream output) throws IOException {
        decompress(Channels.newInputStream(compressed), output);
    }

    /**
     * Create a {@link BrotliInputStream} over compressed bytes.
     *
     * @param compressed Brotli-compressed data
     * @return a new decompressing input stream
     */
    public static BrotliInputStream newInputStream(byte[] compressed) {
        return new BrotliInputStream(compressed);
    }

    /**
     * Create a {@link BrotliInputStream} over a compressed data stream.
     *
     * @param compressed compressed data source (read fully then decompressed)
     * @return a new decompressing input stream
     * @throws IOException if reading the compressed stream fails
     */
    public static BrotliInputStream newInputStream(InputStream compressed) throws IOException {
        return new BrotliInputStream(compressed);
    }

    /**
     * Create a {@link BrotliOutputStream} that writes compressed data
     * to the given {@link OutputStream}.
     *
     * @param out     destination for compressed data
     * @param quality compression quality (0&ndash;11)
     * @return a new compressing output stream
     */
    public static BrotliOutputStream newOutputStream(OutputStream out, int quality) {
        return new BrotliOutputStream(quality, out);
    }

    /**
     * Create a {@link BrotliOutputStream} with default quality 2.
     *
     * @param out destination for compressed data
     * @return a new compressing output stream
     */
    public static BrotliOutputStream newOutputStream(OutputStream out) {
        return new BrotliOutputStream(out);
    }

    /**
     * Create a {@link BrotliOutputStream} that buffers compressed data.
     * Retrieve the result via {@link BrotliOutputStream#getCompressedData()}
     * after closing the stream.
     *
     * @param quality compression quality (0&ndash;11)
     * @return a new compressing output stream
     */
    public static BrotliOutputStream newOutputStream(int quality) {
        return new BrotliOutputStream(quality);
    }

    // ==================== Parallel bulk operations ====================

    /**
     * Compress multiple byte arrays in parallel using virtual threads.
     * Results maintain the same order as the input list.
     *
     * @param inputs  list of uncompressed data arrays
     * @param quality compression quality (0&ndash;11)
     * @return list of compressed arrays in the same order as {@code inputs}
     */
    public static List<byte[]> compressAll(List<byte[]> inputs, int quality) {
        int n = inputs.size();
        List<byte[]> results = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) results.add(null);

        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final byte[] input = inputs.get(idx);
            Thread t = Thread.ofVirtual().unstarted(() -> {
                results.set(idx, compress(input, quality));
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during parallel compress", e);
            }
        }
        return results;
    }

    /**
     * Compress all with default quality 2.
     *
     * @param inputs list of uncompressed data arrays
     * @return list of compressed arrays
     */
    public static List<byte[]> compressAll(List<byte[]> inputs) {
        return compressAll(inputs, 2);
    }

    /**
     * Decompress multiple compressed byte arrays in parallel using virtual
     * threads. Results maintain the same order as the input list.
     *
     * @param compressedList list of Brotli-compressed arrays
     * @return list of decompressed arrays in the same order
     */
    public static List<byte[]> decompressAll(List<byte[]> compressedList) {
        int n = compressedList.size();
        List<byte[]> results = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) results.add(null);

        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final byte[] compressed = compressedList.get(idx);
            Thread t = Thread.ofVirtual().unstarted(() -> {
                results.set(idx, decompress(compressed));
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during parallel decompress", e);
            }
        }
        return results;
    }
}
