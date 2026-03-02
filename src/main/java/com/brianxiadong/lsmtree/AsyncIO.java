package com.brianxiadong.lsmtree;

import java.io.IOException;

/**
 * 异步 IO 全局访问点
 * <p>
 * 提供默认的 {@link AsyncIOManager} 单例实例。
 * 建议在应用启动时初始化，并在关闭时调用 {@link #closeDefault()}。
 */
public final class AsyncIO {
    private static volatile AsyncIOManager DEFAULT;

    private AsyncIO() {}

    /**
     * 获取默认的 AsyncIOManager 实例
     * <p>
     * 如果实例尚未创建，则使用默认配置（线程数为 CPU 核心数，至少为 2）进行初始化。
     * 
     * @return 全局单例 AsyncIOManager
     */
    public static AsyncIOManager get() {
        AsyncIOManager mgr = DEFAULT;
        if (mgr != null) return mgr;
        synchronized (AsyncIO.class) {
            if (DEFAULT == null) {
                try {
                    DEFAULT = new NioAsyncIOManager(Math.max(2, Runtime.getRuntime().availableProcessors()), "default");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return DEFAULT;
        }
    }

    /**
     * 关闭默认的 AsyncIOManager 实例并释放资源
     * 
     * @throws IOException 如果关闭过程中发生错误
     */
    public static void closeDefault() throws IOException {
        AsyncIOManager mgr = DEFAULT;
        if (mgr != null) {
            synchronized (AsyncIO.class) {
                if (DEFAULT != null) {
                    DEFAULT.close();
                    DEFAULT = null;
                }
            }
        }
    }
}