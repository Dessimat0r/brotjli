package com.brotjli.encoder;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Builds canonical Huffman codes for the Brotli encoder.
 *
 * Given symbol frequencies, computes optimal or near-optimal code lengths
 * and the corresponding canonical Huffman code assignments.
 *
 * Supports multiple strategies:
 * - Simple: fixed code lengths (for small alphabets)
 * - Frequency-based: package-merge algorithm for optimal codes
 * - Heuristic: fast approximate code length assignment
 */
public final class HuffmanTreeBuilder {
    private static final int MAX_CODE_LENGTH = 15;

    private int[] codeLengths;
    private int[] codes;
    private int alphabetSize;

    public HuffmanTreeBuilder() {
    }

    /**
     * Build canonical Huffman codes from symbol frequencies.
     *
     * @param frequencies  array of frequency counts for each symbol
     * @param alphabetSize number of symbols
     */
    public void buildFromFrequencies(int[] frequencies, int alphabetSize) {
        this.alphabetSize = alphabetSize;
        this.codeLengths = new int[alphabetSize];
        this.codes = new int[alphabetSize];

        int nonZero = 0;
        for (int i = 0; i < alphabetSize; i++) {
            if (frequencies[i] > 0) nonZero++;
        }

        if (nonZero == 0) {
            codeLengths[0] = 1;
            codes[0] = 0;
            return;
        }

        if (nonZero == 1) {
            for (int i = 0; i < alphabetSize; i++) {
                if (frequencies[i] > 0) {
                    codeLengths[i] = 0;
                    codes[i] = 0;
                }
            }
            return;
        }

        computeCodeLengths(frequencies, alphabetSize);
    }

    /**
     * Build canonical codes from pre-determined code lengths.
     * Used for building the code-length Huffman tree in complex prefix codes.
     */
    public void buildFromLengths(int[] codeLengths, int alphabetSize) {
        this.alphabetSize = alphabetSize;
        this.codeLengths = codeLengths.clone();
        this.codes = new int[alphabetSize];
        computeCanonicalCodes();
    }

    /**
     * Build limited-length canonical codes (for Brotli's 15-bit max).
     * Uses a simplified package-merge algorithm for optimality.
     */
    private void computeCodeLengths(int[] frequencies, int alphabetSize) {
        java.util.ArrayList<Node> leaves = new java.util.ArrayList<>();
        for (int i = 0; i < alphabetSize; i++) {
            if (frequencies[i] > 0) {
                leaves.add(new Node(i, frequencies[i]));
            }
        }

        leaves.sort((a, b) -> Integer.compare(a.freq, b.freq));

        PriorityQueue<Node> queue = new PriorityQueue<>((a, b) -> Integer.compare(a.freq, b.freq));
        queue.addAll(leaves);

        while (queue.size() > 1) {
            Node left = queue.poll();
            Node right = queue.poll();
            Node parent = new Node(-1, left.freq + right.freq);
            parent.left = left;
            parent.right = right;
            queue.add(parent);
        }

        Node root = queue.poll();
        if (root != null) {
            assignLengths(root, 0);
        }

        limitCodeLengths();
    }

    private void assignLengths(Node node, int depth) {
        if (node == null) return;
        if (node.symbol >= 0) {
            codeLengths[node.symbol] = Math.min(depth, MAX_CODE_LENGTH);
            return;
        }
        assignLengths(node.left, depth + 1);
        assignLengths(node.right, depth + 1);
    }

    /**
     * Limit code lengths to MAX_CODE_LENGTH (15) by rebalancing.
     */
    private void limitCodeLengths() {
        int maxLen = 0;
        for (int i = 0; i < alphabetSize; i++) {
            if (codeLengths[i] > maxLen) {
                maxLen = codeLengths[i];
            }
        }

        if (maxLen <= MAX_CODE_LENGTH) {
            computeCanonicalCodes();
            return;
        }

        int[] count = new int[MAX_CODE_LENGTH + 1];
        for (int i = 0; i < alphabetSize; i++) {
            if (codeLengths[i] > MAX_CODE_LENGTH) {
                count[MAX_CODE_LENGTH]++;
            } else if (codeLengths[i] > 0) {
                count[codeLengths[i]]++;
            }
        }

        for (int i = 0; i < alphabetSize; i++) {
            if (codeLengths[i] > MAX_CODE_LENGTH) {
                for (int l = MAX_CODE_LENGTH; l >= 1; l--) {
                    if (count[l] < (1 << l)) {
                        codeLengths[i] = l;
                        count[l]++;
                        break;
                    }
                }
            }
        }

        computeCanonicalCodes();
    }

