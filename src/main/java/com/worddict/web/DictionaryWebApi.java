package com.worddict.web;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import com.worddict.worddictutils.BaseLanguage;
import com.worddict.worddictutils.DictionaryManagerFileSystem;
import com.worddict.worddictutils.Dictionary;
import com.worddict.worddictutils.DictionaryFileSystem;
import com.worddict.worddictcore.Word;

import java.util.Optional;
import java.util.List;

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
            .get("/api/v1/{source}/{target}/{word}", this::handleGetWord);
    }

    private void handleRoot(ServerRequest req, ServerResponse res) {
        res.send("It works :)\n");
    }

    private void handleGetWord(ServerRequest req, ServerResponse res) {
        // 1. Отримуємо параметри з URL
        String source = req.path().pathParameters().get("source");
        String target = req.path().pathParameters().get("target");
        String prefix = req.path().pathParameters().get("word"); // 'word' тут виступає як префікс для пошуку

        int limit = 10;

        try {
            // 2. Шукаємо мову
            Optional<BaseLanguage> baseOpt = manager.getBaseLanguage(source);
            if (baseOpt.isEmpty()) {
                res.status(404).send("Source language '" + source + "' not found.\n");
                return;
            }

            // 3. Шукаємо словник
            Optional<Dictionary> dictOpt = baseOpt.get().listDictionaries().stream()
                .filter(d -> d.getName().equalsIgnoreCase(target))
                .findFirst();

            if (dictOpt.isEmpty()) {
                res.status(404).send("Dictionary '" + target + "' not found for " + source + ".\n");
                return;
            }

            // 4. Логіка пошуку за префіксом (як у вашому CLI)
            DictionaryFileSystem dictFs = (DictionaryFileSystem) dictOpt.get();
        
            // Визначаємо ignoreCase на основі стратегії мови
            boolean finalIgnoreCase = baseOpt.get().getStrategy().isCaseInsensitive();
        
            List<String> words = dictFs.listWords(prefix, limit, finalIgnoreCase);

            if (words.isEmpty()) {
                res.status(404).send("No words found starting with '" + prefix + "'\n");
            } else {
                // З'єднуємо список слів у текстову відповідь
                String responseBody = String.join("\n", words) + "\n";
                res.send(responseBody);
            }

        } catch (Exception e) {
            res.status(500).send("Server Error: " + e.getMessage() + "\n");
        }
    }
    
    public void start() {
        try {
            // Ми не можемо перевірити 'initialized' зовні (він private),
            // але ми можемо обгорнути виклик або додати публічний метод ініціалізації з перевіркою.
            manager.init(this.dictPath);
        } catch (IllegalStateException e) {
            // Якщо вже ініціалізовано — ігноруємо, це нормально для синглтона
            System.out.println("Dictionary Manager already initialized.");
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
