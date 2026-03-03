# LSM Tree MemTable 的核心结构 SkipList：原理、优势与高并发实现

## 1. 引言

在 LSM Tree 中，MemTable 是前台读写路径的核心组件。它负责接收所有新增写入（Put、Delete）并以有序结构维护最新数据，使系统能够在内存中完成快速写入与低延迟查询。MemTable 的数据结构选型直接影响写入吞吐、写入延迟、范围扫描效率以及高并发访问下的整体性能，是决定 LSM 引擎前台性能表现的关键因素之一。

多种有序结构（如 Red-Black Tree、AVL Tree、B-Tree）都能用于 MemTable，但业界主流 LSM 引擎（RocksDB、LevelDB、X-Engine）普遍采用 **SkipList** 作为 MemTable 的核心结构。其原因在于 SkipList 拥有以下特性：

- 插入、查找、删除均具备 **近似 O(log n)** 的期望时间复杂度
- 极易支持 **无锁或低锁的高并发实现**
- 原生支持 **范围扫描（range scan）**
- 无需复杂平衡逻辑，结构维护简单
- 在写密集型场景下，性能优势明显

本文通过精简而专业的方式阐述 SkipList 的数据结构、核心算法与并发实现机制，并结合典型 LSM 引擎中的使用方式进行说明。

---

## 2. 核心数据结构

SkipList 的本质是 “**多层索引的有序链表**”。每个节点都属于最底层的链表，而更高层级的节点是按照概率抽样得到的索引节点。

为确保技术准确性，本章节参考了 **Java ConcurrentSkipListMap** 的结构设计原则（含 CAS-based lock-free 算法）。

### 2.1 节点结构设计（参考 OpenJDK ConcurrentSkipListMap）

```java
/**
 * SkipList 基础节点结构
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L394
 * @param <K> 键类型
 * @param <V> 值类型
 */
static final class Node<K, V> {
    final K key;                    // 节点键值（final 保证不可变性）
    V val;                          // 节点存储的值（实际类型为 V，非 volatile）
    Node<K, V> next;                // 下一节点引用（非 volatile）

    /**
     * 构造函数 - 实际 OpenJDK 实现
     * @param key 键值
     * @param value 存储的值
     * @param next 下一个节点
     */
    Node(K key, V value, Node<K, V> next) {
        this.key = key;
        this.val = value;
        this.next = next;
    }

    /**
     * CAS 更新下一个节点引用 - 实际 OpenJDK 实现
     */
    boolean casNext(Node<K, V> cmp, Node<K, V> val) {
        return NEXT.compareAndSet(this, cmp, val);
    }

    /**
     * CAS 更新值 - 实际 OpenJDK 实现
     */
    boolean casValue(Object cmp, Object val) {
        return VAL.compareAndSet(this, cmp, val);
    }

    /**
     * 检查节点是否被标记删除 - 实际 OpenJDK 实现
     */
    boolean isMarker() {
        return val == this;
    }

    /**
     * 检查节点是否为基节点 - 实际 OpenJDK 实现
     */
    boolean isBaseHeader() {
        return value == BASE_HEADER;
    }

    // VarHandle 常量 - 实际 OpenJDK 实现
    // 已在类级别静态初始化，此处无需重复定义
    private static final Object BASE_HEADER = new Object();
}
```

### 2.2 跳表整体结构（参考 OpenJDK ConcurrentSkipListMap）

```java
/**
 * SkipList 核心类
 */
public class ConcurrentSkipList<K extends Comparable<K>, V> {
    // 头索引节点（指向最高层的第一个索引）
    private volatile HeadIndex<K, V> head;

    // 比较器（支持自定义排序）
    private final Comparator<? super K> comparator;

    // 基础链表头节点
    private final Node<K, V> baseHeader;

    // 随机数生成器（用于决定索引层级）
    private final ThreadLocalRandom random;

    // 配置参数
    private static final int MAX_LEVEL = 32;
    private static final float PROBABILITY = 0.5f;

    /**
     * 构造函数
     */
    public ConcurrentSkipList() {
        this.comparator = null; // 使用自然排序
        this.random = ThreadLocalRandom.current();

        // 初始化基础链表头节点
        this.baseHeader = new Node<>(null, null, null);

        // 初始化头索引节点
        this.head = new HeadIndex<>(baseHeader, null, null, 1);
    }

    /**
     * 带比较器的构造函数
     */
    public ConcurrentSkipList(Comparator<? super K> comparator) {
        this.comparator = comparator;
        this.random = ThreadLocalRandom.current();

        this.baseHeader = new Node<>(null, null, null);
        this.head = new HeadIndex<>(baseHeader, null, null, 1);
    }
}

/**
 * 头索引节点 - 参考 OpenJDK ConcurrentSkipListMap
 */
static final class HeadIndex<K,V> extends Index<K,V> {
    final int level; // 索引层级

    HeadIndex(Node<K,V> node, Index<K,V> down, Index<K,V> right, int level) {
        super(node, down, right);
        this.level = level;
    }
}
```

---

## 3. 算法原理详解

本章深入分析 SkipList 的核心算法实现，包括查找、插入、删除和范围查询操作，所有算法均采用 OpenJDK ConcurrentSkipListMap 的实现模式，确保线程安全和性能优化。

### 3.1 查找算法（参考 OpenJDK ConcurrentSkipListMap）

