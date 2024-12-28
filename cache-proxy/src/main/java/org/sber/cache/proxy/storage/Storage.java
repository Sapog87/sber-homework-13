package org.sber.cache.proxy.storage;

import org.sber.cache.exception.CacheNotFoundException;

public interface Storage {
    boolean containsKey(Object key);

    Object get(Object key) throws CacheNotFoundException;

    void store(Object key, Object value);
}
