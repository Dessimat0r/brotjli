package com.brotjli;

import com.brotjli.decoder.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DecoderTest {

    @Test
    void bitReaderBasic() {
        byte[] data = {(byte)0b10101010};
        BitReader reader = new BitReader(data);
        assertEquals(0, reader.readBit());
        assertEquals(1, reader.readBit());
        assertEquals(0, reader.readBit());
        assertEquals(1, reader.readBit());
        assertEquals(0, reader.readBit());
        assertEquals(1, reader.readBit());
        assertEquals(0, reader.readBit());
        assertEquals(1, reader.readBit());
        assertTrue(reader.isFinished());
    }

    @Test
    void bitReaderMultiByte() {
        // Write 0x1234 as 16 bits LSB first:
        // byte 0: 0x34, byte 1: 0x12
        byte[] data = {(byte)0x34, (byte)0x12};
        BitReader reader = new BitReader(data);
        assertEquals(0x1234, reader.readBits(16));
    }

    @Test
    void bitReaderAlignedBytes() {
        byte[] data = {0x01, 0x02, 0x03};
        BitReader reader = new BitReader(data);
        assertEquals(0x01, reader.readBits(8));
        assertEquals(0x02, reader.readBits(8));
        assertEquals(0x03, reader.readBits(8));
    }

    @Test
    void bitReaderAlignToByte() {
        byte[] data = {(byte)0xFF, 0x42};
        BitReader reader = new BitReader(data);
        assertEquals(1, reader.readBit());
        reader.alignToByte();
        assertEquals(0x42, reader.readBits(8));
    }

    @Test
    void bitReaderReset() {
        BitReader reader = new BitReader(new byte[]{0x0F, (byte)0xF0});
        assertEquals(0x0F, reader.readBits(8));
        assertEquals(0xF0, reader.readBits(8));
        assertTrue(reader.isFinished());
        reader.reset(new byte[]{(byte)0xAB});
        assertEquals(0xAB, reader.readBits(8));
    }

    @Test
    void decoderCanBeCreated() {
        BrotjliDecoder decoder = new BrotjliDecoder();
        assertNotNull(decoder);
    }

    @Test
    void decoderReset() {
        BrotjliDecoder decoder = new BrotjliDecoder();
        decoder.reset();
        assertNotNull(decoder);
    }

    @Test
    void contextDecoderBasic() {
        ContextDecoder cd = new ContextDecoder();
        cd.setContextMode(0, 0);
        int ctx = cd.computeContextId(0x42, 0x00);
        assertEquals(0x42 & 0x3F, ctx);
    }
}