```java
/**
 * 查找指定键对应的值
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L937
 * @param key 要查找的键
 * @return 对应的值，如果不存在返回 null
 */
public V get(K key) {
    // 参数检查 - 实际 OpenJDK 实现
    if (key == null) throw new NullPointerException();

    // 使用索引进行快速查找 - 实际 OpenJDK 实现模式
    Index<K, V> q = head;
    Index<K, V> r = q.right;
    Node<K, V> n;
    K k;
    int c;

    for (;;) {
        Index<K, V> d;
        // 遍历当前层的索引
        if (r != null && (n = r.node) != null && (k = n.key) != null) {
            // 检查节点有效性 - 实际 OpenJDK 实现
            if ((c = cpr(comparator, key, k)) > 0) {
                q = r;
                r = r.right;
                continue;
            } else if (c == 0) {
                // 找到匹配的索引节点，检查值有效性
                V v = n.value;
                return (v != null) ? v : getUsingFindNode(key);
            }
        }

        // 下降到下一层 - 实际 OpenJDK 实现
        if ((d = q.down) != null) {
            q = d;
            r = d.right;
        } else {
            // 到达基础链表层，开始线性搜索
            break;
        }
    }

    // 在基础链表层线性搜索 - 实际 OpenJDK 实现模式
    n = q.node.next;
    while (n != null) {
        if ((k = n.key) != null) {
            if ((c = cpr(comparator, key, k)) == 0) {
                V v = n.value;
                return (v != null) ? v : getUsingFindNode(key);
            } else if (c < 0) {
                break;
            }
        }
        n = n.next;
    }

    return null;
}

/**
 * 辅助方法：通过 findNode 进行精确查找 - 参考 OpenJDK 实现模式
 */
private V getUsingFindNode(K key) {
    Node<K, V> n = findNode(key);
    if (n == null) return null;
    V v = n.value;
    return (v != null) ? v : null;
}

/**
 * 比较器辅助方法
 */
private int cpr(Comparator<? super K> cmp, K x, K y) {
    return (cmp != null) ? cmp.compare(x, y) : ((Comparable<K>)x).compareTo(y);
}
```

**查找过程可视化**（查找 key = 35）：

```text
                           SkipList 多层索引结构
概率性层级分布 | Java ConcurrentSkipListMap 实现限制: 32层 | 生产常用: 12-16层

Level 3: HEAD ─────────────────────────────────────────────────────────────→ NULL
         │
         ▼
Level 2: HEAD ───────────────────────────→ [30] ───────────────────────────→ NULL
         │                                  │
         ▼                                  ▼
Level 1: HEAD ──────→ [10] ──────────────→ [30] ──────────────→ [50] ──────→ NULL
         │              │                   │                     │
         ▼              ▼                   ▼                     ▼
Level 0: HEAD → [5] → [10] → [15] → [20] → [30] → [35] → [40] → [50] → [60] → NULL

节点结构详解:
┌─────────────────────────────────────────────────────────────────────────┐
│                          SkipList 节点结构                               │
│                                                                         │
│  ┌─────────────────┐                                                    │
│  │   节点 [30]      │                                                    │
│  │ ┌─────────────┐ │  forward[3] ──→ NULL                               │
│  │ │    Key: 30  │ │  forward[2] ──→ NULL                               │
│  │ │  Value: ... │ │  forward[1] ──→ [50]                               │
│  │ │ Version: v1 │ │  forward[0] ──→ [35]                               │
│  │ │ Height: 4   │ │                                                    │
│  │ └─────────────┘ │  注：forward[i] 指向第i层的下一个节点                  │
│  └─────────────────┘                                                    │
└─────────────────────────────────────────────────────────────────────────┘
```

**查找过程示例** (查找 key = 35):

1. 从 Level 3 开始: HEAD → NULL (当前层无节点，下降到 Level 2)
2. Level 2: HEAD → [30] → NULL (35 > 30，且节点[30] 的下一个节点是 NULL，因此从节点[30]下降到 Level 1)
3. Level 1: [30] → [50] (35 < 50，因此从节点[30]下降到 Level 0)
4. Level 0: 从[30]开始向右遍历 → [35] (找到目标节点)

**时间复杂度**: O(log n)，空间复杂度: O(n)

### 3.2 插入算法（参考 OpenJDK ConcurrentSkipListMap）

