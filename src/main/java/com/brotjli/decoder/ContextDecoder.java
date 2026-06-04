package com.brotjli.decoder;

import com.brotjli.common.Constants;

/**
 * Context mode computation for Brotli literal decoding.
 *
 * Given a context mode (LSB6, MSB6, UTF8, or SIGNED) and the previous
 * two bytes, computes a context ID (0..63) that selects which Huffman
 * tree to use for the current literal.
 */
public final class ContextDecoder {
    private int contextMode;
    private int literalBlockType;

    public ContextDecoder() {
        this.contextMode = Constants.CONTEXT_LSB6;
    }

    /**
     * Set context mode for a literal block type.
     */
    public void setContextMode(int blockType, int mode) {
        this.contextMode = mode;
        this.literalBlockType = blockType;
    }

    /**
     * Compute context ID from previous bytes.
     *
     * @param prevByte     the byte immediately before current position
     * @param prevPrevByte the byte two positions before current
     * @return context ID (0..63)
     */
    public int computeContextId(int prevByte, int prevPrevByte) {
        return switch (contextMode) {
            case Constants.CONTEXT_LSB6 -> prevByte & 0x3F;
            case Constants.CONTEXT_MSB6 -> (prevByte >>> 2) & 0x3F;
            case Constants.CONTEXT_UTF8 -> computeUtf8Context(prevByte, prevPrevByte);
            case Constants.CONTEXT_SIGNED -> computeSignedContext(prevByte, prevPrevByte);
            default -> 0;
        };
    }

    private int computeUtf8Context(int p1, int p2) {
        if (p1 < 0) p1 += 256;
        if (p2 < 0) p2 += 256;
        return Lut0[p1] | Lut1[p2];
    }

    private int computeSignedContext(int p1, int p2) {
        if (p1 < 0) p1 += 256;
        if (p2 < 0) p2 += 256;
        return (Lut2[p1] << 3) | Lut2[p2];
    }

    public int getContextMode() { return contextMode; }

    private static final int[] Lut0 = buildLut0();
    private static final int[] Lut1 = buildLut1();
    private static final int[] Lut2 = buildLut2();

    private static int[] buildLut0() {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            lut[i] = i >= 192 ? 0 : (i >= 160 ? 1 : (i >= 128 ? 2 : (i >= 96 ? 3 : (i >= 64 ? 4 : 5))));
        }
        for (int i = 0; i < 256; i++) {
            if (i < 192) continue;
            if (i < 224) { lut[i] = 0; continue; }
            if (i < 240) { lut[i] = 1; continue; }
            lut[i] = 2;
        }
        return lut;
    }

    private static int[] buildLut1() {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            lut[i] = i < 192 ? 0 : (i < 224 ? 1 : (i < 240 ? 2 : 3));
        }
        return lut;
    }

    private static int[] buildLut2() {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            if (i <= 1) lut[i] = 0;
            else if (i <= 3) lut[i] = 1;
            else if (i <= 7) lut[i] = 2;
            else if (i <= 15) lut[i] = 3;
            else if (i <= 31) lut[i] = 4;
            else if (i <= 63) lut[i] = 5;
            else if (i <= 127) lut[i] = 6;
            else lut[i] = 7;
        }
        return lut;
    }
}
