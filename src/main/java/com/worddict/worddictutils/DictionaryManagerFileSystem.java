package com.worddict.worddictutils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

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
            Path newPath = Paths.get(rootPathString).toAbsolutePath();
            if (newPath.equals(this.rootDirectory)) {
                return;
            }
            throw new IllegalStateException("DictionaryManagerFileSystem is already initialized.");
        }

        Path rootPath = setupRootDirectory(rootPathString);
        this.rootDirectory = rootPath;

        this.loadBaseLanguages(this.rootDirectory);
        this.initialized = true;

        // System.out.println("Dictionary Manager initialized with root: " + rootPath);
    }

    /**
     * Helper method to validate and create the root directory.
     * @param rootPathString The string path provided by the user.
     * @return The absolute Path object of the root directory.
     * @throws java.io.IOException if directory creation fails.

     */
    private Path setupRootDirectory(String rootPathString) throws IOException {
        Path rootPath = Paths.get(rootPathString).toAbsolutePath();

        if (!Files.exists(rootPath)) {
            Files.createDirectories(rootPath);
        }

        return rootPath;
    }

    /**
     * Сканує кореневу директорію на наявність піддиректорій,
     * які вважаються мовними кодами, і завантажує їх як BaseLanguage.
     * @param rootPath Кореневий шлях словника.
     * @throws IOException Якщо не вдається просканувати директорії.
     */
    private void loadBaseLanguages(Path rootPath) throws IOException {
        try (Stream<Path> subdirectories = Files.list(rootPath)) {
            subdirectories
                .filter(Files::isDirectory)
                .forEach(langDir -> {
                        String langCode = langDir.getFileName().toString().toLowerCase(Locale.ROOT);
                        try {
                            Language l = Language.getLanguageByCode(langCode);
                            BaseLanguage bl = new BaseLanguageFileSystem(l, rootPath);
                            languages.put(langCode, bl);
                            // System.out.println("Discovered and registered language: " + langCode);

                        } catch (Exception e) {
                            // Обробка помилок при створенні Language або BaseLanguage
                            System.err.println("Skipping language directory " + langCode +
                                               " due to error during registration: " + e.getMessage());
                        }
                    });
        }
    }

    public Path getRootDirectory() {
        if (!initialized) {
            throw new IllegalStateException("DictionaryManager is not initialized. Call init() first.");
        }
        return rootDirectory;
    }

    /**
     * Registers a new source language implementation with the dictionary manager.
     * This method is typically called during the application startup/initialization
     * phase to configure the available language backends.
     *
     * <p>If a BaseLanguage with the same language code is already registered (case-insensitive),
     * the registration is skipped, and the existing instance is preserved.</p>
     * * @param baseLanguage The concrete storage implementation (e.g., FileSystemBaseLanguage)
     * for a specific source language (e.g., 'en', 'uk').
     * Must not be null, and its language code must be unique among the registered ones.
     * @throws IllegalArgumentException if the provided BaseLanguage or its language code is null.
     * @throws RuntimeException if the root directory is inaccessible or the corresponding
     * language directory cannot be created on the filesystem.
     */
    @Override
    public void addBaseLanguage(BaseLanguage baseLang) {
        if (baseLang == null || baseLang.getLanguage().getLanguageCode() == null) {
            throw new IllegalArgumentException("BaseLanguage or its code cannot be null");
        }
        String langCode = baseLang.getLanguage().getLanguageCode().toLowerCase(Locale.ROOT);
        if (handleDuplicateLanguage(langCode)) return;
        languages.put(langCode, baseLang);
    }

    /**
     * Checks if a language with the given code is already registered.
     * If it is, prints a warning and returns true, indicating the caller should skip processing.
     * * @param languageCode The lowercased ISO code of the language (e.g., "en").
     * @return true if the language is already registered and was skipped, false otherwise.
     */
    private boolean handleDuplicateLanguage(String languageCode) {
        if (languages.containsKey(languageCode)) {
            System.out.println("Warning: Language " + languageCode + " already loaded. Skipped.");
            return true;
        }
        return false;
    }

    @Override
    public Optional<BaseLanguage> getBaseLanguage(String sourceLangCode) {
        if (sourceLangCode == null) {
            return Optional.empty(); 
        }
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