```java
/**
 * 插入键值对
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L1346
 * @param key 键
 * @param value 值
 * @return 之前与 key 关联的值，如果没有则返回 null
 */
public V put(K key, V value) {
    if (value == null) throw new NullPointerException();
    return doPut(key, value, false);
}

/**
 * 插入实现 - 参考 OpenJDK ConcurrentSkipListMap doPut 方法
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L596
 * @param key 键
 * @param value 值
 * @param onlyIfAbsent 是否仅在键不存在时插入
 * @return 之前与 key 关联的值，如果没有则返回 null
 */
private V doPut(K key, V value, boolean onlyIfAbsent) {
    if (key == null) throw new NullPointerException();
    Comparator<? super K> cmp = comparator;
    for (;;) {
        Index<K,V> h; Node<K,V> b;
        VarHandle.acquireFence();  // 内存屏障保证可见性
        int levels = 0;                    // 下降的层级数

        if ((h = head) == null) {          // 初始化头节点
            Node<K,V> base = new Node<K,V>(null, null, null);
            h = new Index<K,V>(base, null, null);
            b = (HEAD.compareAndSet(this, null, h)) ? base : null;
        } else {
            // 遍历索引层级查找插入位置
            for (Index<K,V> q = h, r, d;;) {
                while ((r = q.right) != null) {
                    Node<K,V> p; K k;
                    if ((p = r.node) == null || (k = p.key) == null || p.val == null) {
                        RIGHT.compareAndSet(q, r, r.right);  // 清理无效索引
                    } else if (cpr(cmp, key, k) > 0) {
                        q = r;
                    } else {
                        break;
                    }
                }
                if ((d = q.down) != null) {
                    ++levels;
                    q = d;
                } else {
                    b = q.node;
                    break;
                }
            }
        }

        if (b != null) {
            Node<K,V> z = null;              // 新节点
            for (;;) {                       // 查找基础链表插入点
                Node<K,V> n, p; K k; V v; int c;
                if ((n = b.next) == null) {
                    if (b.key == null) cpr(cmp, key, key);  // 类型检查
                    c = -1;
                } else if ((k = n.key) == null) {
                    break;                   // 无法追加，重试
                } else if ((v = n.val) == null) {
                    unlinkNode(b, n);        // 清理标记删除的节点
                    c = 1;
                } else if ((c = cpr(cmp, key, k)) > 0) {
                    b = n;
                } else if (c == 0 && (onlyIfAbsent || VAL.compareAndSet(n, v, value))) {
                    return v;                // 更新现有值
                }

                if (c < 0 && NEXT.compareAndSet(b, n, p = new Node<K,V>(key, value, n))) {
                    z = p;
                    break;
                }
            }

            if (z != null) {
                // 随机决定是否建立索引 (1/4 概率)
                int lr = ThreadLocalRandom.nextSecondarySeed();
                if ((lr & 0x3) == 0) {
                    int hr = ThreadLocalRandom.nextSecondarySeed();
                    long rnd = ((long)hr << 32) | ((long)lr & 0xffffffffL);
                    int skips = levels;      // 需要跳过的层级数
                    Index<K,V> x = null;

                    // 创建最多 62 层索引
                    for (;;) {
                        x = new Index<K,V>(z, x, null);
                        if (rnd >= 0L || --skips < 0) break;
                        else rnd <<= 1;
                    }

                    // 添加索引并可能扩展头节点层级
                    if (addIndices(h, skips, x, cmp) && skips < 0 && head == h) {
                        Index<K,V> hx = new Index<K,V>(z, x, null);
                        Index<K,V> nh = new Index<K,V>(h.node, h, hx);
                        HEAD.compareAndSet(this, h, nh);
                    }

                    if (z.val == null)       // 插入期间节点被删除
                        findPredecessor(key, cmp);  // 清理
                }
                addCount(1L);
                return null;
            }
        }
    }
}

/**
 * 查找前驱节点 - 核心搜索算法
 */
private Node<K, V> findPredecessor(K key) {
    Index<K, V> q = head;
    Index<K, V> r = q.right;

    for (;;) {
        if (r != null) {
            Node<K, V> n = r.node;
            K k = n.key;

            // 检查节点有效性
            if (n.value == null) {
                q.casRight(r, r.right);
                r = q.right;
                continue;
            }

            if (cpr(comparator, key, k) > 0) {
                q = r;
                r = r.right;
                continue;
            }
        }

        Index<K, V> d = q.down;
        if (d != null) {
            q = d;
            r = d.right;
        } else {
            return q.node;
        }
    }
}

/**
 * 建立索引
 */
private void addIndex(Node<K, V> z, int level) {
    HeadIndex<K, V> h = head;
    int max = h.level;

    if (level <= max) {
        // 在当前层级范围内建立索引
        Index<K, V> idx = null;
        for (int i = 1; i <= level; i++) {
            idx = new Index<>(z, idx, null);
        }
        addIndex(idx, h, level);
    } else {
        // 需要扩展头索引层级
        level = max + 1;
        Index<K, V>[] idxs = (Index<K, V>[]) new Index<?, ?>[level + 1];
        Index<K, V> idx = null;

        for (int i = 1; i <= level; i++) {
            idx = new Index<>(z, idx, null);
            idxs[i] = idx;
        }

        for (;;) {
            h = head;
            int oldLevel = h.level;
            if (level <= oldLevel) break; // 其他线程已经扩展

            HeadIndex<K, V> newh = h;
            Node<K, V> oldbase = h.node;

            for (int i = oldLevel + 1; i <= level; i++) {
                newh = new HeadIndex<>(oldbase, newh, idxs[i], i);
            }

            if (casHead(h, newh)) {
                h = newh;
                idx = idxs[level = oldLevel];
                break;
            }
        }

        addIndex(idxs[level], h, level);
    }
}

/**
 * 生成随机层级
 */
private int randomLevel() {
    int level = 1;
    while (level < MAX_LEVEL && random.nextFloat() < PROBABILITY) {
        level++;
    }
    return level;
}
```

### 3.3 删除算法（参考 OpenJDK ConcurrentSkipListMap）

```java
/**
 * 删除指定键对应的节点 - 参考 OpenJDK ConcurrentSkipListMap doRemove 方法
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L758
 * @param key 要删除的键
 * @return 与 key 关联的之前的值，如果没有映射关系则返回 null
 */
public V remove(K key) {
    return doRemove(key, null);
}

/**
 * 实际删除实现 - 参考 OpenJDK ConcurrentSkipListMap doRemove 方法
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L758
 * @param key 要删除的键
 * @param value 如果非 null，则仅在当前映射值等于给定值时删除
 * @return 与 key 关联的之前的值，如果没有映射关系则返回 null
 */
final V doRemove(Object key, Object value) {
    if (key == null)
        throw new NullPointerException();
    Comparator<? super K> cmp = comparator;
    V result = null;
    Node<K,V> b;
    outer: while ((b = findPredecessor(key, cmp)) != null &&
                  result == null) {
        for (;;) {
            Node<K,V> n; K k; V v; int c;
            if ((n = b.next) == null)
                break outer;
            else if ((k = n.key) == null)
                break;
            else if ((v = n.val) == null)
                unlinkNode(b, n);
            else if ((c = cpr(cmp, key, k)) > 0)
                b = n;
            else if (c < 0)
                break outer;
            else if (value != null && !value.equals(v))
                break outer;
            else if (VAL.compareAndSet(n, v, null)) {
                result = v;
                unlinkNode(b, n);
                break; // loop to clean up
            }
        }
    }
    if (result != null) {
        tryReduceLevel();
        addCount(-1L);
    }
    return result;
}

/**
 * 尝试减少索引层级 - 参考 OpenJDK ConcurrentSkipListMap tryReduceLevel 方法
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L808
 */
private void tryReduceLevel() {
    Index<K,V> h, d, e;
    if ((h = head) != null && h.right == null &&
        (d = h.down) != null && d.right == null &&
        (e = d.down) != null && e.right == null &&
        HEAD.compareAndSet(this, h, d) &&
        h.right != null)   // recheck
        HEAD.compareAndSet(this, d, h);  // try to backout
}

/**
 * 更新元素计数器 - 参考 OpenJDK ConcurrentSkipListMap addCount 方法
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L434
 * @param x 要增加的数量（可为负值）
 */
private void addCount(long x) {
    // 在实际的 ConcurrentSkipListMap 中，addCount 使用更复杂的并发计数机制
    // 这里简化为原子操作，实际实现应考虑并发性能
    if (x != 0) {
        for (;;) {
            long c = count;
            long next = c + x;
            if (COUNT.compareAndSet(this, c, next))
                break;
        }
    }
}

/**
 * 清理标记删除的节点 - 参考 OpenJDK 的清理机制
 */
private void cleanUp() {
    // 在实际的 ConcurrentSkipListMap 中，清理操作是惰性的
    // 在查找和插入过程中会顺便清理已标记删除的节点

    // 这里可以添加定期清理的逻辑，但通常不需要显式调用
    // 因为并发操作会自动帮助清理
}

/**
 * 检查节点是否被标记删除 - 实际 OpenJDK 实现
 */
boolean isMarker() {
    return value == this;
}
```

