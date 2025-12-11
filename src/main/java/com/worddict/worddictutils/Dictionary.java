package com.worddict.worddictutils;

import com.worddict.worddictcore.Language;
import com.worddict.worddictcore.Word;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Base class for a dictionary of one language pair (source → target).
 * Provides minimal default implementations; subclasses must override for storage backends.
 */
public class Dictionary {

    protected final Language targetLanguage;
    protected String additionalName;

    /** Creates a dictionary for a language pair. */
    public Dictionary(Language sourceLanguage, Language targetLanguage) {
        this(sourceLanguage, targetLanguage, "");
    }

    public Dictionary(Language sourceLanguage, Language targetLanguage, String additionalName) {
        this.targetLanguage = Objects.requireNonNull(targetLanguage);
        if (sourceLanguage.equals(targetLanguage)) {
            throw new IllegalArgumentException("Source and target languages must differ");
        }
        this.additionalName = additionalName;
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

    /** Returns total word count (default: 0). */
    public int wordsCount() {
        return 0;
    }

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

    @Override
    public String toString() {
        return String.format("Dictionary[%s, words: %d]", getName(), wordsCount());
    }
}
