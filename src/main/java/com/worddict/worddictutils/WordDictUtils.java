// src/main/java/com/worddict/worddictutils/WordDictUtils.java
package com.worddict.worddictutils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "worddict",
    version = "1.0",
    mixinStandardHelpOptions = true,
    description = "Утиліти для створення словників WordDolphin"
)
public class WordDictUtils implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new WordDictUtils()).execute(args);
        System.exit(exitCode);
    }

    @Command(name = "import-org", description = "Імпорт з org-mode таблиці")
    void importOrg(
            @Parameters(index = "0", paramLabel = "INPUT", description = "Шлях до .org файлу") String input,
            @Parameters(index = "1", paramLabel = "OUTPUT", description = "Папка для словника (en-uk/)") String output) {
        
        System.out.println("INPUT  → " + input);
        System.out.println("OUTPUT → " + output);
    }

    @Override
    public void run() {
        System.out.println("Використання: worddict import-org <input.org> <output-dir/>");
    }
}
