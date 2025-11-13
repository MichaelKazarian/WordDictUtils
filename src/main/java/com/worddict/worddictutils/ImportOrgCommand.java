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
import java.util.Optional;
import java.util.List;

import com.worddict.worddictcore.Word;
import com.worddict.worddictcore.Translation;
import com.worddict.worddictcore.Pronounce;
import org.json.JSONObject;

/**
 * Command for importing vocabulary data from org-mode tables into JSON format.
 * <p>
 * Expected org-mode format: | *word* [transcription] (pos) ukr1; ukr2 | en1. (ukr1.) en2. (ukr2.) |
 * </p>
 */
@Command(
    name = "import-org",
    description = "Import from org-mode table → JSON (without Dictionary)"
)
public class ImportOrgCommand implements Runnable {
    
    /**
     * Data structure for parsed org-mode table entry.
     */
    private static class ParsedEntry {
        String word;
        String transcription;
        String pos;
        String[] translations;
        String[] examples;
    }
    
    /**
     * Container for import operation results and statistics.
     */
    private static class ImportResult {
        int totalWords = 0;
        int totalTranslations = 0;
        List<String> errors = new ArrayList<>();
    
        /**
         * Records a successfully processed word.
         * 
         * @param translationsCount number of translations for this word
         */
        void recordWord(int translationsCount) {
            totalWords++;
            totalTranslations += translationsCount;
        }
    }
    
    // Pattern: | *word* [transcr] (pos) ukr1; ukr2 | en1. (ukr1.) en2. (ukr2.) |
    private static final Pattern LINE_PATTERN = Pattern.compile(
        "\\|\\s*\\*(\\S+?)\\*\\s*\\[([^]]+)\\]\\s*\\(([^)]+)\\)\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|"
    );
    
    private static final String POS_NOTE_FORMAT = "(POS: %s)";
    private static final String UTF_8 = "UTF-8";
    
    @Parameters(index = "0", paramLabel = "INPUT", description = "Path to .org file")
    private String inputPath;
    
    @Parameters(index = "1", paramLabel = "OUTPUT", description = "Directory for JSON files")
    private String outputDir;
    
    /**
     * Main execution method for the import command.
     * Validates input, prepares output directory, processes file, and prints summary.
     */
    @Override
    public void run() {
        Path input = validateInput();
        Path output = prepareOutput();
        
        try {
            ImportResult result = processFile(input, output);
            printSummary(result);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
    }
    
    /**
     * Validates that the input file exists and is a regular file.
     * 
     * @return validated input path
     * @throws IllegalArgumentException if file doesn't exist
     */
    private Path validateInput() {
        Path input = Paths.get(inputPath);
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("File not found: " + input);
        }
        return input;
    }
    
