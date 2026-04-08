package com.worddict.web;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.http.Http;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;

import com.worddict.worddictutils.BaseLanguage;
import com.worddict.worddictutils.DictionaryManagerFileSystem;
import com.worddict.worddictutils.Dictionary;
import com.worddict.worddictutils.DictionaryFileSystem;
import com.worddict.worddictutils.StatisticsManager;
import com.worddict.worddictcore.Word;

import java.util.Optional;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * REST API Controller for the Word Dictionary service.
 * <p>
 * This class serves as the entry point for all web-based interactions with the dictionary
 * system. It maps incoming HTTP requests (via Helidon SE) to specific language and
 * dictionary operations, including word lookups, prefix searches, and metadata retrieval.
 * </p>
 * <p>
 * <b>Key Responsibilities:</b>
 * <ul>
 * <li>Request Routing: Directs language and dictionary queries to the appropriate {@code BaseLanguage} providers.</li>
 * <li>Response Formatting: Ensures all outputs are delivered as standardized, client-friendly JSON structures.</li>
 * <li>Integration with Statistics: Transparently triggers asynchronous analytics via {@link StatisticsManager}
 * for both successful hits and missing resources.</li>
 * <li>Self-Growth: Facilitates automatic dictionary creation by signaling missing language pairs during lookup.</li>
 * </ul>
 * </p>
 */
public class DictionaryWebApi {
    private final int port;
    private final String dictPath;
    DictionaryManagerFileSystem manager = DictionaryManagerFileSystem.INSTANCE;

    public DictionaryWebApi(int port, String dictPath) {
        this.port = port;
        this.dictPath = dictPath;
    }
    
    private void setupRouting(HttpRouting.Builder routing) {
        routing.get("/", this::handleRoot)
            .get("/api/v1/langs", this::handleListLangs)
            .get("/api/v1/dicts/{source}", this::handleListDicts)
            // q - query (пошук списку слів за префіксом)
            .get("/api/v1/q/{source}/{target}/{word}", this::handleGetWord)
            // g - get (отримання повного JSON конкретного слова)
            .get("/api/v1/g/{source}/{target}/{word}", this::handleGetWordJson);
    }

    private void handleRoot(ServerRequest req, ServerResponse res) {
        res.send("It works :)\n");
    }

    /**
     * Handles the GET request to list all available base languages.
     * <p>
     * This method retrieves all registered base languages from the manager and
     * constructs a JSON array containing the language code and the total count
     * of associated dictionaries for each language.
     * </p>
     * <p>
     * <b>Response Format:</b>
     * <pre>
     * [
     * {"code": "en", "dictionaries": 5},
     * {"code": "de", "dictionaries": 2}
     * ]
     * </pre>
     * </p>
     *
     * @param req the server request (unused in this specific handler)
     * @param res the server response used to deliver the JSON array of languages
     */
    private void handleListLangs(ServerRequest req, ServerResponse res) {
        var languages = manager.getBaseLanguages();
    
        // Формуємо простий JSON масив об'єктів
        StringBuilder sb = new StringBuilder("[");
        languages.forEach(bl -> {
                String code = bl.getLanguage().getLanguageCode();
                int count = bl.listDictionaries().size();
                sb.append(String.format("{\"code\":\"%s\", \"dictionaries\":%d},", code, count));
            });
        if (sb.length() > 1) sb.setLength(sb.length() - 1); // видаляємо останню кому
        sb.append("]");

        res.status(Status.OK_200)
            .header("Content-Type", "application/json")
            .send(sb.toString());
    }

    /**
     * Handles the GET request to list all dictionaries associated with a specific source language.
     * <p>
     * This method identifies the source language from the path parameters and retrieves
     * its dictionary collection. Each dictionary is serialized into a JSON object
     * containing its metadata.
     * </p>
     * <p>
     * <b>Response Format:</b>
     * <pre>
     * {
     * "source": "en",
     * "dictionaries": [
     * { "target": "uk", "name": "English-Ukrainian", "entries": 1200 },
     * { "target": "de", "name": "English-German", "entries": 850 }
     * ]
     * }
     * </pre>
     * </p>
     *
     * @param req the server request containing the "source" path parameter
     * @param res the server response delivering the JSON representation of dictionaries
     */
    private void handleListDicts(ServerRequest req, ServerResponse res) {
        String source = req.path().pathParameters().get("source");

        manager.getBaseLanguage(source).ifPresentOrElse(base -> {
                var dictionaries = base.listDictionaries();
        
                JSONArray dictsArray = new JSONArray();
                for (Dictionary dict : dictionaries) {
                    dictsArray.put(dict.toJsonObject());
                }
        
                JSONObject result = new JSONObject();
                result.put("source", source);
                result.put("dictionaries", dictsArray);

                res.status(io.helidon.http.Status.OK_200)
                    .header(io.helidon.http.HeaderNames.CONTENT_TYPE, "application/json")
                    .send(result.toString(2));
           
            }, () -> res.status(io.helidon.http.Status.NOT_FOUND_404)
            .send("Source language not found: " + source + "\n"));
    }

