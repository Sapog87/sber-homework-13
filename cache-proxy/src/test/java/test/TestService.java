package test;

import org.sber.cache.annotation.Cache;
import org.sber.cache.annotation.StorageType;

import java.util.List;

public interface TestService {
    @Cache
    List<Integer> testMethodWithDefaultCache();

    @Cache(limit = 10)
    List<Integer> testMethodWithLimitedCacheOnList();

    @Cache(exclude = {1, 2}, storage = StorageType.FILE, key = "data")
    double testMethodWithExclusion(String arg1, int arg2, int arg3);

    @Cache(storage = StorageType.FILE, zip = true)
    double testMethodWithZipping();

    @Cache(storage = StorageType.FILE)
    NotSerializableClass testMethodWithNotSerializableClass();
}
