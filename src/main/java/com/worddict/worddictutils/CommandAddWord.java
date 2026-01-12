package com.worddict.worddictutils;

import com.worddict.worddictcore.*;
import picocli.CommandLine.*;

import java.io.IOException;
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

    @Option(names = "-ex", description = "Global word samples")
    private List<String> globalExamples;

    @Option(names = "-s", description = "Add audio: 'path === comment === url'")
    private List<String> audioFiles;

    @Option(names = "-t", description = "Translation block: 'trn===ex1===ex2' (samples tied to translation)")
    private List<String> rawTranslations;

    @Override
    public Integer call() throws Exception {
        // 1. Ініціалізація доменних об'єктів
        Language srcLang = Language.getLanguageByCode(srcCode);
        Language targetLang = Language.getLanguageByCode(targetCode);
        BaseLanguageFileSystem blfs = new BaseLanguageFileSystem(srcLang, dictDir);
        Dictionary dict = blfs.getOrCreateDictionary(targetLang, "");

        // 2. Створення та наповнення Word
        Word word = new Word(wordText);
        applyPronunciations(word);
        applyNote(word);
        applyGlobalExamples(word);
        applyTranslations(word);
        processAudioFiles(word, blfs.getLanguageRootPath().resolve("--sounds"));

        dict.saveWord(word);
        System.out.printf("Successfully saved '%s' to %s/%s\n", wordText, srcCode, dict.getName());
        return 0;
    }

    private void applyPronunciations(Word word) {
        if (ipaList == null) return;
        
        Pronounce p = new Pronounce();
        for (String raw : ipaList) {
            String[] parts = raw.split("===");
            Pronounce.TextPronounce tp = new Pronounce.TextPronounce(parts[0].trim());
            if (parts.length > 1) {
                tp.setMemo(parts[1].trim());
            }
            p.addTextPronounce(tp);
        }
        word.setPronounce(p);
    }

    private void applyNote(Word word) {
        if (note != null) {
            word.setNote(note);
        }
    }

    private void applyGlobalExamples(Word word) {
        if (globalExamples == null) return;
        
        for (String ex : globalExamples) {
            String trimmed = ex.trim();
            if (!trimmed.isEmpty()) {
                word.getSamplesList().add(trimmed);
            }
        }
    }

    private void applyTranslations(Word word) {
        if (rawTranslations == null) return;

        for (String raw : rawTranslations) {
            String[] parts = raw.split("===");
            if (parts.length > 0) {
                Translation tr = new Translation(parts[0].trim());
                for (int i = 1; i < parts.length; i++) {
                    tr.addSample(parts[i].trim());
                }
                word.addTranslation(tr);
            }
        }
    }

    private void processAudioFiles(Word word, Path soundsTargetDir) throws IOException {
        if (audioFiles == null) return;

        // Перевіряємо/створюємо папку звуків один раз
        if (Files.notExists(soundsTargetDir)) {
            Files.createDirectories(soundsTargetDir);
        }

        for (String raw : audioFiles) {
            String[] parts = raw.split("===");
            Path sourceFile = Path.of(parts[0].trim());

            if (Files.exists(sourceFile)) {
                String fileName = sourceFile.getFileName().toString();
                Path targetFile = soundsTargetDir.resolve(fileName);

                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

                AudioSample as = createAudioSample(fileName, targetFile, parts);
                word.getAudioSamples().add(as);
            } else {
                System.err.println("Warning: Audio file not found: " + sourceFile);
            }
        }
    }

    private AudioSample createAudioSample(String fileName, Path targetFile, String[] parts) {
        String nameId = fileName.replaceFirst("[.][^.]+$", "");
        AudioSample as = new AudioSample(nameId);
        as.setFile(targetFile.toFile());

        if (parts.length > 1) as.setComment(parts[1].trim());
        if (parts.length > 2) as.setUrl(parts[2].trim());
        
        return as;
    }
}
