package org.sber.cache.proxy.storage;

import java.io.Serializable;

public class DiscEntry implements Serializable {
    final Object key;
    final Object value;

    public DiscEntry(Object key, Object value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "DiscEntry{" +
                "key=" + key +
                ", value=" + value +
                '}';
    }
}
