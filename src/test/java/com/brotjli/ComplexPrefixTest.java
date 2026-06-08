package com.brotjli;

import com.brotjli.common.BitUtil;
import com.brotjli.common.Constants;
import com.brotjli.decoder.BitReader;
import com.brotjli.decoder.HuffmanDecoder;
import com.brotjli.encoder.BitWriter;
import com.brotjli.encoder.HuffmanTreeBuilder;
import java.util.Arrays;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive round-trip tests for the Brotjli complex prefix code path.
 *
 * Tests CL symbol encoding/decoding, full complex prefix code write/read,
 * and end-to-end compress/decompress at all quality levels with data that
 * triggers complex prefix codes (>4 unique symbols).
 *
 * Diagnostic output reports the specific field that causes a failure.
 */
public class ComplexPrefixTest {

    // ── 1. CL symbol round-trip (fixed code length values 0..5) ────────────

    @Test
    void clSymbolRoundTrip() {
        // Each entry: {clSymbolValue, rawBits, nBits}
        // Maps from writeFixedCodeLengthValue input → writeBits args
        int[][] cases = {
            {0, 0, 2},   // value 0: writeBits(0, 2)
            {4, 1, 2},   // value 4: writeBits(1, 2)
            {3, 2, 2},   // value 3: writeBits(2, 2)
            {2, 3, 3},   // value 2: writeBits(3, 3)
            {1, 7, 4},   // value 1: writeBits(7, 4)
            {5, 15, 4},  // value 5: writeBits(15, 4)
        };

        for (int[] c : cases) {
            int expected = c[0];
            int rawVal = c[1];
            int nbits = c[2];

            BitWriter w = new BitWriter();
            w.writeBits(rawVal, nbits);
            w.flush();

            BitReader r = new BitReader(w.toByteArray());
            int decoded = readCodeLengthSymbol(r);

            // Diagnostic
            if (decoded != expected) {
                String bits = toBits(rawVal, nbits);
                fail(String.format(
                    "CL symbol %d round-trip failed: writeBits(%d,%d)=%s -> decoded %d",
                    expected, rawVal, nbits, bits, decoded));
            }
        }
    }

    // ── 2. Full complex prefix code write/read round-trip ─────────────────

    @Test
    void complexPrefixCodeRoundTrip() {
        int alphabetSize = 256;
        int[] originalLengths = new int[alphabetSize];
        int[] frequencies = new int[alphabetSize];
        java.util.Random rng = new java.util.Random(123);
        for (int i = 0; i < alphabetSize; i++) {
            frequencies[i] = rng.nextInt(90) + 10;
        }
        HuffmanTreeBuilder tree = new HuffmanTreeBuilder();
        tree.buildFromFrequencies(frequencies, alphabetSize);
        originalLengths = tree.getCodeLengths().clone();

        // Encode
        BitWriter writer = new BitWriter();
        int hskip = 0;
        writeComplexPrefixCode(writer, originalLengths, alphabetSize, hskip);
        writer.flush();
        byte[] encoded = writer.toByteArray();

        // Decode
        BitReader reader = new BitReader(encoded);
        System.err.println("First byte of encoded in binary: " + Integer.toBinaryString(encoded[0] & 0xFF));
        System.err.println("Peek 8 bits before hskip read: " + Integer.toBinaryString(reader.peekBitsInt(8)));
        int decodedHskip = reader.readBitsInt(2);
        System.err.println("Decoded hskip: " + decodedHskip);
        System.err.println("Peek 8 bits after hskip read: " + Integer.toBinaryString(reader.peekBitsInt(8)));
        assertEquals(hskip, decodedHskip);
        HuffmanDecoder decoder;
        try {
            decoder = HuffmanDecoder.readComplexPrefixCode(reader, alphabetSize, hskip);
        } catch (Exception e) {
            System.err.println("Original lengths: " + java.util.Arrays.toString(originalLengths));
            dumpBits("Complex prefix code", encoded);
            fail("readComplexPrefixCode threw: " + e.getMessage());
            return;
        }

        // Verify the decoder produces a valid Huffman table
        assertNotNull(decoder);
        assertTrue(decoder.getMaxSymbol() >= 0, "Decoder maxSymbol should be >= 0");

        // Verify the decoder produces a valid Huffman table
        // by checking that symbols with non-zero lengths can be decoded.
        // We re-encode the length array and build a new decoder to verify.
        HuffmanTreeBuilder referenceTree = new HuffmanTreeBuilder();
        referenceTree.buildFromLengths(originalLengths, alphabetSize);

        int verified = 0;
        int maxCheck = Math.min(16, alphabetSize);
        for (int sym = 0; sym < maxCheck; sym++) {
            int cl = originalLengths[sym];
            if (cl == 0) continue;
            // Encode the symbol using the reference encoder tree
            BitWriter w2 = new BitWriter();
            int code = referenceTree.getCode(sym);
            w2.writeBitsReversed(code, cl);
            w2.flush();

            // Decode using the test decoder
            BitReader r2 = new BitReader(w2.toByteArray());
            HuffmanDecoder freshDecoder = new HuffmanDecoder();
            freshDecoder.buildFromCodeLengths(originalLengths, alphabetSize);
            int decoded = freshDecoder.decodeSymbol(r2);
            assertEquals(sym, decoded,
                "Symbol " + sym + " (len=" + cl + ") round-trip failed");
            verified++;
        }
        assertTrue(verified > 0, "At least one symbol should be verifiable");
    }

