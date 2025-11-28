package com.worddict.worddictutils;

import java.util.Collection;

/**
 * Global dictionary manager – single entry point to all dictionaries.
 * Allows different storage backends (file system, database, cloud, etc.).
 */
public interface DictionaryManager {

    /**
     * Registers a new source language with its storage implementation.
     * Usually called once at application startup.
     */
    void addBaseLanguage(BaseLanguage baseLanguage);

    /**
     * Returns BaseLanguage for given source language code (case-insensitive).
     * @return BaseLanguage or null if not registered
     */
    BaseLanguage getBaseLanguage(String sourceLangCode);

    /**
     * Returns all registered source languages.
     */
    Collection<BaseLanguage> getBaseLanguages();
}
