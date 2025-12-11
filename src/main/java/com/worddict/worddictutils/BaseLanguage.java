package com.worddict.worddictutils;

import java.util.*;
import com.worddict.worddictcore.Language;

/**
 * Manages all dictionaries that use the same source language.
 *
 * Each BaseLanguage groups dictionaries like en→uk, en→de, etc.
 * Implementations define how dictionaries are stored (e.g., filesystem).
 */
public abstract class BaseLanguage {
    /** The source language */
    protected final Language language;

    protected BaseLanguage(Language language) {
        this.language = Objects.requireNonNull(language);
    }

    /** Returns the source language */
    public Language getLanguage() {
        return language;
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

}
