package com.brotjli.encoder;

import com.brotjli.common.Constants;
import java.util.Arrays;

public final class MetaBlockBuilder {
    private int[] literals;
    private int literalCount;
    private Command[] commands;
    private int commandCount;
    private int totalOutputSize;

    private int[] litFreq;
    private int[] icFreq;
    private int[] distFreq;

    private HuffmanTreeBuilder litTree;
    private HuffmanTreeBuilder icTree;
    private HuffmanTreeBuilder distTree;

    private int quality;

    private static final int NUM_LITERAL_CONTEXTS = 64;
    private int numLitTrees;
    private int[][] litFreqs;
    private HuffmanTreeBuilder[] litTrees;
    private int[] contextMapArray;

    private final int[] distTemp = new int[3];
    private HuffmanTreeBuilder mapTreeReusable;
    private HuffmanTreeBuilder clTreeReusable;
    private final int[] recentDistances = new int[]{16, 15, 11, 4};

    public record Command(
        int insertLength,
        int copyLength,
        int distance,
        byte[] insertLiterals,
        int insertCode,
        int copyCode,
        int icSymbol,
        int distCode,
        int distExtra,
        int distExtraBits
    ) {
        public int insertExtra() {
            return Constants.INSERT_LENGTH_EXTRA_BITS[insertCode];
        }

        public int copyExtra() {
            return Constants.COPY_LENGTH_EXTRA_BITS[copyCode];
        }

        public int insertBase() {
            return Constants.INSERT_LENGTH_BASE[insertCode];
        }

        public int copyBase() {
            return Constants.COPY_LENGTH_BASE[copyCode];
        }
    }

    public static int getCommandSymbol(int insertCode, int copyCode, boolean isImplicitDistance) {
        if (isImplicitDistance) {
            if (insertCode < 8 && copyCode < 8) {
                return insertCode * 8 + copyCode;
            } else if (insertCode < 8 && copyCode >= 8 && copyCode < 16) {
                return 64 + insertCode * 8 + (copyCode - 8);
            }
        } else {
            if (insertCode < 8 && copyCode < 8) {
                return 128 + insertCode * 8 + copyCode;
            } else if (insertCode < 8 && copyCode >= 8 && copyCode < 16) {
                return 192 + insertCode * 8 + (copyCode - 8);
            } else if (insertCode >= 8 && insertCode < 16 && copyCode < 8) {
                return 256 + (insertCode - 8) * 8 + copyCode;
            } else if (insertCode >= 8 && insertCode < 16 && copyCode >= 8 && copyCode < 16) {
                return 320 + (insertCode - 8) * 8 + (copyCode - 8);
            } else if (insertCode < 8 && copyCode >= 16 && copyCode < 24) {
                return 384 + insertCode * 8 + (copyCode - 16);
            } else if (insertCode >= 16 && insertCode < 24 && copyCode < 8) {
                return 448 + (insertCode - 16) * 8 + copyCode;
            } else if (insertCode >= 8 && insertCode < 16 && copyCode >= 16 && copyCode < 24) {
                return 512 + (insertCode - 8) * 8 + (copyCode - 16);
            } else if (insertCode >= 16 && insertCode < 24 && copyCode >= 8 && copyCode < 16) {
                return 576 + (insertCode - 16) * 8 + (copyCode - 8);
            } else if (insertCode >= 16 && insertCode < 24 && copyCode >= 16 && copyCode < 24) {
                return 640 + (insertCode - 16) * 8 + (copyCode - 16);
            }
        }
        throw new IllegalArgumentException("Invalid insertCode " + insertCode + " or copyCode " + copyCode);
    }

