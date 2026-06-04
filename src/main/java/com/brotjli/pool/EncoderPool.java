package com.brotjli.pool;

import com.brotjli.BrotjliEncoder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe pool of BrotjliEncoder instances.
 *
 * Uses a borrow/release pattern for web server usage:
 * <pre>{@code
 * EncoderPool pool = new EncoderPool(maxInstances, defaultQuality);
 * BrotjliEncoder enc = pool.borrow();
 * try {
 *     byte[] result = enc.encode(data);
 * } finally {
 *     pool.release(enc);
 * }
 * }</pre>
 *
 * Instances are reset on release for reuse.
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
     * @param maxSize maximum number of encoder instances
     * @param defaultQuality default compression quality (0-3)
     */
    public EncoderPool(int maxSize, int defaultQuality) {
        this.maxSize = maxSize;
        this.defaultQuality = Math.max(0, Math.min(3, defaultQuality));
        this.available = new LinkedBlockingQueue<>();
        this.permits = new Semaphore(maxSize);
        this.created = new AtomicInteger(0);
        this.closed = false;
    }

    /**
     * Create pool with default quality 2.
     */
    public EncoderPool(int maxSize) {
        this(maxSize, 2);
    }

    /**
     * Create pool with max size = available processors.
     */
    public EncoderPool() {
        this(Runtime.getRuntime().availableProcessors(), 2);
    }

    /**
     * Borrow an encoder from the pool. Blocks until one is available.
     *
     * @return a BrotjliEncoder instance
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if pool is closed
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
     * @param timeout maximum wait time
     * @param unit time unit
     * @return a BrotjliEncoder instance, or null if timeout
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

    /**
     * Release an encoder back to the pool.
     */
    public void release(BrotjliEncoder encoder) {
        if (encoder == null) return;
        encoder.reset();
        available.offer(encoder);
        permits.release();
    }

    /**
     * Get current pool size (created instances).
     */
    public int size() {
        return created.get();
    }

    /**
     * Get available (idle) instances.
     */
    public int available() {
        return available.size();
    }

    /**
     * Close the pool and release all resources.
     */
    @Override
    public void close() {
        closed = true;
        available.clear();
        permits.drainPermits();
    }
}
