package com.worddict.worddictutils;

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
     * <p>
     * The method uses the strategy's bucket system to narrow down the search to a specific 
     * directory, then filters files by prefix. If {@code ignoreCase} is true, the search 
     * is case-insensitive, but the resulting list preserves the original file casing.
     * </p>
     *
     * @param prefix     the string that word names should start with
     * @param limit      the maximum number of results to return (if <= 0, returns all)
     * @param ignoreCase if true, "Apple" will be found by "ap"
     * @return a sorted list of word names matching the criteria
     */
    public List<String> listWords(String prefix, int limit, boolean ignoreCase) {
        if (Files.notExists(dictionaryPath)) return List.of();

        if (prefix == null || prefix.isBlank()) {
            return listAllWords();
        }

        String cleanPrefix = prefix.trim();
        if (ignoreCase) cleanPrefix = cleanPrefix.toLowerCase();
        String bucket = strategy.getBucket(cleanPrefix);
        Path bucketPath = dictionaryPath.resolve(bucket);

        if (Files.notExists(bucketPath)) return List.of();

        try (Stream<Path> files = Files.list(bucketPath)) {
            final String finalPrefix = cleanPrefix;
            Stream<String> wordStream = files
                .filter(path -> path.toString().endsWith(".json"))
                .map(path -> path.getFileName().toString().replace(".json", ""))
                .filter(name -> {
                        String normalized = strategy.normalize(name);
                        if (ignoreCase) return normalized.toLowerCase().startsWith(finalPrefix);
                        return normalized.startsWith(finalPrefix);
                    })
                .sorted();
            if (limit > 0) {
                wordStream = wordStream.limit(limit);
            }
            return wordStream.toList();
        } catch (IOException e) {
            System.err.println("Error reading bucket " + bucket + ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Внутрішній метод для отримання шляху до файлу слова.
     * Централізує логіку формування шляху: [dictionaryPath]/[bucket]/[word].json
     */
    private Path getWordPath(String wordText) {
        String fileName = strategy.getFileName(wordText);
        if (fileName.isEmpty()) return null;

        String bucket = strategy.getBucket(fileName);
        return dictionaryPath.resolve(bucket).resolve(fileName + ".json");
    }

    @Override
    public boolean isPresent(String wordText) {
        Path filePath = getWordPath(wordText);
        return filePath != null && Files.exists(filePath);
    }

    @Override
    public String getWordJson(String wordText) {
        if (!isPresent(wordText)) {
            return null;
        }

        try {
            return Files.readString(getWordPath(wordText), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error reading word file '" + wordText + "': " + e.getMessage());
            return null;
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