    /**
     * Compute canonical codes from code lengths.
     * After this, codes[sym] contains the MSB-first code value.
     */
    private void computeCanonicalCodes() {
        int[] blCount = new int[MAX_CODE_LENGTH + 1];
        for (int i = 0; i < alphabetSize; i++) {
            if (codeLengths[i] > 0) {
                blCount[codeLengths[i]]++;
            }
        }

        int[] nextCode = new int[MAX_CODE_LENGTH + 1];
        int code = 0;
        for (int bits = 1; bits <= MAX_CODE_LENGTH; bits++) {
            code = (code + blCount[bits - 1]) << 1;
            nextCode[bits] = code;
        }

        for (int sym = 0; sym < alphabetSize; sym++) {
            int len = codeLengths[sym];
            if (len > 0) {
                codes[sym] = nextCode[len];
                nextCode[len]++;
            }
        }
    }

    /**
     * Build simple prefix code with fixed lengths.
     * Used for simple prefix tree encoding in the bitstream.
     *
     * @param symbols     the symbol values (sorted)
     * @param nsym        number of symbols (1-4)
     * @param treeSelect  0 or 1 for nsym==4
     */
    public void buildSimpleCode(int[] symbols, int nsym, int treeSelect) {
        this.alphabetSize = nsym;
        this.codeLengths = new int[nsym];
        this.codes = new int[nsym];

        if (nsym == 1) {
            codeLengths[0] = 0;
            codes[0] = 0;
        } else if (nsym == 2) {
            codeLengths[0] = 1;
            codeLengths[1] = 1;
            computeCanonicalCodes();
        } else if (nsym == 3) {
            codeLengths[0] = 1;
            codeLengths[1] = 2;
            codeLengths[2] = 2;
            computeCanonicalCodes();
        } else {
            if (treeSelect == 0) {
                codeLengths[0] = 2;
                codeLengths[1] = 2;
                codeLengths[2] = 2;
                codeLengths[3] = 2;
            } else {
                codeLengths[0] = 1;
                codeLengths[1] = 2;
                codeLengths[2] = 3;
                codeLengths[3] = 3;
            }
            computeCanonicalCodes();
        }
    }

    /**
     * Get the code length for a symbol.
     */
    public int getCodeLength(int symbol) {
        if (symbol < codeLengths.length) {
            return codeLengths[symbol];
        }
        return 0;
    }

    /**
     * Get all code lengths.
     */
    public int[] getCodeLengths() {
        return codeLengths.clone();
    }

    /**
     * Get the code (MSB-first) for a symbol.
     */
    public int getCode(int symbol) {
        if (symbol < codes.length) {
            return codes[symbol];
        }
        return 0;
    }

    /**
     * Count non-zero code lengths.
     */
    public int nonZeroCount() {
        int count = 0;
        for (int i = 0; i < alphabetSize; i++) {
            if (codeLengths[i] > 0) count++;
        }
        return count;
    }

    /**
     * Compute the total encoded size of data compressed with this tree.
     */
    public long computeEncodedSize(int[] frequencies) {
        long size = 0;
        for (int i = 0; i < alphabetSize; i++) {
            if (frequencies[i] > 0 && codeLengths[i] > 0) {
                size += (long) frequencies[i] * codeLengths[i];
            }
        }
        return size;
    }

    private static class Node {
        final int symbol;
        final int freq;
        Node left;
        Node right;

        Node(int symbol, int freq) {
            this.symbol = symbol;
            this.freq = freq;
        }
    }
}