### 3.4 范围查询

```java
/**
 * 范围查询 - 参考 OpenJDK ConcurrentSkipListMap 的 subMap 实现模式
 * @param fromKey 起始键（包含）
 * @param toKey 结束键（包含）
 * @return 范围内的键值对列表
 */
public List<Map.Entry<K, V>> rangeQuery(K fromKey, K toKey) {
    List<Map.Entry<K, V>> result = new ArrayList<>();

    // 使用 findPredecessor 查找前驱节点，保持与 OpenJDK 一致性
    Node<K, V> n = findPredecessor(fromKey);
    Node<K, V> last = n;

    // 遍历底层链表收集范围内的节点
    while ((n = n.next) != null) {
        K k = n.key;
        if (k == null) {
            // 遇到标记删除的节点，继续遍历
            continue;
        }

        // 比较键值，确定是否在范围内
        int c = cpr(comparator, fromKey, k);
        if (c > 0) {
            // 当前节点键小于起始键，继续遍历
            continue;
        }

        // 检查是否超出范围
        if (cpr(comparator, k, toKey) > 0) {
            break;
        }

        V v = n.value;
        if (v != null) {
            // 只收集有效节点（未被标记删除）
            result.add(new AbstractMap.SimpleEntry<>(k, v));
        }
    }

    return result;
}

/**
 * 查找小于等于指定键的最后一个节点 - 参考 OpenJDK findPredecessor
 * @param key 查找键
 * @return 前驱节点
 */
private Node<K, V> findPredecessor(K key) {
    if (key == null) {
        throw new NullPointerException();
    }

    // 从最高层索引开始搜索
    Index<K, V> q = head;
    Index<K, V> r = q.right;

    for (;;) {
        if (r != null) {
            Node<K, V> n = r.node;
            K k = n.key;

            if (n.value == null) {
                // 遇到标记删除的节点，帮助清理并重试
                if (!q.unlink(r)) {
                    break; // 重试
                }
                r = q.right;
                continue;
            }

            if (cpr(comparator, key, k) > 0) {
                // 当前索引节点键小于目标键，继续向右搜索
                q = r;
                r = r.right;
                continue;
            }
        }

        // 向下搜索
        Index<K, V> d = q.down;
        if (d != null) {
            q = d;
            r = d.right;
        } else {
            break;
        }
    }

    return q.node;
}

/**
 * 检查是否包含指定键
 * @param key 要检查的键
 * @return 如果包含返回 true，否则返回 false
 */
public boolean containsKey(K key) {
    return get(key) != null;
}

/**
 * 获取跳表中的元素数量
 * @return 元素数量
 */
public int size() {
    return size.get();
}

/**
 * 检查跳表是否为空
 * @return 如果为空返回 true，否则返回 false
 */
public boolean isEmpty() {
    return size.get() == 0;
}

/**
 * 清空跳表中的所有元素
 */
public void clear() {
    // 重置所有层级的前向指针
    for (int i = 0; i < maxAllowedLevel; i++) {
        header.forward[i] = null;
    }

    // 重置状态变量
    maxLevel = 1;
    size.set(0);
    tail = null;
    modCount.incrementAndGet();
}

/**
 * 获取第一个键
 * @return 第一个键，如果跳表为空返回 null
 */
public K firstKey() {
    if (header.forward[0] == null) {
        return null;
    }
    return header.forward[0].key;
}

/**
 * 获取最后一个键
 * @return 最后一个键，如果跳表为空返回 null
 */
public K lastKey() {
    if (tail == null) {
        return null;
    }
    return tail.key;
}

/**
 * 获取第一个键值对
 * @return 第一个键值对，如果跳表为空返回 null
 */
public Map.Entry<K, V> firstEntry() {
    if (header.forward[0] == null) {
        return null;
    }
    return new AbstractMap.SimpleEntry<>(header.forward[0].key, header.forward[0].value);
}

/**
 * 获取最后一个键值对
 * @return 最后一个键值对，如果跳表为空返回 null
 */
public Map.Entry<K, V> lastEntry() {
    if (tail == null) {
        return null;
    }
    return new AbstractMap.SimpleEntry<>(tail.key, tail.value);
}
```

---

## 4. 关键参数与配置

本章探讨 SkipList 的关键配置参数及其对性能的影响，提供详细的调优建议和实践指导，帮助开发者根据具体应用场景优化 SkipList 性能。

### 4.1 层级控制参数

```java
// 推荐配置（基于实践经验）
public class SkipListConfig {
    // 最大层级数（通常设置为 32-64）：限制跳表的最大高度，防止内存过度使用
    public static final int MAX_LEVEL = 32;

    // 层级增长概率（通常设置为 0.5-0.75）：控制节点建立索引的概率，影响空间和时间复杂度平衡
    public static final float PROBABILITY = 0.5f;

    // 初始内存分配大小：预分配节点数量，减少动态内存分配开销
    public static final int INITIAL_CAPACITY = 1024;

    // 并发控制参数：根据CPU核心数设置合适的并发粒度
    public static final int CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();
}
```

### 4.2 性能优化参数

| 参数           | 默认值     | 说明                                                 | 调优建议                                                       |
| -------------- | ---------- | ---------------------------------------------------- | -------------------------------------------------------------- |
| **最大层级**   | 32         | 限制跳表的最大高度，防止极端情况下的内存浪费         | 根据数据规模调整：小数据集(16-32)，大数据集(32-64)             |
| **增长概率**   | 0.5        | 节点层级增长概率，直接影响空间复杂度和查询性能的平衡 | 0.5-0.75：较低概率节省空间，较高概率提升查询速度               |
| **内存池大小** | 1024       | 节点预分配数量，减少 GC 压力和内存分配开销           | 根据并发程度调整：低并发(512-1024)，高并发(2048-4096)          |
| **并发级别**   | CPU 核心数 | 并发控制粒度，影响锁竞争和并行处理能力               | 根据实际并发需求调整：CPU 密集型(核心数)，IO 密集型(2× 核心数) |
| **缓存行大小** | 64         | CPU 缓存行大小，用于内存对齐优化                     | 固定值 64 字节，用于避免伪共享和优化缓存命中率                 |
| **重试次数**   | 1024       | CAS 操作最大重试次数，防止活锁                       | 根据冲突概率调整：低冲突(256-512)，高冲突(1024-2048)           |

