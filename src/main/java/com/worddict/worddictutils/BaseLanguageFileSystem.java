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
    private final Map<String, Dictionary> dictionaries = new HashMap<>();
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
        initializeDirectoryStructure();
        
        if (!Files.isDirectory(languageRoot)) {
             throw new IllegalArgumentException("Base language root path is not a directory: " + languageRoot);
        }

        this.dictionaries.putAll(loadDictionariesFromFilesystem());
    }
    
    /**
     * Ensures the root directory for this BaseLanguage and its mandatory '--sounds' storage 
     * subdirectory exist on the filesystem.
     * @throws IOException if directory creation fails.
     */
    private void initializeDirectoryStructure() throws IOException {
        // Створення каталогу базової мови (наприклад, 'en')
        if (Files.notExists(this.languageRoot)) {
            Files.createDirectories(this.languageRoot);
            // System.out.println("Created BaseLanguage directory: " + this.languageRoot);
        }
        
        // Створення каталогу для аудіо-файлів (наприклад, 'en/--sounds')
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
        // 1. Формуємо унікальну назву
        String dictName = getDictionaryName(targetLanguage.getLanguageCode(), additionalName);
        Path dictPath = getDictionaryPath(dictName);

        // 2. Перевірка унікальності (за каталогом ФС та внутрішнім Map)
        if (Files.exists(dictPath) || dictionaries.containsKey(dictName)) {
            throw new IllegalArgumentException("Dictionary already exists: " + dictPath);
        }
        
        try {
            Files.createDirectories(dictPath);
            // TODO: Замінити на new DictionaryFileSystem(...)
            Dictionary newDict = new Dictionary(this.language, targetLanguage); 
            
            // 4. Реєстрація у внутрішній мапі
            dictionaries.put(dictName, newDict);
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

    private Map<String, Dictionary> loadDictionariesFromFilesystem() {
        Map<String, Dictionary> loadedDicts = new HashMap<>();
         
        try (Stream<Path> subdirectories = Files.list(this.languageRoot)) {
            subdirectories
                .filter(Files::isDirectory)
                // Ігноруємо спеціалізований каталог --sounds
                .filter(p -> !p.getFileName().toString().equals("--sounds")) 
                .forEach(dictDir -> {
                    String dictName = dictDir.getFileName().toString();
                    
                    // 1. ПЕРЕВІРКА ФОРМАТУ ЗА РЕГУЛЯРНИМ ВИРАЗОМ
                    if (!dictName.matches(DICT_NAME_PATTERN)) {
                        System.err.println("Skipping directory '" + dictName + 
                                           "': Does not match dictionary naming convention (target[-name]).");
                        return;
                    }
                    
                    try {
                        // 2. Екстракція target code (завжди перші 2 літери)
                        String targetCode = dictName.substring(0, 2);
                        
                        // 3. Екстракція additionalName (якщо є)
                        String additionalName = "";
                        if (dictName.length() > 2 && dictName.charAt(2) == '-') {
                            additionalName = dictName.substring(3); // Всі символи після першого дефісу
                        }
                        
                        // 4. Валідація мови
                        Language targetLang = Language.getLanguageByCode(targetCode);
                        
                        // 5. Створення об'єкта (використовуємо заглушку Dictionary з оновленим конструктором)
                        // TODO: Замінити на new DictionaryFileSystem(...)
                        Dictionary dict = new Dictionary(this.language, targetLang, additionalName); 
                        loadedDicts.put(dictName, dict);
                         
                    } catch (IllegalArgumentException iae) {
                         // Спіймає помилку, якщо targetCode є, але не є валідним кодом мови (наприклад, 'zx')
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
        System.out.println(dictionaries.values());
        return new ArrayList<>(dictionaries.values());
    }
}
