package com.worddict.worddictutils.strategies;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class LatinStrategy extends Strategy {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    @Override
    public String normalize(String text) {
        String base = super.normalize(text);
        if (base.isEmpty()) return "";
        // "ü" -> "u"
        String decompounded = Normalizer.normalize(base, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decompounded).replaceAll("");
    }

    @Override
    public String getBucket(String word) {
        String baseOnly = normalize(word);
        if (baseOnly.isEmpty()) return "_";
        return baseOnly.substring(0, 1).toUpperCase();
    }
}
