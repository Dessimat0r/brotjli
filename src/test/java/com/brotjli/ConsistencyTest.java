package com.brotjli;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

public class ConsistencyTest {

    @Test
    void deterministicCompression() {
        byte[] data = "This is a deterministic test of compression consistency.".getBytes();
        byte[] compressed1 = Brotjli.compress(data, 0);
        byte[] compressed2 = Brotjli.compress(data, 0);
        assertArrayEquals(compressed1, compressed2);
    }

    @RepeatedTest(5)
    void randomShorts() {
        Random rng = new Random();
        byte[] data = new byte[rng.nextInt(100) + 1];
        rng.nextBytes(data);
        byte[] compressed = Brotjli.compress(data, 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @RepeatedTest(3)
    void randomMedium() {
        Random rng = new Random();
        byte[] data = new byte[rng.nextInt(5000) + 100];
        rng.nextBytes(data);
        byte[] compressed = Brotjli.compress(data, 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void cascadeCompress() {
        byte[] data = "Cascade compression test: compress, decompress, re-compress".getBytes();
        for (int i = 0; i < 5; i++) {
            byte[] compressed = Brotjli.compress(data, 0);
            data = Brotjli.decompress(compressed);
        }
        assertEquals("Cascade compression test: compress, decompress, re-compress",
                     new String(data, java.nio.charset.StandardCharsets.UTF_8));
    }

    @RepeatedTest(2)
    void largeRandomData() {
        Random rng = new Random();
        byte[] data = new byte[rng.nextInt(50000) + 10000];
        rng.nextBytes(data);
        byte[] compressed = Brotjli.compress(data, 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }
}
