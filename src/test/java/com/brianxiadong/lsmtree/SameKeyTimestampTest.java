package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 测试同 Key 同 Timestamp 的覆盖逻辑
 * 确保在时间戳相同时，较新的数据源（MemTable > 新SSTable > 旧SSTable）优先
 */
public class SameKeyTimestampTest {

    @Test
    public void testLSMTreeGetAndRangeWithSameTimestamp() throws IOException {
        TestLogger log = new TestLogger("同时间戳LSMTree读取测试");
        log.start("测试 MemTable 与 SSTable 同 Key 同 Timestamp 时的优先级");

        String dir = Files.createTempDirectory("lsm-same-ts-read").toFile().getAbsolutePath();
        // MemTable size = 100, 避免自动 flush
        LSMTree tree = new LSMTree(dir, 100);

        long ts = 1000L;
        String key = "key1";
        
        // 1. 手动构造一个 SSTable 包含 (key, val_old, ts)
        // 为了注入特定 timestamp，我们需要绕过 LSMTree.put (它使用 System.currentTimeMillis())
        // 但 LSMTree 没有提供带 timestamp 的 put。
        // 我们可以直接生成 SSTable 文件并重新加载 tree，或者利用反射/Mock，
        // 或者简单点：我们手动创建 SSTable 文件，然后放入 dataDir，重启 tree。
        
        // 关闭 tree 以便操作文件
        tree.close();
        
        // 清理目录
        File dataDir = new File(dir);
        if (dataDir.exists()) {
            for (File f : dataDir.listFiles()) f.delete();
        }
        dataDir.mkdirs();
        
        // 创建旧 SSTable (Level 0)
        List<KeyValue> data1 = new ArrayList<>();
        data1.add(new KeyValue(key, "val_old", ts, false));
        String sst1 = dir + "/sstable_level0_1000.db";
        new SSTable(sst1, data1);
        
        // 重新打开 Tree，加载 SSTable
        tree = new LSMTree(dir, 100);
        
        // 验证 SSTable 加载成功
        Assert.assertEquals("val_old", tree.get(key));
        
        // 2. 现在我们需要在 MemTable 中插入 (key, val_new, ts)
        // 由于 LSMTree.put 强制使用 System.currentTimeMillis()，我们很难精确控制 ts 相等。
        // 除非我们 Mock System.currentTimeMillis 或者修改源码。
        // 或者，我们可以利用 "ActiveMemTable" 的访问权限（反射）来注入数据。
        
        // 使用新的重载方法注入 MemTable
        try {
            java.lang.reflect.Field fActive = LSMTree.class.getDeclaredField("activeMemTable");
            fActive.setAccessible(true);
            MemTable mt = (MemTable) fActive.get(tree);
            
            // 使用新添加的 put(KeyValue) 方法
            mt.put(new KeyValue(key, "val_new", ts, false));
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // 3. 验证 get()
        // 预期：MemTable (val_new) 覆盖 SSTable (val_old)
        String val = tree.get(key);
        log.data("get() result", val);
        Assert.assertEquals("val_new", val);
        
        // 4. 验证 range()
        // 预期：返回 val_new
        java.util.Iterator<KeyValue> it = tree.range(key, key, true, true);
        Assert.assertTrue(it.hasNext());
        KeyValue kv = it.next();
        log.data("range() result", kv.getValue());
        Assert.assertEquals("val_new", kv.getValue());
        
        tree.close();
        log.pass();
    }
    
    @Test
    public void testLeveledCompactionSameTimestamp() throws IOException {
        testCompactionSameTimestamp(true);
    }
    
    @Test
    public void testSizeTieredCompactionSameTimestamp() throws IOException {
        testCompactionSameTimestamp(false);
    }
    
    private void testCompactionSameTimestamp(boolean isLeveled) throws IOException {
        String name = isLeveled ? "Leveled" : "SizeTiered";
        TestLogger log = new TestLogger(name + "Compaction 同时间戳覆盖测试");
        log.start("测试 " + name + " 策略在同 Key 同 Timestamp 时的合并行为");
        
        String dir = Files.createTempDirectory("lsm-same-ts-compact-" + name).toFile().getAbsolutePath();
        
        long ts = 2000L;
        String key = "keyA";
        
        // 构造两个 SSTable
        // Table 1 (Old): val_old
        List<KeyValue> d1 = new ArrayList<>();
        d1.add(new KeyValue(key, "val_old", ts, false));
        String f1 = dir + "/sstable_level0_1000.db"; // 时间戳较小，文件名排序在前
        SSTable t1 = new SSTable(f1, d1);
        
        // Table 2 (New): val_new
        List<KeyValue> d2 = new ArrayList<>();
        d2.add(new KeyValue(key, "val_new", ts, false));
        String f2 = dir + "/sstable_level0_2000.db"; // 时间戳较大，文件名排序在后
        SSTable t2 = new SSTable(f2, d2);
        
        List<SSTable> tables = new ArrayList<>(Arrays.asList(t1, t2));
        
        CompactionStrategy strategy;
        if (isLeveled) {
            strategy = new LeveledCompactionStrategy(dir, 1, 10); // threshold=1 to force compaction
        } else {
            strategy = new SizeTieredCompactionStrategy(dir, 10, 2);
        }
        
        // 执行压缩
        List<SSTable> result = strategy.compact(tables);
        
        // 验证结果
        Assert.assertFalse(result.isEmpty());
        // 读取结果 SSTable
        SSTable compacted = result.get(0);
        KeyValue kv = compacted.getEntryRaw(key);
        
        log.data("Compaction result value", kv.getValue());
        log.data("Compaction result timestamp", kv.getTimestamp());
        log.data("Expected timestamp", ts);
        
        Assert.assertEquals("val_new", kv.getValue());
        
        log.pass();
    }
}
