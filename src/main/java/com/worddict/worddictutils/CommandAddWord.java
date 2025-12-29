package com.worddict.worddictutils;

import com.worddict.worddictcore.Language;
import com.worddict.worddictcore.Translation;
import com.worddict.worddictcore.Word;
import picocli.CommandLine.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "add-word", description = "Adds or overwrites a word in the dictionary")
public class CommandAddWord implements Callable<Integer> {

    @Parameters(index = "0", description = "Root dictionary directory")
    private Path dictDir;

    @Parameters(index = "1", description = "Source language code (e.g., en)")
    private String srcCode;

    @Parameters(index = "2", description = "Target language code (e.g., uk)")
    private String targetCode;

    @Parameters(index = "3", description = "The word to add")
    private String wordText;

    @Option(names = {"-i", "--ipa"}, description = "Transcription")
    private String ipa;

    @Option(names = "-n", description = "Global note")
    private String note;

    @Option(names = "-ex", split = ";!;", description = "Global examples")
    private List<String> globalExamples;

    @Option(names = "-s", split = ";!;", description = "Audio files to copy")
    private List<String> audioFiles;

    @Option(names = "-t", description = "Translation block: 'trn;!;ex1;!;ex2'")
    private List<String> rawTranslations;

    @Override
    public Integer call() throws Exception {
        // 1. Ініціалізація оточення
        Language srcLang = Language.getLanguageByCode(srcCode);
        Language targetLang = Language.getLanguageByCode(targetCode);
        BaseLanguageFileSystem blfs = new BaseLanguageFileSystem(srcLang, dictDir);
        
        // Отримуємо або створюємо словник
        Dictionary dict = blfs.getOrCreateDictionary(targetLang, "");
        // 2. Створення та наповнення об'єкта Word
        Word word = new Word(wordText);
        // if (ipa != null) word.getPronounce().addPronounce(ipa); // Використовуємо ядро WordDictCore
        // if (note != null) word.setNote(note);
        
        // Глобальні приклади
        if (globalExamples != null) {
            // word.getSamplesList().addAll(globalExamples);
        }

        // Обробка перекладів
        if (rawTranslations != null) {
            for (String raw : rawTranslations) {
                String[] parts = raw.split(";!;");
                if (parts.length > 0) {
                    Translation tr = new Translation(parts[0]);
                    for (int i = 1; i < parts.length; i++) {
                        tr.addSample(parts[i]);
                    }
                    word.addTranslation(tr);
                }
            }
        }

        // 3. Обробка звуку (Копіювання файлів)
        if (audioFiles != null) {
            Path soundsTargetDir = blfs.getLanguageRootPath().resolve("--sounds");
            for (String audioPathStr : audioFiles) {
                Path sourceFile = Path.of(audioPathStr);
                if (Files.exists(sourceFile)) {
                    String fileName = sourceFile.getFileName().toString();
                    Path targetFile = soundsTargetDir.resolve(fileName);
                    
                    // Копіюємо, якщо файлу ще немає
                    if (Files.notExists(targetFile)) {
                        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    // У Word записуємо тільки ім'я файлу (це зафіксовано в ТЗ)
                    // word.getAudioSamples().add(new AudioSample(fileName)); 
                    // Примітка: Додай AudioSample відповідно до твого ядра
                } else {
                    System.err.println("Warning: Audio file not found: " + audioPathStr);
                }
            }
        }

        // 4. ЗБЕРЕЖЕННЯ
        dict.saveWord(word);

        System.out.printf("Successfully saved '%s' to %s/%s\n", wordText, srcCode, dict.getName());
        return 0;
    }
}
