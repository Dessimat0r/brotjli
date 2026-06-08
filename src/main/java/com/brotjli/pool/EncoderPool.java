package com.brotjli.pool;

import com.brotjli.BrotjliEncoder;
import com.brotjli.stream.BrotliOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe pool of {@link BrotjliEncoder} instances.
 *
 * <p>Suitable for high-concurrency server environments. Uses a borrow/release
 * pattern backed by a {@link Semaphore} and a {@link BlockingQueue}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EncoderPool pool = new EncoderPool(maxInstances, quality);
 * BrotjliEncoder enc = pool.borrow();
 * try {
 *     byte[] result = enc.encode(data);
 * } finally {
 *     pool.release(enc);
 * }
 * }</pre>
 *
 * <h2>Convenience methods</h2>
 * <pre>{@code
 * byte[] compressed = pool.compress(data);           // byte array
 * byte[] compressed = pool.compress(byteBuffer);     // ByteBuffer
 * byte[] compressed = pool.compress(inputStream);    // InputStream
 * pool.compress(inputStream, outputStream, quality); // stream-to-stream
 * List<byte[]> results = pool.compressAll(inputs);   // parallel bulk
 * }</pre>
 *
 * <p>Instances are automatically reset on release for safe reuse.
 * Implements {@link AutoCloseable} for try-with-resources.
 */
public final class EncoderPool implements AutoCloseable {
    private final BlockingQueue<BrotjliEncoder> available;
    private final Semaphore permits;
    private final AtomicInteger created;
    private final int maxSize;
    private final int defaultQuality;
    private volatile boolean closed;

    /**
     * Create an encoder pool.
     *
     * @param maxSize        maximum concurrent encoder instances
     * @param defaultQuality default compression quality (0&ndash;11)
     */
    public EncoderPool(int maxSize, int defaultQuality) {
        this.maxSize = maxSize;
        this.defaultQuality = Math.max(0, Math.min(11, defaultQuality));
        this.available = new LinkedBlockingQueue<>();
        this.permits = new Semaphore(maxSize);
        this.created = new AtomicInteger(0);
        this.closed = false;
    }

    /** Create pool with default quality 2. */
    public EncoderPool(int maxSize) {
        this(maxSize, 2);
    }

    /** Create pool sized to available processors with default quality 2. */
    public EncoderPool() {
        this(Runtime.getRuntime().availableProcessors(), 2);
    }

    /**
     * Borrow an encoder, blocking until one is available.
     *
     * @return a borrowed encoder instance
     * @throws InterruptedException  if interrupted while waiting
     * @throws IllegalStateException if the pool is closed
     */
    public BrotjliEncoder borrow() throws InterruptedException {
        if (closed) throw new IllegalStateException("Pool is closed");
        permits.acquire();
        BrotjliEncoder encoder = available.poll();
        if (encoder == null) {
            encoder = new BrotjliEncoder();
            created.incrementAndGet();
        }
        return encoder;
    }

    /**
     * Borrow with timeout.
     *
     * @param timeout maximum time to wait
     * @param unit    time unit
     * @return a borrowed encoder, or {@code null} on timeout
     * @throws InterruptedException if interrupted
     */
    public BrotjliEncoder borrow(long timeout, TimeUnit unit) throws InterruptedException {
        if (closed) throw new IllegalStateException("Pool is closed");
        if (!permits.tryAcquire(timeout, unit)) return null;
        BrotjliEncoder encoder = available.poll();
        if (encoder == null) {
            encoder = new BrotjliEncoder();
            created.incrementAndGet();
        }
        return encoder;
    }

    /** Release an encoder back to the pool (resets it automatically). */
    public void release(BrotjliEncoder encoder) {
        if (encoder == null) return;
        encoder.reset();
        available.offer(encoder);
        permits.release();
    }

    /** @return total encoder instances created */
    public int size() { return created.get(); }

    /** @return idle encoder instances available */
    public int available() { return available.size(); }

    @Override
    public void close() {
        closed = true;
        available.clear();
        permits.drainPermits();
    }

    // ==================== Convenience methods ====================

    /** Compress with default quality. */
    public byte[] compress(byte[] input) { return compress(input, defaultQuality); }

    /** Compress byte array at given quality. */
    public byte[] compress(byte[] input, int quality) {
        try { BrotjliEncoder enc = borrow(); try { return enc.encode(input, quality); } finally { release(enc); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }

    /** Compress ByteBuffer with default quality. */
    public byte[] compress(ByteBuffer input) { return compress(input, defaultQuality); }

    /** Compress ByteBuffer at given quality. */
    public byte[] compress(ByteBuffer input, int quality) {
        try { BrotjliEncoder enc = borrow(); try { return enc.encode(input, quality); } finally { release(enc); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }

    /** Compress from InputStream with default quality. */
    public byte[] compress(InputStream input) throws IOException { return compress(input, defaultQuality); }

    /** Compress from InputStream at given quality. */
    public byte[] compress(InputStream input, int quality) throws IOException {
        try { BrotjliEncoder enc = borrow(); try { return enc.encode(input, quality); } finally { release(enc); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }

    /** Compress stream-to-stream. */
    public void compress(InputStream input, OutputStream output, int quality) throws IOException {
        try { BrotjliEncoder enc = borrow(); try { enc.encode(input, output, quality); } finally { release(enc); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }

    /**
     * Compress multiple inputs in parallel using virtual threads.
     * Results maintain the same order as inputs.
     */
    public List<byte[]> compressAll(List<byte[]> inputs, int quality) {
        int n = inputs.size();
        List<byte[]> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) results.add(null);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Thread t = Thread.ofVirtual().unstarted(() -> results.set(idx, compress(inputs.get(idx), quality)));
            threads.add(t); t.start();
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
        }
        return results;
    }

    /** Parallel compress with default quality. */
    public List<byte[]> compressAll(List<byte[]> inputs) { return compressAll(inputs, defaultQuality); }

    /** Create a compressing output stream backed by this pool. */
    public BrotliOutputStream newOutputStream(OutputStream out) { return new BrotliOutputStream(out); }

    /** Create a compressing output stream with given quality. */
    public BrotliOutputStream newOutputStream(OutputStream out, int quality) { return new BrotliOutputStream(quality, out); }
}
