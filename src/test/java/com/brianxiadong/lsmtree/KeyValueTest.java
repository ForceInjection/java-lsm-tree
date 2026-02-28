package com.brianxiadong.lsmtree;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * KeyValue数据结构测试类
 * 验证排序行为、时间戳版本控制和墓碑标记机制
 */
public class KeyValueTest {

    private List<KeyValue> keyValues;
    private TestLogger log;

    @Before
    public void setUp() {
        keyValues = new ArrayList<>();
    }

    /**
     * 测试基本的KeyValue创建和属性访问
     */
    @Test
    public void testBasicKeyValueCreation() {
        log = new TestLogger("KeyValue基本创建测试");
        log.start("测试KeyValue对象的创建和属性访问");
        
        log.step("创建KeyValue对象");
        KeyValue kv = new KeyValue("key1", "value1");
        log.data("key", kv.getKey());
        log.data("value", kv.getValue());
        log.data("isDeleted", kv.isDeleted());
        log.data("timestamp", kv.getTimestamp());
        
        assertEquals("key1", kv.getKey());
        assertEquals("value1", kv.getValue());
        assertFalse(kv.isDeleted());
        assertTrue(kv.getTimestamp() > 0);
        log.assertSuccess("KeyValue属性正确");
        log.pass();
    }

    /**
     * 测试墓碑标记的创建
     */
    @Test
    public void testTombstoneCreation() {
        log = new TestLogger("墓碑标记创建测试");
        log.start("测试删除标记（Tombstone）的创建");
        
        log.step("创建Tombstone");
        KeyValue tombstone = KeyValue.createTombstone("key1");
        log.data("key", tombstone.getKey());
        log.data("value", tombstone.getValue());
        log.data("isDeleted", tombstone.isDeleted());
        log.data("timestamp", tombstone.getTimestamp());
        
        assertEquals("key1", tombstone.getKey());
        assertNull(tombstone.getValue());
        assertTrue(tombstone.isDeleted());
        assertTrue(tombstone.getTimestamp() > 0);
        log.assertSuccess("Tombstone属性正确");
        log.pass();
    }

    /**
     * 测试相同键的排序行为（按时间戳降序）
     */
    @Test
    public void testSortingBySameKey() throws InterruptedException {
        log = new TestLogger("相同键排序测试");
        log.start("测试相同键按时间戳降序排列");
        
        log.step("创建相同键但不同时间戳的KeyValue");
        KeyValue kv1 = new KeyValue("key1", "value1", 1000, false);
        Thread.sleep(1); // 确保时间戳不同
        KeyValue kv2 = new KeyValue("key1", "value2", 2000, false);
        KeyValue kv3 = new KeyValue("key1", "value3", 3000, false);
        log.data("创建条目数", 3);

        keyValues.add(kv1);
        keyValues.add(kv3);
        keyValues.add(kv2);

        log.step("执行排序");
        Collections.sort(keyValues);

        log.data("排序后[0]时间戳", keyValues.get(0).getTimestamp());
        log.data("排序后[1]时间戳", keyValues.get(1).getTimestamp());
        log.data("排序后[2]时间戳", keyValues.get(2).getTimestamp());
        
        // 验证排序结果：相同键按时间戳降序排列（新的在前）
        assertEquals(3000, keyValues.get(0).getTimestamp());
        assertEquals(2000, keyValues.get(1).getTimestamp());
        assertEquals(1000, keyValues.get(2).getTimestamp());
        log.assertSuccess("相同键按时间戳降序排列正确");
        log.pass();
    }

    /**
     * 测试不同键的排序行为（按键字典序）
     */
    @Test
    public void testSortingByDifferentKeys() {
        log = new TestLogger("不同键排序测试");
        log.start("测试不同键按字典序排列");
        
        log.step("创建不同键的KeyValue");
        KeyValue kv1 = new KeyValue("c", "value1", 1000, false);
        KeyValue kv2 = new KeyValue("a", "value2", 2000, false);
        KeyValue kv3 = new KeyValue("b", "value3", 3000, false);

        keyValues.add(kv1);
        keyValues.add(kv2);
        keyValues.add(kv3);

        log.step("执行排序");
        Collections.sort(keyValues);

        log.data("排序后[0]键", keyValues.get(0).getKey());
        log.data("排序后[1]键", keyValues.get(1).getKey());
        log.data("排序后[2]键", keyValues.get(2).getKey());

        // 验证排序结果：不同键按字典序排列
        assertEquals("a", keyValues.get(0).getKey());
        assertEquals("b", keyValues.get(1).getKey());
        assertEquals("c", keyValues.get(2).getKey());
        log.assertSuccess("不同键按字典序排列正确");
        log.pass();
    }

