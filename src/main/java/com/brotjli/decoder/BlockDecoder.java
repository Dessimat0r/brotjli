package com.brotjli.decoder;

import com.brotjli.common.Constants;

/**
 * Decodes block type/count switching for one of the three block streams
 * (literals, insert-and-copy lengths, or distances).
 *
 * Each meta-block can have up to 256 block types per stream. The block
 * type changes at defined intervals, controlled by block count values.
 */
public final class BlockDecoder {
    private int numBlockTypes;
    private int blockType;
    private int blockLength;
    private int blocksRemaining;

    private HuffmanDecoder blockTypeDecoder;
    private HuffmanDecoder blockCountDecoder;

    public BlockDecoder() {
        this.numBlockTypes = 1;
        this.blockType = 0;
        this.blockLength = 1 << 24;
        this.blocksRemaining = 0;
    }

    /**
     * Initialize block switching for a stream.
     *
     * @param nbltypes  number of block types (1..256)
     * @param typeTree  Huffman decoder for block type alphabet
     * @param countTree Huffman decoder for block count alphabet
     * @param initialBlockCount the first block's count
     */
    public void init(int nbltypes, HuffmanDecoder typeTree, HuffmanDecoder countTree, int initialBlockCount) {
        this.numBlockTypes = nbltypes;
        this.blockTypeDecoder = typeTree;
        this.blockCountDecoder = countTree;
        this.blockType = 0;
        this.blockLength = initialBlockCount;
        this.blocksRemaining = initialBlockCount;
    }

    /**
     * Initialize for a single block type (no switching).
     */
    public void initSingle(int nbltypes, int blockLength) {
        this.numBlockTypes = nbltypes;
        this.blockTypeDecoder = null;
        this.blockCountDecoder = null;
        this.blockType = 0;
        this.blockLength = blockLength;
        this.blocksRemaining = blockLength;
    }

    /** Set for single-block-type infinite length. */
    public void initInfinite() {
        this.numBlockTypes = 1;
        this.blockTypeDecoder = null;
        this.blockCountDecoder = null;
        this.blockType = 0;
        this.blockLength = 16777216;
        this.blocksRemaining = 16777216;
    }

    /**
     * Get current block type.
     */
    public int currentType() {
        return blockType;
    }

    /**
     * Check if a block switch is needed.
     */
    public boolean needsSwitch() {
        return blocksRemaining <= 0;
    }

    /**
     * Read a block switch command from the bit reader.
     * Updates block type and block count.
     */
    public void readBlockSwitch(BitReader reader) {
        if (numBlockTypes == 1) {
            blocksRemaining = blockLength;
            return;
        }

        int typeSymbol = blockTypeDecoder.decodeSymbol(reader);

        int newType;
        if (typeSymbol == 0) {
            newType = previousBlockType;
        } else if (typeSymbol == 1) {
            newType = (currentBlockType + 1) % numBlockTypes;
        } else {
            newType = typeSymbol - 2;
        }

        previousBlockType = currentBlockType;
        currentBlockType = newType;
        blockType = newType;

        int countSymbol = blockCountDecoder.decodeSymbol(reader);
        int extraBits = Constants.BLOCK_COUNT_EXTRA_BITS[countSymbol];
        int base = Constants.BLOCK_COUNT_BASE[countSymbol];
        int count = base + (int)reader.readBits(extraBits);

        blocksRemaining = count;
    }

    /**
     * Consume one element from the current block.
     */
    public void consume() {
        blocksRemaining--;
    }

    private int previousBlockType = 1;
    private int currentBlockType = 0;

    /** Reset block state. */
    public void reset() {
        previousBlockType = 1;
        currentBlockType = 0;
        blockType = 0;
        blocksRemaining = 0;
    }
}
