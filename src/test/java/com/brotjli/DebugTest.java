package com.brotjli;

import org.junit.jupiter.api.Test;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

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
    }
}