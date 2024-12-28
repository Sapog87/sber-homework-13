package org.sber.proxy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sber.cache.CacheProxy;
import org.sber.cache.exception.CacheException;
import test.NotSerializableClass;
import test.TestService;

import java.io.File;
import java.io.IOException;
import java.io.NotSerializableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheProxyTest {

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
    void testDefaultCache() {
        CacheProxy cacheProxy = new CacheProxy(dir.toFile());

        TestService testService = mock(TestService.class);
        doReturn(List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))
                .when(testService)
                .testMethodWithDefaultCache();

        TestService cachedService = (TestService) cacheProxy.cache(testService);

        List<Integer> firstCall = cachedService.testMethodWithDefaultCache();
        // проверка вызова метода у оригинального объекта
        verify(testService, times(1)).testMethodWithDefaultCache();

        List<Integer> secondCall = cachedService.testMethodWithDefaultCache();
        // проверка обращения в кэш
        verify(testService, times(1)).testMethodWithDefaultCache();

        assertEquals(firstCall.size(), secondCall.size());
    }

    @Test
    @DisplayName("тест ограничения размера list")
    void testWithLimitedCacheOnList() {
        CacheProxy cacheProxy = new CacheProxy(dir.toFile());

        TestService testService = mock(TestService.class);
        doReturn(List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))
                .when(testService)
                .testMethodWithLimitedCacheOnList();

        TestService cachedService = (TestService) cacheProxy.cache(testService);

        List<Integer> firstCall = cachedService.testMethodWithLimitedCacheOnList();
        // проверка вызова метода у оригинального объекта
        verify(testService, times(1)).testMethodWithLimitedCacheOnList();

        List<Integer> secondCall = cachedService.testMethodWithLimitedCacheOnList();
        // проверка обращения в кэш
        verify(testService, times(1)).testMethodWithLimitedCacheOnList();

        assertNotEquals(firstCall.size(), secondCall.size());
    }

    @Test
    @DisplayName("тест исключения аргументов из ключа")
    void testCacheWithExclusion() {
        CacheProxy cacheProxy = new CacheProxy(dir.toFile());

        TestService testService = mock(TestService.class);
        doReturn(10D).when(testService).testMethodWithExclusion("aaa", 1, 1);
        doReturn(20D).when(testService).testMethodWithExclusion("aaa", 2, 2);

        TestService cachedService = (TestService) cacheProxy.cache(testService);

        double firstCall = cachedService.testMethodWithExclusion("aaa", 1, 1);
        double secondCall = cachedService.testMethodWithExclusion("aaa", 2, 2);

        // по логике testService значения не должны быть равны,
        // но благодаря cachedService 2 и 3 параметр игнорируются
        assertEquals(firstCall, secondCall, 1e-6);
        assertNotNull(dir.toFile().listFiles());
    }

    @Test
    @DisplayName("тест сохранения несериализуемого объекта а файл")
    void testNotSerializableObject() {
        CacheProxy cacheProxy = new CacheProxy(dir.toFile());

        TestService testService = mock(TestService.class);
        doReturn(new NotSerializableClass()).when(testService).testMethodWithNotSerializableClass();

        TestService cachedService = (TestService) cacheProxy.cache(testService);

        Throwable throwable = assertThrowsExactly(CacheException.class, cachedService::testMethodWithNotSerializableClass);
        assertEquals(NotSerializableException.class, throwable.getCause().getClass());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("тест загрузки кэша из файла при перезапуске")
    void testSaveCacheWithRestart(boolean firstStart) {
        CacheProxy cacheProxy = new CacheProxy(dir.toFile());
        TestService testService = mock(TestService.class);
        TestService cachedService = (TestService) cacheProxy.cache(testService);

        if (firstStart) {
            doReturn(10D).when(testService).testMethodWithZipping();
            double result = cachedService.testMethodWithZipping();
            assertEquals(10D, result, 1e-6);
        } else {
            doReturn(20D).when(testService).testMethodWithZipping();
            double result = cachedService.testMethodWithZipping();
            assertEquals(10D, result, 1e-6);
        }
    }
}