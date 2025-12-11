package com.worddict.worddictutils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import com.worddict.worddictcore.Language;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.io.IOException;

/**
 * Creates a new dictionary using DictionaryManagerFileSystem.
 */
@Command(
    name = "create-dict",
    description = "Create a new dictionary (filesystem backend)"
)
public class CommandCreateDictionary implements Runnable {

    @Parameters(index = "0", paramLabel = "ROOT",
            description = "Root directory where dictionaries are stored")
    private String rootDirString;

    @Parameters(index = "1", paramLabel = "SOURCE",
            description = "Source language code (e.g. en)")
    private String sourceLangCode;

    @Parameters(index = "2", paramLabel = "TARGET",
            description = "Target language code (e.g. uk)")
    private String targetLangCode;

    @Option(
        names = {"-n", "--name"},
        description = "Optional dictionary name (default: auto-generated)"
    )
    private String dictName;

    @Override
    public void run() {
        DictionaryManagerFileSystem manager = DictionaryManagerFileSystem.INSTANCE;
        try {
            manager.init(rootDirString);

            Language source = Language.getLanguageByCode(sourceLangCode);
            Language target = Language.getLanguageByCode(targetLangCode);

            BaseLanguage baseLangFs = new BaseLanguageFileSystem(source, manager.getRootDirectory());
           
            manager.addBaseLanguage(baseLangFs);
            Optional<BaseLanguage> baseOptional = manager.getBaseLanguage(sourceLangCode);

            if (baseOptional.isEmpty()) {
                System.err.println("Error: BaseLanguage implementation for '" +
                                     sourceLangCode + "' not registered.");
                return;
            }
            // Гарантовано отримуємо щойно зареєстрований екземпляр BaseLanguageFileSystem
            BaseLanguage base = baseOptional.get();

            Dictionary dict = (dictName == null)
                ? base.createDictionary(target)
                : base.createDictionary(target, dictName);

            System.out.println("Created dictionary:");
            System.out.print("  Path (Root): " + manager.getRootDirectory() + " → ");
            // Оскільки dict.toString() може не містити повного шляху, виводимо згенеровану назву
            String finalDictName = base.getDictionaryName(targetLangCode, dictName);
            
            System.out.println(String.format("  Name: %s, Full Path: %s",
                                 finalDictName, 
                                 ((BaseLanguageFileSystem)base).getDictionaryPath(finalDictName).toString()));

        } catch (IllegalArgumentException iae) {
            System.err.println("Input Error: " + iae.getMessage());
        } catch (IllegalStateException ise) {
            System.err.println("Manager Error: " + ise.getMessage());
        } catch (IOException ioe) {
            System.err.println("Filesystem Error: Could not initialize root directory or language structure: " + ioe.getMessage());
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
    }
}
