package com.worddict.worddictutils;

import com.worddict.worddictcore.Language;
import picocli.CommandLine.*;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "remove-word", description = "Removes a word from the dictionary")
public class CommandRemoveWord implements Callable<Integer> {

    @Parameters(index = "0", description = "Root dictionary directory")
    private Path dictDir;

    @Parameters(index = "1", description = "Source language code (e.g., en)")
    private String srcCode;

    @Parameters(index = "2", description = "Target language code (e.g., uk)")
    private String targetCode;

    @Parameters(index = "3", description = "The word to remove")
    private String wordText;

    @Override
    public Integer call() throws Exception {
        // 1. Ініціалізація мов та файлової системи
        Language srcLang = Language.getLanguageByCode(srcCode);
        Language targetLang = Language.getLanguageByCode(targetCode);
        
        BaseLanguageFileSystem blfs = new BaseLanguageFileSystem(srcLang, dictDir);
        
        // 2. Отримуємо словник
        // Використовуємо DictionaryFileSystem, щоб мати доступ до deleteWord
        DictionaryFileSystem dict = (DictionaryFileSystem) blfs.getOrCreateDictionary(targetLang, "");

        // 3. Спроба видалення
        if (dict.deleteWord(wordText)) {
            System.out.printf("Successfully removed '%s' from %s/%s\n", 
                              wordText, srcCode, targetLang.getLanguageCode());
            return 0;
        } else {
            System.err.printf("Error: Word '%s' not found in %s/%s\n", 
                              wordText, srcCode, targetLang.getLanguageCode());
            return 1;
        }
    }
}
