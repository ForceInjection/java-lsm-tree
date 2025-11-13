package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CompactionSizeTieredCasesTest {
    @Test
    public void testSizeTieredCompactionMergesSimilarSized() throws Exception {
        String dir = TestConfig.getPerformanceTestDataPath("size-tiered");
        LSMTree tree = new LSMTree(dir, 2);
        for (int i = 0; i < 6; i++) {
            tree.put("a" + i, "v" + i);
        }
        tree.close();
        File d = new File(dir);
        File[] files = d.listFiles((x, n) -> n.endsWith(".db"));
        List<SSTable> tables = new ArrayList<>();
        if (files != null) {
            for (File f : files) tables.add(new SSTable(f.getAbsolutePath()));
        }
        SizeTieredCompactionStrategy strat = new SizeTieredCompactionStrategy(dir, 64, 2);
        List<SSTable> out = strat.compact(tables);
        Assert.assertTrue(out.size() >= 1);
    }
}
