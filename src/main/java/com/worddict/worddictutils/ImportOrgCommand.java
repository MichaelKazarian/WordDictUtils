package com.worddict.worddictutils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.worddict.worddictcore.Word;
import com.worddict.worddictcore.Translation;
import com.worddict.worddictcore.Pronounce;
import org.json.JSONObject;

@Command(
    name = "import-org",
    description = "Імпорт з org-mode таблиці → JSON (без Dictionary)"
)
public class ImportOrgCommand implements Runnable {

    // Патерн: | *word* [transcr] (pos) укр1; укр2 | en1. (укр1.) en2. (укр2.) |
    private static final Pattern LINE_PATTERN = Pattern.compile(
        "\\|\\s*\\*(\\S+?)\\*\\s*\\[([^]]+)\\]\\s*\\(([^)]+)\\)\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|"
    );

    @Parameters(index = "0", paramLabel = "INPUT", description = "Шлях до .org файлу")
    private String inputPath;

    @Parameters(index = "1", paramLabel = "OUTPUT", description = "Папка для JSON-файлів")
    private String outputDir;

    @Override
    public void run() {
        Path input = validateInput();
        Path output = prepareOutput();

        int totalWords = 0;
        int totalTranslations = 0;

        try {
            for (String rawLine : Files.readAllLines(input)) {
                String line = rawLine.trim();
                if (!line.startsWith("| *") || line.contains("<l")) continue;

                Matcher m = LINE_PATTERN.matcher(line);
                if (!m.matches()) continue;

                String word = m.group(1);
                String transcription = m.group(2);
                String pos = m.group(3);
                String ukrPart = m.group(4).trim();      // "над; вище"
                String enPart = m.group(5).trim();       // "The bird... She ranked..."

                Word w = new Word(word);
                Pronounce p = new Pronounce();
                Pronounce.TextPronounce tp0 = new Pronounce.TextPronounce(transcription);
                p.addTextPronounce(tp0);
                w.setPronounce(p);
                w.setNote("(POS: "+pos+")");
                // Розбиваємо переклади
                String[] ukrTranslations = ukrPart.split("\\s*;\\s*");
                String[] examples = enPart.split("\\)\\s+(?=[A-Z\"'A-Za-z])");

                ArrayList<Translation> translations = new ArrayList<>();

                for (int i = 0; i < ukrTranslations.length; i++) {
                    String ukr = ukrTranslations[i].trim();
                    if (ukr.isEmpty()) continue;

                    Translation t = new Translation(ukr);

                    // Шукаємо відповідний приклад
                    if (i < examples.length) {
                        String block = examples[i].trim();
                        if (!block.isEmpty()) {
                            // Витягуємо (укр частину) з кінця
                            int parenIdx = block.lastIndexOf('(');
                            if (parenIdx != -1) {
                                String enText = block.substring(0, parenIdx).trim();
                                String ukText = block.substring(parenIdx + 1, block.length() - 1).trim();
                                t.addSample(enText + " (" + ukText + ")");
                            } else {
                                t.addSample(block);
                            }
                        }
                    }

                    translations.add(t);
                    totalTranslations++;
                }

                w.setTranslations(translations);

                // Запис у JSON
                JSONObject json = w.toJsonObject();
                Path outFile = output.resolve(word + ".json");
                Files.write(outFile, json.toString(2).getBytes("UTF-8"));

                totalWords++;
            }

            System.out.printf("Успіх! Оброблено %d слів, %d перекладів → %s%n",
                totalWords, totalTranslations, output);

        } catch (IOException e) {
            System.err.println("Пом(li)ка I/O: " + e.getMessage());
        }
    }

    private Path validateInput() {
        Path input = Paths.get(inputPath);
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("Файл не знайдено: " + input);
        }
        return input;
    }

    private Path prepareOutput() {
        Path output = Paths.get(outputDir);
        try {
            Files.createDirectories(output);
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Не вдалося створити папку: " + output, e);
        }
    }
}
