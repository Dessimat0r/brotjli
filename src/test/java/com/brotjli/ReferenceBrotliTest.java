package com.brotjli;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

public class ReferenceBrotliTest {

    @Test
    void testAgainstReferenceCli() throws Exception {
        byte[][] testInputs = {
            new byte[0],
            "Hello World!".getBytes(),
            "A".repeat(10000).getBytes(),
            buildComplexTestData(5000),
            buildRandomData(10000)
        };

        for (byte[] input : testInputs) {
            // Test standard qualities
            for (int q : new int[]{0, 1, 2, 3, 5, 9, 11}) {
                byte[] compressed = Brotjli.compress(input, q);
                assertNotNull(compressed);

                File compressedFile = File.createTempFile("brotjli-test", ".br");
                File decompressedFile = File.createTempFile("brotjli-test", ".dec");
                try {
                    System.out.println("Testing input of size " + input.length + " at quality " + q);
                    try (FileOutputStream fos = new FileOutputStream(compressedFile)) {
                        fos.write(compressed);
                    }
                    // Run: brotli -d -c compressedFile > decompressedFile
                    ProcessBuilder pb = new ProcessBuilder(
                        "brotli", "-d", "-f", "-o", decompressedFile.getAbsolutePath(), compressedFile.getAbsolutePath()
                    );
                    Process process = pb.start();
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        // Copy to test.br for debugging
                        Files.copy(compressedFile.toPath(), new File("test.br").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        // Capture error output
                        InputStream err = process.getErrorStream();
                        byte[] errBytes = err.readAllBytes();
                        fail("Reference brotli CLI failed to decode size " + input.length + " at quality " + q + 
                             " with exit code " + exitCode + ". Error: " + new String(errBytes));
                    }

                    byte[] decompressed = Files.readAllBytes(decompressedFile.toPath());
                    if (!java.util.Arrays.equals(input, decompressed)) {
                        Files.copy(compressedFile.toPath(), new File("test.br").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        assertArrayEquals(input, decompressed, 
                            "Decompressed output from reference CLI does not match original at quality " + q);
                    }
                } finally {
                    compressedFile.delete();
                    decompressedFile.delete();
                }
            }
        }
    }

    private byte[] buildComplexTestData(int size) {
        StringBuilder sb = new StringBuilder();
        String chunk = "The quick brown fox jumps over the lazy dog. ";
        while (sb.length() < size) {
            sb.append(chunk);
        }
        return sb.substring(0, size).getBytes();
    }

    private byte[] buildRandomData(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        return data;
    }
}
