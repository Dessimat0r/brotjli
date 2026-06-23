package com.brotjli.decoder;

import com.brotjli.common.BrotliDictionary;
import com.brotjli.common.Constants;
import com.brotjli.common.WordTransform;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Core Brotli decoder state machine.
 *
 * Implements RFC 7932: Brotli Compressed Data Format.
 * Processes the bitstream format and produces decompressed output.
 *
 * The decoder works in a streaming fashion: feed it compressed data
 * via feed(), and it will produce output via the output buffer callback.
 */
public final class DecoderImpl {

    // Decoder state
    private State state;
    private BitReader reader;

    // Meta-block state
    private int windowBits;
    private int windowSize;
    private int metaBlockRemaining;
    private boolean isLast;
    private boolean isUncompressed;

    // Ring buffer for sliding window
    private byte[] ringBuffer;
    private int ringBufferMask;
    private int ringBufferPosition;
    private int bytesProduced;

    // Recent distances ring
    private int[] recentDistances;

    // Block decoders
    private BlockDecoder literalBlockDecoder;
    private BlockDecoder insertCopyBlockDecoder;
    private BlockDecoder distanceBlockDecoder;

    // Context
    private ContextDecoder contextDecoder;
    private int[] contextMapLiteral;
    private int[] contextMapDistance;
    private int numLiteralBlockTypes;
    private int numInsertCopyBlockTypes;
    private int numDistanceBlockTypes;
    private int numLiteralTrees;
    private int numDistanceTrees;

    // Huffman trees
    private HuffmanDecoder[] literalTrees;
    private HuffmanDecoder[] insertCopyTrees;
    private HuffmanDecoder[] distanceTrees;

    // NPOSTFIX and NDIRECT for distance encoding
    private int npostfix;
    private int ndirect;
    private int postfixMask;
    private int distanceAlphabetSize;

    // Command decoding state
    private int commandInsertLength;
    private int commandCopyLength;
    private int commandDistanceCode;
    private boolean commandImplicitDistanceZero;

    // Output buffer
    private byte[] outputBuffer;
    private int outputOffset;
    private static final int OUTPUT_BUFFER_SIZE = 65536;

    public enum State {
        READ_WBITS,
        READ_METABLOCK,
        READ_UNCOMPRESSED,
        DECOMPRESS_COMMANDS,
        DONE,
        ERROR
    }

    public DecoderImpl() {
        this.reader = new BitReader();
        this.recentDistances = new int[]{16, 15, 11, 4};
        this.literalBlockDecoder = new BlockDecoder();
        this.insertCopyBlockDecoder = new BlockDecoder();
        this.distanceBlockDecoder = new BlockDecoder();
        this.contextDecoder = new ContextDecoder();
        this.outputBuffer = new byte[OUTPUT_BUFFER_SIZE];
        this.state = State.READ_WBITS;
    }

    /**
     * Reset decoder state for reuse.
     */
    public void reset() {
        this.state = State.READ_WBITS;
        this.reader.reset(new byte[0]);
        this.ringBuffer = null;
        this.ringBufferPosition = 0;
        this.bytesProduced = 0;
        this.recentDistances[0] = 16;
        this.recentDistances[1] = 15;
        this.recentDistances[2] = 11;
        this.recentDistances[3] = 4;
        this.metaBlockRemaining = 0;
        this.outputOffset = 0;
        this.literalBlockDecoder.reset();
        this.insertCopyBlockDecoder.reset();
        this.distanceBlockDecoder.reset();
    }

    /**
     * Feed compressed data to the decoder.
     *
     * @param data compressed Brotli data
     * @return true if more data can be accepted, false if decoding is complete
     */
    public boolean feed(byte[] data) {
        reader.reset(data);
        return process();
    }

    /**
     * Feed compressed data from a ByteBuffer.
     */
    public boolean feed(ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        reader.reset(bytes);
        return process();
    }

