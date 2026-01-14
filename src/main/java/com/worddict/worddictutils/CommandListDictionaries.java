package com.worddict.worddictutils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

/**
 * Lists all registered BaseLanguages (source languages) or dictionaries 
 * within a specific source language.
 */
@Command(
    name = "list-dicts",
    description = "List all registered source languages or dictionaries for a specific source language."
)
public class CommandListDictionaries implements Runnable {

    @Parameters(index = "0", paramLabel = "DICTDIR",
            description = "Root directory where dictionaries are stored (e.g., /home/user/worddict)")
    private String rootDirString;

    @Option(
        names = {"-s", "--source"},
        paramLabel = "SRC [TARGET]",
        arity = "1..2", // Дозволяє вказати від 1 до 2 значень (en або en de)
        description = "Source language and optional target dictionary (e.g. 'en' or 'en de')"
    )
    private String[] sourceArgs;

    @Option(
        names = {"-l", "--lookup"},
        paramLabel = "PREFIX",
        description = "Optional: Prefix to filter words (e.g., 'a')."
    )
    private String lookupPrefix;

    @Override
    public void run() {
        DictionaryManagerFileSystem manager = DictionaryManagerFileSystem.INSTANCE;
        try {
            manager.init(rootDirString);
            // System.out.println("--- WordDict Dictionary List ---");
            // System.out.println("Root Directory: " + manager.getRootDirectory());
            if (sourceArgs == null || sourceArgs.length == 0) { // 1. -s is empty
                listAllBaseLanguages(manager);
                return;
            }
            String src = sourceArgs[0].trim();
            if (sourceArgs.length == 2) { // 2. source and target (-s en de)
                String target = sourceArgs[1].trim();
                listWordsInDictionary(manager, src, target);
            } 
            else { // 3. Source only (-s en)
                listDictionariesForSourceLanguage(manager, src);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Lists all registered BaseLanguages, including the count of dictionaries for each.
     */
    private void listAllBaseLanguages(DictionaryManager manager) {
        Collection<BaseLanguage> languages = manager.getBaseLanguages();
         
        System.out.println("Registered Source Languages (" + languages.size() + "):");
         
        if (languages.isEmpty()) {
            return;
        }

        languages.stream()
            .forEach(bl -> {
                String code = bl.getLanguage().getLanguageCode();
                int dictCount = bl.listDictionaries().size(); 
                
                System.out.println(" - " + code + " (" + dictCount + " dictionaries)");
            });
             
        System.out.println("\nHint: To see dictionaries, re-run with '--source [CODE]'");
    }

    /**
     * Lists dictionaries for a specific BaseLanguage.
     */
    private void listDictionariesForSourceLanguage(DictionaryManager manager, String code) {
        Optional<BaseLanguage> baseOptional = manager.getBaseLanguage(code);

        if (baseOptional.isEmpty()) {
            System.err.println("\nError: Source language '" + code + "' is not registered.");
            System.out.println("Run 'list-dicts [ROOT]' to see available source languages.");
            return;
        }

        BaseLanguage base = baseOptional.get();
        Collection<Dictionary> dictionaries = base.listDictionaries(); // Використовуємо метод BaseLanguage
        
        System.out.println("\n## Dictionaries for Source Language: " + base.getLanguage().getLanguageCode());
        
        if (dictionaries.isEmpty()) {
            System.out.println("No dictionaries found for source language '" + code + "'.");
            return;
        }
        
        System.out.println("Dictionaries:");
        for (Dictionary dict : dictionaries) {
             // Припускаємо, що Dictionary має метод getName()
             // System.out.println(" - " + dict.getName());
            System.out.println(" - Dictionary: " + dict.getName() + " (Words: "+dict.wordsCount()+ ")");
        }
    }

    /**
     * Lists words within a specific dictionary with optional prefix filtering.
     */
    private void listWordsInDictionary(DictionaryManager manager, String src, String target) {
        Optional<BaseLanguage> baseOpt = manager.getBaseLanguage(src);
        if (baseOpt.isEmpty()) {
            System.err.println("\nError: Source language '" + src + "' is not registered.");
            return;
        }

        Optional<Dictionary> dictOpt = baseOpt.get().listDictionaries().stream()
                .filter(d -> d.getName().equalsIgnoreCase(target))
                .findFirst();

        if (dictOpt.isEmpty()) {
            System.err.println("\nError: Dictionary '" + target + "' not found for language '" + src + "'.");
            return;
        }

        DictionaryFileSystem dictFs = (DictionaryFileSystem) dictOpt.get();
        List<String> words = dictFs.listWords(lookupPrefix);
        if (!words.isEmpty()) words.forEach(w -> System.out.println(w));
    }
}
