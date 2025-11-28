package com.worddict.worddictutils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.worddict.worddictcore.Language;

/**
 * File-system-based implementation of DictionaryManager.
 * Singleton.
 */
public class DictionaryManagerFileSystem implements DictionaryManager {

    private static volatile DictionaryManagerFileSystem instance;

    // Key: lowercased ISO code, e.g. "en", "es"
    private final Map<String, BaseLanguage> languages = new ConcurrentHashMap<>();

    private DictionaryManagerFileSystem() {
    }

    public static DictionaryManagerFileSystem getInstance() {
        if (instance == null) {
            synchronized (DictionaryManagerFileSystem.class) {
                if (instance == null) {
                    instance = new DictionaryManagerFileSystem();
                }
            }
        }
        return instance;
    }

    @Override
    public void addBaseLanguage(BaseLanguage baseLanguage) {
        if (baseLanguage == null || baseLanguage.getLanguage().getLanguageCode() == null) {
            throw new IllegalArgumentException("BaseLanguage or its code cannot be null");
        }
        languages.put(baseLanguage.getLanguage().getLanguageCode().toLowerCase(), baseLanguage);
    }

    @Override
    public BaseLanguage getBaseLanguage(String sourceLangCode) {
        if (sourceLangCode == null) return null;
        return languages.get(sourceLangCode.toLowerCase());
    }

    @Override
    public Collection<BaseLanguage> getBaseLanguages() {
        return Collections.unmodifiableCollection(languages.values());
    }

    /**
     * For testing only: clear all loaded languages.
     */
    void resetForTests() {
        languages.clear();
    }
}