---

## 5. 性能分析与 LSM Tree 适用性

本章从理论角度分析 SkipList 的时间复杂度、空间复杂度和内存访问模式，通过与其他数据结构的对比，全面评估 SkipList 的性能特征和在 LSM Tree 中的适用场景。

### 5.1 时间复杂度

| 操作         | 平均情况     | 最坏情况 | 说明               |
| ------------ | ------------ | -------- | ------------------ |
| **查找**     | O(log n)     | O(n)     | 基于概率的平衡     |
| **插入**     | O(log n)     | O(n)     | 包含查找和指针更新 |
| **删除**     | O(log n)     | O(n)     | 包含查找和指针更新 |
| **范围查询** | O(log n + k) | O(n + k) | k 为范围内元素数量 |

### 5.2 空间复杂度

- **期望空间使用**：O(n)
- **实际空间开销**：每个节点平均需要 1/(1-p) 个指针（其中 p 为层级增长概率）
- **内存计算示例**：当 p=0.5 时，平均每个节点需要 2 个指针；当 p=0.75 时，平均需要 4 个指针
- **与平衡树对比**：SkipList 通常比平衡树使用更多指针（平衡树每个节点通常需要 2-3 个指针），但实现更简单且不需要复杂的再平衡操作

### 5.3 内存访问模式

优点：

- 顺序访问模式：范围查询时具有良好的缓存局部性
- 预取友好：指针数组连续存储，便于硬件预取
- 内存对齐：节点结构可以优化对齐以提高访问速度

缺点：

- 指针开销：每个节点需要存储多个指针
- 内存碎片：动态分配可能产生碎片
- 随机访问：非顺序键的访问模式可能较差

### 5.4 为什么 LSM MemTable 更适合 SkipList？

与红黑树、AVL 和其它平衡结构相比，SkipList 在 LSM Tree MemTable 场景中具有显著优势：

| 特性           | SkipList           | 红黑树/AVL                 |
| -------------- | ------------------ | -------------------------- |
| **插入复杂度** | O(log n)           | O(log n)                   |
| **实现复杂度** | 简单               | 较复杂                     |
| **并发实现**   | CAS 友好，无需旋转 | 难以实现无锁，通常需全局锁 |
| **范围扫描**   | O(n) 顺链表即可    | 需中序遍历                 |
| **写密集优化** | 非常友好           | 容易产生锁冲突             |
| **内存使用**   | 指针开销略高       | 节点结构更紧凑             |
| **维护成本**   | 无需再平衡         | 需要复杂的旋转操作         |

LSM Tree 的典型特点使其特别适合使用 SkipList：

- **写多读少**：MemTable 主要用于接收写入操作，SkipList 的无锁并发写入性能优异
- **并发要求高**：MemTable 必须支持高并发写入，SkipList 的 CAS 操作避免了锁竞争
- **数据规模适中**：Flush 前 MemTable 通常不会过大（百万级别），SkipList 的性能优势明显
- **范围查询需求**：Compaction 和查询时经常需要范围扫描，SkipList 的链表结构天然支持
- **简单可靠**：不需要复杂的平衡逻辑，减少了实现和维护的复杂性

SkipList 正好契合 LSM Tree MemTable 的这些核心需求，这也是为什么业界主流 LSM 引擎（RocksDB、LevelDB、X-Engine 等）普遍采用 SkipList 作为 MemTable 的核心数据结构。

基于以上性能分析，我们将在下一章深入探讨 LSM Tree 架构中选择 SkipList 作为 MemTable 核心数据结构的技术决策依据和深度技术分析。

---

## 6. LSM Tree 选择 SkipList 的深度技术分析

本章深入探讨 LSM Tree 架构中选择 SkipList 作为 MemTable 核心数据结构的技术决策依据，从系统架构、性能特征、工程实践等多个维度进行深度分析。

### 6.1 架构层面的技术权衡

#### 6.1.1 写优化 vs 读优化

LSM Tree 的核心设计哲学是**写优化**，而 SkipList 完美契合这一设计目标：

- **写入友好性**：SkipList 的插入操作只需要局部修改，不需要全局再平衡
- **无锁并发**：CAS 操作天然支持高并发写入，避免锁竞争瓶颈
- **内存屏障控制**：精细化的内存屏障使用确保线程安全的同时最小化性能开销

#### 6.1.2 内存管理特性

```java
// LSM Tree MemTable 的内存管理需求与 SkipList 的匹配度分析
public class MemTableMemoryAnalysis {

    // 1. 动态内存分配模式匹配
    // SkipList 的节点动态分配模式与 MemTable 的写入模式高度匹配
    // 每个写入操作对应一个或多个节点的分配，无需预分配大块内存

    // 2. 内存回收效率
    // 删除操作通过标记-清除模式，延迟实际内存回收
    // 与 LSM Tree 的 Compaction 机制协同工作，避免频繁 GC

    // 3. 内存局部性优化
    // 范围查询时具有良好的空间局部性，提高缓存命中率
    // 与 LSM Tree 的 Scan 操作模式高度契合
}
```

### 6.2 性能特征深度分析

#### 6.2.1 写入性能优势

SkipList 在写入性能方面的优势来源于其概率性结构：

- **平均情况优化**：期望 O(log n) 时间复杂度，实际性能稳定
- **避免最坏情况**：通过概率分布避免极端性能退化
- **并发扩展性**：写入操作之间冲突极少，支持线性扩展

#### 6.2.2 读取性能特征

虽然 SkipList 以写入优化著称，但其读取性能同样优秀：

- **范围查询优势**：链表结构天然支持高效的范围扫描
- **缓存友好性**：连续内存访问模式提高缓存利用率
- **预测性能**：时间复杂度分布集中，性能可预测性强

### 6.3 与 LSM Tree 架构的协同效应

#### 6.3.1 与 Compaction 机制的协同

