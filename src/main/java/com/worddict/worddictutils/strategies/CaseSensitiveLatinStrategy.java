package com.worddict.worddictutils.strategies;

public class CaseSensitiveLatinStrategy extends LatinStrategy {
    /** Визначає ім'я папки (наприклад, "T") */
    @Override
    public String getFileName(String word) {
        System.out.println(word);
         return word.trim();
    }

    /**
     * Indicates that the strategy is case-sensitive.
     * Distinguishes between uppercase and lowercase letters during lookups.
     */
    @Override
    public boolean isCaseInsensitive() {
        return false;
    }
} 
