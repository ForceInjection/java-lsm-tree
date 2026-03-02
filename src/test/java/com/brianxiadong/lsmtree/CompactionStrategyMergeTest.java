package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 压缩策略合并逻辑测试
 * 重点测试数据去重和版本控制
 */
public class CompactionStrategyMergeTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testSizeTieredCompactionMergeLogic() throws IOException {
        TestLogger log = new TestLogger("Size-Tiered 合并逻辑测试");
        log.start("测试合并时的版本覆盖");

        String dir = tempFolder.newFolder("stcs_merge").getAbsolutePath();
        SizeTieredCompactionStrategy strategy = new SizeTieredCompactionStrategy(dir, 1024, 2);

        // 模拟两个 SSTable
        // Table 1 (Old): k1=v1_old (ts=100), k2=v2 (ts=100)
        // Table 2 (New): k1=v1_new (ts=200), k3=v3 (ts=200)
        
        List<KeyValue> data1 = new ArrayList<>();
        data1.add(new KeyValue("k1", "v1_old", 100, false));
        data1.add(new KeyValue("k2", "v2", 100, false));
        SSTable table1 = new SSTable(dir + "/t1.db", data1);

        List<KeyValue> data2 = new ArrayList<>();
        data2.add(new KeyValue("k1", "v1_new", 200, false));
        data2.add(new KeyValue("k3", "v3", 200, false));
        SSTable table2 = new SSTable(dir + "/t2.db", data2);

        List<SSTable> tables = new ArrayList<>();
        tables.add(table1);
        tables.add(table2);

        log.step("执行压缩");
        List<SSTable> result = strategy.compact(tables);
        
        log.step("验证结果");
        // 应该合并为一个或多个 Table，包含 k1=v1_new, k2=v2, k3=v3
        List<KeyValue> allResult = new ArrayList<>();
        for (SSTable t : result) {
            allResult.addAll(t.getAllEntries());
        }
        
        // 排序以便验证
        allResult.sort(KeyValue::compareTo);
        
        log.data("结果条数", allResult.size());
        Assert.assertEquals(3, allResult.size());
        
        // 验证 k1
        KeyValue k1 = findKey(allResult, "k1");
        Assert.assertNotNull(k1);
        Assert.assertEquals("v1_new", k1.getValue());
        Assert.assertEquals(200, k1.getTimestamp());
        log.data("k1 value", k1.getValue());
        
        // 验证 k2
        KeyValue k2 = findKey(allResult, "k2");
        Assert.assertNotNull(k2);
        Assert.assertEquals("v2", k2.getValue());
        
        // 验证 k3
        KeyValue k3 = findKey(allResult, "k3");
        Assert.assertNotNull(k3);
        Assert.assertEquals("v3", k3.getValue());
        
        log.assertSuccess("合并逻辑正确，保留了最新版本");
    }

    private KeyValue findKey(List<KeyValue> list, String key) {
        for (KeyValue kv : list) {
            if (kv.getKey().equals(key)) return kv;
        }
        return null;
    }
}