```java
// SkipList 与 LSM Compaction 的协同工作模式
public class CompactionSynergy {

    // 1. Immutable MemTable 转换
    // SkipList 支持快速转换为不可变状态，便于后台 Compaction
    // 通过简单的头指针切换实现状态转换

    // 2. 迭代器性能
    // 提供高效的有序迭代器，支持全量数据扫描
    // 与 Compaction 的归并排序需求完美匹配

    // 3. 内存使用效率
    // 在 Compaction 期间保持内存使用稳定
    // 避免内存峰值对系统稳定性的影响
}
```

#### 6.3.2 故障恢复支持

SkipList 为 LSM Tree 提供了优秀的故障恢复特性：

- **状态一致性**：通过原子操作保证数据结构的一致性
- **恢复效率**：重启后可以快速重建内存状态
- **日志友好性**：操作日志与数据结构变更模式匹配

### 6.4 工程实践考量

#### 6.4.1 实现复杂性评估

与其他有序数据结构相比，SkipList 的实现复杂性显著较低：

- **代码可维护性**：算法逻辑清晰，易于理解和维护
- **调试友好性**：结构可视化程度高，便于问题排查
- **测试覆盖性**：边界条件明确，测试用例设计简单

#### 6.4.2 生产环境验证

业界主流数据库系统的实践验证了 SkipList 在 LSM Tree 中的优越性：

- **RocksDB**：基于 SkipList 的 MemTable 实现，支撑 Facebook 海量数据存储
- **LevelDB**：Google 的经典实现，证明 SkipList 的稳定性和性能
- **Cassandra**：在分布式环境中广泛应用 SkipList-based MemTable
- **ScyllaDB**：高性能分布式数据库，深度优化 SkipList 实现

### 6.5 技术决策框架

选择 SkipList 作为 LSM MemTable 的技术决策应基于以下考量：

1. **工作负载特征**：写密集型、中等规模数据量、需要范围查询
2. **并发需求**：高并发写入、多核处理器环境
3. **内存约束**：合理的内存使用效率要求
4. **维护成本**：团队技术能力和维护复杂度接受度
5. **生态兼容性**：与现有基础设施和工具的兼容性

SkipList 在这些维度上表现出色，使其成为 LSM Tree MemTable 的理想选择。

基于以上深度技术分析，我们将在下一章具体探讨 SkipList 在 LSM Tree 存储引擎中的实际应用和优化实践。

---

## 7. MemTable 实现细节与优化

本章探讨 SkipList 在 LSM Tree 存储引擎中的具体应用，重点分析其在 MemTable 中的核心作用和各种性能优化实践，包括缓存优化和搜索优化技术。

### 7.1 ConcurrentSkipListMap 实现原理

OpenJDK 的 `ConcurrentSkipListMap` 采用了一种基于 CAS（Compare-And-Swap）的无锁算法，其主要特点包括：

#### 7.1.1 核心设计思想

```java
// 参考 OpenJDK ConcurrentSkipListMap 的核心设计
public class ConcurrentSkipListMap<K,V> extends AbstractMap<K,V>
    implements ConcurrentNavigableMap<K,V>, Cloneable, Serializable {

    // 头节点，指向最高层的第一个节点
    private transient volatile HeadIndex<K,V> head;

    // 比较器
    final Comparator<? super K> comparator;

    // 键值集合的视图
    private transient KeySet<K> keySet;
    private transient Values<V> values;
    private transient EntrySet<K,V> entrySet;
}
```

#### 7.1.2 索引节点结构

```java
/**
 * 索引节点结构
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L540
 */
static class Index<K,V> {
    final Node<K,V> node;      // 基础节点引用
    final Index<K,V> down;     // 下一层索引
    volatile Index<K,V> right; // 同一层的下一个索引

    /**
     * 构造函数 - 实际 OpenJDK 实现
     */
    Index(Node<K,V> node, Index<K,V> down, Index<K,V> right) {
        this.node = node;
        this.down = down;
        this.right = right;
    }

    /**
     * CAS 更新右指针 - 实际 OpenJDK 实现
     */
    final boolean casRight(Index<K,V> cmp, Index<K,V> val) {
        return RIGHT.compareAndSet(this, cmp, val);
    }

    /**
     * 解除索引链接 - 实际 OpenJDK unlink 实现
     */
    final boolean unlink(Index<K,V> succ) {
        return node.value != null && casRight(succ, succ.right);
    }

    // VarHandle 常量 - 实际 OpenJDK 实现
    // 已在类级别静态初始化，此处无需重复定义
}

/**
 * 头索引节点
 * 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L614
 */
static final class HeadIndex<K,V> extends Index<K,V> {
    final int level; // 索引层级

    /**
     * 构造函数 - 实际 OpenJDK 实现
     */
    HeadIndex(Node<K,V> node, Index<K,V> down, Index<K,V> right, int level) {
        super(node, down, right);
        this.level = level;
    }
}
```

### 7.2 并发控制策略

#### 7.2.1 CAS 操作实现

```java
/**
 * 基于 ConcurrentSkipListMap 的并发插入策略
 */
public V put(K key, V value) {
    // 参数检查
    if (value == null) throw new NullPointerException();

    // 查找插入位置
    Node<K,V> n = findPredecessor(key);
    Node<K,V> next = n.next;

    // 检查键是否已存在
    if (next != null) {
        K k = next.key;
        if (k != null && k.equals(key)) {
            // 更新现有值
            V v = next.value;
            if (v != null && casValue(next, v, value)) {
                return v;
            }
        }
    }

    // 创建新节点并插入
    Node<K,V> newNode = new Node<K,V>(key, value, next);
    if (n.casNext(next, newNode)) {
        // 随机决定是否建立索引
        int rnd = ThreadLocalRandom.current().nextInt();
        if ((rnd & 0x80000001) == 0) { // 测试最高和最低位
            int level = 1;
            while (((rnd >>>= 1) & 1) != 0) level++;
            if (level > (h.level + 1)) level = h.level + 1;

            // 建立索引
            if (level <= maxLevel) {
                Index<K,V> idx = null;
                for (int i = 1; i <= level; i++)
                    idx = new Index<K,V>(newNode, idx, null);
                addIndex(idx, h, level);
            }
        }
        return null;
    }
}

/**
 * 查找前驱节点 - 核心搜索算法
 */
private Node<K,V> findPredecessor(K key) {
    Index<K,V> q = head;
    Index<K,V> r = q.right;
    Node<K,V> d;

    for (;;) {
        if (r != null) {
            Node<K,V> n = r.node;
            K k = n.key;
            if (k != null && cpr(comparator, key, k) > 0) {
                q = r;
                r = r.right;
                continue;
            }
        }

        if ((d = q.down) != null) {
            q = d;
            r = d.right;
        } else {
            return q.node;
        }
    }
}
```

