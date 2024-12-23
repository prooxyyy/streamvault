package me.proo0xy.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * DataStore is a singleton class that manages key-value data storage,
 * providing thread-safe operations and real-time update notifications.
 */
public class DataStore {

    private static final Logger log = LoggerFactory.getLogger(DataStore.class);
    private static DataStore instance;

    private final Map<String, DataEntry> dataMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<DataEntry>> subscribers = new CopyOnWriteArrayList<>();
    private final String storagePath;
    private final String backupPath;
    private final Gson gson;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService subscriberExecutor = Executors.newCachedThreadPool();
    private final Type mapType = new TypeToken<Map<String, DataEntry>>() {}.getType();

    /**
     * Private constructor to enforce Singleton pattern.
     *
     * @param storagePath      Path to store the main data file.
     * @param backupPath       Path to store the backup.
     * @param autoSaveInterval Auto-save interval in seconds.
     */
    private DataStore(String storagePath, String backupPath, long autoSaveInterval) {
        this.storagePath = ensureTrailingSlash(storagePath);
        this.backupPath = ensureTrailingSlash(backupPath);
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        ensureDirectoryExists(this.storagePath);
        ensureDirectoryExists(this.backupPath);

        loadFromDisk();
        startAutoSave(autoSaveInterval);
    }

    /**
     * Initializes the singleton instance of DataStore.
     *
     * @param storagePath      Path to store the main data file.
     * @param backupPath       Path to store the backup.
     * @param autoSaveInterval Auto-save interval in seconds.
     */
    public static synchronized void initialize(String storagePath, String backupPath, long autoSaveInterval) {
        if (instance == null) {
            instance = new DataStore(storagePath, backupPath, autoSaveInterval);
            log.info("DataStore initialized successfully.");
        } else {
            log.warn("DataStore is already initialized.");
        }
    }

    /**
     * Retrieves the singleton instance of DataStore.
     *
     * @return The singleton instance.
     */
    public static DataStore getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DataStore is not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Adds or updates an entry in the store.
     *
     * @param key   The key of the entry.
     * @param value The value of the entry.
     */
    public void put(String key, Object value) {
        DataEntry entry = dataMap.computeIfAbsent(key, k -> new DataEntry(k, value));
        entry.setValue(value);
        notifySubscribers(entry);
    }

    /**
     * Retrieves an entry by key.
     *
     * @param key The key of the entry.
     * @return The DataEntry or null if not found.
     */
    public DataEntry get(String key) {
        return dataMap.get(key);
    }

    /**
     * Retrieves all data as a map.
     *
     * @return A copy of all key-value pairs.
     */
    public Map<String, DataEntry> getAllData() {
        return new ConcurrentHashMap<>(dataMap);
    }

    /**
     * Removes an entry by key.
     *
     * @param key The key of the entry.
     * @return The removed DataEntry or null if not found.
     */
    public DataEntry remove(String key) {
        DataEntry removed = dataMap.remove(key);
        if (removed != null) {
            notifySubscribers(new DataEntry(key, null));
        }
        return removed;
    }

    /**
     * Clears all entries in the store.
     */
    public void clear() {
        dataMap.clear();
    }

    /**
     * Subscribes to updates.
     *
     * @param subscriber The subscriber.
     */
    public void subscribe(Consumer<DataEntry> subscriber) {
        subscribers.add(subscriber);
    }

    /**
     * Unsubscribes from updates.
     *
     * @param subscriber The subscriber.
     */
    public void unsubscribe(Consumer<DataEntry> subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * Notifies all subscribers about a changed entry.
     *
     * @param entry The changed entry.
     */
    private void notifySubscribers(DataEntry entry) {
        if (entry == null) {
            return;
        }

        for (Consumer<DataEntry> subscriber : subscribers) {
            subscriberExecutor.submit(() -> {
                try {
                    log.info("Notifying subscriber: {}", subscriber);
                    subscriber.accept(entry);
                } catch (Exception e) {
                    log.warn("Error notifying subscriber", e);
                }
            });
        }
    }

    /**
     * Saves data to disk.
     */
    private void saveToDisk() {
        String storageFilePath = getStorageFilePath();
        try (Writer writer = new BufferedWriter(new FileWriter(storageFilePath))) {
            gson.toJson(dataMap, writer);
            log.info("Data successfully saved to disk.");
        } catch (IOException e) {
            log.error("Error saving data to disk", e);
        }
    }

    /**
     * Creates a backup of data on disk.
     */
    private void backupToDisk() {
        String backupFilePath = getBackupFilePath();
        try (Writer writer = new BufferedWriter(new FileWriter(backupFilePath))) {
            gson.toJson(dataMap, writer);
            log.info("Data backup successfully created.");
        } catch (IOException e) {
            log.error("Error creating data backup", e);
        }
    }

    /**
     * Loads data from disk.
     */
    private void loadFromDisk() {
        String storageFilePath = getStorageFilePath();
        File storageFile = new File(storageFilePath);
        if (!storageFile.exists()) {
            log.info("Data file not found, starting fresh.");
            return;
        }

        try (Reader reader = new BufferedReader(new FileReader(storageFilePath))) {
            Map<String, DataEntry> loadedData = gson.fromJson(reader, mapType);
            if (loadedData != null) {
                dataMap.putAll(loadedData);
                log.info("Data successfully loaded from disk.");
            }
        } catch (IOException e) {
            log.error("Error loading data from disk", e);
        }
    }

    /**
     * Starts automatic saving and backup.
     *
     * @param intervalInSeconds Interval in seconds.
     */
    private void startAutoSave(long intervalInSeconds) {
        scheduler.scheduleAtFixedRate(this::saveToDisk, intervalInSeconds, intervalInSeconds, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::backupToDisk, intervalInSeconds * 2, intervalInSeconds * 2, TimeUnit.SECONDS);
        log.info("Automatic saving and backup started.");
    }

    /**
     * Shuts down the store, saving data and stopping schedulers.
     */
    public void shutdown() {
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            subscriberExecutor.shutdown();
            if (!subscriberExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                subscriberExecutor.shutdownNow();
            }
            saveToDisk();
            backupToDisk();
            log.info("Store successfully shut down.");
        } catch (InterruptedException e) {
            log.error("Error shutting down the store", e);
            scheduler.shutdownNow();
            subscriberExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gets the full path to the data storage file.
     *
     * @return Path to the data storage file.
     */
    private String getStorageFilePath() {
        return this.storagePath + "data.json";
    }

    /**
     * Gets the full path to the backup file.
     *
     * @return Path to the backup file.
     */
    private String getBackupFilePath() {
        return this.backupPath + "data.json.backup";
    }

    /**
     * Ensures that the directory exists, and creates it if necessary.
     *
     * @param path Path to the directory.
     */
    private void ensureDirectoryExists(String path) {
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            log.error("Failed to create directory: {}", path, e);
            throw new RuntimeException("Failed to create directory: " + path, e);
        }
    }

    /**
     * Ensures that the path ends with a slash.
     *
     * @param path The file path.
     * @return The path ending with a slash.
     */
    private String ensureTrailingSlash(String path) {
        return path.endsWith("/") || path.endsWith("\\") ? path : path + File.separator;
    }
}
