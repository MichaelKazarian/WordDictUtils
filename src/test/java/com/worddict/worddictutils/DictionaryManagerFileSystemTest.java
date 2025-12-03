package com.worddict.worddictutils;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
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
                 .forEach(java.io.File::delete);
        }
    }

    @Test
    public void testGetDictionaryName() {
        Language en = Language.getLanguageByCode("en");
        BaseLanguage base = new MockBaseLanguage(en);
        assertEquals("en-uk", base.getDictionaryName("uk", null));
        assertEquals("en-uk", base.getDictionaryName("uk", ""));
        assertEquals("en-de-extra", base.getDictionaryName("de", "Extra"));
        assertEquals("en-fr-my-name", base.getDictionaryName("fr", " my name "));
        assertEquals("en-pl-my-dict", base.getDictionaryName("pl", "my*dict?"));
        assertEquals("en-pl-my-dict", base.getDictionaryName("pl", "my-dict"));
        assertEquals("en-it-a-b-c", base.getDictionaryName("it", "A--B--C"));
    }

    @Test
    public void testGetDictionaryNameUnicode() {
        Language en = Language.getLanguageByCode("en");
        BaseLanguage base = new MockBaseLanguage(en);

        assertEquals("en-uk", base.getDictionaryName("uk", "тест"));
        assertEquals("en-fr", base.getDictionaryName("fr", "éàöü"));
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
