package com.brotjli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class RoundTripTest {

    private static final int SEED_1 = 42;
    private static final int SEED_2 = 137;
    private static final int SEED_3 = 2024;
    private static final int QUALITY = 0;

    record TestCase(String name, byte[] data) {}
    record QualityTestCase(String name, byte[] data, int quality) {}

    static Stream<TestCase> testCases() {
        Stream.Builder<TestCase> cases = Stream.builder();

        cases.add(new TestCase("empty", new byte[0]));

        cases.add(new TestCase("byte_0", new byte[]{0}));
        cases.add(new TestCase("byte_1", new byte[]{1}));
        cases.add(new TestCase("byte_127", new byte[]{127}));
        cases.add(new TestCase("byte_255", new byte[]{(byte) 0xFF}));

        byte[] data10 = new byte[10];
        new Random(SEED_1).nextBytes(data10);
        cases.add(new TestCase("10_bytes", data10));

        byte[] data100 = new byte[100];
        new Random(SEED_1).nextBytes(data100);
        cases.add(new TestCase("100_bytes", data100));

        byte[] data1000 = new byte[1000];
        new Random(SEED_1).nextBytes(data1000);
        cases.add(new TestCase("1000_bytes", data1000));

        byte[] data10000 = new byte[10000];
        new Random(SEED_1).nextBytes(data10000);
        cases.add(new TestCase("10000_bytes", data10000));

        byte[] data50000 = new byte[50000];
        new Random(SEED_1).nextBytes(data50000);
        cases.add(new TestCase("50000_bytes", data50000));

        cases.add(new TestCase("all_zeros_100", new byte[100]));

        byte[] allOnes = new byte[100];
        java.util.Arrays.fill(allOnes, (byte) 0xFF);
        cases.add(new TestCase("all_ones_100", allOnes));

        byte[] alternating = new byte[100];
        for (int i = 0; i < 100; i++) alternating[i] = (byte) (i & 1);
        cases.add(new TestCase("alternating_100", alternating));

        byte[] sequential = new byte[256];
        for (int i = 0; i < 256; i++) sequential[i] = (byte) i;
        cases.add(new TestCase("sequential_256", sequential));

        byte[] randomSeed42 = new byte[1000];
        new Random(42).nextBytes(randomSeed42);
        cases.add(new TestCase("random_seed_42", randomSeed42));

        byte[] randomSeed137 = new byte[1000];
        new Random(SEED_2).nextBytes(randomSeed137);
        cases.add(new TestCase("random_seed_137", randomSeed137));

        byte[] randomSeed2024 = new byte[1000];
        new Random(SEED_3).nextBytes(randomSeed2024);
        cases.add(new TestCase("random_seed_2024", randomSeed2024));

        return cases.build();
    }

    static Stream<QualityTestCase> qualityTestCases() {
        Stream.Builder<QualityTestCase> cases = Stream.builder();
        int[] qualities = {0, 1, 2, 3};

        String complexText = "Hello World! This text has many distinct symbols: "
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789";
        byte[] complexBytes = complexText.getBytes();
        for (int q : qualities) {
            cases.add(new QualityTestCase("complex_prefix_text", complexBytes, q));
        }

        byte[] all256 = new byte[256];
        for (int i = 0; i < 256; i++) all256[i] = (byte) i;
        for (int q : qualities) {
            cases.add(new QualityTestCase("all_256_values", all256, q));
        }

        byte[] random5k = new byte[5000];
        new Random(SEED_1).nextBytes(random5k);
        for (int q : qualities) {
            cases.add(new QualityTestCase("random_5k", random5k, q));
        }

        byte[] random10k = new byte[10000];
        new Random(SEED_2).nextBytes(random10k);
        for (int q : qualities) {
            cases.add(new QualityTestCase("random_10k", random10k, q));
        }

        byte[] patterned = new byte[5000];
        for (int i = 0; i < patterned.length; i++) {
            patterned[i] = (byte) ("ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(i % 26));
        }
        for (int q : qualities) {
            cases.add(new QualityTestCase("patterned_26sym_5k", patterned, q));
        }

        byte[] repeated = new byte[5000];
        java.util.Arrays.fill(repeated, (byte) 'X');
        for (int q : qualities) {
            cases.add(new QualityTestCase("repeated_single_5k", repeated, q));
        }

        byte[] balanced = new byte[5120];
        for (int i = 0; i < balanced.length; i++) {
            balanced[i] = (byte) (i % 256);
        }
        for (int q : qualities) {
            cases.add(new QualityTestCase("balanced_freq_5120", balanced, q));
        }

        byte[] skewed = new byte[5000];
        java.util.Arrays.fill(skewed, (byte) 'A');
        for (int i = 0; i < 50; i++) {
            skewed[i * 100] = (byte) 'B';
            skewed[i * 100 + 1] = (byte) 'C';
            skewed[i * 100 + 2] = (byte) 'D';
            skewed[i * 100 + 3] = (byte) 'E';
        }
        for (int q : qualities) {
            cases.add(new QualityTestCase("skewed_freq_5000", skewed, q));
        }

        byte[] varied = new byte[10000];
        Random rng = new Random(SEED_3);
        for (int i = 0; i < varied.length; i++) {
            varied[i] = (byte) rng.nextInt(200);
        }
        for (int q : qualities) {
            cases.add(new QualityTestCase("varied_freq_200sym_10k", varied, q));
        }

        return cases.build();
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void roundTrip(TestCase tc) {
        byte[] compressed = Brotjli.compress(tc.data(), QUALITY);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0 || tc.data().length == 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(tc.data(), decompressed, "Round-trip failed for: " + tc.name());
    }

    @ParameterizedTest
    @MethodSource("qualityTestCases")
    void qualityRoundTrip(QualityTestCase tc) {
        byte[] compressed = Brotjli.compress(tc.data(), tc.quality());
        assertNotNull(compressed);
        assertTrue(compressed.length > 0 || tc.data().length == 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(tc.data(), decompressed,
            "Round-trip failed for: " + tc.name() + " q=" + tc.quality());
    }

    @Test
    void encoderDecoderReuse() {
        BrotjliEncoder encoder = new BrotjliEncoder();
        BrotjliDecoder decoder = new BrotjliDecoder();

        byte[] data1 = "Hello, Brotjli!".getBytes();
        byte[] compressed1 = encoder.encode(data1, QUALITY);
        byte[] decompressed1 = decoder.decode(compressed1);
        assertArrayEquals(data1, decompressed1, "First encode/decode round-trip failed");

        encoder.reset();
        decoder.reset();

        byte[] data2 = "Another message for testing reuse.".getBytes();
        byte[] compressed2 = encoder.encode(data2, QUALITY);
        byte[] decompressed2 = decoder.decode(compressed2);
        assertArrayEquals(data2, decompressed2, "Second encode/decode round-trip failed");
    }

    @Test
    void encoderDecoderReuseHigherQualities() {
        BrotjliEncoder encoder = new BrotjliEncoder();
        BrotjliDecoder decoder = new BrotjliDecoder();

        String complexText = "Complex prefix test with many distinct symbols: "
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789!@#$%^&*()";
        byte[] data = complexText.getBytes();

        for (int q = 0; q <= 3; q++) {
            byte[] compressed = encoder.encode(data, q);
            byte[] decompressed = decoder.decode(compressed);
            assertArrayEquals(data, decompressed,
                "Reuse round-trip failed for quality " + q);
            encoder.reset();
            decoder.reset();
        }
    }
}
