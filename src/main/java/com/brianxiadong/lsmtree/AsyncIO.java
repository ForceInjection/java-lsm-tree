package com.brianxiadong.lsmtree;

import java.io.IOException;

public final class AsyncIO {
    private static volatile AsyncIOManager DEFAULT;

    private AsyncIO() {}

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

    public static void closeDefault() throws IOException {
        AsyncIOManager mgr = DEFAULT;
        if (mgr != null) {
            mgr.close();
            DEFAULT = null;
        }
    }
}