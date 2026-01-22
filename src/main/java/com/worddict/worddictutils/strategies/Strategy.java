package com.worddict.worddictutils.strategies;

import com.worddict.worddictcore.Language;
import java.text.Normalizer;

public class Strategy {
    /** Base normalization: trims the string and converts to lower case.
     * Strategies can override this to add specific rules (like removing accents).
     */
    public String normalize(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase();
    }

    public String getBucket(String word) {
        String normalized = normalize(word);
        if (normalized.isEmpty()) return "_";
        return normalized.substring(0, 1).toUpperCase();
    }
    
    public static Strategy getStrategy(Language language) {
        return switch (language.getLanguageCode().toLowerCase()) {
            case "en", "fr", "es", "it", "pt" -> new LatinStrategy();
            case "de" -> new CaseSensitiveLatinStrategy();
            default -> new Strategy();
        };
    }

    /** Визначає ім'я файлу (наприклад, "Test" або "test") */
    public String getFileName(String word) {
        return word.trim().toLowerCase();
    }

    /**
     * Indicates that the strategy is case-insensitive by default.
     * Matches will ignore the difference between uppercase and lowercase letters.
     */
    public boolean isCaseInsensitive() {
        return true;
    }
}