    /**
     * Searches for a list of words starting with a specific prefix.
     * <p>
     * This method retrieves the source/target languages and the prefix from the path parameters.
     * It uses the language-specific strategy to determine case sensitivity for the search.
     * The results are limited to 10 entries by default.
     * </p>
     * <p>
     * <b>Response Logic:</b>
     * <ul>
     * <li>If matches found: Returns a newline-separated list of words (plain text).</li>
     * <li>If missing/no matches: Returns a 404 status with an empty response or error message.</li>
     * </ul>
     * </p>
     *
     * @param req the server request containing language codes and the word prefix
     * @param res the server response used to send the list of found words
     */
    private void handleGetWord(ServerRequest req, ServerResponse res) {
        String source = req.path().pathParameters().get("source");
        String target = req.path().pathParameters().get("target");
        String prefix = req.path().pathParameters().get("word");
        // int limit = req.query().get("limit").asInt().orElse(10);
        int limit = 10;

        Optional<BaseLanguage> baseOpt = manager.getBaseLanguage(source);
        if (baseOpt.isEmpty()) {
            res.status(404).send("Language not found: " + source + "\n");
            return;
        }

        BaseLanguage base = baseOpt.get();
        Optional<Dictionary> dictOpt = base.getDictionary(target); //Отримуємо словник
        if (dictOpt.isEmpty()) {
            res.status(404).send("Dictionary not found: " + target + " for " + source + "\n");
            return;
        }

        // Визначаємо ignoreCase на основі стратегії, якщо користувач не вказав інше
        // (У Web API зазвичай використовуємо дефолт стратегії мови)
        boolean ic = base.getStrategy().isCaseInsensitive();
        List<String> words = dictOpt.get().listWords(prefix, limit, ic);
        if (words.isEmpty()) {
            res.status(404).send("No words found for prefix: " + prefix + "\n");
            return;
        }

        res.send(String.join("\n", words) + "\n");
    }

    /**
     * Handles the GET request for a specific word's JSON data.
     * <p>
     * This method retrieves language parameters and the word text from the request path.
     * It attempts to locate the dictionary and the specific word data.
     * </p>
     * <p>
     * <b>Response Logic:</b>
     * <ul>
     * <li>If found: Returns the word's JSON content with a 200 OK status.</li>
     * <li>If missing: Returns an empty JSON object ({}) with a 404 Not Found status
     * to ensure client-side parsing stability.</li>
     * </ul>
     * </p>
     * <p>
     * <b>Asynchronous Operations:</b>
     * All statistical logging is offloaded to the {@link StatisticsManager}. If a dictionary
     * is missing, the system attempts to initialize it automatically while preserving
     * the requested word in the background processing queue.
     * </p>
     *
     * @param req the server request containing path parameters (source, target, word)
     * @param res the server response used to send JSON data or a 404 error status
     */
    private void handleGetWordJson(ServerRequest req, ServerResponse res) {
        final String EMPTY_JSON = "{}";
        String source = req.path().pathParameters().get("source");
        String target = req.path().pathParameters().get("target");
        String wordText = req.path().pathParameters().get("word");

        Optional<Dictionary> dictOpt = manager.getBaseLanguage(source)
                .flatMap(base -> base.getDictionary(target));

        // 1. Handle missing dictionary
        if (dictOpt.isEmpty()) {
            res.status(404)
               .header(io.helidon.http.HeaderNames.CONTENT_TYPE, "application/json")
               .send(EMPTY_JSON);
            StatisticsManager.afterDictionaryNotFound(source, target, wordText);
            return;
        }

        Dictionary dict = dictOpt.get();
        String json = dict.getWordJson(wordText);

        // 2. Handle word result
        if (json != null) {
            res.status(io.helidon.http.Status.OK_200)
               .header(io.helidon.http.HeaderNames.CONTENT_TYPE, "application/json")
               .send(json);
            StatisticsManager.afterGetSuccess(dict, wordText);
        } else {
            // 3. Handle missing word with empty JSON instead of plain text
            res.status(404)
               .header(io.helidon.http.HeaderNames.CONTENT_TYPE, "application/json")
               .send("EMPTY_JSON");
            StatisticsManager.afterWordNotFound(dict, wordText);
        }
    }
    
    public void start() {
        try {
            manager.init(this.dictPath);
        } catch (Exception e) {
            System.err.println("Failed to initialize dictionaries: " + e.getMessage());
            return;
        }
        
        WebServer server = WebServer.builder()
            .port(port)
            .routing(this::setupRouting)
            .build()
            .start();
        
        System.out.println("WEB server started on http://localhost:" + port);
        System.out.println("Dictionaries directory: " + dictPath);
    }

    public static void main(String[] args) {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;
        String path = (args.length > 1) ? args[1] : "./dicts";
        
        new DictionaryWebApi(port, path).start();
    }
}
