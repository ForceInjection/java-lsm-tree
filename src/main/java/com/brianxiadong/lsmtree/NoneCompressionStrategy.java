package com.brianxiadong.lsmtree;

import java.io.IOException;

/**
 * 无压缩策略
 * <p>
 * 不对数据进行任何压缩，直接存储原始数据。
 * 适用于对 CPU 敏感或数据本身已压缩的场景。
 */
public class NoneCompressionStrategy implements CompressionStrategy {
    private long in = 0;
    private long out = 0;

    @Override
    public byte[] compress(byte[] data) throws IOException {
        in += data.length;
        out += data.length;
        return data;
    }

    @Override
    public byte[] decompress(byte[] compressedData) throws IOException {
        return compressedData;
    }

    @Override
    public String getType() {
        return "NONE";
    }

    @Override
    public double getCompressionRatio() {
        if (in == 0) return 1.0;
        return out * 1.0 / in;
    }
}

