package com.brotjli;

import com.brotjli.pool.DecoderPool;
import com.brotjli.pool.EncoderPool;
import com.brotjli.stream.BrotliInputStream;
import com.brotjli.stream.BrotliOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class StreamTest {

    // ==================== BrotliInputStream ====================

    @Test
    void inputStreamRead() throws Exception {
        byte[] data = "Hello Brotli Stream!".getBytes();
        byte[] compressed = Brotjli.compress(data, 0);
        BrotliInputStream bis = new BrotliInputStream(compressed);
        byte[] output = new byte[data.length];
        int totalRead = 0;
        while (totalRead < output.length) {
            int read = bis.read(output, totalRead, output.length - totalRead);
            if (read < 0) break;
            totalRead += read;
        }
        assertArrayEquals(data, output);
        assertEquals(-1, bis.read());
        bis.close();
    }

    @Test
    void inputStreamReadSingleByte() throws Exception {
        byte[] data = "ABC".getBytes();
        byte[] compressed = Brotjli.compress(data, 0);
        BrotliInputStream bis = new BrotliInputStream(compressed);
        assertEquals('A', bis.read());
        assertEquals('B', bis.read());
        assertEquals('C', bis.read());
        assertEquals(-1, bis.read());
        bis.close();
    }

    @Test
    void inputStreamFromInputStream() throws Exception {
        byte[] data = "Stream from InputStream!".getBytes();
        byte[] compressed = Brotjli.compress(data, 0);
        InputStream compressedStream = new ByteArrayInputStream(compressed);
        BrotliInputStream bis = new BrotliInputStream(compressedStream);
        byte[] output = bis.readAllBytes();
        assertArrayEquals(data, output);
        bis.close();
    }

    @Test
    void inputStreamWithCustomDecoder() throws Exception {
        byte[] data = "Custom decoder stream".getBytes();
        byte[] compressed = Brotjli.compress(data, 0);
        BrotjliDecoder decoder = new BrotjliDecoder();
        BrotliInputStream bis = new BrotliInputStream(compressed, decoder);
        byte[] output = bis.readAllBytes();
        assertArrayEquals(data, output);
        bis.close();
    }

    @Test
    void inputStreamSkip() throws Exception {
        byte[] data = "ABCDEFGHIJ".getBytes();
        byte[] compressed = Brotjli.compress(data, 0);
        BrotliInputStream bis = new BrotliInputStream(compressed);
        assertEquals(2, bis.skip(2));
        assertEquals('C', bis.read());
        assertEquals(3, bis.skip(3));
        assertEquals('G', bis.read());
        bis.close();
    }

    @Test
    void inputStreamAvailable() throws Exception {
        byte[] data = "Available test".getBytes();
        byte[] compressed = Brotjli.compress(data, 0);
        BrotliInputStream bis = new BrotliInputStream(compressed);
        assertEquals(data.length, bis.available());
        bis.read();
        assertEquals(data.length - 1, bis.available());
        bis.close();
        assertEquals(0, bis.available());
    }

    @Test
    void inputStreamClosedThrows() throws Exception {
        byte[] compressed = Brotjli.compress("test".getBytes(), 0);
        BrotliInputStream bis = new BrotliInputStream(compressed);
        bis.close();
        assertThrows(IOException.class, () -> bis.read());
    }

    @Test
    void inputStreamEmptyData() throws Exception {
        byte[] compressed = Brotjli.compress(new byte[0], 0);
        BrotliInputStream bis = new BrotliInputStream(compressed);
        assertEquals(-1, bis.read());
        assertEquals(0, bis.available());
        bis.close();
    }

    // ==================== BrotliOutputStream ====================

    @Test
    void outputStreamCompress() throws Exception {
        BrotliOutputStream bos = new BrotliOutputStream(0);
        byte[] data = "Test output stream data".getBytes();
        bos.write(data);
        bos.close();
        byte[] compressed = bos.getCompressedData();
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void outputStreamLargeData() throws Exception {
        BrotliOutputStream bos = new BrotliOutputStream(0);
        byte[] data = new byte[50_000];
        new Random(999).nextBytes(data);
        bos.write(data);
        bos.close();
        byte[] compressed = bos.getCompressedData();
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void outputStreamWritesToProvidedOutputStream() throws Exception {
        byte[] data = "Write to provided OutputStream".getBytes();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BrotliOutputStream bos = new BrotliOutputStream(0, baos);
        bos.write(data);
        bos.close();
        byte[] compressed = baos.toByteArray();
        assertTrue(compressed.length > 0);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void outputStreamDefaultQuality() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BrotliOutputStream bos = new BrotliOutputStream(0, baos);
        byte[] data = "Default quality stream".getBytes();
        bos.write(data);
        bos.close();
        byte[] compressed = baos.toByteArray();
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void outputStreamEmptyData() throws Exception {
        BrotliOutputStream bos = new BrotliOutputStream(0);
        bos.close();
        byte[] compressed = bos.getCompressedData();
        assertNotNull(compressed);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertEquals(0, decompressed.length);
    }

    @Test
    void outputStreamEmptyDataWithOutputStream() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BrotliOutputStream bos = new BrotliOutputStream(0, baos);
        bos.close();
        byte[] compressed = baos.toByteArray();
        byte[] decompressed = Brotjli.decompress(compressed);
        assertEquals(0, decompressed.length);
    }

    // ==================== Round trips ====================

    @Test
    void streamRoundTrip() throws Exception {
        byte[] data = new byte[10_000];
        new Random(777).nextBytes(data);
        BrotliOutputStream bos = new BrotliOutputStream(0);
        bos.write(data);
        bos.close();
        byte[] compressed = bos.getCompressedData();
        BrotliInputStream bis = new BrotliInputStream(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = bis.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        bis.close();
        assertArrayEquals(data, baos.toByteArray());
    }

    @Test
    void streamRoundTripWithProvidedStreams() throws Exception {
        byte[] data = new byte[10_000];
        new Random(777).nextBytes(data);
        ByteArrayOutputStream compressedOs = new ByteArrayOutputStream();
        BrotliOutputStream bos = new BrotliOutputStream(0, compressedOs);
        bos.write(data);
        bos.close();
        byte[] compressed = compressedOs.toByteArray();
        InputStream compressedIs = new ByteArrayInputStream(compressed);
        BrotliInputStream bis = new BrotliInputStream(compressedIs);
        byte[] output = bis.readAllBytes();
        bis.close();
        assertArrayEquals(data, output);
    }

    @Test
    void multipleOutputStreams() throws Exception {
        for (int i = 0; i < 10; i++) {
            BrotliOutputStream bos = new BrotliOutputStream(0);
            bos.write(("Message " + i).getBytes());
            bos.close();
            byte[] compressed = bos.getCompressedData();
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(("Message " + i).getBytes(), decompressed);
        }
    }

    // ==================== Brotjli facade stream methods ====================

    @Test
    void facadeCompressInputStream() throws Exception {
        byte[] data = new byte[10_000];
        new Random(42).nextBytes(data);
        InputStream input = new ByteArrayInputStream(data);
        byte[] compressed = Brotjli.compress(input, 0);
        assertNotNull(compressed);
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void facadeCompressInputStreamToOutputStream() throws Exception {
        byte[] data = new byte[5_000];
        new Random(137).nextBytes(data);
        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        Brotjli.compress(new ByteArrayInputStream(data), compressedOut, 0);
        byte[] compressed = compressedOut.toByteArray();
        byte[] decompressed = Brotjli.decompress(compressed);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void facadeDecompressInputStream() throws Exception {
        byte[] data = new byte[10_000];
        new Random(777).nextBytes(data);
        byte[] compressed = Brotjli.compress(data, 0);
        InputStream compressedStream = new ByteArrayInputStream(compressed);
        byte[] decompressed = Brotjli.decompress(compressedStream);
        assertArrayEquals(data, decompressed);
    }

    @Test
    void facadeDecompressInputStreamToOutputStream() throws Exception {
        byte[] data = new byte[5_000];
        new Random(888).nextBytes(data);
        byte[] compressed = Brotjli.compress(data, 0);
        ByteArrayOutputStream decompressedOut = new ByteArrayOutputStream();
        Brotjli.decompress(new ByteArrayInputStream(compressed), decompressedOut);
        assertArrayEquals(data, decompressedOut.toByteArray());
    }

    @Test
    void facadeNewInputStream() throws Exception {
        byte[] data = "newInputStream test".getBytes();
        byte[] compressed = Brotjli.compress(data, 0);
        BrotliInputStream bis = Brotjli.newInputStream(compressed);
        byte[] output = bis.readAllBytes();
        assertArrayEquals(data, output);
        bis.close();
    }

    @Test
    void facadeNewInputStreamFromStream() throws Exception {
        byte[] data = "newInputStream from stream".getBytes();
        byte[] compressed = Brotjli.compress(data, 0);
        BrotliInputStream bis = Brotjli.newInputStream(new ByteArrayInputStream(compressed));
        byte[] output = bis.readAllBytes();
        assertArrayEquals(data, output);
        bis.close();
    }

    @Test
    void facadeNewOutputStream() throws Exception {
        byte[] data = "newOutputStream test".getBytes();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BrotliOutputStream bos = Brotjli.newOutputStream(baos, 0);
        bos.write(data);
        bos.close();
        byte[] decompressed = Brotjli.decompress(baos.toByteArray());
        assertArrayEquals(data, decompressed);
    }

    @Test
    void facadeFullStreamRoundTrip() throws Exception {
        byte[] data = new byte[10_000];
        new Random(456).nextBytes(data);
        ByteArrayOutputStream compressedOs = new ByteArrayOutputStream();
        Brotjli.compress(new ByteArrayInputStream(data), compressedOs, 0);
        ByteArrayOutputStream decompressedOs = new ByteArrayOutputStream();
        Brotjli.decompress(new ByteArrayInputStream(compressedOs.toByteArray()), decompressedOs);
        assertArrayEquals(data, decompressedOs.toByteArray());
    }

    // ==================== BrotjliEncoder/Decoder stream methods ====================

    @Test
    void encoderEncodeInputStream() throws Exception {
        byte[] data = new byte[10_000];
        new Random(42).nextBytes(data);
        try (BrotjliEncoder encoder = new BrotjliEncoder()) {
            byte[] compressed = encoder.encode(new ByteArrayInputStream(data), 0);
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(data, decompressed);
        }
    }

    @Test
    void encoderEncodeInputStreamToOutputStream() throws Exception {
        byte[] data = new byte[5_000];
        new Random(99).nextBytes(data);
        try (BrotjliEncoder encoder = new BrotjliEncoder()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            encoder.encode(new ByteArrayInputStream(data), baos, 0);
            byte[] compressed = baos.toByteArray();
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(data, decompressed);
        }
    }

    @Test
    void encoderEncodeByteBuffer() throws Exception {
        byte[] data = new byte[1000];
        new Random(42).nextBytes(data);
        ByteBuffer bb = ByteBuffer.wrap(data);
        try (BrotjliEncoder encoder = new BrotjliEncoder()) {
            byte[] compressed = encoder.encode(bb, 0);
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(data, decompressed);
        }
    }

    @Test
    void decoderDecodeInputStream() throws Exception {
        byte[] data = new byte[10_000];
        new Random(42).nextBytes(data);
        byte[] compressed = Brotjli.compress(data, 0);
        try (BrotjliDecoder decoder = new BrotjliDecoder()) {
            byte[] decompressed = decoder.decode(new ByteArrayInputStream(compressed));
            assertArrayEquals(data, decompressed);
        }
    }

    @Test
    void decoderDecodeInputStreamToOutputStream() throws Exception {
        byte[] data = new byte[5_000];
        new Random(99).nextBytes(data);
        byte[] compressed = Brotjli.compress(data, 0);
        try (BrotjliDecoder decoder = new BrotjliDecoder()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            decoder.decode(new ByteArrayInputStream(compressed), baos);
            assertArrayEquals(data, baos.toByteArray());
        }
    }

    @Test
    void decoderDecodeByteBuffer() throws Exception {
        byte[] data = new byte[1000];
        new Random(42).nextBytes(data);
        byte[] compressed = Brotjli.compress(data, 0);
        try (BrotjliDecoder decoder = new BrotjliDecoder()) {
            byte[] decompressed = decoder.decode(ByteBuffer.wrap(compressed));
            assertArrayEquals(data, decompressed);
        }
    }

    // ==================== Pool convenience methods ====================

    @Test
    void encoderPoolCompressBytes() throws Exception {
        EncoderPool pool = new EncoderPool(4, 0);
        try {
            byte[] data = "pool compress bytes".getBytes();
            byte[] compressed = pool.compress(data);
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(data, decompressed);
        } finally {
            pool.close();
        }
    }

    @Test
    void encoderPoolCompressByteBuffer() throws Exception {
        EncoderPool pool = new EncoderPool(4, 0);
        try {
            byte[] data = "pool compress bb".getBytes();
            byte[] compressed = pool.compress(ByteBuffer.wrap(data));
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(data, decompressed);
        } finally {
            pool.close();
        }
    }

    @Test
    void encoderPoolCompressInputStream() throws Exception {
        EncoderPool pool = new EncoderPool(4, 0);
        try {
            byte[] data = "pool compress inputstream".getBytes();
            byte[] compressed = pool.compress(new ByteArrayInputStream(data));
            byte[] decompressed = Brotjli.decompress(compressed);
            assertArrayEquals(data, decompressed);
        } finally {
            pool.close();
        }
    }

    @Test
    void encoderPoolCompressStreamToStream() throws Exception {
        EncoderPool pool = new EncoderPool(4, 0);
        try {
            byte[] data = "pool stream to stream".getBytes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pool.compress(new ByteArrayInputStream(data), baos, 0);
            byte[] decompressed = Brotjli.decompress(baos.toByteArray());
            assertArrayEquals(data, decompressed);
        } finally {
            pool.close();
        }
    }

    @Test
    void decoderPoolDecompressBytes() throws Exception {
        DecoderPool pool = new DecoderPool(4);
        try {
            byte[] data = "pool decompress bytes".getBytes();
            byte[] compressed = Brotjli.compress(data, 0);
            byte[] decompressed = pool.decompress(compressed);
            assertArrayEquals(data, decompressed);
        } finally {
            pool.close();
        }
    }

    @Test
    void decoderPoolDecompressByteBuffer() throws Exception {
        DecoderPool pool = new DecoderPool(4);
        try {
            byte[] data = "pool decompress bb".getBytes();
            byte[] compressed = Brotjli.compress(data, 0);
            byte[] decompressed = pool.decompress(ByteBuffer.wrap(compressed));
            assertArrayEquals(data, decompressed);
        } finally {
            pool.close();
        }
    }

    @Test
    void decoderPoolDecompressInputStream() throws Exception {
        DecoderPool pool = new DecoderPool(4);
        try {
            byte[] data = "pool decompress stream".getBytes();
            byte[] compressed = Brotjli.compress(data, 0);
            byte[] decompressed = pool.decompress(new ByteArrayInputStream(compressed));
            assertArrayEquals(data, decompressed);
        } finally {
            pool.close();
        }
    }

    @Test
    void decoderPoolDecompressStreamToStream() throws Exception {
        DecoderPool pool = new DecoderPool(4);
        try {
            byte[] data = "pool stream to stream decode".getBytes();
            byte[] compressed = Brotjli.compress(data, 0);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pool.decompress(new ByteArrayInputStream(compressed), baos);
            assertArrayEquals(data, baos.toByteArray());
        } finally {
            pool.close();
        }
    }

    // ==================== Parallel bulk operations ====================

    @Test
    void facadeCompressAll() throws Exception {
        List<byte[]> inputs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            byte[] data = new byte[200];
            new Random(i * 100).nextBytes(data);
            inputs.add(data);
        }
        List<byte[]> compressed = Brotjli.compressAll(inputs, 0);
        assertEquals(inputs.size(), compressed.size());
        for (int i = 0; i < inputs.size(); i++) {
            byte[] decompressed = Brotjli.decompress(compressed.get(i));
            assertArrayEquals(inputs.get(i), decompressed);
        }
    }

    @Test
    void facadeDecompressAll() throws Exception {
        List<byte[]> inputs = new ArrayList<>();
        List<byte[]> compressedList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            byte[] data = new byte[200];
            new Random(i * 200).nextBytes(data);
            inputs.add(data);
            compressedList.add(Brotjli.compress(data, 0));
        }
        List<byte[]> decompressed = Brotjli.decompressAll(compressedList);
        assertEquals(inputs.size(), decompressed.size());
        for (int i = 0; i < inputs.size(); i++) {
            assertArrayEquals(inputs.get(i), decompressed.get(i));
        }
    }

    @Test
    void largeDataRoundTrip() throws Exception {
        byte[] data = new byte[50_000];
        new Random(12345).nextBytes(data);
        byte[] compressed = Brotjli.compress(data, 0);
        BrotliInputStream bis = new BrotliInputStream(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = bis.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        bis.close();
        assertArrayEquals(data, baos.toByteArray());
    }
}
