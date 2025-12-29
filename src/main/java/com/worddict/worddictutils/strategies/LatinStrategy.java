package com.worddict.worddictutils.strategies;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class LatinStrategy extends Strategy {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

     /** Визначає ім'я папки (наприклад, "t") */
    @Override
    public String getBucket(String word) {
        String w = super.getBucket(word);
        // 1. Нормалізація: "ü" -> "u" + "umlaut"
        String normalized = Normalizer.normalize(w, Normalizer.Form.NFD);
        // 2. Видаляємо тільки діакритичні знаки
        String baseOnly = DIACRITICS.matcher(normalized).replaceAll("");
        if (baseOnly.isEmpty()) return "_";
        // 3. Перша літера в нижньому регістрі (для ext4)
        return baseOnly.substring(0, 1).toUpperCase();
    }
}