    private boolean process() {
        while (state != State.DONE && state != State.ERROR && !reader.isFinished()) {
            switch (state) {
                case READ_WBITS -> readWindowBits();
                case READ_METABLOCK -> readMetaBlock();
                case READ_UNCOMPRESSED -> decompressUncompressed();
                case DECOMPRESS_COMMANDS -> decompressCommands();
                default -> throw new IllegalStateException("Unexpected state: " + state);
            }
        }
        return state != State.DONE;
    }

    private void readWindowBits() {
        try {
            // Read WBITS using the standard Brotli prefix code decision tree (RFC 7932 Section 9.1).
            int b0 = reader.readBit();
            if (b0 == 0) {
                windowBits = 16;
            } else {
                int b1 = reader.readBit();
                if (b1 == 1) {
                    int b2 = reader.readBit();
                    int b3 = reader.readBit();
                    if (b2 == 0) {
                        windowBits = (b3 == 0) ? 18 : 22;
                    } else {
                        windowBits = (b3 == 0) ? 20 : 24;
                    }
                } else {
                    int b2 = reader.readBit();
                    int b3 = reader.readBit();
                    if (b2 == 1) {
                        windowBits = (b3 == 0) ? 19 : 23;
                    } else if (b3 == 1) {
                        windowBits = 21;
                    } else {
                        // Read 3 more bits: b4, b5, b6
                        int b4 = reader.readBit();
                        int b5 = reader.readBit();
                        int b6 = reader.readBit();
                        int val = (b6 << 2) | (b5 << 1) | b4;
                        windowBits = switch (val) {
                            case 0 -> 17;
                            case 2 -> 10;
                            case 3 -> 11;
                            case 4 -> 12;
                            case 5 -> 13;
                            case 6 -> 14;
                            case 7 -> 15;
                            default -> throw new IllegalStateException("Invalid WBITS code: " + val);
                        };
                    }
                }
            }
            windowSize = (1 << windowBits) - 16;
            ringBuffer = new byte[windowSize];
            ringBufferMask = windowSize - 1;
            state = State.READ_METABLOCK;
        } catch (Exception e) {
            state = State.ERROR;
        }
    }

    private void readMetaBlock() {
        try {
            isLast = reader.readBit() == 1;

            if (isLast) {
                int isEmpty = reader.readBit();
                if (isEmpty == 1) {
                    // Empty last meta-block: stream ends
                    state = State.DONE;
                    return;
                }
            }

            // Read MNIBBLES
            int nibblesBits = reader.readBitsInt(2);

            if (nibblesBits == 3) {
                // MNIBBLES = 0 -> empty/meta-data meta-block
                reader.readBit();
                int mskipBytes = reader.readBitsInt(2);
                if (mskipBytes > 0) {
                    long mskipLen = reader.readBits(mskipBytes * 8) + 1;
                    reader.alignToByte();
                    // Skip metadata bytes
                    for (long i = 0; i < mskipLen; i++) {
                        reader.readBits(8);
                    }
                } else {
                    reader.alignToByte();
                }
                state = State.READ_METABLOCK;
                return;
            }

            int mnibbles;
            if (nibblesBits == 0) mnibbles = 4;
            else if (nibblesBits == 1) mnibbles = 5;
            else mnibbles = 6;

            int mlen = (int)reader.readBits(mnibbles * 4) + 1;
            metaBlockRemaining = mlen;

            if (!isLast) {
                isUncompressed = reader.readBit() == 1;
            } else {
                isUncompressed = false;
            }

            if (isUncompressed) {
                reader.alignToByte();
                state = State.READ_UNCOMPRESSED;
            } else {
                state = State.DECOMPRESS_COMMANDS;
                initCompressedMetaBlock();
            }
        } catch (Exception e) {
            state = State.ERROR;
        }
    }

