package com.worddict.worddictutils;

import com.worddict.worddictutils.strategies.*;
import com.worddict.worddictcore.Language;

import java.util.*;
import java.io.IOException;

/**
 * Manages all dictionaries that use the same source language.
 *
 * Each BaseLanguage groups dictionaries like en→uk, en→de, etc.
 * Implementations define how dictionaries are stored (e.g., filesystem).
 */
public abstract class BaseLanguage {
    /** The source language */
    protected final Language language;
    private final Strategy strategy;
    protected final Map<String, Dictionary> dictionaries = new HashMap<>();

    protected BaseLanguage(Language language) {
        this.language = Objects.requireNonNull(language);
        this.strategy = Strategy.getStrategy(this.language);
    }

    /** Returns the source language */
    public Language getLanguage() {
        return language;
    }

    /** Returns the source language */
    public Strategy getStrategy() {
        return strategy;
    }

    /**
     * Creates a new dictionary for the given target language.
     */
    public Dictionary createDictionary(Language targetLanguage) {
        return  createDictionary(targetLanguage, "");
    }

    /**
     * Creates a new dictionary with an optional custom name.
     * Name + additionalName must be unique.
     */
    public abstract Dictionary createDictionary(Language targetLanguage, String additionalName);

    /**
     * Deletes a dictionary and its data.
     */
    public abstract void removeDictionary(Dictionary dictionary);

    /**
     * Lists all dictionaries for this source language.
     */
    public abstract List<Dictionary> listDictionaries();

    /**
     * Ensures the core storage area for the base language is initialized.
     * This method is responsible for setting up the necessary root structure
     * for the language's data.
     *
     * @throws Exception if storage setup fails.
     */
    public abstract void setupLanguageStorage() throws Exception;

    /**
     * Ensures the centralized audio storage directory is initialized.
     * This directory is shared by all dictionaries within this base
     * language for storing sound files.
     *
     * @throws Exception if storage setup fails.
     */
    public abstract void setupSoundsStorage() throws Exception;
    
    /**
     * Generates a filesystem-safe dictionary name.
     * If additionalName contains no allowed characters, it is ignored.
     *
     * Examples:
     *   getFoollDictionaryName("uk")          → "en-uk"
     *   getFoolDictionaryName("uk", "Full")  → "en-uk-full"
     *   getFoolDictionaryName("uk", "тест")  → "en-uk"
     *   getFoolDictionaryName("fr", "éàöü")  → "en-fr"
     *
     * @param target target language code
     * @param additionalName optional custom part of the name
     * @return normalized, filesystem-safe dictionary name
     */
    public String getFullDictionaryName(String target, String additionalName) {
        return language.getLanguageCode() + "-" + getDictionaryName(target,additionalName);
    }

    public String getDictionaryName(String target, String additionalName) {
        if (additionalName == null || additionalName.trim().isEmpty()) {
            return sanitize(target);
        }

        String normalized = additionalName.trim().replace(" ", "-");
        return sanitize(target + "-" + normalized);
    }

    /**
     * Normalizes a string for safe usage as a filename. Removes characters
     * forbidden on common filesystems and collapses multiple separators.
     *
     * @param s input string
     * @return sanitized filename-safe string
     */
    private static String sanitize(String s) {
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|]", "-");
        cleaned = cleaned.replaceAll("[^A-Za-z0-9\\-_]", "-");
        cleaned = cleaned.replaceAll("-{2,}", "-");
        cleaned = cleaned.replaceAll("^-|-$", "");
        return cleaned.toLowerCase(Locale.ROOT);
    }

    /**
     * Retrieves a dictionary by its full name from the registry.
     *
     * @param dictName the dictionary identifier (e.g., "uk" or "uk-special")
     * @return the {@link Dictionary} object associated with the given name
     * @throws IllegalArgumentException if the dictionary isn't found or is blank.
     */
    public Dictionary getDictionary(String dictName) {
        if (dictName == null || dictName.isBlank()) {
            throw new IllegalArgumentException("Dictionary name cannot be empty");
        }
        String sanitizedName = sanitize(dictName);
        Dictionary dict = dictionaries.get(sanitizedName);

        if (dict == null) {
            var m = "Dictionary '" + dictName + "' not found. " +
                "Please create it first using 'create-dict'.";
            throw new IllegalArgumentException(m);
        }
        return dict;
    }
}
