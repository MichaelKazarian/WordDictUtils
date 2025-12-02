package com.worddict.worddictutils;

import java.util.Collection;
import java.util.Optional;

/**
 * Global dictionary manager – single entry point to all dictionaries.
 * Allows different storage backends (file system, database, cloud, etc.).
 */
public interface DictionaryManager {

    /**
     * Registers a new source language implementation with the dictionary manager.
     * This method is typically called once during the application startup/initialization 
     * phase to configure the available language backends.
     * * @param baseLanguage The concrete storage implementation (e.g., FileSystemBaseLanguage) 
     * for a specific source language (e.g., 'en', 'uk'). 
     * Must not be null, and its language code must be unique.
     * @throws IllegalArgumentException if the provided BaseLanguage or its code is null.
     */

    void addBaseLanguage(BaseLanguage baseLanguage) throws IllegalArgumentException;
    /**
     * Returns the BaseLanguage implementation for the given source language code (case-insensitive).
     *
     * @param sourceLangCode The two-letter ISO code of the source language (e.g., "en", "uk").
     * @return An **Optional** containing the BaseLanguage if it is registered, 
     * or {@code Optional.empty()} if no implementation is found for the given code.
     */
    public Optional<BaseLanguage> getBaseLanguage(String sourceLangCode);

    /**
     * Returns all registered source languages.
     */
    Collection<BaseLanguage> getBaseLanguages();
}
