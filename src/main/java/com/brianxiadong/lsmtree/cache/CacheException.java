package com.brianxiadong.lsmtree.cache;

public class CacheException extends Exception {
    public CacheException(String message) {
        super(message);
    }
    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}