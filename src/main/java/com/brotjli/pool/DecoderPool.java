package com.brotjli.pool;

import com.brotjli.BrotjliDecoder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe pool of BrotjliDecoder instances.
 *
 * Uses the same borrow/release pattern as EncoderPool.
 * Suitable for web servers handling multiple concurrent requests.
 */
public final class DecoderPool implements AutoCloseable {
    private final BlockingQueue<BrotjliDecoder> available;
    private final Semaphore permits;
    private final AtomicInteger created;
    private final int maxSize;
    private volatile boolean closed;

    public DecoderPool(int maxSize) {
        this.maxSize = maxSize;
        this.available = new LinkedBlockingQueue<>();
        this.permits = new Semaphore(maxSize);
        this.created = new AtomicInteger(0);
        this.closed = false;
    }

    public DecoderPool() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Borrow a decoder. Blocks until one is available.
     */
    public BrotjliDecoder borrow() throws InterruptedException {
        if (closed) throw new IllegalStateException("Pool is closed");
        permits.acquire();
        BrotjliDecoder decoder = available.poll();
        if (decoder == null) {
            decoder = new BrotjliDecoder();
            created.incrementAndGet();
        }
        return decoder;
    }

    /**
     * Borrow with timeout.
     */
    public BrotjliDecoder borrow(long timeout, TimeUnit unit) throws InterruptedException {
        if (closed) throw new IllegalStateException("Pool is closed");
        if (!permits.tryAcquire(timeout, unit)) return null;
        BrotjliDecoder decoder = available.poll();
        if (decoder == null) {
            decoder = new BrotjliDecoder();
            created.incrementAndGet();
        }
        return decoder;
    }

    /**
     * Release a decoder back to the pool.
     */
    public void release(BrotjliDecoder decoder) {
        if (decoder == null) return;
        decoder.reset();
        available.offer(decoder);
        permits.release();
    }

    public int size() { return created.get(); }
    public int available() { return available.size(); }

    @Override
    public void close() {
        closed = true;
        available.clear();
        permits.drainPermits();
    }
}
