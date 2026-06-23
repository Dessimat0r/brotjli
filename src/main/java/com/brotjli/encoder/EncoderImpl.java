package com.brotjli.encoder;

import com.brotjli.common.Constants;

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
            findMatchesAndBuildCommands(input, offset, length);

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
        // Encode window bits (WBITS) using the Brotli prefix code (RFC 7932 Section 9.1).
        // Since BitWriter writes LSB-first, we map the printed Bit Pattern in the RFC
        // (which is parsed right-to-left) to its direct binary integer value.
        switch (windowBits) {
            case 10 -> writer.writeBits(33, 7);      // "0100001" -> LSB: 1, 0, 0, 0, 0, 1, 0
            case 11 -> writer.writeBits(49, 7);      // "0110001" -> LSB: 1, 0, 0, 0, 1, 1, 0
            case 12 -> writer.writeBits(65, 7);      // "1000001" -> LSB: 1, 0, 0, 0, 0, 0, 1
            case 13 -> writer.writeBits(81, 7);      // "1010001" -> LSB: 1, 0, 0, 0, 1, 0, 1
            case 14 -> writer.writeBits(97, 7);      // "1100001" -> LSB: 1, 0, 0, 0, 0, 1, 1
            case 15 -> writer.writeBits(113, 7);     // "1110001" -> LSB: 1, 0, 0, 0, 1, 1, 1
            case 16 -> writer.writeBit(0);           // "0"       -> LSB: 0
            case 17 -> writer.writeBits(1, 7);       // "0000001" -> LSB: 1, 0, 0, 0, 0, 0, 0
            case 18 -> writer.writeBits(3, 4);       // "0011"    -> LSB: 1, 1, 0, 0
            case 19 -> writer.writeBits(5, 4);       // "0101"    -> LSB: 1, 0, 1, 0
            case 20 -> writer.writeBits(7, 4);       // "0111"    -> LSB: 1, 1, 1, 0
            case 21 -> writer.writeBits(9, 4);       // "1001"    -> LSB: 1, 0, 0, 1
            case 22 -> writer.writeBits(11, 4);      // "1011"    -> LSB: 1, 1, 0, 1
            case 23 -> writer.writeBits(13, 4);      // "1101"    -> LSB: 1, 0, 1, 1
            case 24 -> writer.writeBits(15, 4);      // "1111"    -> LSB: 1, 1, 1, 1
            default -> writer.writeBit(0);           // 16
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
