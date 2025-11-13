package com.brianxiadong.lsmtree;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.io.IOException;
import java.util.Arrays;

public class LZ4CompressionStrategy implements CompressionStrategy {
    private final LZ4Factory factory = LZ4Factory.fastestInstance();
    private final LZ4Compressor compressor = factory.fastCompressor();
    private final LZ4FastDecompressor decompressor = factory.fastDecompressor();
    private long in = 0;
    private long out = 0;

    @Override
    public byte[] compress(byte[] data) throws IOException {
        in += data.length;
        int max = compressor.maxCompressedLength(data.length);
        byte[] dest = new byte[max + 4];
        int len = compressor.compress(data, 0, data.length, dest, 4, max);
        // store original length in first 4 bytes (big-endian)
        dest[0] = (byte) ((data.length >>> 24) & 0xFF);
        dest[1] = (byte) ((data.length >>> 16) & 0xFF);
        dest[2] = (byte) ((data.length >>> 8) & 0xFF);
        dest[3] = (byte) (data.length & 0xFF);
        out += len;
        return Arrays.copyOf(dest, len + 4);
    }

    @Override
    public byte[] decompress(byte[] compressedData) throws IOException {
        int origLen = ((compressedData[0] & 0xFF) << 24) | ((compressedData[1] & 0xFF) << 16)
                | ((compressedData[2] & 0xFF) << 8) | (compressedData[3] & 0xFF);
        byte[] outBuf = new byte[origLen];
        decompressor.decompress(compressedData, 4, outBuf, 0, origLen);
        return outBuf;
    }

    @Override
    public String getType() {
        return "LZ4";
    }

    @Override
    public double getCompressionRatio() {
        if (in == 0) return 1.0;
        return out * 1.0 / in;
    }
}