#### 7.2.2 内存屏障与可见性

内存屏障（Memory Barrier）是现代多核处理器架构中的关键同步机制，用于确保内存操作的顺序性和可见性。在并发编程中，内存屏障解决了以下核心问题：

##### 7.2.2.1 内存屏障的基本原理

1. **指令重排序问题**：现代处理器和编译器为了优化性能，可能会对指令进行重排序。内存屏障阻止特定类型的内存操作跨越屏障进行重排序。
2. **缓存一致性**：多核系统中，每个 CPU 核心有自己的缓存。内存屏障确保当一个核心修改了共享数据后，其他核心能够看到最新的值。
3. **内存可见性**：确保一个线程对共享变量的修改能够及时对其他线程可见。

##### 7.2.2.2 Java 内存模型中的内存屏障

Java 内存模型（JMM）定义了以下四种内存屏障：

1. **LoadLoad 屏障**：确保 Load1 操作在 Load2 操作之前完成
2. **StoreStore 屏障**：确保 Store1 操作在 Store2 操作之前完成
3. **LoadStore 屏障**：确保 Load 操作在 Store 操作之前完成
4. **StoreLoad 屏障**：确保 Store 操作在 Load 操作之前完成（最强大的屏障）

##### 7.2.2.3 在 ConcurrentSkipListMap 中的应用

OpenJDK ConcurrentSkipListMap 使用显式内存屏障来精确控制内存语义：

```java
// 使用 VarHandle 提供的内存屏障保证可见性 - 参考 OpenJDK 实际实现
// 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L3427
private static final VarHandle NEXT;
private static final VarHandle VAL;
private static final VarHandle RIGHT;

static {
    try {
        MethodHandles.Lookup l = MethodHandles.lookup();
        NEXT = l.findVarHandle(Node.class, "next", Node.class);
        VAL = l.findVarHandle(Node.class, "val", Object.class);
        RIGHT = l.findVarHandle(Index.class, "right", Index.class);
    } catch (Exception e) {
        throw new Error(e);
    }
}

// 显式内存屏障使用 - 参考 OpenJDK 实际实现
// 源码位置: https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java#L401
final Node<K,V> baseHead() {
    Index<K,V> h;
    VarHandle.acquireFence();  // 获取屏障确保读取最新值
    return ((h = head) == null) ? null : h.node;
}

// CAS 操作提供精确的内存语义控制
final boolean casValue(Node<K,V> cmp, V val) {
    return VAL.compareAndSet(this, cmp, val);
}

final boolean casNext(Node<K,V> cmp, Node<K,V> val) {
    return NEXT.compareAndSet(this, cmp, val);
}

final boolean casRight(Index<K,V> cmp, Index<K,V> val) {
    return RIGHT.compareAndSet(this, cmp, val);
}
```

OpenJDK 使用三种显式内存屏障：

1. **acquireFence()** - 在关键读取操作前使用，确保读取最新值
2. **releaseFence()** - 在写入操作后使用，确保写入对其他线程可见
3. **fullFence()** - 最强的内存屏障，确保前后操作都不会重排序

##### 7.2.2.4 volatile 关键字的内存屏障语义

在 Node 类中使用的 `volatile` 修饰符自动插入内存屏障：

```java
static final class Node<K, V> {
    final K key;                    // 节点键值
    volatile V value;               // 节点存储的值（volatile 保证可见性）
    volatile Node<K, V> next;       // 下一节点引用

    // volatile 变量的写操作：
    // - 之前插入 StoreStore 屏障
    // - 之后插入 StoreLoad 屏障

    // volatile 变量的读操作：
    // - 之后插入 LoadLoad 和 LoadStore 屏障
}
```

##### 7.2.2.5 内存屏障的实际效果

1. **防止指令重排序**：确保关键操作（如指针更新）按预期顺序执行
2. **保证可见性**：确保一个线程的修改对其他线程立即可见
3. **维持数据一致性**：防止出现部分更新的不一致状态

##### 7.2.2.6 性能考虑

虽然内存屏障提供了正确的同步语义，但也会带来性能开销：

- 阻止编译器和处理器的优化
- 增加缓存同步的开销
- 可能引起流水线停顿

因此，ConcurrentSkipListMap 的设计中只在必要时使用内存屏障，通过精细化的并发控制来平衡正确性和性能。

---

## 8. 在 LSM Tree 中的应用

本章探讨 SkipList 在我们的 LSM Tree 教学项目中的具体应用，重点分析其在 MemTable 中的核心作用和各种性能优化实践，包括缓存优化和搜索优化技术。

### 8.1 MemTable 中的核心作用

