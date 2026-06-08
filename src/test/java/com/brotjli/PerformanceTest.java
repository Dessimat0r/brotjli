package com.brotjli;

import com.brotjli.pool.EncoderPool;
import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class PerformanceTest {

    @Test
    void throughputTest() {
        byte[] data = new byte[10_000];
        new Random(42).nextBytes(data);
        BrotjliEncoder enc = new BrotjliEncoder();
        BrotjliDecoder dec = new BrotjliDecoder();

        int warmupIterations = 3;
        for (int i = 0; i < warmupIterations; i++) {
            byte[] compressed = enc.encode(data, 1);
            byte[] decompressed = dec.decode(compressed);
            assertArrayEquals(data, decompressed);
            enc.reset();
            dec.reset();
        }

        int iterations = 10;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            byte[] compressed = enc.encode(data, 1);
            byte[] decompressed = dec.decode(compressed);
            assertArrayEquals(data, decompressed);
            enc.reset();
            dec.reset();
        }
        long elapsed = System.nanoTime() - start;
        long bytesProcessed = (long) iterations * data.length * 2;
        long throughput = bytesProcessed * 1_000_000_000L / elapsed;
        assertTrue(throughput > 0, "Throughput should be measurable");
        assertTrue(elapsed > 0, "Elapsed time should be measurable");
    }

    @Test
    void poolStressTest() throws Exception {
        int numTasks = 20;
        EncoderPool pool = new EncoderPool(8, 1);
        AtomicInteger successCount = new AtomicInteger(0);
        byte[][] testData = new byte[numTasks][];
        Random rng = new Random(1234);
        for (int i = 0; i < numTasks; i++) {
            testData[i] = new byte[2000];
            rng.nextBytes(testData[i]);
        }

        try (pool) {
            CountDownLatch latch = new CountDownLatch(numTasks);
            long start = System.nanoTime();
            for (int i = 0; i < numTasks; i++) {
                final int taskId = i;
                Thread.ofVirtual().start(() -> {
                    try {
                        BrotjliEncoder enc = pool.borrow();
                        byte[] compressed = enc.encode(testData[taskId], 1);
                        byte[] decompressed = Brotjli.decompress(compressed);
                        assertArrayEquals(testData[taskId], decompressed);
                        pool.release(enc);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Task " + taskId + " failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            long elapsed = System.nanoTime() - start;
            assertTrue(completed, "Pool stress tasks should complete within timeout");
            assertEquals(numTasks, successCount.get());
            assertTrue(elapsed > 0, "Completion time should be measurable");
        }
    }
}
