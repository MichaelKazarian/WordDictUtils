package com.worddict.worddictutils;

import com.worddict.worddictcore.Language;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * File-system-based implementation of BaseLanguage.
 * Manages dictionaries stored as subdirectories within the source language directory.
 */
public class BaseLanguageFileSystem extends BaseLanguage {

    private final Path languageRoot;
    private static final String DICT_NAME_PATTERN = "^[a-z]{2}(?:-[a-z0-9-_]+)?$";

    /**
     * Creates a file-system language manager and ensures its necessary directory structure exists.
     * @param language The source language (e.g., English 'en').
     * @param rootPath The absolute file-system path *where the BaseLanguage directory should be created*
     * (i.e., the root of the DictionaryManager).
     * @throws IOException If directory creation fails.
     */
    public BaseLanguageFileSystem(Language language, Path rootPath) throws IOException {
        super(language);

        String langCode = language.getLanguageCode().toLowerCase();
        this.languageRoot = rootPath.resolve(langCode);

        setupLanguageStorage();
        setupSoundsStorage();

        if (!Files.isDirectory(languageRoot)) {
             throw new IllegalArgumentException("Base language root path is not a directory: " + languageRoot);
        }

        this.dictionaries.putAll(loadDictionariesFromFilesystem());
    }

    /**
     * Create the base language directory (e.g., 'en')
     * Ensures the root directory for this BaseLanguage
     * (e.g., '/dictionaries/en') exists.
     * @throws IOException if directory creation fails.
     */
    public void setupLanguageStorage() throws IOException {
        if (Files.notExists(this.languageRoot)) {
            Files.createDirectories(this.languageRoot);
            // System.out.println("Created BaseLanguage directory: " + this.languageRoot);
        }
    }

    /**
     * Create the centralized audio files directory (e.g., 'en/--sounds')
     * Ensures the mandatory '--sounds' storage subdirectory 
     * (e.g., '/dictionaries/en/--sounds') exists.
     * @throws IOException if directory creation fails.
     */
    public void setupSoundsStorage() throws IOException {
        Path soundsPath = this.languageRoot.resolve("--sounds");
        if (Files.notExists(soundsPath)) {
            Files.createDirectories(soundsPath);
            // System.out.println("Created BaseLanguage sounds directory: " + soundsPath);
        }
    }

    /**
     * Returns the file system path for this BaseLanguage (e.g., /dictionaries/en).
     */
    public Path getLanguageRootPath() {
        return languageRoot;
    }
   
    public Path getDictionaryPath(String dictionaryName) {
        return this.languageRoot.resolve(dictionaryName); // Використовуємо this.languageRoot
    }
    
    @Override
    public Dictionary createDictionary(Language targetLanguage, String additionalName) {
        String dictName = getDictionaryName(targetLanguage.getLanguageCode(), additionalName);
        Path dictPath = getDictionaryPath(dictName);

        if (dictionaries.containsKey(dictName)) {
            return dictionaries.get(dictName);
        }

        try {
            checkDictionaryUniqueness(dictName, dictPath);
            Files.createDirectories(dictPath);
            DictionaryFileSystem newDict = new DictionaryFileSystem(this, targetLanguage, additionalName);
            dictionaries.put(dictName, newDict);

            DictionaryProperties props = new DictionaryProperties();
            props.name = "Dictionary " + targetLanguage.getLanguageCode().toUpperCase();
            Path propsFile = dictPath.resolve("properties.json");
            Files.writeString(propsFile, props.toJsonObject().toString(2), java.nio.charset.StandardCharsets.UTF_8);

            return newDict;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create dictionary directory: " + dictPath, e);
        }
    }

    /**
     * Deletes a dictionary and its corresponding file system directory.
     * Note: This is a placeholder and should be implemented carefully with recursive deletion.
     */
    @Override
    public void removeDictionary(Dictionary dictionary) {
        // Логіка видалення буде реалізована пізніше. 
        // Вона повинна знайти шлях словника та рекурсивно видалити його.
        System.out.println("LOG: Attempting to remove dictionary (placeholder): " + dictionary.getTargetLanguage().getLanguageCode());
    }

    /**
     * Scans the filesystem for subdirectories that represent dictionaries,
     * validates their names, and loads them. This method is called during initialization.
     * @return Map of unique dictionary names to Dictionary objects (DictionaryFileSystem).
     */
    private Map<String, Dictionary> loadDictionariesFromFilesystem() {
        Map<String, Dictionary> loadedDicts = new HashMap<>();
        try (Stream<Path> subdirectories = Files.list(this.languageRoot)) {
            subdirectories
                .filter(Files::isDirectory)
                .filter(p -> !p.getFileName().toString().startsWith("--"))
                .forEach(dictDir -> {
                    String dictName = dictDir.getFileName().toString();
                    
                    if (!dictName.matches(DICT_NAME_PATTERN)) {
                        System.err.println("Skipping directory '" + dictName + 
                                           "': Does not match dictionary naming convention (target[-name]).");
                        return;
                    }
                    try {
                        String targetCode = dictName.substring(0, 2);
                        String additionalName = "";
                        if (dictName.length() > 2 && dictName.charAt(2) == '-') {
                            additionalName = dictName.substring(3); // Всі символи після першого дефісу
                        }
                        Language targetLang = Language.getLanguageByCode(targetCode);
                        Dictionary dict = new DictionaryFileSystem(this, targetLang, additionalName); 
                        loadedDicts.put(dictName, dict);
                         
                    } catch (IllegalArgumentException iae) {
                         System.err.println("Skipping directory '" + dictName + 
                                            "': Invalid target language code. " + iae.getMessage());
                    } catch (Exception e) {
                        System.err.println("Error loading dictionary from " + dictName + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to scan language root " + this.languageRoot + ": " + e.getMessage());
        }
         
        return loadedDicts;
    }

    /**
     * Scans the file system for subdirectories that represent dictionaries.
     * For a proper implementation, it should filter subdirectories based on naming conventions 
     * (e.g., target language code prefix). 
     * Currently returns an empty list, as the actual Dictionary implementation (DictionaryFileSystem) is missing.
     */
    @Override
    public List<Dictionary> listDictionaries() {
        return new ArrayList<>(dictionaries.values());
    }

    /**
     * Checks if a dictionary with the given name already exists in the internal storage or on the filesystem.
     * @param dictName Unique dictionary name (target[-additional]).
     * @param dictPath Path to the dictionary directory on the filesystem.
     * @throws IllegalArgumentException If the dictionary already exists.
     * @throws IOException If a filesystem error occurs.
     */
    private void checkDictionaryUniqueness(String dictName, Path dictPath) throws IOException {
        if (dictionaries.containsKey(dictName)) {
            throw new IllegalArgumentException("Dictionary already registered internally: " + dictName);
        }
        if (Files.exists(dictPath)) {
            throw new IllegalArgumentException("Dictionary directory already exists on filesystem: " + dictPath);
        }
    }
}
