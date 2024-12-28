package org.sber.proxy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sber.cache.CacheProxy;
import test.TestService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ConcurrentCacheProxyTest {

    static final Path dir = Path.of("./src/test/resources/test");

    @BeforeAll
    static void setUp() throws IOException {
        Files.createDirectories(dir);
    }

    @AfterAll
    static void tearDown() throws IOException {
        try (Stream<Path> pathStream = Files.walk(dir)) {
            pathStream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    @DisplayName("тест базовой настройки кэша")
    void testDefaultCache() throws ExecutionException, InterruptedException {
        CacheProxy cacheProxy = new CacheProxy(dir.toFile());

        TestService testService = mock(TestService.class);
        doReturn(List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))
                .when(testService)
                .testMethodWithDefaultCache();

        TestService cachedService = (TestService) cacheProxy.cache(testService);

        FutureTask<List<Integer>> future1 = new FutureTask<>(cachedService::testMethodWithDefaultCache);
        FutureTask<List<Integer>> future2 = new FutureTask<>(cachedService::testMethodWithDefaultCache);

        new Thread(future1).start();
        new Thread(future2).start();

        List<Integer> result1 = future1.get();
        List<Integer> result2 = future2.get();

        // проверка, что метод был вызван 1 раз в многопотоке
        verify(testService).testMethodWithDefaultCache();

        assertEquals(result1.size(), result2.size());
    }

    @Test
    @DisplayName("тест ограничения размера list")
    void testWithLimitedCacheOnList() throws ExecutionException, InterruptedException {
        CacheProxy cacheProxy = new CacheProxy(dir.toFile());

        TestService testService = mock(TestService.class);
        doReturn(List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))
                .when(testService)
                .testMethodWithLimitedCacheOnList();

        TestService cachedService = (TestService) cacheProxy.cache(testService);

        FutureTask<List<Integer>> future1 = new FutureTask<>(cachedService::testMethodWithLimitedCacheOnList);
        FutureTask<List<Integer>> future2 = new FutureTask<>(cachedService::testMethodWithLimitedCacheOnList);

        new Thread(future1).start();
        new Thread(future2).start();

        List<Integer> result1 = future1.get();
        List<Integer> result2 = future2.get();

        // проверка, что метод был вызван 1 раз в многопотоке
        verify(testService).testMethodWithLimitedCacheOnList();

        assertNotEquals(result1.size(), result2.size());
    }

    @Test
    @DisplayName("тест исключения аргументов из ключа")
    void testCacheWithExclusion() throws ExecutionException, InterruptedException {
        CacheProxy cacheProxy = new CacheProxy(dir.toFile());

        TestService testService = mock(TestService.class);
        doReturn(10D).when(testService).testMethodWithExclusion("aaa", 1, 1);
        doReturn(20D).when(testService).testMethodWithExclusion("aaa", 2, 2);

        TestService cachedService = (TestService) cacheProxy.cache(testService);

        FutureTask<Double> future1 = new FutureTask<>(() -> cachedService.testMethodWithExclusion("aaa", 1, 1));
        FutureTask<Double> future2 = new FutureTask<>(() -> cachedService.testMethodWithExclusion("aaa", 2, 2));

        new Thread(future1).start();
        new Thread(future2).start();

        Double result1 = future1.get();
        Double result2 = future2.get();

        // проверка, что метод был вызван 1 раз в многопотоке
        verify(testService).testMethodWithExclusion(eq("aaa"), anyInt(), anyInt());

        // по логике testService значения не должны быть равны,
        // но благодаря cachedService 2 и 3 параметр игнорируются
        assertEquals(result1, result2, 1e-6);
        assertNotNull(dir.toFile().listFiles());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("тест загрузки кэша из файла при перезапуске")
    void testSaveCacheWithRestart(boolean firstStart) throws ExecutionException, InterruptedException {
        CacheProxy cacheProxy = new CacheProxy(dir.toFile());
        TestService testService = mock(TestService.class);
        TestService cachedService = (TestService) cacheProxy.cache(testService);

        if (firstStart) {
            doReturn(10D).when(testService).testMethodWithZipping();
        } else {
            doReturn(20D).when(testService).testMethodWithZipping();
        }

        FutureTask<Double> future1 = new FutureTask<>(cachedService::testMethodWithZipping);
        FutureTask<Double> future2 = new FutureTask<>(cachedService::testMethodWithZipping);

        new Thread(future1).start();
        new Thread(future2).start();

        Double result1 = future1.get();
        Double result2 = future2.get();

        if (firstStart) {
            verify(testService).testMethodWithZipping();
        } else {
            verify(testService, never()).testMethodWithZipping();
        }

        assertEquals(10D, result1, 1e-6);
        assertEquals(10D, result2, 1e-6);
    }
}
