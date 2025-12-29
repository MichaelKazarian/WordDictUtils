package com.worddict.worddictutils.strategies;

public class CaseSensitiveLatinStrategy extends LatinStrategy {
    /** Визначає ім'я папки (наприклад, "T") */
    @Override
    public String getFileName(String word) {
        System.out.println(word);
         return word.trim();
    }
} 
