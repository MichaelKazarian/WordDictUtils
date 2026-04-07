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
import com.worddict.worddictcore.Word;

import java.util.Optional;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

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
     * Пошук списку слів за префіксом
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
     * Отримання повного JSON конкретного слова
     */
    private void handleGetWordJson(ServerRequest req, ServerResponse res) {
        String source = req.path().pathParameters().get("source");
        String target = req.path().pathParameters().get("target");
        String wordText = req.path().pathParameters().get("word");

        manager.getBaseLanguage(source)
            .flatMap(base -> base.getDictionary(target))
            .ifPresentOrElse(dict -> {
                    String json = dict.getWordJson(wordText);
                    if (json != null) {
                        res.status(io.helidon.http.Status.OK_200)
                            .header(io.helidon.http.HeaderNames.CONTENT_TYPE, "application/json")
                            .send(json);
                    } else {
                        res.status(404).send("Word not found\n");
                    }
                }, () -> res.status(404).send("Dictionary not found\n"));
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