    /**
     * Prepares output directory by creating it if it doesn't exist.
     * 
     * @return prepared output path
     * @throws RuntimeException if directory creation fails
     */
    private Path prepareOutput() {
        Path output = Paths.get(outputDir);
        try {
            Files.createDirectories(output);
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + output, e);
        }
    }
    
    /**
     * Parses a single org-mode table line into a structured entry.
     * 
     * @param line trimmed line from org-mode file
     * @return Optional containing ParsedEntry if line matches expected format, empty otherwise
     */
    private Optional<ParsedEntry> parseLine(String line) {
        Matcher m = LINE_PATTERN.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
    
        ParsedEntry entry = new ParsedEntry();
        entry.word = m.group(1);
        entry.transcription = m.group(2);
        entry.pos = m.group(3);
        entry.translations = m.group(4).trim().split("\\s*;\\s*");
        entry.examples = m.group(5).trim().split("\\)\\s+(?=[A-Z\"'A-Za-z])");
    
        return Optional.of(entry);
    }
    
    /**
     * Creates a Word object from a parsed entry.
     * 
     * @param entry parsed org-mode entry
     * @return fully constructed Word object with pronunciation, notes, and translations
     */
    private Word createWord(ParsedEntry entry) {
        Word word = new Word(entry.word);
        word.setPronounce(createPronounce(entry.transcription));
        word.setNote(String.format(POS_NOTE_FORMAT, entry.pos));
        word.setTranslations(createTranslations(entry));
        return word;
    }
    
    /**
     * Creates a Pronounce object from transcription text.
     * 
     * @param transcription phonetic transcription
     * @return Pronounce object containing the transcription
     */
    private Pronounce createPronounce(String transcription) {
        Pronounce p = new Pronounce();
        p.addTextPronounce(new Pronounce.TextPronounce(transcription));
        return p;
    }
    
    /**
     * Creates a list of Translation objects from parsed entry data.
     * Matches target language translations with corresponding source language examples.
     * 
     * @param entry parsed org-mode entry
     * @return list of Translation objects with samples
     */
    private ArrayList<Translation> createTranslations(ParsedEntry entry) {
        ArrayList<Translation> translations = new ArrayList<>();
    
        for (int i = 0; i < entry.translations.length; i++) {
            String t = entry.translations[i].trim();
            if (t.isEmpty()) continue;
        
            Translation tr = new Translation(t);
            addSampleIfPresent(tr, entry.examples, i);
            translations.add(tr);
        }
    
        return translations;
    }
    
    /**
     * Adds an example sample to a translation if available at the given index.
     * 
     * @param t translation to add sample to
     * @param examples array of example strings
     * @param index index of the example to add
     */
    private void addSampleIfPresent(Translation t, String[] examples, int index) {
        if (index >= examples.length) return;
    
        String block = examples[index].trim();
        if (block.isEmpty()) return;
    
        t.addSample(extractSample(block));
    }
    
    /**
     * Extracts a formatted sample from an example block.
     * Separates source text from target translation in parentheses.
     * 
     * @param block example block text
     * @return formatted sample string "Source text (target translation)"
     */
    private String extractSample(String block) {
        int parenIdx = block.lastIndexOf('(');
        if (parenIdx == -1) return block;
    
        String sourceText = block.substring(0, parenIdx).trim();
        String targetText = block.substring(parenIdx + 1, block.length() - 1).trim();
        return sourceText + " (" + targetText + ")";
    }
    
    /**
     * Prints summary of the import operation including statistics and errors.
     * 
     * @param result import operation results
     */
    private void printSummary(ImportResult result) {
        System.out.printf("Success! Processed %d words, %d translations → %s%n",
                          result.totalWords, result.totalTranslations, outputDir);
    
        if (!result.errors.isEmpty()) {
            System.err.println("Errors during processing:");
            result.errors.forEach(System.err::println);
        }
    }
    
    /**
     * Saves a Word object as a JSON file in the output directory.
     * Errors are logged but don't stop processing of other words.
     * 
     * @param word Word object to save
     * @param outputDir directory to save the JSON file
     */
    private void saveWord(Word word, Path outputDir) {
        try {
            JSONObject json = word.toJsonObject();
            Path outFile = outputDir.resolve(word.getWord() + ".json");
            Files.write(outFile, json.toString(2).getBytes(UTF_8));
        } catch (IOException e) {
            System.err.printf("Error writing %s: %s%n", 
                              word.getWord(), e.getMessage());
        }
    }
    
    /**
     * Processes the input org-mode file line by line.
     * Parses each valid line, creates Word objects, and saves them as JSON files.
     * 
     * @param input path to input org-mode file
     * @param output path to output directory
     * @return ImportResult containing statistics and any errors
     * @throws IOException if file reading fails
     */
    private ImportResult processFile(Path input, Path output) throws IOException {
        ImportResult result = new ImportResult();
        List<String> lines = Files.readAllLines(input);
    
        for (String line : lines) {
            Optional<ParsedEntry> parsedEntry = parseLine(line.trim());
            if (!parsedEntry.isPresent()) {
                continue;
            }
        
            Word word = createWord(parsedEntry.get());
            saveWord(word, output);
            result.recordWord(word.getTranslations().size());
        }
    
        return result;
    }
}