    private void initCompressedMetaBlock() {
        // Read NBLTYPES for literal, insert-copy, distance
        numLiteralBlockTypes = readNbltypes();
        numInsertCopyBlockTypes = readNbltypes();
        numDistanceBlockTypes = readNbltypes();

        // Initialize block decoders
        if (numLiteralBlockTypes >= 2) {
            initBlockDecoder(literalBlockDecoder, numLiteralBlockTypes);
        } else {
            literalBlockDecoder.initInfinite();
        }

        if (numInsertCopyBlockTypes >= 2) {
            initBlockDecoder(insertCopyBlockDecoder, numInsertCopyBlockTypes);
        } else {
            insertCopyBlockDecoder.initInfinite();
        }

        if (numDistanceBlockTypes >= 2) {
            initBlockDecoder(distanceBlockDecoder, numDistanceBlockTypes);
        } else {
            distanceBlockDecoder.initInfinite();
        }

        // Read NPOSTFIX and NDIRECT
        npostfix = reader.readBitsInt(2);
        int ndirectBits = reader.readBitsInt(4);
        ndirect = ndirectBits << npostfix;
        postfixMask = (1 << npostfix) - 1;
        distanceAlphabetSize = 16 + ndirect + (48 << npostfix);

        // Read context modes
        for (int i = 0; i < numLiteralBlockTypes; i++) {
            int mode = reader.readBitsInt(2);
            contextDecoder.setContextMode(i, mode);
        }

        // Read number of literal prefix trees
        numLiteralTrees = readNbltypes();
        if (numLiteralTrees >= 2) {
            contextMapLiteral = readContextMap(64 * numLiteralBlockTypes, numLiteralTrees);
        } else {
            contextMapLiteral = new int[64 * numLiteralBlockTypes];
        }

        // Read number of distance prefix trees
        numDistanceTrees = readNbltypes();
        if (numDistanceTrees >= 2) {
            contextMapDistance = readContextMap(4 * numDistanceBlockTypes, numDistanceTrees);
        } else {
            contextMapDistance = new int[4 * numDistanceBlockTypes];
        }

        // Read all Huffman trees
        literalTrees = new HuffmanDecoder[numLiteralTrees];
        for (int i = 0; i < numLiteralTrees; i++) {
            literalTrees[i] = readPrefixCode(256);
        }

        insertCopyTrees = new HuffmanDecoder[numInsertCopyBlockTypes];
        for (int i = 0; i < numInsertCopyBlockTypes; i++) {
            insertCopyTrees[i] = readPrefixCode(704);
        }

        distanceTrees = new HuffmanDecoder[numDistanceTrees];
        for (int i = 0; i < numDistanceTrees; i++) {
            distanceTrees[i] = readPrefixCode(distanceAlphabetSize);
        }
    }

    private void decompressUncompressed() {
        int toRead = Math.min(metaBlockRemaining, 65536);
        for (int i = 0; i < toRead; i++) {
            if (reader.isFinished()) break;
            int b = reader.readBitsInt(8);
            writeOutput((byte)b);
            metaBlockRemaining--;
        }
        if (metaBlockRemaining <= 0) {
            state = isLast ? State.DONE : State.READ_METABLOCK;
        }
    }

