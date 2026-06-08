package com.brotjli.pool;

import com.brotjli.BrotjliDecoder;
import com.brotjli.stream.BrotliInputStream;

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

    // ==================== Convenience methods ====================

    public byte[] decompress(byte[] compressed) {
        try { BrotjliDecoder dec = borrow(); try { return dec.decode(compressed); } finally { release(dec); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }

    public byte[] decompress(ByteBuffer compressed) {
        try { BrotjliDecoder dec = borrow(); try { return dec.decode(compressed); } finally { release(dec); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }

    public byte[] decompress(InputStream compressed) throws IOException {
        try { BrotjliDecoder dec = borrow(); try { return dec.decode(compressed); } finally { release(dec); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }

    public void decompress(InputStream compressed, OutputStream output) throws IOException {
        try { BrotjliDecoder dec = borrow(); try { dec.decode(compressed, output); } finally { release(dec); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }

    public List<byte[]> decompressAll(List<byte[]> compressedList) {
        int n = compressedList.size();
        List<byte[]> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) results.add(null);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Thread t = Thread.ofVirtual().unstarted(() -> results.set(idx, decompress(compressedList.get(idx))));
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
        }
        return results;
    }

    public BrotliInputStream newInputStream(byte[] compressed) { return new BrotliInputStream(compressed); }
    public BrotliInputStream newInputStream(InputStream compressed) throws IOException { return new BrotliInputStream(compressed); }
}
