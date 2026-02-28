package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * SSTable布隆过滤器误判测试类
 * 测试布隆过滤器过滤不存在的key
 */
public class SSTableBloomFilterMissTest {
    @Test
    public void testGetNonExistingKeyReturnsNull() throws Exception {
        TestLogger log = new TestLogger("布隆过滤器过滤测试");
        log.start("测试布隆过滤器过滤不存在的key");
        
        String dir = Files.createTempDirectory("sst-bloom-miss").toFile().getAbsolutePath();
        String file = dir + "/sstable_level0_" + System.currentTimeMillis() + ".db";
        log.data("临时目录", dir);
        
        log.step("创建包含100条数据的SSTable");
        List<KeyValue> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) data.add(new KeyValue("k"+i, "v"+i));
        data.sort(KeyValue::compareTo);
        SSTable t = new SSTable(file, data);
        log.data("数据条目", "k0-k99（100条）");
        
        log.step("查询不存在的key");
        String val = t.get("ZZZ_non_exist_key");
        log.data("查询key", "ZZZ_non_exist_key");
        log.data("返回值", val);
        
        Assert.assertNull(val);
        log.assertSuccess("布隆过滤器正确过滤不存在的key");
        log.pass();
    }
}

