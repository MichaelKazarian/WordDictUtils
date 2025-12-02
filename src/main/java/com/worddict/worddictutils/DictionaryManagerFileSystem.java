package com.worddict.worddictutils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.worddict.worddictcore.Language;

/**
 * File-system-based implementation of DictionaryManager.
 * Singleton.
 */
public enum DictionaryManagerFileSystem implements DictionaryManager {
    INSTANCE;

    private volatile Path rootDirectory;
    private volatile boolean initialized = false;

    private DictionaryManagerFileSystem() {
        // Possible additional init
    }
    // Key: lowercased ISO code, e.g. "en", "es"
    private final Map<String, BaseLanguage> languages = new ConcurrentHashMap<>();

    public synchronized void init(String rootPathString) throws java.io.IOException {
        if (initialized) {
            throw new IllegalStateException("DictionaryManagerFileSystem is already initialized.");
        }

        Path rootPath = setupRootDirectory(rootPathString);
        this.rootDirectory = rootPath;
        this.initialized = true;

        System.out.println("Dictionary Manager initialized with root: " + rootPath);
    }

    /**
     * Helper method to validate and create the root directory.
     * @param rootPathString The string path provided by the user.
     * @return The absolute Path object of the root directory.
     * @throws java.io.IOException if directory creation fails.
     */
    private Path setupRootDirectory(String rootPathString) throws IOException {
        Path rootPath = Paths.get(rootPathString).toAbsolutePath();

        if (Files.notExists(rootPath)) {
            Files.createDirectories(rootPath);
        }

        return rootPath;
    }

    public Path getRootDirectory() {
        if (!initialized) {
            throw new IllegalStateException("DictionaryManager is not initialized. Call init() first.");
        }
        return rootDirectory;
    }

    @Override
    public void addBaseLanguage(BaseLanguage baseLanguage) {
        if (baseLanguage == null || baseLanguage.getLanguage().getLanguageCode() == null) {
            throw new IllegalArgumentException("BaseLanguage or its code cannot be null");
        }
        languages.put(baseLanguage.getLanguage().getLanguageCode().toLowerCase(), baseLanguage);
    }

    @Override
    public Optional<BaseLanguage> getBaseLanguage(String sourceLangCode) {
        if (sourceLangCode == null) {
            // Використовуйте порожній Optional
            return Optional.empty(); 
        }
        // Використовуйте новіший метод getOrDefault або Optional.ofNullable
        return Optional.ofNullable(languages.get(sourceLangCode.toLowerCase()));
    }

    @Override
    public Collection<BaseLanguage> getBaseLanguages() {
        return Collections.unmodifiableCollection(languages.values());
    }

    /**
     * For testing only: clear all loaded languages and reset initialization state.
     * This should only be used in JUnit's @Before method.
     */
    void resetForTests() {
        languages.clear();
        this.rootDirectory = null;
        this.initialized = false;
    }
}
