package com.brotjli;

import com.brotjli.encoder.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EncoderTest {

    @Test
    void bitWriterBasic() {
        BitWriter writer = new BitWriter();
        writer.writeBit(1);
        writer.writeBit(0);
        writer.writeBit(1);
        writer.flush();
        byte[] data = writer.toByteArray();
        assertEquals(1, data.length);
        assertEquals(0b101, data[0] & 0x07);
    }

    @Test
    void bitWriterMultiByte() {
        BitWriter writer = new BitWriter();
        writer.writeBits(0x12345678, 32);
        writer.flush();
        byte[] data = writer.toByteArray();
        assertEquals(4, data.length);
        assertEquals(0x78, data[0] & 0xFF);
        assertEquals(0x56, data[1] & 0xFF);
        assertEquals(0x34, data[2] & 0xFF);
        assertEquals(0x12, data[3] & 0xFF);
    }

    @Test
    void bitWriterAlignToByte() {
        BitWriter writer = new BitWriter();
        writer.writeBits(0xABC, 12);
        writer.alignToByte();
        assertEquals(2, writer.bytesWritten());
    }

    @Test
    void bitWriterReset() {
        BitWriter writer = new BitWriter();
        writer.writeBits(0xFF, 8);
        writer.reset();
        assertEquals(0, writer.bytesWritten());
        writer.writeBits(0x42, 8);
        writer.flush();
        byte[] data = writer.toByteArray();
        assertEquals(1, data.length);
        assertEquals(0x42, data[0] & 0xFF);
    }

    @Test
    void huffmanTreeBuilderBasic() {
        HuffmanTreeBuilder builder = new HuffmanTreeBuilder();
        int[] freqs = new int[256];
        freqs['a'] = 100;
        freqs['b'] = 50;
        freqs['c'] = 30;
        freqs['d'] = 20;
        builder.buildFromFrequencies(freqs, 256);
        assertTrue(builder.getCodeLength('a') > 0);
        assertTrue(builder.nonZeroCount() > 0);
    }

    @Test
    void encoderCanBeCreated() {
        BrotjliEncoder encoder = new BrotjliEncoder();
        assertNotNull(encoder);
    }

    @Test
    void encoderReset() {
        BrotjliEncoder encoder = new BrotjliEncoder();
        encoder.encode("test".getBytes(), 1);
        encoder.reset();
        assertNotNull(encoder);
    }

    @Test
    void lz77MatchFinderBasic() {
        LZ77MatchFinder mf = new LZ77MatchFinder();
        byte[] data = "aaaaabbbbbaaaabbbbbcccccddddd".getBytes();
        mf.init(data, 64, 2);
        mf.buildHashTable();
        var match = mf.findMatch(0);
        assertNotNull(match);
    }
}
