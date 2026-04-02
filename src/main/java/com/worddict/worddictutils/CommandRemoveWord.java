package com.worddict.worddictutils;

import com.worddict.worddictcore.Language;
import picocli.CommandLine.*;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.Optional;

@Command(name = "remove-word", description = "Removes a word from the dictionary")
public class CommandRemoveWord implements Callable<Integer> {

    @Parameters(index = "0", description = "Root dictionary directory")
    private Path dictDir;

    @Parameters(index = "1", description = "Source language code (e.g., en)")
    private String srcCode;

    @Parameters(index = "2", description = "Target language code (e.g., uk)")
    private String targetDictName;

    @Parameters(index = "3", description = "The word to remove")
    private String wordText;

    @Override
    public Integer call() throws Exception {
    Language srcLang = Language.getLanguageByCode(srcCode);
    BaseLanguageFileSystem blfs = new BaseLanguageFileSystem(srcLang, dictDir);
    Optional<Dictionary> dictOpt = blfs.getDictionary(targetDictName);

    if (dictOpt.isEmpty()) {
        System.err.printf("Error: Dictionary '%s' not found for language '%s'.\n",
                          targetDictName, srcCode);
        return 1;
    }
    Dictionary dict = dictOpt.get();

    if (dict.deleteWord(wordText)) {
        System.out.printf("Successfully removed '%s' from %s/%s\n",
                          wordText, srcCode, targetDictName);
        return 0;
    } else {
        System.err.printf("Error: Word '%s' not found in %s/%s\n",
                          wordText, srcCode, targetDictName);
        return 1;
    }
    }
}
