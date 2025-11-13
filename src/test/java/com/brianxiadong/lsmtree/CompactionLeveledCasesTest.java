package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CompactionLeveledCasesTest {
    @Test
    public void testLeveledCompactionMergesAndDeletesOld() throws Exception {
        String dir = TestConfig.getPerformanceTestDataPath("leveled-compaction");
        LSMTree tree = new LSMTree(dir, 2);
        for (int i = 0; i < 6; i++) {
            tree.put("k" + i, "v" + i);
        }
        tree.close();
        File d = new File(dir);
        File[] files = d.listFiles((x, n) -> n.endsWith(".db"));
        List<SSTable> tables = new ArrayList<>();
        if (files != null) {
            for (File f : files) tables.add(new SSTable(f.getAbsolutePath()));
        }
        LeveledCompactionStrategy strat = new LeveledCompactionStrategy(dir, 1, 2);
        List<SSTable> out = strat.compact(tables);
        int level1 = 0;
        for (SSTable t : out) if (t.getFilePath().contains("level1")) level1++;
        Assert.assertTrue(level1 >= 1);
    }
}
