package com.worddict.worddictutils.strategies;

import com.worddict.worddictcore.Language;
import java.text.Normalizer;

public class Strategy {
    public String getBucket(String word) {
        if (word == null || word.isBlank()) return "_";
        return word.trim().substring(0, 1).toUpperCase();
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
}
