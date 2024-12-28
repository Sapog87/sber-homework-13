package org.sber.cache.proxy;

import org.sber.cache.annotation.Cache;
import org.sber.cache.exception.CacheNotFoundException;
import org.sber.cache.proxy.storage.FileStorage;
import org.sber.cache.proxy.storage.Storage;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class ConcurrentCachedInvocationHandler implements InvocationHandler {
    private final Map<Object, Lock> keyLocks = new ConcurrentHashMap<>();
    private final Storage memoryStorage;
    private final FileStorage fileStorage;
    private final Object delegate;

    public ConcurrentCachedInvocationHandler(Object delegate, Storage memoryStorage, FileStorage fileStorage) {
        this.delegate = delegate;
        this.memoryStorage = memoryStorage;
        this.fileStorage = fileStorage;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Cache cache = method.getAnnotation(Cache.class);
        if (cache == null) {
            return invoke(method, args);
        }
        return getCacheOrInvoke(cache, method, args);
    }

    private Object getCacheOrInvoke(Cache cache, Method method, Object[] args) throws Throwable {
        return switch (cache.storage()) {
            case MEMORY -> getCacheFromRamOrInvoke(cache, method, args);
            case FILE -> getCacheFromDiskOrInvoke(cache, method, args);
        };
    }

    private Object getCacheFromRamOrInvoke(Cache cache, Method method, Object[] args) throws Throwable {
        Object key = key(cache, method, args);
        try {
            return memoryStorage.get(key);
        } catch (CacheNotFoundException e) {
            Lock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
            lock.lock();
            try {
                if (memoryStorage.containsKey(key)) {
                    return memoryStorage.get(key);
                }

                Object result = invoke(method, args);
                if (result != null) {
                    storeInMemory(cache, result, key);
                    return result;
                }

                return null;
            } finally {
                lock.unlock();
            }
        }
    }

    private void storeInMemory(Cache cache, Object result, Object key) {
        if (result instanceof List<?> list) {
            Object limitedList = limitList(cache.limit(), list);
            memoryStorage.store(key, limitedList);
        } else {
            memoryStorage.store(key, result);
        }
    }

    private Object getCacheFromDiskOrInvoke(Cache cache, Method method, Object[] args) throws Throwable {
        Object key = key(cache, method, args);
        try {
            return fileStorage.get(key);
        } catch (CacheNotFoundException e) {
            Lock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
            lock.lock();
            try {
                if (fileStorage.containsKey(key)) {
                    return fileStorage.get(key);
                }

                Object result = invoke(method, args);
                if (result != null) {
                    storeInFile(cache, key, result, getFileName(cache, method));
                    return result;
                }

                return null;
            } finally {
                lock.unlock();
            }
        }
    }

    private void storeInFile(Cache cache, Object key, Object result, String fileName) {
        if (result instanceof List<?> list) {
            Object limitedList = limitList(cache.limit(), list);
            fileStorage.store(key, limitedList, fileName, cache.zip());
        } else {
            fileStorage.store(key, result, fileName, cache.zip());
        }
    }

    private String getFileName(Cache cache, Method method) {
        String prefix = cache.key().isBlank() ? method.getName() : cache.key();
        return "%s_%s.%s".formatted(prefix, UUID.randomUUID(), cache.zip() ? "zip" : "bin");
    }

    private Object limitList(long limit, List<?> list) {
        if (limit > 0) {
            return list.stream().limit(limit).toList();
        }
        return list;
    }

    private Object invoke(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Impossible");
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Object key(Cache cache, Method method, Object[] args) {
        Set<Integer> excludedIndices = Arrays.stream(cache.exclude())
                .boxed()
                .collect(Collectors.toSet());

        List<Object> key = new ArrayList<>();

        String keyValue = "".equals(cache.key()) ? method.getName() : cache.key();
        key.add(keyValue);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (!excludedIndices.contains(i)) {
                    key.add(args[i]);
                }
            }
        }
        return key;
    }
}