package com.worddict.worddictutils;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.File;
import java.util.Comparator;
import java.util.UUID;

import com.worddict.worddictcore.Language;

public class DictionaryManagerFileSystemTest {

    private static DictionaryManagerFileSystem manager;
    private static String currentTestRoot;

    @Before
    public void setUp() throws IOException {
        manager = DictionaryManagerFileSystem.INSTANCE;
        manager.resetForTests();
        currentTestRoot = getTempPath("dict-setup-");
        manager.init(currentTestRoot.toString());
    }

    @org.junit.AfterClass
    public static void tearDownClass() throws IOException {
        if (currentTestRoot != null) {
            deleteDirectoryRecursively(currentTestRoot);
            currentTestRoot = null;
        }
        manager.resetForTests();
    }

    @Test
    public void testSingleton() throws IOException {
        DictionaryManagerFileSystem m = DictionaryManagerFileSystem.INSTANCE;
        assertSame(manager, m);
    }

    @Test
    public void testAddAndGetBaseLanguage() {
        Language l = Language.getLanguageByCode("en");
        BaseLanguage en = new MockBaseLanguage(l);
        manager.addBaseLanguage(en);

        Optional<BaseLanguage> resultOptional = manager.getBaseLanguage("en");
        assertTrue(resultOptional.isPresent());
        var result = resultOptional.orElseThrow();
        assertEquals("en", result.getLanguage().getLanguageCode());
    }

    @Test
    public void testCaseInsensitiveLookup() {
        Language l = Language.getLanguageByCode("es");
        BaseLanguage es = new MockBaseLanguage(l);
        manager.addBaseLanguage(es);
        assertTrue(manager.getBaseLanguage("ES").isPresent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullLanguage() {
        manager.addBaseLanguage(null);
    }

    @Test
    public void testGetDictionaryName() {
        Language en = Language.getLanguageByCode("en");
        BaseLanguage base = new MockBaseLanguage(en);
        assertEquals("en-uk", base.getFullDictionaryName("uk", null));
        assertEquals("en-uk", base.getFullDictionaryName("uk", ""));
        assertEquals("en-de-extra", base.getFullDictionaryName("de", "Extra"));
        assertEquals("en-fr-my-name", base.getFullDictionaryName("fr", " my name "));
        assertEquals("en-pl-my-dict", base.getFullDictionaryName("pl", "my*dict?"));
        assertEquals("en-pl-my-dict", base.getFullDictionaryName("pl", "my-dict"));
        assertEquals("en-it-a-b-c", base.getFullDictionaryName("it", "A--B--C"));
    }

    @Test
    public void testGetDictionaryNameUnicode() {
        Language en = Language.getLanguageByCode("en");
        BaseLanguage base = new MockBaseLanguage(en);

        assertEquals("en-uk", base.getFullDictionaryName("uk", "тест"));
        assertEquals("en-fr", base.getFullDictionaryName("fr", "éàöü"));
    }


    /**
     * Tests that calling addBaseLanguage() multiple times with the same
     * language code registers the language only once and preserves the
     * original BaseLanguage instance, ensuring that subsequent calls are
     * skipped (the 'duplicate is skipped' logic).
     */
    @Test
    public void testAddBaseLanguage_duplicateIsSkipped() {
        Language l = Language.getLanguageByCode("en");
        BaseLanguage originalBL = new MockBaseLanguage(l);
        manager.addBaseLanguage(originalBL);

        assertEquals("Initial count must be one", 1, manager.getBaseLanguages().size());
        
        BaseLanguage newBL = new MockBaseLanguage(l);
        manager.addBaseLanguage(newBL);

        assertEquals("Count must remain one", 1, manager.getBaseLanguages().size());
        Optional<BaseLanguage> resultOptional = manager.getBaseLanguage("en");
        assertTrue(resultOptional.isPresent());
        
        BaseLanguage saved = resultOptional.get();
        assertSame("Must keep original instance", originalBL, saved);
        assertNotSame("New instance must be skipped", newBL, saved);
    }

    /**
     * Helper method to recursively delete a directory and all its contents.
     */
    static void deleteDirectoryRecursively(String p) throws IOException {
        if (p == null) return;
        Path path = Paths.get(p);
        if (Files.exists(path)) {
            // Traverse the directory, reverse the order, map to File, and delete.
            Files.walk(path)
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }

    /**
     * Generates a unique, non-existent path String within the system's temporary directory,
     * isolated by the current user's name, suitable for testing root directory creation.
     * * @param testName A descriptive name to include in the path (e.g., "positive-init").
     * @return The String representation of the absolute Path that does not exist on the filesystem yet.
     */
    static String getTempPath(String testName) {
        Path systemTempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        String userName = System.getProperty("user.name");
        String uniquePathSegment = userName + "/" + testName + "-" + UUID.randomUUID().toString();
        Path nonExistentPath = systemTempDir.resolve(uniquePathSegment);

        return nonExistentPath.toString();
    }
}