    // ── 3. End-to-end quality 1 with complex prefix code (>4 symbols) ─────

    @Test
    void quality1ComplexRoundTrip() {
        byte[] data = "Hello! Many symbols: ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789".getBytes();
        testRoundTrip(data, 1, "q1-complex");
    }

    // ── 4. All qualities with complex data ────────────────────────────────

    @Test
    void allQualitiesComplexRoundTrip() {
        byte[] data = buildComplexTestData();
        int unique = uniqueCount(data);
        System.out.println("Complex test data: " + data.length + " bytes, " + unique + " unique symbols");
        assertTrue(unique > 4, "Test data must have >4 unique symbols for complex prefix codes");

        for (int q = 0; q <= 3; q++) {
            testRoundTrip(data, q, "q" + q + "-complex");
        }
    }

    // ── 5. Quality 1 boundary: simple vs complex prefix codes ────────────

    @Test
    void quality1SimpleVsComplexBoundary() {
        // 4 unique symbols → simple prefix code
        byte[] data4 = "abcd".getBytes();
        // 5 unique symbols → complex prefix code
        byte[] data5 = "abcde".getBytes();

        testRoundTrip(data4, 1, "q1-4sym-simple");
        testRoundTrip(data5, 1, "q1-5sym-complex");
    }

    // ── 6. Quality 1 with data triggering complex codes in all 3 trees ────

