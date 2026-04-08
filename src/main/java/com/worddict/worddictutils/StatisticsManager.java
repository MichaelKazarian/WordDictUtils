package com.worddict.worddictutils;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Orchestrates asynchronous statistics collection for dictionary access events.
 * <p>
 * This manager acts as a thread-safe dispatcher that offloads blocking I/O operations
 * (such as file updates or counter increments) to a dedicated background executor.
 * This ensures that the main web server threads remain responsive while analytics
 * are processed in a fire-and-forget manner.
 * </p>
 */
public class StatisticsManager {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Records a successful word lookup event.
     * <p>
     * Dispatches a task to increment the hit counter for a word that was
     * successfully found in the dictionary.
     * </p>
     *
     * @param dict the dictionary instance where the word was found
     * @param word the text of the accessed word
     */
    public static void afterGetSuccess(Dictionary dict, String word) {
        executor.submit(() -> dict.processCounter(word, false));
    }

    /**
     * Records a failed word lookup event.
     * <p>
     * Dispatches a task to log or increment a counter for a word that is
     * missing from the dictionary. This data is typically used to identify
     * high-priority words for future translation.
     * </p>
     *
     * @param dict the dictionary instance where the lookup failed
     * @param word the text of the requested word
     */
    public static void afterWordNotFound(Dictionary dict, String word) {
        executor.submit(() -> dict.processCounter(word, true));
    }

    /**
     * Handles cases where the requested dictionary does not exist.
     * <p>
     * This method asynchronously attempts to create the missing dictionary first.
     * If the creation is successful, it chains a call to record the missing word
     * within the newly created dictionary instance.
     * </p>
     *
     * @param source the source language code (e.g., "en")
     * @param target the target language code (e.g., "uk")
     * @param word   the specific word that triggered the search
     */
    public static void afterDictionaryNotFound(String source, String target, String word) {
        executor.submit(() -> {
                Dictionary.createRequestedDictionary(source, target).ifPresent(newDict -> {
                        afterWordNotFound(newDict, word);
                    });
            });
    }
}
