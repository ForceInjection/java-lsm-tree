package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * SSTable范围查询测试类
 * 测试SSTable的范围查询功能
 */
public class SSTableRangeTest {
    @Test
    public void testRangeExclusive() throws Exception {
        TestLogger log = new TestLogger("SSTable开区间查询测试");
        log.start("测试SSTable的开区间查询(a1,a3)");
        
        String dir = Files.createTempDirectory("sst-range").toFile().getAbsolutePath();
        String file = dir + "/sstable_level0_" + System.currentTimeMillis() + ".db";
        log.data("临时目录", dir);
        
        log.step("创建包含3条数据的SSTable");
        List<KeyValue> data = new ArrayList<>();
        data.add(new KeyValue("a1","v1"));
        data.add(new KeyValue("a2","v2"));
        data.add(new KeyValue("a3","v3"));
        data.sort(KeyValue::compareTo);
        SSTable t = new SSTable(file, data);
        log.data("数据条目", "a1, a2, a3");
        
        log.step("执行开区间查询(a1,a3)");
        List<KeyValue> res = t.getRangeEntries("a1","a3", false, false);
        log.data("返回条目数", res.size());
        log.data("返回的键", res.isEmpty() ? "无" : res.get(0).getKey());
        
        Assert.assertEquals(1, res.size());
        Assert.assertEquals("a2", res.get(0).getKey());
        log.assertSuccess("开区间查询正确返回中间元素");
        log.pass();
    }

    @Test
    public void testEmptySSTableRange() throws IOException {
        TestLogger log = new TestLogger("空SSTable范围查询测试");
        log.start("测试空SSTable的范围查询");
        
        String dir = Files.createTempDirectory("sst-range-empty").toFile().getAbsolutePath();
        String file = dir + "/sstable_level0_" + System.currentTimeMillis() + ".db";
        log.data("临时目录", dir);
        
        log.step("创建空SSTable");
        List<KeyValue> data = new ArrayList<>();
        SSTable t = new SSTable(file, data);
        log.data("数据条目数", 0);
        
        log.step("执行范围查询[a,z]");
        List<KeyValue> res = t.getRangeEntries("a","z", true, true);
        log.data("返回条目数", res.size());
        
        Assert.assertTrue(res.isEmpty());
        log.assertSuccess("空SSTable返回空结果");
        log.pass();
    }

    @Test
    public void testEmptySSTableGetReturnsNull() throws IOException {
        TestLogger log = new TestLogger("空SSTable读取测试");
        log.start("测试空SSTable的单点查询返回null");

        String dir = Files.createTempDirectory("sst-get-empty").toFile().getAbsolutePath();
        String file = dir + "/sstable_level0_" + System.currentTimeMillis() + ".db";
        log.data("临时目录", dir);

        log.step("创建空SSTable");
        List<KeyValue> data = new ArrayList<>();
        SSTable t = new SSTable(file, data);
        log.data("数据条目数", 0);

        log.step("查询不存在的key");
        String value = t.get("missing_key");
        log.data("返回值", value);

        Assert.assertNull(value);
        log.assertSuccess("空SSTable查询返回null");
        log.pass();
    }
}
