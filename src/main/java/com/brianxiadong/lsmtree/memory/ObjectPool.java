package com.brianxiadong.lsmtree.memory;

/**
 * 对象池接口
 * @param <T> 对象类型
 */
public interface ObjectPool<T> {
    
    /**
     * 从池中获取对象
     * @return 对象实例
     */
    T borrowObject();
    
    /**
     * 归还对象到池中
     * @param obj 要归还的对象
     */
    void returnObject(T obj);
    
    /**
     * 创建新对象（当池为空时调用）
     * @return 新创建的对象
     */
    T createObject();
    
    /**
     * 销毁对象
     * @param obj 要销毁的对象
     */
    void destroyObject(T obj);
    
    /**
     * 验证对象是否有效
     * @param obj 要验证的对象
     * @return 是否有效
     */
    boolean validateObject(T obj);
    
    /**
     * 获取池统计信息
     * @return 池统计信息
     */
    PoolStats getStats();
    
    /**
     * 清空池
     */
    void clear();
    
    /**
     * 关闭池
     */
    void close();
}