package com.worddict.worddictutils;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "worddict",
    version = "1.0",
    mixinStandardHelpOptions = true,
    description = "Утиліти для створення словників WordDolphin",
    subcommands = { ImportOrgCommand.class }
)
public class WordDictUtils implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new WordDictUtils()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Використання: worddict <команда>\n" +
                         "Доступні команди: import-org");
    }
}
