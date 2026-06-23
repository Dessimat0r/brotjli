package com.brotjli;

import org.junit.jupiter.api.Test;
import java.util.Random;

public class DebugTest {

    @Test
    void debugQuality1() {
        byte[] data = new byte[100];
        new Random(42).nextBytes(data);
        
        int q = 1;
        System.out.println("Quality: " + q);
        byte[] compressed = Brotjli.compress(data, q);
        System.out.println("  Compressed length: " + compressed.length);
        System.out.println("  All bytes: ");
        for (int i = 0; i < compressed.length; i++) {
            System.out.printf("%02X ", compressed[i]);
            if ((i + 1) % 16 == 0) System.out.println();
        }
        System.out.println();
        
        // Print bits
        System.out.println("  Bits: ");
        for (int i = 0; i < compressed.length; i++) {
            int b = compressed[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                System.out.print((b >> bit) & 1);
            }
            System.out.print(" ");
            if ((i + 1) % 8 == 0) System.out.println();
        }
        System.out.println();
        
        byte[] decompressed = Brotjli.decompress(compressed);
        System.out.println("  Decompressed length: " + decompressed.length);
        System.out.println("  Match: " + java.util.Arrays.equals(data, decompressed));
        if (!java.util.Arrays.equals(data, decompressed)) {
            System.out.println("  Original first 20 bytes: ");
            for (int i = 0; i < Math.min(20, data.length); i++) System.out.printf("%02X ", data[i]);
            System.out.println();
            System.out.println("  Decompressed first 20 bytes: ");
            for (int i = 0; i < Math.min(20, decompressed.length); i++) System.out.printf("%02X ", decompressed[i]);
            System.out.println();
            for (int i = 0; i < Math.min(data.length, decompressed.length); i++) {
                if (data[i] != decompressed[i]) {
                    System.out.printf("  First diff at index %d: expected %02X, got %02X\n", i, data[i], decompressed[i]);
                    break;
                }
            }
        }
    }
}