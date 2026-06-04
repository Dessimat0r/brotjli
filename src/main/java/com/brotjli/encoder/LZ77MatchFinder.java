package com.brotjli.encoder;

import java.util.Arrays;

/**
 * LZ77 match finder using hash chains.
 *
 * Finds the best copy match for each position in the input data.
 * Supports configurable window size, minimum match length, and search depth.
 */
public final class LZ77MatchFinder {
    private static final int HASH_BITS = 15;
    private static final int HASH_SIZE = 1 << HASH_BITS;
    private static final int HASH_MASK = HASH_SIZE - 1;
    private static final int MIN_MATCH = 3;
    private static final int MAX_MATCH = 258;

    private int[] hashTable;
    private int[] prevMatches;  // chain of previous matches for each position
    private byte[] data;
    private int windowSize;
    private int maxChainLength;
    private int[] litFrequencies;

    /**
     * Represents a match found by the match finder.
     */
    public record Match(int position, int length, int distance) {
        public boolean isValid() { return length >= MIN_MATCH; }
    }

    public LZ77MatchFinder() {
        this.hashTable = new int[HASH_SIZE];
        this.prevMatches = new int[65536];
        this.litFrequencies = new int[256];
        Arrays.fill(hashTable, -1);
    }

    /**
     * Initialize match finder for new data.
     *
     * @param data input data to search
     * @param windowSize sliding window size
     * @param quality quality level (0-3)
     */
    public void init(byte[] data, int windowSize, int quality) {
        this.data = data;
        this.windowSize = windowSize;
        this.maxChainLength = switch (quality) {
            case 0 -> 0;
            case 1 -> 8;
            case 2 -> 32;
            case 3 -> 128;
            default -> 16;
        };
        Arrays.fill(hashTable, -1);
        Arrays.fill(litFrequencies, 0);
    }

    /**
     * Build hash table for the input data.
     * Call before findMatch.
     */
    public void buildHashTable() {
        for (int i = 0; i < data.length - 2; i++) {
            int hash = hash(data, i);
            if (i < prevMatches.length) {
                prevMatches[i] = hashTable[hash];
            }
            hashTable[hash] = i;
        }
    }

    /**
     * Find the best match at the given position.
     */
    public Match findMatch(int position) {
        if (position + MIN_MATCH > data.length) {
            return new Match(position, 1, 0);
        }

        int hash = hash(data, position);
        int bestLength = 1;
        int bestDistance = 0;
        int chainLen = 0;
        int matchIndex = hashTable[hash];

        while (matchIndex >= 0 && matchIndex < position
               && position - matchIndex <= windowSize
               && chainLen < maxChainLength) {
            chainLen++;

            int matchLen = compareMatches(position, matchIndex);
            if (matchLen > bestLength) {
                bestLength = matchLen;
                bestDistance = position - matchIndex;
                if (bestLength >= MAX_MATCH) break;
            }

            if (matchIndex < prevMatches.length) {
                matchIndex = prevMatches[matchIndex];
            } else {
                break;
            }
        }

        return new Match(position, bestLength, bestDistance);
    }

    /**
     * Record a literal (non-matched byte) for frequency tracking.
     */
    public void recordLiteral(byte b) {
        litFrequencies[b & 0xFF]++;
    }

    public int[] getLitFrequencies() {
        return litFrequencies;
    }

    private int compareMatches(int pos1, int pos2) {
        int maxLen = Math.min(data.length - pos1, MAX_MATCH);
        int len = 0;
        while (len < maxLen && data[pos1 + len] == data[pos2 + len]) {
            len++;
        }
        return len;
    }

    private static int hash(byte[] data, int offset) {
        int h = ((data[offset] & 0xFF) << 10)
              ^ ((data[offset + 1] & 0xFF) << 5)
              ^ (data[offset + 2] & 0xFF);
        return h & HASH_MASK;
    }

    public void reset() {
        Arrays.fill(hashTable, -1);
        Arrays.fill(litFrequencies, 0);
    }
}
