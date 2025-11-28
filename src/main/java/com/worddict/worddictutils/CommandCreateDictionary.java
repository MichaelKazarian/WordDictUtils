package com.worddict.worddictutils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import com.worddict.worddictcore.Language;

import java.nio.file.Path;
import java.nio.file.Paths;

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
        try {
            Path rootDir = Paths.get(rootDirString);

            Language source = Language.getLanguageByCode(sourceLangCode);
            Language target = Language.getLanguageByCode(targetLangCode);

            // DictionaryManagerFileSystem manager =
            //         new DictionaryManagerFileSystem(rootDir);
            DictionaryManagerFileSystem manager = DictionaryManagerFileSystem.getInstance();

            //BaseLanguage base = manager.getBaseLanguage(source);
            BaseLanguage base = new MockBaseLanguage(source);

            Dictionary dict;
            if (dictName == null) {
                dict = base.createDictionary(target);
            } else {
                dict = base.createDictionary(target, dictName);
            }

            System.out.println("Created dictionary:");
            System.out.print("  " + rootDirString + " → " );
            System.out.println("  name: " +
                               base.getDictionaryName(targetLangCode, dictName));

        } catch (Exception e) {
            System.err.println("Error creating dictionary: " + e.getMessage());
        }
    }
}