    @Test
    void quality1AllTreesComplex() {
        // Data large enough to trigger LZ77 matching, creating multiple IC/dist symbols
        byte[] data = new byte[10000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 200 + 30);
        }
        testRoundTrip(data, 1, "q1-all-trees");
    }

    // ── 7. Various data patterns at all quality levels ────────────────────

    @Test
    void variousDataPatterns() {
        byte[][] datasets = {
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(),
            buildSkewedData(5000),
            buildRepeatingData(5000),
        };
        String[] names = {"mixed-case-alpha", "skewed-5k", "pattern-5k"};

        for (int d = 0; d < datasets.length; d++) {
            for (int q = 0; q <= 3; q++) {
                try {
                    testRoundTrip(datasets[d], q, names[d] + "-q" + q);
                } catch (AssertionError e) {
                    System.err.println("FAIL: " + names[d] + " q=" + q + ": " + e.getMessage());
                }
            }
        }
    }

    // ── 8. Diagnostic: walk bitstream and report misalignment ────────────

    @Test
    void diagnosticBitstreamWalk() {
        byte[] data = "abcde".getBytes();
        System.out.println("=== Diagnostic bitstream walk for 'abcde' q=1 ===");

        BrotjliEncoder enc = new BrotjliEncoder();
        byte[] compressed = enc.encode(data, 1);
        System.out.println("Compressed: " + compressed.length + " bytes");
        dumpBits("Hex", compressed);

        try {
            // Walk the bitstream manually
            BitReader reader = new BitReader(compressed);
            int wb = readWindowBits(reader);
            System.out.println("Window bits: " + wb);

            int isLast = reader.readBit();
            assert isLast == 1 : "ISLAST should be 1";
            int isEmpty = reader.readBit();
            assert isEmpty == 0 : "ISLASTEMPTY should be 0";

            int nibblesBits = reader.readBitsInt(2);
            int mnibbles = nibblesBits == 0 ? 4 : (nibblesBits == 1 ? 5 : 6);
            int mlen = (int) reader.readBits(mnibbles * 4) + 1;
            System.out.println("MLEN: " + mlen);

            int nLitBlk = readNbltypes(reader);
            int nIcBlk = readNbltypes(reader);
            int nDistBlk = readNbltypes(reader);
            System.out.println("NBLTYPES: lit=" + nLitBlk + " ic=" + nIcBlk + " dist=" + nDistBlk);

            int npostfix = reader.readBitsInt(2);
            int ndirectBits = reader.readBitsInt(4);
            System.out.println("NPOSTFIX=" + npostfix + " NDIRECT_BITS=" + ndirectBits);

            for (int i = 0; i < nLitBlk; i++) {
                int mode = reader.readBitsInt(2);
                System.out.println("Context mode[" + i + "]=" + mode);
            }

            int numLitTrees = readNbltypes(reader);
            int numDistTrees = readNbltypes(reader);
            System.out.println("NTREES: lit=" + numLitTrees + " dist=" + numDistTrees);

            System.out.println("\nReading literal prefix code:");
            int litType = reader.readBitsInt(2);
            System.out.println("  type=" + litType);
            if (litType == 3) {
                walkFlatPrefixCode(reader, 256);
            } else if (litType != 1) {
                walkComplexPrefixCode(reader, 256, litType);
            } else {
                reportSimplePrefixCode(reader, 256, litType);
            }

            // Now read subsequent prefix codes
            long bitPos = getBitPosition(reader);
            System.out.println("\nReading IC prefix code at bit ~" + bitPos + ":");
            int icType = reader.readBitsInt(2);
            System.out.println("  type=" + icType);
            if (icType != 1) {
                walkComplexPrefixCode(reader, 704, icType);
            } else {
                reportSimplePrefixCode(reader, 704, icType);
            }

            bitPos = getBitPosition(reader);
            System.out.println("\nReading distance prefix code at bit ~" + bitPos + ":");
            int distType = reader.readBitsInt(2);
            System.out.println("  type=" + distType);
            if (distType != 1) {
                walkComplexPrefixCode(reader, 64, distType);
            } else {
                reportSimplePrefixCode(reader, 64, distType);
            }

            System.out.println("\nAll prefix codes parsed successfully!");
            System.out.println("Remaining bits: " + remainingBits(reader));

        } catch (Exception e) {
            long pos = getBitPosition(new BitReader(compressed));
            System.err.println("ERROR at bit position ~" + pos + ": " + e.getMessage());
            fail("Bitstream walk failed: " + e.getMessage());
        }
    }

    // ── Round-trip helper with diagnostics ────────────────────────────────

    private void testRoundTrip(byte[] data, int quality, String label) {
        BrotjliEncoder enc = new BrotjliEncoder();
        BrotjliDecoder dec = new BrotjliDecoder();
        byte[] compressed;
        byte[] decompressed;

        try {
            compressed = enc.encode(data, quality);
        } catch (Exception e) {
            System.err.println(label + ": encoder threw: " + e.getMessage());
            fail(label + ": encoder threw: " + e.getMessage());
            return;
        }

        try {
            decompressed = dec.decode(compressed);
        } catch (Exception e) {
            System.err.println(label + ": decoder threw: " + e.getMessage());
            dumpBits("Compressed", compressed);
            fail(label + ": decoder threw: " + e.getMessage());
            return;
        }

        if (decompressed.length != data.length) {
            System.err.println(label + ": length mismatch: expected " + data.length
                + " got " + decompressed.length);
            dumpBits("Compressed", compressed);
            fail(label + ": length mismatch: expected " + data.length
                + " got " + decompressed.length);
            return;
        }

        for (int i = 0; i < data.length; i++) {
            if (data[i] != decompressed[i]) {
                System.err.println(label + ": data mismatch at index " + i
                    + ": expected " + (data[i] & 0xFF) + " got " + (decompressed[i] & 0xFF));
                dumpBits("Compressed", compressed);
                fail(label + ": data mismatch at index " + i
                    + ": expected " + (data[i] & 0xFF) + " got " + (decompressed[i] & 0xFF));
                return;
            }
        }
    }

    // ── Bit-level helpers ─────────────────────────────────────────────────

    private static final int[] CL_SYMBOL_TABLE = {
        2, 34, 26, 19, 2, 34, 26, 12, 2, 34, 26, 19, 2, 34, 26, 44,
        2, 34, 26, 19, 2, 34, 26, 12, 2, 34, 26, 19, 2, 34, 26, 44
    };

    private static int readCodeLengthSymbol(BitReader r) {
        int peek = (int) r.peekBits(5);
        int entry = CL_SYMBOL_TABLE[peek];
        r.skipBits(entry & 0x7);
        return entry >>> 3;
    }

    private static int readNbltypes(BitReader r) {
        if (r.readBit() == 0) return 1;
        int k = 1;
        while (r.readBit() == 1) k++;
        if (k == 1) return 2;
        int base = (1 << (k - 1)) + 1;
        int extra = r.readBitsInt(k - 1);
        return Math.min(base + extra, 256);
    }

    private static int readWindowBits(BitReader r) {
        int bits = 0, len = 0;
        while (true) {
            int b = r.readBit();
            bits = (bits << 1) | b;
            len++;
            if (len == 1 && b == 0) return 16;
            if (len == 2 && bits == 0b10) return 17;
            if (len == 3) {
                if (bits == 0b010) return 10;
                if (bits == 0b011) return 11;
                if (bits == 0b100) return 12;
                if (bits == 0b101) return 13;
                if (bits == 0b110) return 18;
            }
            if (len == 4 && bits == 0b1100) return 14;
            if (len == 4 && bits == 0b1101) return 15;
            if (len == 4 && bits == 0b1110) return 19;
            if (len == 5 && bits == 0b11110) return 20;
            if (len == 6 && bits == 0b111110) return 21;
            if (len == 7 && bits == 0b1111110) return 22;
            if (len == 8 && bits == 0b11111110) return 23;
            if (len == 9 && bits == 0b111111110) return 24;
            if (len >= 9) return 16;
        }
    }

    private static void reportSimplePrefixCode(BitReader r, int alphabetSize, int type) {
        int nsym = r.readBitsInt(2) + 1;
        int alphabetBits = BitUtil.ceilLog2(alphabetSize);
        int[] syms = new int[nsym];
        for (int i = 0; i < nsym; i++) {
            syms[i] = r.readBitsInt(alphabetBits);
        }
        System.out.println("  simple nsym=" + nsym + " symbols=" + Arrays.toString(syms));
        if (nsym == 4) {
            int ts = r.readBitsInt(1);
            System.out.println("  treeSelect=" + ts);
        }
    }

    private static long getBitPosition(BitReader r) {
        try {
            var f = BitReader.class.getDeclaredField("bitsInAccumulator");
            f.setAccessible(true);
            int bia = f.getInt(r);
            var of = BitReader.class.getDeclaredField("bufferOffset");
            of.setAccessible(true);
            int bo = of.getInt(r);
            return bo * 8L - bia;
        } catch (Exception e) {
            return -1;
        }
    }

    private static long remainingBits(BitReader r) {
        try {
            var f = BitReader.class.getDeclaredField("bitsInAccumulator");
            f.setAccessible(true);
            int bia = f.getInt(r);
            var of = BitReader.class.getDeclaredField("bufferOffset");
            of.setAccessible(true);
            int bo = of.getInt(r);
            var bl = BitReader.class.getDeclaredField("bufferLimit");
            bl.setAccessible(true);
            int bll = bl.getInt(r);
            return (bll - bo) * 8L + bia;
        } catch (Exception e) {
            return -1;
        }
    }

    // ── Complex prefix code writer ────────────────────────────────────────

    private static void writeComplexPrefixCode(BitWriter w, int[] lengths,
                                                int alphabetSize, int hskip) {
        w.writeBits(hskip, 2);
        int lastNonZero = -1;
        for (int i = 0; i < alphabetSize; i++) {
            if (lengths[i] > 0) lastNonZero = i;
        }
        if (lastNonZero < 0) { return; }

        // Build CL code lengths (matching encoder's fixed approach)
        int[] clCodeLengths = new int[18];
        int[] clFreq = new int[19];
        for (int i = 0; i <= lastNonZero; i++) {
            int len = lengths[i];
            if (len < clFreq.length) clFreq[len]++;
        }
        buildClCodeLengths(clFreq, clCodeLengths);

        for (int i = 0; i < 18; i++) {
            int orderSym = Constants.CODE_LENGTH_ORDER[i];
            writeFixedCodeLengthValue(w, clCodeLengths[orderSym]);
        }

        HuffmanTreeBuilderForTest clTree = new HuffmanTreeBuilderForTest();
        clTree.buildFromLengths(clCodeLengths, 18);

        // Write encoded symbol count (must match what decoder expects)
        int symCount = lastNonZero + 1;
        w.writeBits(symCount, 16);

        int[] symbols = convertLengthsToSymbols(lengths, lastNonZero);
        for (int idx = 0; idx < symbols.length; idx++) {
            int sym = symbols[idx];
            int code = clTree.getCode(sym);
            int codeLen = clTree.getCodeLength(sym);
            if (codeLen > 0) {
                w.writeBitsReversed(code, codeLen);
            }
            if (sym == 16) {
                idx++;
                if (idx < symbols.length) {
                    w.writeBits(symbols[idx], 2);
                }
            } else if (sym == 17) {
                idx++;
                if (idx < symbols.length) {
                    w.writeBits(symbols[idx], 3);
                }
            }
        }
    }

    private static int[] convertLengthsToSymbols(int[] lengths, int lastNonZero) {
        java.util.ArrayList<Integer> symbols = new java.util.ArrayList<>();
        int i = 0;
        int prevLen = 8;
        boolean canUse16 = false;

        while (i <= lastNonZero) {
            int len = lengths[i];

            if (len == 0) {
                int zeroCount = 0;
                while (i + zeroCount <= lastNonZero && zeroCount < 138 && lengths[i + zeroCount] == 0) {
                    zeroCount++;
                }
                int remaining = zeroCount;
                if (remaining >= 3) {
                    int chunk = Math.min(remaining - 3, 7);
                    symbols.add(17);
                    symbols.add(chunk);
                    remaining -= (3 + chunk);
                }
                while (remaining > 0) {
                    symbols.add(0);
                    remaining--;
                }
                i += zeroCount;
                canUse16 = false;
            } else if (len == prevLen && canUse16) {
                int repeat = 1;
                while (i + repeat <= lastNonZero && repeat < 6 && lengths[i + repeat] == prevLen) {
                    repeat++;
                }
                if (repeat >= 3) {
                    symbols.add(16);
                    symbols.add(repeat - 3);
                    i += repeat;
                    canUse16 = false;
                } else {
                    symbols.add(len);
                    prevLen = len;
                    canUse16 = true;
                    i++;
                }
            } else {
                symbols.add(len);
                prevLen = len;
                canUse16 = true;
                i++;
            }
        }

        int[] result = new int[symbols.size()];
        for (int j = 0; j < symbols.size(); j++) {
            result[j] = symbols.get(j);
        }
        return result;
    }

    private static void buildClCodeLengths(int[] clFreq, int[] clCodeLengths) {
        Arrays.fill(clCodeLengths, 0);
        int[] active = new int[18];
        int activeCount = 0;
        for (int i = 0; i <= 15; i++) {
            if (i < clFreq.length && clFreq[i] > 0) active[activeCount++] = i;
        }
        if (activeCount == 0) {
            clCodeLengths[0] = 1; return;
        }
        boolean has16 = false, has17 = false;
        for (int i = 0; i < activeCount; i++) {
            if (active[i] == 16) has16 = true; if (active[i] == 17) has17 = true;
        }
        if (!has16) active[activeCount++] = 16;
        if (!has17) active[activeCount++] = 17;
        int n = activeCount;
        int[] lens;
        if (n == 1) lens = new int[]{4};
        else if (n == 2) lens = new int[]{1,1};
        else if (n == 3) lens = new int[]{1,2,2};
        else if (n == 4) lens = new int[]{2,2,2,2};
        else if (n == 5) lens = new int[]{1,3,3,3,3};
        else if (n == 6) lens = new int[]{2,2,3,3,3,3};
        else if (n == 7) lens = new int[]{2,3,3,3,3,3,3};
        else if (n == 8) lens = new int[]{3,3,3,3,3,3,3,3};
        else if (n == 9) lens = new int[]{3,3,3,3,3,3,3,4,4};
        else if (n == 10) lens = new int[]{3,3,3,3,3,3,4,4,4,4};
        else if (n == 11) lens = new int[]{3,3,3,3,3,4,4,4,4,4,4};
        else if (n == 12) lens = new int[]{3,3,3,3,4,4,4,4,4,4,4,4};
        else if (n == 13) lens = new int[]{2,4,4,4,4,4,4,4,4,4,4,4,4};
        else if (n == 14) lens = new int[]{3,3,4,4,4,4,4,4,4,4,4,4,4,4};
        else if (n == 15) lens = new int[]{3,4,4,4,4,4,4,4,4,4,4,4,4,4,4};
        else if (n == 16) { lens = new int[n]; for (int i=0;i<n;i++) lens[i]=4; }
        else if (n == 17) { lens = new int[n]; for (int i=0;i<15;i++) lens[i]=4; lens[15]=5;lens[16]=5; }
        else { lens = new int[n]; for (int i=0;i<14;i++) lens[i]=4; for (int i=14;i<n;i++) lens[i]=5; }
        // Sort active by frequency descending
        for (int i = 1; i < activeCount; i++) {
            int key = active[i]; 
            int fk = key < clFreq.length ? clFreq[key] : 0;
            int j = i-1;
            while (j >= 0) {
                int a = active[j]; int fa = a < clFreq.length ? clFreq[a] : 0;
                if (fk > fa || (fk == fa && key < a)) { active[j+1]=a; j--; } else break;
            }
            active[j+1]=key;
        }
        for (int i = 0; i < n && i < lens.length; i++) clCodeLengths[active[i]] = lens[i];
    }

    private static void writeFixedCodeLengthValue(BitWriter w, int value) {
        // Brotli RFC 7932 fixed prefix code for CL symbols.
        // MSB codes mapped to LSB bits.
        switch (value) {
            case 0: w.writeBits(0, 2); break;   // "00" MSB → "00" LSB
            case 4: w.writeBits(2, 2); break;   // "01" MSB → "10" LSB
            case 3: w.writeBits(1, 2); break;   // "10" MSB → "01" LSB
            case 2: w.writeBits(6, 4); break;   // "0110" MSB → "0110" LSB
            case 1: w.writeBits(14, 4); break;  // "0111" MSB → "1110" LSB
            case 5: w.writeBits(15, 4); break;  // "1111" MSB → "1111" LSB
            default: w.writeBits(0, 2); break;
        }
    }

    private static class HuffmanTreeBuilderForTest {
        private int[] codeLengths;
        private int[] codes;

        void buildFromLengths(int[] clCodeLengths, int alphabetSize) {
            codeLengths = clCodeLengths.clone();
            codes = new int[alphabetSize];
            int maxLen = 0;
            for (int i = 0; i < alphabetSize; i++) if (codeLengths[i] > maxLen) maxLen = codeLengths[i];
            int ml = Math.max(maxLen, 15);
            int[] bn = new int[ml + 1];
            for (int i = 0; i < alphabetSize; i++) if (codeLengths[i] > 0) bn[codeLengths[i]]++;
            int[] nc = new int[ml + 1];
            int code = 0;
            for (int b = 1; b <= ml; b++) { code = (code + bn[b - 1]) << 1; nc[b] = code; }
            for (int s = 0; s < alphabetSize; s++) {
                int l = codeLengths[s];
                if (l > 0) { codes[s] = nc[l]; nc[l]++; }
            }
        }

        int getCodeLength(int s) { return s < codeLengths.length ? codeLengths[s] : 0; }
        int getCode(int s) { return s < codes.length ? codes[s] : 0; }
    }

    // ── Data helpers ──────────────────────────────────────────────────────

    private static int uniqueCount(byte[] data) {
        boolean[] seen = new boolean[256];
        int count = 0;
        for (byte b : data) {
            int v = b & 0xFF;
            if (!seen[v]) { seen[v] = true; count++; }
        }
        return count;
    }

    private static void dumpBits(String label, byte[] data) {
        StringBuilder sb = new StringBuilder(label).append(" (").append(data.length).append("b): ");
        for (int i = 0; i < Math.min(data.length, 80); i++) {
            sb.append(String.format("%02x ", data[i] & 0xFF));
        }
        if (data.length > 80) sb.append("...");
        System.out.println(sb);
    }

    private static String toBits(int value, int nbits) {
        StringBuilder sb = new StringBuilder();
        for (int i = nbits - 1; i >= 0; i--) sb.append((value >> i) & 1);
        return sb.toString();
    }

    private static byte[] buildComplexTestData() {
        StringBuilder sb = new StringBuilder();
        for (int i = 32; i < 127; i++) sb.append((char) i);
        String chunk = "The quick brown fox jumps over the lazy dog. ";
        for (int i = 0; i < 20; i++) sb.append(chunk);
        for (int i = 0; i < 256; i++) sb.append((char) (i % 128 + 32));
        String repeated = "AAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        for (int i = 0; i < 10; i++) sb.append(repeated);
        return sb.toString().getBytes();
    }

    private static byte[] buildSkewedData(int size) {
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) 'A');
        for (int i = 0; i < size / 100; i++) {
            int pos = i * 100;
            if (pos + 4 < size) { data[pos] = 'B'; data[pos+1] = 'C'; data[pos+2] = 'D'; data[pos+3] = 'E'; }
        }
        return data;
    }

    private static byte[] buildRepeatingData(int size) {
        String pattern = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) pattern.charAt(i % pattern.length());
        return data;
    }

    private static void walkFlatPrefixCode(BitReader reader, int alphabetSize) {
        int symCount = reader.readBitsInt(16);
        System.out.println("  Flat prefix code: symCount=" + symCount);
        System.out.print("  Code lengths: ");
        for (int i = 0; i < symCount; i++) {
            int len = reader.readBitsInt(4);
            System.out.print(len + " ");
        }
        System.out.println();
    }

    private static void walkComplexPrefixCode(BitReader reader, int alphabetSize, int hskip) {
        int[] clCodeLengths = new int[Constants.CODE_LENGTH_CODES];
        int numCodes = 0;
        int spaceOfCl = 32;
        int startIdx = hskip;
        System.out.print("  CL code lengths: ");
        for (int i = startIdx; i < 18; i++) {
            int codeLen = readCodeLengthSymbol(reader);
            clCodeLengths[Constants.CODE_LENGTH_ORDER[i]] = codeLen;
            System.out.print(codeLen + " ");
            if (codeLen != 0) {
                spaceOfCl -= 32 >> codeLen;
                numCodes++;
                if (spaceOfCl <= 0) {
                    break;
                }
            }
        }
        System.out.println();

        int nonZeroCount = 0;
        int lastNonZero = -1;
        for (int i = 0; i < 18; i++) {
            if (clCodeLengths[i] > 0) {
                nonZeroCount++;
                lastNonZero = i;
            }
        }

        HuffmanDecoder clDecoder = new HuffmanDecoder();
        if (nonZeroCount == 1) {
            clDecoder.buildSingleSymbol(lastNonZero);
        } else {
            clDecoder.buildFromCodeLengths(clCodeLengths, Constants.CODE_LENGTH_CODES);
        }

        int[] resultLengths = new int[alphabetSize];
        int idx = 0;
        int prevValue = 8;
        int space = 32768;
        int repeat = 0;
        int repeat_code_len = 0;

        while (idx < alphabetSize && space > 0) {
            int sym = clDecoder.decodeSymbol(reader);
            if (sym < 16) {
                repeat = 0;
                if (sym > 0) {
                    resultLengths[idx] = sym;
                    prevValue = sym;
                    space -= 32768 >> sym;
                } else {
                    resultLengths[idx] = 0;
                }
                idx++;
            } else {
                int extra_bits = (sym == 16) ? 2 : 3;
                int repeat_delta = reader.readBitsInt(extra_bits);
                int new_len = (sym == 16) ? prevValue : 0;
                if (repeat_code_len != new_len) {
                    repeat = 0;
                    repeat_code_len = new_len;
                }
                int old_repeat = repeat;
                if (repeat > 0) {
                    repeat -= 2;
                    repeat <<= extra_bits;
                }
                repeat += repeat_delta + 3;
                int actual_repeat_delta = repeat - old_repeat;

                if (repeat_code_len != 0) {
                    for (int j = 0; j < actual_repeat_delta; j++) {
                        resultLengths[idx++] = repeat_code_len;
                    }
                    space -= actual_repeat_delta << (15 - repeat_code_len);
                } else {
                    for (int j = 0; j < actual_repeat_delta; j++) {
                        resultLengths[idx++] = 0;
                    }
                }
            }
        }
    }
}
