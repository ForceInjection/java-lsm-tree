package com.brianxiadong.lsmtree;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * LSM Tree删除可见性测试类
 * 测试删除操作在多层存储中的可见性
 */
public class LSMTreeDeletionVisibilityTest {
    @Test
    public void testDeletionAcrossLayersInRangeAndGet() throws Exception {
        TestLogger log = new TestLogger("删除可见性测试");
        log.start("测试删除操作在多层存储中的可见性");
        
        String dir = TestConfig.getFunctionalTestDataPath("del-vis");
        LSMTree tree = new LSMTree(dir, 3);
        log.data("MemTable阈值", 3);
        
        log.step("插入k1,k2,k3,kX触发刷盘");
        tree.put("k1","v1");
        tree.put("k2","v2");
        tree.put("k3","v3");
        tree.put("kX","vx"); // reach threshold and flush to level0
        log.data("插入数据", "k1, k2, k3, kX");
        
        log.step("删除k2（创建墓碑标记）");
        tree.delete("k2");
        log.data("删除", "k2");

        log.step("测试get操作");
        String k2Value = tree.get("k2");
        log.data("get(k2)结果", k2Value);
        Assert.assertNull(k2Value);
        log.assertSuccess("get正确返回null");
        
        log.step("测试范围查询");
        Iterator<KeyValue> it = tree.range("k1","kZ", true, true);
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next().getKey());
        log.data("返回的keys", keys);
        log.data("包含k2", keys.contains("k2"));
        log.data("包含k1", keys.contains("k1"));
        log.data("包含k3", keys.contains("k3"));
        
        Assert.assertFalse(keys.contains("k2"));
        Assert.assertTrue(keys.contains("k1"));
        Assert.assertTrue(keys.contains("k3"));
        log.assertSuccess("范围查询正确排除了已删除的k2");
        tree.close();
        log.pass();
    }
}
