package com.worddict.worddictutils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

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
        paramLabel = "SRC",
        description = "Optional: Source language code to inspect (e.g., 'en'). If specified, lists its dictionaries."
    )
    private String sourceLangCode;

    @Override
    public void run() {
        DictionaryManagerFileSystem manager = DictionaryManagerFileSystem.INSTANCE;
        try {
            manager.init(rootDirString);
            
            System.out.println("--- WordDict Dictionary List ---");
            System.out.println("Root Directory: " + manager.getRootDirectory());
            
            if (sourceLangCode != null && !sourceLangCode.isBlank()) {
                listDictionariesForSourceLanguage(manager, sourceLangCode.trim());
            } else {
                listAllBaseLanguages(manager);
            }

        } catch (IllegalStateException ise) {
            System.err.println("Manager Error: " + ise.getMessage());
        } catch (IOException ioe) {
            System.err.println("Filesystem Error: Could not initialize root directory: " + ioe.getMessage());
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Lists all registered BaseLanguages.
     */
    private void listAllBaseLanguages(DictionaryManager manager) {
        Collection<BaseLanguage> languages = manager.getBaseLanguages();
        
        System.out.println("Registered Source Languages (" + languages.size() + "):");
        
        if (languages.isEmpty()) {
            return;
        }

        languages.stream()
            .map(bl -> bl.getLanguage().getLanguageCode().toUpperCase())
            .forEach(code -> System.out.println(" - " + code));
            
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
        
        System.out.println("[WARNING] Real dictionary listing logic is currently mocked.");
        System.out.println("Dictionaries (Mocked List):");
        
        for (Dictionary dict : dictionaries) {
             // Припускаємо, що Dictionary має метод getName()
             // System.out.println(" - " + dict.getName());
             System.out.println(" - [MOCK] Dictionary: " + code + "-target (Size: 0)"); 
        }
    }
}
