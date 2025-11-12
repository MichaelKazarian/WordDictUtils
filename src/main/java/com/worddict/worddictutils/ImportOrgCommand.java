package com.worddict.worddictutils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "import-org",
    description = "Імпорт словника з org-mode таблиці"
)
public class ImportOrgCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "INPUT", description = "Шлях до .org файлу")
    private String input;

    @Parameters(index = "1", paramLabel = "OUTPUT", description = "Папка для словника (en-uk/)")
    private String output;

    @Override
    public void run() {
        System.out.println("INPUT  → " + input);
        System.out.println("OUTPUT → " + output);
    }
}
