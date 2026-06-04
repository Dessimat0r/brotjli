package com.brotjli;

import com.brotjli.stream.BrotliInputStream;
import com.brotjli.stream.BrotliOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class StreamTest {

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
}
