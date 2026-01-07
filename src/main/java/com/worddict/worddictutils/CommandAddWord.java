package com.worddict.worddictutils;

import com.worddict.worddictcore.AudioSample;
import com.worddict.worddictcore.Language;
import com.worddict.worddictcore.Translation;
import com.worddict.worddictcore.Pronounce;
import com.worddict.worddictcore.SamplesList;
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

    @Option(names = {"-i", "--ipa"}, description = "Transcription: 'ipa === memo'")
    private List<String> ipaList;

    @Option(names = "-n", description = "General word note")
    private String note;

    @Option(names = "-ex", split = "===", description = "Global word samples")
    private List<String> globalExamples;

    @Option(names = "-s", description = "Add audio: 'path === comment === url'")
    private List<String> audioFiles;

    @Option(names = "-t", description = "Translation block: 'trn===ex1===ex2' (samples tied to translation)")
    private List<String> rawTranslations;

    @Override
    public Integer call() throws Exception {
        // 1. Ініціалізація
        Language srcLang = Language.getLanguageByCode(srcCode);
        Language targetLang = Language.getLanguageByCode(targetCode);
        
        // Використовуємо BaseLanguageFileSystem для управління ієрархією
        BaseLanguageFileSystem blfs = new BaseLanguageFileSystem(srcLang, dictDir);
        
        // Отримуємо або створюємо словник
        Dictionary dict = blfs.getOrCreateDictionary(targetLang, "");

        // 2. Створення та наповнення об'єкта Word
        Word word = new Word(wordText);
        
        // IPA Pronunciation (згідно з WordTest/getOther)
        if (ipaList != null) {
            Pronounce p = new Pronounce();
            for (String rawIpa : ipaList) {
                String[] parts = rawIpa.split("===");
                String ipaValue = parts[0].trim();
                Pronounce.TextPronounce tp = new Pronounce.TextPronounce(ipaValue);
                // Якщо є коментар (memo) після ===
                if (parts.length > 1) {
                    tp.setMemo(parts[1].trim());
                }
                p.addTextPronounce(tp);
            }
            word.setPronounce(p);
        }
        
        // Нотатка до слова
        if (note != null) {
            word.setNote(note);
        }
        
        // Глобальні приклади (SAMPLE_LIST)
        if (globalExamples != null) {
            SamplesList sl = word.getSamplesList();
            for (String ex : globalExamples) {
                sl.add(ex);
            }
        }

        // Обробка перекладів та їхніх ПРИВ'ЯЗАНИХ прикладів
        if (rawTranslations != null) {
            for (String raw : rawTranslations) {
                String[] parts = raw.split("===");
                if (parts.length > 0) {
                    // Перша частина - сам переклад
                    Translation tr = new Translation(parts[0]);
                    // Наступні частини - приклади саме для ЦЬОГО перекладу
                    for (int i = 1; i < parts.length; i++) {
                        tr.addSample(parts[i]);
                    }
                    word.addTranslation(tr);
                }
            }
        }

        // 3. Обробка звуку (Копіювання файлів та розбір метаданих)
        if (audioFiles != null) {
            Path soundsTargetDir = blfs.getLanguageRootPath().resolve("--sounds");
            System.out.println(audioFiles);
            for (String rawAudio : audioFiles) {
                // Розбиваємо кожен окремий -s на частини
                String[] parts = rawAudio.split("===");
                Path sourceFile = Path.of(parts[0].trim());

                if (Files.exists(sourceFile)) {
                    String fileName = sourceFile.getFileName().toString();
                    Path targetFile = soundsTargetDir.resolve(fileName);

                    // 1. Фізичне копіювання у спільну папку мови
                    Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

                    // 2. Створення об'єкта AudioSample (ID - це ім'я файлу без розширення)
                    String nameId = fileName.replaceFirst("[.][^.]+$", "");
                    AudioSample as = new AudioSample(nameId);
                    
                    // Встановлюємо посилання на скопійований файл
                    as.setFile(targetFile.toFile());

                    // 3. Заповнюємо додаткові поля, якщо вони передані через ===
                    if (parts.length > 1) {
                        as.setComment(parts[1].trim()); // Коментар (напр. Audio (US))
                    }
                    if (parts.length > 2) {
                        as.setUrl(parts[2].trim());    // Посилання на джерело
                    }

                    word.getAudioSamples().add(as);
                } else {
                    System.err.println("Warning: Audio file not found: " + sourceFile);
                }
            }
        }

        // 4. Збереження через Dictionary (з врахуванням бакетів A, T, U...)
        dict.saveWord(word);

        System.out.printf("Successfully saved '%s' to %s/%s\n", wordText, srcCode, dict.getName());
        return 0;
    }
}
