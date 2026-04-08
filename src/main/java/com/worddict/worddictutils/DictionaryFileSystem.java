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
import java.util.Optional;
import java.time.LocalDateTime;

public class DictionaryFileSystem extends Dictionary {

    private final BaseLanguageFileSystem parentLanguage;
    private final Path dictionaryPath;

    public DictionaryFileSystem(BaseLanguageFileSystem parent, Language targetLanguage, String additionalName) {
        super(parent, targetLanguage, additionalName);
        this.parentLanguage = parent;
        this.dictionaryPath = parent.getDictionaryPath(this.getName());
        loadProperties();
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
     * Filesystem-based implementation of word access tracking.
     * <p>
     * This method maintains word counters by managing physical files within a "calls" directory.
     * It uses a naming convention where the filename includes the word and its hit count
     * (e.g., {@code apple.1}).
     * </p>
     * <p>
     * <b>Operational Logic:</b>
     * <ul>
     * <li><b>Missing Words:</b> Prefixed with {@code ~~~} (e.g., {@code ~~~banana.1}) to
     * highlight gaps in the dictionary.</li>
     * <li><b>Successful Lookups:</b> Increments existing counters or creates a new file.
     * If a word was previously marked as missing ({@code ~~~}), the marker is removed
     * upon the first successful access.</li>
     * </ul>
     * </p>
     * <p>
     * Errors during filesystem operations are caught and suppressed to ensure that
     * analytics processing never disrupts the primary dictionary service.
     * </p>
     *
     * @param word      the text of the word being tracked
     * @param isMissing {@code true} to prefix the file as a missing entry,
     * {@code false} for standard hit counting
     */
    @Override
    public void processCounter(String word, boolean isMissing) {
        try {
            Path callsDir = prepareCallsDir();
            String prefix = isMissing ? "~~~" + word + "." : word + ".";
            findCounterFile(callsDir, prefix).ifPresentOrElse(
                                                              this::incrementFile,
                                                              () -> createInitialFile(callsDir, prefix)
                                                              );
            if (!isMissing) {
                removeMissingMarker(callsDir, word);
            }
        } catch (IOException e) {
            // Статистика не повинна валити сервер, тому просто логуємо FINE/DEBUG
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

    /**
     * Завантажує метадані словника з файлу properties.json, якщо він існує.
     */
    private void loadProperties() {
        Path propsPath = dictionaryPath.resolve("properties.json");
        if (Files.exists(propsPath)) {
            try {
                String content = Files.readString(propsPath, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                this.props = new DictionaryProperties(json);
            } catch (IOException e) {
                System.err.println("Warning: Could not read properties.json for " + getName() + ": " + e.getMessage());
            }
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

    /**
     * Ensures the "calls" directory exists within the statistics folder.
     *
     * @return the path to the initialized calls directory
     * @throws IOException if the directory cannot be accessed or created
     */
    private Path prepareCallsDir() throws IOException {
        Path calls = getStatsDir().resolve("calls");
        if (Files.notExists(calls)) Files.createDirectories(calls);
        return calls;
    }

    /**
     * Searches for a counter file in the specified directory that matches the given prefix.
     *
     * @param dir    the directory to search in
     * @param prefix the filename prefix (usually the word plus a dot)
     * @return an Optional containing the found Path, or empty if no file matches the prefix
     * @throws IOException if an I/O error occurs during directory listing
     */
    private Optional<Path> findCounterFile(Path dir, String prefix) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().startsWith(prefix)).findFirst();
        }
    }

    /**
     * Increments the hit counter by renaming the file.
     * <p>
     * This method extracts the numeric suffix from the filename (e.g., from "apple.5" to 5),
     * increments it, and performs an atomic move to update the counter.
     * </p>
     *
     * @param file the current counter file to be incremented
     */
    private void incrementFile(Path file) {
        try {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            int count = Integer.parseInt(name.substring(dot + 1));
            String newName = name.substring(0, dot + 1) + (count + 1);
            Files.move(file, file.resolveSibling(newName), StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {}
    }

    /**
     * Creates a new counter file with an initial count of 1.
     *
     * @param dir    the directory where the file should be created
     * @param prefix the filename prefix, including the word and optional markers
     */
    private void createInitialFile(Path dir, String prefix) {
        try {
            Files.createFile(dir.resolve(prefix + "1"));
        } catch (IOException ignored) {}
    }

    /**
     * Removes the "missing" marker file for a word if it exists.
     * <p>
     * This is typically called when a word that was previously flagged as missing
     * is successfully found or added to the dictionary.
     * </p>
     *
     * @param dir  the directory containing counter files
     * @param word the word whose missing marker (prefix ~~~) should be deleted
     */
    private void removeMissingMarker(Path dir, String word) {
        try {
            findCounterFile(dir, "~~~" + word + ".").ifPresent(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }
}
