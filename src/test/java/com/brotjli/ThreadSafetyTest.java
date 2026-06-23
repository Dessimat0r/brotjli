package com.brotjli;

import com.brotjli.pool.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

public class ThreadSafetyTest {

    @Test
    void encoderPoolBasicBorrowRelease() throws Exception {
        EncoderPool pool = new EncoderPool(4, 0);
        BrotjliEncoder enc = pool.borrow();
        assertNotNull(enc);
        byte[] result = enc.encode("pool test".getBytes(), 0);
        assertNotNull(result);
        pool.release(enc);
        pool.close();
    }

    @Test
    void decoderPoolBasicBorrowRelease() throws Exception {
        DecoderPool pool = new DecoderPool(4);
        BrotjliDecoder dec = pool.borrow();
        assertNotNull(dec);
        pool.release(dec);
        pool.close();
    }

    @Test
    void concurrentEncoderPoolAccess() throws Exception {
        int numThreads = 4;
        int numTasks = 8;
        EncoderPool pool = new EncoderPool(numThreads, 0);
        AtomicInteger successCount = new AtomicInteger(0);

        try (pool) {
            CountDownLatch latch = new CountDownLatch(numTasks);
            for (int i = 0; i < numTasks; i++) {
                final int taskId = i;
                Thread.ofVirtual().start(() -> {
                    BrotjliEncoder enc = null;
                    try {
                        enc = pool.borrow();
                        byte[] data = ("Task " + taskId + " data for concurrent testing").getBytes();
                        byte[] compressed = enc.encode(data, 0);
                        byte[] decompressed = Brotjli.decompress(compressed);
                        assertArrayEquals(data, decompressed);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Thread " + taskId + " failed: " + e.getMessage());
                    } finally {
                        if (enc != null) {
                            pool.release(enc);
                        }
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue(completed, "Tasks should complete within timeout");
            assertEquals(numTasks, successCount.get());
        }
    }

    @Test
    void concurrentDecoderPoolAccess() throws Exception {
        int numThreads = 4;
        int numTasks = 8;
        DecoderPool pool = new DecoderPool(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        byte[] testData = "Concurrent decoder pool test data".getBytes();
        byte[] compressed;
        try (BrotjliEncoder encoder = new BrotjliEncoder()) {
            compressed = encoder.encode(testData, 0);
        }

        try (pool) {
            CountDownLatch latch = new CountDownLatch(numTasks);
            for (int i = 0; i < numTasks; i++) {
                Thread.ofVirtual().start(() -> {
                    BrotjliDecoder dec = null;
                    try {
                        dec = pool.borrow();
                        byte[] result = dec.decode(compressed);
                        assertArrayEquals(testData, result);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Decoder thread failed: " + e.getMessage());
                    } finally {
                        if (dec != null) {
                            pool.release(dec);
                        }
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue(completed, "Decoder tasks should complete");
            assertEquals(numTasks, successCount.get());
        }
    }

    @Test
    void poolTimeout() throws Exception {
        EncoderPool pool = new EncoderPool(1, 0);
        BrotjliEncoder enc = pool.borrow();
        BrotjliEncoder enc2 = pool.borrow(100, TimeUnit.MILLISECONDS);
        assertNull(enc2, "Should fail to borrow when pool is empty");
        pool.release(enc);
        pool.close();
    }

    @Test
    void poolSizeTracking() throws Exception {
        EncoderPool pool = new EncoderPool(10, 0);
        assertEquals(0, pool.size());
        BrotjliEncoder enc = pool.borrow();
        assertEquals(1, pool.size());
        pool.release(enc);
        pool.close();
    }

    @Test
    void highContentionEncoderPool() throws Exception {
        int numThreads = 16;
        int numTasks = 100;
        EncoderPool pool = new EncoderPool(numThreads, 1);
        AtomicInteger successCount = new AtomicInteger(0);

        try (pool) {
            CountDownLatch latch = new CountDownLatch(numTasks);
            byte[][] testData = new byte[numTasks][];
            for (int i = 0; i < numTasks; i++) {
                testData[i] = ("Task " + i + " data for high contention testing with "
                    + "many distinct symbols ABCDEFGHIJKLMNOPQRSTUVWXYZ").getBytes();
            }
            for (int i = 0; i < numTasks; i++) {
                final int taskId = i;
                Thread.ofVirtual().start(() -> {
                    BrotjliEncoder enc = null;
                    try {
                        enc = pool.borrow();
                        byte[] compressed = enc.encode(testData[taskId], 1);
                        byte[] decompressed = Brotjli.decompress(compressed);
                        assertArrayEquals(testData[taskId], decompressed);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Thread " + taskId + " failed: " + e.getMessage());
                    } finally {
                        if (enc != null) {
                            pool.release(enc);
                        }
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "High contention tasks should complete within timeout");
            assertEquals(numTasks, successCount.get());
        }
    }

    @Test
    void concurrentPoolWithLargeData() throws Exception {
        int numThreads = 8;
        int numTasks = 20;
        EncoderPool pool = new EncoderPool(numThreads, 2);
        AtomicInteger successCount = new AtomicInteger(0);
        byte[][] originalData = new byte[numTasks][];

        Random rng = new Random(42);
        for (int i = 0; i < numTasks; i++) {
            originalData[i] = new byte[5000 + rng.nextInt(5000)];
            rng.nextBytes(originalData[i]);
        }

        try (pool) {
            CountDownLatch latch = new CountDownLatch(numTasks);
            for (int i = 0; i < numTasks; i++) {
                final int taskId = i;
                Thread.ofVirtual().start(() -> {
                    BrotjliEncoder enc = null;
                    try {
                        enc = pool.borrow();
                        byte[] compressed = enc.encode(originalData[taskId], 2);
                        byte[] decompressed;
                        try (BrotjliDecoder dec = new BrotjliDecoder()) {
                            decompressed = dec.decode(compressed);
                        }
                        assertArrayEquals(originalData[taskId], decompressed);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Task " + taskId + " failed: " + e.getMessage());
                    } finally {
                        if (enc != null) {
                            pool.release(enc);
                        }
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "Large data tasks should complete within timeout");
            assertEquals(numTasks, successCount.get());
        }
    }

    @Test
    void poolStressTest() throws Exception {
        int numTasks = 100;
        EncoderPool pool = new EncoderPool(8, 1);
        AtomicInteger successCount = new AtomicInteger(0);
        byte[] data = new byte[2000];
        new Random(42).nextBytes(data);

        try (pool) {
            CountDownLatch latch = new CountDownLatch(numTasks);
            long start = System.nanoTime();
            for (int i = 0; i < numTasks; i++) {
                Thread.ofVirtual().start(() -> {
                    BrotjliEncoder enc = null;
                    try {
                        enc = pool.borrow();
                        byte[] compressed = enc.encode(data, 1);
                        byte[] decompressed = Brotjli.decompress(compressed);
                        assertArrayEquals(data, decompressed);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Stress task failed: " + e.getMessage());
                    } finally {
                        if (enc != null) {
                            pool.release(enc);
                        }
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            long elapsed = System.nanoTime() - start;
            assertTrue(completed, "Pool stress tasks should complete within timeout");
            assertEquals(numTasks, successCount.get());
            assertTrue(elapsed > 0, "Should have measurable completion time");
        }
    }

    @Test
    void concurrentDataIntegrity() throws Exception {
        int numThreads = 8;
        int numTasks = 50;
        EncoderPool pool = new EncoderPool(numThreads, 1);
        AtomicInteger successCount = new AtomicInteger(0);

        try (pool) {
            CountDownLatch latch = new CountDownLatch(numTasks);
            for (int i = 0; i < numTasks; i++) {
                final int seed = i * 137;
                Thread.ofVirtual().start(() -> {
                    BrotjliEncoder enc = null;
                    try {
                        byte[] data = new byte[1000];
                        new Random(seed).nextBytes(data);
                        enc = pool.borrow();
                        byte[] compressed = enc.encode(data, 1);
                        byte[] decompressed = Brotjli.decompress(compressed);
                        assertArrayEquals(data, decompressed,
                            "Data corruption detected for seed " + seed);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Task failed: " + e.getMessage());
                    } finally {
                        if (enc != null) {
                            pool.release(enc);
                        }
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "Data integrity tasks should complete");
            assertEquals(numTasks, successCount.get());
        }
    }
}