    private void decompressCommands() {
        while (metaBlockRemaining > 0 && !reader.isFinished() && outputOffset < outputBuffer.length) {
            // Read insert-and-copy length symbol
            int insertCopyType = insertCopyBlockDecoder.currentType();
            if (insertCopyBlockDecoder.needsSwitch()) {
                insertCopyBlockDecoder.readBlockSwitch(reader);
                insertCopyType = insertCopyBlockDecoder.currentType();
            }
            insertCopyBlockDecoder.consume();

            int icSymbol = insertCopyTrees[insertCopyType].decodeSymbol(reader);
            decodeInsertCopyLength(icSymbol);

            // Read insert literals
            for (int i = 0; i < commandInsertLength && metaBlockRemaining > 0; i++) {
                int literalType = literalBlockDecoder.currentType();
                if (literalBlockDecoder.needsSwitch()) {
                    literalBlockDecoder.readBlockSwitch(reader);
                    literalType = literalBlockDecoder.currentType();
                }
                literalBlockDecoder.consume();

                int prevByte = ringBufferPosition > 0 ? ringBuffer[(ringBufferPosition - 1) & ringBufferMask] : 0;
                int prevPrevByte = ringBufferPosition > 1 ? ringBuffer[(ringBufferPosition - 2) & ringBufferMask] : 0;
                int contextId = contextDecoder.computeContextId(prevByte, prevPrevByte);

                int contextMapIndex = literalType * 64 + contextId;
                int treeIndex = contextMapLiteral != null && contextMapIndex < contextMapLiteral.length
                    ? contextMapLiteral[contextMapIndex] : 0;

                int literal = literalTrees[Math.min(treeIndex, literalTrees.length - 1)].decodeSymbol(reader);
                writeOutput((byte)literal);
                metaBlockRemaining--;
            }

            if (metaBlockRemaining <= 0) break;

            // Handle copy command
            if (!commandImplicitDistanceZero) {
                int distanceType = distanceBlockDecoder.currentType();
                if (distanceBlockDecoder.needsSwitch()) {
                    distanceBlockDecoder.readBlockSwitch(reader);
                    distanceType = distanceBlockDecoder.currentType();
                }
                distanceBlockDecoder.consume();

                int distContext = 3;
                if (commandCopyLength == 2) distContext = 0;
                else if (commandCopyLength == 3) distContext = 1;
                else if (commandCopyLength == 4) distContext = 2;
                int contextMapIndex = distanceType * 4 + distContext;
                int treeIndex = contextMapDistance != null && contextMapIndex < contextMapDistance.length
                    ? contextMapDistance[contextMapIndex] : 0;
                int distanceSymbol = distanceTrees[Math.min(treeIndex, distanceTrees.length - 1)].decodeSymbol(reader);
                commandDistanceCode = distanceSymbol;
            }

            int distance = resolveDistance();

            int copyLen = Math.min(commandCopyLength, metaBlockRemaining);
            if (distance > 0 && distance <= ringBufferPosition + copyLen) {
                for (int i = 0; i < copyLen; i++) {
                    int srcPos = (ringBufferPosition - distance) & ringBufferMask;
                    byte b = ringBuffer[srcPos];
                    writeOutput(b);
                }
            } else if (distance > ringBufferPosition) {
                copyFromDictionary(commandCopyLength, distance);
            }
            metaBlockRemaining -= copyLen;
        }
        if (metaBlockRemaining <= 0) {
            state = isLast ? State.DONE : State.READ_METABLOCK;
        }
    }

    private void decodeInsertCopyLength(int symbol) {
        commandImplicitDistanceZero = (symbol >= 64 && symbol < 128);
        int block = symbol / 64;
        int offset = symbol % 64;
        int inBlock = offset >>> 3;
        int cpBlock = offset & 0x7;
        int insertBase, copyBase;
        switch (block) {
            case 0: case 2:          insertBase = 0;  copyBase = 0;  break;
            case 1: case 3:          insertBase = 0;  copyBase = 8;  break;
            case 4:                  insertBase = 8;  copyBase = 0;  break;
            case 5:                  insertBase = 8;  copyBase = 8;  break;
            case 6:                  insertBase = 0;  copyBase = 16; break;
            case 7:                  insertBase = 16; copyBase = 0;  break;
            case 8:                  insertBase = 8;  copyBase = 16; break;
            case 9:                  insertBase = 16; copyBase = 8;  break;
            case 10:                 insertBase = 16; copyBase = 16; break;
            default:         insertBase = 0;  copyBase = 0;  break;
        }
        int insertCode = Math.min(inBlock + insertBase, Constants.INSERT_LENGTH_BASE.length - 1);
        int copyCode = Math.min(cpBlock + copyBase, Constants.COPY_LENGTH_BASE.length - 1);
        commandInsertLength = Constants.INSERT_LENGTH_BASE[insertCode]
            + (int)reader.readBits(Constants.INSERT_LENGTH_EXTRA_BITS[insertCode]);
        commandCopyLength = Constants.COPY_LENGTH_BASE[copyCode]
            + (int)reader.readBits(Constants.COPY_LENGTH_EXTRA_BITS[copyCode]);
    }

