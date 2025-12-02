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

import com.worddict.worddictcore.Language;

public class DictionaryManagerFileSystemTest {

    private DictionaryManagerFileSystem manager;

    @Before
    public void setUp() {
        manager = DictionaryManagerFileSystem.INSTANCE;
        manager.resetForTests();
    }

    @Test
    public void testSingleton() {
        DictionaryManagerFileSystem m1 = DictionaryManagerFileSystem.INSTANCE;
        DictionaryManagerFileSystem m2 = DictionaryManagerFileSystem.INSTANCE;
        assertSame(m1, m2);
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
     * Tests the initialization of the root directory:
     * 1. Creates a temporary directory.
     * 2. Initializes the manager with this path.
     * 3. Verifies that the path is set correctly and the directory exists.
     * 4. Verifies that re-initialization throws IllegalStateException.
     */
    @Test
    public void testRootDirectoryInitialization() throws IOException {
        Path tempDir = Files.createTempDirectory("dict-test-root-");
        try {
            String rootPathString = tempDir.toString();
            manager.init(rootPathString);                 //1. Initialization Check
            Path actualRoot = manager.getRootDirectory(); //2. Check that the root path is set correctly
            assertEquals(tempDir.toAbsolutePath(), actualRoot.toAbsolutePath());
            assertTrue(Files.isDirectory(actualRoot));    //3. Check that the directory exists on the FS

            try {                                         //4. Attempt to re-initialize (should fail)
                manager.init("/some/other/path");
                fail("Expected IllegalStateException on re-initialization");
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("already initialized"));
            }
        } finally {
            deleteDirectoryRecursively(tempDir);           //5. Cleanup
        }
    }

    @Test(expected = IOException.class)
    public void testInitializationFailureOnInvalidPath() throws IOException {
        String restrictedPathString = "/root/test_init_fail";
        Path restrictedPath = Paths.get(restrictedPathString); // Перетворення на Path
        try {
            manager.init(restrictedPathString);
            fail("Expected IOException due to permission denied, but initialization succeeded.");
        } finally {
            if (Files.isDirectory(restrictedPath)) {
                deleteDirectoryRecursively(restrictedPath);
            } else {
                Files.deleteIfExists(restrictedPath);
            }
        }
    }

    /**
     * Helper method to recursively delete a directory and all its contents.
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
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
}
