package com.brianxiadong.lsmtree.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 堆外内存 (Direct Memory) 管理器
 * <p>
 * 通过池化 {@link ByteBuffer} 来减少 direct memory 的分配和释放开销。
 * Direct ByteBuffer 的分配通常比 Heap ByteBuffer 昂贵。
 */
public class DirectMemoryManager {
    private final ConcurrentLinkedQueue<ByteBuffer> bufferPool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger poolSize = new AtomicInteger(0);
    private final AtomicLong allocatedBytes = new AtomicLong(0);
    private final AtomicLong deallocatedBytes = new AtomicLong(0);
    private final int MAX_POOL_SIZE = 100; // 最大池大小
    
    /**
     * 分配ByteBuffer
     */
    public ByteBuffer allocate(int size) {
        // 尝试从池中获取
        ByteBuffer buffer = bufferPool.poll();
        if (buffer != null && buffer.capacity() >= size) {
            // 兼容 Java 8: 显式转换为 Buffer
            ((java.nio.Buffer) buffer).clear();
            poolSize.decrementAndGet();
            return buffer;
        }
        
        // 创建新的直接缓冲区
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(size);
        allocatedBytes.addAndGet(size);
        return newBuffer;
    }
    
    /**
     * 释放ByteBuffer到池中
     */
    public void deallocate(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) {
            return;
        }
        
        // 如果池未满，放回池中
        if (poolSize.get() < MAX_POOL_SIZE) {
            // 兼容 Java 8: 显式转换为 Buffer
            ((java.nio.Buffer) buffer).clear();
            bufferPool.offer(buffer);
            poolSize.incrementAndGet();
        } else {
            // 池满了，直接释放
            deallocatedBytes.addAndGet(buffer.capacity());
        }
    }
    
    /**
     * 获取统计信息
     */
    public DirectMemoryStats getStats() {
        return new DirectMemoryStats(
            poolSize.get(),
            bufferPool.size(),
            allocatedBytes.get(),
            deallocatedBytes.get()
        );
    }
    
    /**
     * 清理资源
     */
    public void shutdown() {
        bufferPool.clear();
        poolSize.set(0);
    }
    
    /**
     * 堆外内存统计信息
     */
    public static class DirectMemoryStats {
        private final int poolSize;
        private final int availableBuffers;
        private final long totalAllocated;
        private final long totalDeallocated;
        
        public DirectMemoryStats(int poolSize, int availableBuffers, 
                               long totalAllocated, long totalDeallocated) {
            this.poolSize = poolSize;
            this.availableBuffers = availableBuffers;
            this.totalAllocated = totalAllocated;
            this.totalDeallocated = totalDeallocated;
        }
        
        // Getters
        public int getPoolSize() { return poolSize; }
        public int getAvailableBuffers() { return availableBuffers; }
        public long getTotalAllocated() { return totalAllocated; }
        public long getTotalDeallocated() { return totalDeallocated; }
        
        @Override
        public String toString() {
            return String.format(
                "DirectMemoryStats{pool=%d, available=%d, allocated=%dB, deallocated=%dB}",
                poolSize, availableBuffers, totalAllocated, totalDeallocated
            );
        }
    }
}