    private int resolveDistance() {
        if (commandDistanceCode < 16) {
            return resolveSpecialDistance(commandDistanceCode);
        }
        if (commandDistanceCode < 16 + ndirect) {
            int distance = commandDistanceCode - 15;
            updateRecentDistances(distance);
            return distance;
        }
        int dcode = commandDistanceCode - ndirect - 16;
        int hcode = dcode >> npostfix;
        int lcode = dcode & postfixMask;
        int ndistBits = 1 + ((hcode >> 1));
        int dextra = (int)reader.readBits(ndistBits);
        int offset = ((2 + (hcode & 1)) << ndistBits) - 4;
        int distance = ((offset + dextra) << npostfix) + lcode + ndirect + 1;
        updateRecentDistances(distance);
        return distance;
    }

    private int resolveSpecialDistance(int code) {
        int distance;
        if (code == 0) {
            distance = recentDistances[0];
        } else if (code == 1) {
            distance = recentDistances[1];
            recentDistances[1] = recentDistances[0];
            recentDistances[0] = distance;
        } else if (code == 2) {
            distance = recentDistances[2];
            recentDistances[2] = recentDistances[1];
            recentDistances[1] = recentDistances[0];
            recentDistances[0] = distance;
        } else if (code == 3) {
            distance = recentDistances[3];
            recentDistances[3] = recentDistances[2];
            recentDistances[2] = recentDistances[1];
            recentDistances[1] = recentDistances[0];
            recentDistances[0] = distance;
        } else if (code >= 4 && code <= 9) {
            int offset = (code & 1) == 0 ? -(1 + ((code - 4) >> 1)) : (1 + ((code - 4) >> 1));
            distance = recentDistances[0] + offset;
            if (distance <= 0) distance = 1;
            recentDistances[3] = recentDistances[2];
            recentDistances[2] = recentDistances[1];
            recentDistances[1] = recentDistances[0];
            recentDistances[0] = distance;
        } else { // 10..15
            int offset = (code & 1) == 0 ? -(1 + ((code - 10) >> 1)) : (1 + ((code - 10) >> 1));
            distance = recentDistances[1] + offset;
            if (distance <= 0) distance = 1;
            recentDistances[3] = recentDistances[2];
            recentDistances[2] = recentDistances[1];
            recentDistances[1] = recentDistances[0];
            recentDistances[0] = distance;
        }
        return distance;
    }

    private void updateRecentDistances(int distance) {
        recentDistances[3] = recentDistances[2];
        recentDistances[2] = recentDistances[1];
        recentDistances[1] = recentDistances[0];
        recentDistances[0] = distance;
    }

    private void copyFromDictionary(int copyLength, int distance) {
        int maxAllowed = Math.min(windowSize, bytesProduced);
        if (distance <= maxAllowed) return;
        int wordId = distance - (maxAllowed + 1);
        int length = Math.max(Constants.MIN_DICT_WORD_LENGTH,
            Math.min(copyLength, Constants.MAX_DICT_WORD_LENGTH));
        int nwords = 1 << Constants.DICTIONARY_SIZE_BITS[length];
        int index = wordId % nwords;
        int transformId = wordId >> Constants.DICTIONARY_SIZE_BITS[length];
        byte[] word = BrotliDictionary.getWord(length, index);
        byte[] transformed = WordTransform.apply(word, transformId % WordTransform.count());
        for (byte b : transformed) {
            writeOutput(b);
        }
    }

    private void writeOutput(byte b) {
        if (outputOffset >= outputBuffer.length) {
            return;
        }
        outputBuffer[outputOffset++] = b;
        ringBuffer[ringBufferPosition & ringBufferMask] = b;
        ringBufferPosition++;
        bytesProduced++;
    }

