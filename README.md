# Brotjli

A pure-Java Brotli compression library with encoder and decoder.

## Features
- Full Brotli format support (RFC 7932)
- Pure Java, no native dependencies
- Thread-safe with borrow/release pooling
- Streaming API (InputStream/OutputStream)
- Configurable quality levels (0–11)
- BSD licensed

## Usage
```java
// One-shot
byte[] compressed = Brotjli.compress(data, 2);
byte[] decompressed = Brotjli.decompress(compressed);

// Streaming
BrotliInputStream decoder = new BrotliInputStream(inputStream);
BrotliOutputStream encoder = new BrotliOutputStream(outputStream, 2);

// Pooled (for web servers)
EncoderPool pool = new EncoderPool(4, 2);
BrotjliEncoder enc = pool.borrow();
enc.encode(input, output);
pool.release(enc);
```

## Building
```bash
mvn package
```
