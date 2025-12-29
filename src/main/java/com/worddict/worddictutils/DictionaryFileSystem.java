package com.worddict.worddictutils;

import com.worddict.worddictutils.strategies.*;
import com.worddict.worddictcore.Language;
import com.worddict.worddictcore.Word;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DictionaryFileSystem extends Dictionary {

    private final BaseLanguageFileSystem parentLanguage;
    private final Path dictionaryPath;
    private final Strategy strategy;

    public DictionaryFileSystem(BaseLanguageFileSystem parent, Language targetLanguage, String additionalName) {
        super(parent.getLanguage(), targetLanguage, additionalName);
        this.parentLanguage = parent;
        this.dictionaryPath = parent.getDictionaryPath(this.getName());
        this.strategy = Strategy.getStrategy(targetLanguage);
    }

    @Override
    public void saveWord(Word word) {
        String wordText = strategy.getFileName(word.getWord());
        if (wordText.isEmpty()) return;

        // Структура: [dictionaryPath]/S/spring.json
        String bucket = strategy.getBucket(wordText);
        Path subDir = dictionaryPath.resolve(bucket);
        Path filePath = subDir.resolve(wordText + ".json");

        try {
            if (Files.notExists(subDir)) Files.createDirectories(subDir);
            Files.writeString(filePath, word.toJsonObject().toString(2), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Save failed", e);
        }
    }
}