    /**
     * 测试混合排序：键优先，时间戳次之
     */
    @Test
    public void testMixedSorting() {
        log = new TestLogger("混合排序测试");
        log.start("测试键优先、时间戳次之的排序规则");
        
        log.step("创建混合的KeyValue列表");
        KeyValue kv1 = new KeyValue("b", "value1", 1000, false);
        KeyValue kv2 = new KeyValue("a", "value2", 3000, false);
        KeyValue kv3 = new KeyValue("a", "value3", 2000, false);
        KeyValue kv4 = new KeyValue("c", "value4", 1500, false);

        keyValues.add(kv1);
        keyValues.add(kv2);
        keyValues.add(kv3);
        keyValues.add(kv4);

        log.step("执行排序");
        Collections.sort(keyValues);

        log.data("排序后顺序", 
            keyValues.get(0).getKey() + "(" + keyValues.get(0).getTimestamp() + "), " +
            keyValues.get(1).getKey() + "(" + keyValues.get(1).getTimestamp() + "), " +
            keyValues.get(2).getKey() + "(" + keyValues.get(2).getTimestamp() + "), " +
            keyValues.get(3).getKey() + "(" + keyValues.get(3).getTimestamp() + ")");

        // 验证排序结果
        assertEquals("a", keyValues.get(0).getKey());
        assertEquals(3000, keyValues.get(0).getTimestamp()); // a键中时间戳最新的
        assertEquals("a", keyValues.get(1).getKey());
        assertEquals(2000, keyValues.get(1).getTimestamp()); // a键中时间戳较旧的
        assertEquals("b", keyValues.get(2).getKey());
        assertEquals("c", keyValues.get(3).getKey());
        log.assertSuccess("混合排序正确：键优先、时间戳次之");
        log.pass();
    }

    /**
     * 测试版本控制场景：同一键的多个版本
     */
    @Test
    public void testVersionControl() {
        log = new TestLogger("版本控制测试");
        log.start("测试同一键的多个版本排序");
        
        String key = "user:123";
        log.data("测试键", key);
        
        log.step("模拟同一用户数据的多次更新");
        KeyValue v1 = new KeyValue(key, "name:Alice", 1000, false);
        KeyValue v2 = new KeyValue(key, "name:Alice,age:25", 2000, false);
        KeyValue v3 = new KeyValue(key, "name:Alice,age:26", 3000, false);

        keyValues.add(v2);
        keyValues.add(v1);
        keyValues.add(v3);

        log.step("执行排序");
        Collections.sort(keyValues);

        log.data("最新版本值", keyValues.get(0).getValue());
        log.data("最新版本时间戳", keyValues.get(0).getTimestamp());

        // 最新版本应该在前面
        assertEquals(3000, keyValues.get(0).getTimestamp());
        assertEquals("name:Alice,age:26", keyValues.get(0).getValue());
        
        // 验证历史版本的顺序
        assertEquals(2000, keyValues.get(1).getTimestamp());
        assertEquals(1000, keyValues.get(2).getTimestamp());
        log.assertSuccess("版本控制排序正确，最新版本在前");
        log.pass();
    }

    /**
     * 测试墓碑标记在排序中的行为
     */
    @Test
    public void testTombstoneInSorting() {
        log = new TestLogger("墓碑标记排序测试");
        log.start("测试删除标记在排序中的位置");
        
        String key = "key1";
        log.data("测试键", key);
        
        log.step("创建包含墓碑标记的数据");
        KeyValue normalValue = new KeyValue(key, "value1", 1000, false);
        KeyValue tombstone = new KeyValue(key, null, 2000, true);
        KeyValue newerValue = new KeyValue(key, "value2", 3000, false);

        keyValues.add(tombstone);
        keyValues.add(normalValue);
        keyValues.add(newerValue);

        log.step("执行排序");
        Collections.sort(keyValues);

        log.data("排序后[0]时间戳", keyValues.get(0).getTimestamp() + " (isDeleted: " + keyValues.get(0).isDeleted() + ")");
        log.data("排序后[1]时间戳", keyValues.get(1).getTimestamp() + " (isDeleted: " + keyValues.get(1).isDeleted() + ")");
        log.data("排序后[2]时间戳", keyValues.get(2).getTimestamp() + " (isDeleted: " + keyValues.get(2).isDeleted() + ")");

        // 验证排序：最新的值在前，然后是墓碑，最后是旧值
        assertEquals(3000, keyValues.get(0).getTimestamp());
        assertFalse(keyValues.get(0).isDeleted());
        
        assertEquals(2000, keyValues.get(1).getTimestamp());
        assertTrue(keyValues.get(1).isDeleted());
        
        assertEquals(1000, keyValues.get(2).getTimestamp());
        assertFalse(keyValues.get(2).isDeleted());
        log.assertSuccess("墓碑标记排序正确");
        log.pass();
    }

