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
    private void configure() {
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
        start(8080);
    }

    public void start(int port) {
        WebServer.builder()
            .port(port)
            .routing(this::setupRouting)
            .build()
            .start();
    }

    public static void main(String[] args) {
        new DictionaryWebApi().start();
    }
}