    /**
     * Get decoded output data.
     */
    public byte[] getOutput() {
        return Arrays.copyOf(outputBuffer, outputOffset);
    }

    /**
     * Get output length in bytes.
     */
    public int getOutputLength() {
        return outputOffset;
    }

    /**
     * Copy output into destination array.
     */
    public int getOutput(byte[] dest, int offset, int length) {
        int toCopy = Math.min(outputOffset, length);
        System.arraycopy(outputBuffer, 0, dest, offset, toCopy);
        return toCopy;
    }

    public State getState() { return state; }

    public boolean isDone() { return state == State.DONE; }
    public boolean isError() { return state == State.ERROR; }

    // ========== Helper methods for bitstream parsing ==========

    private int readNbltypes() {
        if (reader.readBit() == 0) return 1;
        if (reader.readBit() == 0) return 2;
        int count = 0;
        while (reader.readBit() == 1) {
            count++;
        }
        return 3 + count;
    }

    private int[] readContextMap(int mapSize, int numTrees) {
        // Read RLEMAX
        int rlemax;
        int rleBit = reader.readBit();
        if (rleBit == 0) {
            rlemax = 0;
        } else {
            int bits = 0;
            while (true) {
                int b = reader.readBit();
                bits = (bits << 1) | b;
                if (b == 1) {
                    rlemax = bits >> 1;
                    break;
                }
            }
        }

        int contextMapAlphabetSize = numTrees + rlemax;
        HuffmanDecoder cmDecoder = readPrefixCode(contextMapAlphabetSize);

        int[] map = new int[mapSize];
        int idx = 0;
        while (idx < mapSize) {
            int sym = cmDecoder.decodeSymbol(reader);
            if (sym < rlemax + 1) {
                // Repeat zero
                int repeat;
                if (rlemax == 0) {
                    repeat = 1;
                } else {
                    repeat = 1 << sym;
                    if (sym < 3) {
                        repeat += reader.readBitsInt(1);
                    } else {
                        repeat += reader.readBitsInt(sym);
                    }
                }
                for (int i = 0; i < repeat && idx < mapSize; i++) {
                    map[idx++] = 0;
                }
            } else {
                map[idx++] = sym - rlemax - 1;
            }
        }

        // Inverse move-to-front
        int imtf = reader.readBitsInt(1);
        if (imtf == 1) {
            inverseMoveToFront(map);
        }

        return map;
    }

    private void inverseMoveToFront(int[] v) {
        byte[] mtf = new byte[256];
        for (int i = 0; i < 256; i++) mtf[i] = (byte)i;
        for (int i = 0; i < v.length; i++) {
            int index = v[i] & 0xFF;
            byte value = mtf[index];
            v[i] = value & 0xFF;
            System.arraycopy(mtf, 0, mtf, 1, index);
            mtf[0] = value;
        }
    }

    private void initBlockDecoder(BlockDecoder bd, int nbltypes) {
        HuffmanDecoder typeTree = readPrefixCode(nbltypes + 2);
        HuffmanDecoder countTree = readPrefixCode(26);
        int firstCount = decodeBlockCount();
        bd.init(nbltypes, typeTree, countTree, firstCount);
    }

    private int decodeBlockCount() {
        int symbol = (int)reader.readBits(5); // 5 bits for 26-symbol alphabet
        int extra = Constants.BLOCK_COUNT_EXTRA_BITS[symbol];
        int base = Constants.BLOCK_COUNT_BASE[symbol];
        return base + (int)reader.readBits(extra);
    }

    private HuffmanDecoder readPrefixCode(int alphabetSize) {
        // Read HSKIP (first 2 bits of prefix code, RFC 7932 Section 3.4/3.5).
        int hskip = reader.readBitsInt(2);
        if (hskip == 1) {
            return HuffmanDecoder.readSimplePrefixCode(reader, alphabetSize);
        } else {
            return HuffmanDecoder.readComplexPrefixCode(reader, alphabetSize, hskip);
        }
    }
}