    /**
     * 测试压缩场景：查找最新有效值
     */
    @Test
    public void testCompactionScenario() {
        log = new TestLogger("压缩场景测试");
        log.start("测试压缩时查找最新有效值");
        
        String key = "key1";
        log.data("测试键", key);
        
        log.step("模拟压缩前的数据：包含多个版本和删除标记");
        KeyValue v1 = new KeyValue(key, "value1", 1000, false);
        KeyValue delete = new KeyValue(key, null, 2000, true);
        KeyValue v2 = new KeyValue(key, "value2", 3000, false);
        KeyValue v3 = new KeyValue(key, "value3", 4000, false);

        keyValues.add(delete);
        keyValues.add(v1);
        keyValues.add(v3);
        keyValues.add(v2);

        log.step("执行排序");
        Collections.sort(keyValues);

        log.step("查找最新的有效值（用于压缩决策）");
        KeyValue latestValid = null;
        for (KeyValue kv : keyValues) {
            if (kv.getKey().equals(key)) {
                if (!kv.isDeleted()) {
                    latestValid = kv;
                    break; // 找到最新的非删除值
                }
            }
        }

        log.data("最新有效值", latestValid != null ? latestValid.getValue() : "null");
        log.data("最新有效时间戳", latestValid != null ? latestValid.getTimestamp() : "null");

        assertNotNull(latestValid);
        assertEquals("value3", latestValid.getValue());
        assertEquals(4000, latestValid.getTimestamp());
        log.assertSuccess("成功找到最新有效值");
        log.pass();
    }

    /**
     * 测试toString方法
     */
    @Test
    public void testToString() {
        log = new TestLogger("toString方法测试");
        log.start("测试KeyValue的toString输出");
        
        log.step("测试普通KeyValue的toString");
        KeyValue kv = new KeyValue("key1", "value1", 12345, false);
        String expected = "KeyValue{key='key1', value='value1', timestamp=12345, deleted=false}";
        log.data("普通KV toString", kv.toString());
        assertEquals(expected, kv.toString());

        log.step("测试墓碑标记的toString");
        KeyValue tombstone = new KeyValue("key2", null, 67890, true);
        String expectedTombstone = "KeyValue{key='key2', value='null', timestamp=67890, deleted=true}";
        log.data("墓碑toString", tombstone.toString());
        assertEquals(expectedTombstone, tombstone.toString());
        log.assertSuccess("toString输出正确");
        log.pass();
    }

    /**
     * 测试compareTo方法的边界情况
     */
    @Test
    public void testCompareToEdgeCases() {
        log = new TestLogger("compareTo边界测试");
        log.start("测试compareTo方法的边界情况");
        
        log.step("创建相同键和时间戳的KeyValue");
        KeyValue kv1 = new KeyValue("key1", "value1", 1000, false);
        KeyValue kv2 = new KeyValue("key1", "value2", 1000, false);

        log.data("kv1.compareTo(kv2)", kv1.compareTo(kv2));
        log.data("kv2.compareTo(kv1)", kv2.compareTo(kv1));
        
        // 相同键和时间戳的情况
        assertEquals(0, kv1.compareTo(kv2));
        assertEquals(0, kv2.compareTo(kv1));
        log.assertSuccess("相同键时间戳比较返回0");

        log.step("测试自己与自己比较");
        log.data("kv1.compareTo(kv1)", kv1.compareTo(kv1));
        assertEquals(0, kv1.compareTo(kv1));
        log.assertSuccess("自比较返回0");
        log.pass();
    }

    /**
     * 测试大量数据的排序性能
     */
    @Test
    public void testLargeDataSorting() {
        log = new TestLogger("大量数据排序性能测试");
        log.start("测试10000个KeyValue的排序性能");
        
        log.step("创建10000个测试数据（100个不同的键）");
        for (int i = 0; i < 10000; i++) {
            String key = "key" + (i % 100); // 100个不同的键
            String value = "value" + i;
            long timestamp = System.currentTimeMillis() + i;
            keyValues.add(new KeyValue(key, value, timestamp, false));
        }
        log.data("数据条数", keyValues.size());
        log.data("不同键数", 100);

        log.step("执行排序");
        long startTime = System.currentTimeMillis();
        Collections.sort(keyValues);
        long endTime = System.currentTimeMillis();
        log.data("排序耗时", (endTime - startTime) + "ms");

        log.step("验证排序结果正确性");
        int errors = 0;
        for (int i = 1; i < keyValues.size(); i++) {
            KeyValue prev = keyValues.get(i - 1);
            KeyValue curr = keyValues.get(i);
            if (prev.compareTo(curr) > 0) {
                errors++;
            }
        }
        log.data("排序错误数", errors);
        
        // 验证排序结果的正确性
        for (int i = 1; i < keyValues.size(); i++) {
            KeyValue prev = keyValues.get(i - 1);
            KeyValue curr = keyValues.get(i);
            assertTrue("排序结果不正确: " + prev + " should <= " + curr, 
                prev.compareTo(curr) <= 0);
        }
        log.assertSuccess("10000个KeyValue排序正确");
        log.pass();
    }
}