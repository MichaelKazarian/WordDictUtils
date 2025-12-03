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
            throw new IllegalStateException("DictionaryManagerFileSystem is already initialized.");
        }

        Path rootPath = setupRootDirectory(rootPathString);
        this.rootDirectory = rootPath;

        this.loadBaseLanguages(this.rootDirectory);
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
                            BaseLanguage bl = new MockBaseLanguage(l);

                            languages.put(langCode, bl);
                            System.out.println("Discovered and registered language: " + langCode);

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

    @Override
    public void addBaseLanguage(BaseLanguage baseLanguage) {
        if (baseLanguage == null || baseLanguage.getLanguage().getLanguageCode() == null) {
            throw new IllegalArgumentException("BaseLanguage or its code cannot be null");
        }
        String langCode = baseLanguage.getLanguage().getLanguageCode().toLowerCase(Locale.ROOT);

        // Перевіряємо, чи вже завантажена мова під час ініціалізації
        if (languages.containsKey(langCode)) {
            System.out.println("Warning: Language " + langCode + " already loaded. Skipped.");
            return;
        }
        
        try {
            setupLanguageDirectory(langCode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory for language: " + langCode, e);
        }

        languages.put(langCode, baseLanguage);
    }

    /**
     * Creates the directory for the given language code within the manager's root directory.
     * * <p>The method constructs the path and creates the directory only if it does not already exist.</p>
     * * @param languageCode The standard language code (e.g., "en", "es").
     * @throws IOException If the root directory is inaccessible or the language directory
     * cannot be created.
     */
    private void setupLanguageDirectory(String languageCode) throws IOException {
        Path rootPath = getRootDirectory();
        Path langPath = rootPath.resolve(languageCode);

        if (Files.notExists(langPath)) {
            Files.createDirectories(langPath);
            System.out.println("Created language directory: " + langPath);
        }
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
