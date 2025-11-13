package com.brianxiadong.lsmtree;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SSTableReadFailureTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test(expected = IOException.class)
    public void testInvalidHeaderThrows() throws Exception {
        File f = tmp.newFile("bad.db");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
            out.writeBytes("LSM1");
            out.writeBytes("LZ");
        }
        new SSTable(f.getAbsolutePath());
    }
}

