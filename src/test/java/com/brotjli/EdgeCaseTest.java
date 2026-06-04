package com.brotjli;

import org.junit.jupiter.api.Test;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class EdgeCaseTest {

    @Test
    void emptyInput() {
        byte[] compressed = Brotjli.compress(new byte[0], 0);
        assertNotNull(compressed);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertEquals(0, decompressed.length);
    }

    @Test
    void singleByteAllValues() {
        for (int b = 0; b < 256; b++) {
            byte[] input = new byte[]{(byte)b};
            byte[] compressed = Brotjli.compress(input, 0);
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(input, decompressed, "Failed for byte value: " + b);
        }
    }

    @Test
    void veryLargeRepeatedByte() {
        byte[] input = new byte[10_000];
        java.util.Arrays.fill(input, (byte)'A');
        byte[] compressed = Brotjli.compress(input, 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(input, decompressed);
    }

    @Test
    void binaryData() {
        Random rng = new Random(12345);
        for (int size : new int[]{50, 500, 5000}) {
            byte[] input = new byte[size];
            rng.nextBytes(input);
            for (int q = 0; q <= 0; q++) {
                byte[] compressed = Brotjli.compress(input, q);
                byte[] decompressed = Brotjli.decompress(compressed);
                assertArrayEquals(input, decompressed, "Binary round-trip failed size=" + size + " q=" + q);
            }
        }
    }

    @Test
    void unicodeText() {
        String text = "The quick brown fox jumps over the lazy dog. "
                + "Pack my box with five dozen liquor jugs. "
                + "\u65E5\u672C\u8A9E\u306E\u30C6\u30B9\u30C8\u6587\u7AE0\u3067\u3059\u3002"
                + "\u0421\u044A\u0435\u0448\u044C \u0435\u0449\u0451 \u044D\u0442\u0438\u0445 "
                + "\u043C\u044F\u0433\u043A\u0438\u0445 \u0444\u0440\u0430\u043D\u0446\u0443\u0437\u0441\u043A\u0438\u0445 "
                + "\u0431\u0443\u043B\u043E\u043A, \u0434\u0430 \u0432\u044B\u043F\u0435\u0439 \u0436\u0435 \u0447\u0430\u044E.";
        byte[] input = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] compressed = Brotjli.compress(input, 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(input, decompressed);
    }

    @Test
    void qualityLevelValidation() {
        byte[] input = "Test data".getBytes();
        byte[] compressed = Brotjli.compress(input, 0);
        assertNotNull(compressed);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(input, decompressed);
    }

    @Test
    void repeatedPatterns() {
        byte[] data = new byte[5000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte)("ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(i % 26));
        }
        byte[] compressed = Brotjli.compress(data, 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void veryLargeAlphabet() {
        byte[] data = new byte[50000];
        new Random(9999).nextBytes(data);
        for (int q = 0; q <= 3; q++) {
            byte[] compressed = Brotjli.compress(data, q);
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(data, decompressed, "Large alphabet failed q=" + q);
        }
    }

    @Test
    void huffmanTreeShapes() {
        byte[] balanced = new byte[2560];
        for (int i = 0; i < balanced.length; i++) {
            balanced[i] = (byte) (i % 256);
        }

        byte[] skewed = new byte[10000];
        java.util.Arrays.fill(skewed, (byte) 'A');
        for (int i = 0; i < 20; i++) {
            skewed[i * 500] = (byte) 'B';
            skewed[i * 500 + 1] = (byte) 'C';
            skewed[i * 500 + 2] = (byte) 'D';
        }

        byte[] sparse = new byte[5000];
        new Random(42).nextBytes(sparse);

        byte[][] datasets = {balanced, skewed, sparse};
        String[] names = {"balanced", "skewed", "sparse"};

        for (int d = 0; d < datasets.length; d++) {
            for (int q = 0; q <= 3; q++) {
                byte[] compressed = Brotjli.compress(datasets[d], q);
                byte[] decompressed = Brotjli.decompress(compressed);
                assertArrayEquals(datasets[d], decompressed,
                    "Huffman shape " + names[d] + " failed q=" + q);
            }
        }
    }

    @Test
    void boundaryDataSizes() {
        int[] sizes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                       16, 32, 64, 128, 256, 512, 1024};
        for (int size : sizes) {
            byte[] data = new byte[size];
            new Random(size * 31).nextBytes(data);
            for (int q = 0; q <= 3; q++) {
                byte[] compressed = Brotjli.compress(data, q);
                byte[] decompressed = Brotjli.decompress(compressed);
                assertArrayEquals(data, decompressed,
                    "Boundary size=" + size + " failed q=" + q);
            }
        }
    }

    @Test
    void qualityEdgeCases() {
        byte[] data = new byte[5000];
        new Random(1234).nextBytes(data);

        byte[] compressed0 = Brotjli.compress(data, 0);
        byte[] decompressed0 = Brotjli.decompress(compressed0);
        assertArrayEquals(data, decompressed0);

        byte[] compressed3 = Brotjli.compress(data, 3);
        byte[] decompressed3 = Brotjli.decompress(compressed3);
        assertArrayEquals(data, decompressed3);
    }

    @Test
    void longRepeatedRun() {
        byte[] data = new byte[20000];
        java.util.Arrays.fill(data, (byte) 0x42);
        for (int q = 0; q <= 3; q++) {
            byte[] compressed = Brotjli.compress(data, q);
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(data, decompressed, "Long run failed q=" + q);
        }
    }

    @Test
    void complexPrefixAllQualities() {
        String complex = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz"
            + "!@#$%^&*()_+-=[]{}|;':\",./<>?`~ ";
        byte[] data = complex.getBytes();
        for (int q = 0; q <= 3; q++) {
            byte[] compressed = Brotjli.compress(data, q);
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(data, decompressed,
                "Complex prefix round-trip failed q=" + q);
        }
    }
}