```java
/**
 * LSM Tree 中的 MemTable 实现 - 基于实际项目代码
 */
public class MemTable {
    private final ConcurrentSkipListMap<String, KeyValue> data;
    private final int maxSize;
    private volatile int currentSize;

    public MemTable(int maxSize) {
        this.data = new ConcurrentSkipListMap<>();
        this.maxSize = maxSize;
        this.currentSize = 0;
    }

    /**
     * 写入操作 - 实际项目实现
     */
    public void put(String key, String value) {
        KeyValue kv = new KeyValue(key, value);
        KeyValue oldValue = data.put(key, kv);
        if (oldValue == null) {
            currentSize++;
        }
    }

    /**
     * 删除操作 - 实际项目实现（插入删除标记）
     */
    public void delete(String key) {
        KeyValue tombstone = KeyValue.createTombstone(key);
        KeyValue oldValue = data.put(key, tombstone);
        if (oldValue == null) {
            currentSize++;
        }
    }

    /**
     * 查询操作 - 实际项目实现
     */
    public String get(String key) {
        KeyValue kv = data.get(key);
        if (kv == null || kv.isDeleted()) {
            return null;
        }
        return kv.getValue();
    }

    /**
     * 范围查询 - 实际项目实现
     * 
     * 边界条件说明：
     * - startKey 为 null: 查询从最小键开始
     * - endKey 为 null: 查询到最大键结束  
     * - includeStart = true: 包含起始键
     * - includeEnd = true: 包含结束键
     * - 自动过滤已删除的键（tombstone）
     * 
     * 示例：
     * getRange("a", "z", true, false) - 查询 [a, z) 范围内的键
     * getRange(null, "m", false, true) - 查询 (-∞, m] 范围内的键
     * getRange("k", null, true, false) - 查询 [k, +∞) 范围内的键
     */
    public List<KeyValue> getRange(String startKey, String endKey, 
                                  boolean includeStart, boolean includeEnd) {
        ConcurrentSkipListMap<String, KeyValue> m = this.data;
        List<KeyValue> res = new ArrayList<>();
        
        if (startKey == null && endKey == null) {
            // 查询所有键值对
            for (KeyValue kv : m.values()) if (!kv.isDeleted()) res.add(kv);
            return res;
        }
        if (startKey == null) {
            // 查询从最小键到 endKey
            for (KeyValue kv : m.headMap(endKey, includeEnd).values()) if (!kv.isDeleted()) res.add(kv);
            return res;
        }
        if (endKey == null) {
            // 查询从 startKey 到最大键
            for (KeyValue kv : m.tailMap(startKey, includeStart).values()) if (!kv.isDeleted()) res.add(kv);
            return res;
        }
        // 查询指定范围 [startKey, endKey]
        for (KeyValue kv : m.subMap(startKey, includeStart, endKey, includeEnd).values()) if (!kv.isDeleted()) res.add(kv);
        return res;
    }

    /**
     * 检查是否需要刷盘 - 实际项目实现
     */
    public boolean shouldFlush() {
        return currentSize >= maxSize;
    }
}
```

### 8.2 性能优化实践

**注意**：以下优化技术是理论建议，当前项目实际使用的是标准的 `ConcurrentSkipListMap` 实现。这些优化方案展示了 SkipList 在 LSM Tree 中的潜在性能提升空间。

**内存分配优化建议**：

```java
// 使用对象池减少 GC 压力
private final ObjectPool<SkipListNode<K, V>> nodePool = new ObjectPool<>(
    () -> new SkipListNode<>(null, null, 1),
    node -> {
        // 重置节点状态
        Arrays.fill(node.forward, null);
        node.backward = null;
        node.value = null; // 帮助 GC
    }
);

// 批量预分配节点
private void preallocateNodes(int count, int maxLevel) {
    for (int i = 0; i < count; i++) {
        SkipListNode<K, V> node = new SkipListNode<>(null, null, maxLevel);
        nodePool.returnObject(node);
    }
}
```

#### 8.2.1 缓存优化

```java
// 使用缓存友好的内存布局
class CacheFriendlySkipListNode {
    // 将频繁访问的字段放在一起（64字节缓存行优化）
    final long keyHash;              // 8 bytes
    final K key;                    // 8 bytes (引用)
    V value;                        // 8 bytes (引用)
    volatile long version;          // 8 bytes

    // 填充字段以避免伪共享（总共64字节）
    private long pad1, pad2, pad3, pad4; // 32 bytes

    // 指针数组
    final SkipListNode<K, V>[] forward;
    SkipListNode<K, V> backward;
}
```

#### 8.2.2 搜索优化

```java
// 使用哨兵节点优化边界检查
private final SkipListNode<K, V> tailSentinel = new SkipListNode<>(null, null, MAX_LEVEL);

// 预计算键的哈希值用于快速比较
private int precomputeKeyHash(K key) {
    return key == null ? 0 : key.hashCode();
}

// 使用局部变量优化频繁访问的字段
public V getOptimized(K key) {
    int keyHash = precomputeKeyHash(key);
    SkipListNode<K, V> current = header;

    for (int i = maxLevel - 1; i >= 0; i--) {
        SkipListNode<K, V> next = current.forward[i];
        while (next != null && next != tailSentinel) {
            if (next.keyHash < keyHash ||
                (next.keyHash == keyHash && next.key.compareTo(key) < 0)) {
                current = next;
                next = current.forward[i];
            } else {
                break;
            }
        }
    }

    // ... 后续检查逻辑
}
```

---

## 9. 总结与展望

SkipList 作为一种优秀的概率数据结构，在平衡实现复杂度和性能方面表现出色：

- **简单性优势**：相比红黑树、AVL 树等平衡树结构，SkipList 的实现更加直观易懂
- **性能表现**：平均 O(log n) 的时间复杂度满足大多数应用场景需求
- **并发友好**：天然适合实现高效的并发版本，在多核环境下表现优异
- **扩展性强**：易于实现范围查询、迭代遍历等高级操作

随着大数据和实时处理需求的增长，SkipList 在以下领域有广阔应用前景：

- **数据库系统**：作为 LSM Tree 的 MemTable 核心数据结构
- **内存数据库**：Redis 等内存数据库的有序集合实现
- **实时计算**：流处理系统中的窗口聚合和状态管理
- **分布式系统**：分布式索引和元数据管理

未来的优化方向包括：

- **内存效率**：进一步减少指针开销和内存碎片
- **并发性能**：优化锁策略和无锁算法
- **缓存优化**：利用硬件特性提升缓存命中率
- **自适应调整**：根据负载动态调整概率参数

---

## 10. 参考资料

1. Pugh, W. (1990). Skip lists: A probabilistic alternative to balanced trees. Communications of the ACM, 33(6), 668-676.
2. Herlihy, M., & Shavit, N. (2008). The Art of Multiprocessor Programming. Morgan Kaufmann.
3. LevelDB/RocksDB 源代码实现. <https://github.com/google/leveldb>
4. Java ConcurrentSkipListMap 实现参考. <https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentSkipListMap.java>
5. Sundell, H., & Tsigas, P. (2003). Fast and lock-free concurrent priority queues for multi-thread systems. Journal of Parallel and Distributed Computing, 63(5), 609-627.
