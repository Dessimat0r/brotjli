package com.brotjli.common;

/**
 * Brotli dictionary word transforms. Each transform takes a dictionary word
 * and applies: prefix + word[omitFirst .. wordLength - omitLast] + suffix.
 *
 * There are 88 transforms (0-87) for standard Brotli, and 33 additional
 * (88-120) for large-window mode.
 */
public final class WordTransform {
    private WordTransform() {}

    /**
     * A single transform operation.
     */
    public record Transform(byte[] prefix, byte[] suffix, int omitFirst, int omitLast) {
        public int maxOutputSize(int wordLength) {
            return prefix.length + wordLength + suffix.length;
        }
    }

    private static final byte[] EMPTY = {};
    private static final byte[] SPACE = {' '};
    private static final byte[] COMMA = {','};
    private static final byte[] DOT = {'.'};
    private static final byte[] APOSTROPHE = {'\''};
    private static final byte[] THE_SPACE = {'t', 'h', 'e', ' '};

    private static final byte[][] PREFIXES = {
        EMPTY, EMPTY, SPACE, EMPTY, SPACE, EMPTY, EMPTY, SPACE, THE_SPACE, EMPTY, SPACE
    };

    private static final byte[][] SUFFIXES = {
        EMPTY, COMMA, SPACE, DOT, EMPTY, SPACE, APOSTROPHE, SPACE, EMPTY, COMMA, EMPTY
    };

    private static final Transform[] TRANSFORMS = new Transform[121];

    static {
        int idx = 0;
        for (int base = 0; base < 11; base++) {
            for (int variant = 0; variant < 8; variant++) {
                int omitLast = variant;
                int actualFirst = 0;
                // Base 8, variant 7 has special omitLast handling in RFC 7932
                int actualLast = base == 8 && variant == 7 ? 8 : omitLast;
                TRANSFORMS[idx++] = new Transform(PREFIXES[base], SUFFIXES[base], actualFirst, actualLast);
            }
        }
        for (; idx < 121; idx++) {
            TRANSFORMS[idx] = new Transform(EMPTY, EMPTY, 0, 0);
        }
    }

    /**
     * Apply transform to a dictionary word.
     *
     * @param word        the raw dictionary word bytes
     * @param transformId transform index (0-120)
     * @return the transformed word bytes
     */
    public static byte[] apply(byte[] word, int transformId) {
        if (transformId < 0 || transformId >= TRANSFORMS.length) {
            throw new IllegalArgumentException("Invalid transform ID: " + transformId);
        }
        Transform t = TRANSFORMS[transformId];

        int wordLen = word.length;
        int omittedFirst = Math.min(t.omitFirst(), wordLen);
        int omittedLast = Math.min(t.omitLast(), wordLen - omittedFirst);
        int middleLen = wordLen - omittedFirst - omittedLast;

        byte[] result = new byte[t.prefix().length + middleLen + t.suffix().length];
        int pos = 0;

        System.arraycopy(t.prefix(), 0, result, pos, t.prefix().length);
        pos += t.prefix().length;

        System.arraycopy(word, omittedFirst, result, pos, middleLen);
        pos += middleLen;

        System.arraycopy(t.suffix(), 0, result, pos, t.suffix().length);

        return result;
    }

    /**
     * Apply transform into an existing buffer.
     *
     * @param word        raw dictionary word
     * @param transformId transform index
     * @param dest        destination buffer
     * @param destPos     starting position in dest
     * @return number of bytes written
     */
    public static int apply(byte[] word, int transformId, byte[] dest, int destPos) {
        Transform t = TRANSFORMS[transformId];
        int wordLen = word.length;
        int omittedFirst = Math.min(t.omitFirst(), wordLen);
        int omittedLast = Math.min(t.omitLast(), wordLen - omittedFirst);
        int middleLen = wordLen - omittedFirst - omittedLast;
        int totalLen = t.prefix().length + middleLen + t.suffix().length;

        int pos = destPos;
        System.arraycopy(t.prefix(), 0, dest, pos, t.prefix().length);
        pos += t.prefix().length;
        System.arraycopy(word, omittedFirst, dest, pos, middleLen);
        pos += middleLen;
        System.arraycopy(t.suffix(), 0, dest, pos, t.suffix().length);

        return totalLen;
    }

    /**
     * Maximum number of bytes a transform can add to a word.
     * Max prefix length (4) + max suffix length (4) + padding = 13
     */
    public static int maxExtraBytes() {
        return 13;
    }

    /**
     * Get the number of transforms.
     */
    public static int count() {
        return TRANSFORMS.length;
    }

    /**
     * Get a specific transform.
     */
    public static Transform get(int id) {
        return TRANSFORMS[id];
    }
}
