package com.worddict.web;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import com.worddict.worddictutils.DictionaryManagerFileSystem;
import com.worddict.worddictutils.Dictionary;
import com.worddict.worddictcore.Word;
import java.util.Optional;

public class DictionaryWebApi {
    private final int port;
    private final String dictPath;

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
        String source = req.path().pathParameters().get("source");
        String target = req.path().pathParameters().get("target");
        String word   = req.path().pathParameters().get("word");
        res.send("Searching: " + word + " (" + source + " -> " + target + ")");
    }
    
    public void start() {
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