    private Command createCommand(int insertLength, int copyLength, int distance, byte[] insertLiterals) {
        int insertCode = 0;
        for (int i = Constants.INSERT_LENGTH_BASE.length - 1; i >= 0; i--) {
            if (insertLength >= Constants.INSERT_LENGTH_BASE[i]) {
                insertCode = i;
                break;
            }
        }

        int copyCode = 0;
        for (int i = Constants.COPY_LENGTH_BASE.length - 1; i >= 0; i--) {
            if (copyLength >= Constants.COPY_LENGTH_BASE[i]) {
                copyCode = i;
                break;
            }
        }

        int distCode = 0;
        int distExtra = 0;
        int distExtraBits = 0;
        boolean isImplicit = false;

        if (distance > 0) {
            if (distance == recentDistances[0]) {
                distCode = 0;
                isImplicit = true;
            } else if (distance == recentDistances[1]) {
                distCode = 1;
            } else if (distance == recentDistances[2]) {
                distCode = 2;
            } else if (distance == recentDistances[3]) {
                distCode = 3;
            } else {
                int dcode = 0;
                boolean found = false;
                for (int dc = 0; dc < 48; dc++) {
                    int ndb = 1 + (dc >> 1);
                    int hc = dc & 1;
                    int offset = ((2 + hc) << ndb) - 4;
                    int maxExtra = (1 << ndb) - 1;
                    int minDist = offset + 1;
                    int maxDist = offset + maxExtra + 1;
                    if (distance >= minDist && distance <= maxDist) {
                        distCode = 16 + dc;
                        distExtra = distance - minDist;
                        distExtraBits = ndb;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    distCode = 16 + 47;
                    distExtra = 0;
                    distExtraBits = 24;
                }
            }

            if (distCode == 1) {
                int temp = recentDistances[1];
                recentDistances[1] = recentDistances[0];
                recentDistances[0] = temp;
            } else if (distCode == 2) {
                int temp = recentDistances[2];
                recentDistances[2] = recentDistances[1];
                recentDistances[1] = recentDistances[0];
                recentDistances[0] = temp;
            } else if (distCode == 3) {
                int temp = recentDistances[3];
                recentDistances[3] = recentDistances[2];
                recentDistances[2] = recentDistances[1];
                recentDistances[1] = recentDistances[0];
                recentDistances[0] = temp;
            } else if (distCode >= 4) {
                recentDistances[3] = recentDistances[2];
                recentDistances[2] = recentDistances[1];
                recentDistances[1] = recentDistances[0];
                recentDistances[0] = distance;
            }
        } else {
            isImplicit = true;
        }

        int icSymbol = getCommandSymbol(insertCode, copyCode, isImplicit);

        return new Command(
            insertLength,
            copyLength,
            distance,
            insertLiterals,
            insertCode,
            copyCode,
            icSymbol,
            distCode,
            distExtra,
            distExtraBits
        );
    }

    public MetaBlockBuilder() {
        this.literals = new int[128];
        this.commands = new Command[32];
        this.litFreq = new int[256];
        this.icFreq = new int[704];
        this.distFreq = new int[520];
        this.litTree = new HuffmanTreeBuilder();
        this.icTree = new HuffmanTreeBuilder();
        this.distTree = new HuffmanTreeBuilder();
        this.mapTreeReusable = new HuffmanTreeBuilder();
        this.clTreeReusable = new HuffmanTreeBuilder();
    }

    public void init(int quality) {
        this.quality = quality;
        this.literalCount = 0;
        this.commandCount = 0;
        this.recentDistances[0] = 16;
        this.recentDistances[1] = 15;
        this.recentDistances[2] = 11;
        this.recentDistances[3] = 4;
        this.totalOutputSize = 0;
        this.simpleLitMap = null;
        Arrays.fill(litFreq, 0);
        Arrays.fill(icFreq, 0);
        Arrays.fill(distFreq, 0);

        int numGroups = quality >= 3 ? 8 : (quality >= 2 ? 4 : 1);
        this.numLitTrees = numGroups + 1;
        if (litFreqs == null || litFreqs.length != numLitTrees) {
            litFreqs = new int[numLitTrees][256];
            litTrees = new HuffmanTreeBuilder[numLitTrees];
        }
        for (int t = 0; t < numLitTrees; t++) {
            Arrays.fill(litFreqs[t], 0);
            litTrees[t] = new HuffmanTreeBuilder();
        }
        int contextsPerGroup = NUM_LITERAL_CONTEXTS / numGroups;
        contextMapArray = new int[NUM_LITERAL_CONTEXTS];
        for (int c = 0; c < NUM_LITERAL_CONTEXTS; c++) {
            contextMapArray[c] = Math.min(c / contextsPerGroup, numGroups - 1);
        }
    }

    public void addLiteral(byte b) {
        addLiteral(b, 0);
    }

    public void addLiteral(byte b, int contextId) {
        if (literalCount >= literals.length) {
            literals = Arrays.copyOf(literals, literals.length * 2);
        }
        literals[literalCount++] = b & 0xFF;
        litFreq[b & 0xFF]++;
        if (contextId >= 0 && contextId < NUM_LITERAL_CONTEXTS && contextMapArray != null) {
            int treeIdx = contextMapArray[contextId];
            if (treeIdx >= 0 && treeIdx < litFreqs.length) {
                litFreqs[treeIdx][b & 0xFF]++;
            }
        }
        totalOutputSize++;
    }

    public void addCopy(int copyLength, int distance) {
        byte[] insertLit = new byte[0];
        if (literalCount > 0) {
            insertLit = new byte[literalCount];
            for (int i = 0; i < literalCount; i++) {
                insertLit[i] = (byte) literals[i];
            }
            literalCount = 0;
        }

        Command cmd = createCommand(insertLit.length, copyLength, distance, insertLit);
        if (commandCount >= commands.length) {
            commands = Arrays.copyOf(commands, commands.length * 2);
        }
        commands[commandCount++] = cmd;

        int icSymbol = cmd.icSymbol();
        if (icSymbol < icFreq.length) {
            icFreq[icSymbol]++;
        }

        if (distance > 0 && icSymbol >= 128) {
            int distCode = cmd.distCode();
            if (distCode < distFreq.length) {
                distFreq[distCode]++;
            }
        }

        totalOutputSize += copyLength;
    }

    public void write(BitWriter writer, int windowBits, boolean isLast) {
        if (literalCount > 0) {
            byte[] insertLit = new byte[literalCount];
            for (int i = 0; i < literalCount; i++) {
                insertLit[i] = (byte) literals[i];
            }
            Command cmd = createCommand(literalCount, 2, 1, insertLit);
            if (commandCount >= commands.length) {
                commands = Arrays.copyOf(commands, commands.length * 2);
            }
            commands[commandCount++] = cmd;
            int icSymbol = cmd.icSymbol();
            if (icSymbol < icFreq.length) icFreq[icSymbol]++;
            if (icSymbol >= 128) {
                int distCode = cmd.distCode();
                if (distCode < distFreq.length) distFreq[distCode]++;
            }
            literalCount = 0;
        }

        buildHuffmanTrees();

        writer.writeBit(isLast ? 1 : 0);

        if (isLast) {
            writer.writeBit(0);
        }

        int mlen = totalOutputSize;
        if (mlen == 0) {
            writer.writeBits(0b11, 2);
            writer.writeBit(0);
            writer.writeBits(0, 2);
            return;
        }

        int mnibbles;
        if (mlen <= 65536) mnibbles = 4;
        else if (mlen <= 1048576) mnibbles = 5;
        else mnibbles = 6;

        switch (mnibbles) {
            case 4 -> writer.writeBits(0, 2);
            case 5 -> writer.writeBits(1, 2);
            case 6 -> writer.writeBits(2, 2);
        }

        writer.writeBits((mlen - 1), mnibbles * 4);

        if (!isLast) {
            writer.writeBit(0);
        }

        // NBLTYPES - one block type for each stream
        writeNbltypes(writer, 1);
        writeNbltypes(writer, 1);
        writeNbltypes(writer, 1);

        // NPOSTFIX and NDIRECT
        writer.writeBits(0, 2);
        writer.writeBits(0, 4);

        // Context modes (1 block type)
        writer.writeBits(Constants.CONTEXT_LSB6, 2);

        // NTREES - always single tree per stream
        writeNbltypes(writer, 1);
        writeNbltypes(writer, 1);

        // Write prefix codes - single litTree for all qualities
        simpleLitMap = null;
        writeSimplePrefixCode(writer, litTree, 256, litFreq);
        java.util.HashMap<Integer, int[]> savedLitMap = simpleLitMap;
        writeSimplePrefixCode(writer, icTree, 704, icFreq);
        writeSimplePrefixCode(writer, distTree, 64, distFreq);
        simpleLitMap = savedLitMap;

        writeCommands(writer);
    }

    private void buildHuffmanTrees() {
        litTree.buildFromFrequencies(litFreq, 256);

        int maxIc = 703;
        while (maxIc > 0 && icFreq[maxIc] == 0) maxIc--;
        icTree.buildFromFrequencies(icFreq, maxIc + 1);

        distTree.buildFromFrequencies(distFreq, 64);
    }

    private java.util.HashMap<Integer, int[]> simpleLitMap;

    private void writeContextMap(BitWriter writer, int[] map, int numTrees) {
        writer.writeBit(0); // rlemax = 0

        int alphabetSize = numTrees;
        int[] mapFreq = new int[alphabetSize];
        for (int value : map) {
            int sym = value + 1;
            if (sym < alphabetSize) {
                mapFreq[sym]++;
            }
        }
        mapFreq[0] = 0;

        mapTreeReusable.buildFromFrequencies(mapFreq, alphabetSize);

        int nsym = 0;
        for (int i = 0; i < alphabetSize; i++) {
            if (mapTreeReusable.getCodeLength(i) > 0) nsym++;
        }
        if (nsym == 0) {
            nsym = 1;
        }

        if (nsym <= 4) {
            writer.writeBits(1, 2);
            writer.writeBits(nsym - 1, 2);
            int alphabetBits = com.brotjli.common.BitUtil.ceilLog2(alphabetSize);
            for (int i = 0; i < alphabetSize; i++) {
                if (mapTreeReusable.getCodeLength(i) > 0) {
                    writer.writeBits(i, alphabetBits);
                }
            }
            if (nsym == 4) writer.writeBit(0);
        } else {
            writer.writeBits(0, 2);
            writeComplexPrefixCode(writer, mapTreeReusable, alphabetSize);
        }

        for (int value : map) {
            int sym = value + 1;
            writer.writeBitsReversed(mapTreeReusable.getCode(sym), mapTreeReusable.getCodeLength(sym));
        }

        writer.writeBit(0); // IMTF = 0
    }

    private void writeCommands(BitWriter writer) {
        for (int ci = 0; ci < commandCount; ci++) {
            Command cmd = commands[ci];
            int icSymbol = cmd.icSymbol();
            int icBits = icTree.getCodeLength(icSymbol);
            if (icBits > 0) {
                writer.writeBitsReversed(icTree.getCode(icSymbol), icBits);
            }

            int insertExtra = cmd.insertExtra();
            int insertBase = cmd.insertBase();
            if (insertExtra > 0) {
                writer.writeBits(cmd.insertLength() - insertBase, insertExtra);
            }

            int copyExtra = cmd.copyExtra();
            int copyBase = cmd.copyBase();
            if (copyExtra > 0) {
                writer.writeBits(cmd.copyLength() - copyBase, copyExtra);
            }

            if (cmd.insertLiterals() != null) {
                byte[] litData = cmd.insertLiterals();
                for (byte b : litData) {
                    int sym = b & 0xFF;
                    int code;
                    int litBits;
                    if (simpleLitMap != null && simpleLitMap.containsKey(sym)) {
                        int[] entry = simpleLitMap.get(sym);
                        code = entry[0];
                        litBits = entry[1];
                    } else {
                        code = litTree.getCode(sym);
                        litBits = litTree.getCodeLength(sym);
                    }
                    if (litBits > 0) {
                        writer.writeBitsReversed(code, litBits);
                    }
                }
            }

            if (icSymbol >= 128) {
                int distCode = cmd.distCode();
                int distExtra = cmd.distExtra();
                int distExtraBits = cmd.distExtraBits();
                int distBits = distTree.getCodeLength(distCode);
                if (distBits > 0) {
                    writer.writeBitsReversed(distTree.getCode(distCode), distBits);
                }
                if (distExtraBits > 0) {
                    writer.writeBits(distExtra, distExtraBits);
                }
            }
        }
    }

    private void writeNbltypes(BitWriter writer, int nbltypes) {
        if (nbltypes == 1) {
            writer.writeBit(0);
        } else if (nbltypes == 2) {
            writer.writeBits(1, 2);
        } else {
            writer.writeBits(3, 2);
            int remaining = nbltypes - 3;
            while (remaining > 0) {
                writer.writeBit(1);
                remaining--;
            }
            writer.writeBit(0);
        }
    }

    private void writeSimplePrefixCode(BitWriter writer, HuffmanTreeBuilder tree, int alphabetSize, int[] frequencies) {
        int[] symbolList = new int[alphabetSize];
        int nsym = 0;
        for (int i = 0; i < alphabetSize; i++) {
            int cl = tree.getCodeLength(i);
            if (cl > 0) {
                symbolList[nsym++] = i;
            }
        }
        if (nsym == 0 && frequencies != null) {
            for (int i = 0; i < alphabetSize; i++) {
                if (i < frequencies.length && frequencies[i] > 0) {
                    symbolList[nsym++] = i;
                    break;
                }
            }
        }
        if (nsym > 0 && nsym <= 4) {
            writer.writeBits(1, 2);
            writer.writeBits(nsym - 1, 2);
            int alphabetBits = com.brotjli.common.BitUtil.ceilLog2(alphabetSize);
            for (int si = 0; si < nsym; si++) {
                writer.writeBits(symbolList[si], alphabetBits);
            }
            if (nsym == 4) writer.writeBit(0);

            {
                if (simpleLitMap == null) simpleLitMap = new java.util.HashMap<>();
                if (nsym == 1) {
                    simpleLitMap.put(symbolList[0], new int[]{0, 0});
                } else {
                    int[] lens = nsym == 2 ? new int[]{1, 1} :
                        nsym == 3 ? new int[]{1, 2, 2} :
                        new int[]{2, 2, 2, 2};
                    int[] idxs = new int[nsym];
                    for (int i = 0; i < nsym; i++) idxs[i] = i;
                    for (int i = 1; i < nsym; i++) {
                        int key = idxs[i];
                        int j = i - 1;
                        while (j >= 0) {
                            int a = idxs[j];
                            if (lens[a] > lens[key] || (lens[a] == lens[key] && symbolList[a] > symbolList[key])) {
                                idxs[j + 1] = a;
                                j--;
                            } else break;
                        }
                        idxs[j + 1] = key;
                    }
                    int code = 0;
                    int prevLen = 0;
                    for (int idx : idxs) {
                        int len = lens[idx];
                        if (len > prevLen) {
                            code <<= (len - prevLen);
                            prevLen = len;
                        }
                        simpleLitMap.put(symbolList[idx], new int[]{code, len});
                        code++;
                    }
                }
                // Rebuild tree with simple codes for correct subsequent encoding
                int[] fixLens = new int[alphabetSize];
                for (int i = 0; i < nsym; i++) {
                    int len = (nsym == 1) ? 0 :
                              (nsym == 2) ? 1 :
                              (nsym == 3) ? (i == 0 ? 1 : 2) :
                              2;
                    fixLens[symbolList[i]] = len;
                }
                tree.buildFromLengths(fixLens, alphabetSize);
            }
        } else {
            simpleLitMap = null;
            writer.writeBits(0, 2); // HSKIP = 0
            writeComplexPrefixCode(writer, tree, alphabetSize);
        }
    }

    private void writeComplexPrefixCode(BitWriter writer, HuffmanTreeBuilder tree, int alphabetSize) {
        int[] lengths = tree.getCodeLengths();
        int lastNonZero = -1;
        for (int i = 0; i < alphabetSize; i++) {
            if (lengths[i] > 0) lastNonZero = i;
        }
        if (lastNonZero < 0) return;

        int[] clFreq = new int[18];
        for (int i = 0; i <= lastNonZero; i++) {
            int len = lengths[i];
            if (len >= 0 && len < clFreq.length) clFreq[len]++;
        }

        int[] clCodeLengths = new int[18];
        buildClCodeLengths(clFreq, clCodeLengths);

        int space = 32;
        for (int i = 0; i < 18; i++) {
            int orderSym = Constants.CODE_LENGTH_ORDER[i];
            int cl = clCodeLengths[orderSym];
            writeFixedCodeLengthValue(writer, cl);
            if (cl > 0) {
                space -= (32 >> cl);
                if (space <= 0) {
                    break;
                }
            }
        }

        int nonZeroCl = 0;
        for (int i = 0; i < 18; i++) {
            if (clCodeLengths[i] > 0) nonZeroCl++;
        }
        if (nonZeroCl >= 2) {
            clTreeReusable.buildFromLengths(clCodeLengths, 18);
        } else if (nonZeroCl == 1) {
            clTreeReusable.buildFromLengths(clCodeLengths, 18);
        } else {
            clCodeLengths[0] = 1;
            clTreeReusable.buildFromLengths(clCodeLengths, 18);
        }

        encodeLengthsWithClTree(writer, clTreeReusable, lengths, lastNonZero);
    }

    private void buildClCodeLengths(int[] clFreq, int[] clCodeLengths) {
        Arrays.fill(clCodeLengths, 0);

        int[] active = new int[18];
        int activeCount = 0;
        for (int i = 0; i <= 15; i++) {
            if (clFreq[i] > 0) active[activeCount++] = i;
        }
        boolean has16 = false, has17 = false;
        for (int i = 0; i < activeCount; i++) {
            if (active[i] == 16) has16 = true;
            if (active[i] == 17) has17 = true;
        }
        if (!has16) active[activeCount++] = 16;
        if (!has17) active[activeCount++] = 17;

        int n = activeCount;
        if (n == 0) { clCodeLengths[0] = 1; return; }

        // Valid distributions for each n (Kraft sum = 32768).
        // Generated by solving: 16a+8b+4c+2d+e=32, a+b+c+d+e=n, a,b,c,d,e >= 0
        int[] lens;
        if (n == 1) {
            lens = new int[]{4};  // single symbol at len 4
        } else if (n == 2) {
            lens = new int[]{1, 1};
        } else if (n == 3) {
            lens = new int[]{1, 2, 2};
        } else if (n == 4) {
            lens = new int[]{2, 2, 2, 2};
        } else if (n == 5) {
            lens = new int[]{1, 3, 3, 3, 3};
        } else if (n == 6) {
            lens = new int[]{2, 2, 3, 3, 3, 3};
        } else if (n == 7) {
            lens = new int[]{2, 3, 3, 3, 3, 3, 3};
        } else if (n == 8) {
            lens = new int[]{3, 3, 3, 3, 3, 3, 3, 3};
        } else if (n == 9) {
            lens = new int[]{3,3,3,3,3,3,3,4,4};
        } else if (n == 10) {
            lens = new int[]{3,3,3,3,3,3,4,4,4,4};
        } else if (n == 11) {
            lens = new int[]{3,3,3,3,3,4,4,4,4,4,4};
        } else if (n == 12) {
            lens = new int[]{3,3,3,3,4,4,4,4,4,4,4,4};
        } else if (n == 13) {
            lens = new int[]{2,4,4,4,4,4,4,4,4,4,4,4,4};
        } else if (n == 14) {
            lens = new int[]{3,3,4,4,4,4,4,4,4,4,4,4,4,4};
        } else if (n == 15) {
            lens = new int[]{3,4,4,4,4,4,4,4,4,4,4,4,4,4,4};
        } else if (n == 16) {
            lens = new int[n];
            for (int i = 0; i < n; i++) lens[i] = 4;
        } else if (n == 17) {
            lens = new int[n];
            for (int i = 0; i < 15; i++) lens[i] = 4;
            lens[15] = 5; lens[16] = 5;
        } else {
            lens = new int[n];
            for (int i = 0; i < 14; i++) lens[i] = 4;
            for (int i = 14; i < n; i++) lens[i] = 5;
        }

        // Sort active symbols by clFreq (descending) to assign shorter lengths to more needed symbols.
        for (int i = 1; i < activeCount; i++) {
            int key = active[i];
            int fk = key <= 15 ? clFreq[key] : 0;
            int j = i - 1;
            while (j >= 0) {
                int a = active[j];
                int fa = a <= 15 ? clFreq[a] : 0;
                if (fk > fa || (fk == fa && key < a)) {
                    active[j + 1] = a;
                    j--;
                } else break;
            }
            active[j + 1] = key;
        }

        for (int i = 0; i < n && i < lens.length; i++) {
            clCodeLengths[active[i]] = lens[i];
        }
    }

    private void encodeLengthsWithClTree(BitWriter writer, HuffmanTreeBuilder clTree,
                                           int[] lengths, int lastNonZero) {
        int i = 0;
        int prevLen = 8;
        boolean canUse16 = false;

        while (i <= lastNonZero) {
            int len = lengths[i];

            if (len == 0) {
                // Count consecutive zeros
                int zeroCount = 0;
                while (i + zeroCount <= lastNonZero && zeroCount < 138 && lengths[i + zeroCount] == 0) {
                    zeroCount++;
                }
                // Write zeros: use ONE symbol 17 for a run of 3-10 zeros max,
                // then individual symbol 0 for the rest.
                // This avoids chaining consecutive 17s which the decoder
                // would interpret as a geometrically growing chain.
                int remaining = zeroCount;
                if (remaining >= 3) {
                    int chunk = Math.min(remaining - 3, 7);
                    writer.writeBitsReversed(clTree.getCode(17), clTree.getCodeLength(17));
                    writer.writeBits(chunk, 3);
                    remaining -= (3 + chunk);
                }
                while (remaining > 0) {
                    writer.writeBitsReversed(clTree.getCode(0), clTree.getCodeLength(0));
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
                    writer.writeBitsReversed(clTree.getCode(16), clTree.getCodeLength(16));
                    writer.writeBits(repeat - 3, 2);
                    i += repeat;
                    canUse16 = false;
                } else {
                    if (len <= 15 && clTree.getCodeLength(len) > 0) {
                        writer.writeBitsReversed(clTree.getCode(len), clTree.getCodeLength(len));
                    }
                    prevLen = len;
                    canUse16 = true;
                    i++;
                }
            } else {
                if (len <= 15 && clTree.getCodeLength(len) > 0) {
                    writer.writeBitsReversed(clTree.getCode(len), clTree.getCodeLength(len));
                }
                prevLen = len;
                canUse16 = true;
                i++;
            }
        }
    }

    private void writeFixedCodeLengthValue(BitWriter writer, int value) {
        switch (value) {
            case 0: writer.writeBits(0, 2); break;
            case 1: writer.writeBits(7, 4); break;
            case 2: writer.writeBits(3, 3); break;
            case 3: writer.writeBits(2, 2); break;
            case 4: writer.writeBits(1, 2); break;
            case 5: writer.writeBits(15, 4); break;
            default: writer.writeBits(0, 2); break;
        }
    }

    private int[] distanceEncode(int distance) {
        if (distance <= 0) {
            distTemp[0] = 0; distTemp[1] = 0; distTemp[2] = 0;
            return distTemp;
        }

        if (distance == 4)  { distTemp[0] = 0; distTemp[1] = 0; distTemp[2] = 0; return distTemp; }
        if (distance == 11) { distTemp[0] = 1; distTemp[1] = 0; distTemp[2] = 0; return distTemp; }
        if (distance == 15) { distTemp[0] = 2; distTemp[1] = 0; distTemp[2] = 0; return distTemp; }
        if (distance == 16) { distTemp[0] = 3; distTemp[1] = 0; distTemp[2] = 0; return distTemp; }
        if (distance == 3)  { distTemp[0] = 4; distTemp[1] = 0; distTemp[2] = 0; return distTemp; }
        if (distance == 5)  { distTemp[0] = 5; distTemp[1] = 0; distTemp[2] = 0; return distTemp; }
        if (distance == 2)  { distTemp[0] = 6; distTemp[1] = 0; distTemp[2] = 0; return distTemp; }
        if (distance == 6)  { distTemp[0] = 7; distTemp[1] = 0; distTemp[2] = 0; return distTemp; }

        for (int dcode = 0; dcode < 48; dcode++) {
            int ndb = 1 + (dcode >> 1);
            int hc = dcode & 1;
            int offset = ((2 + hc) << ndb) - 4;
            int maxExtra = (1 << ndb) - 1;
            int minDist = offset + 1;
            int maxDist = offset + maxExtra + 1;
            if (distance >= minDist && distance <= maxDist) {
                int dextra = distance - minDist;
                distTemp[0] = 16 + dcode; distTemp[1] = dextra; distTemp[2] = ndb;
                return distTemp;
            }
        }
        distTemp[0] = 16 + 47; distTemp[1] = 0; distTemp[2] = 24;
        return distTemp;
    }

    @Deprecated
    private int distanceToCode(int distance) {
        return distanceEncode(distance)[0];
    }

    public int getTotalOutputSize() {
        return totalOutputSize;
    }

    public void reset() {
        literalCount = 0;
        commandCount = 0;
        totalOutputSize = 0;
        simpleLitMap = null;
        Arrays.fill(litFreq, 0);
        Arrays.fill(icFreq, 0);
        Arrays.fill(distFreq, 0);
        if (litFreqs != null) {
            for (int t = 0; t < litFreqs.length; t++) {
                Arrays.fill(litFreqs[t], 0);
            }
        }
        contextMapArray = null;
        numLitTrees = 0;
    }
}
