package org.sber.cache.proxy.storage;

public interface FileStorage extends Storage {
    void store(Object key, Object value, String fileName, boolean compress);
}
