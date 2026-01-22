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
import java.util.List;
import java.util.stream.Stream;
import java.time.LocalDateTime;

public class DictionaryFileSystem extends Dictionary {

    private final BaseLanguageFileSystem parentLanguage;
    private final Path dictionaryPath;

    public DictionaryFileSystem(BaseLanguageFileSystem parent, Language targetLanguage, String additionalName) {
        super(parent, targetLanguage, additionalName);
        this.parentLanguage = parent;
        this.dictionaryPath = parent.getDictionaryPath(this.getName());
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
            if (isNewWord) updateStats(bucket, 1);
        } catch (IOException e) {
            throw new RuntimeException("Save failed", e);
        }
    }

    public boolean deleteWord(String wordText) {
        String fileName = strategy.getFileName(wordText);
        if (fileName.isEmpty()) return false;

        String bucket = strategy.getBucket(fileName);
        Path filePath = dictionaryPath.resolve(bucket).resolve(fileName + ".json");

        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                
                updateStats(bucket, -1);
                cleanUpEmptyBucket(filePath.getParent());
                return true;
            }
        } catch (IOException e) {
            throw new RuntimeException("Delete failed", e);
        }
        return false;
    }

    private void updateStats(String bucket, int delta) {
        if (!Boolean.getBoolean("update.stats")) return;

        Path statsDir = getStatsDir();
        Path statsFile = statsDir.resolve(bucket + ".json");

        try {
            if (Files.notExists(statsDir)) {
                Files.createDirectories(statsDir);
            }

            JSONObject statsJson = getStatsJson(statsFile);
            updateCounter(statsJson, delta);
            safeSaving(statsFile, statsJson);

        } catch (IOException e) {
            System.err.println("Stats error for " + getName() + " [" + bucket + "]: " + e.getMessage());
        }
    }

    @Override
    public int wordsCount() {
        Path statsDir = getStatsDir();
        if (Files.notExists(statsDir)) {
            return 0;
        }

        try (Stream<Path> files = Files.list(statsDir)) {
            return files
                .filter(path -> path.toString().endsWith(".json"))
                // Читаємо кожен файл статистики та витягуємо total_words
                .mapToInt(this::readCountFromFile)
                .sum();
        } catch (IOException e) {
            System.err.println("Error calculating words count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Lists words using an optimized bucket search based on the prefix.
     * If no prefix is provided, it calls {@link #listAllWords()} for a full scan.
     *
     * @param prefix Optional prefix to filter the results (case-insensitive).
     * @return A sorted list of word names.
     */
    public List<String> listWords(String prefix) {
        if (Files.notExists(dictionaryPath)) return List.of();
        if (prefix == null || prefix.isBlank()) {
            return listAllWords();
        }

        String cleanPrefix = prefix.trim().toLowerCase();
        String bucket = strategy.getBucket(cleanPrefix);
        Path bucketPath = dictionaryPath.resolve(bucket);

        if (Files.notExists(bucketPath)) return List.of();

        try (Stream<Path> files = Files.list(bucketPath)) {
            return files
                .filter(path -> path.toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replace(".json", ""))
                .filter(name -> {
                        String searchBase = strategy.normalize(name);
                        return searchBase.startsWith(cleanPrefix);
                    })
                .sorted()
                .toList();
        } catch (IOException e) {
            System.err.println("Error reading bucket " + bucket + ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Performs a complete scan of the dictionary structure to find all words.
     * Searches up to 2 levels deep to cover all alphabet/prefix buckets.
     *
     * @return A sorted list of all word names in the dictionary.
     */
    public List<String> listAllWords() {
        if (Files.notExists(dictionaryPath)) return List.of();

        try (Stream<Path> walk = Files.walk(dictionaryPath, 2)) {
            return walk
                .filter(path -> path.toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replace(".json", ""))
                .sorted()
                .toList();
        } catch (IOException e) {
            System.err.println("Error scanning all dictionary words: " + e.getMessage());
            return List.of();
        }
    }

    private int readCountFromFile(Path statsFile) {
        try {
            String content = Files.readString(statsFile, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
            return json.optInt("total_words", 0);
        } catch (IOException e) {
            return 0;
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

    private void updateCounter(JSONObject statsJson, int delta) {
        int newCount = statsJson.optInt("total_words", 0) + delta;
        statsJson.put("total_words", Math.max(0, newCount));
        statsJson.put("last_updated", LocalDateTime.now().toString());
    }

    private void cleanUpEmptyBucket(Path bucketDir) {
        try (Stream<Path> s = Files.list(bucketDir)) {
            if (!s.findAny().isPresent()) {
                Files.delete(bucketDir);
            }
        } catch (IOException ignored) {}
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
