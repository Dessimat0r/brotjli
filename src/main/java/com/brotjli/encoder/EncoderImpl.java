package com.brotjli.encoder;

import com.brotjli.common.Constants;
import java.util.Arrays;

/**
 * Core Brotli encoder state machine.
 *
 * Transforms uncompressed input data into a Brotli-compressed bitstream.
 * Supports quality levels 0-3.
 */
public final class EncoderImpl {
    private BitWriter writer;
    private LZ77MatchFinder matchFinder;
    private MetaBlockBuilder metaBlockBuilder;

    private int quality;
    private int windowBits;
    private int windowSize;

    public enum State {
        READY,
        PROCESSING,
        DONE,
        ERROR
    }

    private State state;

    public EncoderImpl() {
        this.writer = new BitWriter();
        this.matchFinder = new LZ77MatchFinder();
        this.metaBlockBuilder = new MetaBlockBuilder();
        this.state = State.READY;
    }

    /**
     * Initialize encoder for a compression operation.
     *
     * @param quality quality level (0-3)
     */
    public void init(int quality) {
        this.quality = Math.max(Constants.MIN_QUALITY, Math.min(quality, Constants.MAX_QUALITY));
        this.windowBits = Constants.QUALITY_WBITS[this.quality];
        this.windowSize = (1 << windowBits) - 16;
        this.writer.reset();
        this.state = State.READY;
    }

    /**
     * Compress data.
     *
     * @param input uncompressed input data
     * @return compressed Brotli data
     */
    public byte[] compress(byte[] input) {
        return compress(input, 0, input.length);
    }

    /**
     * Compress a portion of data.
     */
    public byte[] compress(byte[] input, int offset, int length) {
        writer.reset();
        state = State.PROCESSING;

        // Write window bits header
        writeWindowBits();

        int pos = offset;
        int end = offset + length;

        // For quality 0, just store uncompressed
        if (quality == Constants.QUALITY_STORE) {
            if (length == 0) {
                // Empty input: single empty last meta-block
                writer.writeBit(1); // ISLAST
                writer.writeBit(1); // ISLASTEMPTY
            } else {
                // Per RFC 7932: ISUNCOMPRESSED can only be present when ISLAST=0.
                // So we write the data as ISLAST=0 uncompressed, then an empty
                // terminal meta-block (ISLAST=1, ISLASTEMPTY=1).
                writeUncompressedMetaBlock(input, offset, length, false);
                writer.writeBit(1); // ISLAST (for the empty terminal meta-block)
                writer.writeBit(1); // ISLASTEMPTY
            }
        } else {
            // Initialize match finder
            matchFinder.init(input, windowSize, quality);
            matchFinder.buildHashTable();
            metaBlockBuilder.init(quality);

            // Find matches and build meta-block
            pos = findMatchesAndBuildCommands(input, offset, length);

            // Write any remaining literals
            if (metaBlockBuilder != null) {
                metaBlockBuilder.write(writer, windowBits, true);
            }
        }

        writer.flush();
        state = State.DONE;
        return writer.toByteArray();
    }

    private void writeWindowBits() {
        // Encode window bits (WBITS) using the Brotli prefix code.
        // These patterns are the MSB-first code values as they appear
        // in the stream. Since BitWriter writes LSB-first, we reverse them.
        // For single-bit patterns we write individual bits.
        // The decoder reads bits MSB-first to reconstruct the pattern.
        switch (windowBits) {
            case 16 -> writer.writeBit(0); // "0"
            case 17 -> writer.writeBitsReversed(0b10, 2); // "10"
            case 18 -> writer.writeBitsReversed(0b110, 3); // "110"
            case 19 -> writer.writeBitsReversed(0b1110, 4); // "1110"
            case 20 -> writer.writeBitsReversed(0b11110, 5); // "11110"
            case 21 -> writer.writeBitsReversed(0b111110, 6); // "111110"
            case 22 -> writer.writeBitsReversed(0b1111110, 7); // "1111110"
            case 23 -> writer.writeBitsReversed(0b11111110, 8); // "11111110"
            case 24 -> writer.writeBitsReversed(0b111111110, 9); // "111111110"
            case 10 -> writer.writeBitsReversed(0b010, 3); // "010"
            case 11 -> writer.writeBitsReversed(0b011, 3); // "011"
            case 12 -> writer.writeBitsReversed(0b100, 3); // "100"
            case 13 -> writer.writeBitsReversed(0b101, 3); // "101"
            case 14 -> writer.writeBitsReversed(0b1100, 4); // "1100"
            case 15 -> writer.writeBitsReversed(0b1101, 4); // "1101"
            default -> writer.writeBit(0); // 16
        }
    }

    private void writeUncompressedMetaBlock(byte[] data, int offset, int length, boolean isLast) {
        writer.writeBit(isLast ? 1 : 0);
        if (isLast) {
            writer.writeBit(0); // not empty
        }

        // Compute MNIBBLES
        int mnibbles;
        if (length <= 65536) mnibbles = 4;
        else if (length <= 1048576) mnibbles = 5;
        else mnibbles = 6;

        switch (mnibbles) {
            case 4 -> writer.writeBits(0, 2);
            case 5 -> writer.writeBits(1, 2);
            case 6 -> writer.writeBits(2, 2);
        }

        writer.writeBits((length - 1), mnibbles * 4);

        if (!isLast) {
            writer.writeBit(1); // ISUNCOMPRESSED = 1
        }

        // Align to byte boundary
        writer.alignToByte();

        // Write raw data
        for (int i = offset; i < offset + length; i++) {
            writer.writeBits(data[i] & 0xFF, 8);
        }
    }

    private int findMatchesAndBuildCommands(byte[] data, int offset, int length) {
        metaBlockBuilder.init(quality);
        int pos = offset;
        int end = offset + length;

        while (pos < end) {
            LZ77MatchFinder.Match match = matchFinder.findMatch(pos);

            if (match.length() >= 3 && match.distance() > 0) {
                // Emit literals collected before this match
                // (stored in metaBlockBuilder)
                metaBlockBuilder.addCopy(match.length(), match.distance());
                pos += match.length();
            } else {
                int prevByte = (pos > offset) ? (data[pos - 1] & 0xFF) : 0;
                metaBlockBuilder.addLiteral(data[pos], prevByte & 0x3F);
                matchFinder.recordLiteral(data[pos]);
                pos++;
            }

            // Flush meta-block if it gets large
            if (metaBlockBuilder.getTotalOutputSize() > 65536 && pos < end) {
                // Write partial meta-block (not last)
                metaBlockBuilder.write(writer, windowBits, false);
                // Can't easily resume after partial write with this simple implementation
                // For now, continue with same builder
            }
        }

        return pos;
    }

    public State getState() { return state; }
    public byte[] getOutput() { return writer.toByteArray(); }

    public void reset() {
        writer.reset();
        matchFinder.reset();
        metaBlockBuilder.reset();
        state = State.READY;
    }
}
