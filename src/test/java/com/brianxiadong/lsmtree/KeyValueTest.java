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

    @Before
    public void setUp() {
        keyValues = new ArrayList<>();
    }

    /**
     * 测试基本的KeyValue创建和属性访问
     */
    @Test
    public void testBasicKeyValueCreation() {
        KeyValue kv = new KeyValue("key1", "value1");
        
        assertEquals("key1", kv.getKey());
        assertEquals("value1", kv.getValue());
        assertFalse(kv.isDeleted());
        assertTrue(kv.getTimestamp() > 0);
    }

    /**
     * 测试墓碑标记的创建
     */
    @Test
    public void testTombstoneCreation() {
        KeyValue tombstone = KeyValue.createTombstone("key1");
        
        assertEquals("key1", tombstone.getKey());
        assertNull(tombstone.getValue());
        assertTrue(tombstone.isDeleted());
        assertTrue(tombstone.getTimestamp() > 0);
    }

    /**
     * 测试相同键的排序行为（按时间戳降序）
     */
    @Test
    public void testSortingBySameKey() throws InterruptedException {
        // 创建相同键但不同时间戳的KeyValue
        KeyValue kv1 = new KeyValue("key1", "value1", 1000, false);
        Thread.sleep(1); // 确保时间戳不同
        KeyValue kv2 = new KeyValue("key1", "value2", 2000, false);
        KeyValue kv3 = new KeyValue("key1", "value3", 3000, false);

        keyValues.add(kv1);
        keyValues.add(kv3);
        keyValues.add(kv2);

        Collections.sort(keyValues);

        // 验证排序结果：相同键按时间戳降序排列（新的在前）
        assertEquals(3000, keyValues.get(0).getTimestamp());
        assertEquals(2000, keyValues.get(1).getTimestamp());
        assertEquals(1000, keyValues.get(2).getTimestamp());
    }

    /**
     * 测试不同键的排序行为（按键字典序）
     */
    @Test
    public void testSortingByDifferentKeys() {
        KeyValue kv1 = new KeyValue("c", "value1", 1000, false);
        KeyValue kv2 = new KeyValue("a", "value2", 2000, false);
        KeyValue kv3 = new KeyValue("b", "value3", 3000, false);

        keyValues.add(kv1);
        keyValues.add(kv2);
        keyValues.add(kv3);

        Collections.sort(keyValues);

        // 验证排序结果：不同键按字典序排列
        assertEquals("a", keyValues.get(0).getKey());
        assertEquals("b", keyValues.get(1).getKey());
        assertEquals("c", keyValues.get(2).getKey());
    }

    /**
     * 测试混合排序：键优先，时间戳次之
     */
    @Test
    public void testMixedSorting() {
        // 创建混合的KeyValue列表
        KeyValue kv1 = new KeyValue("b", "value1", 1000, false);
        KeyValue kv2 = new KeyValue("a", "value2", 3000, false);
        KeyValue kv3 = new KeyValue("a", "value3", 2000, false);
        KeyValue kv4 = new KeyValue("c", "value4", 1500, false);

        keyValues.add(kv1);
        keyValues.add(kv2);
        keyValues.add(kv3);
        keyValues.add(kv4);

        Collections.sort(keyValues);

        // 验证排序结果
        assertEquals("a", keyValues.get(0).getKey());
        assertEquals(3000, keyValues.get(0).getTimestamp()); // a键中时间戳最新的
        assertEquals("a", keyValues.get(1).getKey());
        assertEquals(2000, keyValues.get(1).getTimestamp()); // a键中时间戳较旧的
        assertEquals("b", keyValues.get(2).getKey());
        assertEquals("c", keyValues.get(3).getKey());
    }

    /**
     * 测试版本控制场景：同一键的多个版本
     */
    @Test
    public void testVersionControl() {
        String key = "user:123";
        
        // 模拟同一用户数据的多次更新
        KeyValue v1 = new KeyValue(key, "name:Alice", 1000, false);
        KeyValue v2 = new KeyValue(key, "name:Alice,age:25", 2000, false);
        KeyValue v3 = new KeyValue(key, "name:Alice,age:26", 3000, false);

        keyValues.add(v2);
        keyValues.add(v1);
        keyValues.add(v3);

        Collections.sort(keyValues);

        // 最新版本应该在前面
        assertEquals(3000, keyValues.get(0).getTimestamp());
        assertEquals("name:Alice,age:26", keyValues.get(0).getValue());
        
        // 验证历史版本的顺序
        assertEquals(2000, keyValues.get(1).getTimestamp());
        assertEquals(1000, keyValues.get(2).getTimestamp());
    }

    /**
     * 测试墓碑标记在排序中的行为
     */
    @Test
    public void testTombstoneInSorting() {
        String key = "key1";
        
        KeyValue normalValue = new KeyValue(key, "value1", 1000, false);
        KeyValue tombstone = new KeyValue(key, null, 2000, true);
        KeyValue newerValue = new KeyValue(key, "value2", 3000, false);

        keyValues.add(tombstone);
        keyValues.add(normalValue);
        keyValues.add(newerValue);

        Collections.sort(keyValues);

        // 验证排序：最新的值在前，然后是墓碑，最后是旧值
        assertEquals(3000, keyValues.get(0).getTimestamp());
        assertFalse(keyValues.get(0).isDeleted());
        
        assertEquals(2000, keyValues.get(1).getTimestamp());
        assertTrue(keyValues.get(1).isDeleted());
        
        assertEquals(1000, keyValues.get(2).getTimestamp());
        assertFalse(keyValues.get(2).isDeleted());
    }

    /**
     * 测试压缩场景：查找最新有效值
     */
    @Test
    public void testCompactionScenario() {
        String key = "key1";
        
        // 模拟压缩前的数据：包含多个版本和删除标记
        KeyValue v1 = new KeyValue(key, "value1", 1000, false);
        KeyValue delete = new KeyValue(key, null, 2000, true);
        KeyValue v2 = new KeyValue(key, "value2", 3000, false);
        KeyValue v3 = new KeyValue(key, "value3", 4000, false);

        keyValues.add(delete);
        keyValues.add(v1);
        keyValues.add(v3);
        keyValues.add(v2);

        Collections.sort(keyValues);

        // 查找最新的有效值（用于压缩决策）
        KeyValue latestValid = null;
        for (KeyValue kv : keyValues) {
            if (kv.getKey().equals(key)) {
                if (!kv.isDeleted()) {
                    latestValid = kv;
                    break; // 找到最新的非删除值
                }
            }
        }

        assertNotNull(latestValid);
        assertEquals("value3", latestValid.getValue());
        assertEquals(4000, latestValid.getTimestamp());
    }

    /**
     * 测试toString方法
     */
    @Test
    public void testToString() {
        KeyValue kv = new KeyValue("key1", "value1", 12345, false);
        String expected = "KeyValue{key='key1', value='value1', timestamp=12345, deleted=false}";
        assertEquals(expected, kv.toString());

        KeyValue tombstone = new KeyValue("key2", null, 67890, true);
        String expectedTombstone = "KeyValue{key='key2', value='null', timestamp=67890, deleted=true}";
        assertEquals(expectedTombstone, tombstone.toString());
    }

    /**
     * 测试compareTo方法的边界情况
     */
    @Test
    public void testCompareToEdgeCases() {
        KeyValue kv1 = new KeyValue("key1", "value1", 1000, false);
        KeyValue kv2 = new KeyValue("key1", "value2", 1000, false);

        // 相同键和时间戳的情况
        assertEquals(0, kv1.compareTo(kv2));
        assertEquals(0, kv2.compareTo(kv1));

        // 自己与自己比较
        assertEquals(0, kv1.compareTo(kv1));
    }

    /**
     * 测试大量数据的排序性能
     */
    @Test
    public void testLargeDataSorting() {
        // 创建大量测试数据
        for (int i = 0; i < 10000; i++) {
            String key = "key" + (i % 100); // 100个不同的键
            String value = "value" + i;
            long timestamp = System.currentTimeMillis() + i;
            keyValues.add(new KeyValue(key, value, timestamp, false));
        }

        long startTime = System.currentTimeMillis();
        Collections.sort(keyValues);
        long endTime = System.currentTimeMillis();

        // 验证排序结果的正确性
        for (int i = 1; i < keyValues.size(); i++) {
            KeyValue prev = keyValues.get(i - 1);
            KeyValue curr = keyValues.get(i);
            assertTrue("排序结果不正确: " + prev + " should <= " + curr, 
                prev.compareTo(curr) <= 0);
        }

        System.out.println("排序10000个KeyValue耗时: " + (endTime - startTime) + "ms");
    }
}