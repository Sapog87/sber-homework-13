package org.sber.cache.proxy.storage;

import org.sber.cache.exception.CacheNotFoundException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentMemoryStorage implements Storage {
    private final Map<Object, Object> cache = new ConcurrentHashMap<>();

    @Override
    public boolean containsKey(Object key) {
        return cache.containsKey(key);
    }

    @Override
    public Object get(Object key) throws CacheNotFoundException {
        Object value = cache.get(key);
        if (value == null) {
            throw new CacheNotFoundException("кэш не найден");
        }
        return value;
    }

    @Override
    public void store(Object key, Object value) {
        cache.put(key, value);
    }
}
