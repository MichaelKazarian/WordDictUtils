package com.worddict.worddictutils;

import com.worddict.worddictcore.Language;
//import com.worddict.worddictutils.Dictionary;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for the file system implementation of BaseLanguage (using JUnit 4).
 */
public class BaseLanguageFileSystemTest {
    // JUnit 4 Rule for creating and managing a temporary directory
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    private Path tempRoot; // Base path for the test filesystem
    private Language sourceLanguage;   // 'en'
    private Language targetLanguageUk; // 'uk'
    private Language targetLanguageEs; // 'es'
    
    private BaseLanguageFileSystem baseLangFs; 

    @Before
    public void setUp() throws Exception {
        // Get the root path provided by the TemporaryFolder Rule
        tempRoot = tempFolder.getRoot().toPath();
        
        sourceLanguage = Language.getLanguageByCode("en");
        targetLanguageUk = Language.getLanguageByCode("uk");
        targetLanguageEs = Language.getLanguageByCode("es");
        
        // This should create 'en' and 'en/--sounds' inside tempRoot
        baseLangFs = new BaseLanguageFileSystem(sourceLanguage, tempRoot);
    }
    
    // =========================================================================
    // 1) Test for Base Language Directory and System Folder Creation
    // =========================================================================

    @Test
    public void testInitialization_createsCorrectStructure() {
        // Check 1.1: Existence of the language root directory (e.g., /temp/en)
        Path langRoot = tempRoot.resolve("en");
        assertTrue("Base language root 'en' must exist.", Files.isDirectory(langRoot));
        
        // Check 1.2: Existence of the system directory (--sounds)
        Path soundsPath = langRoot.resolve("--sounds");
        assertTrue("System directory '--sounds' must exist.", Files.isDirectory(soundsPath));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testDictionaryUniquenessCheck_failsForExistingFsDirectory() throws IOException {
        // Get the expected path for the 'uk' dictionary
        Path existingDictPath = tempRoot.resolve("en").resolve("uk");
        // Create the directory manually, simulating an existing dictionary folder
        Files.createDirectories(existingDictPath);
        // Attempt to create the 'uk' dictionary. Should throw IllegalArgumentException (as expected by @Test(expected=...)).
        baseLangFs.createDictionary(targetLanguageUk);
        
        // Note: For JUnit 4, we don't need a try-catch block here; the expected exception handles it.
    }

    // =========================================================================
    // 2) Test for Dictionary Creation
    // =========================================================================

    @Test
    public void testCreateDictionary_withoutAdditionalName() {
        // Create 'uk' dictionary
        Dictionary dict = baseLangFs.createDictionary(targetLanguageUk);
        
        // Check 2.1: Dictionary must be registered
        assertTrue("Dictionary 'uk' must be registered.", baseLangFs.listDictionaries().contains(dict));
        
        // Check 2.2: Directory must exist
        Path dictPath = tempRoot.resolve("en").resolve("uk");
        assertTrue("Dictionary directory 'en/uk' must exist.", Files.isDirectory(dictPath));
        
        // Check 2.3: Additional name must be empty
        assertEquals("Additional name must be empty.", "", dict.getAdditionalName());
    }

    @Test
    public void testCreateDictionary_withAdditionalName() {
        String additionalName = "Tech-Terms";
        // Create 'es-tech-terms' dictionary
        Dictionary dict = baseLangFs.createDictionary(targetLanguageEs, additionalName);
        
        // Check 2.4: Directory must exist with the correct format
        Path dictPath = tempRoot.resolve("en").resolve("es-tech-terms");
        assertTrue("Dictionary directory 'en/es-tech-terms' must exist.", Files.isDirectory(dictPath));
        
        // Check 2.5: Additional name must be correctly saved
        String an = dict.getAdditionalName();
        assertEquals("Additional name must be present", "Tech-Terms", dict.getAdditionalName());
    }

    // =========================================================================
    // 3) Test for Loading Dictionaries from Filesystem
    // =========================================================================

    @Test
    public void testLoadDictionariesFromFilesystem_loadsExistingValidDictionaries() throws Exception {
        // Reset the manager and prepare the directory structure manually
        baseLangFs = null; 

        // Get the path to the language root
        Path langRoot = tempRoot.resolve("en");
        
        // Preparation 3.1: Create dictionary 1: 'uk'
        Path ukPath = langRoot.resolve("uk");
        Files.createDirectories(ukPath.resolve("words")); 
        
        // Preparation 3.2: Create dictionary 2: 'es-geo'
        Path esGeoPath = langRoot.resolve("es-geo");
        Files.createDirectories(esGeoPath.resolve("words")); 
        
        // Create an invalid name that should be skipped
        Files.createDirectories(langRoot.resolve("invalid-name-format"));
        
        // Create BaseLanguageFileSystem again. It should read them (reloading)
        BaseLanguageFileSystem reloadedBaseLangFs = new BaseLanguageFileSystem(sourceLanguage, tempRoot);
        
        // Check 3.3: 2 dictionaries should be found
        List<Dictionary> dictionaries = reloadedBaseLangFs.listDictionaries();
        assertEquals("Should load exactly 2 valid dictionaries.", 2, dictionaries.size());
        
        // Check 3.4: Correctness of loaded objects
        boolean ukFound = dictionaries.stream()
                .anyMatch(d -> d.getTargetLanguage().equals(targetLanguageUk) && d.getAdditionalName().isEmpty());
        assertTrue("Dictionary 'uk' must be loaded correctly.", ukFound);
        
        boolean esGeoFound = dictionaries.stream()
                .anyMatch(d -> d.getTargetLanguage().equals(targetLanguageEs) && d.getAdditionalName().equals("geo"));
        assertTrue("Dictionary 'es-geo' must be loaded correctly.", esGeoFound);
    }

    @Test
    public void testLoadDictionariesFromFilesystem_ignoresSystemAndInvalidDirectories() throws Exception {
        // Reset the manager
        baseLangFs = null; 

        Path langRoot = tempRoot.resolve("en");
        
        // Create system directory (should be ignored by startsWith("--"))
        Files.createDirectories(langRoot.resolve("--cache"));
        
        // Create invalid name format (should be ignored by Regex)
        Files.createDirectories(langRoot.resolve("english-uk"));
        
        // Create BaseLanguageFileSystem again.
        BaseLanguageFileSystem reloadedBaseLangFs = new BaseLanguageFileSystem(sourceLanguage, tempRoot);
        
        // Check 3.5: No dictionary should be found
        List<Dictionary> dictionaries = reloadedBaseLangFs.listDictionaries();
        assertTrue("No dictionary should be loaded if only system/invalid directories exist.", dictionaries.isEmpty());
    }
}
