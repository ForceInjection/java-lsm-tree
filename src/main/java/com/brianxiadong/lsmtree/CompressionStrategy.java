package com.brianxiadong.lsmtree;

import java.io.IOException;

public interface CompressionStrategy {
    byte[] compress(byte[] data) throws IOException;
    byte[] decompress(byte[] compressedData) throws IOException;
    String getType();
    double getCompressionRatio();
}

