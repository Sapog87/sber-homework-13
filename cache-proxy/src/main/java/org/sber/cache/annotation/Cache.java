package org.sber.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cache {
    /**
     * Определяет какие параметры метода игнорировать при составлении ключа для кэша.
     * <p>
     * По дефолту все значения учитываются.
     */
    int[] exclude() default {};

    /**
     * Если аннотированный метод возвращает {@link java.util.List}, то его размер может быть ограничен.
     * <p>
     * Отрицательные значения и 0 не ограничивают список.
     */
    long limit() default 0;

    /**
     * Определения как хранить кэш: в памяти или в файле.
     * <p>
     * По дефолту кэш хранится в памяти JVM.
     */
    StorageType storage() default StorageType.MEMORY;

    /**
     * Будет ли сжат файл хранящий кэш в zip архив,
     * в случае если <code>storage = StorageType.FILE</code>
     * <p>
     * По дефолту кэш не сжимается.
     */
    boolean zip() default false;

    /**
     * Ключ (префикс файла) для кэша.
     * <p>
     * По дефолту используется имя метода.
     */
    String key() default "";
}
