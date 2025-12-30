package com.worddict.worddictutils;

import com.worddict.worddictutils.strategies.*;
import com.worddict.worddictcore.Language;
import com.worddict.worddictcore.Word;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.time.LocalDateTime;

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
            boolean isNewWord = Files.notExists(filePath);
            Files.writeString(filePath, word.toJsonObject().toString(2), StandardCharsets.UTF_8);
            if (isNewWord) updateStats(bucket);
        } catch (IOException e) {
            throw new RuntimeException("Save failed", e);
        }
    }

    private void updateStats(String bucket) {
        if (!Boolean.getBoolean("update.stats")) return;

        Path statsDir = getStatsDir();
        Path statsFile = statsDir.resolve(bucket + ".json");

        try {
            if (Files.notExists(statsDir)) {
                Files.createDirectories(statsDir);
            }

            JSONObject statsJson = getStatsJson(statsFile);
            updateCounter(statsJson);
            safeSaving(statsFile, statsJson);

        } catch (IOException e) {
            System.err.println("Stats error for " + getName() + " [" + bucket + "]: " + e.getMessage());
        }
    }

    private Path getStatsDir() {
        // storage/en/--stats/de/A.json
        return dictionaryPath.getParent().resolve("--stats").resolve(getName());
    }

    private JSONObject getStatsJson(Path statsFile) throws IOException {
        JSONObject statsJson;
        if (Files.exists(statsFile)) {
            statsJson = new JSONObject(Files.readString(statsFile, StandardCharsets.UTF_8));
        } else {
            statsJson = new JSONObject();
            statsJson.put("total_words", 0);
        }
        return statsJson;
    }

    private void updateCounter(JSONObject statsJson) {
        statsJson.put("total_words", statsJson.getInt("total_words") + 1);
        statsJson.put("last_updated", LocalDateTime.now().toString());
    }

    private void safeSaving(Path statsFile, JSONObject statsJson) throws IOException {
        Path tempFile = statsFile.getParent().resolve(statsFile.getFileName().toString() + ".tmp");
        System.out.println(tempFile);
        // Атомарний запис
        Files.writeString(tempFile, statsJson.toString(2), StandardCharsets.UTF_8);
        Files.move(tempFile, statsFile,
                   StandardCopyOption.REPLACE_EXISTING,
                   StandardCopyOption.ATOMIC_MOVE);
    }
}
