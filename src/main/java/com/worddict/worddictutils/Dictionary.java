package com.worddict.worddictutils;

import com.worddict.worddictutils.strategies.*;
import com.worddict.worddictcore.Language;
import com.worddict.worddictcore.Word;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Base class for a dictionary of one language pair (source → target).
 * Provides minimal default implementations; subclasses must override for storage backends.
 */
public abstract class Dictionary {
    protected final Language targetLanguage;
    protected final Strategy strategy;
    protected final BaseLanguage parentLanguage;
    protected String additionalName;
    protected DictionaryProperties props = new DictionaryProperties();

    /** Creates a dictionary for a language pair. */
    public Dictionary(BaseLanguage parent, Language targetLanguage) {
        this(parent, targetLanguage, "");
    }

    public Dictionary(BaseLanguage parent, Language targetLanguage, String additionalName) {
        this.targetLanguage = Objects.requireNonNull(targetLanguage);
        parentLanguage = parent;
        if (parentLanguage.getLanguage().equals(targetLanguage)) {
            throw new IllegalArgumentException("Source and target languages must differ");
        }
        this.additionalName = additionalName;
        this.strategy = parentLanguage.getStrategy();
    }

    /** Returns the target language. */
    public Language getTargetLanguage() {
        return targetLanguage;
    }

    public String getAdditionalName() {
        return additionalName;
    }

    public String getName() {
        String r = targetLanguage.getLanguageCode();
        if (!additionalName.isEmpty()) r += "-"+additionalName;
        return r;
    }

    // === Word Operations ===

    /** Returns a word by text (default: empty). */
    public Optional<Word> getWord(String wordText) {
        return Optional.empty();
    }

    /** Returns true if the word exists. */
    public boolean containsWord(String wordText) {
        return getWord(wordText).isPresent();
    }

    /** Saves or updates a word (default: no-op). */
    public void saveWord(Word word) {
        // no-op
    }

    /** Deletes a word (default: false). */
    public boolean deleteWord(String wordText) {
        return false;
    }

    /** Finds words by prefix (default: empty list). */
    public List<Word> findWords(String prefix) {
        return List.of();
    }

    /**
     * Lists words with default settings: a limit of 10 results and case sensitivity
     * defined by the specific language strategy.
     *
     * @param prefix optional prefix to filter the results
     * @return a sorted list of up to 10 word names
     * @see Strategy#isCaseInsensitive()
     * @see #listWords(String, int, boolean)
     */
    public List<String> listWords(String prefix) {
        return listWords(prefix, 10, strategy.isCaseInsensitive());
    }

    public List<String> listWords(String prefix, int limit, boolean ignoreCase) {
        return new ArrayList<String>();
    }

    /** Returns total word count (default: 0). */
    public int wordsCount() {
        return 0;
    }

    /**
     * Searches for multiple words in the dictionary and returns those that exist.
     * <p>
     * This method is case-insensitive and handles input normalization (trimming).
     * It is optimized for batch processing to reduce I/O overhead.
     * </p>
     *
     * @param words a list of words to check
     * @return a list of words found in the dictionary, normalized to lower case
     */
    public abstract List<String> find(List<String> words);

    /**
     * Checks if a single word exists in the dictionary.
     * <p>
     * This implementation relies on the {@link #find(List)} method to ensure
     * consistent normalization and lookup logic.
     * </p>
     *
     * @param wordText the word to check
     * @return {@code true} if the word exists, {@code false} otherwise
     */
    public boolean isPresent(String wordText) {
        if (wordText == null || wordText.isBlank()) {
            return false;
        }
        return !find(List.of(wordText)).isEmpty();
    }

    /**
     * Повертає повний JSON вміст слова або null, якщо слово не знайдено.
     */
    public abstract String getWordJson(String wordText);

    // === Audio Operations ===

    /** Returns number of audio samples (default: 0). */
    public int audioSamplesCount(String wordText) {
        return 0;
    }

    /** Saves audio for a word (default: no-op). */
    public void addAudio(String wordText, byte[] audioData) {
        // no-op
    }

    /** Retrieves audio bytes (default: empty). */
    public Optional<byte[]> getAudio(String wordText) {
        return Optional.empty();
    }

    /** Deletes audio (default: false). */
    public boolean deleteAudio(String wordText) {
        return false;
    }

    public DictionaryProperties getProperties() {
        return props;
    }

    /**
     * Records an access event for a specific word to track usage analytics.
     * <p>
     * This method acts as a notification hook to signal that a word has been
     * requested. It allows implementations to maintain hit counters, log
     * missing entries, or perform other analytics-driven operations.
     * </p>
     * <p>
     * <b>Note:</b> This operation is intended to be invoked asynchronously
     * to prevent statistical processing from blocking the primary task execution.
     * </p>
     *
     * @param word      the text of the word being accessed or searched
     * @param isMissing {@code true} if the word was not found in the dictionary;
     * {@code false} if the lookup was successful
     */
    public void processCounter(String word, boolean isMissing) {
        // Default implementation does nothing
    }

    /**
     * Creates a new dictionary if the base language exists.
     * @param sourceLangCode source language (e.g. "en")
     * @param targetLangCode target language (e.g. "uk")
     * @return Optional with the dictionary instance, or empty if base lang not found.
     */
    public static Optional<Dictionary> createRequestedDictionary(String sourceLangCode, String targetLangCode) {
        DictionaryManagerFileSystem manager = DictionaryManagerFileSystem.INSTANCE;
        return manager.getBaseLanguage(sourceLangCode).map(base -> {
                try {
                    // Check if it was created by a concurrent request
                    Optional<Dictionary> existing = base.getDictionary(targetLangCode);
                    if (existing.isPresent()) return existing.get();

                    // Physical creation (folders, properties.json)
                    Language target = Language.getLanguageByCode(targetLangCode);
                    Dictionary newDict = base.createDictionary(target);
                    System.out.println("[AutoCreate] Successfully initialized " + sourceLangCode + "-" + targetLangCode);
                    return newDict;
                } catch (Exception e) {
                    System.err.println("[AutoCreate] Critical error: " + e.getMessage());
                    return null; // map() will convert this to Optional.empty()
                }
            });
    }

    @Override
    public String toString() {
        return String.format("Dictionary[%s, words: %d]", getName(), wordsCount());
    }

    /**
     * Повертає метадані словника у форматі JSONObject.
     */
    public JSONObject toJsonObject() {
        JSONObject json = props.toJsonObject();
        json.put("id", getName());
        json.put("wordsCount", wordsCount());
        return json;
    }
}
