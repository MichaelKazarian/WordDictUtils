package com.worddict.worddictutils;

import com.worddict.web.DictionaryWebApi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.lang.Thread;

@Command(name = "run-api", description = "Запуск Helidon Web API сервера")
public class CommandRunApi implements Runnable {

    @Option(names = {"-p", "--port"}, description = "Порт сервера (типово: 8080)", defaultValue = "8080")
    private int port;

    @Option(names = {"-d", "--dicts"}, description = "Шлях до каталогу зі словниками", defaultValue = "./dicts")
    private String dictPath;

    @Override
    public void run() {
        new DictionaryWebApi(port, dictPath).start();
        try {
            // Це змушує поточний потік (main) чекати вічно (або поки не вб'ють процес)
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            // Відновлюємо статус переривання
            Thread.currentThread().interrupt();
            System.err.println("Server execution was interrupted.");
        }
    }
}
