package org.sber.cache.proxy.storage;

import org.sber.cache.exception.CacheException;
import org.sber.cache.exception.CacheNotFoundException;

import java.io.*;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ConcurrentFileStorageImpl implements FileStorage {
    private final Map<Object, File> cacheLocation = new ConcurrentHashMap<>();
    private final File rootDir;

    public ConcurrentFileStorageImpl(File cacheDir) {
        rootDir = cacheDir;
        loadCacheFromDisc(rootDir);
    }

    private void loadCacheFromDisc(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()) {
                    try (InputStream fileInputStream = new BufferedInputStream(new FileInputStream(file))) {
                        if (file.toString().endsWith(".zip")) {
                            ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);
                            while (zipInputStream.getNextEntry() != null) {
                                updateCacheLocation(zipInputStream, file);
                            }
                        } else {
                            updateCacheLocation(fileInputStream, file);
                        }
                    } catch (InvalidClassException e) {
                        throw new CacheException("ошибка сериализации объекта", e);
                    } catch (ClassNotFoundException e) {
                        throw new CacheException("не удалось найти класс", e);
                    } catch (IOException e) {
                        throw new CacheException("ошибка чтения объекта из файл %s".formatted(file), e);
                    }
                }
            }
        }
    }

    private void updateCacheLocation(InputStream inputStream, File file) throws IOException, ClassNotFoundException {
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
        Object object = objectInputStream.readObject();
        if (object instanceof DiscEntry discEntry) {
            cacheLocation.put(discEntry.key, file);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        return cacheLocation.containsKey(key);
    }

    @Override
    public Object get(Object key) throws CacheNotFoundException {
        if (!cacheLocation.containsKey(key)) {
            throw new CacheNotFoundException("кэш не найден");
        }

        File file = cacheLocation.get(key);
        boolean isZip = file.getName().endsWith(".zip");
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            if (isZip) {
                ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);
                while (zipInputStream.getNextEntry() != null) {
                    Object object = new ObjectInputStream(zipInputStream).readObject();
                    if (object instanceof DiscEntry discEntry && Objects.equals(discEntry.key, key)) {
                        return discEntry.value;
                    }
                }
            } else {
                Object object = new ObjectInputStream(fileInputStream).readObject();
                if (object instanceof DiscEntry discEntry && Objects.equals(discEntry.key, key)) {
                    return discEntry.value;
                }
            }
        } catch (InvalidClassException e) {
            throw new CacheException("ошибка сериализации объекта", e);
        } catch (ClassNotFoundException e) {
            throw new CacheException("не удалось найти класс", e);
        } catch (IOException e) {
            throw new CacheException("ошибка чтения объекта из файл", e);
        }

        throw new CacheNotFoundException("кэш не найден");
    }

    @Override
    public void store(Object key, Object value) {
        String fileName = "cache_%s.bin".formatted(UUID.randomUUID().toString());
        store(key, value, fileName, false);
    }

    @Override
    public void store(Object key, Object value, String fileName, boolean compress) {
        DiscEntry entry = new DiscEntry(key, value);
        File file = new File(rootDir, fileName);

        try (OutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(file, false))) {
            if (compress) {
                ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
                zipOutputStream.putNextEntry(new ZipEntry("cache"));
                new ObjectOutputStream(zipOutputStream).writeObject(entry);
                zipOutputStream.closeEntry();
            } else {
                new ObjectOutputStream(fileOutputStream).writeObject(entry);
            }
            cacheLocation.put(key, file);
        } catch (NotSerializableException e) {
            file.delete();
            throw new CacheException("объект должен реализовывать Serializable {%s}".formatted(entry), e);
        } catch (InvalidClassException e) {
            throw new CacheException("ошибка сериализации объекта", e);
        } catch (IOException e) {
            throw new CacheException("ошибка записи объекта в файл", e);
        }
    }
}
